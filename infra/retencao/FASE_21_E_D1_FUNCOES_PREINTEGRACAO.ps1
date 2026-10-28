# FASE 21-E-D1: contrato estatico de compatibilidade; nenhuma execucao produtiva.
# IMPORTAR ESTE ARQUIVO NAO EXECUTA PROCESSOS NEM ABRE ARQUIVOS.
Set-StrictMode -Version Latest

function Get-21ED1-FontesEsperadas {
    return @(
        [pscustomobject]@{ path = 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_C_INICIAR_SUPERVISIONADO.ps1'; sha256 = 'A5C2BAE88EB22F5D821C97B85749F35821C4306B8DF084ABB1019F6D93C2B197' },
        [pscustomobject]@{ path = 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_C_FUNCOES_SUPERVISOR.ps1'; sha256 = '208CD858F8265FBBC6E9C74F7D8EBAE1EBE3579BCD98698045315FE1CFA85C75' },
        [pscustomobject]@{ path = 'infra/retencao/FASE_21_E_B9_FUNCOES_CAPTURA_CATALOGADA.ps1'; sha256 = '6ED0F24B1ECFB5D42D85B12D9E6CE88DECF4918F8D551415680C984B16A230E9' },
        [pscustomobject]@{ path = 'src/main/java/com/raspingamazon/infrastructure/bootstrap/ContinuousProcessingMain.java'; sha256 = '04AAF72F17F3E5830280D8F9B2CBC2AA80AD75BAB61A881ADDB6DF049ECE51D3' },
        [pscustomobject]@{ path = 'src/main/java/com/raspingamazon/infrastructure/observability/JsonStructuredOperationalLogAdapter.java'; sha256 = 'D623F1584184D0DBB46539ED0B33ED5AB9143F02860FBDBBE4BD308809768766' }
    )
}

function Assert-21ED1-CamposExatos {
    param(
        [Parameter(Mandatory=$true)][object]$Objeto,
        [Parameter(Mandatory=$true)][string[]]$Nomes
    )
    if ($Objeto -isnot [pscustomobject]) { throw '21-E-D1: objeto inesperado' }
    $props = @($Objeto.PSObject.Properties | Where-Object { $_.MemberType -eq 'NoteProperty' })
    if ($props.Count -ne $Nomes.Count) { throw '21-E-D1: quantidade de campos inesperada' }
    foreach ($prop in $props) {
        if ($Nomes -cnotcontains $prop.Name) { throw '21-E-D1: campo inesperado' }
    }
    foreach ($nome in $Nomes) {
        if ($null -eq $Objeto.PSObject.Properties[$nome]) { throw '21-E-D1: campo ausente' }
    }
}

function Assert-21ED1-Contrato {
    param([Parameter(Mandatory=$true)][object]$Contrato)
    Assert-21ED1-CamposExatos -Objeto $Contrato -Nomes @(
        'schema_version','execution_mode','daemon_start_authorized',
        'supervisor_integration_authorized','continuous_log_capture_authorized',
        'destructive_actions_authorized','production_adapter_status','expected_sources'
    )
    if (($Contrato.schema_version -isnot [int] -and $Contrato.schema_version -isnot [long]) -or
        $Contrato.schema_version -ne 1 -or
        $Contrato.execution_mode -isnot [string] -or
        $Contrato.execution_mode -cne 'static_review_only' -or
        $Contrato.production_adapter_status -isnot [string] -or
        $Contrato.production_adapter_status -cne 'implemented_policy_gated') {
        throw '21-E-D1: modo do contrato nao autorizado'
    }
    foreach ($flag in @('daemon_start_authorized','supervisor_integration_authorized',
        'continuous_log_capture_authorized','destructive_actions_authorized')) {
        if ($Contrato.PSObject.Properties[$flag].Value -isnot [bool] -or $Contrato.$flag -ne $false) {
            throw '21-E-D1: autorizacao precisa permanecer falsa'
        }
    }
    if ($Contrato.expected_sources -isnot [array]) { throw '21-E-D1: lista de fontes nao e array' }
    $esperados = @(Get-21ED1-FontesEsperadas)
    $recebidos = @($Contrato.expected_sources)
    if ($recebidos.Count -ne $esperados.Count) { throw '21-E-D1: numero de fontes divergente' }
    for ($i=0; $i -lt $esperados.Count; $i++) {
        Assert-21ED1-CamposExatos -Objeto $recebidos[$i] -Nomes @('path','sha256')
        if ($recebidos[$i].path -isnot [string] -or
            $recebidos[$i].path -cne $esperados[$i].path -or
            $recebidos[$i].sha256 -isnot [string] -or
            $recebidos[$i].sha256 -cne $esperados[$i].sha256) {
            throw '21-E-D1: fonte divergente da revisao aprovada'
        }
    }
}

function Assert-21ED1-FonteSupervisor {
    param([Parameter(Mandatory=$true)][string]$Conteudo)
    # Validacao suplementar ao SHA-256 exato: registra invariantes humanos.
    $bloqueio = $Conteudo.IndexOf('Exigir-PartidaAutorizada-C-C',[System.StringComparison]::Ordinal)
    $inicio = $Conteudo.IndexOf('[System.Diagnostics.Process]::Start($psi)',[System.StringComparison]::Ordinal)
    $gateCaptura = $Conteudo.IndexOf('Assert-21E-AutorizacaoCaptura',[System.StringComparison]::Ordinal)
    $redirOut = $Conteudo.IndexOf('$psi.RedirectStandardOutput = $true',[System.StringComparison]::Ordinal)
    $redirErr = $Conteudo.IndexOf('$psi.RedirectStandardError = $true',[System.StringComparison]::Ordinal)
    if ($bloqueio -lt 0 -or $gateCaptura -le $bloqueio -or
        $redirOut -le $gateCaptura -or $redirErr -le $gateCaptura -or
        $inicio -le $redirOut -or $inicio -le $redirErr -or
        -not $Conteudo.Contains('$psi.UseShellExecute = $false') -or
        -not $Conteudo.Contains('$psi.CreateNoWindow = $true') -or
        -not $Conteudo.Contains('Confirmar-ProcessoJavaCriado-D-I') -or
        -not $Conteudo.Contains('Resolver-PartidaNaoRegistrada-D-I') -or
        -not $Conteudo.Contains('RASPING_SUPERVISED_STOP_REQUEST_FILE') -or
        -not $Conteudo.Contains('Invoke-21E-CapturarProcessoSupervisionado')) {
        throw '21-E-D1: supervisor divergente dos gates de captura e supervisao'
    }
}

function Assert-21ED1-FonteB9 {
    param([Parameter(Mandatory=$true)][string]$Conteudo)
    if (-not $Conteudo.Contains('New-21EB9-ArquivoFilhoSintetico') -or
        -not $Conteudo.Contains('target/retencao-21eb1-testes') -or
        -not $Conteudo.Contains('powershell.exe') -or
        -not $Conteudo.Contains('$processo.Kill()') -or
        -not $Conteudo.Contains('Write-21EB6-EventoLacrado') -or
        -not $Conteudo.Contains('Assert-21EB8-Catalogo')) {
        throw '21-E-D1: capturador B9 inesperado; nao integrar'
    }
}

function Assert-21ED1-BloqueioPoliticas {
    param([Parameter(Mandatory=$true)][object]$Startup,
          [Parameter(Mandatory=$true)][object]$Retencao,
          [Parameter(Mandatory=$true)][object]$Catalogo)
    Assert-21ED1-CamposExatos -Objeto $Startup -Nomes @(
        'schema_version','execution_mode','initial_schedule_interval',
        'daemon_start_authorized','flyway_migrate_authorized',
        'external_actions_authorized','automatic_restart_authorized',
        'shutdown_grace_seconds'
    )
    if ($Startup.schema_version -ne 1 -or $Startup.execution_mode -cne 'preparation_only' -or
        $Startup.initial_schedule_interval -cne 'PT1H' -or $Startup.shutdown_grace_seconds -ne 30) {
        throw '21-E-D1: plano de startup inesperado'
    }
    foreach ($flag in @('daemon_start_authorized','flyway_migrate_authorized',
        'external_actions_authorized','automatic_restart_authorized')) {
        if ($Startup.PSObject.Properties[$flag].Value -isnot [bool] -or $Startup.$flag -ne $false) {
            throw '21-E-D1: startup nao bloqueado'
        }
    }
    if ($Retencao.execution_mode -cne 'inventory_only' -or
        $Retencao.destructive_actions_authorized -isnot [bool] -or
        $Retencao.destructive_actions_authorized -ne $false -or
        $Retencao.continuous_log_capture_authorized -isnot [bool] -or
        $Retencao.continuous_log_capture_authorized -ne $false) {
        throw '21-E-D1: retencao nao bloqueada'
    }
    if ($Catalogo.execution_mode -cne 'offline_validation_only' -or
        $Catalogo.continuous_log_capture_authorized -isnot [bool] -or
        $Catalogo.continuous_log_capture_authorized -ne $false -or
        $Catalogo.destructive_actions_authorized -isnot [bool] -or
        $Catalogo.destructive_actions_authorized -ne $false) {
        throw '21-E-D1: catalogo nao bloqueado'
    }
}

function Assert-21ED1-FontesIntactas {
    param([Parameter(Mandatory=$true)][string]$Raiz,
          [Parameter(Mandatory=$true)][object]$Contrato)
    Assert-21ED1-Contrato -Contrato $Contrato
    foreach ($fonte in @($Contrato.expected_sources)) {
        $caminho = Join-Path $Raiz $fonte.path
        if (-not (Test-Path -LiteralPath $caminho -PathType Leaf)) { throw '21-E-D1: fonte ausente' }
        $item = Get-Item -LiteralPath $caminho -Force -ErrorAction Stop
        if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw '21-E-D1: arquivo simbolico proibido'
        }
        $hash = (Get-FileHash -LiteralPath $caminho -Algorithm SHA256 -ErrorAction Stop).Hash
        if ($hash -cne $fonte.sha256) { throw '21-E-D1: fonte alterada desde a revisao' }
    }
}
