# FASE 21-E-D8: testes offline dos contratos de diretorio e sessao externa.
# Nenhuma escrita, nem mesmo em target. Apenas dados e ACLs em memoria.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$raiz = (Get-Location).ProviderPath
if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) { throw 'D8: Windows necessario' }
if (-not (Test-Path -LiteralPath 'pom.xml' -PathType Leaf)) { throw 'D8: raiz do projeto ausente' }
if (-not (Test-Path -LiteralPath '.git')) { throw 'D8: Git ausente' }
$branch = ((& git branch --show-current) | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $branch -cne 'feat/fase-21-seguranca-governanca-v1') { throw 'D8: branch incorreta' }
foreach ($script in @(
    'infra/retencao/FASE_21_E_A_FUNCOES_RETENCAO.ps1',
    'infra/retencao/FASE_21_E_B1_FUNCOES_SEGMENTACAO.ps1',
    'infra/retencao/FASE_21_E_B8_FUNCOES_CATALOGO.ps1',
    'infra/retencao/FASE_21_E_D1_FUNCOES_PREINTEGRACAO.ps1',
    'infra/retencao/FASE_21_E_D8_FUNCOES_PREPARACAO_EXTERNA.ps1'
)) {
    if (-not (Test-Path -LiteralPath $script -PathType Leaf)) { throw 'D8: dependencia ausente' }
    . (Resolve-Path -LiteralPath $script).ProviderPath
}
function Exigir-D8 { param([bool]$Condicao,[string]$Motivo)
    if (-not $Condicao) { throw ('D8: assercao reprovada: ' + $Motivo) }
}
function Recusar-D8 { param([scriptblock]$Acao,[string]$Motivo)
    $recusado = $false
    try { & $Acao | Out-Null } catch { $recusado = $true }
    Exigir-D8 $recusado $Motivo
}
$startup = Get-Content -LiteralPath 'config/operacao/continuous-startup-policy.v1.json' -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
$retencao = Get-Content -LiteralPath 'config/operacao/retention-policy.v1.json' -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
$catalogo = Get-Content -LiteralPath 'config/operacao/operational-log-catalog.v1.json' -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
$revisao = Get-Content -LiteralPath 'config/operacao/supervisor-log-integration-review.v1.json' -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
Assert-21ED1-FontesIntactas -Raiz $raiz -Contrato $revisao
$plano = Assert-21ED8-Contratos -Startup $startup -Retencao $retencao -Catalogo $catalogo -Revisao $revisao -RaizProjeto $raiz
Exigir-D8 ($plano.Estado -ceq 'DESENHO_NAO_ATIVADO' -and
    $plano.SegmentoBytes -eq 16777216 -and $plano.QuotaBytes -eq 536870912 -and
    $plano.PrazoDias -eq 30) 'contrato-versionado'
Write-Host 'PASS - Politicas e hashes D1 intactos; proposta externa permanece desativada.'

# Adulteracoes sao apenas em objetos copiados para memoria.
foreach ($campo in @('external_directory','proposed_max_age_days','proposed_max_segment_bytes','proposed_max_total_bytes')) {
    $mut = $retencao | ConvertTo-Json -Depth 20 | ConvertFrom-Json
    switch ($campo) {
        'external_directory' { $mut.continuous_logs_proposal.external_directory = 'C:\dev\rasping-amazon-backups\logs' }
        'proposed_max_age_days' { $mut.continuous_logs_proposal.proposed_max_age_days = 0 }
        'proposed_max_segment_bytes' { $mut.continuous_logs_proposal.proposed_max_segment_bytes = 1 }
        'proposed_max_total_bytes' { $mut.continuous_logs_proposal.proposed_max_total_bytes = 0 }
    }
    Recusar-D8 { Assert-21ED8-Contratos -Startup $startup -Retencao $mut -Catalogo $catalogo -Revisao $revisao -RaizProjeto $raiz } ('adulteracao-' + $campo)
}
$surpresa = $retencao | ConvertTo-Json -Depth 20 | ConvertFrom-Json
$surpresa.continuous_logs_proposal | Add-Member -NotePropertyName 'allow_delete' -NotePropertyValue $true
Recusar-D8 { Assert-21ED8-Contratos -Startup $startup -Retencao $surpresa -Catalogo $catalogo -Revisao $revisao -RaizProjeto $raiz } 'campo-inesperado'
$mutStartup = $startup | ConvertTo-Json -Depth 20 | ConvertFrom-Json
$mutStartup.daemon_start_authorized = $true
Recusar-D8 { Assert-21ED8-Contratos -Startup $mutStartup -Retencao $retencao -Catalogo $catalogo -Revisao $revisao -RaizProjeto $raiz } 'daemon'
$mutRetencao = $retencao | ConvertTo-Json -Depth 20 | ConvertFrom-Json
$mutRetencao.continuous_log_capture_authorized = $true
Recusar-D8 { Assert-21ED8-Contratos -Startup $startup -Retencao $mutRetencao -Catalogo $catalogo -Revisao $revisao -RaizProjeto $raiz } 'captura'
Write-Host 'PASS - Mudancas de destino, quota, prazo, esquema e autorizacoes rejeitadas.'

