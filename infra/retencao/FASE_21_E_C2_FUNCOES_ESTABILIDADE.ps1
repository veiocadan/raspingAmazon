# FASE 21-E-C2 - fotografias de metadados para auditoria somente-leitura.
# Nao contem operacoes de exclusao, movimentacao, gravacao, rotacao ou partida.
Set-StrictMode -Version Latest

function Assert-21EC2-Regra {
    param([Parameter(Mandatory=$true)][object]$Regra)
    Assert-21EA-NomesExatos -Objeto $Regra -Nomes @('id','relative_path','proposed_max_age_days') -Contexto 'regra C2'
    $escopos = @{
        'amazon-diagnostics' = 'target/diagnostics'
        'maven-test-reports' = 'target/surefire-reports'
    }
    if ($Regra.id -isnot [string] -or -not $escopos.ContainsKey($Regra.id) -or
        $Regra.relative_path -isnot [string] -or $Regra.relative_path -cne $escopos[$Regra.id] -or
        ($Regra.proposed_max_age_days -isnot [int] -and $Regra.proposed_max_age_days -isnot [long]) -or
        $Regra.proposed_max_age_days -ne 7) {
        throw '21-E-C2: regra fora da allowlist'
    }
}

function Assert-21EC2-CaminhoNoEscopo {
    param(
        [Parameter(Mandatory=$true)][string]$Caminho,
        [Parameter(Mandatory=$true)][string]$DiretorioEscopo
    )
    if (-not [System.IO.Path]::IsPathRooted($Caminho) -or
        -not [System.IO.Path]::IsPathRooted($DiretorioEscopo)) {
        throw '21-E-C2: caminho nao absoluto'
    }
    $raiz = [System.IO.Path]::GetFullPath($DiretorioEscopo).TrimEnd([char[]]@('\','/'))
    $arquivo = [System.IO.Path]::GetFullPath($Caminho)
    $prefixo = $raiz + [System.IO.Path]::DirectorySeparatorChar
    if (-not $arquivo.StartsWith($prefixo, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw '21-E-C2: caminho fora do escopo'
    }
}

function New-21EC2-Registro {
    param(
        [Parameter(Mandatory=$true)][string]$Caminho,
        [Parameter(Mandatory=$true)][long]$Bytes,
        [Parameter(Mandatory=$true)][datetime]$CriadoUtc,
        [Parameter(Mandatory=$true)][datetime]$AlteradoUtc,
        [Parameter(Mandatory=$true)][long]$Atributos
    )
    if ($Bytes -lt 0 -or $CriadoUtc.Kind -ne [System.DateTimeKind]::Utc -or
        $AlteradoUtc.Kind -ne [System.DateTimeKind]::Utc -or $Atributos -lt 0 -or
        ($Atributos -band [long][System.IO.FileAttributes]::ReparsePoint) -ne 0 -or
        ($Atributos -band [long][System.IO.FileAttributes]::Directory) -ne 0) {
        throw '21-E-C2: metadados de arquivo invalidos'
    }
    return [pscustomobject]@{
        caminho=$Caminho; bytes=$Bytes; criado_utc=$CriadoUtc;
        alterado_utc=$AlteradoUtc; atributos=$Atributos
    }
}

function New-21EC2-Foto {
    param(
        [Parameter(Mandatory=$true)][string]$Escopo,
        [Parameter(Mandatory=$true)][string]$Estado,
        [Parameter(Mandatory=$true)][AllowEmptyCollection()][object[]]$Registros,
        [Parameter(Mandatory=$true)][long]$RedirecionamentosIgnorados
    )
    if ($Escopo -cnotin @('amazon-diagnostics','maven-test-reports') -or
        $Estado -cnotin @('PRESENTE','AUSENTE') -or $RedirecionamentosIgnorados -lt 0 -or
        ($Estado -ceq 'AUSENTE' -and ($Registros.Count -ne 0 -or $RedirecionamentosIgnorados -ne 0))) {
        throw '21-E-C2: fotografia invalida'
    }
    return [pscustomobject]@{
        escopo=$Escopo; estado=$Estado; registros=@($Registros);
        redirecionamentos_ignorados=$RedirecionamentosIgnorados
    }
}

function Assert-21EC2-FotosEstaveis {
    param(
        [Parameter(Mandatory=$true)][object]$Primeira,
        [Parameter(Mandatory=$true)][object]$Segunda,
        [Parameter(Mandatory=$true)][string]$DiretorioEscopo
    )
    foreach ($foto in @($Primeira,$Segunda)) {
        Assert-21EA-NomesExatos -Objeto $foto -Nomes @(
            'escopo','estado','registros','redirecionamentos_ignorados') -Contexto 'foto C2'
        if ($foto.registros -isnot [array] -or
            $foto.estado -cnotin @('PRESENTE','AUSENTE') -or
            $foto.escopo -cnotin @('amazon-diagnostics','maven-test-reports') -or
            ($foto.estado -ceq 'AUSENTE' -and $foto.registros.Count -ne 0)) {
            throw '21-E-C2: formato de fotografia inesperado'
        }
    }
    if ($Primeira.escopo -cne $Segunda.escopo -or
        $Primeira.estado -cne $Segunda.estado -or
        $Primeira.redirecionamentos_ignorados -ne $Segunda.redirecionamentos_ignorados -or
        $Primeira.registros.Count -ne $Segunda.registros.Count) {
        throw '21-E-C2: escopo ou quantidade mudou entre leituras'
    }
    $mapa = @{}
    foreach ($registro in $Primeira.registros) {
        Assert-21EA-NomesExatos -Objeto $registro -Nomes @(
            'caminho','bytes','criado_utc','alterado_utc','atributos') -Contexto 'registro C2'
        Assert-21EC2-CaminhoNoEscopo -Caminho $registro.caminho -DiretorioEscopo $DiretorioEscopo
        [void](New-21EC2-Registro -Caminho $registro.caminho -Bytes $registro.bytes `
            -CriadoUtc $registro.criado_utc -AlteradoUtc $registro.alterado_utc -Atributos $registro.atributos)
        if ($mapa.ContainsKey($registro.caminho)) { throw '21-E-C2: arquivo duplicado na primeira leitura' }
        $mapa[$registro.caminho] = $registro
    }
    $vistos = @{}
    foreach ($registro in $Segunda.registros) {
        Assert-21EA-NomesExatos -Objeto $registro -Nomes @(
            'caminho','bytes','criado_utc','alterado_utc','atributos') -Contexto 'registro C2'
        Assert-21EC2-CaminhoNoEscopo -Caminho $registro.caminho -DiretorioEscopo $DiretorioEscopo
        [void](New-21EC2-Registro -Caminho $registro.caminho -Bytes $registro.bytes `
            -CriadoUtc $registro.criado_utc -AlteradoUtc $registro.alterado_utc -Atributos $registro.atributos)
        if ($vistos.ContainsKey($registro.caminho) -or -not $mapa.ContainsKey($registro.caminho)) {
            throw '21-E-C2: arquivo novo, removido ou duplicado entre leituras'
        }
        $vistos[$registro.caminho] = $true
        $anterior = $mapa[$registro.caminho]
        if ($anterior.bytes -ne $registro.bytes -or
            $anterior.criado_utc.Ticks -ne $registro.criado_utc.Ticks -or
            $anterior.alterado_utc.Ticks -ne $registro.alterado_utc.Ticks -or
            $anterior.atributos -ne $registro.atributos) {
            throw '21-E-C2: metadados alterados entre leituras'
        }
    }
}

function Get-21EC2-FotoEscopo {
    param(
        [Parameter(Mandatory=$true)][string]$RaizRepositorio,
        [Parameter(Mandatory=$true)][object]$Regra
    )
    Assert-21EC2-Regra -Regra $Regra
    $raiz = [System.IO.Path]::GetFullPath($RaizRepositorio).TrimEnd([char[]]@('\','/'))
    Assert-21EA-SemRedirecionamento -Diretorio $raiz
    $relativo = $Regra.relative_path.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
    $pastaEscopo = [System.IO.Path]::GetFullPath((Join-Path $raiz $relativo))
    Assert-21EC2-CaminhoNoEscopo -Caminho (Join-Path $pastaEscopo 'sentinela-c2') -DiretorioEscopo $pastaEscopo
    if (-not $pastaEscopo.StartsWith(($raiz + [System.IO.Path]::DirectorySeparatorChar),
        [System.StringComparison]::OrdinalIgnoreCase)) {
        throw '21-E-C2: escopo externo ao repositorio'
    }
    Assert-21EA-SemRedirecionamento -Diretorio $pastaEscopo
    if (-not (Test-Path -LiteralPath $pastaEscopo)) {
        return New-21EC2-Foto -Escopo $Regra.id -Estado 'AUSENTE' -Registros @() -RedirecionamentosIgnorados 0
    }
    if (-not (Test-Path -LiteralPath $pastaEscopo -PathType Container)) {
        throw '21-E-C2: escopo nao e diretorio'
    }
    $fila = New-Object 'System.Collections.Generic.Queue[string]'
    $fila.Enqueue($pastaEscopo)
    $dados = New-Object 'System.Collections.Generic.List[object]'
    [long]$redirecionamentos = 0
    while ($fila.Count -gt 0) {
        $pasta = $fila.Dequeue()
        Assert-21EA-SemRedirecionamento -Diretorio $pasta
        foreach ($item in @(Get-ChildItem -LiteralPath $pasta -Force -ErrorAction Stop)) {
            if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
                $redirecionamentos++
                continue
            }
            if ($item.PSIsContainer) {
                $fila.Enqueue($item.FullName)
            } else {
                Assert-21EC2-CaminhoNoEscopo -Caminho $item.FullName -DiretorioEscopo $pastaEscopo
                $dados.Add((New-21EC2-Registro -Caminho $item.FullName `
                    -Bytes ([long]$item.Length) -CriadoUtc $item.CreationTimeUtc `
                    -AlteradoUtc $item.LastWriteTimeUtc -Atributos ([long]$item.Attributes)))
            }
        }
    }
    return New-21EC2-Foto -Escopo $Regra.id -Estado 'PRESENTE' `
        -Registros $dados.ToArray() -RedirecionamentosIgnorados $redirecionamentos
}

function Get-21EC2-ResumoFoto {
    param(
        [Parameter(Mandatory=$true)][object]$Foto,
        [Parameter(Mandatory=$true)][datetime]$AgoraUtc,
        [Parameter(Mandatory=$true)][int]$PrazoDias
    )
    if ($AgoraUtc.Kind -ne [System.DateTimeKind]::Utc -or $PrazoDias -ne 7) {
        throw '21-E-C2: referencia temporal invalida'
    }
    [long]$bytes = 0
    [long]$candidatos = 0
    [long]$bytesCandidatos = 0
    foreach ($registro in $Foto.registros) {
        $bytes += $registro.bytes
        if (Test-21EC1-AnteriorAoPrazo -UltimaAlteracaoUtc $registro.alterado_utc `
            -AgoraUtc $AgoraUtc -PrazoDias $PrazoDias) {
            $candidatos++
            $bytesCandidatos += $registro.bytes
        }
    }
    return [pscustomobject]@{
        escopo=$Foto.escopo; estado=$Foto.estado;
        arquivos=[long]$Foto.registros.Count; bytes=$bytes;
        candidatos=$candidatos; bytes_candidatos=$bytesCandidatos;
        redirecionamentos_ignorados=$Foto.redirecionamentos_ignorados
    }
}
