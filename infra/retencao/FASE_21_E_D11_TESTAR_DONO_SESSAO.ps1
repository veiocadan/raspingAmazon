# FASE 21-E-D11: isolamento de diretorio e escrita com dois processos reais.
# Nao iniciar daemon, Java, supervisor; nao criar diretorio externo nem excluir.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$raiz = (Get-Location).ProviderPath
if ((& git branch --show-current).Trim() -cne 'feat/fase-21-seguranca-governanca-v1') {
    throw '21-E-D11: branch incorreta'
}
foreach ($arquivo in @(
    'infra/retencao/FASE_21_E_A_FUNCOES_RETENCAO.ps1',
    'infra/retencao/FASE_21_E_B1_FUNCOES_SEGMENTACAO.ps1',
    'infra/retencao/FASE_21_E_B8_FUNCOES_CATALOGO.ps1',
    'infra/retencao/FASE_21_E_D1_FUNCOES_PREINTEGRACAO.ps1',
    'infra/retencao/FASE_21_E_D2_FUNCOES_ESCRITOR_OFFLINE.ps1',
    'infra/retencao/FASE_21_E_D8_FUNCOES_PREPARACAO_EXTERNA.ps1',
    'infra/retencao/FASE_21_E_D9_FUNCOES_ADAPTADOR_ISOLADO.ps1',
    'infra/retencao/FASE_21_E_D10_FUNCOES_RESERVA_ATOMICA.ps1',
    'infra/retencao/FASE_21_E_D11_FUNCOES_DONO_SESSAO.ps1'
)) {
    if (-not (Test-Path -LiteralPath $arquivo -PathType Leaf)) {
        throw ('21-E-D11: dependencia ausente: ' + $arquivo)
    }
    . (Resolve-Path -LiteralPath $arquivo -ErrorAction Stop).ProviderPath
}
$catalogo = Assert-21ED9-Contexto -Raiz $raiz
if (@($catalogo.events).Count -ne 6) { throw '21-E-D11: catalogo inesperado' }
Write-Host 'PASS - Branch, catalogo e politicas preparation_only/inventory_only confirmados.'

function Exigir-D11 {
    param([bool]$Ok,[string]$Nome)
    if (-not $Ok) { throw ('21-E-D11: assercao reprovada: ' + $Nome) }
}
function Recusar-D11 {
    param([scriptblock]$Acao,[string]$Nome)
    $rejeitou = $false
    try { & $Acao | Out-Null } catch { $rejeitou = $true }
    Exigir-D11 $rejeitou $Nome
}

# O escritor D2 mantem o ultimo segmento aberto com FileShare.Read.
# Get-FileHash usa um compartilhamento de abertura incompatível com esse
# gravador em Windows. Abrir em modo somente leitura e compartilhar ReadWrite
# permite calcular SHA-256 sem fechar ou alterar o arquivo do filho.
function Obter-Hash-D11-LeituraCompartilhada {
    param([Parameter(Mandatory=$true)][string]$Caminho)
    if ([IO.Path]::GetFileName($Caminho) -cnotmatch '^operacional-[0-9]{6}\.jsonl$') {
        throw '21-E-D11: hash permitido somente para segmento offline'
    }
    Assert-21EB1-SemRedirecionamento -Caminho $Caminho
    $arquivo = Get-Item -LiteralPath $Caminho -ErrorAction Stop
    if ($arquivo.PSIsContainer) { throw '21-E-D11: segmento invalido' }
    $tamanhoAntes = [long]$arquivo.Length
    $dataAntes = [long]$arquivo.LastWriteTimeUtc.Ticks
    $fluxo = $null
    $calculador = $null
    try {
        $fluxo = [IO.FileStream]::new($Caminho,[IO.FileMode]::Open,
            [IO.FileAccess]::Read,[IO.FileShare]::ReadWrite)
        $calculador = [Security.Cryptography.SHA256]::Create()
        $bytesHash = $calculador.ComputeHash($fluxo)
    } finally {
        if ($null -ne $calculador) { $calculador.Dispose() }
        if ($null -ne $fluxo) { $fluxo.Dispose() }
    }
    $arquivo.Refresh()
    if ([long]$arquivo.Length -ne $tamanhoAntes -or
        [long]$arquivo.LastWriteTimeUtc.Ticks -ne $dataAntes) {
        throw '21-E-D11: metadados mudaram durante a leitura do hash'
    }
    return [BitConverter]::ToString($bytesHash).Replace('-','')
}
Recusar-D11 { New-21ED11-SessaoProdutiva -DiretorioExterno 'C:\dev\rasping-amazon-operacao\logs' } 'produtivo'
Recusar-D11 {
    New-21ED11-SessaoEnsaio -Raiz $raiz -Cenario (Join-Path $raiz 'infra') `
        -Nonce ([guid]::NewGuid().ToString('N')) -MaximoSegmentoBytes 256 -MaximoTotalBytes 1024
} 'caminho-fora-sandbox'

$cenario = New-21EB1-Sandbox -Raiz $raiz
Recusar-D11 {
    New-21ED11-SessaoEnsaio -Raiz $raiz -Cenario $cenario -Nonce 'INVALIDO' `
        -MaximoSegmentoBytes 256 -MaximoTotalBytes 1024
} 'nonce-invalido'
Exigir-D11 (@(Get-ChildItem -LiteralPath $cenario -Force).Count -eq 0) 'erro-preflight-nao-escreve'

