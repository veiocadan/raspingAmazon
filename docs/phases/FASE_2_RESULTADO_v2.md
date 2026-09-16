# FASE 2 --- RESULTADO CONSOLIDADO v2

**Projeto:** Rasping Amazon  
**Fase:** 2 --- PostgreSQL, schema, migrations e persistência comercial  
**Versão:** v2  
**Data:** 15/09/2026  
**Status:** CONCLUÍDA --- resultado consolidado da evolução comercial da persistência

---

## 1. Objetivo

A FASE 2 teve como objetivo estabelecer a persistência SQL do projeto utilizando PostgreSQL, migrations versionadas e JDBC.

A revisitação para a FASE 2 v2 foi necessária porque a FASE 0 v2 fechou a semântica comercial de preços e condições de pagamento que não estava suficientemente representada no schema e na persistência existentes.

A FASE 2 v2 foi concluída após a implementação e validação de:

- `basis_price`;
- condições de pagamento;
- métodos de pagamento;
- condição à vista;
- condição de parcelamento no cartão de crédito;
- quantidade de parcelas;
- valor da parcela;
- total parcelado;
- juros;
- persistência de `OfferSnapshot` com preço-base;
- repositories JDBC correspondentes;
- testes de domínio e persistência.

---

## 2. Princípios preservados

A FASE 2 v2 permanece subordinada aos princípios arquiteturais do projeto:

1. PostgreSQL como persistência SQL principal;
2. Flyway como mecanismo de versionamento;
3. JDBC como mecanismo de persistência;
4. domínio independente da infraestrutura;
5. não utilizar ORM como atalho arquitetural;
6. não editar migrations já aplicadas;
7. evoluir o schema por nova migration;
8. preservar histórico por snapshots;
9. não colocar regras comerciais nos repositories;
10. não inventar dados ausentes;
11. manter rastreabilidade suficiente para decisões posteriores;
12. preservar a ordem das fases.

---

## 3. Estado inicial da FASE 2

A FASE 2 original já possuía a migration:

```text
V1__initial_schema.sql
```

com as estruturas principais:

```text
product
offer_snapshot
deal_evaluation
publication
publication_attempt
configuration_version
```

A V1 permanece preservada.

A evolução comercial foi realizada exclusivamente por uma nova migration:

```text
V2__commercial_offer_conditions.sql
```

---

## 4. Migration V2

Foi criada:

```text
src/main/resources/db/migration/V2__commercial_offer_conditions.sql
```

A migration adiciona:

```text
offer_snapshot.basis_price
```

e cria:

```text
offer_payment_condition
offer_payment_condition_method
```

A V2 foi aplicada pelo Flyway com sucesso.

---

## 5. `basis_price`

A FASE 0 v2 estabeleceu que o preço de lista/base deve permanecer semanticamente separado de `previous_price`.

A FASE 2 v2 implementou essa decisão com:

```text
offer_snapshot.basis_price
```

O campo é opcional porque a fonte pode não fornecer preço-base.

A persistência não transforma:

```text
basis_price
```

em:

```text
previous_price
```

---

## 6. Condições de pagamento

Foi criada a tabela:

```text
offer_payment_condition
```

Ela representa uma condição comercial associada a um `OfferSnapshot`.

Campos persistidos:

```text
condition_type
price
discount_percentage
installment_count
installment_amount
installment_total
interest
```

A estrutura permite representar condições diferentes sem transformar cada modalidade de pagamento em uma coluna independente do snapshot.

---

## 7. Métodos de pagamento

Foi criada:

```text
offer_payment_condition_method
```

A relação é:

```text
offer_snapshot
      |
      v
offer_payment_condition
      |
      v
offer_payment_condition_method
```

Uma condição pode possuir mais de um método de pagamento.

Isso permite representar, por exemplo:

```text
CASH
├── PIX
└── NUPAY_ADDITIONAL_LIMIT
```

sem perder a associação entre o desconto e a condição comercial.

---

