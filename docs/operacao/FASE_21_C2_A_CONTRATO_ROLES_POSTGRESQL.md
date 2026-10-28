# FASE 21-C2-A — Provisionamento de identidades PostgreSQL

**Estado:** etapa preparatoria de C2. **Nao conclui a separacao efetiva de credenciais.**

## Entrada comprovada

- C1 registrou o unico login nao interno `rasping`, com `SUPERUSER`, `CREATEROLE`, `CREATEDB` e `REPLICATION`.
- `rasping` e proprietario das 29 tabelas e 26 sequences no schema `public`.
- Nenhuma default ACL foi encontrada na auditoria C1.
- Java e Flyway ainda usam a identidade `DB_USER`/`DB_PASSWORD` comum.
- C1 foi registrada no commit `0216ec2`, com working tree clean.

## Contrato desta etapa

Criar somente as roles **`rasping_migrator`** e **`rasping_runtime`**, ambas:

- `NOLOGIN`, `NOINHERIT`, `NOSUPERUSER`, `NOCREATEDB`, `NOCREATEROLE`, `NOREPLICATION`, `NOBYPASSRLS`.
- Sem senha, membership, ownership ou privilegios sobre objetos concedidos pelo script.
- A regra de `NOLOGIN` impede troca prematura da configuracao de aplicacao.
- A role `rasping`, o banco `rasping_amazon`, o schema `public`, as migrations Flyway e os dados atuais permanecem intocados.

Nao conceder `CREATE` ao runtime. Migrations historicas nao devem ser reescritas. A concessao de DML, transferencia de ownership, segredo de migrator, segredo de runtime, configuracao separada e testes de execucao devem ser introduzidos em etapas controladas posteriores e com rollback.

## Arquivos

- `infra/postgres/security/FASE_21_C2_A_CRIAR_ROLES.sql`: cria ou verifica roles sem LOGIN, atomicamente.
- `infra/postgres/security/FASE_21_C2_A_VERIFICAR_ROLES.sql`: consultas somente leitura sobre atributos e privilegios.
- `infra/postgres/security/FASE_21_C2_A_DESFAZER_ROLES.sql`: rollback **apenas enquanto roles nao forem ativadas**.

## Execucao

Na raiz do projeto, com PostgreSQL existente ativo, conectado como `rasping`:

```powershell
Get-Content -Raw -Encoding UTF8 "infra/postgres/security/FASE_21_C2_A_CRIAR_ROLES.sql" | docker compose -f infra/compose.yml exec -T postgres psql -X -U rasping -d rasping_amazon -v ON_ERROR_STOP=1 -f -

Get-Content -Raw -Encoding UTF8 "infra/postgres/security/FASE_21_C2_A_VERIFICAR_ROLES.sql" | docker compose -f infra/compose.yml exec -T postgres psql -X -U rasping -d rasping_amazon -v ON_ERROR_STOP=1 -f -
```

Nao mostrar nem alterar `DB_PASSWORD`. Nao reiniciar, recriar ou remover container ou volume; nao editar `.env` neste passo.

## Gate de aceite

- Comando de criacao encerra com `COMMIT` e sem erros.
- Verificacao lista `rasping_migrator` e `rasping_runtime` com `can_login = f`, `inherits = f`, `is_superuser = f` e todas as flags elevadas `f`.
- Secao de memberships retorna zero linhas.
- Owner dos objetos permanece `rasping`: 29 tabelas e 26 sequences na evidencia corrente.
- Nenhuma role nova possui `CREATE` no schema `public` e nenhum privilegio DML efetivo sobre as tabelas.
- Git altera somente os tres scripts SQL e este contrato. Nao e necessario executar Maven novamente porque nenhum arquivo de codigo Java foi alterado.

## Rollback

Somente se esta for a unica etapa executada: aplicar `FASE_21_C2_A_DESFAZER_ROLES.sql` como `rasping` e verificar novamente. O script falha em vez de forcar remocao se as roles ganharam privilegios, LOGIN, memberships ou ownership. Se o rollback falhar, **nao usar CASCADE**.
