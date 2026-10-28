# FASE 21-E-D9: identidade de sessao, escritor seguro e lacre, somente offline.
# Nao inicia processos e nao consulta diretorio de logs operacional externo.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$raiz = (Get-Location).ProviderPath
foreach ($arquivo in @(
    'infra/retencao/FASE_21_E_A_FUNCOES_RETENCAO.ps1',
    'infra/retencao/FASE_21_E_B1_FUNCOES_SEGMENTACAO.ps1',
    'infra/retencao/FASE_21_E_B8_FUNCOES_CATALOGO.ps1',
    'infra/retencao/FASE_21_E_D1_FUNCOES_PREINTEGRACAO.ps1',
    'infra/retencao/FASE_21_E_D2_FUNCOES_ESCRITOR_OFFLINE.ps1',
    'infra/retencao/FASE_21_E_D8_FUNCOES_PREPARACAO_EXTERNA.ps1',
    'infra/retencao/FASE_21_E_D9_FUNCOES_ADAPTADOR_ISOLADO.ps1'
)) {
    if (-not (Test-Path -LiteralPath $arquivo -PathType Leaf)) {
        throw '21-E-D9: dependencia local ausente'
    }
    . (Resolve-Path -LiteralPath $arquivo -ErrorAction Stop).ProviderPath
}
function Exigir-D9 {
    param([bool]$Condicao,[string]$Nome)
    if (-not $Condicao) { throw ('21-E-D9: assercao reprovada: ' + $Nome) }
}
function Recusar-D9 {
    param([scriptblock]$Acao,[string]$Nome)
    $rejeitou = $false
    try { & $Acao | Out-Null } catch { $rejeitou = $true }
    Exigir-D9 $rejeitou $Nome
}
$catalogo = Assert-21ED9-Contexto -Raiz $raiz
Exigir-D9 (@($catalogo.events).Count -eq 6) 'seis-eventos-autorizados'
Write-Host 'PASS - Politicas, catalogo, fontes D1 e branch validados; captura produtiva bloqueada.'