$filho = (Resolve-Path -LiteralPath 'infra/retencao/FASE_21_E_D11_FILHO_ESCRITA.ps1').ProviderPath
$ps = (Get-Command powershell.exe -ErrorAction Stop).Source
foreach ($valor in @($filho,$ps,$raiz,$cenario)) {
    if ($valor.Contains('"') -or $valor.Contains("`n") -or $valor.Contains("`r")) {
        throw '21-E-D11: caminho inadequado para execucao protegida'
    }
}
function Iniciar-D11 {
    param([string]$Modo,[string]$Cenario,[string]$Nonce)
    if ($Modo -cnotin @('dono','concorrente','reuso','independente') -or
        $Nonce -cnotmatch '^[0-9a-f]{32}$') {
        throw '21-E-D11: parametros de fixture invalidos'
    }
    $psi = [Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $ps
    $psi.Arguments = '-NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "' +
        $filho + '" -Modo ' + $Modo + ' -Raiz "' + $raiz + '" -Cenario "' +
        $Cenario + '" -Nonce ' + $Nonce
    $psi.WorkingDirectory = $raiz
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    foreach ($nome in @($psi.EnvironmentVariables.Keys)) {
        if ($nome -match '(?i)(PASSWORD|TOKEN|SECRET|CREDENTIAL|API_KEY|AUTHORIZATION|^DB_|^PG|^AMAZON_|^TELEGRAM_|^WHATSAPP_|^RASPING_|^AWS_|^AZURE_|^GITHUB_|^JAVA_TOOL_OPTIONS$|^JDK_JAVA_OPTIONS$|^_JAVA_OPTIONS$)') {
            [void]$psi.EnvironmentVariables.Remove($nome)
        }
    }
    $p = [Diagnostics.Process]::Start($psi)
    if ($null -eq $p) { throw '21-E-D11: processo de teste nao iniciado' }
    return $p
}
function Conferir-D11 {
    param([Diagnostics.Process]$Processo,[int]$Codigo,[string]$Nome)
    if (-not $Processo.WaitForExit(19000)) {
        throw ('21-E-D11: filho pendente; inspecao manual: ' + $Nome)
    }
    $saida = $Processo.StandardOutput.ReadToEnd()
    $erro = $Processo.StandardError.ReadToEnd()
    Exigir-D11 ($Processo.ExitCode -eq $Codigo -and $saida.Length -eq 0 -and
        $erro.Length -eq 0) $Nome
}
$nonceDono = [guid]::NewGuid().ToString('N')
$pronto = Join-Path $cenario 'd11-pronto.marker'
$liberar = Join-Path $cenario 'd11-liberar.marker'
$marcadorDono = Join-Path $cenario 'd11-dono-diretorio.v1.json'
$filhos = [Collections.Generic.List[Diagnostics.Process]]::new()
try {
    $dono = Iniciar-D11 -Modo 'dono' -Cenario $cenario -Nonce $nonceDono
    $filhos.Add($dono)
    $limite = [DateTime]::UtcNow.AddSeconds(10)
    while (-not (Test-Path -LiteralPath $pronto -PathType Leaf)) {
        if ($dono.HasExited -or [DateTime]::UtcNow -ge $limite) {
            throw '21-E-D11: dono nao sinalizou prontidao'
        }
        [Threading.Thread]::Sleep(50)
    }
    Exigir-D11 ((Test-Path -LiteralPath $marcadorDono -PathType Leaf) -and
        -not $dono.HasExited) 'dono-vivo-reserva-fixa'
    $segmentos = @(Get-ChildItem -LiteralPath $cenario -File -Filter 'operacional-*.jsonl' | Sort-Object Name)
    Exigir-D11 ($segmentos.Count -eq 2) 'dois-segmentos'
    $eventos = @($segmentos | ForEach-Object { Get-Content -LiteralPath $_.FullName -Encoding UTF8 })
    Exigir-D11 ($eventos.Count -eq 2 -and
        (ConvertFrom-Json -InputObject $eventos[0]).event -ceq 'processing.job.claimed' -and
        (ConvertFrom-Json -InputObject $eventos[1]).event -ceq 'runtime.stderr.redacted') 'catalogo-e-marcador'
    # No Windows, o segmento mais recente pode permanecer aberto pelo dono.
    # A leitura e permitida pelo FileShare.Read do escritor D2; o calculador
    # compartilha tambem a escrita existente e NAO escreve no segmento.
    $hashAntes = @($segmentos | ForEach-Object {
        Obter-Hash-D11-LeituraCompartilhada -Caminho $_.FullName
    })
    $reservaNonce = Join-Path $cenario ('reserva-' + $nonceDono + '.v1.json')
    # O marcador D10 usa FileShare.None enquanto o processo e dono.
    # Verificar apenas a existencia agora; ler a identidade apos o fechamento.
    Exigir-D11 (Test-Path -LiteralPath $reservaNonce -PathType Leaf) 'd10-reserva-ativa'
    Write-Host 'PASS - Dono real gravou 2 eventos em 2 segmentos e manteve reserva fixa ativa.'

    $nonceAtaque = [guid]::NewGuid().ToString('N')
    if ($nonceAtaque -ceq $nonceDono) { throw '21-E-D11: nonce duplicado' }
    $concorrente = Iniciar-D11 -Modo 'concorrente' -Cenario $cenario -Nonce $nonceAtaque
    $filhos.Add($concorrente)
    Conferir-D11 -Processo $concorrente -Codigo 23 -Nome 'nonce-diferente-recusado'
    Exigir-D11 (-not $dono.HasExited) 'dono-nao-afetado'
    Exigir-D11 (@(Get-ChildItem -LiteralPath $cenario -File -Filter 'operacional-*.jsonl').Count -eq 2) 'sem-segmento-invasor'
    for ($i=0; $i -lt $segmentos.Count; $i++) {
        Exigir-D11 ((Obter-Hash-D11-LeituraCompartilhada -Caminho $segmentos[$i].FullName) -ceq
            $hashAntes[$i]) 'historico-sem-alteracao'
    }
    Write-Host 'PASS - Segundo processo com nonce DIFERENTE recusado; segmentos intactos.'

    Assert-21EB1-SemRedirecionamento -Caminho $liberar
    $f = [IO.FileStream]::new($liberar,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
    $f.Dispose()
    Conferir-D11 -Processo $dono -Codigo 0 -Nome 'fechamento-cooperativo'
    # Somente agora ambas as reservas exclusivas D10/D11 foram fechadas.
    # Conferir identidade original, sem tentar contornar FileShare.None.
    $identidade = Get-Content -LiteralPath $reservaNonce -Raw -Encoding UTF8 |
        ConvertFrom-Json -ErrorAction Stop
    Exigir-D11 ([long]$identidade.owner_pid -eq [long]$dono.Id -and
        [long]$identidade.owner_creation_utc_ticks -gt 0 -and
        $identidade.nonce -ceq $nonceDono) 'd10-pid-ticks-nonce'
    for ($i=0; $i -lt $segmentos.Count; $i++) {
        Exigir-D11 ((Get-FileHash -LiteralPath $segmentos[$i].FullName -Algorithm SHA256).Hash -ceq
            $hashAntes[$i]) 'historico-apos-fechamento'
    }
    Write-Host 'PASS - Identidade PID/ticks/nonce validada apos fechamento; hashes preservados.'
    $hashMarcador = (Get-FileHash -LiteralPath $marcadorDono -Algorithm SHA256).Hash
    $reuso = Iniciar-D11 -Modo 'reuso' -Cenario $cenario -Nonce ([guid]::NewGuid().ToString('N'))
    $filhos.Add($reuso)
    Conferir-D11 -Processo $reuso -Codigo 23 -Nome 'reuso-apos-fechamento'
    Exigir-D11 ((Get-FileHash -LiteralPath $marcadorDono -Algorithm SHA256).Hash -ceq $hashMarcador) 'reserva-persistente'
    Write-Host 'PASS - Fechamento nao liberou reuso; reserva fixa permaneceu imutavel.'

    $novoCenario = New-21EB1-Sandbox -Raiz $raiz
    $novo = Iniciar-D11 -Modo 'independente' -Cenario $novoCenario -Nonce ([guid]::NewGuid().ToString('N'))
    $filhos.Add($novo)
    Conferir-D11 -Processo $novo -Codigo 0 -Nome 'nova-sessao-independente'
    $novosSegmentos = @(Get-ChildItem -LiteralPath $novoCenario -File -Filter 'operacional-*.jsonl')
    Exigir-D11 ($novosSegmentos.Count -eq 2 -and $novoCenario -cne $cenario) 'sessao-independente'
    Write-Host 'PASS - Diretorio novo permitiu outra sessao sem reabrir o historico anterior.'
} finally {
    foreach ($p in $filhos) {
        if ($null -eq $p) { continue }
        try {
            if (-not $p.HasExited -and -not $p.WaitForExit(19000)) {
                throw '21-E-D11: processo ainda ativo; nao reiniciar sem inspecao'
            }
        } finally { $p.Dispose() }
    }
}
Write-Host 'PROTEGIDO - Somente processos PowerShell e diretorios privados sob target; sem Kill/taskkill.'
Write-Host 'PROTEGIDO - Sem Java, daemon, supervisor, banco, backups, rede, logs externos ou exclusoes.'
Write-Host 'PASS - Gate 21-E-D11 aprovado; adaptador produtivo NAO IMPLEMENTADO/BLOQUEADO.'
