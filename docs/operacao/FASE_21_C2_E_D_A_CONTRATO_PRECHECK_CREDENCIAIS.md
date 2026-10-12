# FASE 21-C2-E-D-A — Contrato de prontidao para credenciais separadas

## Estado de entrada confirmado

- Branch: `feat/fase-21-seguranca-governanca-v1`.
- Commit `057d50d` da aplicacao HBA SCRAM, com Git limpo.
- `rasping_migrator`: owner do schema `public`, 29 tabelas e 26 sequences; `NOLOGIN NOINHERIT`, sem senha e sem privilegios administrativos globais.
- `rasping_runtime`: grants explicitos — 27 tabelas SELECT, 25 INSERT, 12 UPDATE, 23 sequences USAGE; nenhuma permissao DELETE/TRUNCATE/CREATE, nem acesso as tabelas Flyway.
- PostgreSQL 18.6; HBA com sete regras SCRAM (`trust` eliminado), autenticacao real positiva e negativa testada via socket, loopback e rede Docker na C2-E-C-C-B.
- Porta publicada somente no host `127.0.0.1:5432`.
- Backup de dados e backup HBA permanecem fora do Git.

## Decisao de arquitetura a preservar

As roles `rasping_runtime` e `rasping_migrator` sao as identidades tecnicas ja planejadas e possuem grants/ownership especificos. A proxima subetapa devera preparar **senhas distintas**, protegidas e armazenadas fora do repositorio, para autentica-las. Nenhuma das duas recebera SUPERUSER, CREATEDB, CREATEROLE, REPLICATION, BYPASSRLS ou memberships ampliadoras de acesso. Nao compartilhar a mesma senha entre os usuarios tecnicos.

**Bloqueio obrigatorio de aplicacao:** o codigo atual usa `ApplicationConfig.databaseUser()` e `ApplicationConfig.databasePassword()` tanto em `DatabaseConnection.open` quanto em `DatabaseMigration.migrate`, alimentados por `DB_USER` e `DB_PASSWORD`. Antes de trocar `DB_USER` para `rasping_runtime`, a aplicacao precisa separar explicitamente o contrato de credenciais de **migrações Flyway** e **runtime JDBC**, com configuracao/segredos independentes, testes automatizados e compatibilidade planejada. Nao usar o runtime para executar migrations e nao manter o superuser `rasping` no runtime final.

A ativacao das roles PostgreSQL e a troca de credenciais da aplicacao devem ocorrer em passos pequenos e verificaveis, com rollback preparado, sem senhas em Git, logs, tela, historico do PowerShell nem mensagens enviadas ao chat. `PGPASSWORD` durante estas auditorias usa apenas a variavel ja existente dentro do container; nenhum segredo novo e criado.

## Arquivos completos

1. `infra/postgres/security/FASE_21_C2_E_D_A_PRECHECK_IDENTIDADES.sql`: somente leitura administrativa; confirma estado HBA, ownership, grants, roles `NOLOGIN` e ausencia de password/membership, sem expor hashes.
2. `infra/postgres/security/FASE_21_C2_E_D_A_VERIFICAR_RUNTIME_SIMULADO.sql`: `SET LOCAL ROLE rasping_runtime`, valida grants efetivos com transacao READ ONLY e sem leitura de registros de negocio.
3. `infra/postgres/security/FASE_21_C2_E_D_A_EXECUTAR_PRECHECK.ps1`: gate que confere a estrutura Java existente, o bind Docker e roda os dois SQLs usando a senha administrativa somente dentro do container PostgreSQL.
4. `infra/postgres/security/FASE_21_C2_E_D_A_EXECUTAR_SQL.sh`: auxiliar de autenticacao no container, utiliza POSTGRES_PASSWORD ja existente sem exibi-la.
5. Este documento operacional.

## Instrucoes para Windows PowerShell

Com worker, scheduler e demais processos Java parados, na raiz do projeto:

```powershell
Set-Location 'C:\dev\rasping-amazon'
$ErrorActionPreference = 'Stop'

powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'infra/postgres/security/FASE_21_C2_E_D_A_EXECUTAR_PRECHECK.ps1'
if ($LASTEXITCODE -ne 0) { throw 'C2-E-D-A: precheck reprovado; interromper.' }

git diff --check
git status
```

Esperado, sem senha na saida:

- `C2-E-D-A FONTE PASS` e alerta que as credenciais Java ainda nao foram separadas;
- `C2-E-D-A PORTA PASS`;
- `C2-E-D-A PRECHECK IDENTIDADES PASS`;
- `C2-E-D-A RUNTIME SIMULADO PASS`;
- `C2-E-D-A GATE PASS`.

**Nao executar migrations, nao habilitar LOGIN, nao configurar segredos novos, nao alterar DB_USER/DB_PASSWORD, nao alterar roles/HBA e nao fazer commit/push antes de validar o resultado.**

## Limites desta evidencia

A auditoria do arquivo HBA nao substitui o teste real da C2-E-C-C-B nem comprova TLS (o servidor continua `ssl=off`). Os checks sobre o codigo Java validam o contrato basico de conexao, mas nao substituem a revisao e os testes de compilacao da alteracao futura. A simulacao SET ROLE nao comprova login real das identidades tecnicas; isso sera tratado depois de gerar credenciais distintas com seguranca.
