# CLI empacotada: --help sem banco; demais comandos exigem intencao explicita.
# Nao inicia o processamento continuo nem executa Flyway.
param(
    [switch]$ConfirmarOperacao,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Comando
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$raiz = Split-Path -Parent $MyInvocation.MyCommand.Path
$jar = Join-Path $raiz 'rasping-amazon-1.0-SNAPSHOT.jar'
$lib = Join-Path $raiz 'lib'
if (-not (Test-Path -LiteralPath $jar -PathType Leaf) -or
    -not (Test-Path -LiteralPath $lib -PathType Container)) {
    throw '21-G: distribuicao incompleta'
}
if ($null -eq $Comando -or $Comando.Count -eq 0) {
    $Comando = @('--help')
}
$ajuda = ($Comando.Count -eq 1 -and $Comando[0] -cin @('help','--help','-h'))
if (-not $ajuda -and -not $ConfirmarOperacao) {
    throw '21-G: comandos operacionais exigem -ConfirmarOperacao e autorizacao de operacao separada'
}
$classpath = $jar + ';' + (Join-Path $lib '*')
& java.exe '-cp' $classpath 'com.raspingamazon.infrastructure.bootstrap.OperationalCliMain' @Comando
if ($LASTEXITCODE -ne 0) {
    throw ('21-G: CLI terminou com codigo ' + $LASTEXITCODE)
}
