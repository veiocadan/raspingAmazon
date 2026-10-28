# FASE 21-E-C1 - Testes hermeticos, somente objetos em memoria.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..') -ErrorAction Stop).ProviderPath
Set-Location -LiteralPath $repo
. (Join-Path $PSScriptRoot 'FASE_21_E_A_FUNCOES_RETENCAO.ps1')
. (Join-Path $PSScriptRoot 'FASE_21_E_C1_FUNCOES_SIMULACAO.ps1')

function Assert-21EC1-Teste {
    param([bool]$Condicao,[string]$Descricao)
    if (-not $Condicao) { throw ('21-E-C1: teste reprovado: ' + $Descricao) }
}
function Assert-21EC1-Rejeita {
    param([scriptblock]$Acao,[string]$Descricao)
    $rejeitou = $false
    try { & $Acao | Out-Null } catch { $rejeitou = $true }
    Assert-21EC1-Teste -Condicao $rejeitou -Descricao $Descricao
}

$politica = Get-Content 'config/operacao/retention-policy.v1.json' -Raw -Encoding UTF8 | ConvertFrom-Json
$startup = Get-Content 'config/operacao/continuous-startup-policy.v1.json' -Raw -Encoding UTF8 | ConvertFrom-Json
Assert-21EA-Politica -Politica $politica
Assert-21EC1-StartupBloqueado -Plano $startup
Write-Host 'PASS - Contratos originais E-A e startup validados.'

$agora = [DateTime]::SpecifyKind([datetime]'2026-10-27T20:00:00', [System.DateTimeKind]::Utc)
$limite = $agora.AddDays(-7)
Assert-21EC1-Teste -Condicao (Test-21EC1-AnteriorAoPrazo -UltimaAlteracaoUtc $limite.AddTicks(-1) -AgoraUtc $agora -PrazoDias 7) -Descricao 'mais antigo'
Assert-21EC1-Teste -Condicao (-not (Test-21EC1-AnteriorAoPrazo -UltimaAlteracaoUtc $limite -AgoraUtc $agora -PrazoDias 7)) -Descricao 'igual ao limite'
Assert-21EC1-Teste -Condicao (-not (Test-21EC1-AnteriorAoPrazo -UltimaAlteracaoUtc $agora.AddHours(1) -AgoraUtc $agora -PrazoDias 7)) -Descricao 'data futura'
Assert-21EC1-Rejeita -Acao { Test-21EC1-AnteriorAoPrazo -UltimaAlteracaoUtc $limite -AgoraUtc $agora -PrazoDias 0 } -Descricao 'prazo zero'
Assert-21EC1-Rejeita -Acao { Test-21EC1-AnteriorAoPrazo -UltimaAlteracaoUtc $limite -AgoraUtc $agora -PrazoDias 30 } -Descricao 'prazo inesperado'
Assert-21EC1-Rejeita -Acao { Test-21EC1-AnteriorAoPrazo -UltimaAlteracaoUtc ([datetime]'2026-10-01') -AgoraUtc $agora -PrazoDias 7 } -Descricao 'data sem UTC'
Write-Host 'PASS - Fronteira temporal, futuro, prazos e UTC verificados.'

function New-21EC1-ItemSintetico {
    param([datetime]$Data,[long]$Bytes,[bool]$Redirecionamento=$false)
    $attrs = [System.IO.FileAttributes]::Normal
    if ($Redirecionamento) { $attrs = [System.IO.FileAttributes]::ReparsePoint }
    return [pscustomobject]@{
        Attributes=$attrs; PSIsContainer=$false; Length=$Bytes; LastWriteTimeUtc=$Data
    }
}
$sum = New-21EC1-Contadores
Add-21EC1-ArquivoAoResumo -Contadores $sum -Arquivo (New-21EC1-ItemSintetico -Data $limite.AddDays(-1) -Bytes 100) -AgoraUtc $agora -PrazoDias 7
Add-21EC1-ArquivoAoResumo -Contadores $sum -Arquivo (New-21EC1-ItemSintetico -Data $limite -Bytes 200) -AgoraUtc $agora -PrazoDias 7
Add-21EC1-ArquivoAoResumo -Contadores $sum -Arquivo (New-21EC1-ItemSintetico -Data $agora.AddDays(1) -Bytes 300) -AgoraUtc $agora -PrazoDias 7
Add-21EC1-ArquivoAoResumo -Contadores $sum -Arquivo (New-21EC1-ItemSintetico -Data $limite.AddDays(-9) -Bytes 1000 -Redirecionamento $true) -AgoraUtc $agora -PrazoDias 7
Assert-21EC1-Teste -Condicao ($sum.arquivos -eq 3 -and $sum.bytes -eq 600 -and $sum.candidatos -eq 1 -and $sum.bytes_candidatos -eq 100 -and $sum.redirecionamentos_ignorados -eq 1) -Descricao 'agregacao de metadados'
Assert-21EC1-Rejeita -Acao { Add-21EC1-ArquivoAoResumo -Contadores $sum -Arquivo (New-21EC1-ItemSintetico -Data $limite -Bytes -1) -AgoraUtc $agora -PrazoDias 7 } -Descricao 'bytes negativos'
Assert-21EC1-Teste -Condicao ($sum.arquivos -eq 3 -and $sum.bytes -eq 600) -Descricao 'sem mutacao apos rejeicao'
Write-Host 'PASS - Agregacao em memoria, candidato unico e link ignorado.'

$politicaAlterada = $politica | ConvertTo-Json -Depth 12 | ConvertFrom-Json
$politicaAlterada.destructive_actions_authorized = $true
Assert-21EC1-Rejeita -Acao { Assert-21EA-Politica -Politica $politicaAlterada } -Descricao 'autorizar exclusoes'
$politicaAlterada = $politica | ConvertTo-Json -Depth 12 | ConvertFrom-Json
$politicaAlterada.local_rules[1].relative_path = '../backups'
Assert-21EC1-Rejeita -Acao { Assert-21EA-Politica -Politica $politicaAlterada } -Descricao 'escopo fora da allowlist'
$startupAlterado = $startup | ConvertTo-Json -Depth 12 | ConvertFrom-Json
$startupAlterado.daemon_start_authorized = $true
Assert-21EC1-Rejeita -Acao { Assert-21EC1-StartupBloqueado -Plano $startupAlterado } -Descricao 'partida do daemon'
Write-Host 'PASS - Autorizacao destrutiva, caminho externo e partida rejeitados.'
Write-Host 'PROTEGIDO - Nenhum arquivo de teste criado, alterado ou excluido.'
Write-Host 'PASS - Gate 21-E-C1 hermetico aprovado; retencao permanece apenas simulada.'
