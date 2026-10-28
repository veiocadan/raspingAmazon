# FASE 21-G - distribuir a CLI Java sem ativar runtime, banco ou canais externos.
# Execucao a partir da raiz do repositorio. Somente escreve em target/.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Exigir-21G([bool]$Condicao, [string]$Mensagem) {
    if (-not $Condicao) { throw ('21-G: ' + $Mensagem) }
}

$branch = ((& git branch --show-current) | Out-String).Trim()
Exigir-21G ($LASTEXITCODE -eq 0 -and $branch -ceq 'feat/fase-21-seguranca-governanca-v1') 'branch inesperada'

foreach ($caminho in @(
    'config/operacao/continuous-startup-policy.v1.json',
    'config/operacao/retention-policy.v1.json',
    'infra/release/FASE_21_G_INICIAR_CLI.ps1',
    'docs/operacao/FASE_21_G_OPERACAO_DISTRIBUICAO.md',
    'mvnw.cmd', 'pom.xml', 'infra/compose.yml'
)) {
    Exigir-21G (Test-Path -LiteralPath $caminho -PathType Leaf) ('dependencia ausente: ' + $caminho)
}

$startup = Get-Content -LiteralPath 'config/operacao/continuous-startup-policy.v1.json' -Raw -Encoding UTF8 | ConvertFrom-Json
$retencao = Get-Content -LiteralPath 'config/operacao/retention-policy.v1.json' -Raw -Encoding UTF8 | ConvertFrom-Json
Exigir-21G ($startup.execution_mode -ceq 'preparation_only' -and
    $startup.daemon_start_authorized -ceq $false -and
    $startup.flyway_migrate_authorized -ceq $false -and
    $startup.external_actions_authorized -ceq $false -and
    $startup.automatic_restart_authorized -ceq $false) 'startup nao esta bloqueado'
Exigir-21G ($retencao.execution_mode -ceq 'inventory_only' -and
    $retencao.destructive_actions_authorized -ceq $false -and
    $retencao.continuous_log_capture_authorized -ceq $false) 'retencao nao esta bloqueada'

& git diff --check
Exigir-21G ($LASTEXITCODE -eq 0) 'git diff --check reprovado'
Write-Host 'PASS - Branch, politicas preparation_only/inventory_only e Git confirmados.'

[xml]$pom = Get-Content -LiteralPath 'pom.xml' -Raw -Encoding UTF8
$versaoNode = $pom.SelectSingleNode("/*[local-name()='project']/*[local-name()='version']")
$artefatoNode = $pom.SelectSingleNode("/*[local-name()='project']/*[local-name()='artifactId']")
Exigir-21G ($null -ne $versaoNode -and $null -ne $artefatoNode) 'metadados Maven ausentes'
$versao = [string]$versaoNode.InnerText
$artefato = [string]$artefatoNode.InnerText
Exigir-21G ($versao -ceq '1.0-SNAPSHOT' -and $artefato -ceq 'rasping-amazon') 'versao/artefato Maven inesperados; nao declarar release final'

$wrapper = (Resolve-Path -LiteralPath '.\mvnw.cmd').ProviderPath
& $wrapper '-o' '-q' '-DskipTests' 'package'
Exigir-21G ($LASTEXITCODE -eq 0) 'Maven package offline falhou'

$raizTarget = (Resolve-Path -LiteralPath 'target').ProviderPath
$jarOrigem = Join-Path $raizTarget ($artefato + '-' + $versao + '.jar')
Exigir-21G (Test-Path -LiteralPath $jarOrigem -PathType Leaf) 'JAR principal nao gerado'

$identificador = [guid]::NewGuid().ToString('N')
$classpathFile = Join-Path $raizTarget ('fase21g-classpath-' + $identificador + '.txt')
& $wrapper '-o' '-q' '-DincludeScope=runtime' ('-Dmdep.outputFile=' + $classpathFile) 'dependency:build-classpath'
Exigir-21G ($LASTEXITCODE -eq 0) 'classpath de runtime nao gerado (plugin Maven pode nao estar no cache offline)'
Exigir-21G (Test-Path -LiteralPath $classpathFile -PathType Leaf) 'arquivo de classpath ausente'

