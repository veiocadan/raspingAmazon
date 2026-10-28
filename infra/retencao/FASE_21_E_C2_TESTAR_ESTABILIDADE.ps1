# FASE 21-E-C2 - testes adversariais de identidade/metadados somente em memoria.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..') -ErrorAction Stop).ProviderPath
Set-Location -LiteralPath $repo
. (Join-Path $PSScriptRoot 'FASE_21_E_A_FUNCOES_RETENCAO.ps1')
. (Join-Path $PSScriptRoot 'FASE_21_E_C1_FUNCOES_SIMULACAO.ps1')
. (Join-Path $PSScriptRoot 'FASE_21_E_C2_FUNCOES_ESTABILIDADE.ps1')

function Assert-21EC2-Teste {
    param([bool]$Condicao,[string]$Descricao)
    if (-not $Condicao) { throw ('21-E-C2: teste reprovado: ' + $Descricao) }
}
function Assert-21EC2-Rejeita {
    param([scriptblock]$Acao,[string]$Descricao)
    $rejeitou = $false
    try { & $Acao | Out-Null } catch { $rejeitou = $true }
    Assert-21EC2-Teste -Condicao $rejeitou -Descricao $Descricao
}
$politica = Get-Content 'config/operacao/retention-policy.v1.json' -Raw -Encoding UTF8 | ConvertFrom-Json
$startup = Get-Content 'config/operacao/continuous-startup-policy.v1.json' -Raw -Encoding UTF8 | ConvertFrom-Json
Assert-21EA-Politica -Politica $politica
Assert-21EC1-StartupBloqueado -Plano $startup
Write-Host 'PASS - Retencao inventory_only e startup preparation_only confirmados.'

