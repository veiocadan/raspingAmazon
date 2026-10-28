# FASE 21-E-D4 - ensaio de sinalizacao de parada cooperativa, exclusivamente sintetico.
# Importar nao executa processos, nao cria diretorios, nao escreve e nao inicia Java.
Set-StrictMode -Version Latest

function Assert-21ED4-Contexto {
    param([Parameter(Mandatory=$true)][string]$Raiz)
    $catalogo = Assert-21ED2-Contexto -Raiz $Raiz
    $supervisor = Join-Path $Raiz 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_C_INICIAR_SUPERVISIONADO.ps1'
    $sinalJava = Join-Path $Raiz 'src/main/java/com/raspingamazon/infrastructure/bootstrap/SupervisedStopSignal.java'
    if (-not (Test-Path -LiteralPath $supervisor -PathType Leaf) -or
        -not (Test-Path -LiteralPath $sinalJava -PathType Leaf)) {
        throw '21-E-D4: fontes de supervisao ausentes'
    }
    $fonteSupervisor = Get-Content -LiteralPath $supervisor -Raw -Encoding UTF8
    $fonteJava = Get-Content -LiteralPath $sinalJava -Raw -Encoding UTF8
    if ($fonteSupervisor -notmatch 'Exigir-PartidaAutorizada-C-C' -or
        $fonteSupervisor -notmatch 'RASPING_SUPERVISED_STOP_REQUEST_FILE' -or
        $fonteJava -notmatch 'RASPING_SUPERVISED_STOP_REQUEST_FILE' -or
        $fonteJava -notmatch 'POLL_MILLIS') {
        throw '21-E-D4: contrato de parada supervisionada nao reconhecido'
    }
    return $catalogo
}

function Assert-21ED4-CaminhoSinal {
    param(
        [Parameter(Mandatory=$true)][string]$Diretorio,
        [Parameter(Mandatory=$true)][string]$Caminho
    )
    $diretorioSeguro = Assert-21ED2-DiretorioSandbox -Diretorio $Diretorio
    $esperado = Join-Path $diretorioSeguro 'parada-sintetica-d4.request'
    $atual = [System.IO.Path]::GetFullPath($Caminho)
    if (-not $atual.Equals($esperado,[System.StringComparison]::OrdinalIgnoreCase)) {
        throw '21-E-D4: pedido de parada fora do sandbox'
    }
    Assert-21EB1-SemRedirecionamento -Caminho $atual
    return $atual
}

function New-21ED4-PedidoParada {
    param([Parameter(Mandatory=$true)][object]$Sessao)
    [void](Assert-21ED4-Contexto -Raiz (Get-Location).ProviderPath)
    if (-not $Sessao.Lacrada) {
        throw '21-E-D4: parada por falha exige sessao ja lacrada'
    }
    $caminho = Assert-21ED4-CaminhoSinal -Diretorio $Sessao.Diretorio `
        -Caminho (Join-Path $Sessao.Diretorio 'parada-sintetica-d4.request')
    if (Test-Path -LiteralPath $caminho) {
        throw '21-E-D4: pedido preexistente; nao substituir'
    }
    $bytes = [System.Text.Encoding]::ASCII.GetBytes("PARADA_COOPERATIVA_SINTETICA_D4`n")
    $fluxo = [System.IO.FileStream]::new($caminho,
        [System.IO.FileMode]::CreateNew,[System.IO.FileAccess]::Write,
        [System.IO.FileShare]::None)
    try {
        $fluxo.Write($bytes,0,$bytes.Length)
        $fluxo.Flush($true)
    } finally {
        $fluxo.Dispose()
    }
    return $caminho
}

