# FASE 21-E-D3 — regressao da ponte sintética B9 -> D2.
# Cria SOMENTE filhos PowerShell artificiais e segmentos privados sob target/.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$raiz = (Get-Location).ProviderPath
foreach ($arquivo in @(
    'infra/retencao/FASE_21_E_A_FUNCOES_RETENCAO.ps1',
    'infra/retencao/FASE_21_E_B1_FUNCOES_SEGMENTACAO.ps1',
    'infra/retencao/FASE_21_E_B3_FUNCOES_COMPATIBILIDADE.ps1',
    'infra/retencao/FASE_21_E_B4_FUNCOES_CAPTURA_MISTA.ps1',
    'infra/retencao/FASE_21_E_B6_FUNCOES_PROTECAO.ps1',
    'infra/retencao/FASE_21_E_B8_FUNCOES_CATALOGO.ps1',
    'infra/retencao/FASE_21_E_B9_FUNCOES_CAPTURA_CATALOGADA.ps1',
    'infra/retencao/FASE_21_E_D2_FUNCOES_ESCRITOR_OFFLINE.ps1',
    'infra/retencao/FASE_21_E_D3_FUNCOES_PONTE_OFFLINE.ps1'
)) {
    if (-not (Test-Path -LiteralPath $arquivo -PathType Leaf)) { throw 'D3: dependencia ausente' }
    . (Resolve-Path -LiteralPath $arquivo).ProviderPath
}
function Exigir-D3 {
    param([bool]$Condicao,[string]$Descricao)
    if (-not $Condicao) { throw ('21-E-D3: teste reprovado: ' + $Descricao) }
}
function EsperarFalha-D3 {
    param([scriptblock]$Acao,[string]$Descricao)
    $houveFalha=$false
    try { & $Acao | Out-Null } catch { $houveFalha=$true }
    Exigir-D3 $houveFalha $Descricao
}
function ObterRegistros-D3 {
    param([string]$Diretorio)
    $segmentos = @(Get-ChildItem -LiteralPath $Diretorio -Filter 'operacional-*.jsonl' -File | Sort-Object Name)
    $linhas = @($segmentos | ForEach-Object { Get-Content -LiteralPath $_.FullName -Encoding UTF8 })
    return [pscustomobject]@{
        Segmentos = $segmentos
        Linhas = $linhas
        Texto = ($linhas -join "`n")
    }
}
function NovoCenario-D3 {
    param([object]$Catalogo,[long]$Segmento=512,[long]$Total=12000)
    $dir = New-21EB1-Sandbox -Raiz $raiz
    $sessao = New-21ED2-SessaoOffline -Diretorio $dir -Catalogo $Catalogo `
        -MaximoSegmentoBytes $Segmento -MaximoTotalBytes $Total
    return [pscustomobject]@{Diretorio=$dir;Sessao=$sessao}
}
function CodigoLinha-D3 {
    param([Parameter(Mandatory=$true)][string]$Linha)
    return "[Console]::WriteLine('" + $Linha.Replace("'", "''") + "')"
}
$catalogo = Assert-21ED2-Contexto -Raiz $raiz
Write-Host 'PASS - Contratos B1/B8/B9/D2 e preparation_only/inventory_only confirmados.'

$timestamp = [DateTimeOffset]::UtcNow.ToString('o')
$validos = @()
foreach ($def in @($catalogo.events)) {
    $obj = [ordered]@{
        timestamp=$timestamp;level=$def.level;event=$def.code;
        component=$def.component;operation=$def.operation;
        outcome=@($def.outcomes)[0];runId=5;jobId=8
    }
    if ($def.code -ceq 'integration.observation.persistence-failed') {
        $obj.failureOrigin='INTERNAL'
        $obj.failureType='PERMANENT'
        $obj.errorCode='SEGREDO_TESTE_D3_NAO_PERSISTIR'
        $obj.asin='TOKEN_TESTE_D3_NAO_PERSISTIR'
    }
    $validos += (ConvertTo-Json -InputObject $obj -Compress)
}
$codigos = @('[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false,$true)')
foreach ($linha in $validos) {
    $codigos += (CodigoLinha-D3 -Linha $linha)
    $codigos += "[Console]::WriteLine('TOKEN_TESTE_D3_NAO_PERSISTIR')"
    $codigos += "[Console]::Error.WriteLine('SEGREDO_TESTE_D3_NAO_PERSISTIR')"
    $codigos += "[Console]::Error.WriteLine('STACK_TESTE_D3_NAO_PERSISTIR')"
}
$cenario = NovoCenario-D3 -Catalogo $catalogo
$r = Invoke-21ED3-CapturaEscritorOffline -CodigoFilho ($codigos -join "`n") `
    -Sessao $cenario.Sessao -Catalogo $catalogo -TimeoutSegundos 30
Exigir-D3 ($r.StdoutEventos -eq 12 -and $r.StderrEventos -eq 12 -and
    $r.StdoutCatalogados -eq 6 -and $r.StdoutRedigidos -eq 6 -and
    $r.StderrRedigidos -eq 12 -and $r.ExitCode -eq 0) 'contadores-captura-mista'
Exigir-D3 $cenario.Sessao.Lacrada 'sessao-lacrada-ao-encerrar'
$conteudo = ObterRegistros-D3 -Diretorio $cenario.Diretorio
Exigir-D3 ($conteudo.Linhas.Count -eq 24 -and $conteudo.Segmentos.Count -ge 2) 'segmentacao-24-registros'
foreach ($def in @($catalogo.events)) {
    Exigir-D3 (@($conteudo.Linhas | Where-Object {
        (ConvertFrom-Json -InputObject $_).event -ceq $def.code
    }).Count -eq 1) ('codigo-' + $def.code)
}
Exigir-D3 (-not ($conteudo.Texto -match 'TOKEN_TESTE_D3|SEGREDO_TESTE_D3|STACK_TESTE_D3')) 'sem-texto-livre'
Exigir-D3 ([long](($conteudo.Segmentos | Measure-Object -Property Length -Sum).Sum) -eq [long]$r.TotalBytes) 'bytes-no-disco'
$falha = @($conteudo.Linhas | ForEach-Object { ConvertFrom-Json -InputObject $_ } |
    Where-Object { $_.event -ceq 'integration.observation.persistence-failed' })
Exigir-D3 ($falha.Count -eq 1 -and $falha[0].errorCode -ceq 'REDACTED') 'codigo-falha-minimizado'
Write-Host ('PASS - Captura D3: stdout=12, stderr=12; 6 catalogados e 18 marcadores; segmentos={0}.' -f $conteudo.Segmentos.Count)
Write-Host 'PASS - Seis codigos, metadados REDACTED, sem texto bruto; segmentos e quota conferidos.'

# Falha do filho: nenhum retry ou exclusao, segmento ja escrito permanece.
$cenarioFalha = NovoCenario-D3 -Catalogo $catalogo
$codigoFalha = (CodigoLinha-D3 -Linha $validos[0]) + "`n" +
    "[Console]::Error.WriteLine('SEGREDO_TESTE_D3_NAO_PERSISTIR')`nexit 17"
EsperarFalha-D3 {
    Invoke-21ED3-CapturaEscritorOffline -CodigoFilho $codigoFalha `
        -Sessao $cenarioFalha.Sessao -Catalogo $catalogo -TimeoutSegundos 15
} 'saida-nao-zero'
$posFalha = ObterRegistros-D3 -Diretorio $cenarioFalha.Diretorio
Exigir-D3 ($cenarioFalha.Sessao.Lacrada -and $posFalha.Linhas.Count -eq 2) 'evidencias-apos-exit-17'
Exigir-D3 (-not ($posFalha.Texto -match 'SEGREDO_TESTE_D3')) 'redacao-apos-exit-17'
Write-Host 'PASS - Codigo nao zero rejeitado; evidencias previas preservadas e sessao encerrada.'

# Erro de quota: primeira evidencia permanece, sem reabrir uma sessao lacrada.
$cenarioQuota = NovoCenario-D3 -Catalogo $catalogo -Segmento 512 -Total 512
$codigoQuota = @((CodigoLinha-D3 -Linha $validos[0]))
foreach ($i in 1..7) { $codigoQuota += (CodigoLinha-D3 -Linha $validos[0]) }
EsperarFalha-D3 {
    Invoke-21ED3-CapturaEscritorOffline -CodigoFilho ($codigoQuota -join "`n") `
        -Sessao $cenarioQuota.Sessao -Catalogo $catalogo -TimeoutSegundos 15
} 'quota-combinada'
$posQuota = ObterRegistros-D3 -Diretorio $cenarioQuota.Diretorio
Exigir-D3 ($cenarioQuota.Sessao.Lacrada -and $posQuota.Linhas.Count -ge 1 -and
    $cenarioQuota.Sessao.TotalBytes -le 512) 'quota-preserva-evento'
EsperarFalha-D3 { Write-21ED2-EventoCatalogado -Sessao $cenarioQuota.Sessao -LinhaJson $validos[0] } 'quota-lacre'
Write-Host 'PASS - Quota unica para ambos canais lacra a sessao, sem sobrescrever historico.'

# Linha de stdout excessiva: nao copiar seus bytes; manter o evento anterior.
$cenarioLinha = NovoCenario-D3 -Catalogo $catalogo
$codigoLinha = (CodigoLinha-D3 -Linha $validos[0]) + "`n" +
    '[Console]::WriteLine([string]::new([char]88, 33000))'
EsperarFalha-D3 {
    Invoke-21ED3-CapturaEscritorOffline -CodigoFilho $codigoLinha `
        -Sessao $cenarioLinha.Sessao -Catalogo $catalogo -TimeoutSegundos 15
} 'linha-excessiva'
$posLinha = ObterRegistros-D3 -Diretorio $cenarioLinha.Diretorio
Exigir-D3 ($cenarioLinha.Sessao.Lacrada -and $posLinha.Linhas.Count -eq 1) 'linha-previa-intacta'
Write-Host 'PASS - Linha excedente bloqueada antes da gravacao; evento anterior conservado.'

# Timeout: somente filho PowerShell sintético encerrado; processo Java nunca iniciado.
$cenarioTimeout = NovoCenario-D3 -Catalogo $catalogo
$codigoTimeout = (CodigoLinha-D3 -Linha $validos[0]) + "`n" +
    '[System.Threading.Thread]::Sleep(30000)'
EsperarFalha-D3 {
    Invoke-21ED3-CapturaEscritorOffline -CodigoFilho $codigoTimeout `
        -Sessao $cenarioTimeout.Sessao -Catalogo $catalogo -TimeoutSegundos 3
} 'timeout'
$posTimeout = ObterRegistros-D3 -Diretorio $cenarioTimeout.Diretorio
Exigir-D3 ($cenarioTimeout.Sessao.Lacrada -and $posTimeout.Linhas.Count -eq 1) 'timeout-evidencia-anterior'
Write-Host 'PASS - Timeout encerrou apenas filho sintetico; evento anterior preservado.'

# O construtor produtivo continua impossibilitado.
EsperarFalha-D3 {
    New-21ED2-SessaoProdutiva -Diretorio 'C:\dev\rasping-amazon-operacao\logs'
} 'escritor-produtivo-bloqueado'
Write-Host 'PROTEGIDO - Somente filhos PowerShell sob target; sem Java, supervisor, credenciais, banco, backups ou exclusoes.'
Write-Host 'PASS - Gate 21-E-D3 isolado aprovado; captura produtiva permanece desautorizada.'
