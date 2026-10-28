# FASE 21-E-D10: teste REAL de disputa de reserva entre processos PowerShell.
# Somente arquivos privados target/; nenhuma exclusao ou acionamento produtivo.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$raiz = (Get-Location).ProviderPath
if ((& git branch --show-current).Trim() -cne 'feat/fase-21-seguranca-governanca-v1') {
    throw '21-E-D10: branch incorreta'
}
foreach ($arquivo in @(
    'infra/retencao/FASE_21_E_A_FUNCOES_RETENCAO.ps1',
    'infra/retencao/FASE_21_E_B1_FUNCOES_SEGMENTACAO.ps1',
    'infra/retencao/FASE_21_E_B8_FUNCOES_CATALOGO.ps1',
    'infra/retencao/FASE_21_E_D1_FUNCOES_PREINTEGRACAO.ps1',
    'infra/retencao/FASE_21_E_D2_FUNCOES_ESCRITOR_OFFLINE.ps1',
    'infra/retencao/FASE_21_E_D8_FUNCOES_PREPARACAO_EXTERNA.ps1',
    'infra/retencao/FASE_21_E_D9_FUNCOES_ADAPTADOR_ISOLADO.ps1',
    'infra/retencao/FASE_21_E_D10_FUNCOES_RESERVA_ATOMICA.ps1'
)) {
    if (-not (Test-Path -LiteralPath $arquivo -PathType Leaf)) {
        throw ('21-E-D10: dependencia ausente: ' + $arquivo)
    }
    . (Resolve-Path -LiteralPath $arquivo -ErrorAction Stop).ProviderPath
}
$catalogo = Assert-21ED9-Contexto -Raiz $raiz
if (@($catalogo.events).Count -ne 6) { throw '21-E-D10: catalogo inesperado' }
Write-Host 'PASS - Branch, politicas, catalogo B8, D1, D8 e D9 validados.'

function Exigir-D10 {
    param([bool]$Ok,[string]$Nome)
    if (-not $Ok) { throw ('21-E-D10: assercao reprovada: ' + $Nome) }
}
function Recusar-D10 {
    param([scriptblock]$Acao,[string]$Nome)
    $recusou = $false
    try { & $Acao | Out-Null } catch { $recusou = $true }
    Exigir-D10 $recusou $Nome
}
Recusar-D10 { New-21ED10-ReservaProdutiva -DiretorioExterno 'C:\dev\rasping-amazon-operacao\logs' } 'produtivo-bloqueado'
Recusar-D10 {
    New-21ED10-ReservaAtomicaEnsaio -Raiz $raiz -Cenario (Join-Path $raiz 'infra') -Nonce ([guid]::NewGuid().ToString('N'))
} 'nao-sandbox'
$cenario = New-21EB1-Sandbox -Raiz $raiz
Recusar-D10 { New-21ED10-ReservaAtomicaEnsaio -Raiz $raiz -Cenario $cenario -Nonce 'AAAA' } 'nonce-invalido'

