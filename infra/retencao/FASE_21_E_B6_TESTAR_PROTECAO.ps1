# FASE 21-E-B6 - provas OFFLINE de metadados livres e falha de escrita.
# Executar apenas na raiz do projeto, com -NoProfile -ExecutionPolicy Bypass.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$raiz = (Get-Location).ProviderPath
. (Join-Path $PSScriptRoot 'FASE_21_E_B1_FUNCOES_SEGMENTACAO.ps1')
. (Join-Path $PSScriptRoot 'FASE_21_E_B3_FUNCOES_COMPATIBILIDADE.ps1')
. (Join-Path $PSScriptRoot 'FASE_21_E_B6_FUNCOES_PROTECAO.ps1')

if (-not (Assert-21EB1-Contexto -Raiz $raiz)) {
    throw '21-E-B6: contexto protegido invalido'
}
Write-Host 'PASS - Contratos E-A, startup preparation_only e sandbox isolado.'

$segredo = 'SEGREDOFICTICIO21EB6ABCDEFGHIJKLMNOP'
# Texto que cumpre o padrao B3: o risco e semantico, nao de sintaxe.
$entrada = [ordered]@{
    timestamp = '2026-10-15T18:00:00Z'
    level = 'WARN'
    event = $segredo
    component = $segredo
    operation = $segredo
    outcome = $segredo
    jobId = 37
    runId = 9
    durationMs = 0
    integration = $segredo
    jobType = $segredo
    errorCode = $segredo
    failureOrigin = 'EXTERNAL'
    failureType = $segredo
}
$bruto = ConvertTo-Json -InputObject $entrada -Compress -Depth 3
if (-not (Test-21EB3-EventoJavaSeguro -Linha $bruto)) {
    throw '21-E-B6: caso adversarial deixou de passar pelo contrato B3'
}
$anterior = Convert-21EB3-LinhaConsole -Linha $bruto -Canal stdout
if ($anterior.Classificacao -cne 'STRUCTURED' -or
    -not $anterior.LinhaJson.Contains($segredo)) {
    throw '21-E-B6: risco anterior nao reproduzido em memoria'
}
Write-Host 'PASS - Risco identificado: texto livre valido seria mantido pela B3 isolada.'

$segura = Convert-21EB6-LinhaMinimizada -Linha $bruto -Canal stdout
if ($segura.Classificacao -cne 'STRUCTURED_MINIMIZED' -or
    $segura.LinhaJson.Contains($segredo)) {
    throw '21-E-B6: projecao manteve dado textual livre'
}
$projetado = $segura.LinhaJson | ConvertFrom-Json -ErrorAction Stop
if ($projetado.timestamp -cne '2026-10-15T18:00:00Z' -or
    $projetado.level -cne 'WARN' -or
    $projetado.jobId -ne 37 -or $projetado.runId -ne 9 -or
    $projetado.durationMs -ne 0 -or
    $projetado.event -cne 'runtime.stdout.structured_minimized' -or
    $projetado.PSObject.Properties.Name -ccontains 'integration' -or
    $projetado.PSObject.Properties.Name -ccontains 'errorCode') {
    throw '21-E-B6: dados minimos esperados nao foram respeitados'
}
Write-Host 'PASS - Projecao retira strings livres; preserva data, nivel e correlacao numerica.'

$marcadorOut = Convert-21EB6-LinhaMinimizada -Linha ('TOKEN=' + $segredo) -Canal stdout
$marcadorErr = Convert-21EB6-LinhaMinimizada -Linha ('Exception: ' + $segredo) -Canal stderr
if ($marcadorOut.Classificacao -cne 'REDACTED' -or
    $marcadorErr.Classificacao -cne 'REDACTED' -or
    $marcadorOut.LinhaJson.Contains($segredo) -or
    $marcadorErr.LinhaJson.Contains($segredo)) {
    throw '21-E-B6: saida desconhecida nao foi redigida'
}
Write-Host 'PASS - Stdout e stderr desconhecidos produzem marcadores sem texto bruto.'

