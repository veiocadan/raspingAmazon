# Gate unico de fechamento 21-E: contrato do supervisor + escrita segura no sandbox.
# Nao inicia JVM nem consulta pasta externa. Evidencias de teste permanecem em target.
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$raiz=(Get-Location).ProviderPath
if (((& git branch --show-current) | Out-String).Trim() -cne 'feat/fase-21-seguranca-governanca-v1') {
    throw '21-E: branch incorreta'
}
foreach ($relativo in @(
    'infra/retencao/FASE_21_E_A_FUNCOES_RETENCAO.ps1',
    'infra/retencao/FASE_21_E_B1_FUNCOES_SEGMENTACAO.ps1',
    'infra/retencao/FASE_21_E_B3_FUNCOES_COMPATIBILIDADE.ps1',
    'infra/retencao/FASE_21_E_B8_FUNCOES_CATALOGO.ps1',
    'infra/retencao/FASE_21_E_B9_FUNCOES_CAPTURA_CATALOGADA.ps1',
    'infra/retencao/FASE_21_E_D1_FUNCOES_PREINTEGRACAO.ps1',
    'infra/retencao/FASE_21_E_D2_FUNCOES_ESCRITOR_OFFLINE.ps1',
    'infra/retencao/FASE_21_E_FUNCOES_CAPTURA_OPERACIONAL.ps1'
)) {
    . (Resolve-Path -LiteralPath $relativo -ErrorAction Stop).ProviderPath
}
function Exigir-21E { param([bool]$Sim,[string]$Motivo)
    if (-not $Sim) { throw ('21-E: falha no teste ' + $Motivo) }
}
$revisao=Get-Content 'config/operacao/supervisor-log-integration-review.v1.json' -Raw -Encoding UTF8 | ConvertFrom-Json
$catalogo=Get-Content 'config/operacao/operational-log-catalog.v1.json' -Raw -Encoding UTF8 | ConvertFrom-Json
Assert-21ED1-Contrato -Contrato $revisao
Assert-21ED1-FontesIntactas -Raiz $raiz -Contrato $revisao
Assert-21ED1-FonteSupervisor -Conteudo (Get-Content -LiteralPath $revisao.expected_sources[0].path -Raw -Encoding UTF8)
[void](Assert-21EB1-Contexto -Raiz $raiz)
Assert-21EB8-Catalogo -Catalogo $catalogo
$recusou=$false
try { [void](Assert-21E-AutorizacaoCaptura -Raiz $raiz) } catch { $recusou=$true }
Exigir-21E $recusou 'partida-captura-indesejada'
Write-Host 'PASS - Supervisor integrado, contrato D1 atualizado e autorizacao produtiva bloqueada.'

$diretorio=New-21EB1-Sandbox -Raiz $raiz
$dono=$null;$sessao=$null
try {
    $dono=[IO.FileStream]::new((Join-Path $diretorio 'owner.v1.json'),
        [IO.FileMode]::CreateNew,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    $sessao=[pscustomobject]@{
        Diretorio=$diretorio; Catalogo=$catalogo
        SegmentoMaximo=[long]512; TotalMaximo=[long]4096
        Indice=[int]0; SegmentoBytes=[long]0; TotalBytes=[long]0
        Fluxo=$null; FluxoDono=$dono; Lacrada=$false
    }
    $def=@($catalogo.events)[0]
    $entrada=ConvertTo-Json -InputObject ([ordered]@{
        timestamp=[DateTimeOffset]::UtcNow.ToString('o')
        level=$def.level; event=$def.code; component=$def.component
        operation=$def.operation; outcome=$def.outcomes[0]
        runId=[long]1; jobId=[long]2
    }) -Compress
    $evento=Convert-21EB9-LinhaCatalogada -Linha $entrada -Canal stdout -Catalogo $catalogo
    Exigir-21E ($evento.Classificacao -ceq 'STRUCTURED_CATALOGED') 'evento-catalogado'
    Write-21E-EventoOperacional -Sessao $sessao -LinhaJson $evento.LinhaJson
    $marcador=Convert-21EB9-LinhaCatalogada -Linha 'SEGREDO_FICTICIO_21E_NAO_PERSISTIR' -Canal stderr -Catalogo $catalogo
    Exigir-21E ($marcador.Classificacao -ceq 'REDACTED') 'stderr-redigido'
    Write-21E-EventoOperacional -Sessao $sessao -LinhaJson $marcador.LinhaJson
    Close-21E-SessaoCapturaOperacional -Sessao $sessao
    $segmentos=@(Get-ChildItem -LiteralPath $diretorio -Filter 'operacional-*.jsonl' -File)
    Exigir-21E ($segmentos.Count -eq 1) 'segmentacao'
    $linhas=@(Get-Content -LiteralPath $segmentos[0].FullName -Encoding UTF8)
    Exigir-21E ($linhas.Count -eq 2) 'dois-registros'
    Exigir-21E (-not (($linhas -join '') -match 'SEGREDO_FICTICIO_21E')) 'segredo-ausente'
    Exigir-21E ((ConvertFrom-Json $linhas[0]).event -ceq $def.code) 'evento-original'
    Exigir-21E ((ConvertFrom-Json $linhas[1]).event -ceq 'runtime.stderr.redacted') 'stderr-sem-texto'
    Exigir-21E (([long]$segmentos[0].Length) -eq $sessao.TotalBytes) 'bytes'
    $recusou=$false
    try { Write-21E-EventoOperacional -Sessao $sessao -LinhaJson $evento.LinhaJson } catch { $recusou=$true }
    Exigir-21E $recusou 'lacre-irreversivel'
} finally {
    if ($null -ne $sessao -and -not $sessao.Lacrada) {
        try { Close-21E-SessaoCapturaOperacional -Sessao $sessao } catch { }
    }
    if ($null -ne $dono) { try { $dono.Dispose() } catch { } }
}
Write-Host 'PASS - Escritor integrado: catalogacao, redacao, quota e lacre no sandbox.'
Write-Host 'PROTEGIDO - Nenhuma JVM, banco, supervisor, credencial, backup, pasta externa ou exclusao.'
Write-Host 'PASS - Gate final 21-E aprovado; integracao instalada, captura produtiva desautorizada.'
