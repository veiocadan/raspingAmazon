# FASE 21-E-D6: teste isolado da fronteira real PowerShell -> Java stop signal.
# Os unicos subprocessos permitidos sao JVMs executando a fixture DE TESTE.
# Nenhum daemon, banco, integracao externa, backup ou limpeza automatica.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$raiz = (Get-Location).ProviderPath
if ((& git branch --show-current).Trim() -cne 'feat/fase-21-seguranca-governanca-v1') {
    throw '21-E-D6: branch incorreta'
}

. (Join-Path $raiz 'infra/retencao/FASE_21_E_B1_FUNCOES_SEGMENTACAO.ps1')
if (-not (Assert-21EB1-Contexto -Raiz $raiz)) {
    throw '21-E-D6: gates preparation_only/inventory_only indisponiveis'
}
$fonteJava = 'src/main/java/com/raspingamazon/infrastructure/bootstrap/SupervisedStopSignal.java'
& git diff --quiet -- $fonteJava
if ($LASTEXITCODE -ne 0) { throw '21-E-D6: fonte principal Java foi alterada' }
& git diff --cached --quiet -- $fonteJava
if ($LASTEXITCODE -ne 0) { throw '21-E-D6: fonte Java tem alteracao staged' }

$classe = Join-Path $raiz 'target/test-classes/com/raspingamazon/infrastructure/bootstrap/Phase21EJavaStopProbeMain.class'
if (-not (Test-Path -LiteralPath $classe -PathType Leaf)) {
    throw '21-E-D6: fixture nao compilada; executar mvnw.cmd test-compile primeiro'
}
$java = (Get-Command java.exe -ErrorAction Stop).Source
$classpath = (Join-Path $raiz 'target/test-classes') + ';' + (Join-Path $raiz 'target/classes')
foreach ($caminho in @($java,$classpath,$raiz)) {
    if ($caminho.Contains('"')) { throw '21-E-D6: caminho com aspas recusado' }
}
Write-Host 'PASS - Contratos, branch e fonte Java original confirmados.'

function New-21ED6-FilhoJava {
    param(
        [Parameter(Mandatory=$true)][string]$Diretorio,
        [ValidateRange(1500,5000)][int]$TimeoutMilissegundos = 3000
    )
    [void](Assert-21EB1-Contexto -Raiz $raiz)
    Assert-21EB1-SemRedirecionamento -Caminho $Diretorio
    Assert-21EB1-ACLPrivada -Diretorio $Diretorio
    $request = Join-Path $Diretorio 'java-d6-stop.request'
    $ready = Join-Path $Diretorio 'java-d6-ready.marker'
    foreach ($caminho in @($request,$ready)) {
        if ($caminho.Contains('"')) { throw '21-E-D6: argumento de caminho invalido' }
    }
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $java
    $psi.Arguments = '-cp "' + $classpath + '" com.raspingamazon.infrastructure.bootstrap.Phase21EJavaStopProbeMain "' + $request + '" "' + $ready + '" ' + $TimeoutMilissegundos
    $psi.WorkingDirectory = $raiz
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    foreach ($nome in @($psi.EnvironmentVariables.Keys)) {
        if ($nome -match '(?i)(PASSWORD|TOKEN|SECRET|CREDENTIAL|API_KEY|AUTHORIZATION|^DB_|^PG|^AMAZON_|^TELEGRAM_|^WHATSAPP_|^RASPING_|^AWS_|^AZURE_|^GITHUB_)') {
            [void]$psi.EnvironmentVariables.Remove($nome)
        }
    }
    $processo = [System.Diagnostics.Process]::Start($psi)
    if ($null -eq $processo) { throw '21-E-D6: filho JVM de teste nao iniciado' }
    return [pscustomobject]@{ Processo=$processo; Pedido=$request; Pronto=$ready; Diretorio=$Diretorio }
}

function Wait-21ED6-Terminacao {
    param(
        [Parameter(Mandatory=$true)][object]$Filho,
        [Parameter(Mandatory=$true)][int]$CodigoEsperado
    )
    $processo = $Filho.Processo
    if (-not $processo.WaitForExit(9000)) {
        throw '21-E-D6: JVM de teste nao terminou no prazo; inspecao manual obrigatoria'
    }
    if ($processo.ExitCode -ne $CodigoEsperado) {
        throw ('21-E-D6: codigo de saida inesperado: ' + $processo.ExitCode)
    }
    # Nenhuma saida bruta e exibida ou persistida.
    if ($processo.StandardOutput.ReadToEnd().Length -gt 0 -or
        $processo.StandardError.ReadToEnd().Length -gt 0) {
        throw '21-E-D6: fixture emitiu saida inesperada; revisar sem mostrar conteudo'
    }
}

