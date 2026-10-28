# FASE 21-E-D11: fixture PowerShell filha, somente ambiente local target.
param(
    [Parameter(Mandatory=$true)][ValidateSet('dono','concorrente','reuso','independente')][string]$Modo,
    [Parameter(Mandatory=$true)][string]$Raiz,
    [Parameter(Mandatory=$true)][string]$Cenario,
    [Parameter(Mandatory=$true)][string]$Nonce
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
try {
    foreach ($arquivo in @(
        'infra/retencao/FASE_21_E_A_FUNCOES_RETENCAO.ps1',
        'infra/retencao/FASE_21_E_B1_FUNCOES_SEGMENTACAO.ps1',
        'infra/retencao/FASE_21_E_B8_FUNCOES_CATALOGO.ps1',
        'infra/retencao/FASE_21_E_D1_FUNCOES_PREINTEGRACAO.ps1',
        'infra/retencao/FASE_21_E_D2_FUNCOES_ESCRITOR_OFFLINE.ps1',
        'infra/retencao/FASE_21_E_D8_FUNCOES_PREPARACAO_EXTERNA.ps1',
        'infra/retencao/FASE_21_E_D9_FUNCOES_ADAPTADOR_ISOLADO.ps1',
        'infra/retencao/FASE_21_E_D10_FUNCOES_RESERVA_ATOMICA.ps1',
        'infra/retencao/FASE_21_E_D11_FUNCOES_DONO_SESSAO.ps1'
    )) {
        . (Resolve-Path -LiteralPath (Join-Path $Raiz $arquivo) -ErrorAction Stop).ProviderPath
    }
    $catalogo = Assert-21ED9-Contexto -Raiz $Raiz
    if ($Modo -in @('concorrente','reuso')) {
        try {
            $indevida = New-21ED11-SessaoEnsaio -Raiz $Raiz -Cenario $Cenario `
                -Nonce $Nonce -MaximoSegmentoBytes 256 -MaximoTotalBytes 1024
            Close-21ED11-SessaoEnsaio -Sessao $indevida
            exit 24
        } catch {
            if ($_.Exception.Message -ceq '21-E-D11: diretorio ja reservado; nao reutilizar') {
                exit 23
            }
            exit 31
        }
    }
    $sessao = New-21ED11-SessaoEnsaio -Raiz $Raiz -Cenario $Cenario `
        -Nonce $Nonce -MaximoSegmentoBytes 256 -MaximoTotalBytes 1024
    try {
        $d = $catalogo.events[0]
        $dt = [DateTimeOffset]::UtcNow.ToString('o')
        $linha = ConvertTo-Json -InputObject ([ordered]@{
            timestamp=$dt;level=$d.level;event=$d.code;
            component=$d.component;operation=$d.operation;outcome=@($d.outcomes)[0];
            runId=7;jobId=11
        }) -Compress
        $marcador = ConvertTo-Json -InputObject ([ordered]@{
            timestamp=$dt;level='ERROR';event='runtime.stderr.redacted';
            component='runtime-capture';operation='safe-catalog-classification';outcome='REDACTED'
        }) -Compress
        Write-21ED11-EventoEnsaio -Sessao $sessao -LinhaJson $linha
        Write-21ED11-EventoEnsaio -Sessao $sessao -LinhaJson $marcador
        if ($sessao.Registros -ne 2) { exit 35 }
        if ($Modo -ceq 'dono') {
            $pronto = Join-Path $Cenario 'd11-pronto.marker'
            $liberar = Join-Path $Cenario 'd11-liberar.marker'
            Assert-21EB1-SemRedirecionamento -Caminho $pronto
            Assert-21EB1-SemRedirecionamento -Caminho $liberar
            $fluxo = [IO.FileStream]::new($pronto,[IO.FileMode]::CreateNew,
                [IO.FileAccess]::Write,[IO.FileShare]::None)
            $fluxo.Dispose()
            $ate = [DateTime]::UtcNow.AddSeconds(15)
            while (-not (Test-Path -LiteralPath $liberar -PathType Leaf)) {
                if ([DateTime]::UtcNow -ge $ate) { exit 36 }
                [Threading.Thread]::Sleep(50)
            }
        }
    } finally { Close-21ED11-SessaoEnsaio -Sessao $sessao }
    exit 0
} catch {
    # Nunca imprimir mensagens de erro de entradas nao confiaveis.
    exit 37
}
