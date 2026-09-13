# FASE 2 — RESULTADO

**Projeto:** Rasping Amazon  
**Fase:** 2 — PostgreSQL + Migrations + Persistência inicial  
**Data de conclusão:** 13/09/2026  
**Status:** CONCLUÍDA

## 1. Objetivo

Estabelecer a fundação de persistência SQL do projeto com PostgreSQL, migrations versionadas e uma primeira integração Java com o banco.

A fase foi executada sem antecipar as responsabilidades das fases posteriores.

## 2. Resultado geral

A Fase 2 foi concluída com sucesso.

Foi validado o fluxo:

```text
Java
  │
  ├── Flyway
  │
  └── JDBC
       │
       ▼
PostgreSQL 18.6
       │
       ▼
rasping_amazon
```

O banco foi validado em desenvolvimento, a migration inicial foi aplicada a partir de um schema vazio, sua reexecução foi comprovada como idempotente e o Java conseguiu persistir e consultar um produto.

## 3. Ambiente PostgreSQL

Ambiente validado:

- PostgreSQL 18.6
- Banco: `rasping_amazon`
- Usuário: `rasping`
- Schema: `public`
- Porta: `5432`
- Container: `rasping-amazon-postgres`

A conexão foi validada diretamente com `pg_isready` e consultas SQL de identificação do banco, usuário e versão.

Antes da migration, o banco não possuía tabelas de aplicação.

## 4. Configuração de ambiente

Foi criado `.env.example` com:

```text
DB_HOST=localhost
DB_PORT=5432
DB_NAME=rasping_amazon
DB_USER=rasping
DB_PASSWORD=CHANGE_ME
```

A configuração local foi mantida em `.env` com as credenciais de desenvolvimento.

O `.gitignore` passou a conter:

```text
### Environment ###
.env
.env.*
!.env.example
```

A validação `git check-ignore -v .env` confirmou que `.env` está sendo ignorado pelo Git.

## 5. Dependências

Foram adicionadas:

```text
org.flywaydb:flyway-core:11.14.1
org.flywaydb:flyway-database-postgresql:11.14.1
org.postgresql:postgresql:42.7.8
```

A árvore de dependências foi verificada.

## 6. Estrutura de migrations

Foi criado:

```text
src/main/resources/db/migration/
```

com:

```text
V1__initial_schema.sql
```

## 7. Schema inicial

A V1 criou:

```text
product
offer_snapshot
deal_evaluation
publication
publication_attempt
configuration_version
```

Além de `flyway_schema_history`, criada pelo Flyway.

Total verificado no PostgreSQL: 7 tabelas.

## 8. Modelo `product`

```text
id          BIGINT identity / PK
asin        VARCHAR(10) NOT NULL
title       TEXT NOT NULL
image_url   TEXT
product_url TEXT NOT NULL
```

Foi criada a constraint `uq_product_asin`, garantindo unicidade do ASIN.

O identificador interno `id` foi separado do identificador externo Amazon `asin`.

## 9. Modelo `offer_snapshot`

Campos:

```text
id
product_id
collected_at
current_price
previous_price
discount_percentage
sold_percentage
rating
review_count
seller_name
delivery_provider
source
```

`product_id` possui FK para `product(id)`.

Foi criado índice em `collected_at`.

O modelo preserva snapshots históricos em vez de substituir uma coleta anterior.

## 10. Modelo `deal_evaluation`

Campos:

```text
id
offer_snapshot_id
eligible
rejection_reason
filter_version
score
momentum
evaluated_at
```

`offer_snapshot_id` possui FK para `offer_snapshot(id)`.

## 11. Modelo `publication`

Campos:

```text
id
deal_evaluation_id
template_version
generated_text
affiliate_url
status
created_at
```

`deal_evaluation_id` possui FK para `deal_evaluation(id)`.

## 12. Modelo `publication_attempt`

Campos:

```text
id
publication_id
channel
target
attempt_number
status
provider_reference
error_code
created_at
```

`publication_id` possui FK para `publication(id)`.

A estrutura permite registrar múltiplas tentativas e canais.

## 13. Modelo `configuration_version`

Campos:

```text
id
key
value
version
active
created_at
```

A estrutura permite manter versões de configuração.

## 14. Índices

Foram criados índices para:

```text
offer_snapshot.collected_at
deal_evaluation.score
publication.status
publication_attempt.status
```

O ASIN utiliza a estrutura de índice criada pela constraint `UNIQUE`.

Não foi criada uma coluna física `ranking`, pois a especificação não definiu uma coluna desse tipo. A estratégia definitiva de ranking permanece relacionada à formalização futura do scoring.

## 15. Flyway

Foi criada:

```text
src/main/java/com/raspingamazon/infrastructure/migration/DatabaseMigration.java
```

Responsabilidades:

