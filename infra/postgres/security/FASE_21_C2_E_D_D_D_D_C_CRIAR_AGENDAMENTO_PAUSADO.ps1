# D-C: altera EXCLUSIVAMENTE uma linha de processing_schedule, com autorizacao explicita.
# NAO EXECUTAR ate receber a autorizacao para a fase de escrita.
param(
    [switch]$ConfirmarCriacaoPausada,
    [string]$NomeContainer = 'rasping-amazon-postgres',
    [string]$DiretorioSegredos = 'C:\dev\rasping-amazon-backups\credenciais_c2edcb'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not $ConfirmarCriacaoPausada) { throw 'D-C: confirmacao explicita ausente' }
if ($PSVersionTable.PSVersion.Major -lt 5) { throw 'D-C: PowerShell 5.1 ou superior obrigatorio' }
$scriptPrecheck = 'infra/postgres/security/FASE_21_C2_E_D_D_D_D_C_PRECHECK_AGENDAMENTO.ps1'
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $scriptPrecheck
if ($LASTEXITCODE -ne 0) { throw 'D-C: gate read-only reprovado' }
. 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_A_FUNCOES_CONTROLES.ps1'
. 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_C_FUNCOES_SUPERVISOR.ps1'
$registroProd = 'C:\dev\rasping-amazon-backups\supervisao\rasping-amazon-daemon.v1.json'
if ((Obter-StatusSupervisao-C -CaminhoRegistro $registroProd) -cne 'PARADO_SEM_REGISTRO') {
    throw 'D-C: daemon ou registro produtivo detectado'
}
$origem = [System.IO.Path]::GetFullPath($DiretorioSegredos)
$raiz = [System.IO.Path]::GetFullPath((Get-Location).Path).TrimEnd([char[]]@('\','/'))
if ($origem.StartsWith($raiz + '\',[System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'D-C: DPAPI nao pode estar no repositorio'
}
$arquivoSQL = 'infra/postgres/security/FASE_21_C2_E_D_D_D_D_C_INSERIR_AGENDAMENTO_PAUSADO.sql'
$arquivoShell = 'infra/postgres/security/FASE_21_C2_E_D_D_D_D_C_EXECUTAR_INSERCAO.sh'
$envExistia = Test-Path -LiteralPath '.env' -PathType Leaf
$hashAntes = if ($envExistia) { (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash } else { $null }
foreach ($arquivo in @($arquivoSQL,$arquivoShell)) {
    $nome = [System.IO.Path]::GetFileName($arquivo)
    & docker cp $arquivo ($NomeContainer + ':/tmp/' + $nome) | Out-Null
    if ($LASTEXITCODE -ne 0) { throw ('D-C: nao foi possivel instalar SQL publico: ' + $nome) }
}
$segredo = $null
try {
    $segredo = Ler-SenhaDPAPI-C-C -Caminho (Join-Path $origem 'rasping_runtime.dpapi')
    $saida = @($segredo | & docker compose -f infra/compose.yml exec -T postgres sh '/tmp/FASE_21_C2_E_D_D_D_D_C_EXECUTAR_INSERCAO.sh' 2>$null)
    if ($LASTEXITCODE -ne 0) { throw 'D-C: insercao PostgreSQL falhou; nao repetir automaticamente' }
} finally {
    Remove-Variable segredo -ErrorAction SilentlyContinue
}
if ($saida.Count -ne 1 -or [string]$saida[0] -cne 'D_C_AGENDAMENTO_PAUSADO_COMMIT_PASS') {
    throw 'D-C: marcador de COMMIT inesperado, verificar banco antes de repetir'
}
Write-Host '=== C2-E-D-D-D-D-C SQL COMMIT PASS - agendamento amazon-deals criado PAUSADO ==='
if ((Test-Path -LiteralPath '.env' -PathType Leaf) -ne $envExistia) { throw 'D-C: existencia .env alterada' }
if ($envExistia -and (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash -cne $hashAntes) { throw 'D-C: hash .env mudou' }
if ((Obter-StatusSupervisao-C -CaminhoRegistro $registroProd) -cne 'PARADO_SEM_REGISTRO') { throw 'D-C: daemon apareceu inesperadamente' }
Write-Host '=== C2-E-D-D-D-D-C ENV PASS - arquivo .env e daemon preservados ==='
Write-Host '=== C2-E-D-D-D-D-C BLOQUEIO ATIVO - daemon ainda nao autorizado ==='
