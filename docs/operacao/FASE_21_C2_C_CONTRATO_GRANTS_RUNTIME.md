# FASE 21-C2-C — Concessões explícitas da role de runtime

**Estado:** concessões preliminares, sem ativação de LOGIN ou alteração de identidade utilizada pela aplicação.

## Evidência de entrada

- Commit C2-B `00f05d3`, PostgreSQL 18.6; inventário público de 29 tabelas e 26 sequences.
- Todas as relações e sequences pertencem a `rasping`; `flyway_schema_history` pertence ao mesmo proprietário.
- `rasping_runtime` e `rasping_migrator` são `NOLOGIN NOINHERIT`, sem memberships.
- A revisão das queries SQL dos repositórios e adapters JDBC do branch base identificou os caminhos de leitura e escrita.
- A aplicação ainda usa `DB_USER=rasping`, independentemente destas concessões.

## Privilégios permitidos

| Categoria | Contagem | Semântica |
| --- | ---: | --- |
| SELECT | 27 tabelas | Consultas operacionais e leitura para persistência |
| INSERT | 25 tabelas | Inclusão no pipeline, fila, avaliações e publicação |
| UPDATE | 12 tabelas | UPSERT, lease, status, reconciliação e configuração operacional |
| USAGE | 23 sequences | IDs operacionais via `nextval` |
| CONNECT | Banco `rasping_amazon` | Concessão explícita à role |
| USAGE | Schema `public` | Acesso a objetos SQL |

Não há `DELETE`, `TRUNCATE`, `CREATE`, `REFERENCES`, `TRIGGER`, `UPDATE` em sequences, ownership, membership, default ACL, senha ou `LOGIN` concedidos. `flyway_schema_history` e `configuration_version` não recebem DML do runtime. As sequences `configuration_version_id_seq`, `filter_profile_id_seq` e `score_profile_id_seq` não recebem USAGE.

### UPDATE — mapa específico

- `deal_candidate`
- `processing_job`
- `processing_run`
- `processing_schedule`
- `product`
- `publication`
- `publication_attempt`
- `publication_cadence_profile`
- `publication_outbox`
- `publication_quota_profile`
- `publication_rate_limit_state`
- `publication_selection_profile`

## Aplicação

Execute na raiz do repositório, PostgreSQL existente em execução, sob o usuário administrador atual `rasping`:

```powershell
Get-Content -Raw -Encoding UTF8 "infra/postgres/security/FASE_21_C2_C_CONCEDER_PRIVILEGIOS_RUNTIME.sql" | docker compose -f infra/compose.yml exec -T postgres psql -X -U rasping -d rasping_amazon -v ON_ERROR_STOP=1 -f -

Get-Content -Raw -Encoding UTF8 "infra/postgres/security/FASE_21_C2_C_VERIFICAR_PRIVILEGIOS_RUNTIME.sql" | docker compose -f infra/compose.yml exec -T postgres psql -X -U rasping -d rasping_amazon -v ON_ERROR_STOP=1 -f -
```

A concessão roda em transação e falha se o inventário, owner, roles ou memberships divergirem. A verificação roda em `READ ONLY` e confere a matriz positiva e a ausência de privilégios adicionais.

## Aceite

- Aplicação encerra com `COMMIT`, nenhuma exceção e mensagem C2-C.
- Verificação retorna 29 tabelas, 27 SELECT, 25 INSERT, 12 UPDATE, 0 DELETE e 0 TRUNCATE.
- Verificação retorna 26 sequences, 23 USAGE, 0 UPDATE.
- `configuration_version` e `flyway_schema_history` permanecem sem SELECT/INSERT/UPDATE/DELETE.
- Duas roles ainda sem LOGIN, schema sem CREATE para runtime, nenhuma concessão de migrator.
- Nenhum dado empresarial, segredo ou objeto de schema foi alterado.

## Rollback

Antes de qualquer etapa que ative login/membership ou altere ownership, executar **somente se necessário** `infra/postgres/security/FASE_21_C2_C_REVOGAR_PRIVILEGIOS_RUNTIME.sql`; depois executar novamente o inventário da C2-B (o script verificador C2-C exigirá grants presentes e, por isso, não serve para verificar o rollback). Não usar `CASCADE`.

## Limites

Não editar `.env`, `infra/compose.yml`, código Java, Flyway histórico ou usuários de aplicação neste passo. Não executar scripts de rollback sem uma decisão expressa. O acesso da aplicação continua como antes porque as duas roles técnicas permanecem `NOLOGIN` e sem membership.
