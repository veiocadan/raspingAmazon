# FASE 21-C2-E-D-D-C-B - diagnostico isolado de ALERTS.
# SOMENTE LEITURA: nao inicia Java, Maven, Docker, PostgreSQL, worker ou scheduler.
# Nao le DPAPI, nao altera .env, nao apresenta valores de variaveis.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$fonte = 'src/main/java/com/raspingamazon/infrastructure/config/EnvironmentOperationalAlertPolicyProvider.java'
if (-not (Test-Path -LiteralPath 'pom.xml' -PathType Leaf) -or
    -not (Test-Path -LiteralPath $fonte -PathType Leaf)) {
    throw 'C2-E-D-D-C-B DIAG: execute a partir da raiz C:\dev\rasping-amazon'
}

$regras = @(
    @{ Nome='ALERT_REPEATED_EXTERNAL_FAILURE_THRESHOLD'; Tipo='Min2' },
    @{ Nome='ALERT_REPEATED_EXTERNAL_FAILURE_WINDOW_SECONDS'; Tipo='PosLong' },
    @{ Nome='ALERT_SUSPICIOUS_COLLECTION_LOOKBACK_RUNS'; Tipo='PosInt' },
    @{ Nome='ALERT_SUSPICIOUS_COLLECTION_DROP_FRACTION'; Tipo='Fracao' },
    @{ Nome='ALERT_SUSPICIOUS_COLLECTION_MINIMUM_BASELINE_CANDIDATES'; Tipo='PosLong' }
)

function TestarValor([string]$Valor, [string]$Tipo) {
    if ([string]::IsNullOrWhiteSpace($Valor)) { return $false }
    $valorNormalizado = $Valor.Trim()
    if ($valorNormalizado -ceq 'CHANGE_ME') { return $false }
    switch ($Tipo) {
        'Min2' {
            $num = 0
            return ([int]::TryParse($valorNormalizado, [ref]$num) -and $num -ge 2)
        }
        'PosInt' {
            $num = 0
            return ([int]::TryParse($valorNormalizado, [ref]$num) -and $num -ge 1)
        }
        'PosLong' {
            $num = [long]0
            return ([long]::TryParse($valorNormalizado, [ref]$num) -and $num -ge 1)
        }
        'Fracao' {
            $num = [decimal]0
            $estilo = [System.Globalization.NumberStyles]::Float
            $cultura = [System.Globalization.CultureInfo]::InvariantCulture
            return ([decimal]::TryParse($valorNormalizado, $estilo, $cultura, [ref]$num) -and
                    $num -gt [decimal]0 -and $num -lt [decimal]1)
        }
        default { throw 'C2-E-D-D-C-B DIAG: regra interna desconhecida' }
    }
}

# Confirma que o contrato JAVA contem as variaveis. O conteudo da fonte NAO e mostrado.
$codigo = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $fonte).Path)
foreach ($regra in $regras) {
    if (-not $codigo.Contains($regra.Nome)) {
        throw ('C2-E-D-D-C-B DIAG: contrato Java nao reconhece ' + $regra.Nome)
    }
}
Write-Host '=== C2-E-D-D-C-B DIAG CONTRATO PASS - politica ALERT_* obrigatoria no Java ==='

# Ler .env SOMENTE para checar existencia de chave e sua forma, sem imprimir qualquer valor.
# Nao efetua importacao de variaveis nem pressupoe que o Java leia .env automaticamente.
$linhasEnv = @()
$existeEnv = Test-Path -LiteralPath '.env' -PathType Leaf
if ($existeEnv) {
    $linhasEnv = @(Get-Content -LiteralPath '.env')
}
$quantidadeValida = 0
foreach ($regra in $regras) {
    $nome = [string]$regra.Nome
    $tipo = [string]$regra.Tipo
    $processo = [System.Environment]::GetEnvironmentVariable($nome, 'Process')
    if ([string]::IsNullOrWhiteSpace($processo)) {
        $situacaoProcesso = 'AUSENTE'
    } elseif (TestarValor $processo $tipo) {
        $situacaoProcesso = 'VALIDA'
        $quantidadeValida++
    } else {
        $situacaoProcesso = 'INVALIDA'
    }

    $situacaoArquivo = 'ARQUIVO_AUSENTE'
    if ($existeEnv) {
        $ocorrencias = @()
        $expressao = '^\s*(?:export\s+)?' + [regex]::Escape($nome) + '\s*=\s*(.*)$'
        foreach ($linha in $linhasEnv) {
            if ($linha -match $expressao) {
                $ocorrencias += $Matches[1]
            }
        }
        if ($ocorrencias.Count -eq 0) {
            $situacaoArquivo = 'CHAVE_AUSENTE'
        } elseif ($ocorrencias.Count -gt 1) {
            $situacaoArquivo = 'CHAVE_DUPLICADA'
        } else {
            $bruto = [string]$ocorrencias[0]
            # Suporta valor simples ou entre aspas, sem expandir/interpolar e sem mostrar conteudo.
            $bruto = $bruto.Trim()
            if (($bruto.StartsWith('"') -and $bruto.EndsWith('"') -and $bruto.Length -ge 2) -or
                ($bruto.StartsWith("'") -and $bruto.EndsWith("'") -and $bruto.Length -ge 2)) {
                $bruto = $bruto.Substring(1, $bruto.Length - 2)
            }
            if (TestarValor $bruto $tipo) {
                $situacaoArquivo = 'VALIDA_NAO_IMPORTADA'
            } else {
                $situacaoArquivo = 'INVALIDA_OU_PLACEHOLDER'
            }
        }
    }

    # Somente nome conhecido e estados calculados, nenhum conteudo de variavel ou arquivo.
    Write-Host ('=== C2-E-D-D-C-B DIAG ' + $nome +
                ' | PROCESSO=' + $situacaoProcesso +
                ' | .ENV=' + $situacaoArquivo + ' ===')
}
if ($quantidadeValida -eq $regras.Count) {
    Write-Host '=== C2-E-D-D-C-B DIAG POLITICA PROCESSO COMPLETA - checar causa SQL em etapa posterior, sem repetir CLI agora ==='
} else {
    Write-Host '=== C2-E-D-D-C-B DIAG POLITICA PROCESSO INCOMPLETA - hipotese de configuracao confirmada ==='
}
Write-Host '=== C2-E-D-D-C-B DIAGNOSTICO PASS - nenhuma credencial exibida, nenhum processo Java iniciado, nenhum arquivo modificado ==='