function New-21ED4-CodigoFilho {
    param(
        [Parameter(Mandatory=$true)][string]$Diretorio,
        [ValidateRange(2,15)][int]$PrazoVidaSegundos = 10
    )
    $dir = Assert-21ED2-DiretorioSandbox -Diretorio $Diretorio
    $sinal = (Join-Path $dir 'parada-sintetica-d4.request').Replace("'","''")
    $pronto = (Join-Path $dir 'filho-d4-pronto.marker').Replace("'","''")
    # Apenas os dois caminhos de um mesmo cenario privado sao passados ao filho.
    $codigo = @'
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$sinal = '__SINAL__'
$pronto = '__PRONTO__'
$fluxo = [System.IO.FileStream]::new($pronto,[System.IO.FileMode]::CreateNew,[System.IO.FileAccess]::Write,[System.IO.FileShare]::None)
$fluxo.Dispose()
$termino = [DateTime]::UtcNow.AddSeconds(__PRAZO__)
while ([DateTime]::UtcNow -lt $termino) {
    if (Test-Path -LiteralPath $sinal) {
        $obj = Get-Item -LiteralPath $sinal -Force -ErrorAction Stop
        if ($obj.PSIsContainer -or ($obj.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) { exit 76 }
        if (-not [System.IO.File]::Exists($sinal)) { exit 76 }
        exit 0
    }
    [System.Threading.Thread]::Sleep(100)
}
exit 75
'@
    return $codigo.Replace('__SINAL__',$sinal).Replace('__PRONTO__',$pronto).Replace('__PRAZO__',[string]$PrazoVidaSegundos)
}

function Start-21ED4-FilhoSintetico {
    param(
        [Parameter(Mandatory=$true)][string]$Diretorio,
        [ValidateRange(2,15)][int]$PrazoVidaSegundos = 10
    )
    [void](Assert-21ED4-Contexto -Raiz (Get-Location).ProviderPath)
    $dir = Assert-21ED2-DiretorioSandbox -Diretorio $Diretorio
    $exe = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
    if (-not (Test-Path -LiteralPath $exe -PathType Leaf)) {
        throw '21-E-D4: PowerShell Windows indisponivel'
    }
    $codigo = New-21ED4-CodigoFilho -Diretorio $dir -PrazoVidaSegundos $PrazoVidaSegundos
    $arquivo = New-21EB9-ArquivoFilhoSintetico -CodigoFilho $codigo `
        -SessaoStdout ([pscustomobject]@{ Diretorio=$dir })
    if ($arquivo.Contains('"')) { throw '21-E-D4: caminho do filho invalido' }
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $exe
    $psi.Arguments = '-NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -OutputFormat Text -File "' + $arquivo + '"'
    $psi.WorkingDirectory = (Get-Location).ProviderPath
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    # Nenhuma saida do filho contem dados reais; nao ha captura produtiva.
    $psi.RedirectStandardOutput = $false
    $psi.RedirectStandardError = $false
    foreach ($chave in @($psi.EnvironmentVariables.Keys)) {
        if ($chave -match '(?i)(PASSWORD|TOKEN|SECRET|CREDENTIAL|API_KEY|AUTHORIZATION|^DB_|^PG|^AMAZON_|^TELEGRAM_|^WHATSAPP_|^RASPING_|^AWS_|^AZURE_|^GITHUB_)') {
            [void]$psi.EnvironmentVariables.Remove($chave)
        }
    }
    $processo = New-Object System.Diagnostics.Process
    $processo.StartInfo = $psi
    try {
        if (-not $processo.Start()) { throw '21-E-D4: falha ao iniciar filho sintetico' }
        return $processo
    } catch {
        $processo.Dispose()
        throw '21-E-D4: filho sintetico nao iniciado'
    }
}

function Wait-21ED4-FilhoPronto {
    param([Parameter(Mandatory=$true)][object]$Processo,
        [Parameter(Mandatory=$true)][string]$Diretorio,
        [ValidateRange(1,8)][int]$PrazoSegundos = 6)
    $dir = Assert-21ED2-DiretorioSandbox -Diretorio $Diretorio
    $pronto = Join-Path $dir 'filho-d4-pronto.marker'
    $limite = [DateTime]::UtcNow.AddSeconds($PrazoSegundos)
    while ([DateTime]::UtcNow -lt $limite) {
        if (Test-Path -LiteralPath $pronto -PathType Leaf) {
            Assert-21EB1-SemRedirecionamento -Caminho $pronto
            return
        }
        if ($Processo.HasExited) { throw '21-E-D4: filho terminou antes de sinalizar pronto' }
        [System.Threading.Thread]::Sleep(100)
    }
    throw '21-E-D4: filho nao confirmou prontidao'
}

function Wait-21ED4-EncerramentoNatural {
    param([Parameter(Mandatory=$true)][object]$Processo,
        [ValidateRange(1,20)][int]$PrazoSegundos = 12)
    if (-not $Processo.WaitForExit($PrazoSegundos * 1000)) {
        # Nunca forcar Kill/taskkill: se falhar, exige inspecao manual.
        throw '21-E-D4: filho sintetico nao terminou; inspecao manual obrigatoria'
    }
    return [int]$Processo.ExitCode
}