## 8. Modelo de domínio comercial

Foi criado o pacote:

```text
com.raspingamazon.domain.commercial
```

com:

```text
PaymentConditionType
PaymentMethod
PaymentCondition
```

### `PaymentConditionType`

Valores implementados:

```text
CASH
CREDIT_INSTALLMENT
```

### `PaymentMethod`

Valores implementados:

```text
PIX
NUPAY_ADDITIONAL_LIMIT
CREDIT_CARD
```

### `PaymentCondition`

Representa:

```text
type
price
discountPercentage
installmentCount
installmentAmount
installmentTotal
interest
paymentMethods
```

---

## 9. Invariantes de `PaymentCondition`

A estrutura de domínio implementa as invariantes próprias da condição.

Para:

```text
CASH
```

os campos de parcelamento não devem ser preenchidos.

Para:

```text
CREDIT_INSTALLMENT
```

devem existir valores positivos para:

```text
installmentCount
installmentAmount
installmentTotal
```

Os métodos de pagamento são mantidos de forma imutável para evitar alteração externa da coleção interna.

---

## 10. Regra de parcelamento

A FASE 0 v2 definiu a regra:

```text
Cartão de Crédito
→ somente parcelas sem juros
→ maior número de parcelas
→ utilizar essa condição para publicação
```

A FASE 2 v2 **não implementa essa seleção**.

O repository persiste as condições recebidas.

A seleção permanece como responsabilidade da camada de negócio correspondente.

Portanto:

```text
repository
≠
regra de seleção comercial
```

---

## 11. Evolução de `OfferSnapshot`

`OfferSnapshot` recebeu:

```text
basisPrice
```

mantendo separados:

```text
currentPrice
basisPrice
previousPrice
```

O significado estabelecido na FASE 0 v2 é preservado:

```text
currentPrice
→ preço principal/customer-visible da oferta

basisPrice
→ preço de lista/base, quando evidenciado

previousPrice
→ preço anterior/histórico, somente quando houver evidência adequada
```

---

## 12. Persistência de `OfferSnapshot`

Foi criado:

```text
OfferSnapshotRepository
```

A implementação utiliza JDBC.

O repository persiste os campos existentes da V1 e o novo:

```text
basis_price
```

Não foi introduzido:

```text
JPA
Hibernate
```

nem outro ORM.

---

## 13. Persistência das condições comerciais

Foi criado:

```text
OfferPaymentConditionRepository
```

O fluxo de persistência é:

```text
PaymentCondition
        |
        v
offer_payment_condition
        |
        v
offer_payment_condition_method
```

O repository não calcula, interpreta ou escolhe a condição comercial.

Sua responsabilidade é persistir o modelo recebido.

---

## 14. Exemplo comercial persistido

A oferta investigada na FASE 0 v2 apresentou:

```text
basis_price = 299.00
customer_visible_price = 161.40

CASH
discount_percentage = 15
payment_methods =
    PIX
    NUPAY_ADDITIONAL_LIMIT

CREDIT_INSTALLMENT
installment_count = 6
installment_amount = 31.65
installment_total = 189.90
interest = 0
payment_method = CREDIT_CARD
```

A FASE 2 v2 passou a possuir estrutura suficiente para persistir esses conceitos sem transformar suas relações em inferências.

---

## 15. Pix

A FASE 0 v2 não confirmou um valor numérico específico de Pix na aba Pix.

Por isso, a FASE 2 v2 não criou:

```text
pix_price
```

como campo obrigatório ou derivado.

A condição:

```text
PIX
```

pode ser associada à condição à vista quando explicitamente identificada.

Isso não significa que o sistema deva inferir:

```text
pix_price = customer_visible_price
```

sem evidência.

---

## 16. Desconto

A FASE 0 v2 estabeleceu que o percentual de desconto deve permanecer contextualizado.

A FASE 2 v2 permite persistir:

```text
discount_percentage
```

dentro de:

```text
offer_payment_condition
```

