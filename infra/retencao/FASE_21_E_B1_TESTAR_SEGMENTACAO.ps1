# FASE 21-E-B1. Testes ISOLADOS, sem exclusao e sem inicio de daemon.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$raiz = [System.IO.Path]::GetFullPath((Get-Location).Path)
. (Join-Path $PSScriptRoot 'FASE_21_E_B1_FUNCOES_SEGMENTACAO.ps1')

if (-not (Assert-21EB1-Contexto -Raiz $raiz)) { throw '21-E-B1: contexto invalido' }
Write-Host 'PASS - Politicas E-A e startup preparation_only validadas.'

$dir = New-21EB1-Sandbox -Raiz $raiz
$writer = New-21EB1-Sessao -Diretorio $dir -MaximoSegmentoBytes 256 -MaximoTotalBytes 4096
$esperados = New-Object 'System.Collections.Generic.List[string]'
try {
    for ($i = 1; $i -le 16; $i++) {
        $linha = ('{{"timestamp":"2026-10-15T00:00:00Z","level":"INFO","event":"fase21eb1.teste.{0}","component":"isolado","operation":"sintetica","outcome":"SUCCESS"}}' -f $i)
        $esperados.Add($linha)
        Write-21EB1-EventoSintetico -Sessao $writer -LinhaJson $linha
    }
} finally {
    Close-21EB1-Sessao -Sessao $writer
}
$partes = @(Get-ChildItem -LiteralPath $dir -Filter 'segmento-*.jsonl' -File | Sort-Object Name)
if ($partes.Count -lt 2) { throw '21-E-B1: rotacao nao aconteceu' }
$obtidos = New-Object 'System.Collections.Generic.List[string]'
[long]$totalReal = 0
foreach ($p in $partes) {
    if ($p.Length -gt 256 -or $p.Length -le 0) {
        throw '21-E-B1: segmento fora do limite'
    }
    $totalReal += $p.Length
    foreach ($linha in [System.IO.File]::ReadAllLines($p.FullName, [System.Text.Encoding]::UTF8)) {
        $obtidos.Add($linha)
    }
}
if ($totalReal -ne $writer.TotalBytes -or $obtidos.Count -ne $esperados.Count) {
    throw '21-E-B1: perda, duplicacao ou diferenca de contagem'
}
for ($j = 0; $j -lt $esperados.Count; $j++) {
    if ($obtidos[$j] -cne $esperados[$j]) {
        throw '21-E-B1: quebra de evento ou sequencia fora de ordem'
    }
}
Write-Host 'PASS - Segmentacao sem quebra, perda, duplicacao ou reordenacao.'
Write-Host ('PASS - Segmentos={0}; Eventos={1}; Bytes={2}.' -f $partes.Count, $obtidos.Count, $totalReal)

$dir2 = New-21EB1-Sandbox -Raiz $raiz
$limitado = New-21EB1-Sessao -Diretorio $dir2 -MaximoSegmentoBytes 256 -MaximoTotalBytes 512
$limiteRejeitado = $false
try {
    for ($i = 1; $i -le 20; $i++) {
        $linha = ('{{"timestamp":"2026-10-15T00:00:00Z","level":"INFO","event":"quota.{0}","component":"isolado","operation":"sintetica","outcome":"SUCCESS"}}' -f $i)
        try { Write-21EB1-EventoSintetico -Sessao $limitado -LinhaJson $linha }
        catch {
            if ($_.Exception.Message -notlike '*limite total atingido*') { throw }
            $limiteRejeitado = $true
            break
        }
    }
} finally {
    Close-21EB1-Sessao -Sessao $limitado
}
if (-not $limiteRejeitado) { throw '21-E-B1: limite global nao foi aplicado' }
[long]$totalDisco = 0
foreach ($p in @(Get-ChildItem -LiteralPath $dir2 -File)) { $totalDisco += $p.Length }
if ($totalDisco -ne $limitado.TotalBytes -or $totalDisco -gt 512) {
    throw '21-E-B1: limite global violado ou gravacao parcial'
}
Write-Host 'PASS - Limite global recusa novas gravacoes e preserva segmentos.'

$dir3 = New-21EB1-Sandbox -Raiz $raiz
$neg = New-21EB1-Sessao -Diretorio $dir3 -MaximoSegmentoBytes 256 -MaximoTotalBytes 512
$casos = @(
    [pscustomobject]@{ Nome = 'campo-proibido'; Linha = '{"token":"nao-gravar"}' },
    [pscustomobject]@{ Nome = 'linha-multipla'; Linha = "{`"timestamp`":`"x`"}`n{`"timestamp`":`"y`"}" },
    [pscustomobject]@{ Nome = 'json-invalido'; Linha = '{invalido' },
    [pscustomobject]@{ Nome = 'objeto-invalido'; Linha = '[1,2,3]' },
    [pscustomobject]@{ Nome = 'evento-grande'; Linha = ('{"timestamp":"2026-10-15T00:00:00Z","level":"INFO","event":"' + ('X' * 400) + '","component":"isolado","operation":"sintetica","outcome":"SUCCESS"}') }
)
try {
    foreach ($caso in $casos) {
        $rejeitou = $false
        try { Write-21EB1-EventoSintetico -Sessao $neg -LinhaJson $caso.Linha }
        catch { $rejeitou = $true }
        if (-not $rejeitou) { throw ('21-E-B1: falhou negativo ' + $caso.Nome) }
        Write-Host ('PASS - Entrada invalida rejeitada: ' + $caso.Nome)
    }
} finally {
    Close-21EB1-Sessao -Sessao $neg
}
if (@(Get-ChildItem -LiteralPath $dir3 -Force).Count -ne 0) {
    throw '21-E-B1: entrada invalida produziu arquivos'
}
Write-Host 'PASS - Nenhum evento rejeitado foi persistido.'
Write-Host 'PROTEGIDO - Nao tocou supervisor, backups, dados PostgreSQL ou destino operacional.'
Write-Host 'PASS - 21-E-B1 isolado validado; captura de daemon e exclusao continuam desativadas.'