foreach ($caminho in @(
    'target\logs',
    '\\servidor\share\logs',
    'C:\dev\rasping-amazon-backups\logs',
    'C:\Dev\rasping-amazon\target\logs',
    'C:\dev\rasping-amazon-operacao\logs\..',
    'C:\dev\rasping-amazon-operacao\logs:ads',
    'C:\dev\rasping-amazon-operacao\logs\filho',
    'C:/dev/rasping-amazon-operacao/logs'
)) {
    Recusar-D8 { Assert-21ED8-CaminhoProposto -Caminho $caminho -RaizProjeto $raiz } 'caminho-externo'
}
Write-Host 'PASS - Traversal, UNC, ADS, backup, alvo interno e caminho divergente rejeitados.'

# ACL NTFS em memoria, com a mesma receita privada do sandbox B1.
$privada = New-21EB1-ACLPrivada
$operador = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
Exigir-D8 (Assert-21ED8-IdentidadeACL -Acl $privada -SidOperador $operador) 'acl-privada'
$herdada = New-Object System.Security.AccessControl.DirectorySecurity
# Uma ACL nova permanece sem protecao contra heranca e deve falhar fechada.
Recusar-D8 { Assert-21ED8-IdentidadeACL -Acl $herdada -SidOperador $operador } 'acl-herdada'
$ampla = New-21EB1-ACLPrivada
$usuarios = [System.Security.Principal.SecurityIdentifier]::new('S-1-5-32-545')
$rule = [System.Security.AccessControl.FileSystemAccessRule]::new(
    $usuarios, [System.Security.AccessControl.FileSystemRights]::Read,
    [System.Security.AccessControl.InheritanceFlags]::ContainerInherit -bor
    [System.Security.AccessControl.InheritanceFlags]::ObjectInherit,
    [System.Security.AccessControl.PropagationFlags]::None,
    [System.Security.AccessControl.AccessControlType]::Allow)
$ampla.AddAccessRule($rule)
Recusar-D8 { Assert-21ED8-IdentidadeACL -Acl $ampla -SidOperador $operador } 'acl-usuarios-leitura'
Write-Host 'PASS - ACL privada aceita; heranca e permissao adicional de leitura rejeitadas.'

$nonce = '1234567890abcdef1234567890abcdef'
$dir = 'C:\dev\rasping-amazon-operacao\logs'
$ses = [System.IO.Path]::Combine($dir,('sessao-{0}-{1}-{2}' -f 123,638975520000000000,$nonce))
Exigir-D8 (Assert-21ED8-SessaoPlanejada -DiretorioBase $dir -DiretorioSessao $ses -PidVerificado 123 -CreationTicksVerificados 638975520000000000 -Nonce $nonce -SessaoPreexistente:$false -ReservaExclusiva:$true) 'sessao-exclusiva'
Recusar-D8 { Assert-21ED8-SessaoPlanejada -DiretorioBase $dir -DiretorioSessao $ses -PidVerificado 123 -CreationTicksVerificados 638975520000000000 -Nonce $nonce -SessaoPreexistente:$true -ReservaExclusiva:$true } 'reutilizacao'
Recusar-D8 { Assert-21ED8-SessaoPlanejada -DiretorioBase $dir -DiretorioSessao $ses -PidVerificado 123 -CreationTicksVerificados 638975520000000000 -Nonce $nonce -SessaoPreexistente:$false -ReservaExclusiva:$false } 'sem-exclusividade'
Recusar-D8 { Assert-21ED8-SessaoPlanejada -DiretorioBase $dir -DiretorioSessao ($ses + '\outro') -PidVerificado 123 -CreationTicksVerificados 638975520000000000 -Nonce $nonce -SessaoPreexistente:$false -ReservaExclusiva:$true } 'filho-inesperado'
Recusar-D8 { Assert-21ED8-SessaoPlanejada -DiretorioBase $dir -DiretorioSessao $ses -PidVerificado 0 -CreationTicksVerificados 638975520000000000 -Nonce $nonce -SessaoPreexistente:$false -ReservaExclusiva:$true } 'pid-invalido'
Recusar-D8 { New-21ED8-SessaoProdutiva -Diretorio $dir } 'produtor-inativo'
Write-Host 'PASS - Modelo de sessao exige PID, creation ticks, nonce, exclusividade e diretorio novo.'
Write-Host 'PROTEGIDO - Nenhum arquivo criado ou alterado; ACLs e adulteracoes apenas em memoria.'
Write-Host 'PROTEGIDO - Diretorio externo nao consultado; sem Java, daemon, banco, backups ou exclusoes.'
Write-Host 'PASS - Gate 21-E-D8 preparacao externa aprovado; escritor produtivo e integracao seguem proibidos.'