$raizEscopo = Join-Path $repo 'target\surefire-reports'
$criado = [datetime]::SpecifyKind([datetime]'2026-10-01T12:00:00', [System.DateTimeKind]::Utc)
$alterado = [datetime]::SpecifyKind([datetime]'2026-10-12T12:00:00', [System.DateTimeKind]::Utc)
$caminho = Join-Path $raizEscopo 'synthetic-c2.xml'
$caminhoOutro = Join-Path $raizEscopo 'synthetic-c2-extra.xml'
$attrs = [long][System.IO.FileAttributes]::Archive
function New-RegistroTeste {
    param([string]$Nome,[long]$Tamanho=100,[datetime]$Criacao=$criado,
        [datetime]$Alteracao=$alterado,[long]$Atributos=$attrs)
    return New-21EC2-Registro -Caminho $Nome -Bytes $Tamanho `
        -CriadoUtc $Criacao -AlteradoUtc $Alteracao -Atributos $Atributos
}
$primeira = New-21EC2-Foto -Escopo 'maven-test-reports' -Estado 'PRESENTE' `
    -Registros @((New-RegistroTeste -Nome $caminho)) -RedirecionamentosIgnorados 0
$segunda = New-21EC2-Foto -Escopo 'maven-test-reports' -Estado 'PRESENTE' `
    -Registros @((New-RegistroTeste -Nome $caminho)) -RedirecionamentosIgnorados 0
Assert-21EC2-FotosEstaveis -Primeira $primeira -Segunda $segunda -DiretorioEscopo $raizEscopo
Write-Host 'PASS - Duas fotografias identicas aceitas, sem ler arquivos.'

Assert-21EC2-Rejeita -Acao {
    $alvo = New-21EC2-Foto -Escopo 'maven-test-reports' -Estado 'PRESENTE' `
        -Registros @((New-RegistroTeste -Nome $caminho -Tamanho 101)) -RedirecionamentosIgnorados 0
    Assert-21EC2-FotosEstaveis -Primeira $primeira -Segunda $alvo -DiretorioEscopo $raizEscopo
} -Descricao 'tamanho mudou'
Assert-21EC2-Rejeita -Acao {
    $alvo = New-21EC2-Foto -Escopo 'maven-test-reports' -Estado 'PRESENTE' `
        -Registros @((New-RegistroTeste -Nome $caminho -Alteracao $alterado.AddSeconds(1))) -RedirecionamentosIgnorados 0
    Assert-21EC2-FotosEstaveis -Primeira $primeira -Segunda $alvo -DiretorioEscopo $raizEscopo
} -Descricao 'modificacao mudou'
Assert-21EC2-Rejeita -Acao {
    $alvo = New-21EC2-Foto -Escopo 'maven-test-reports' -Estado 'PRESENTE' `
        -Registros @((New-RegistroTeste -Nome $caminho -Criacao $criado.AddSeconds(1))) -RedirecionamentosIgnorados 0
    Assert-21EC2-FotosEstaveis -Primeira $primeira -Segunda $alvo -DiretorioEscopo $raizEscopo
} -Descricao 'criacao mudou'
Assert-21EC2-Rejeita -Acao {
    $alvo = New-21EC2-Foto -Escopo 'maven-test-reports' -Estado 'PRESENTE' `
        -Registros @((New-RegistroTeste -Nome $caminho -Atributos ([long][System.IO.FileAttributes]::ReadOnly))) -RedirecionamentosIgnorados 0
    Assert-21EC2-FotosEstaveis -Primeira $primeira -Segunda $alvo -DiretorioEscopo $raizEscopo
} -Descricao 'atributos mudaram'
Assert-21EC2-Rejeita -Acao {
    $alvo = New-21EC2-Foto -Escopo 'maven-test-reports' -Estado 'PRESENTE' `
        -Registros @((New-RegistroTeste -Nome $caminhoOutro)) -RedirecionamentosIgnorados 0
    Assert-21EC2-FotosEstaveis -Primeira $primeira -Segunda $alvo -DiretorioEscopo $raizEscopo
} -Descricao 'identidade de caminho mudou'
Assert-21EC2-Rejeita -Acao {
    $alvo = New-21EC2-Foto -Escopo 'maven-test-reports' -Estado 'PRESENTE' `
        -Registros @((New-RegistroTeste -Nome $caminho), (New-RegistroTeste -Nome $caminhoOutro)) -RedirecionamentosIgnorados 0
    Assert-21EC2-FotosEstaveis -Primeira $primeira -Segunda $alvo -DiretorioEscopo $raizEscopo
} -Descricao 'arquivo novo'
Assert-21EC2-Rejeita -Acao {
    $alvo = New-21EC2-Foto -Escopo 'maven-test-reports' -Estado 'AUSENTE' -Registros @() -RedirecionamentosIgnorados 0
    Assert-21EC2-FotosEstaveis -Primeira $primeira -Segunda $alvo -DiretorioEscopo $raizEscopo
} -Descricao 'pasta removida'
Assert-21EC2-Rejeita -Acao {
    $alvo = New-21EC2-Foto -Escopo 'maven-test-reports' -Estado 'PRESENTE' `
        -Registros @((New-RegistroTeste -Nome $caminho)) -RedirecionamentosIgnorados 1
    Assert-21EC2-FotosEstaveis -Primeira $primeira -Segunda $alvo -DiretorioEscopo $raizEscopo
} -Descricao 'redirecionamento apareceu'
Assert-21EC2-Rejeita -Acao {
    $alvo = New-21EC2-Foto -Escopo 'maven-test-reports' -Estado 'PRESENTE' `
        -Registros @((New-RegistroTeste -Nome $caminho), (New-RegistroTeste -Nome $caminho)) -RedirecionamentosIgnorados 0
    Assert-21EC2-FotosEstaveis -Primeira $alvo -Segunda $alvo -DiretorioEscopo $raizEscopo
} -Descricao 'caminho duplicado'
Write-Host 'PASS - Mudancas de caminho, tamanho, datas, atributos, quantidade e links rejeitadas.'

Assert-21EC2-Rejeita -Acao {
    Assert-21EC2-CaminhoNoEscopo -Caminho (Join-Path $raizEscopo '..\fora.xml') -DiretorioEscopo $raizEscopo
} -Descricao 'travessia'
Assert-21EC2-Rejeita -Acao {
    Assert-21EC2-CaminhoNoEscopo -Caminho (Join-Path $repo 'target\segredo.xml') -DiretorioEscopo $raizEscopo
} -Descricao 'fora do escopo'
Assert-21EC2-Rejeita -Acao {
    New-21EC2-Registro -Caminho $caminho -Bytes 100 -CriadoUtc $criado -AlteradoUtc $alterado `
        -Atributos ([long][System.IO.FileAttributes]::ReparsePoint)
} -Descricao 'metadados link'
Assert-21EC2-Rejeita -Acao {
    New-21EC2-Registro -Caminho $caminho -Bytes -1 -CriadoUtc $criado -AlteradoUtc $alterado -Atributos $attrs
} -Descricao 'bytes negativos'
$alteradoLocal = [datetime]'2026-10-12T12:00:00'
Assert-21EC2-Rejeita -Acao {
    New-21EC2-Registro -Caminho $caminho -Bytes 100 -CriadoUtc $criado -AlteradoUtc $alteradoLocal -Atributos $attrs
} -Descricao 'timezone ambigua'
$alterada = $politica | ConvertTo-Json -Depth 12 | ConvertFrom-Json
$alterada.destructive_actions_authorized = $true
Assert-21EC2-Rejeita -Acao { Assert-21EA-Politica -Politica $alterada } -Descricao 'exclusao autorizada'
$alterada = $politica | ConvertTo-Json -Depth 12 | ConvertFrom-Json
$alterada.local_rules[1].relative_path = 'target'
Assert-21EC2-Rejeita -Acao { Assert-21EA-Politica -Politica $alterada } -Descricao 'escopo amplo'
Write-Host 'PASS - Travessia, metadados ambiguos e autorizacoes proibidas rejeitados.'
Write-Host 'PROTEGIDO - Nenhum arquivo de teste foi criado, alterado ou removido.'
Write-Host 'PASS - Gate 21-E-C2 em memoria aprovado. Nenhuma exclusao autorizada.'