- montar a URL JDBC;
- configurar o Flyway;
- executar as migrations.

A infraestrutura não conhece regras de negócio, collector, parser ou canais de publicação.

## 16. Aplicação da V1

A primeira execução foi realizada contra o PostgreSQL real.

O Flyway:

1. conectou ao PostgreSQL 18.6;
2. validou a migration;
3. criou `flyway_schema_history`;
4. aplicou `V1__initial_schema.sql`;
5. registrou o banco na versão `1`.

Registro verificado:

```text
version      = 1
description  = initial schema
type         = SQL
script       = V1__initial_schema.sql
success      = t
```

## 17. Idempotência

A migration foi executada novamente.

Resultado:

```text
Successfully validated 1 migration
Current version of schema "public": 1
Schema "public" is up to date. No migration necessary.
```

Isso comprovou que a segunda execução não recria nem duplica a V1.

## 18. Integridade do banco

### 18.1 ASIN duplicado

Foi tentada a inserção de dois produtos com o mesmo ASIN.

O PostgreSQL rejeitou corretamente:

```text
duplicate key value violates unique constraint "uq_product_asin"
```

Após rollback:

```text
SELECT COUNT(*) FROM product;
```

resultado:

```text
0
```

### 18.2 Foreign key

Foi tentada a inserção de `offer_snapshot` com `product_id` inexistente.

O PostgreSQL rejeitou corretamente:

```text
violates foreign key constraint "fk_offer_snapshot_product"
```

Após rollback:

```text
SELECT COUNT(*) FROM offer_snapshot;
```

resultado:

```text
0
```

## 19. Persistência Java

Foi criada a infraestrutura:

```text
src/main/java/com/raspingamazon/infrastructure/persistence/
```

com:

```text
DatabaseConnection.java
ProductRepository.java
```

`DatabaseConnection` abre conexões JDBC.

`ProductRepository` realiza, nesta etapa:

- inserção de produto;
- obtenção do ID gerado;
- consulta de existência por ASIN.

Foi utilizado `PreparedStatement`.

## 20. Teste de integração Java → PostgreSQL

Foi criado:

```text
src/test/java/com/raspingamazon/infrastructure/persistence/ProductRepositoryTest.java
```

O teste realizou:

```text
abrir conexão
    ↓
INSERT product
    ↓
receber ID
    ↓
consultar por ASIN
    ↓
validar existência
    ↓
remover registro de teste
```

Resultado:

```text
ProductRepositoryTest
Tests run: 1
Failures: 0
Errors: 0
Skipped: 0
```

Após o teste:

```text
SELECT COUNT(*) FROM product;
```

resultado: `0`.

A consulta de `offer_snapshot` também retornou `0`.

## 21. Testes finais

Suíte executada:

```text
FoundationTest
DatabaseMigrationTest
ProductRepositoryTest
```

Resultado:

```text
Tests run: 3
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

## 22. Decisões preservadas

- PostgreSQL como persistência SQL principal.
- SQL como persistência principal.
- Migrations versionadas.
- Flyway como ferramenta de migration.
- Separação entre infraestrutura e domínio.
- ASIN como identificador externo Amazon.
- Histórico por snapshots.
- Integridade referencial no banco.
- Nenhuma dependência de Excel para persistência.
- Nenhuma credencial armazenada no código-fonte.
- Nenhuma política arbitrária de retenção criada.

## 23. Pendências para fases posteriores

Permanecem pendentes:

- período exato de retenção/arquivamento;
- formalização completa das regras de domínio;
- catálogo definitivo de estados de publicação;
- filtros;
- scoring;
- momentum;
- collector;
- parser;
- validação de seller/fulfillment no domínio;
- scheduler;
- canais WhatsApp/Telegram;
- integração oficial Amazon/afiliados conforme aplicabilidade.

Essas pendências não foram artificialmente resolvidas na Fase 2.

## 24. Critérios de conclusão

| Critério | Resultado |
|---|---|
| Banco disponível em desenvolvimento | CONCLUÍDO |
| Migration executável a partir de banco vazio | CONCLUÍDO |
| Evolução versionada | CONCLUÍDO |
| Idempotência | CONCLUÍDO |
| Integridade referencial | CONCLUÍDO |
| Unicidade do ASIN | CONCLUÍDO |
| Persistência Java | CONCLUÍDO |
| Consulta Java | CONCLUÍDO |
| Testes sem resíduos | CONCLUÍDO |

## 25. Estado final

```text
FASE 2 — PostgreSQL + Migrations
STATUS: CONCLUÍDA
```

Próxima fase:

```text
FASE 3 — Domínio + Contratos
```

A Fase 3 deverá formalizar as entidades e contratos de domínio sem acoplar o domínio ao PostgreSQL, Flyway, HTML, Excel ou canais de publicação.
