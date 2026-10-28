# FASE 21-C2-E-D-D-C-A — Gate da CLI operacional com credenciais separadas

## Objetivo

Antes de ativar permanentemente a separacao das credenciais Java, executar um teste
real do caminho de bootstrap da CLI operacional com `rasping_runtime`. `rasping_migrator`
permanece configurado exclusivamente como identidade destinada ao Flyway.

O gate anterior C2-E-D-D-B ja confirmou JDBC real para ambas as roles e
`Flyway.validate()` para 37 migrations. Esta etapa valida a composicao operacional,
sem repetir provisionamento, sem executar migrations e sem iniciar daemon.

## Arquivos completos

- `src/test/java/com/raspingamazon/infrastructure/security/OperationalCredentialCliProbe.java`
- `infra/postgres/security/FASE_21_C2_E_D_D_C_A_EXECUTAR_GATE_CLI.ps1`
- Este documento.

## Contrato de execucao

1. Executar como o **mesmo usuario Windows** que realizou o provisionamento DPAPI.
2. Iniciar no diretorio raiz `C:\dev\rasping-amazon`.
3. Manter worker, scheduler e daemon parados durante o gate.
4. Confirmar que `rasping-amazon-postgres` publica somente `127.0.0.1:5432`.
5. Ler as credenciais dos arquivos DPAPI existentes fora do Git.
6. Injetar `DB_USER`/`DB_PASSWORD` e `DB_MIGRATION_USER`/
   `DB_MIGRATION_PASSWORD` apenas no ambiente temporario do processo Maven.
7. Executar `mvn '-Dtest=OperationalCredentialCliProbe' test`.
8. Restaurar as variaveis de ambiente do processo ao final, inclusive em caso de erro.
9. Nao salvar senhas no `.env`, argumentos do Maven, logs ou arquivos versionados.

## Testes

- Contrato de identidades distinto: `rasping_runtime` / `rasping_migrator`.
- `runs list --limit 1` via `OperationalCliBootstrap` real.
- `jobs list --limit 1` via `OperationalCliBootstrap` real.
- Suas saidas permanecem em memoria para evitar logar dados historicos do banco.
- Nao executar comandos de alteracao de schedules, worker, scheduler ou migrations.

## Resultados esperados

- `C2-E-D-D-C-A PORTA PASS`
- `C2-E-D-D-C-A CONTRATO JAVA PASS`
- `C2-E-D-D-C-A CONFIG PASS`
- `C2-E-D-D-C-A RUNS PASS`
- `C2-E-D-D-C-A JOBS PASS`
- `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`
- `BUILD SUCCESS`
- `C2-E-D-D-C-A GATE PASS`

**Observacao:** o gate nao prova a execucao integral do daemon com menor privilegio,
mas confirma o caminho de leitura operacional da CLI e a compatibilidade das
credenciais na configuracao atual.

## Tratamento de falhas

Qualquer falha bloqueia a ativacao. Nao rodar o provisionamento novamente;
nao modificar roles, HBA, ownership, `.env` ou PostgreSQL para mascarar falhas.
Enviar apenas a saida do teste, sem segredos DPAPI.

## Reversao

Esta etapa nao altera permanentemente o ambiente. Antes do commit, os tres arquivos
podem ser removidos se o gate for reprovado. O executor restaura variaveis do
processo PowerShell mesmo em erro. Nao executar rollback PostgreSQL nesta etapa.

## Proxima fase

Apos validacao e commit, preparar o mecanismo operacional de inicializacao
controlada com credenciais DPAPI, testes e procedimento documentado de reversao,
sem habilitar credenciais administrativas no runtime.
