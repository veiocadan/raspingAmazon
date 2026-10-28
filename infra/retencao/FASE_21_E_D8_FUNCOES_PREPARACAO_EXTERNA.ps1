# FASE 21-E-D8: contrato de seguranca para diretorio operacional externo.
# Importar nao cria diretorios, arquivos, processos ou conexoes.
# Somente leitura de contratos versionados locais; sem acesso ao diretorio externo.
Set-StrictMode -Version Latest

function Assert-21ED8-PropriedadesExatas {
    param([Parameter(Mandatory=$true)][object]$Objeto,
          [Parameter(Mandatory=$true)][string[]]$Esperadas)
    if ($Objeto -isnot [pscustomobject]) { throw '21-E-D8: objeto de contrato invalido' }
    $props = @($Objeto.PSObject.Properties | Where-Object { $_.MemberType -eq 'NoteProperty' })
    if ($props.Count -ne $Esperadas.Count) { throw '21-E-D8: esquema de contrato divergente' }
    foreach ($prop in $props) {
        if ($Esperadas -cnotcontains $prop.Name) { throw '21-E-D8: campo inesperado' }
    }
    foreach ($nome in $Esperadas) {
        if ($null -eq $Objeto.PSObject.Properties[$nome]) { throw '21-E-D8: campo obrigatorio ausente' }
    }
}

