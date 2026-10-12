# FASE 21-C1 — Contrato e auditoria de identidades PostgreSQL

**Estado:** auditoria local, somente leitura.  
**Fase:** 21 — segurança e governança operacional.  
**Entrada:** branch `feat/fase-21-seguranca-governanca-v1`, commit `957618b`; baseline informado: **1.817 testes verdes**.

## 1. Objetivo

Registrar evidencias concretas da identidade utilizada pelo PostgreSQL, da propriedade dos objetos e dos privilegios existentes antes de introduzir separacao de contas. A auditoria **nao faz a separacao** e nao deve ser confundida com um gate de seguranca concluido.

## 2. Estado conhecido e limites da evidencia

- O `infra/compose.yml` do baseline inicia `postgres:18.6` com `POSTGRES_USER=rasping`, `POSTGRES_DB=rasping_amazon` e `POSTGRES_PASSWORD=${DB_PASSWORD}`.
- `DatabaseMigration.migrate(ApplicationConfig)` e `DatabaseConnection.open(ApplicationConfig)` utilizam as mesmas credenciais `databaseUser()` e `databasePassword()`.
- O contrato da FASE 21-B2 usa `DB_USER` e `DB_PASSWORD` para a configuracao corrente e reserva explicitamente a separacao entre bootstrap, migrations e runtime para a FASE 21-C.
- O projeto utiliza Flyway e o PostgreSQL e a fonte de verdade persistente. O baseline documentado de migrations e V37.
- A configuracao acima descreve o baseline do repositorio consultado e precisa ser contrastada com o resultado da auditoria da branch local.

## 3. Identidades pretendidas (nao criadas nesta etapa)

| Identidade logica | Finalidade | Privilegios-alvo |
|---|---|---|
| Bootstrap administrativo | Provisionamento e manutencao de roles e ownership | DDL e administracao sob controle; nao utilizado pelo worker |
| Migrator Flyway | Aplicar e validar migrations versionadas | Ownership e DDL no escopo do schema; acesso ao `flyway_schema_history` |
| Runtime | Executar scheduler, workers, orquestracao e CLI operacional | `CONNECT`, `USAGE` no schema e DML estritamente necessario; sem `CREATEROLE`, `CREATEDB`, `SUPERUSER` e sem DDL |
| Backup/restore | Operacao de recuperacao e teste restauravel | Acesso separado do runtime, com retencao, armazenamento e credenciais proprias |

Nao atribuir a identidade runtime `SUPERUSER` nem propriedade do schema/tabelas, e nao usar a conta de migracao nas operacoes normais. As permissoes exatas de runtime so devem ser definidas apos mapear todas as operacoes JDBC e o fluxo de restore.

## 4. Executar a auditoria

Na raiz do projeto (`C:\dev\rasping-amazon`), abra **PowerShell** e execute:

```powershell
Set-Location "C:\dev\rasping-amazon"

docker compose -f infra/compose.yml ps

Get-Content -Raw "infra/postgres/audit/FASE_21_C1_AUDITORIA_ROLES.sql" | docker compose -f infra/compose.yml exec -T postgres psql -X -U rasping -d rasping_amazon -v ON_ERROR_STOP=1 -f -
```

**Nao** cole nem envie senhas, tokens, variaveis `DB_PASSWORD`, cadeias de conexao autenticadas ou conteudo de `.env`. O script consulta nomes de roles, flags de privilegios e propriedades; nao consulta credenciais. Se o container usa outra identidade, **nao adivinhe o usuario**: interrompa e reporte o erro.

Se `docker compose ... ps` indicar que o servico nao esta disponivel, interrompa a auditoria: nao recrie containers/volumes nem redefina credenciais nesta etapa.

## 5. Resultado esperado e sinais de bloqueio

- Todas as consultas numeradas C1.1–C1.9 retornam sem erro.
- A transacao e iniciada com `BEGIN TRANSACTION READ ONLY` e encerrada com `ROLLBACK`. Nenhuma operacao DDL ou DML e realizada.
- A role de runtime atual, se corresponder a `rasping` com `is_superuser = true`, confirma **risco aberto**, nao sucesso da separacao.
- Os dados de ownership e privilegios servem para preparar uma transicao idempotente e com rollback previamente definido.
- Objetos criados por Flyway e eventuais ACLs/default privileges precisam ser considerados para migrations futuras, sem mudar checksums historicos.

## 6. Criterios para encerrar C1 e iniciar C2

- [ ] Auditoria completa obtida do ambiente PostgreSQL existente.
- [ ] Roles de login e atributos identificados.
- [ ] Proprietarios de tabelas, sequences, views e schema identificados.
- [ ] Privilegios de DML, DDL e sequences identificados.
- [ ] Configuracao real do Java e do Compose conferida contra a evidencia.
- [ ] A separacao das credenciais sera implementada em arquivos completos, com testes, sem interromper Flyway e sem destruir dados existentes.

**Fora desta etapa:** mudar senhas, aplicar `GRANT`/`REVOKE`, editar a role `rasping`, habilitar autenticacao de painel, criar conta de backup, testar restore ou alterar o Compose.
