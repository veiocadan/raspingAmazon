# FASE 21-C2-E-D-B — Contrato Java de credenciais PostgreSQL independentes

## Objetivo

Separar as credenciais destinadas ao processamento JDBC e ao Flyway, sem alterar o
schema, o HBA ou as roles nesta etapa. A mudanca e de codigo e de contrato de
variaveis de ambiente, **nao** de provisionamento nem de ativacao de LOGIN.

## Configuracao

| Destino | Usuario | Senha |
|---|---|---|
| Runtime JDBC (DatabaseConnection) | `DB_USER` | `DB_PASSWORD` |
| Migracao Flyway (DatabaseMigration) | `DB_MIGRATION_USER` | `DB_MIGRATION_PASSWORD` |

`DB_MIGRATION_USER` e `DB_MIGRATION_PASSWORD` devem ser configuradas juntas.
Quando ambas estao AUSENTES, Flyway usa temporariamente `DB_USER`/`DB_PASSWORD`,
para preservar os testes e a inicializacao existentes **antes** da ativacao das
roles especificas. Essa compatibilidade nao representa separacao efetiva em
execucao e sera removida numa etapa posterior, apos migracao operacional.

Uma configuracao parcial, em branco ou com o mesmo usuario do runtime quando
explicitamente configurada e rejeitada antes da conexao. Senhas nao sao
normalizadas (nao se aplica trim) e nao sao inseridas em excecoes nem `toString`.

## Pontos de aplicacao

- `EnvironmentConfigProvider.load()` resolve as variaveis do ambiente.
- `ApplicationConfig` carrega as duas identidades; o construtor existente com
  seis argumentos continua valido para chamadores antigos.
- `DatabaseConnection.open()` continua consumindo `databaseUser()` e
  `databasePassword()`, sem alteracao de fonte.
- `DatabaseMigration.migrate()` utiliza exclusivamente
  `databaseMigrationUser()` e `databaseMigrationPassword()`.

## Arquivos completos entregues

- `src/main/java/com/raspingamazon/infrastructure/config/ApplicationConfig.java`
- `src/main/java/com/raspingamazon/infrastructure/config/EnvironmentConfigProvider.java`
- `src/main/java/com/raspingamazon/infrastructure/migration/DatabaseMigration.java`
- `src/test/java/com/raspingamazon/infrastructure/config/ApplicationConfigTest.java`
- `src/test/java/com/raspingamazon/infrastructure/config/EnvironmentConfigProviderTest.java`
- `.env.example`
- Este documento.

## Gate desta etapa

1. Aplicar os arquivos completos, preservando as pastas.
2. **Nao configurar** `DB_MIGRATION_USER` / `DB_MIGRATION_PASSWORD` ainda.
3. Executar testes direcionados e suite completa Maven.
4. Confirmar que nenhuma credencial aparece no Git ou logs.
5. Nao mudar roles, HBA, grants, ownership, credenciais reais ou containers.
6. Solicitar verificacao do resultado antes do commit.

## Testes esperados

- Construtor historico permanece compativel.
- Credenciais dedicadas migram exclusivamente para os campos Flyway.
- Variaveis parcialmente presentes sao rejeitadas.
- Senhas ficam ausentes de `toString()` e mensagens de erro.
- Testes existentes de integracao permanecem validos com o fallback transitorio.

## Rollback de codigo

Antes de commit, restaurar os seis arquivos pelo Git somente apos preservar
qualquer alteracao local relevante. A reversao do codigo nao altera PostgreSQL.
**Nao executar scripts de rollback da base** por uma falha de testes Java.
