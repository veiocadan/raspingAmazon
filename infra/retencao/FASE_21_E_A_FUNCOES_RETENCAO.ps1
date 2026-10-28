# FASE 21-E-A — Funcoes puras de contrato e inventario estritamente read-only.
# A importacao nao executa efeitos colaterais.
Set-StrictMode -Version Latest

function Assert-21EA-NomesExatos {
    param(
        [Parameter(Mandatory=$true)][object]$Objeto,
        [Parameter(Mandatory=$true)][string[]]$Nomes,
        [Parameter(Mandatory=$true)][string]$Contexto
    )
    if ($null -eq $Objeto) { throw "21-E-A: objeto ausente: $Contexto" }
    $reais = @($Objeto.PSObject.Properties | ForEach-Object { $_.Name })
    if ($reais.Count -ne $Nomes.Count) { throw "21-E-A: esquema inesperado: $Contexto" }
    foreach ($nome in $reais) {
        if ($Nomes -cnotcontains $nome) {
            throw "21-E-A: propriedade desconhecida: $Contexto"
        }
    }
    foreach ($nome in $Nomes) {
        if ($reais -cnotcontains $nome) {
            throw "21-E-A: propriedade obrigatoria ausente: $Contexto"
        }
    }
}

function Assert-21EA-NumeroInteiro {
    param([object]$Valor, [int64]$Minimo, [int64]$Maximo, [string]$Descricao)
    if (($Valor -isnot [int]) -and ($Valor -isnot [long])) {
        throw "21-E-A: numero invalido: $Descricao"
    }
    if ([int64]$Valor -lt $Minimo -or [int64]$Valor -gt $Maximo) {
        throw "21-E-A: numero fora dos limites: $Descricao"
    }
}

function Assert-21EA-Politica {
    param([Parameter(Mandatory=$true)][object]$Politica)

    Assert-21EA-NomesExatos -Objeto $Politica -Contexto 'raiz' -Nomes @(
        'schema_version', 'execution_mode', 'destructive_actions_authorized',
        'continuous_log_capture_authorized', 'github_probe_artifact_days',
        'local_rules', 'continuous_logs_proposal'
    )

    if ($Politica.schema_version -isnot [int] -and $Politica.schema_version -isnot [long]) {
        throw '21-E-A: versao de esquema invalida'
    }
    if ($Politica.schema_version -ne 1) { throw '21-E-A: versao de esquema nao suportada' }
    if ($Politica.execution_mode -isnot [string] -or $Politica.execution_mode -cne 'inventory_only') {
        throw '21-E-A: somente inventory_only e permitido'
    }
    foreach ($nome in @('destructive_actions_authorized','continuous_log_capture_authorized')) {
        if ($Politica.PSObject.Properties[$nome].Value -isnot [bool]) {
            throw '21-E-A: autorizacao exige valor booleano'
        }
        if ($Politica.PSObject.Properties[$nome].Value) {
            throw '21-E-A: qualquer acao nao inventarial esta bloqueada'
        }
    }
    Assert-21EA-NumeroInteiro -Valor $Politica.github_probe_artifact_days -Minimo 3 -Maximo 3 -Descricao 'GitHub Actions'

    if ($Politica.local_rules -isnot [array] -or $Politica.local_rules.Count -ne 2) {
        throw '21-E-A: lista de escopos locais invalida'
    }
    $esperados = @{
        'amazon-diagnostics' = 'target/diagnostics'
        'maven-test-reports' = 'target/surefire-reports'
    }
    $encontrados = @{}
    foreach ($regra in $Politica.local_rules) {
        Assert-21EA-NomesExatos -Objeto $regra -Contexto 'regra' -Nomes @(
            'id','relative_path','proposed_max_age_days'
        )
        if ($regra.id -isnot [string] -or -not $esperados.ContainsKey($regra.id)) {
            throw '21-E-A: identificador de escopo nao permitido'
        }
        if ($encontrados.ContainsKey($regra.id)) {
            throw '21-E-A: escopo duplicado'
        }
        $encontrados[$regra.id] = $true
        if ($regra.relative_path -isnot [string] -or
            $regra.relative_path -cne $esperados[$regra.id]) {
            throw '21-E-A: caminho fora da allowlist fixa'
        }
        Assert-21EA-NumeroInteiro -Valor $regra.proposed_max_age_days -Minimo 7 -Maximo 7 -Descricao 'prazo local proposto'
    }
    Assert-21EA-NomesExatos -Objeto $Politica.continuous_logs_proposal -Contexto 'logs' -Nomes @(
        'external_directory','proposed_max_age_days',
        'proposed_max_segment_bytes','proposed_max_total_bytes'
    )
    $log = $Politica.continuous_logs_proposal
    if ($log.external_directory -isnot [string] -or
        $log.external_directory -cne 'C:\dev\rasping-amazon-operacao\logs') {
        throw '21-E-A: destino proposto de logs diferente do contrato'
    }
    Assert-21EA-NumeroInteiro -Valor $log.proposed_max_age_days -Minimo 30 -Maximo 30 -Descricao 'dias logs'
    Assert-21EA-NumeroInteiro -Valor $log.proposed_max_segment_bytes -Minimo 16777216 -Maximo 16777216 -Descricao 'segmento logs'
    Assert-21EA-NumeroInteiro -Valor $log.proposed_max_total_bytes -Minimo 536870912 -Maximo 536870912 -Descricao 'limite logs'
}

