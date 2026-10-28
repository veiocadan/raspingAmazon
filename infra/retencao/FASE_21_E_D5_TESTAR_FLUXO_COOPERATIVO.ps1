# FASE 21-E-D5 — gate de captura catalogada + parada cooperativa.
# Somente PowerShell sintetico e armazenamento privado sob target/.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$raiz = (Get-Location).ProviderPath
foreach ($arquivo in @(
    'infra/retencao/FASE_21_E_A_FUNCOES_RETENCAO.ps1',
    'infra/retencao/FASE_21_E_B1_FUNCOES_SEGMENTACAO.ps1',
    'infra/retencao/FASE_21_E_B3_FUNCOES_COMPATIBILIDADE.ps1',
    'infra/retencao/FASE_21_E_B8_FUNCOES_CATALOGO.ps1',
    'infra/retencao/FASE_21_E_B9_FUNCOES_CAPTURA_CATALOGADA.ps1',
    'infra/retencao/FASE_21_E_D2_FUNCOES_ESCRITOR_OFFLINE.ps1',
    'infra/retencao/FASE_21_E_D3_FUNCOES_PONTE_OFFLINE.ps1',
    'infra/retencao/FASE_21_E_D4_FUNCOES_PARADA_SINTETICA.ps1',
    'infra/retencao/FASE_21_E_D5_FUNCOES_FLUXO_COOPERATIVO.ps1'
)) {
    if (-not (Test-Path -LiteralPath $arquivo -PathType Leaf)) {
        throw ('21-E-D5: dependencia ausente: ' + $arquivo)
    }
    . (Resolve-Path -LiteralPath $arquivo -ErrorAction Stop).ProviderPath
}
function Exigir-D5 { param([bool]$Condicao,[string]$Nome)
    if (-not $Condicao) { throw ('21-E-D5: teste reprovado: ' + $Nome) }
}
function Rejeitar-D5 { param([scriptblock]$Acao,[string]$Nome)
    $rejeitou = $false
    try { & $Acao | Out-Null } catch { $rejeitou = $true }
    Exigir-D5 $rejeitou $Nome
}
$catalogo = Assert-21ED4-Contexto -Raiz $raiz
Write-Host 'PASS - Politicas preparation_only/inventory_only, catalogo e parada D4 validados.'
$externo = (Get-Content -LiteralPath 'config/operacao/retention-policy.v1.json' -Raw -Encoding UTF8 |
    ConvertFrom-Json).continuous_logs_proposal.external_directory
$antesExterno = Test-Path -LiteralPath $externo
$dir = New-21EB1-Sandbox -Raiz $raiz
$sessao = New-21ED2-SessaoOffline -Diretorio $dir -Catalogo $catalogo `
    -MaximoSegmentoBytes 512 -MaximoTotalBytes 4096
$resultado = Invoke-21ED5-CapturaComParadaOffline -Sessao $sessao -Catalogo $catalogo
Exigir-D5 ($resultado.StdoutEventos -eq 1 -and $resultado.StderrEventos -eq 1) 'dois-eventos'
Exigir-D5 ($resultado.Catalogados -eq 1 -and $resultado.Marcadores -eq 1) 'classificacao'
Exigir-D5 ($resultado.CodigoSaida -eq 0 -and $resultado.Lacrada -and $sessao.Lacrada) 'parada-natural'
Exigir-D5 ((Test-Path -LiteralPath $resultado.Pedido -PathType Leaf) -and
    (Test-Path -LiteralPath (Join-Path $dir 'filho-d4-pronto.marker') -PathType Leaf)) 'sinais-privados'
$segmentos = @(Get-ChildItem -LiteralPath $dir -Filter 'operacional-*.jsonl' -File | Sort-Object Name)
$linhas = @($segmentos | ForEach-Object { Get-Content -LiteralPath $_.FullName -Encoding UTF8 })
Exigir-D5 ($linhas.Count -eq 2 -and $segmentos.Count -ge 1) 'dois-registros'
$eventos = @($linhas | ForEach-Object { ConvertFrom-Json -InputObject $_ -ErrorAction Stop })
$identificados = @($eventos | Where-Object { $_.event -ceq 'processing.job.claimed' })
$redigidos = @($eventos | Where-Object { $_.event -ceq 'runtime.stderr.redacted' })
Exigir-D5 ($identificados.Count -eq 1 -and $redigidos.Count -eq 1) 'catalogo-e-marcador'
Exigir-D5 ($identificados[0].jobId -eq 11 -and $identificados[0].runId -eq 7) 'correlacao'
Exigir-D5 (-not (($linhas -join "`n") -match 'TOKEN_FICTICIO_D5|secret|stack trace')) 'segredo-ausente'
Exigir-D5 ($resultado.TotalBytes -eq [long](($segmentos | Measure-Object -Property Length -Sum).Sum)) 'contagem-de-bytes'
Write-Host 'PASS - Captura simultanea: stdout=1 catalogado; stderr=1 marcador redigido.'
Write-Host 'PASS - Falha sintetica de validacao lacrou escritor e gerou pedido privado de parada.'
Write-Host 'PASS - Mesmo filho reconheceu parada e terminou naturalmente com codigo 0.'
Write-Host 'PASS - Evidencias anteriores preservadas e texto ficticio nao persistido.'
Rejeitar-D5 { Write-21ED2-EventoCatalogado -Sessao $sessao -LinhaJson $linhas[0] } 'sem-gravacao-apos-lacre'
Rejeitar-D5 { New-21ED4-PedidoParada -Sessao $sessao } 'sem-pedido-duplicado'
Rejeitar-D5 { New-21ED2-SessaoProdutiva -Diretorio $externo } 'producao-desabilitada'
Exigir-D5 ((Test-Path -LiteralPath $externo) -eq $antesExterno) 'externo-intocado'
Write-Host 'PASS - Lacre irreversivel, pedido duplicado e escritor externo rejeitados.'
Write-Host 'PROTEGIDO - Somente filho PowerShell e dados sinteticos sob target; nenhum Kill, taskkill, Java, supervisor, banco ou backup.'
Write-Host 'PASS - Gate 21-E-D5 offline aprovado; captura produtiva ainda desautorizada.'
