# FASE 21-C2-E-D-D-C-B - Politica explicita de ALERTS em sessao isolada.
# Nao escolhe limiares pelo usuario. Nao escreve .env e nao toca em segredos DPAPI.
# Executa exclusivamente o gate de leitura existente apos aprovacao interativa.
param(
    [string]$CaminhoExecutor = 'infra/postgres/security/FASE_21_C2_E_D_D_C_B_EXECUTAR_CLI_DPAPI.ps1'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$nomes = @(
    'ALERT_REPEATED_EXTERNAL_FAILURE_THRESHOLD',
    'ALERT_REPEATED_EXTERNAL_FAILURE_WINDOW_SECONDS',
    'ALERT_SUSPICIOUS_COLLECTION_LOOKBACK_RUNS',
    'ALERT_SUSPICIOUS_COLLECTION_DROP_FRACTION',
    'ALERT_SUSPICIOUS_COLLECTION_MINIMUM_BASELINE_CANDIDATES'
)

function LerInteiroValidado {
    param(
        [string]$Nome,
        [string]$Explicacao,
        [long]$Minimo,
        [long]$Maximo
    )
    for ($tentativa = 1; $tentativa -le 3; $tentativa++) {
        Write-Host ('Parametro: ' + $Nome)
        Write-Host ('Descricao: ' + $Explicacao)
        $bruto = Read-Host 'Informe o inteiro escolhido, sem separador de milhar'
        if ($null -eq $bruto) { throw 'Entrada interativa indisponivel; nada foi executado.' }
        $bruto = $bruto.Trim()
        $numero = [long]0
        if ($bruto -cmatch '^[0-9]+$' -and
            [long]::TryParse($bruto,
                [System.Globalization.NumberStyles]::None,
                [System.Globalization.CultureInfo]::InvariantCulture,
                [ref]$numero) -and
            $numero -ge $Minimo -and $numero -le $Maximo) {
            return $numero.ToString([System.Globalization.CultureInfo]::InvariantCulture)
        }
        Write-Warning ('Valor invalido. Deve ser um inteiro entre ' + $Minimo + ' e ' + $Maximo + '.')
    }
    throw ('Limite de tentativas excedido: ' + $Nome + '. Nenhuma CLI foi executada.')
}

function LerFracaoValidada {
    Write-Host 'Parametro: ALERT_SUSPICIOUS_COLLECTION_DROP_FRACTION'
    Write-Host 'Descricao: fracao de queda suspeita, estritamente maior que zero e menor que um.'
    Write-Host 'Use ponto ou virgula decimal; o Java recebera sempre ponto decimal.'
    for ($tentativa = 1; $tentativa -le 3; $tentativa++) {
        $bruto = Read-Host 'Informe a fracao escolhida (sem porcentagem e sem simbolo %)'
        if ($null -eq $bruto) { throw 'Entrada interativa indisponivel; nada foi executado.' }
        $normalizado = $bruto.Trim().Replace(',', '.')
        $numero = [decimal]0
        if ($normalizado -cmatch '^0\.[0-9]+$' -and
            [decimal]::TryParse($normalizado,
                [System.Globalization.NumberStyles]::AllowDecimalPoint,
                [System.Globalization.CultureInfo]::InvariantCulture,
                [ref]$numero) -and
            $numero -gt [decimal]0 -and $numero -lt [decimal]1) {
            return $normalizado
        }
        Write-Warning 'Fracao invalida. O valor deve estar estritamente entre 0 e 1.'
    }
    throw 'Limite de tentativas excedido: fracao. Nenhuma CLI foi executada.'
}

if (-not (Test-Path -LiteralPath 'pom.xml' -PathType Leaf)) {
    throw 'Execute este script na raiz C:\dev\rasping-amazon.'
}
if (-not (Test-Path -LiteralPath $CaminhoExecutor -PathType Leaf)) {
    throw 'Executor original nao encontrado. Preserve o ZIP C2-E-D-D-C-B ja instalado.'
}
if (-not (Test-Path -LiteralPath 'infra/postgres/security/FASE_21_C2_E_D_D_C_B_PRECHECK_CLI.ps1' -PathType Leaf)) {
    throw 'Precheck original nao encontrado. Nao executar a CLI.'
}
if ($null -eq (Get-Command powershell.exe -ErrorAction SilentlyContinue)) {
    throw 'powershell.exe nao localizado.'
}

Write-Host '=== C2-E-D-D-C-B POLITICA - nenhuma configuracao de alerta definida pelas fontes ==='
Write-Host 'Informe deliberadamente cada valor operacional. Nao existem defaults neste gate.'
Write-Host 'A politica so sera aplicada a processos filhos deste teste; nao sera gravada em .env.'
Write-Host 'Se nao puder decidir os cinco valores, pressione Ctrl+C antes de confirmar.'

$valores = @{}
$valores['ALERT_REPEATED_EXTERNAL_FAILURE_THRESHOLD'] = LerInteiroValidado `
    -Nome 'ALERT_REPEATED_EXTERNAL_FAILURE_THRESHOLD' `
    -Explicacao 'Numero minimo de falhas EXTERNAL repetidas para gerar um alerta.' `
    -Minimo 2 -Maximo ([int]::MaxValue)
$valores['ALERT_REPEATED_EXTERNAL_FAILURE_WINDOW_SECONDS'] = LerInteiroValidado `
    -Nome 'ALERT_REPEATED_EXTERNAL_FAILURE_WINDOW_SECONDS' `
    -Explicacao 'Tamanho, em segundos, da janela temporal de falhas externas.' `
    -Minimo 1 -Maximo ([long]::MaxValue)
$valores['ALERT_SUSPICIOUS_COLLECTION_LOOKBACK_RUNS'] = LerInteiroValidado `
    -Nome 'ALERT_SUSPICIOUS_COLLECTION_LOOKBACK_RUNS' `
    -Explicacao 'Numero de runs COMPLETED anteriores para compor a referencia historica.' `
    -Minimo 1 -Maximo ([int]::MaxValue)
$valores['ALERT_SUSPICIOUS_COLLECTION_DROP_FRACTION'] = LerFracaoValidada
$valores['ALERT_SUSPICIOUS_COLLECTION_MINIMUM_BASELINE_CANDIDATES'] = LerInteiroValidado `
    -Nome 'ALERT_SUSPICIOUS_COLLECTION_MINIMUM_BASELINE_CANDIDATES' `
    -Explicacao 'Media historica minima de candidatos para avaliar queda suspeita.' `
    -Minimo 1 -Maximo ([long]::MaxValue)

Write-Host ''
Write-Host '=== C2-E-D-D-C-B POLITICA VALIDADA - 5 campos com tipos e limites conformes ==='
Write-Host 'ATENCAO: esses cinco valores foram escolhidos pelo operador; nao sao defaults do sistema.'
$confirmacao = Read-Host 'Digite exatamente CONFIRMO para executar as cinco consultas da CLI com esta politica temporaria'
if ($confirmacao -cne 'CONFIRMO') {
    throw 'Politica nao confirmada. Nenhuma CLI foi executada.'
}

$anteriores = @{}
foreach ($nome in $nomes) {
    $anteriores[$nome] = [System.Environment]::GetEnvironmentVariable($nome, 'Process')
}
$envExisteAntes = Test-Path -LiteralPath '.env' -PathType Leaf
$envHashAntes = $null
if ($envExisteAntes) {
    $envHashAntes = (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash
}

try {
    foreach ($nome in $nomes) {
        [System.Environment]::SetEnvironmentVariable($nome, $valores[$nome], 'Process')
    }
    Write-Host '=== C2-E-D-D-C-B POLITICA SESSAO PASS - cinco variaveis definidas apenas no processo ==='
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $CaminhoExecutor -ConfirmarExecucao
    $resultado = $LASTEXITCODE
    if ($resultado -ne 0) {
        throw 'C2-E-D-D-C-B: o gate da CLI nao passou. Nao repetir sem analisar o erro.'
    }
    Write-Host '=== C2-E-D-D-C-B ALERTS COM POLITICA PASS - executor original aprovou as cinco consultas ==='
} finally {
    foreach ($nome in $nomes) {
        [System.Environment]::SetEnvironmentVariable($nome, $anteriores[$nome], 'Process')
    }
    Write-Host '=== C2-E-D-D-C-B POLITICA RESTAURADA - valores temporarios removidos ==='
    $envExisteDepois = Test-Path -LiteralPath '.env' -PathType Leaf
    if ($envExisteAntes -ne $envExisteDepois) {
        throw 'C2-E-D-D-C-B: presenca do .env mudou. Verificar antes de continuar.'
    }
    if ($envExisteDepois) {
        $envHashDepois = (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash
        if ($envHashDepois -cne $envHashAntes) {
            throw 'C2-E-D-D-C-B: hash de .env mudou. Verificar antes de continuar.'
        }
    }
    Write-Host '=== C2-E-D-D-C-B ENV INTACTO PASS - arquivo .env preservado ==='
}