function Assert-21EA-SemRedirecionamento {
    param([Parameter(Mandatory=$true)][string]$Diretorio)

    $atual = [System.IO.Path]::GetFullPath($Diretorio)
    # Nao seguir junctions, symlinks ou outros reparse points nos ancestrais.
    while ($true) {
        if (Test-Path -LiteralPath $atual) {
            $objeto = Get-Item -LiteralPath $atual -Force -ErrorAction Stop
            if (($objeto.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw '21-E-A: caminho com redirecionamento simbolico ou junction'
            }
        }
        $pai = [System.IO.Directory]::GetParent($atual)
        if ($null -eq $pai) { break }
        $atual = $pai.FullName
    }
}

function Get-21EA-ResumoInventario {
    param(
        [Parameter(Mandatory=$true)][string]$RaizRepositorio,
        [Parameter(Mandatory=$true)][object]$Regra,
        [Parameter(Mandatory=$true)][datetime]$AgoraUtc
    )
    $relativo = $Regra.relative_path.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
    $diretorio = [System.IO.Path]::GetFullPath((Join-Path $RaizRepositorio $relativo))
    $prefixo = [System.IO.Path]::GetFullPath($RaizRepositorio).TrimEnd([char[]]@('\','/')) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $diretorio.StartsWith($prefixo,[System.StringComparison]::OrdinalIgnoreCase)) {
        throw '21-E-A: caminho fora do repositorio'
    }
    Assert-21EA-SemRedirecionamento -Diretorio $diretorio
    if (-not (Test-Path -LiteralPath $diretorio)) {
        return [pscustomobject]@{
            escopo=$Regra.id; estado='AUSENTE'; arquivos=0; bytes=0;
            anteriores_ao_prazo_proposto=0; reparse_points_ignorados=0
        }
    }
    if (-not (Test-Path -LiteralPath $diretorio -PathType Container)) {
        throw '21-E-A: escopo esperado como pasta e arquivo'
    }
    $fila = New-Object 'System.Collections.Generic.Queue[string]'
    $fila.Enqueue($diretorio)
    [long]$qtd=0; [long]$bytes=0; [long]$antigos=0; [long]$redirecionamentos=0
    $limite = $AgoraUtc.AddDays(-[int]$Regra.proposed_max_age_days)
    while ($fila.Count -gt 0) {
        $pasta = $fila.Dequeue()
        $itens = @(Get-ChildItem -LiteralPath $pasta -Force -ErrorAction Stop)
        foreach ($item in $itens) {
            if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
                $redirecionamentos++
                continue
            }
            if ($item.PSIsContainer) {
                $fila.Enqueue($item.FullName)
            } else {
                $qtd++
                $bytes += [long]$item.Length
                if ($item.LastWriteTimeUtc -lt $limite) { $antigos++ }
            }
        }
    }
    return [pscustomobject]@{
        escopo=$Regra.id; estado='PRESENTE'; arquivos=$qtd; bytes=$bytes;
        anteriores_ao_prazo_proposto=$antigos;
        reparse_points_ignorados=$redirecionamentos
    }
}