$diretorio = New-21EB1-Sandbox -Raiz $raiz
$sessao = New-21EB1-Sessao -Diretorio $diretorio -MaximoSegmentoBytes 1024 -MaximoTotalBytes 4096
Write-21EB6-EventoLacrado -Sessao $sessao -LinhaJson $segura.LinhaJson
$segmento = Join-Path $diretorio 'segmento-000001.jsonl'
if (-not (Test-Path -LiteralPath $segmento -PathType Leaf)) {
    throw '21-E-B6: evidencia inicial ausente'
}
# O FileStream da B1 permanece aberto para gravação nesta etapa.
# Get-FileHash pode negar acesso ao arquivo ainda aberto no Windows.
# Em vez de ler simultaneamente o segmento, derivamos o hash esperado
# dos bytes UTF-8 exatos enviados ao escritor B1 (sem BOM, com LF).
$bytesEsperados = [System.Text.UTF8Encoding]::new($false, $true).GetBytes(
    $segura.LinhaJson + "`n"
)
$sha256 = [System.Security.Cryptography.SHA256]::Create()
try {
    $antesHash = [System.BitConverter]::ToString(
        $sha256.ComputeHash($bytesEsperados)
    ).Replace('-', '')
} finally {
    $sha256.Dispose()
}
$antesBytes = (Get-Item -LiteralPath $segmento -ErrorAction Stop).Length
if ($antesBytes -ne $bytesEsperados.Length -or $antesBytes -le 0) {
    throw '21-E-B6: tamanho da evidencia inicial diverge dos bytes esperados'
}

# Injecao offline e deterministica: o fluxo ja aberto torna-se indisponivel.
# Nao muda ACLs, nem toca em logs reais, disco do sistema ou supervisor.
$sessao.Fluxo.Dispose()
$falhou = $false
try {
    Write-21EB6-EventoLacrado -Sessao $sessao -LinhaJson $marcadorOut.LinhaJson
} catch {
    if ($_.Exception.Message -cne '21-E-B6: falha de persistencia; sessao lacrada sem exclusao') {
        throw
    }
    $falhou = $true
}
if (-not $falhou -or -not $sessao.Encerrada -or $null -ne $sessao.Fluxo) {
    throw '21-E-B6: falha de escrita nao lacrou a sessao'
}
$rejeitada = $false
try {
    Write-21EB6-EventoLacrado -Sessao $sessao -LinhaJson $marcadorErr.LinhaJson
} catch {
    if ($_.Exception.Message -cne '21-E-B6: sessao lacrada, gravacao proibida') { throw }
    $rejeitada = $true
}
if (-not $rejeitada) {
    throw '21-E-B6: nova escrita apos falha foi autorizada'
}
$depoisHash = (Get-FileHash -LiteralPath $segmento -Algorithm SHA256).Hash
if ($antesHash -cne $depoisHash) {
    throw '21-E-B6: evidencia anterior mudou depois da falha'
}
$registros = [IO.File]::ReadAllLines($segmento,[Text.Encoding]::UTF8)
if ($registros.Count -ne 1 -or $registros[0].Contains($segredo) -or
    @(Get-ChildItem -LiteralPath $diretorio -File -Force).Count -ne 1) {
    throw '21-E-B6: evidencia inesperada ou dado sigiloso persistido'
}
[void]($registros[0] | ConvertFrom-Json -ErrorAction Stop)
Write-Host 'PASS - Falha simulada de fluxo lacrou a sessao e rejeitou novas gravacoes.'
Write-Host 'PASS - Evidencia anterior conservada byte a byte; nenhum segredo persistido.'
Write-Host 'PROTEGIDO - Apenas novo cenario em target; sem daemon, PostgreSQL, backups ou exclusao.'
Write-Host 'PASS - 21-E-B6 offline aprovado; wrappers ainda NAO integrados ao capturador B4.'
