# FASE 21-E-D4: valida lacre do escritor -> pedido privado -> parada cooperativa.
# NAO executa daemon/Java, nao usa diretorio externo, NAO remove evidencias.
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
    'infra/retencao/FASE_21_E_D4_FUNCOES_PARADA_SINTETICA.ps1'
)) {
    if (-not (Test-Path -LiteralPath $arquivo -PathType Leaf)) {
        throw ('21-E-D4: dependencia ausente: ' + $arquivo)
    }
    . (Resolve-Path -LiteralPath $arquivo -ErrorAction Stop).ProviderPath
}
function Exigir-D4 { param([bool]$Condicao,[string]$Descricao)
    if (-not $Condicao) { throw ('21-E-D4: teste reprovado: ' + $Descricao) }
}
function Rejeitar-D4 { param([scriptblock]$Acao,[string]$Descricao)
    $rejeitou = $false
    try { & $Acao | Out-Null } catch { $rejeitou = $true }
    Exigir-D4 $rejeitou $Descricao
}
$catalogo = Assert-21ED4-Contexto -Raiz $raiz
Write-Host 'PASS - Politicas preparation_only/inventory_only, catalogo e supervisao verificados.'

# Primeiro cenario: falha real do validador de escrita em sessao lacrada.
$dir = New-21EB1-Sandbox -Raiz $raiz
$sessao = New-21ED2-SessaoOffline -Diretorio $dir -Catalogo $catalogo `
    -MaximoSegmentoBytes 512 -MaximoTotalBytes 2048
$def = @($catalogo.events | Where-Object { $_.code -ceq 'processing.job.claimed' })[0]
$evento = ConvertTo-Json -Compress -InputObject ([ordered]@{
    timestamp=[DateTimeOffset]::UtcNow.ToString('o');level=$def.level;event=$def.code;
    component=$def.component;operation=$def.operation;outcome='CLAIMED';runId=7;jobId=11
})
Write-21ED2-EventoCatalogado -Sessao $sessao -LinhaJson $evento
$filho = $null
$filhoConcluido = $false
try {
    $filho = Start-21ED4-FilhoSintetico -Diretorio $dir -PrazoVidaSegundos 10
    Wait-21ED4-FilhoPronto -Processo $filho -Diretorio $dir -PrazoSegundos 6
    $falhaGravacao = $false
    try {
        # Um campo que nao pertence ao contrato provoca falha de escrita;
        # o escritor D2 lacra a sessao SEM copiar esse valor.
        Write-21ED2-EventoCatalogado -Sessao $sessao `
            -LinhaJson '{"timestamp":"2026-10-27T20:00:00Z","secret":"SEGREDO_SINTETICO_D4"}'
    } catch { $falhaGravacao = $true }
    Exigir-D4 ($falhaGravacao -and $sessao.Lacrada) 'falha-lacra-escritor'
    $pedido = New-21ED4-PedidoParada -Sessao $sessao
    Exigir-D4 (Test-Path -LiteralPath $pedido -PathType Leaf) 'pedido-privado-criado'
    $codigo = Wait-21ED4-EncerramentoNatural -Processo $filho -PrazoSegundos 5
    $filhoConcluido = $true
    Exigir-D4 ($codigo -eq 0) 'filho-reconheceu-parada-cooperativa'
    Rejeitar-D4 { New-21ED4-PedidoParada -Sessao $sessao } 'pedido-duplicado-rejeitado'
    Rejeitar-D4 { Write-21ED2-EventoCatalogado -Sessao $sessao -LinhaJson $evento } 'escrita-pos-lacre'
    Rejeitar-D4 {
        Assert-21ED4-CaminhoSinal -Diretorio $dir -Caminho 'C:\dev\rasping-amazon-backups\supervisao\parada-sintetica-d4.request'
    } 'pedido-fora-sandbox'
    $segmentos = @(Get-ChildItem -LiteralPath $dir -Filter 'operacional-*.jsonl' -File)
    $linhas = @($segmentos | ForEach-Object { Get-Content -LiteralPath $_.FullName -Encoding UTF8 })
    Exigir-D4 ($segmentos.Count -eq 1 -and $linhas.Count -eq 1) 'evidencia-anterior-preservada'
    $registro = ConvertFrom-Json -InputObject $linhas[0]
    Exigir-D4 ($registro.event -ceq $def.code -and $registro.jobId -eq 11) 'correlacao-preservada'
    Exigir-D4 (-not (($linhas -join "`n") -match 'SEGREDO_SINTETICO_D4')) 'nenhum-segredo-no-segmento'
    Write-Host 'PASS - Falha real de validacao lacrou o escritor e gerou pedido privado de parada.'
    Write-Host 'PASS - Filho PowerShell reconheceu pedido e encerrou naturalmente com codigo 0.'
    Write-Host 'PASS - Pedido duplicado e caminho externo rejeitados; evidencia anterior intacta.'
} finally {
    if ($null -ne $filho) {
        try {
            if (-not $filhoConcluido -and -not $filho.HasExited) {
                # O filho tem prazo autonomo de vida; nao forcamos seu encerramento.
                [void]$filho.WaitForExit(12000)
            }
        } finally { $filho.Dispose() }
    }
    if (-not $sessao.Lacrada) { Close-21ED2-Sessao -Sessao $sessao }
}

# Segundo cenario: sem falha de escrita, nao deve existir pedido de parada.
$dirNatural = New-21EB1-Sandbox -Raiz $raiz
$sessaoNatural = New-21ED2-SessaoOffline -Diretorio $dirNatural -Catalogo $catalogo `
    -MaximoSegmentoBytes 512 -MaximoTotalBytes 2048
$filhoNatural = $null
try {
    $filhoNatural = Start-21ED4-FilhoSintetico -Diretorio $dirNatural -PrazoVidaSegundos 2
    Wait-21ED4-FilhoPronto -Processo $filhoNatural -Diretorio $dirNatural -PrazoSegundos 6
    $codigoNatural = Wait-21ED4-EncerramentoNatural -Processo $filhoNatural -PrazoSegundos 5
    Exigir-D4 ($codigoNatural -eq 75) 'filho-expirou-sem-sinal'
    Exigir-D4 (-not (Test-Path -LiteralPath (Join-Path $dirNatural 'parada-sintetica-d4.request'))) 'sem-pedido-espurio'
    Rejeitar-D4 { New-21ED4-PedidoParada -Sessao $sessaoNatural } 'nao-sinalizar-sessao-saudavel'
    Write-Host 'PASS - Filho sem falha terminou pelo seu prazo; nenhum pedido espurio criado.'
} finally {
    if ($null -ne $filhoNatural) {
        try {
            if (-not $filhoNatural.HasExited) { [void]$filhoNatural.WaitForExit(5000) }
        } finally { $filhoNatural.Dispose() }
    }
    Close-21ED2-Sessao -Sessao $sessaoNatural
}

Rejeitar-D4 {
    New-21ED2-SessaoProdutiva -Diretorio 'C:\dev\rasping-amazon-operacao\logs'
} 'escritor-produtivo-proibido'
Write-Host 'PROTEGIDO - Apenas filhos PowerShell sinteticos; nenhum Kill, taskkill, daemon, Java, PostgreSQL ou backup.'
Write-Host 'PROTEGIDO - Evidencias permanecem em target; sem exclusoes nem logs operacionais externos.'
Write-Host 'PASS - Gate 21-E-D4 offline aprovado; integracao real e captura produtiva continuam bloqueadas.'
