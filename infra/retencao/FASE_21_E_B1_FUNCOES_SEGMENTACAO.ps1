# FASE 21-E-B1. Escritor de teste ISOLADO, sem captura de daemon ou exclusao.
# Importar este arquivo nao cria arquivos, processos ou conexoes.
Set-StrictMode -Version Latest

function Assert-21EB1-Contexto {
    param([Parameter(Mandatory=$true)][string]$Raiz)

    if ([System.Environment]::OSVersion.Platform -ne [System.PlatformID]::Win32NT) {
        throw '21-E-B1: este gate exige Windows e ACL NTFS'
    }
    if (-not (Test-Path -LiteralPath (Join-Path $Raiz 'pom.xml') -PathType Leaf) -or
        -not (Test-Path -LiteralPath (Join-Path $Raiz '.git'))) {
        throw '21-E-B1: executar na raiz do repositorio Git'
    }
    $arquivoFuncoesA = Join-Path $Raiz 'infra/retencao/FASE_21_E_A_FUNCOES_RETENCAO.ps1'
    if (-not (Test-Path -LiteralPath $arquivoFuncoesA -PathType Leaf)) {
        throw '21-E-B1: contrato da etapa A ausente'
    }
    . $arquivoFuncoesA
    $politica = Get-Content -LiteralPath (Join-Path $Raiz 'config/operacao/retention-policy.v1.json') -Encoding UTF8 -Raw | ConvertFrom-Json -ErrorAction Stop
    Assert-21EA-Politica -Politica $politica
    $startup = Get-Content -LiteralPath (Join-Path $Raiz 'config/operacao/continuous-startup-policy.v1.json') -Encoding UTF8 -Raw | ConvertFrom-Json -ErrorAction Stop
    if ($startup.execution_mode -cne 'preparation_only' -or
        $startup.daemon_start_authorized -ne $false -or
        $startup.flyway_migrate_authorized -ne $false -or
        $startup.external_actions_authorized -ne $false -or
        $startup.automatic_restart_authorized -ne $false) {
        throw '21-E-B1: startup nao esta bloqueado'
    }
    if ($politica.destructive_actions_authorized -ne $false -or
        $politica.continuous_log_capture_authorized -ne $false) {
        throw '21-E-B1: politica de retencao nao permanece somente-inventario'
    }
    # Nao criamos o destino operacional: somente o sandbox ignorado em target/.
    return $true
}