$filho = (Resolve-Path -LiteralPath 'infra/retencao/FASE_21_E_D10_FILHO_DISPUTA.ps1').ProviderPath
$ps = (Get-Command powershell.exe -ErrorAction Stop).Source
foreach ($valor in @($filho,$ps,$raiz,$cenario)) {
    if ($valor.Contains('"') -or $valor.Contains("`n") -or $valor.Contains("`r")) {
        throw '21-E-D10: caminho inviavel para argumento seguro'
    }
}
function Iniciar-D10-Filho {
    param([string]$Modo,[string]$Nonce)
    if ($Modo -cnotin @('dono','concorrente','reuso','independente') -or
        $Nonce -cnotmatch '^[0-9a-f]{32}$') {
        throw '21-E-D10: parametros de fixture invalidos'
    }
    $psi = New-Object Diagnostics.ProcessStartInfo
    $psi.FileName = $ps
    $psi.Arguments = '-NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "' +
        $filho + '" -Modo ' + $Modo + ' -Raiz "' + $raiz + '" -Cenario "' +
        $cenario + '" -Nonce ' + $Nonce
    $psi.WorkingDirectory = $raiz
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    # Nao passar valores de integracoes ou credenciais aos filhos.
    foreach ($nome in @($psi.EnvironmentVariables.Keys)) {
        if ($nome -match '(?i)(PASSWORD|TOKEN|SECRET|CREDENTIAL|API_KEY|AUTHORIZATION|^DB_|^PG|^AMAZON_|^TELEGRAM_|^WHATSAPP_|^RASPING_|^AWS_|^AZURE_|^GITHUB_|^JAVA_TOOL_OPTIONS$|^JDK_JAVA_OPTIONS$|^_JAVA_OPTIONS$)') {
            [void]$psi.EnvironmentVariables.Remove($nome)
        }
    }
    $p = [Diagnostics.Process]::Start($psi)
    if ($null -eq $p) { throw '21-E-D10: filho sintetico nao iniciado' }
    return $p
}
function Aguardar-D10-Codigo {
    param([Diagnostics.Process]$Processo,[int]$Esperado,[string]$Nome)
    if (-not $Processo.WaitForExit(18000)) {
        throw ('21-E-D10: filho nao terminou, inspecao manual necessaria: ' + $Nome)
    }
    $out = $Processo.StandardOutput.ReadToEnd()
    $err = $Processo.StandardError.ReadToEnd()
    Exigir-D10 ($Processo.ExitCode -eq $Esperado -and $out.Length -eq 0 -and $err.Length -eq 0) $Nome
}
$nonce = [guid]::NewGuid().ToString('N')
$pronto = Join-Path $cenario 'd10-pronto.marker'
$liberar = Join-Path $cenario 'd10-liberar.marker'
$reserva = Join-Path $cenario ('reserva-' + $nonce + '.v1.json')
$dono = $null
$filhos = New-Object 'System.Collections.Generic.List[System.Diagnostics.Process]'
try {
    $dono = Iniciar-D10-Filho -Modo dono -Nonce $nonce
    $filhos.Add($dono)
    $prazo = [DateTime]::UtcNow.AddSeconds(9)
    while (-not (Test-Path -LiteralPath $pronto -PathType Leaf)) {
        if ($dono.HasExited -or [DateTime]::UtcNow -ge $prazo) {
            throw '21-E-D10: dono nao sinalizou prontidao'
        }
        [Threading.Thread]::Sleep(40)
    }
    Exigir-D10 ((Test-Path -LiteralPath $reserva -PathType Leaf) -and -not $dono.HasExited) 'dono-vivo-e-reserva'
    $concorrente = Iniciar-D10-Filho -Modo concorrente -Nonce $nonce
    $filhos.Add($concorrente)
    Aguardar-D10-Codigo -Processo $concorrente -Esperado 23 -Nome 'rejeicao-em-concorrencia'
    Exigir-D10 (-not $dono.HasExited) 'dono-sobreviveu-ao-concorrente'
    Write-Host 'PASS - Dois processos reais disputaram a mesma reserva; somente o dono obteve CreateNew.'

    Assert-21EB1-SemRedirecionamento -Caminho $liberar
    $s = [IO.FileStream]::new($liberar,[IO.FileMode]::CreateNew,
        [IO.FileAccess]::Write,[IO.FileShare]::None)
    $s.Dispose()
    Aguardar-D10-Codigo -Processo $dono -Esperado 0 -Nome 'dono-fechou-naturalmente'
    $metadados = Get-Content -LiteralPath $reserva -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
    Exigir-D10 ($metadados.schema_version -eq 1 -and
        $metadados.mode -ceq 'offline_sandbox_only' -and
        [long]$metadados.owner_pid -eq [long]$dono.Id -and
        [long]$metadados.owner_creation_utc_ticks -gt 0 -and
        $metadados.nonce -ceq $nonce) 'identidade-real-persistida'
    $hashAntes = (Get-FileHash -LiteralPath $reserva -Algorithm SHA256).Hash
    $reuso = Iniciar-D10-Filho -Modo reuso -Nonce $nonce
    $filhos.Add($reuso)
    Aguardar-D10-Codigo -Processo $reuso -Esperado 23 -Nome 'reuso-apos-encerramento'
    Exigir-D10 ((Get-FileHash -LiteralPath $reserva -Algorithm SHA256).Hash -ceq $hashAntes) 'reserva-original-intacta'
    Write-Host 'PASS - Reserva persistiu apos fechamento; reuso recusado e hash original preservado.'

    $novoNonce = [guid]::NewGuid().ToString('N')
    if ($novoNonce -ceq $nonce) { throw '21-E-D10: nonce novo duplicado' }
    $novo = Iniciar-D10-Filho -Modo independente -Nonce $novoNonce
    $filhos.Add($novo)
    Aguardar-D10-Codigo -Processo $novo -Esperado 0 -Nome 'nova-reserva-independente'
    $novoCaminho = Join-Path $cenario ('reserva-' + $novoNonce + '.v1.json')
    $novoObjeto = Get-Content -LiteralPath $novoCaminho -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
    Exigir-D10 ($novoObjeto.nonce -ceq $novoNonce -and
        [long]$novoObjeto.owner_pid -eq [long]$novo.Id -and
        (Get-FileHash -LiteralPath $reserva -Algorithm SHA256).Hash -ceq $hashAntes) 'independencia-de-sessao'
    Write-Host 'PASS - Outro processo criou reserva independente com nonce diferente, sem alterar historico.'
} finally {
    foreach ($p in $filhos) {
        if ($null -eq $p) { continue }
        try {
            # Nenhuma terminacao forcada. O dono tem limite proprio de 12 segundos.
            if (-not $p.HasExited -and -not $p.WaitForExit(17000)) {
                throw '21-E-D10: filho pendente; investigar sem iniciar outro'
            }
        } finally { $p.Dispose() }
    }
}
Write-Host 'PASS - Tentativas de caminho externo, nonce invalido e abertura produtiva rejeitadas.'
Write-Host 'PROTEGIDO - Apenas processos PowerShell filhos e reservas privadas sob target; sem Kill/taskkill.'
Write-Host 'PROTEGIDO - Sem Java, supervisor, banco, backups, rede, logs externos ou exclusoes.'
Write-Host 'PASS - Gate 21-E-D10 concluido; adaptador produtivo permanece NAO IMPLEMENTADO/BLOQUEADO.'
