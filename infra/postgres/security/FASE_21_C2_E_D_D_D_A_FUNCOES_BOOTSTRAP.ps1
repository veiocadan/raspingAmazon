# FASE 21-C2-E-D-D-D-A - Funcoes do gate de bootstrap (somente leitura).
# Nao carrega nem converte senhas DPAPI; esse contrato cabe ao precheck anterior.
Set-StrictMode -Version Latest

function Exigir-ArquivoLocal {
    param([Parameter(Mandatory=$true)][string]$Caminho)
    if (-not (Test-Path -LiteralPath $Caminho -PathType Leaf)) {
        throw ('C2-E-D-D-D-A: arquivo obrigatorio nao encontrado: ' + $Caminho)
    }
}

function Ler-FonteSemSegredos {
    param([Parameter(Mandatory=$true)][string]$Caminho)
    Exigir-ArquivoLocal -Caminho $Caminho
    return [System.IO.File]::ReadAllText(
        [System.IO.Path]::GetFullPath($Caminho),
        [System.Text.Encoding]::UTF8
    )
}

function Exigir-Trecho {
    param(
        [Parameter(Mandatory=$true)][string]$Conteudo,
        [Parameter(Mandatory=$true)][string]$Expressao,
        [Parameter(Mandatory=$true)][string]$Descricao
    )
    if ($Conteudo -notmatch $Expressao) {
        throw ('C2-E-D-D-D-A: contrato Java inesperado: ' + $Descricao)
    }
}

function Verificar-ContratoBootstrap {
    $base = 'src/main/java/com/raspingamazon/infrastructure/'
    $config = Ler-FonteSemSegredos ($base + 'config/EnvironmentConfigProvider.java')
    $migracao = Ler-FonteSemSegredos ($base + 'migration/DatabaseMigration.java')
    $bootstrap = Ler-FonteSemSegredos ($base + 'bootstrap/ContinuousProcessingBootstrap.java')
    $principal = Ler-FonteSemSegredos ($base + 'bootstrap/ContinuousProcessingMain.java')
    $cli = Ler-FonteSemSegredos ($base + 'bootstrap/OperationalCliBootstrap.java')

    Exigir-Trecho $config 'DB_MIGRATION_USER' 'usuario independente para Flyway'
    Exigir-Trecho $config 'DB_MIGRATION_PASSWORD' 'senha independente para Flyway'
    Exigir-Trecho $migracao 'databaseMigrationUser\s*\(' 'Flyway com usuario migrator'
    Exigir-Trecho $migracao 'databaseMigrationPassword\s*\(' 'Flyway com senha migrator'
    Exigir-Trecho $migracao 'flyway\.migrate\s*\(' 'migracao somente sob invocacao explicita'

    # A CLI de leitura nao equivale ao bootstrap do daemon.
    Exigir-Trecho $cli 'OperationalInterfaceComposition\.open\s*\(' 'CLI usa composicao propria'
    Exigir-Trecho $bootstrap 'EnvironmentConfigProvider\.load\s*\(' 'daemon usa contrato da aplicacao'
    Exigir-Trecho $bootstrap 'DatabaseConnection\.open\s*\(' 'scheduler e worker abrem JDBC runtime'
    Exigir-Trecho $bootstrap 'EnsureProcessingScheduleUseCase' 'daemon possui inicializacao de schedule com possivel escrita'
    Exigir-Trecho $bootstrap '(?s)startupRecoveryService\s*\(\s*\)\s*\.\s*recover\s*\(' 'daemon executa recuperacao duravel no bootstrap'
    Exigir-Trecho $principal 'application\.start\s*\(' 'daemon inicia runtime efetivo'
    Exigir-Trecho $principal 'application\.awaitTermination\s*\(' 'daemon permanece em execucao'

    # Nao autorizar migracoes implicitas pelo daemon antes de gate especifico.
    if ($bootstrap -match '\bDatabaseMigration\s*\.\s*migrate\s*\(' -or
        $principal -match '\bDatabaseMigration\s*\.\s*migrate\s*\(') {
        throw 'C2-E-D-D-D-A: invocacao Flyway no daemon requer revisao arquitetural antes da partida'
    }

    Write-Host '=== C2-E-D-D-D-A IDENTIDADES PASS - JDBC runtime e Flyway migrator com contratos distintos ==='
    Write-Host '=== C2-E-D-D-D-A EFEITOS PASS - boot do daemon reconhecido como operacao com escritas e recovery ==='
    Write-Host '=== C2-E-D-D-D-A MIGRACAO PASS - nenhuma migracao implicita localizada nos entrypoints do daemon ==='
}

function Verificar-BackupPreexistente {
    param([Parameter(Mandatory=$true)][string]$CaminhoBackupBanco)

    Exigir-ArquivoLocal -Caminho $CaminhoBackupBanco
    $arquivo = Get-Item -LiteralPath $CaminhoBackupBanco
    if ([long]$arquivo.Length -ne 143273) {
        throw 'C2-E-D-D-D-A: tamanho do backup PostgreSQL original divergiu'
    }
    $esperado = 'CBD926A9FEDAD4A20106925EAB9803C918EC0331AED9C83A2379BBCA09C18530'
    $observado = (Get-FileHash -LiteralPath $arquivo.FullName -Algorithm SHA256).Hash
    if ($observado -cne $esperado) {
        throw 'C2-E-D-D-D-A: SHA256 do backup PostgreSQL divergiu; nao iniciar servico'
    }
    Write-Host '=== C2-E-D-D-D-A BACKUP PASS - dump previamente restaurado conserva SHA256 original ==='
}

function Verificar-SemDaemonWindows {
    if ($null -eq (Get-Command Get-CimInstance -ErrorAction SilentlyContinue)) {
        throw 'C2-E-D-D-D-A: Windows CIM nao esta disponivel para precheck de processos'
    }
    try {
        $processos = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe' OR Name = 'cmd.exe' OR Name = 'powershell.exe' OR Name = 'pwsh.exe'" -ErrorAction Stop)
    } catch {
        throw 'C2-E-D-D-D-A: nao foi possivel consultar processos Java do Windows; gate fechado'
    }
    $marcador = '(?i)(?:com[.]raspingamazon[.]infrastructure[.]bootstrap[.]ContinuousProcessingMain|\bContinuousProcessingMain\b)'
    foreach ($processo in $processos) {
        if ($null -ne $processo.CommandLine -and $processo.CommandLine -match $marcador) {
            # Nao exibir a linha de comando: pode conter parametros sensiveis.
            throw 'C2-E-D-D-D-A: possivel daemon Rasping Amazon ja ativo; parar e investigar antes de prosseguir'
        }
    }
    Write-Host '=== C2-E-D-D-D-A PROCESSOS PASS - daemon identificado por linha de comando nao encontrado ==='
}
