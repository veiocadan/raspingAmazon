# FASE 21-C2-E-D-D-C-B — Execucao operacional da CLI real com DPAPI

## Objetivo

Apos o commit `573c49a`, as provas anteriores confirmaram JDBC com `rasping_runtime`, Flyway `validate()` com `rasping_migrator` e o caminho de CLI por testes JUnit. Esta etapa executa **a entrada real** `OperationalCliMain` por `mvn exec:java`, com credenciais independentes injetadas apenas no ambiente do processo PowerShell filho.

Este procedimento e uma **ativacao pontual da CLI** para consultas; **nao** e a migracao permanente do servico, do worker ou do scheduler. Nao inicia daemon, nao executa `migrate()`, nao altera `.env`, PostgreSQL, HBA, ownership ou roles.

## Arquivos completos

1. `infra/postgres/security/FASE_21_C2_E_D_D_C_B_PRECHECK_CLI.ps1` — somente leitura, testa DPAPI, porta e contrato Java.
2. `infra/postgres/security/FASE_21_C2_E_D_D_C_B_EXECUTAR_CLI_DPAPI.ps1` — executa a CLI real em modo leitura e descarta a saida operacional apos verificar o cabecalho.
3. Este documento.

## Precondicoes

- Executar no Windows PowerShell, na raiz `C:\dev\rasping-amazon`.
- Utilizar o mesmo usuario Windows que provisionou o DPAPI.
- Manter o daemon Java, worker e scheduler parados durante a validacao.
- Preservar o container `rasping-amazon-postgres` e a porta `127.0.0.1:5432`.
- Os dois `.dpapi` devem permanecer fora do Git, em `C:\dev\rasping-amazon-backups\credenciais_c2edcb`.
- Nao recriar senhas, roles ou alterar `.env`.

## Execucao segura em duas passagens

**Passagem 1 — precheck**, sem credenciais em texto nem execucao Java:

```powershell
Set-Location "C:\dev\rasping-amazon"
$ErrorActionPreference = "Stop"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "infra/postgres/security/FASE_21_C2_E_D_D_C_B_PRECHECK_CLI.ps1"
if ($LASTEXITCODE -ne 0) { throw "C2-E-D-D-C-B PRECHECK reprovado. Nao executar a CLI." }
git diff --check
git status
```

**Passagem 2 — so depois da autorizacao e PASS do precheck**:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "infra/postgres/security/FASE_21_C2_E_D_D_C_B_EXECUTAR_CLI_DPAPI.ps1" -ConfirmarExecucao
if ($LASTEXITCODE -ne 0) { throw "C2-E-D-D-C-B: nao aprovada; preservar o erro sem divulgar segredos." }
```

## Operacoes realizadas na segunda passagem

- Validacao de `.dpapi` via mesmo usuario Windows e de porta Docker loopback.
- Leitura DPAPI para a identidade runtime `DB_USER=rasping_runtime` e migrator `DB_MIGRATION_USER=rasping_migrator`.
- Injecao das senhas somente no ambiente do processo PowerShell filho; nunca em argumentos Maven ou arquivos versionados.
- `mvn -q -DskipTests test-compile`: compila, sem executar testes ou migracoes.
- `mvn -q '-Dexec.args=evaluations list --limit 1' exec:java` e consultas analogas para `runs`, `jobs`, `publications` e `alerts`.
- Checagem de retorno Maven e do cabecalho das respostas; **saida bruta permanece em memoria e nao aparece no terminal**, evitando vazamento de dados historicos.
- Restauro das variaveis de ambiente do processo em `finally` mesmo em falha; verificacao do hash de `.env`.

## PASS esperado

- `C2-E-D-D-C-B DPAPI PASS`
- `C2-E-D-D-C-B PORTA PASS`
- `C2-E-D-D-C-B JAVA PASS`
- `C2-E-D-D-C-B PRECHECK PASS`
- Em execucao futura: `C2-E-D-D-C-B COMPILACAO PASS`, cinco rotas `CLI ... PASS`, `C2-E-D-D-C-B EXECUCAO PASS` e `C2-E-D-D-C-B AMBIENTE PASS`.

## Contingencia e limites

Qualquer falha interrompe a sequencia. Nao executar novamente o provisionamento de senhas nem alterar permissoes para contornar uma falha. Enviar apenas marcadores PASS e mensagens genericas, **nunca o arquivo DPAPI, senha, hash de senha ou dumps operacionais**.

Como este procedimento nao altera o estado persistido, a reversao e simplesmente encerrar a CLI: as variaveis do processo sao restauradas no `finally`. A proxima etapa devera tratar separadamente a inicializacao persistente/daemon, incluindo um gate especifico de escrita com menor privilegio. Esta etapa **nao** demonstra que o worker completo possui todas as permissoes de gravacao necessarias.