em vez de tratar o percentual como uma propriedade isolada e universal da oferta.

Isso permite manter a relação:

```text
15%
+
à vista
+
Pix / NuPay Limite Adicional
```

---

## 17. Promoções concorrentes

A segunda promoção de 15% identificada na FASE 0 v2:

```text
Prime
+
primeira compra
+
cartão Amazon
+
cupom
```

não foi incorporada como desconto principal da oferta.

A FASE 2 v2 não cria mecanismo para tratar qualquer ocorrência de `15%` como desconto comercial principal.

A identificação e classificação dessas promoções permanece responsabilidade das etapas de coleta, parsing e normalização.

---

## 18. Repositories e separação de responsabilidades

A estrutura de persistência permanece separada do domínio.

```text
domain
   |
   v
objetos comerciais
   |
   v
infrastructure
   |
   v
JDBC / PostgreSQL
```

O domínio não conhece:

```text
SQL
PostgreSQL
Flyway
JDBC
```

Os repositories não conhecem:

```text
HTML
DOM
Amazon
regras de seleção
```

---

## 19. Testes de domínio

Foram adicionados testes para os novos conceitos comerciais.

Cobertura criada:

```text
PaymentCondition
PaymentConditionType
PaymentMethod
```

As invariantes estruturais de `PaymentCondition` foram verificadas.

---

## 20. Testes de persistência

Foram adicionados testes para:

```text
OfferSnapshotRepository
OfferPaymentConditionRepository
```

### `OfferSnapshotRepositoryTest`

A persistência foi validada com:

```text
current_price   = 161.40
basis_price     = 299.00
previous_price  = 199.90
discount        = 15
sold_percentage = 30
rating          = 4.7
review_count    = 1234
```

além dos dados de seller, delivery e source.

### `OfferPaymentConditionRepositoryTest`

Foram validadas as condições:

```text
CASH
```

com:

```text
15%
PIX
NUPAY_ADDITIONAL_LIMIT
```

e:

```text
CREDIT_INSTALLMENT
```

com:

```text
6
31.65
189.90
0
CREDIT_CARD
```

---

## 21. Evolução dos testes existentes

Os testes existentes que constroem `OfferSnapshot` foram ajustados para o novo campo:

```text
basisPrice
```

Também foram adicionados testes para os invariantes de `DealEvaluation`:

```text
eligible = true
→ rejectionReason = null
```

e:

```text
eligible = false
→ rejectionReason != null
```

As alterações mantêm as responsabilidades existentes e adicionam cobertura para os novos invariantes.

---

## 22. Flyway

A validação do Flyway confirmou:

```text
Successfully validated 2 migrations
```

e:

```text
Current version of schema "public": 2
Schema "public" is up to date.
No migration necessary.
```

Estado final:

```text
V1 → aplicada
V2 → aplicada
schema → versão 2
```

A V1 não foi alterada.

A V2, uma vez aplicada, não deve ser editada retroativamente.

Novas mudanças estruturais devem utilizar novas migrations.

---

## 23. PostgreSQL

O ambiente utilizado para validação possui:

```text
Banco: rasping_amazon
Usuário: rasping
Schema: public
Porta: 5432
PostgreSQL: 18.6
Container: rasping-amazon-postgres
```

A infraestrutura local permanece baseada em Docker Compose.

---

## 24. Validação do build

Foi executado:

```powershell
mvn clean test
```

Resultado:

