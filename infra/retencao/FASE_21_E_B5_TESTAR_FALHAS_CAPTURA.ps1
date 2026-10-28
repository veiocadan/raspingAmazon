# FASE 21-E-B5 - testes adversariais da captura sintética.
# Somente Windows; cenarios novos em target/retencao-21eb1-testes.
# Nao acessa daemon, PostgreSQL, backups, logs reais ou diretorio de supervisao.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$raiz = (Get-Location).ProviderPath
. (Join-Path $PSScriptRoot 'FASE_21_E_B1_FUNCOES_SEGMENTACAO.ps1')
. (Join-Path $PSScriptRoot 'FASE_21_E_B3_FUNCOES_COMPATIBILIDADE.ps1')
. (Join-Path $PSScriptRoot 'FASE_21_E_B4_FUNCOES_CAPTURA_MISTA.ps1')

if (-not (Assert-21EB1-Contexto -Raiz $raiz)) {
    throw '21-E-B5: contexto nao autorizado'
}
$fonteB4 = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'FASE_21_E_B4_FUNCOES_CAPTURA_MISTA.ps1'))
if ($fonteB4 -notmatch '\.ReadAsync\(' -or
    $fonteB4 -match '\.ReadLineAsync\(' -or
    $fonteB4 -notmatch 'linha excede limite de leitura segura') {
    throw '21-E-B5: capturador nao possui leitura limitada por bloco'
}
Write-Host 'PASS - Contexto protegido e leitura concorrente com blocos limitados.'

function New-21EB5-Canais {
    $outDir = New-21EB1-Sandbox -Raiz $raiz
    $errDir = New-21EB1-Sandbox -Raiz $raiz
    return [pscustomobject]@{
        OutDir = $outDir
        ErrDir = $errDir
        Out = New-21EB1-Sessao -Diretorio $outDir -MaximoSegmentoBytes 1024 -MaximoTotalBytes 65536
        Err = New-21EB1-Sessao -Diretorio $errDir -MaximoSegmentoBytes 1024 -MaximoTotalBytes 65536
    }
}

function Get-21EB5-Eventos {
    param([Parameter(Mandatory=$true)][string]$Diretorio)
    $resultados = New-Object 'System.Collections.Generic.List[object]'
    $segmentos = @(Get-ChildItem -LiteralPath $Diretorio -File -Filter 'segmento-*.jsonl' | Sort-Object Name)
    foreach ($segmento in $segmentos) {
        if ($segmento.Length -lt 1 -or $segmento.Length -gt 1024) {
            throw '21-E-B5: segmento excedeu limite'
        }
        foreach ($linha in [IO.File]::ReadAllLines($segmento.FullName, [Text.Encoding]::UTF8)) {
            $obj = $linha | ConvertFrom-Json -ErrorAction Stop
            if ($null -eq $obj -or $obj -isnot [pscustomobject]) {
                throw '21-E-B5: linha nao e objeto JSON'
            }
            $resultados.Add($obj)
        }
    }
    return $resultados.ToArray()
}

# 1) Linha stdout excede 32768 caracteres. Preservar registro anterior.
$compridoOut = New-21EB5-Canais
$codigoOut = @'
[Console]::Out.WriteLine('{"timestamp":"2026-10-15T18:00:00Z","level":"INFO","event":"fase21eb5.before-large","component":"synthetic-child","operation":"emit","outcome":"SUCCESS"}')
[Console]::Out.WriteLine(('X' * 40000))
[Console]::Out.Flush()
exit 0
'@
$rejeitou = $false
try {
    Invoke-21EB4-CapturaMistaSintetica -CodigoFilho $codigoOut `
        -SessaoStdout $compridoOut.Out -SessaoStderr $compridoOut.Err `
        -MaximoCombinadoBytes 65536 | Out-Null
} catch {
    if ($_.Exception.Message -notlike '*linha excede limite de leitura segura*') { throw }
    $rejeitou = $true
}
$eventos = @(Get-21EB5-Eventos -Diretorio $compridoOut.OutDir)
if (-not $rejeitou -or $eventos.Count -ne 1 -or
    $eventos[0].event -cne 'fase21eb5.before-large' -or
    -not $compridoOut.Out.Encerrada -or -not $compridoOut.Err.Encerrada) {
    throw '21-E-B5: stdout longo nao rejeitado ou evidencia anterior comprometida'
}
Write-Host 'PASS - Linha stdout excessiva rejeitada antes de persistir; evidencia anterior preservada.'

