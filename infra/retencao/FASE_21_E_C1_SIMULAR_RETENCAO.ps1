# FASE 21-E-C1: relatorio read-only de candidatos por prazo PROPOSTO.
# NAO possui modo de limpeza, exclusao, copia, rotacao ou agendamento.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..') -ErrorAction Stop).ProviderPath
Set-Location -LiteralPath $repo
. (Join-Path $PSScriptRoot 'FASE_21_E_A_FUNCOES_RETENCAO.ps1')
. (Join-Path $PSScriptRoot 'FASE_21_E_C1_FUNCOES_SIMULACAO.ps1')

$politica = Get-Content -LiteralPath 'config/operacao/retention-policy.v1.json' `
    -Raw -Encoding UTF8 -ErrorAction Stop | ConvertFrom-Json -ErrorAction Stop
$startup = Get-Content -LiteralPath 'config/operacao/continuous-startup-policy.v1.json' `
    -Raw -Encoding UTF8 -ErrorAction Stop | ConvertFrom-Json -ErrorAction Stop
Assert-21EA-Politica -Politica $politica
Assert-21EC1-StartupBloqueado -Plano $startup
$agora = [DateTime]::UtcNow
Write-Host 'PASS - Contratos inventory_only e preparation_only confirmados.'
Write-Host ('INSTANTE_REFERENCIA_UTC=' + $agora.ToString('o'))
foreach ($regra in $politica.local_rules) {
    $resumo = Get-21EC1-ResumoEscopo -RaizRepositorio $repo -Regra $regra -AgoraUtc $agora
    # Verificacao cruzada usando o inventario E-A ja aprovado.
    $baseline = Get-21EA-ResumoInventario -RaizRepositorio $repo -Regra $regra -AgoraUtc $agora
    if ($resumo.estado -cne $baseline.estado -or
        $resumo.arquivos -ne $baseline.arquivos -or
        $resumo.bytes -ne $baseline.bytes -or
        $resumo.candidatos -ne $baseline.anteriores_ao_prazo_proposto -or
        $resumo.redirecionamentos_ignorados -ne $baseline.reparse_points_ignorados) {
        throw '21-E-C1: divergencia entre inventarios; nenhuma acao permitida'
    }
    Write-Host ('ESCOPO={0}; ESTADO={1}; ARQUIVOS={2}; BYTES={3}; CANDIDATOS_APENAS_SIMULADOS={4}; BYTES_CANDIDATOS={5}; REDIRECIONAMENTOS_IGNORADOS={6}' -f `
        $resumo.escopo,$resumo.estado,$resumo.arquivos,$resumo.bytes,
        $resumo.candidatos,$resumo.bytes_candidatos,$resumo.redirecionamentos_ignorados)
}
Write-Host 'PROTEGIDO - Nenhum arquivo listado nominalmente ou modificado.'
Write-Host 'PROTEGIDO - Sem inspecao de credenciais, supervisao, logs externos, PostgreSQL ou backups.'
Write-Host 'PASS - Simulacao somente-leitura concluida; ZERO exclusoes e ZERO rotacoes.'