```text
Tests run: 103
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

Também foi validado:

```powershell
git diff --check
```

sem problemas reportados.

---

## 25. O que NÃO foi implementado nesta fase

Para preservar a separação de responsabilidades, a FASE 2 v2 não implementou:

1. collector da Amazon;
2. parser HTML/JSON;
3. extração de ASIN;
4. normalização completa da oferta;
5. cliente HTTP da Amazon;
6. validação efetiva de seller;
7. validação efetiva de delivery;
8. filtros;
9. score;
10. momentum;
11. seleção final do parcelamento para publicação;
12. geração de publicação;
13. publicação em canais;
14. scheduler;
15. processamento assíncrono;
16. observabilidade completa;
17. mecanismos de escalabilidade.

Essas responsabilidades permanecem para as fases correspondentes.

---

## 26. Relação com a FASE 0 v2

A FASE 0 v2 estabeleceu que:

```text
basis_price
customer_visible_price
payment_condition
cash_payment_price
discount_percentage
discount_condition
payment_methods
credit_installment_count
credit_installment_amount
credit_installment_total
interest
```

são conceitos comerciais relevantes.

A FASE 2 v2 implementou a parte de persistência necessária para representar esses conceitos, sem afirmar que cada conceito precise corresponder literalmente a uma coluna.

A decisão estrutural foi:

```text
OfferSnapshot
        |
        +── basis_price
        |
        +── PaymentCondition
                 |
                 +── métodos de pagamento
                 +── desconto
                 +── parcelamento
                 +── juros
```

---

## 27. Relação com a FASE 3

A FASE 3 permanece conceitualmente concluída.

A FASE 2 v2 não substitui o domínio da FASE 3.

A alteração de `OfferSnapshot` foi realizada porque a semântica comercial consolidada exige representar `basisPrice`.

A FASE 2 v2 não implementa:

```text
filtros
score
momentum
```

nem transforma persistência em mecanismo de decisão comercial.

---

## 28. Política de não inferência

A persistência deve preservar a ausência quando a fonte não fornecer evidência.

Portanto:

```text
basis_price ausente
→ NULL

pix_price não confirmado
→ não criar valor

discount ausente
→ não inventar

installment ausente
→ não inventar

previous_price sem evidência histórica
→ não preencher com basis_price
```

Essa política mantém a semântica definida na FASE 0 v2.

---

## 29. Política de evolução do schema

A regra consolidada é:

```text
V1
 ↓
V2
 ↓
V3
 ↓
...
```

e não:

```text
editar V1
```

Uma evolução futura deverá criar uma nova migration quando houver alteração estrutural.

Isso preserva:

- histórico;
- reprodutibilidade;
- auditabilidade;
- capacidade de reproduzir o estado do banco;
- compatibilidade entre ambientes.

---

## 30. Estrutura resultante

A estrutura relevante após a FASE 2 v2 é:

```text
src/
├── main/
│   ├── java/
│   │   └── com/raspingamazon/
│   │       ├── domain/
│   │       │   └── commercial/
│   │       │       ├── PaymentCondition.java
│   │       │       ├── PaymentConditionType.java
│   │       │       └── PaymentMethod.java
│   │       │
│   │       └── infrastructure/
│   │           └── persistence/
│   │               ├── OfferPaymentConditionRepository.java
│   │               └── OfferSnapshotRepository.java
│   │
│   └── resources/
│       └── db/
│           └── migration/
│               ├── V1__initial_schema.sql
│               └── V2__commercial_offer_conditions.sql
│
└── test/
    └── java/
        └── com/raspingamazon/
            ├── domain/
            │   └── commercial/
            └── infrastructure/
                └── persistence/
