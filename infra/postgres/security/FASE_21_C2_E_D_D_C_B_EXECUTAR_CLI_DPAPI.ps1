# FASE 21-C2-E-D-D-C-B - executa CLI REAL, sem Maven JUnit probe.
# Apenas comandos operacionais de leitura. Nunca escreve .env ou arquivo de segredo.
param(
    [string]$DiretorioSegredos = 'C:\dev\rasping-amazon-backups\credenciais_c2edcb',
    [string]$NomeContainer = 'rasping-amazon-postgres',
    [switch]$ConfirmarExecucao
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not $ConfirmarExecucao) {
    throw 'C2-E-D-D-C-B: este script requer -ConfirmarExecucao apos PRECHECK PASS'
}

function ExigirSucesso([string]$Mensagem) {
    if ($LASTEXITCODE -ne 0) { throw $Mensagem }
}

function LerSegredo([string]$Caminho) {
    if (-not (Test-Path -LiteralPath $Caminho -PathType Leaf)) {
        throw 'C2-E-D-D-C-B: arquivo DPAPI ausente; nao reprovisionar'
    }
    $texto = [System.IO.File]::ReadAllText($Caminho).Trim()
    $seguro = ConvertTo-SecureString -String $texto
    $ponteiro = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($seguro)
    try {
        $senha = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($ponteiro)
        if ($senha -cnotmatch '^[0-9A-F]{64}$') {
            throw 'C2-E-D-D-C-B: credencial DPAPI invalida'
        }
        return $senha
    } finally {
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ponteiro)
    }
}

function InvocarMavenOculto([string[]]$Argumentos, [string]$Erro) {
    # Intercepta toda a saida em memoria. Nao revelar nem salvar dados operacionais
    # ou mensagens externas em erro: podem conter historico de origem/SQL.
    $prefAnterior = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $saida = (& mvn @Argumentos 2>&1 | Out-String)
        $codigo = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prefAnterior
    }
    if ($codigo -ne 0) {
        throw $Erro
    }
    return $saida
}

$precheck = 'infra/postgres/security/FASE_21_C2_E_D_D_C_B_PRECHECK_CLI.ps1'
if (-not (Test-Path -LiteralPath $precheck -PathType Leaf)) {
    throw 'C2-E-D-D-C-B: arquivo de precheck nao encontrado'
}
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $precheck -DiretorioSegredos $DiretorioSegredos -NomeContainer $NomeContainer
ExigirSucesso 'C2-E-D-D-C-B: precheck reprovado; a CLI nao foi executada'

# Respeitar valores de ambiente que ja existiam, inclusive os ausentes (null).
$variaveis = @('DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD',
               'DB_MIGRATION_USER','DB_MIGRATION_PASSWORD')
$anteriores = @{}
foreach ($chave in $variaveis) {
    $anteriores[$chave] = [System.Environment]::GetEnvironmentVariable($chave,'Process')
}

# Proteger uma eventual configuracao .env existente contra alteracoes acidentais.
$envExiste = Test-Path -LiteralPath '.env' -PathType Leaf
$envHashAntes = $null
if ($envExiste) {
    $envHashAntes = (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash
}

$runtime = $null
$migrator = $null
try {
    $diretorio = [System.IO.Path]::GetFullPath($DiretorioSegredos)
    $runtime = LerSegredo (Join-Path $diretorio 'rasping_runtime.dpapi')
    $migrator = LerSegredo (Join-Path $diretorio 'rasping_migrator.dpapi')
    if ($runtime -ceq $migrator) {
        throw 'C2-E-D-D-C-B: senhas das identidades nao podem ser iguais'
    }

    [System.Environment]::SetEnvironmentVariable('DB_HOST','127.0.0.1','Process')
    [System.Environment]::SetEnvironmentVariable('DB_PORT','5432','Process')
    [System.Environment]::SetEnvironmentVariable('DB_NAME','rasping_amazon','Process')
    [System.Environment]::SetEnvironmentVariable('DB_USER','rasping_runtime','Process')
    [System.Environment]::SetEnvironmentVariable('DB_PASSWORD',$runtime,'Process')
    [System.Environment]::SetEnvironmentVariable('DB_MIGRATION_USER','rasping_migrator','Process')
    [System.Environment]::SetEnvironmentVariable('DB_MIGRATION_PASSWORD',$migrator,'Process')

    # Compilar sem executar os testes ou migracoes.
    $null = InvocarMavenOculto @('-q','-DskipTests','test-compile') 'C2-E-D-D-C-B: compilacao Maven falhou; CLI nao iniciada'
    Write-Host '=== C2-E-D-D-C-B COMPILACAO PASS - sem testes ou migracoes ==='

    # Executar a MAIN real via exec-maven-plugin. Todas as rotas sao de leitura.
    # O retorno de cada comando e testado pelo codigo de saida e pelo cabecalho.
    $consultas = @(
        @{ Nome='EVALUATIONS'; Argumentos='evaluations list --limit 1'; Cabecalho='EVALUATION_ID' },
        @{ Nome='RUNS';        Argumentos='runs list --limit 1';        Cabecalho='RUN_ID' },
        @{ Nome='JOBS';        Argumentos='jobs list --limit 1';        Cabecalho='JOB_ID' },
        @{ Nome='PUBLICATIONS';Argumentos='publications list --limit 1';Cabecalho='PUBLICATION_ID' },
        @{ Nome='ALERTS';      Argumentos='alerts list';               Cabecalho='ALERT_TYPE' }
    )

    foreach ($consulta in $consultas) {
        $argsMaven = @('-q', ('-Dexec.args=' + $consulta.Argumentos), 'exec:java')
        $saida = InvocarMavenOculto $argsMaven ('C2-E-D-D-C-B: falha ao executar ' + $consulta.Nome + '; nao divulgar saida bruta')
        # O cabecalho e emitido ate quando nenhuma linha de dados existe.
        $cabecalhoEsperado = '(?m)^' + [regex]::Escape($consulta.Cabecalho) + "`t"
        if ($saida -notmatch $cabecalhoEsperado) {
            throw ('C2-E-D-D-C-B: cabecalho inesperado na rota ' + $consulta.Nome)
        }
        Remove-Variable saida -ErrorAction SilentlyContinue
        Write-Host ('=== C2-E-D-D-C-B CLI ' + $consulta.Nome + ' PASS - processo Java real, consulta somente leitura ===')
    }
    Write-Host '=== C2-E-D-D-C-B EXECUCAO PASS - identidades separadas usadas pela CLI real ==='
} finally {
    foreach ($chave in $variaveis) {
        [System.Environment]::SetEnvironmentVariable($chave,$anteriores[$chave],'Process')
    }
    Remove-Variable runtime, migrator -ErrorAction SilentlyContinue
    Write-Host '=== C2-E-D-D-C-B AMBIENTE PASS - variaveis de processo restauradas ==='
    $envExisteDepois = Test-Path -LiteralPath '.env' -PathType Leaf
    if ($envExiste -ne $envExisteDepois) {
        throw 'C2-E-D-D-C-B: presenca do arquivo .env mudou durante a execucao'
    }
    if ($envExisteDepois) {
        $envHashDepois = (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash
        if ($envHashDepois -cne $envHashAntes) {
            throw 'C2-E-D-D-C-B: arquivo .env mudou durante a execucao'
        }
    }
}