# 2) Linha stderr longa; um marcador seguro antes dela.
$compridoErr = New-21EB5-Canais
$codigoErr = @'
[Console]::Error.WriteLine('Continuous processing interrupted')
[Console]::Error.WriteLine(('Y' * 40000))
[Console]::Error.Flush()
exit 0
'@
$rejeitou = $false
try {
    Invoke-21EB4-CapturaMistaSintetica -CodigoFilho $codigoErr `
        -SessaoStdout $compridoErr.Out -SessaoStderr $compridoErr.Err `
        -MaximoCombinadoBytes 65536 | Out-Null
} catch {
    if ($_.Exception.Message -notlike '*linha excede limite de leitura segura*') { throw }
    $rejeitou = $true
}
$eventos = @(Get-21EB5-Eventos -Diretorio $compridoErr.ErrDir)
if (-not $rejeitou -or $eventos.Count -ne 1 -or
    $eventos[0].event -cne 'runtime.stderr.interrupted' -or
    -not $compridoErr.Out.Encerrada -or -not $compridoErr.Err.Encerrada) {
    throw '21-E-B5: stderr longo nao rejeitado ou marcador anterior comprometido'
}
Write-Host 'PASS - Linha stderr excessiva rejeitada sem registrar texto bruto.'

# 3) Leitura em blocos: CRLF, parcial sem LF no encerramento e buffers independentes.
$parcial = New-21EB5-Canais
$codigoParcial = @'
[Console]::Out.Write('{"timestamp":"2026-10-15T18:00:00Z","level":"INFO","event":"fase21eb5.partial-eof","component":"synthetic-child","operation":"emit","outcome":"SUCCESS"}')
[Console]::Error.Write("Continuous processing interrupted`r`n")
[Console]::Out.Flush()
[Console]::Error.Flush()
exit 0
'@
$resumo = Invoke-21EB4-CapturaMistaSintetica -CodigoFilho $codigoParcial `
    -SessaoStdout $parcial.Out -SessaoStderr $parcial.Err `
    -MaximoCombinadoBytes 65536
$outEventos = @(Get-21EB5-Eventos -Diretorio $parcial.OutDir)
$errEventos = @(Get-21EB5-Eventos -Diretorio $parcial.ErrDir)
if ($resumo.StdoutEventos -ne 1 -or $resumo.StderrEventos -ne 1 -or
    $outEventos.Count -ne 1 -or $errEventos.Count -ne 1 -or
    $outEventos[0].event -cne 'fase21eb5.partial-eof' -or
    $errEventos[0].event -cne 'runtime.stderr.interrupted') {
    throw '21-E-B5: perda de ultima linha ou quebra CRLF incorreta'
}
Write-Host 'PASS - EOF sem quebra de linha e CRLF tratados sem perda de eventos.'

# 4) Simular colisoes de nome sem apagar ou sobrescrever arquivo preexistente.
$colisao = New-21EB5-Canais
$sentinela = Join-Path $colisao.OutDir 'segmento-000001.jsonl'
$textoSentinela = 'B5_SENTINELA_NAO_SOBRESCREVER'
[IO.File]::WriteAllText($sentinela, $textoSentinela, [Text.Encoding]::UTF8)
$codigoColisao = @'
[Console]::Out.WriteLine('{"timestamp":"2026-10-15T18:00:00Z","level":"INFO","event":"fase21eb5.collision","component":"synthetic-child","operation":"emit","outcome":"SUCCESS"}')
[Console]::Out.Flush()
exit 0
'@
$rejeitou = $false
try {
    Invoke-21EB4-CapturaMistaSintetica -CodigoFilho $codigoColisao `
        -SessaoStdout $colisao.Out -SessaoStderr $colisao.Err `
        -MaximoCombinadoBytes 65536 | Out-Null
} catch {
    $rejeitou = $true
}
if (-not $rejeitou -or
    [IO.File]::ReadAllText($sentinela, [Text.Encoding]::UTF8) -cne $textoSentinela -or
    -not $colisao.Out.Encerrada -or -not $colisao.Err.Encerrada) {
    throw '21-E-B5: colisao permitiu escrita indevida ou nao encerrou sessao'
}
Write-Host 'PASS - Colisao de nome rejeitada; arquivo preexistente intocado.'

# 5) Garantia de ausencia de carga bruta excessiva persistida.
foreach ($dir in @($compridoOut.OutDir,$compridoErr.ErrDir,$parcial.OutDir,$parcial.ErrDir)) {
    $arquivos = @(Get-ChildItem -LiteralPath $dir -File -Filter 'segmento-*.jsonl')
    foreach ($f in $arquivos) {
        $conteudo = [IO.File]::ReadAllText($f.FullName, [Text.Encoding]::UTF8)
        if ($conteudo.Contains(('X' * 32)) -or $conteudo.Contains(('Y' * 32))) {
            throw '21-E-B5: texto bruto longo vazou para segmento'
        }
    }
}
Write-Host 'PASS - Nenhuma carga bruta de linhas excessivas foi persistida.'
Write-Host 'PROTEGIDO - Testes apenas em target; sem daemon, Git, banco, backups ou exclusao.'
Write-Host 'PASS - 21-E-B5 adversarial isolado aprovado; nenhuma integracao produtiva autorizada.'