function New-21ED6-PedidoExclusivo {
    param([Parameter(Mandatory=$true)][string]$Caminho)
    Assert-21EB1-SemRedirecionamento -Caminho $Caminho
    $stream = [System.IO.FileStream]::new(
        $Caminho,[System.IO.FileMode]::CreateNew,
        [System.IO.FileAccess]::Write,[System.IO.FileShare]::None
    )
    $stream.Dispose()
}

function Close-21ED6-Filho {
    param([Parameter(Mandatory=$true)][object]$Filho)
    try {
        if (-not $Filho.Processo.HasExited) {
            # A fixture tem prazo proprio de no maximo 5s. Nao usar Kill/taskkill.
            if (-not $Filho.Processo.WaitForExit(9000)) {
                throw '21-E-D6: filho JVM pendente; examinar manualmente antes de prosseguir'
            }
        }
    } finally { $Filho.Processo.Dispose() }
}

# Cenario 1: parada cooperativa real pela classe Java, sem daemon.
$dir1 = New-21EB1-Sandbox -Raiz $raiz
$filho1 = New-21ED6-FilhoJava -Diretorio $dir1 -TimeoutMilissegundos 5000
try {
    $pronto = $false
    $prazo = [DateTime]::UtcNow.AddSeconds(7)
    while ([DateTime]::UtcNow -lt $prazo) {
        if (Test-Path -LiteralPath $filho1.Pronto -PathType Leaf) {
            $pronto = $true
            break
        }
        if ($filho1.Processo.HasExited) { break }
        [System.Threading.Thread]::Sleep(50)
    }
    if (-not $pronto -or $filho1.Processo.HasExited) {
        throw '21-E-D6: fixture Java nao anunciou readiness'
    }
    if (Test-Path -LiteralPath $filho1.Pedido) { throw '21-E-D6: pedido espurio' }
    New-21ED6-PedidoExclusivo -Caminho $filho1.Pedido
    Wait-21ED6-Terminacao -Filho $filho1 -CodigoEsperado 0
    if (-not (Test-Path -LiteralPath $filho1.Pedido -PathType Leaf)) {
        throw '21-E-D6: pedido de parada desapareceu'
    }
    Write-Host 'PASS - JVM de teste recebeu pedido PowerShell e encerrou cooperativamente (codigo 0).'
} finally { Close-21ED6-Filho -Filho $filho1 }

# Cenario 2: sem pedido, a fixture encerra por seu proprio timeout.
$dir2 = New-21EB1-Sandbox -Raiz $raiz
$filho2 = New-21ED6-FilhoJava -Diretorio $dir2 -TimeoutMilissegundos 1500
try {
    Wait-21ED6-Terminacao -Filho $filho2 -CodigoEsperado 75
    if (Test-Path -LiteralPath $filho2.Pedido) {
        throw '21-E-D6: arquivo de parada criado sem autorizacao'
    }
    Write-Host 'PASS - JVM sem pedido expirou naturalmente; nenhum arquivo de parada criado.'
} finally { Close-21ED6-Filho -Filho $filho2 }

# Cenario 3: pedido ja existente deve bloquear o inicio da ponte Java.
$dir3 = New-21EB1-Sandbox -Raiz $raiz
$pedido3 = Join-Path $dir3 'java-d6-stop.request'
New-21ED6-PedidoExclusivo -Caminho $pedido3
$filho3 = New-21ED6-FilhoJava -Diretorio $dir3 -TimeoutMilissegundos 1500
try {
    Wait-21ED6-Terminacao -Filho $filho3 -CodigoEsperado 76
    if (Test-Path -LiteralPath $filho3.Pronto) {
        throw '21-E-D6: fixture com pedido anterior criou readiness'
    }
    Write-Host 'PASS - JVM recusou pedido preexistente antes de readiness.'
} finally { Close-21ED6-Filho -Filho $filho3 }

Write-Host 'PROTEGIDO - Apenas JVM fixture de teste; sem ContinuousProcessingMain, JDBC, Java daemon, supervisor ou logs reais.'
Write-Host 'PROTEGIDO - Sem Kill, taskkill ou exclusoes; evidencias restritas a target e preservadas.'
Write-Host 'PASS - Gate 21-E-D6 offline aprovado; integracao produtiva continua bloqueada.'