$proposto = 'C:\dev\rasping-amazon-operacao\logs'
Recusar-D9 { New-21ED9-AdaptadorProdutivo -DiretorioExterno $proposto } 'adaptador-produtivo-bloqueado'
foreach ($modelo in @(
    @{ Pid = 0; Ticks = [long]123; Nonce = '1234567890abcdef1234567890abcdef' },
    @{ Pid = 10; Ticks = [long]0; Nonce = '1234567890abcdef1234567890abcdef' },
    @{ Pid = 10; Ticks = [long]123; Nonce = '1234567890abcdef1234567890ABCDEf' },
    @{ Pid = 10; Ticks = [long]123; Nonce = '1234567890abcdef1234567890abcde' }
)) {
    Recusar-D9 {
        Assert-21ED9-IdentidadePlanejada -PidSimulado $modelo.Pid `
            -CreationTicksSimulados $modelo.Ticks -Nonce $modelo.Nonce
    } 'identidade-adulterada'
}
Write-Host 'PASS - Abertura produtiva e identidades incompletas/adulteradas recusadas.'

$nonce = [guid]::NewGuid().ToString('N')
$a = New-21ED9-AdaptadorEnsaio -Raiz $raiz -PidSimulado 123 `
    -CreationTicksSimulados 638975520000000000 -Nonce $nonce `
    -MaximoSegmentoBytes 256 -MaximoTotalBytes 1024
$fechado = $false
try {
    Exigir-D9 ($a.Modo -ceq 'ensaio_sandbox_only' -and $a.Estado -ceq 'ABERTA') 'estado-aberto'
    Exigir-D9 (Assert-21ED9-AdaptadorVivo -Adaptador $a) 'adaptador-original'
    # Adaptador falso com propriedades copiadas nao deve possuir a mesma identidade em memoria.
    $falso = $a | Select-Object *
    Recusar-D9 { Assert-21ED9-AdaptadorVivo -Adaptador $falso } 'objeto-impessoal'
    Recusar-D9 {
        New-21ED9-AdaptadorEnsaio -Raiz $raiz -PidSimulado 123 `
            -CreationTicksSimulados 638975520000000000 -Nonce $nonce `
            -MaximoSegmentoBytes 256 -MaximoTotalBytes 1024
    } 'identidade-reutilizada'
    $dt = [DateTimeOffset]::UtcNow.ToString('o')
    $def = $catalogo.events[0]
    $linha = ConvertTo-Json -InputObject ([ordered]@{
        timestamp=$dt; level=$def.level; event=$def.code;
        component=$def.component; operation=$def.operation;
        outcome=@($def.outcomes)[0]; runId=7; jobId=11
    }) -Compress
    $marcador = ConvertTo-Json -InputObject ([ordered]@{
        timestamp=$dt; level='ERROR'; event='runtime.stderr.redacted';
        component='runtime-capture';operation='safe-catalog-classification';outcome='REDACTED'
    }) -Compress
    Write-21ED9-RegistroEnsaio -Adaptador $a -LinhaJson $linha
    Write-21ED9-RegistroEnsaio -Adaptador $a -LinhaJson $marcador
    Exigir-D9 ($a.Registros -eq 2 -and $a.Sessao.TotalBytes -gt 0) 'escrita-dupla'
    $segmentos = @(Get-ChildItem -LiteralPath $a.Diretorio -File -Filter 'operacional-*.jsonl' | Sort-Object Name)
    $linhas = @($segmentos | ForEach-Object { Get-Content -LiteralPath $_.FullName -Encoding UTF8 })
    Exigir-D9 ($segmentos.Count -eq 2 -and $linhas.Count -eq 2) 'segmentos-exclusivos'
    Exigir-D9 ((ConvertFrom-Json -InputObject $linhas[0]).event -ceq 'processing.job.claimed') 'catalogado'
    Exigir-D9 ((ConvertFrom-Json -InputObject $linhas[1]).event -ceq 'runtime.stderr.redacted') 'marcador'
    Exigir-D9 ($a.Sessao.TotalBytes -eq [long](($segmentos | Measure-Object Length -Sum).Sum)) 'integridade-bytes'
    Write-Host 'PASS - Sessao privada e exclusiva: 2 registros em 2 segmentos; bytes conferidos.'
    $hash = (Get-FileHash -LiteralPath $segmentos[0].FullName -Algorithm SHA256).Hash
    $malicioso = '{"timestamp":"2026-10-27T20:00:00Z","secret":"TOKEN_FICTICIO_D9"}'
    Recusar-D9 { Write-21ED9-RegistroEnsaio -Adaptador $a -LinhaJson $malicioso } 'entrada-sensivel'
    Exigir-D9 ($a.Estado -ceq 'LACRADA' -and $a.Sessao.Lacrada) 'lacre-duplo'
    Recusar-D9 { Write-21ED9-RegistroEnsaio -Adaptador $a -LinhaJson $linha } 'sem-reabertura'
    Exigir-D9 ((Get-FileHash -LiteralPath $segmentos[0].FullName -Algorithm SHA256).Hash -ceq $hash) 'sem-alteracao-do-anterior'
    Exigir-D9 (-not (($linhas -join "`n") -match 'TOKEN_FICTICIO_D9|secret')) 'sem-texto-sensivel'
    Write-Host 'PASS - Evento indevido lacrou o adaptador; evidencias anteriores intactas.'
    Close-21ED9-AdaptadorEnsaio -Adaptador $a
    $fechado = $true
    Recusar-D9 { Write-21ED9-RegistroEnsaio -Adaptador $a -LinhaJson $linha } 'apos-fechar'
    Recusar-D9 { New-21ED2-SessaoOffline -Diretorio $a.Diretorio -Catalogo $catalogo -MaximoSegmentoBytes 256 -MaximoTotalBytes 1024 } 'diretorio-consumido'
    Write-Host 'PASS - Identidade e diretorio nao reutilizaveis; fechamento irreversivel.'
} finally {
    if (-not $fechado) { Close-21ED9-AdaptadorEnsaio -Adaptador $a }
}

$nonce2 = [guid]::NewGuid().ToString('N')
$b = New-21ED9-AdaptadorEnsaio -Raiz $raiz -PidSimulado 123 `
    -CreationTicksSimulados 638975520000000000 -Nonce $nonce2 `
    -MaximoSegmentoBytes 256 -MaximoTotalBytes 1024
try {
    Exigir-D9 ($b.Diretorio -cne $a.Diretorio -and $b.Identidade -cne $a.Identidade) 'sessao-nova-independente'
    Exigir-D9 ((Get-ChildItem -LiteralPath $b.Diretorio -Force | Measure-Object).Count -eq 0) 'sessao-nova-vazia'
} finally { Close-21ED9-AdaptadorEnsaio -Adaptador $b }
Write-Host 'PASS - Novo nonce isolou a sessao sem reabrir historico anterior.'
Write-Host 'PROTEGIDO - Apenas diretorios cenario-* privados em target; sem Java, daemon, rede, banco, backup, logs externos ou exclusoes.'
Write-Host 'PASS - Gate 21-E-D9 offline aprovado; adaptador operacional produtivo permanece NAO IMPLEMENTADO/BLOQUEADO.'
