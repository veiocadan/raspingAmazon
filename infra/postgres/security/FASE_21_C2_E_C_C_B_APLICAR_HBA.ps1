# FASE 21-C2-E-C-C-B - APLICACAO CONTROLADA DO HBA SCRAM.
# NAO executar durante a pre-verificacao. Exige autorizacao explicita.
# Inclui restauracao automatica, a partir do backup C2-E-C-C-A,
# se a troca ou a validacao pos-reload falhar.
param(
    [Parameter(Mandatory = $true)]
    [string]$CaminhoManifesto,
    [switch]$ConfirmarAplicacao,
    [string]$CaminhoCompose = 'infra/compose.yml',
    [string]$NomeContainer = 'rasping-amazon-postgres'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function ExigirCodigoZero([string]$Mensagem) {
    if ($LASTEXITCODE -ne 0) { throw $Mensagem }
}
function ExtrairSha256([object]$Saida) {
    $valor = ($Saida | Out-String).Trim()
    if ($valor -match '^([0-9A-Fa-f]{64})\s+') {
        return $Matches[1].ToUpperInvariant()
    }
    throw 'SHA-256 inesperado retornado pelo container.'
}

if (-not $ConfirmarAplicacao) {
    throw 'APLICACAO BLOQUEADA: falta o parametro -ConfirmarAplicacao.'
}

$precheck = 'infra/postgres/security/FASE_21_C2_E_C_C_B_PRECHECK_APLICACAO.ps1'
$reversao = 'infra/postgres/security/FASE_21_C2_E_C_C_A_RESTAURAR_HBA_ORIGINAL.ps1'
$proposta = 'infra/postgres/security/FASE_21_C2_E_C_B_POLITICA_HBA_PROPOSTA.conf'
$verificarSql = 'infra/postgres/security/FASE_21_C2_E_C_C_B_VERIFICAR_HBA.sql'
$testarShell = 'infra/postgres/security/FASE_21_C2_E_C_C_B_TESTAR_AUTENTICACAO.sh'
foreach ($arquivo in @($precheck, $reversao, $proposta, $verificarSql, $testarShell)) {
    if (-not (Test-Path -LiteralPath $arquivo -PathType Leaf)) {
        throw 'Arquivos obrigatorios da operacao HBA ausentes.'
    }
}

# Reaproveita todas as protecoes: manifesto, hash original, baseline HBA,
# senha SCRAM real, porta exclusivamente loopback e ausencia de conexoes.
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $precheck -CaminhoManifesto $CaminhoManifesto -CaminhoCompose $CaminhoCompose -NomeContainer $NomeContainer
ExigirCodigoZero 'PRECHECK nao aprovado; nenhuma aplicacao executada.'

$m = Get-Content -LiteralPath $CaminhoManifesto -Raw -Encoding UTF8 | ConvertFrom-Json
$hashOriginal = ([string]$m.sha256).ToUpperInvariant()
$hashProposta = (Get-FileHash -LiteralPath $proposta -Algorithm SHA256).Hash.ToUpperInvariant()
$identificador = [string]$PID
$temporario = "/tmp/rasping_c2eccb_hba_$identificador.conf"
$estagio = ([string]$m.caminhoHbaOriginal) + ".c2eccb_stage_$identificador"
$usuarioGrupo = "$($m.uidOriginal):$($m.gidOriginal)"
$alteracaoIniciada = $false

try {
    # Primeiro copia a politica para um arquivo temporario, sem alterar HBA.
    & docker cp $proposta "${NomeContainer}:${temporario}"
    ExigirCodigoZero 'Falha ao copiar politica candidata para o container.'

    # Estagia no MESMO diretorio/volume do HBA para trocar por rename atomico.
    # O script sh recebe os caminhos como parametros, sem interpolacao no shell.
    & docker compose -f $CaminhoCompose exec -T -u root postgres sh -c 'set -eu; test -f "$1"; test ! -e "$2"; cp -- "$1" "$2"; chown "$3" "$2"; chmod "$4" "$2"' preparar $temporario $estagio $usuarioGrupo $m.modoOriginal
    ExigirCodigoZero 'Falha ao preparar arquivo HBA no diretorio do cluster.'

    $hashEstagio = ExtrairSha256 (& docker compose -f $CaminhoCompose exec -T postgres sha256sum $estagio)
    ExigirCodigoZero 'Falha ao conferir hash do HBA em estagio.'
    if ($hashEstagio -cne $hashProposta) {
        throw 'Arquivo HBA de estagio diverge da politica proposta.'
    }

    # Segunda checagem imediatamente anterior a troca: recusa drift.
    $hashAtual = ExtrairSha256 (& docker compose -f $CaminhoCompose exec -T postgres sha256sum $m.caminhoHbaOriginal)
    ExigirCodigoZero 'Falha ao verificar novamente o HBA original.'
    if ($hashAtual -cne $hashOriginal) {
        throw 'HBA original mudou entre precheck e estagio. Aplicacao bloqueada.'
    }

    # Daqui em diante qualquer falha aciona tentativa de restauracao.
    $alteracaoIniciada = $true
    & docker compose -f $CaminhoCompose exec -T -u root postgres sh -c 'set -eu; mv -f -- "$1" "$2"' substituir $estagio $m.caminhoHbaOriginal
    ExigirCodigoZero 'Falha na substituicao atomica do HBA.'

    $hashAplicado = ExtrairSha256 (& docker compose -f $CaminhoCompose exec -T postgres sha256sum $m.caminhoHbaOriginal)
    ExigirCodigoZero 'Falha no SHA-256 do HBA aplicado.'
    if ($hashAplicado -cne $hashProposta) {
        throw 'HBA ativo nao corresponde aos bytes da politica aprovada.'
    }

    # Reload sem SQL: continua disponivel mesmo se o novo HBA bloquear psql.
    & docker compose -f $CaminhoCompose exec -T -u postgres postgres sh -c 'pg_ctl -D "$PGDATA" reload'
    ExigirCodigoZero 'pg_ctl reload falhou.'

    # O sinal de reload e assincrono: testar conexoes NOVAS apos seu processamento.
    Start-Sleep -Seconds 2

    $destinoSql = '/tmp/FASE_21_C2_E_C_C_B_VERIFICAR_HBA.sql'
    $destinoShell = '/tmp/FASE_21_C2_E_C_C_B_TESTAR_AUTENTICACAO.sh'
    & docker cp $verificarSql "${NomeContainer}:${destinoSql}"
    ExigirCodigoZero 'Falha ao copiar validador SQL para o container.'
    & docker cp $testarShell "${NomeContainer}:${destinoShell}"
    ExigirCodigoZero 'Falha ao copiar teste de autenticacao para o container.'
    & docker compose -f $CaminhoCompose exec -T postgres sh $destinoShell
    ExigirCodigoZero 'HBA aplicado, mas validacao de autenticacao falhou.'

    Write-Host '=== C2-E-C-C-B APLICACAO PASS - HBA SCRAM ativo e autenticacao confirmada ==='
    Write-Host '=== BACKUP ORIGINAL PRESERVADO - roles e senhas inalteradas ==='
} catch {
    Write-Warning 'C2-E-C-C-B: aplicacao nao aprovada. Nao reinicie o container.'
    if ($alteracaoIniciada) {
        Write-Warning 'Iniciando recuperacao automatica do HBA pelo manifesto validado.'
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $reversao -CaminhoManifesto $CaminhoManifesto -CaminhoCompose $CaminhoCompose -ConfirmarRestauracao
        if ($LASTEXITCODE -ne 0) {
            Write-Warning 'RECUPERACAO NAO CONFIRMADA: mantenha o container e preserve backups; revisar manualmente.'
        } else {
            Write-Warning 'Restauracao solicitada; ainda e necessario verificar conexoes e regras efetivas.'
        }
    }
    throw 'C2-E-C-C-B FALHA: interromper operacao, preservar backups e enviar a saida para diagnostico.'
}