$classpath = [System.IO.File]::ReadAllText($classpathFile).Trim()
Exigir-21G (-not [string]::IsNullOrWhiteSpace($classpath)) 'classpath vazio'
$dependencias = @($classpath.Split([char]';') | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
Exigir-21G ($dependencias.Count -gt 0) 'nenhuma biblioteca de runtime localizada'

$diretorioPacote = Join-Path $raizTarget ('rasping-amazon-distribuicao-21g-' + $identificador)
$diretorioLib = Join-Path $diretorioPacote 'lib'
[void][System.IO.Directory]::CreateDirectory($diretorioLib)
$jarDestino = Join-Path $diretorioPacote ($artefato + '-' + $versao + '.jar')
Copy-Item -LiteralPath $jarOrigem -Destination $jarDestino -ErrorAction Stop
Exigir-21G ((Get-FileHash -LiteralPath $jarOrigem -Algorithm SHA256).Hash -ceq
    (Get-FileHash -LiteralPath $jarDestino -Algorithm SHA256).Hash) 'JAR copiado incorretamente'

$nomesVistos = @{}
foreach ($dependencia in $dependencias) {
    $caminho = [System.IO.Path]::GetFullPath($dependencia.Trim())
    Exigir-21G (Test-Path -LiteralPath $caminho -PathType Leaf) 'biblioteca Maven indisponivel'
    Exigir-21G ([System.IO.Path]::GetExtension($caminho) -ceq '.jar') 'entrada de classpath nao e JAR'
    $nome = [System.IO.Path]::GetFileName($caminho)
    Exigir-21G (-not $nomesVistos.ContainsKey($nome)) ('bibliotecas com mesmo nome: ' + $nome)
    $nomesVistos[$nome] = $true
    $destino = Join-Path $diretorioLib $nome
    Copy-Item -LiteralPath $caminho -Destination $destino -ErrorAction Stop
    Exigir-21G ((Get-FileHash -LiteralPath $caminho -Algorithm SHA256).Hash -ceq
        (Get-FileHash -LiteralPath $destino -Algorithm SHA256).Hash) ('biblioteca copiada incorretamente: ' + $nome)
}

Copy-Item -LiteralPath 'infra/release/FASE_21_G_INICIAR_CLI.ps1' -Destination (Join-Path $diretorioPacote 'INICIAR_CLI.ps1') -ErrorAction Stop
Copy-Item -LiteralPath 'docs/operacao/FASE_21_G_OPERACAO_DISTRIBUICAO.md' -Destination (Join-Path $diretorioPacote 'LEIA_PRIMEIRO.md') -ErrorAction Stop
Copy-Item -LiteralPath '.env.example' -Destination (Join-Path $diretorioPacote '.env.example') -ErrorAction Stop

$manifestoArquivos = @()
foreach ($arquivo in @(Get-ChildItem -LiteralPath $diretorioPacote -File -Recurse)) {
    $relativo = $arquivo.FullName.Substring($diretorioPacote.Length + 1).Replace('\','/')
    $manifestoArquivos += [ordered]@{
        path = $relativo
        bytes = [long]$arquivo.Length
        sha256 = (Get-FileHash -LiteralPath $arquivo.FullName -Algorithm SHA256).Hash
    }
}
$manifesto = [ordered]@{
    schema_version = 1
    package_type = 'pre_release_local_cli'
    maven_version = $versao
    branch = $branch
    created_utc = [DateTime]::UtcNow.ToString('o')
    files = @($manifestoArquivos | Sort-Object path)
}
$manifesto | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $diretorioPacote 'MANIFESTO.json') -Encoding UTF8

# Gate funcional minimo: apenas ajuda da CLI. Nao abre conexoes nem cria scheduler.
$launcher = Join-Path $diretorioPacote 'INICIAR_CLI.ps1'
$saidaAjuda = (& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $launcher 2>&1 | Out-String)
Exigir-21G ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($saidaAjuda)) 'smoke test de ajuda da CLI falhou'
Write-Host ('PASS - CLI empacotada; bibliotecas de runtime=' + $dependencias.Count + '; ajuda sem banco aprovada.')

$zip = $diretorioPacote + '.zip'
Exigir-21G (-not (Test-Path -LiteralPath $zip)) 'ZIP ja existe'
Compress-Archive -LiteralPath $diretorioPacote -DestinationPath $zip -CompressionLevel Optimal -ErrorAction Stop
Exigir-21G (Test-Path -LiteralPath $zip -PathType Leaf) 'ZIP nao criado'
Write-Host 'PASS - Distribuicao pre-release criada somente sob target, sem segredos.'
Write-Host ('ZIP=' + $zip)
Write-Host ('SHA256=' + (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash)
Write-Host 'PROTEGIDO - Sem daemon, SQL, Flyway migrate, captura produtiva, rede externa ou exclusoes.'
Write-Host 'PASS - Gate 21-G de montagem e ajuda da CLI aprovado; tag v1.0.0 ainda proibida.'
