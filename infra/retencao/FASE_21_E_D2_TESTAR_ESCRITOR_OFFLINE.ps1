# FASE 21-E-D2: testes offline, sem JVM, PostgreSQL, rede, logs reais ou exclusoes.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$raiz = (Get-Location).ProviderPath
foreach ($script in @(
    'infra/retencao/FASE_21_E_A_FUNCOES_RETENCAO.ps1',
    'infra/retencao/FASE_21_E_B1_FUNCOES_SEGMENTACAO.ps1',
    'infra/retencao/FASE_21_E_B8_FUNCOES_CATALOGO.ps1',
    'infra/retencao/FASE_21_E_D2_FUNCOES_ESCRITOR_OFFLINE.ps1'
)) {
    if (-not (Test-Path -LiteralPath $script -PathType Leaf)) { throw 'D2: dependencia ausente' }
    . (Resolve-Path -LiteralPath $script).ProviderPath
}
function Exigir-D2 { param([bool]$Condicao,[string]$Nome)
    if (-not $Condicao) { throw ('21-E-D2: teste reprovado: ' + $Nome) }
}
function EsperarRejeicao-D2 { param([scriptblock]$Acao,[string]$Nome)
    $rejeitou=$false
    try { & $Acao | Out-Null } catch { $rejeitou=$true }
    Exigir-D2 $rejeitou $Nome
}
$catalogo = Assert-21ED2-Contexto -Raiz $raiz
Write-Host 'PASS - Politicas preparation_only/inventory_only e catalogo B8 confirmados.'
EsperarRejeicao-D2 { New-21ED2-SessaoProdutiva -Diretorio 'C:\dev\rasping-amazon-operacao\logs' } 'producao-bloqueada'
$externo = (Get-Content -LiteralPath 'config/operacao/retention-policy.v1.json' -Raw -Encoding UTF8 | ConvertFrom-Json).continuous_logs_proposal.external_directory
# A existencia de um diretorio externo anterior nao e requisito do teste.
# Apenas comparamos o estado antes e depois, sem abri-lo nem grava-lo.
$estadoExternoAntes = Test-Path -LiteralPath $externo
Write-Host 'PASS - Escritor produtivo bloqueado, sem criar diretorio externo.'
$dir = New-21EB1-Sandbox -Raiz $raiz
$dt = [DateTimeOffset]::UtcNow.ToString('o')
$validos = @()
foreach ($def in @($catalogo.events)) {
    $obj = [ordered]@{
        timestamp=$dt;level=$def.level;event=$def.code;
        component=$def.component;operation=$def.operation;outcome=@($def.outcomes)[0];runId=1;jobId=2
    }
    if ($def.code -ceq 'integration.observation.persistence-failed') {
        $obj['failureOrigin']='INTERNAL'
        $obj['failureType']='PERMANENT'
        $obj['errorCode']='REDACTED'
    }
    $validos += (ConvertTo-Json -InputObject $obj -Compress)
}
$marcador = ConvertTo-Json -InputObject ([ordered]@{
    timestamp=$dt;level='ERROR';event='runtime.stderr.redacted';component='runtime-capture';
    operation='safe-catalog-classification';outcome='REDACTED'
}) -Compress
$session = New-21ED2-SessaoOffline -Diretorio $dir -Catalogo $catalogo -MaximoSegmentoBytes 512 -MaximoTotalBytes 4096
foreach ($linha in $validos) { Write-21ED2-EventoCatalogado -Sessao $session -LinhaJson $linha }
Write-21ED2-EventoCatalogado -Sessao $session -LinhaJson $marcador
Close-21ED2-Sessao -Sessao $session
$segmentos = @(Get-ChildItem -LiteralPath $dir -Filter 'operacional-*.jsonl' -File | Sort-Object Name)
$linhas = @($segmentos | ForEach-Object { Get-Content -LiteralPath $_.FullName -Encoding UTF8 })
Exigir-D2 ($segmentos.Count -ge 2) 'segmentacao'
Exigir-D2 ($linhas.Count -eq 7) 'sete-eventos'
for ($i=0; $i -lt 6; $i++) {
    $atual = ConvertFrom-Json -InputObject $linhas[$i]
    Exigir-D2 ($atual.event -ceq $catalogo.events[$i].code) 'ordem-catalogo'
}
$ultimo = ConvertFrom-Json -InputObject $linhas[6]
Exigir-D2 ($ultimo.event -ceq 'runtime.stderr.redacted') 'marcador-stderr'
Exigir-D2 (-not (($linhas -join "`n") -match 'SENHA_TESTE|TOKEN_TESTE')) 'sem-segredos'
Exigir-D2 ([long]$session.TotalBytes -eq [long](($segmentos | Measure-Object -Property Length -Sum).Sum)) 'bytes-confirmados'
Write-Host ("PASS - Escrita offline: eventos=7; segmentos={0}; bytes={1}; ordem intacta." -f $segmentos.Count,$session.TotalBytes)
EsperarRejeicao-D2 { Write-21ED2-EventoCatalogado -Sessao $session -LinhaJson $validos[0] } 'sessao-fechada'
Write-Host 'PASS - Sessao fechada rejeita novas gravacoes.'
# Novo cenario: texto livre/segredo sintaticamente valido eh recusado, sem persistir nada.
$dirAdversarial = New-21EB1-Sandbox -Raiz $raiz
$s2 = New-21ED2-SessaoOffline -Diretorio $dirAdversarial -Catalogo $catalogo -MaximoSegmentoBytes 512 -MaximoTotalBytes 2048
$malicioso = $validos[0] | ConvertFrom-Json
$malicioso | Add-Member -NotePropertyName 'asin' -NotePropertyValue 'TOKEN_TESTE-NAO-PERSISTIR'
$linhaMaliciosa = ConvertTo-Json -InputObject $malicioso -Compress
EsperarRejeicao-D2 { Write-21ED2-EventoCatalogado -Sessao $s2 -LinhaJson $linhaMaliciosa } 'texto-livre-rejeitado'
Exigir-D2 $s2.Lacrada 'lacre-pos-entrada-invalida'
Exigir-D2 (@(Get-ChildItem -LiteralPath $dirAdversarial -File).Count -eq 0) 'nenhum-arquivo-apos-entrada-invalida'
EsperarRejeicao-D2 { Write-21ED2-EventoCatalogado -Sessao $s2 -LinhaJson $validos[0] } 'lacre-irrevogavel'
Write-Host 'PASS - Campo textual injetado rejeitado antes de abrir segmento; sessao lacrada.'
$duplicado = $validos[0].Replace('"level":"INFO",','"level":"INFO","level":"INFO",')
EsperarRejeicao-D2 { Assert-21ED2-EventoCatalogado -LinhaJson $duplicado -Catalogo $catalogo } 'chave-duplicada'
Write-Host 'PASS - Objeto JSON com chaves duplicadas rejeitado.'
EsperarRejeicao-D2 { New-21ED2-SessaoOffline -Diretorio $externo -Catalogo $catalogo -MaximoSegmentoBytes 512 -MaximoTotalBytes 1024 } 'raiz-externa-proibida'
Write-Host 'PASS - Caminho externo recusado pelo construtor offline.'
# Limite global: preserva os bytes anteriores e proibe novas escritas.
$dirLimite = New-21EB1-Sandbox -Raiz $raiz
$s3 = New-21ED2-SessaoOffline -Diretorio $dirLimite -Catalogo $catalogo -MaximoSegmentoBytes 512 -MaximoTotalBytes 512
for ($i=0; $i -lt 20; $i++) {
    if ($s3.Lacrada) { break }
    try { Write-21ED2-EventoCatalogado -Sessao $s3 -LinhaJson $validos[0] }
    catch { break }
}
Exigir-D2 ($s3.Lacrada -and $s3.TotalBytes -gt 0 -and $s3.TotalBytes -le 512) 'limite-total-lacra'
$primeiro = @(Get-ChildItem -LiteralPath $dirLimite -Filter 'operacional-*.jsonl' -File)[0]
$bytesAntes = $s3.TotalBytes
Close-21ED2-Sessao -Sessao $s3
Exigir-D2 ([long](Get-Item -LiteralPath $primeiro.FullName).Length -eq [long]$bytesAntes) 'quota-sem-perda'
$hashAntes = (Get-FileHash -LiteralPath $primeiro.FullName -Algorithm SHA256).Hash
# Testa que uma nova sessao nao pode reabrir um diretorio com evidencia anterior.
EsperarRejeicao-D2 { New-21ED2-SessaoOffline -Diretorio $dirLimite -Catalogo $catalogo -MaximoSegmentoBytes 512 -MaximoTotalBytes 512 } 'sem-reutilizar-evidencias'
Exigir-D2 ((Get-FileHash -LiteralPath $primeiro.FullName -Algorithm SHA256).Hash -ceq $hashAntes) 'arquivo-preservado'
Write-Host 'PASS - Limite total lacra a sessao; arquivo anterior intacto e sem reutilizacao.'
$dirColisao = New-21EB1-Sandbox -Raiz $raiz
[System.IO.File]::WriteAllText((Join-Path $dirColisao 'operacional-000001.jsonl'),'SENTINELA-NAO-TOCAR',[Text.Encoding]::ASCII)
EsperarRejeicao-D2 { New-21ED2-SessaoOffline -Diretorio $dirColisao -Catalogo $catalogo -MaximoSegmentoBytes 512 -MaximoTotalBytes 1024 } 'colisao-rejeitada'
Exigir-D2 ([System.IO.File]::ReadAllText((Join-Path $dirColisao 'operacional-000001.jsonl')) -ceq 'SENTINELA-NAO-TOCAR') 'sentinela-intacta'
Write-Host 'PASS - Colisao rejeitada e sentinela de teste preservada.'
$dirFalha = New-21EB1-Sandbox -Raiz $raiz
$s4 = New-21ED2-SessaoOffline -Diretorio $dirFalha -Catalogo $catalogo -MaximoSegmentoBytes 300 -MaximoTotalBytes 1200
Write-21ED2-EventoCatalogado -Sessao $s4 -LinhaJson $validos[0]
$anteriorBytes = $s4.TotalBytes
$sentinela2 = Join-Path $dirFalha 'operacional-000002.jsonl'
[System.IO.File]::WriteAllText($sentinela2,'SENTINELA-SEGUNDO',[Text.Encoding]::ASCII)
EsperarRejeicao-D2 { Write-21ED2-EventoCatalogado -Sessao $s4 -LinhaJson $validos[1] } 'falha-real-criacao-segmento'
Exigir-D2 $s4.Lacrada 'sessao-lacrada-apos-colisao'
Exigir-D2 ([long](Get-Item -LiteralPath (Join-Path $dirFalha 'operacional-000001.jsonl')).Length -eq [long]$anteriorBytes) 'segmento-1-preservado'
Exigir-D2 ([System.IO.File]::ReadAllText($sentinela2) -ceq 'SENTINELA-SEGUNDO') 'segmento-2-preservado'
EsperarRejeicao-D2 { Write-21ED2-EventoCatalogado -Sessao $s4 -LinhaJson $validos[0] } 'pos-falha-lacre'
Write-Host 'PASS - Falha de criacao de segmento lacra sessao; segmentos anteriores intactos.'
Exigir-D2 ((Test-Path -LiteralPath $externo) -eq $estadoExternoAntes) 'destino-externo-intocado'
Write-Host 'PROTEGIDO - Escrita SOMENTE em cenarios privados sob target; sem daemon, banco, backups, logs externos ou exclusoes.'
Write-Host 'PASS - Gate 21-E-D2 offline aprovado; escritor produtivo continua nao implementado/nao autorizado.'