function Assert-21EB1-SemRedirecionamento {
    param([Parameter(Mandatory=$true)][string]$Caminho)
    $atual = [System.IO.Path]::GetFullPath($Caminho)
    while ($true) {
        if ([System.IO.File]::Exists($atual) -or [System.IO.Directory]::Exists($atual)) {
            $obj = Get-Item -LiteralPath $atual -Force -ErrorAction Stop
            if (($obj.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw '21-E-B1: caminho com reparse point'
            }
        }
        $pai = [System.IO.Directory]::GetParent($atual)
        if ($null -eq $pai) { break }
        $atual = $pai.FullName
    }
}

function New-21EB1-ACLPrivada {
    $acl = New-Object System.Security.AccessControl.DirectorySecurity
    $acl.SetAccessRuleProtection($true, $false)
    $atual = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    foreach ($sidTexto in @($atual, 'S-1-5-18', 'S-1-5-32-544')) {
        $sid = [System.Security.Principal.SecurityIdentifier]::new($sidTexto)
        $regra = [System.Security.AccessControl.FileSystemAccessRule]::new(
            $sid,
            [System.Security.AccessControl.FileSystemRights]::FullControl,
            ([System.Security.AccessControl.InheritanceFlags]::ContainerInherit -bor [System.Security.AccessControl.InheritanceFlags]::ObjectInherit),
            [System.Security.AccessControl.PropagationFlags]::None,
            [System.Security.AccessControl.AccessControlType]::Allow
        )
        $acl.AddAccessRule($regra)
    }
    return $acl
}

function Assert-21EB1-ACLPrivada {
    param([Parameter(Mandatory=$true)][string]$Diretorio)
    $acl = Get-Acl -LiteralPath $Diretorio -ErrorAction Stop
    if (-not $acl.AreAccessRulesProtected) {
        throw '21-E-B1: ACL herda permissoes externas'
    }
    $permitidos = @(
        [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value,
        'S-1-5-18', 'S-1-5-32-544'
    )
    foreach ($regra in $acl.Access) {
        if ($regra.AccessControlType -ne [System.Security.AccessControl.AccessControlType]::Allow) { continue }
        $sid = $regra.IdentityReference.Translate([System.Security.Principal.SecurityIdentifier]).Value
        if ($permitidos -notcontains $sid) {
            throw '21-E-B1: ACL concede acesso a identidade inesperada'
        }
    }
}

function New-21EB1-Sandbox {
    param([Parameter(Mandatory=$true)][string]$Raiz)
    $target = Join-Path $Raiz 'target'
    if (-not (Test-Path -LiteralPath $target -PathType Container)) {
        throw '21-E-B1: target/ precisa existir; nao criar estruturas fora da pasta de testes'
    }
    Assert-21EB1-SemRedirecionamento -Caminho $target
    $base = Join-Path $target 'retencao-21eb1-testes'
    Assert-21EB1-SemRedirecionamento -Caminho $base
    if (Test-Path -LiteralPath $base) {
        if (-not (Test-Path -LiteralPath $base -PathType Container)) {
            throw '21-E-B1: sandbox esperado como diretorio'
        }
        Assert-21EB1-ACLPrivada -Diretorio $base
    } else {
        $acl = New-21EB1-ACLPrivada
        [void][System.IO.Directory]::CreateDirectory($base, $acl)
        Assert-21EB1-ACLPrivada -Diretorio $base
    }
    $filho = Join-Path $base ('cenario-' + [guid]::NewGuid().ToString('N'))
    Assert-21EB1-SemRedirecionamento -Caminho $filho
    if ([System.IO.Directory]::Exists($filho) -or [System.IO.File]::Exists($filho)) {
        throw '21-E-B1: colisao no nome do cenario'
    }
    [void][System.IO.Directory]::CreateDirectory($filho, (New-21EB1-ACLPrivada))
    Assert-21EB1-ACLPrivada -Diretorio $filho
    return $filho
}

function New-21EB1-Sessao {
    param(
        [Parameter(Mandatory=$true)][string]$Diretorio,
        [Parameter(Mandatory=$true)][long]$MaximoSegmentoBytes,
        [Parameter(Mandatory=$true)][long]$MaximoTotalBytes
    )
    if ($MaximoSegmentoBytes -lt 128 -or $MaximoTotalBytes -lt $MaximoSegmentoBytes) {
        throw '21-E-B1: limites de escrita invalidos'
    }
    $absoluto = [System.IO.Path]::GetFullPath($Diretorio)
    $raiz = [System.IO.Path]::GetFullPath((Get-Location).Path)
    $baseAutorizada = [System.IO.Path]::GetFullPath(
        (Join-Path $raiz 'target/retencao-21eb1-testes')
    ).TrimEnd([char[]]@('\','/'))
    $pai = [System.IO.Directory]::GetParent($absoluto)
    if ($null -eq $pai -or
        -not $pai.FullName.Equals($baseAutorizada, [System.StringComparison]::OrdinalIgnoreCase) -or
        -not ([System.IO.Path]::GetFileName($absoluto) -match '^cenario-[0-9a-f]{32}$')) {
        throw '21-E-B1: escritor limitado a cenario descartavel sob target/'
    }
    Assert-21EB1-SemRedirecionamento -Caminho $absoluto
    Assert-21EB1-ACLPrivada -Diretorio $absoluto
    if (@(Get-ChildItem -LiteralPath $Diretorio -Force -ErrorAction Stop).Count -ne 0) {
        throw '21-E-B1: sessao requer diretorio de cenario vazio'
    }
    return [pscustomobject]@{
        Diretorio = [System.IO.Path]::GetFullPath($Diretorio)
        MaximoSegmentoBytes = $MaximoSegmentoBytes
        MaximoTotalBytes = $MaximoTotalBytes
        Indice = 0
        SegmentoBytes = [long]0
        TotalBytes = [long]0
        Fluxo = $null
        Encerrada = $false
    }
}

function Close-21EB1-Sessao {
    param([Parameter(Mandatory=$true)][object]$Sessao)
    if ($null -ne $Sessao.Fluxo) {
        try { $Sessao.Fluxo.Flush($true) } finally {
            $Sessao.Fluxo.Dispose()
            $Sessao.Fluxo = $null
        }
    }
    $Sessao.Encerrada = $true
}

function Write-21EB1-EventoSintetico {
    param(
        [Parameter(Mandatory=$true)][object]$Sessao,
        [Parameter(Mandatory=$true)][string]$LinhaJson
    )
    if ($Sessao.Encerrada) { throw '21-E-B1: sessao encerrada' }
    if ($LinhaJson.Contains("`n") -or $LinhaJson.Contains("`r")) {
        throw '21-E-B1: eventos precisam ter uma unica linha'
    }
    if ($LinhaJson.Length -gt 32768) { throw '21-E-B1: evento muito extenso' }
    try { $obj = $LinhaJson | ConvertFrom-Json -ErrorAction Stop }
    catch { throw '21-E-B1: evento nao e JSON valido' }
    if ($null -eq $obj -or $obj -isnot [pscustomobject]) {
        throw '21-E-B1: o evento precisa ser um objeto JSON'
    }
    $campos = @('timestamp','level','event','component','operation','outcome',
        'runId','jobId','jobType','candidateId','snapshotId','evaluationId',
        'publicationId','asin','integration','durationMs','failureOrigin',
        'failureType','errorCode')
    foreach ($p in $obj.PSObject.Properties) {
        if ($campos -cnotcontains $p.Name) {
            throw '21-E-B1: campo inesperado no evento estruturado'
        }
        if ($p.Value -is [array] -or $p.Value -is [pscustomobject]) {
            throw '21-E-B1: valores complexos nao sao aceitos nos testes'
        }
    }
    foreach ($obrigatorio in @('timestamp','level','event','component','operation','outcome')) {
        if ($obj.PSObject.Properties.Name -cnotcontains $obrigatorio -or
            $obj.$obrigatorio -isnot [string] -or
            [string]::IsNullOrWhiteSpace($obj.$obrigatorio)) {
            throw '21-E-B1: campo obrigatorio ausente ou invalido'
        }
    }
    $bytes = [System.Text.UTF8Encoding]::new($false, $true).GetBytes($LinhaJson + "`n")
    if ($bytes.Length -gt $Sessao.MaximoSegmentoBytes) {
        throw '21-E-B1: evento excede segmento isolado'
    }
    if ($Sessao.TotalBytes + $bytes.Length -gt $Sessao.MaximoTotalBytes) {
        throw '21-E-B1: limite total atingido sem descartar historico'
    }
    if ($null -eq $Sessao.Fluxo -or
        $Sessao.SegmentoBytes + $bytes.Length -gt $Sessao.MaximoSegmentoBytes) {
        if ($null -ne $Sessao.Fluxo) {
            $Sessao.Fluxo.Flush($true)
            $Sessao.Fluxo.Dispose()
            $Sessao.Fluxo = $null
        }
        $Sessao.Indice = [int]$Sessao.Indice + 1
        $nome = 'segmento-{0:D6}.jsonl' -f $Sessao.Indice
        $caminho = Join-Path $Sessao.Diretorio $nome
        $Sessao.Fluxo = [System.IO.FileStream]::new(
            $caminho,
            [System.IO.FileMode]::CreateNew,
            [System.IO.FileAccess]::Write,
            [System.IO.FileShare]::Read
        )
        $Sessao.SegmentoBytes = [long]0
    }
    $Sessao.Fluxo.Write($bytes, 0, $bytes.Length)
    $Sessao.Fluxo.Flush($true)
    $Sessao.SegmentoBytes = [long]$Sessao.SegmentoBytes + $bytes.Length
    $Sessao.TotalBytes = [long]$Sessao.TotalBytes + $bytes.Length
}
