# FASE 21-E-C2 - duas fotografias somente-leitura dos escopos permitidos.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..') -ErrorAction Stop).ProviderPath
Set-Location -LiteralPath $repo
. (Join-Path $PSScriptRoot 'FASE_21_E_A_FUNCOES_RETENCAO.ps1')
. (Join-Path $PSScriptRoot 'FASE_21_E_C1_FUNCOES_SIMULACAO.ps1')
. (Join-Path $PSScriptRoot 'FASE_21_E_C2_FUNCOES_ESTABILIDADE.ps1')

$politica = Get-Content 'config/operacao/retention-policy.v1.json' -Raw -Encoding UTF8 | ConvertFrom-Json
$startup = Get-Content 'config/operacao/continuous-startup-policy.v1.json' -Raw -Encoding UTF8 | ConvertFrom-Json
Assert-21EA-Politica -Politica $politica
Assert-21EC1-StartupBloqueado -Plano $startup
$agora = [DateTime]::UtcNow
Write-Host 'PASS - Politicas inventory_only e preparation_only validadas.'
Write-Host ('INSTANTE_REFERENCIA_UTC=' + $agora.ToString('o'))
foreach ($regra in $politica.local_rules) {
    $relativo = $regra.relative_path.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
    $diretorio = [System.IO.Path]::GetFullPath((Join-Path $repo $relativo))
    $foto1 = Get-21EC2-FotoEscopo -RaizRepositorio $repo -Regra $regra
    $foto2 = Get-21EC2-FotoEscopo -RaizRepositorio $repo -Regra $regra
    Assert-21EC2-FotosEstaveis -Primeira $foto1 -Segunda $foto2 -DiretorioEscopo $diretorio
    $resumo = Get-21EC2-ResumoFoto -Foto $foto2 -AgoraUtc $agora -PrazoDias 7
    $c1 = Get-21EC1-ResumoEscopo -RaizRepositorio $repo -Regra $regra -AgoraUtc $agora
    if ($resumo.escopo -cne $c1.escopo -or $resumo.estado -cne $c1.estado -or
        $resumo.arquivos -ne $c1.arquivos -or $resumo.bytes -ne $c1.bytes -or
        $resumo.candidatos -ne $c1.candidatos -or
        $resumo.bytes_candidatos -ne $c1.bytes_candidatos -or
        $resumo.redirecionamentos_ignorados -ne $c1.redirecionamentos_ignorados) {
        throw '21-E-C2: inventario mudou durante a verificacao; nenhuma acao permitida'
    }
    Write-Host ('ESCOPO={0}; ESTADO={1}; ARQUIVOS={2}; BYTES={3}; CANDIDATOS_SIMULADOS={4}; BYTES_CANDIDATOS={5}; REDIRECIONAMENTOS_IGNORADOS={6}; FOTOGRAFIAS=COINCIDEM' -f `
        $resumo.escopo,$resumo.estado,$resumo.arquivos,$resumo.bytes,$resumo.candidatos,
        $resumo.bytes_candidatos,$resumo.redirecionamentos_ignorados)
    $foto1 = $null
    $foto2 = $null
}
Write-Host 'PROTEGIDO - Caminhos individuais mantidos apenas em memoria; nada exportado.'
Write-Host 'PROTEGIDO - Nenhuma consulta a logs externos, PostgreSQL, supervisao, backups ou credenciais.'
Write-Host 'PASS - Dupla leitura de metadados concluida; ZERO exclusoes, gravacoes ou rotacoes.'
