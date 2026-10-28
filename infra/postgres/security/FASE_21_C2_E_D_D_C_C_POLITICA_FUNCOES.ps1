# FASE 21-C2-E-D-D-C-C - funcoes de validacao da politica publica.
# Nao executa Java, SQL, Docker, nem utiliza credenciais.
Set-StrictMode -Version Latest

function Ler-PoliticaAlertasVersionada {
    param([Parameter(Mandatory=$true)][string]$Caminho)

    if (-not (Test-Path -LiteralPath $Caminho -PathType Leaf)) {
        throw 'C2-E-D-D-C-C: politica JSON nao encontrada'
    }

    $conteudo = [System.IO.File]::ReadAllText(
        [System.IO.Path]::GetFullPath($Caminho),
        [System.Text.Encoding]::UTF8
    )
    if ([string]::IsNullOrWhiteSpace($conteudo)) {
        throw 'C2-E-D-D-C-C: politica JSON vazia'
    }
    try {
        $objeto = ConvertFrom-Json -InputObject $conteudo -ErrorAction Stop
    } catch {
        throw 'C2-E-D-D-C-C: politica JSON com sintaxe invalida'
    }
    if ($null -eq $objeto -or $objeto -isnot [pscustomobject]) {
        throw 'C2-E-D-D-C-C: politica precisa ser um objeto JSON'
    }

    $campos = @(
        'ALERT_REPEATED_EXTERNAL_FAILURE_THRESHOLD',
        'ALERT_REPEATED_EXTERNAL_FAILURE_WINDOW_SECONDS',
        'ALERT_SUSPICIOUS_COLLECTION_LOOKBACK_RUNS',
        'ALERT_SUSPICIOUS_COLLECTION_DROP_FRACTION',
        'ALERT_SUSPICIOUS_COLLECTION_MINIMUM_BASELINE_CANDIDATES'
    )
    $esperados = @('schema_version') + $campos
    $propriedades = @($objeto.PSObject.Properties | Where-Object { $_.MemberType -eq 'NoteProperty' })
    if ($propriedades.Count -ne $esperados.Count) {
        throw 'C2-E-D-D-C-C: quantidade de chaves JSON invalida'
    }
    foreach ($propriedade in $propriedades) {
        if ($esperados -cnotcontains $propriedade.Name) {
            throw 'C2-E-D-D-C-C: chave inesperada na politica JSON'
        }
    }
    foreach ($nome in $esperados) {
        if (@($propriedades | Where-Object { $_.Name -ceq $nome }).Count -ne 1) {
            throw 'C2-E-D-D-C-C: chave obrigatoria ausente na politica JSON'
        }
    }
    if (($objeto.schema_version -isnot [int] -and $objeto.schema_version -isnot [long]) -or
        $objeto.schema_version -ne 1) {
        throw 'C2-E-D-D-C-C: schema_version precisa ser o inteiro 1'
    }

    $retorno = @{}
    $inteiros = @(
        @{ Nome='ALERT_REPEATED_EXTERNAL_FAILURE_THRESHOLD'; Minimo=[long]2; Maximo=[long][int]::MaxValue },
        @{ Nome='ALERT_REPEATED_EXTERNAL_FAILURE_WINDOW_SECONDS'; Minimo=[long]1; Maximo=[long]::MaxValue },
        @{ Nome='ALERT_SUSPICIOUS_COLLECTION_LOOKBACK_RUNS'; Minimo=[long]1; Maximo=[long][int]::MaxValue },
        @{ Nome='ALERT_SUSPICIOUS_COLLECTION_MINIMUM_BASELINE_CANDIDATES'; Minimo=[long]1; Maximo=[long]::MaxValue }
    )
    foreach ($regra in $inteiros) {
        $nome = $regra.Nome
        $valor = $objeto.PSObject.Properties[$nome].Value
        if (($valor -isnot [int] -and $valor -isnot [long]) -or
            [long]$valor -lt $regra.Minimo -or [long]$valor -gt $regra.Maximo) {
            throw ('C2-E-D-D-C-C: inteiro invalido para ' + $nome)
        }
        $retorno[$nome] = [string]$valor
    }

    $nomeFracao = 'ALERT_SUSPICIOUS_COLLECTION_DROP_FRACTION'
    $valorFracao = $objeto.PSObject.Properties[$nomeFracao].Value
    if ($valorFracao -isnot [double] -and $valorFracao -isnot [decimal]) {
        throw 'C2-E-D-D-C-C: fracao deve ser um numero decimal JSON'
    }
    try {
        $fracao = [decimal]$valorFracao
    } catch {
        throw 'C2-E-D-D-C-C: fracao decimal invalida'
    }
    if ($fracao -le 0 -or $fracao -ge 1) {
        throw 'C2-E-D-D-C-C: fracao deve ser maior que zero e menor que um'
    }
    $retorno[$nomeFracao] = $fracao.ToString(
        '0.############################',
        [System.Globalization.CultureInfo]::InvariantCulture
    )
    return $retorno
}
