# FASE 21-C2-E-D-D-B — Gate Java das identidades PostgreSQL

## Antecedentes

Na C2-E-D-D-A (`63eb82a`), autenticacao e privilegios de `rasping_runtime` e `rasping_migrator` foram validados diretamente no PostgreSQL. Na C2-E-D-B (`fd89d3e`), o contrato `ApplicationConfig` foi separado: `DB_USER`/`DB_PASSWORD` para o JDBC de execucao, e `DB_MIGRATION_USER`/`DB_MIGRATION_PASSWORD` para o Flyway. As credenciais efetivas foram provisionadas na C2-E-D-C-B (`00c9c16`) e armazenadas em DPAPI no Windows, fora do Git.

A C2-E-D-D-B verifica **a integracao real Java** antes de modificar o modo de inicializacao da aplicacao. E um gate somente leitura; **nao altera** `.env`, Compose, Java principal, roles, PostgreSQL, HBA, senhas ou ownership. Nao executa `Flyway.migrate()`, `repair()` nem `clean()`.

## Arquivos completos

1. `src/test/java/com/raspingamazon/infrastructure/security/CredentialSeparationJdbcFlywayProbe.java`: prova JUnit de acesso JDBC e validacao Flyway. Terminacao `Probe` evita inclusao automatica na suite padrao; executar explicitamente com `-Dtest`.
2. `infra/postgres/security/FASE_21_C2_E_D_D_B_EXECUTAR_GATE_JAVA.ps1`: carrega segredos DPAPI no ambiente efemero do processo filho Maven, verifica a porta Docker e aciona o Java. Restaura as variaveis de ambiente em `finally`.
3. Este contrato operacional.

## Requisitos

- Branch `feat/fase-21-seguranca-governanca-v1`, com C2-E-D-D-A concluida e Git limpo antes da extracao.
- PostgreSQL em execucao, com a porta publicada somente em `127.0.0.1:5432` e HBA SCRAM-SHA-256.
- Mesma conta Windows que provisionou os arquivos DPAPI em `C:\dev\rasping-amazon-backups\credenciais_c2edcb`.
- Java/Maven usados na suite do projeto.
- Aplicacao Java, worker e scheduler parados; nao introduzir concorrencia na janela de transicao.
- Confiar no ambiente local Windows: durante o teste, os segredos sao variaveis de ambiente do processo Maven/Java (nao se tornam segredos permanentes do usuario). Administradores do host podem inspecionar processos; nao executar sob conta compartilhada ou em ambiente nao confiavel.

## Execucao (sem alterar credenciais da aplicacao)

```powershell
Set-Location 'C:\dev\rasping-amazon'
$ErrorActionPreference = 'Stop'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'infra/postgres/security/FASE_21_C2_E_D_D_B_EXECUTAR_GATE_JAVA.ps1'
if ($LASTEXITCODE -ne 0) { throw 'C2-E-D-D-B reprovada. Nao alterar a inicializacao Java.' }
git diff --check
git status
```

## Resultado esperado

- `C2-E-D-D-B PORTA PASS`
- `C2-E-D-D-B CONTRATO JAVA PASS`
- `C2-E-D-D-B JDBC RUNTIME PASS`
- `C2-E-D-D-B JDBC MIGRATOR PASS`
- `C2-E-D-D-B FLYWAY VALIDATE PASS`
- `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`
- `BUILD SUCCESS`
- `C2-E-D-D-B GATE PASS`

### Escopo das verificacoes Java

- `EnvironmentConfigProvider.load()` interpreta as duas identidades distintas.
- `DatabaseConnection.open(...)` estabelece uma **conexao JDBC real** como `rasping_runtime`, confirma `current_user`, consulta efetiva e matriz de grants (27 SELECT, 25 INSERT, 12 UPDATE, zero DELETE/TRUNCATE; 23 USAGE em 26 sequences; sem acesso as tabelas Flyway e configuration_version).
- A conexao JDBC de `rasping_migrator` valida o owner do schema public, das 29 tabelas e 26 sequences, e a leitura da tabela `flyway_schema_history`.
- `Flyway.validate()` conecta usando a identidade de migracao; verifica o historico de migracoes **sem executar migracoes pendentes**.

## Seguranca e limites

- Nao se escrevem arquivos `.env`, `pgpass`, XML de secrets, logs de senha ou scripts de substituicao de codigo.
- Segredos nao passam nos argumentos CLI. A senha DPAPI e convertida para ambiente do processo Maven filho e removida do ambiente do processo executor apos a execucao; isso nao elimina a senha de memoria das JVMs ate que sejam encerradas.
- O gate nao ativa o daemon, nao prova INSERT/UPDATE reais do runtime e nao executa Flyway migrate; esses passos exigem fase controlada posterior.
- Um `Flyway.validate()` reprovado pode sinalizar diferenca entre historico do banco e migrations existentes; **nao utilizar repair nem migrate automaticamente**. Parar e examinar a saida, sem incluir segredos.
- Em falha, manter a aplicacao no modo anterior; preservar backup do banco e HBA; nao rodar provisionamento novamente.

## Fase seguinte

Depois da aprovacao da prova Java e commit: preparar inicializacao controlada com variaveis de ambiente provenientes do DPAPI, preservacao do fluxo anterior como rollback e testes do processo antes de trocar o modo de operacao. Nenhuma alteracao permanente de `.env` deve ocorrer sem autorizacao.