```

---

## 31. Critérios de conclusão

| Critério | Resultado |
|---|---|
| PostgreSQL funcional | CONCLUÍDO |
| Flyway funcional | CONCLUÍDO |
| V1 preservada | CONCLUÍDO |
| V2 criada | CONCLUÍDO |
| V2 aplicada | CONCLUÍDO |
| `basis_price` persistido | CONCLUÍDO |
| Condições comerciais modeladas | CONCLUÍDO |
| Métodos de pagamento modelados | CONCLUÍDO |
| `OfferSnapshot` evoluído | CONCLUÍDO |
| `OfferSnapshotRepository` | CONCLUÍDO |
| `OfferPaymentConditionRepository` | CONCLUÍDO |
| Testes de domínio | CONCLUÍDO |
| Testes de persistência | CONCLUÍDO |
| Regras de não inferência preservadas | CONCLUÍDO |
| Separação domínio/infraestrutura | CONCLUÍDO |
| Flyway validou 2 migrations | PASSOU |
| `mvn clean test` | PASSOU |
| Testes executados | 103 |
| Falhas | 0 |
| Erros | 0 |
| `git diff --check` | PASSOU |

---

## 32. Resultado final

**FASE 2 v2 — CONCLUÍDA.**

A persistência do Rasping Amazon foi evoluída para representar os conceitos comerciais definidos na FASE 0 v2.

O projeto passou a distinguir:

```text
current_price
basis_price
previous_price
```

e passou a possuir uma estrutura específica para:

```text
condição à vista
condição de parcelamento
métodos de pagamento
desconto contextual
quantidade de parcelas
valor da parcela
total parcelado
juros
```

A evolução foi feita por migration versionada:

```text
V2__commercial_offer_conditions.sql
```

sem alteração retroativa da V1.

A validação final confirmou:

```text
103 testes
0 falhas
0 erros
BUILD SUCCESS
```

A FASE 2 v2 está encerrada.

---

## 33. Regra principal da FASE 2 v2

> **Persistir a semântica comercial sem transformar persistência em regra de negócio.**

> **Não editar migrations aplicadas.**

> **Não transformar `basis_price` em `previous_price`.**

> **Não inventar `pix_price`.**

> **Não transformar qualquer percentual em desconto principal.**

> **Persistir condições de pagamento de forma estruturada.**

> **Manter domínio e infraestrutura separados.**

> **Preservar histórico e rastreabilidade.**

---

## 34. Próxima fase

A ordem oficial permanece:

```text
FASE 0
   ↓
FASE 1 — Fundação Java
   ↓
FASE 2 — PostgreSQL + migrations
   ↓
FASE 2 v2 — Evolução comercial da persistência
   ↓
FASE 3 — Domínio + contratos
   ↓
FASE 4 — Configuração e segredos
   ↓
FASE 5 — Coleta
   ↓
FASE 6 — Parser, ASIN e normalização
   ↓
FASE 7 — Enriquecimento oficial
   ↓
FASE 8 — Validação Amazon
   ↓
FASE 9 — Filtros
   ↓
FASE 10 — Score
   ↓
FASE 11 — Histórico e momentum
   ↓
FASE 12 — Orquestração
   ↓
FASE 13 — Interface
   ↓
FASE 14 — Publicação
```

A próxima etapa deve respeitar essa sequência e não antecipar responsabilidades de fases posteriores.

---

## 35. Registro de encerramento

```text
FASE 2 — PostgreSQL, schema, migrations e persistência
VERSÃO: v2
STATUS: CONCLUÍDA

Migration V2:
OK

PostgreSQL:
OK

Flyway:
OK

Schema:
versão 2

basis_price:
OK

Condições comerciais:
OK

Métodos de pagamento:
OK

Parcelamento:
OK

Persistência JDBC:
OK

Testes:
103

Falhas:
0

Erros:
0

Build:
OK

git diff --check:
OK

Próxima etapa:
seguir para a FASE 3, preservando a separação entre
domínio, persistência, coleta e publicação.
```

---

## 36. Referências internas do projeto

- `FASE_0_RESULTADO.md` — levantamento original da fonte e regras.
- `FASE_0_RESULTADO_v2.md` — resultado consolidado da investigação comercial.
- `FASE_0_v2_PRECOS_PARCELAMENTO_PIX.md` — investigação complementar de preços, Pix e parcelamento.
- `FASE_1_RESULTADO.md` — fundação Java.
- `FASE_2_RESULTADO.md` — PostgreSQL, Flyway e persistência original.
- `FASE_3_RESULTADO.md` — domínio e contratos internos.
- `rasping_Amazon_projeto_revisado.pdf` — documento arquitetural principal.

O resultado desta FASE 2 v2 deve ser tratado como referência do estado de persistência e como base para as evoluções posteriores do domínio, parser, normalização e publicação.