function Assert-21ED8-CaminhoProposto {
    param([Parameter(Mandatory=$true)][string]$Caminho,
          [Parameter(Mandatory=$true)][string]$RaizProjeto)
    # Validacao puramente lexical: NAO executa Test-Path, Get-Item ou Get-Acl fora do repositorio.
    # O valor e fixado na politica versionada, nao recebido de entrada livre do usuario.
    if ($Caminho -cne 'C:\dev\rasping-amazon-operacao\logs' -or
        $Caminho -cnotmatch '^[A-Za-z]:\\[^/<>:"|?*]+(\\[^/<>:"|?*]+)*$' -or
        $Caminho.Contains('..') -or $Caminho.Contains('\\\\') -or
        $Caminho -match '\\[.](\\|$)') {
        throw '21-E-D8: destino externo nao corresponde ao caminho proposto'
    }
    if (-not [System.IO.Path]::IsPathRooted($RaizProjeto)) {
        throw '21-E-D8: raiz do projeto precisa ser absoluta'
    }
    $raiz = [System.IO.Path]::GetFullPath($RaizProjeto).TrimEnd([char[]]@('\','/'))
    $alvo = [System.IO.Path]::GetFullPath($Caminho).TrimEnd([char[]]@('\','/'))
    if ($alvo.Equals($raiz,[System.StringComparison]::OrdinalIgnoreCase) -or
        $alvo.StartsWith($raiz + [System.IO.Path]::DirectorySeparatorChar,
            [System.StringComparison]::OrdinalIgnoreCase)) {
        throw '21-E-D8: destino nao pode ficar dentro do Git'
    }
    return $alvo
}

function Assert-21ED8-Contratos {
    param([Parameter(Mandatory=$true)][object]$Startup,
          [Parameter(Mandatory=$true)][object]$Retencao,
          [Parameter(Mandatory=$true)][object]$Catalogo,
          [Parameter(Mandatory=$true)][object]$Revisao,
          [Parameter(Mandatory=$true)][string]$RaizProjeto)
    # Depende das verificacoes D1 e B8 preexistentes, sem alterar suas semanticas.
    Assert-21ED1-Contrato -Contrato $Revisao
    Assert-21ED1-BloqueioPoliticas -Startup $Startup -Retencao $Retencao -Catalogo $Catalogo
    Assert-21EB8-Catalogo -Catalogo $Catalogo
    Assert-21ED8-PropriedadesExatas -Objeto $Retencao.continuous_logs_proposal -Esperadas @(
        'external_directory','proposed_max_age_days',
        'proposed_max_segment_bytes','proposed_max_total_bytes'
    )
    $proposta = $Retencao.continuous_logs_proposal
    if ($proposta.external_directory -isnot [string]) {
        throw '21-E-D8: caminho nao textual'
    }
    [void](Assert-21ED8-CaminhoProposto -Caminho $proposta.external_directory -RaizProjeto $RaizProjeto)
    foreach ($nome in @('proposed_max_age_days','proposed_max_segment_bytes','proposed_max_total_bytes')) {
        $v = $proposta.PSObject.Properties[$nome].Value
        if (($v -isnot [int] -and $v -isnot [long]) -or $v -is [bool]) {
            throw '21-E-D8: limites precisam ser inteiros'
        }
    }
    if ($proposta.proposed_max_age_days -ne 30 -or
        $proposta.proposed_max_segment_bytes -ne 16777216 -or
        $proposta.proposed_max_total_bytes -ne 536870912) {
        throw '21-E-D8: limites divergentes do contrato versionado'
    }
    if ($Revisao.production_adapter_status -cne 'implemented_policy_gated') {
        throw '21-E-D8: adaptador nao esta bloqueado'
    }
    return [pscustomobject]@{
        DiretorioProposto = $proposta.external_directory
        SegmentoBytes = [long]$proposta.proposed_max_segment_bytes
        QuotaBytes = [long]$proposta.proposed_max_total_bytes
        PrazoDias = [int]$proposta.proposed_max_age_days
        Estado = 'DESENHO_NAO_ATIVADO'
    }
}

function Assert-21ED8-IdentidadeACL {
    param([Parameter(Mandatory=$true)][object]$Acl,
          [Parameter(Mandatory=$true)][string]$SidOperador)
    # Apenas um modelo de DirectorySecurity em memoria. Nao consulta Get-Acl externo.
    # Identidade exata do processo atual; funciona tambem em contas Windows
    # cujo SID nao segue o formato de usuario local tradicional S-1-5-21.
    $sidAtual = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    if ($SidOperador -cne $sidAtual) {
        throw '21-E-D8: SID do operador diferente da identidade atual'
    }
    if ($Acl -isnot [System.Security.AccessControl.DirectorySecurity] -or
        -not $Acl.AreAccessRulesProtected) {
        throw '21-E-D8: ACL herdada ou de tipo incorreto'
    }
    $permitidos = @($SidOperador,'S-1-5-18','S-1-5-32-544') | Select-Object -Unique
    $observados = @{}
    $regras = @($Acl.Access)
    if ($regras.Count -ne $permitidos.Count) { throw '21-E-D8: ACL exige somente tres ACEs' }
    foreach ($regra in $regras) {
        if ($regra.IsInherited -or
            $regra.AccessControlType -ne [System.Security.AccessControl.AccessControlType]::Allow) {
            throw '21-E-D8: ACL nao pode conter heranca nem ACE de negacao'
        }
        try {
            $sid = $regra.IdentityReference.Translate([System.Security.Principal.SecurityIdentifier]).Value
        } catch { throw '21-E-D8: identidade ACL nao traduzivel' }
        if ($permitidos -cnotcontains $sid -or $observados.ContainsKey($sid)) {
            throw '21-E-D8: principal inesperado ou duplicado'
        }
        if ($regra.FileSystemRights -ne [System.Security.AccessControl.FileSystemRights]::FullControl -or
            $regra.InheritanceFlags -ne (
                [System.Security.AccessControl.InheritanceFlags]::ContainerInherit -bor
                [System.Security.AccessControl.InheritanceFlags]::ObjectInherit
            ) -or $regra.PropagationFlags -ne [System.Security.AccessControl.PropagationFlags]::None) {
            throw '21-E-D8: direitos ACL fora da proposta'
        }
        $observados[$sid] = $true
    }
    foreach ($sid in $permitidos) {
        if (-not $observados.ContainsKey($sid)) { throw '21-E-D8: principal obrigatorio ausente' }
    }
    return $true
}

function Assert-21ED8-SessaoPlanejada {
    param([Parameter(Mandatory=$true)][string]$DiretorioBase,
          [Parameter(Mandatory=$true)][string]$DiretorioSessao,
          [Parameter(Mandatory=$true)][long]$PidVerificado,
          [Parameter(Mandatory=$true)][long]$CreationTicksVerificados,
          [Parameter(Mandatory=$true)][string]$Nonce,
          [Parameter(Mandatory=$true)][bool]$SessaoPreexistente,
          [Parameter(Mandatory=$true)][bool]$ReservaExclusiva)
    # Contrato de nomes, nao abertura de arquivos ou confirmacao real de PID.
    if ($PidVerificado -lt 1 -or $CreationTicksVerificados -lt 1 -or
        $Nonce -cnotmatch '^[0-9a-f]{32}$' -or $SessaoPreexistente -or -not $ReservaExclusiva) {
        throw '21-E-D8: sessao sem identidade, nonce ou exclusividade'
    }
    if ($DiretorioBase -cne 'C:\dev\rasping-amazon-operacao\logs') {
        throw '21-E-D8: base externa nao autorizada'
    }
    $nome = ('sessao-{0}-{1}-{2}' -f $PidVerificado,$CreationTicksVerificados,$Nonce)
    $esperado = [System.IO.Path]::Combine($DiretorioBase,$nome)
    if ($DiretorioSessao -cne $esperado) { throw '21-E-D8: sessao com caminho inesperado' }
    return $true
}

function New-21ED8-SessaoProdutiva {
    param([Parameter(Mandatory=$true)][string]$Diretorio)
    # NUNCA criar, abrir, truncar, remover ou inspecionar diretorios operacionais nesta etapa.
    throw '21-E-D8: escritor operacional NAO IMPLEMENTADO e NAO AUTORIZADO'
}
