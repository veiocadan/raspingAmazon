# FASE 3 --- RESULTADO CONSOLIDADO v2

**Projeto:** Rasping Amazon
**Fase:** 3 --- Domínio + contratos internos
**Versão:** v2
**Data:** 15/09/2026
**Status:** CONCLUÍDA --- revisão estrutural e alinhamento com a semântica comercial da FASE 2 v2

------------------------------------------------------------------------

## 1. Objetivo

A FASE 3 tem como objetivo modelar os conceitos de negócio e estabelecer contratos internos antes das integrações externas.

A revisão v2 foi necessária porque a FASE 2 v2 consolidou uma semântica comercial mais precisa para preços e condições de pagamento. O domínio precisava representar essa semântica sem transformar persistência em regra de negócio e sem antecipar responsabilidades de coleta, filtros, score ou publicação.

------------------------------------------------------------------------

## 2. Princípios preservados

A revisão mantém:

1. domínio independente de infraestrutura;
2. snapshots para preservar histórico;
3. separação entre `currentPrice`, `basisPrice` e `previousPrice`;
4. condições de pagamento representadas de forma estruturada;
5. ausência de inferência de `pix_price` quando não houver evidência;
6. ausência de conversão automática de qualquer percentual em desconto principal;
7. separação entre coleta, parser, domínio e publicação;
8. política fail-closed para classificações desconhecidas;
9. versionamento de regras e templates;
10. ordem incremental das fases.

A FASE 2 v2 estabelece explicitamente que `basis_price` não deve ser transformado em `previous_price` e que persistência não deve carregar regras comerciais. Essa decisão permanece como referência para o domínio.

------------------------------------------------------------------------

## 3. Estrutura revisada

```text
domain/
├── commercial/
│   ├── PaymentCondition
│   ├── PaymentConditionType
│   └── PaymentMethod
├── deal/
│   └── OfferSnapshot
├── evaluation/
│   ├── DealEvaluation
│   └── RejectionReason
├── product/
│   ├── Asin
│   └── Product
├── publication/
│   ├── Publication
│   ├── PublicationStatus
│   └── contract/
│       └── PublicationRequest
├── shared/
│   ├── Money
│   └── Percentage
└── validation/
    ├── DeliveryType
    └── SellerType

application/
└── collection/
    └── contract/
        ├── CollectionRequest
        └── CollectionResult
```

Os contratos de coleta permanecem na camada de aplicação para que o conteúdo bruto não entre no domínio de negócio.

------------------------------------------------------------------------

## 4. `OfferSnapshot` revisado

`OfferSnapshot` continua representando uma ocorrência temporal da oferta e não interpreta HTML, JSON, HTTP ou PostgreSQL.

A estrutura revisada passa a ser:

```text
id
product
collectedAt
currentPrice
basisPrice
previousPrice
soldPercentage
rating
reviewCount
sellerName
deliveryProvider
sellerType
deliveryType
source
paymentConditions
```

`currentPrice` representa o principal preço comercial observado.

`basisPrice` representa o preço-base/lista observado quando disponível.

`previousPrice` representa um preço anterior somente quando houver evidência explícita dessa informação.

A revisão remove `discountPercentage` como atributo universal do snapshot. Desconto é dependente da condição comercial e passa a ser representado em `PaymentCondition` quando identificado.

`paymentConditions` mantém condições estruturadas de pagamento e permanece imutável após a construção do snapshot.

------------------------------------------------------------------------

## 5. `PaymentCondition`

A estrutura consolidada da FASE 2 v2 permanece no domínio comercial.

Tipos relevantes:

```text
CASH
CREDIT_INSTALLMENT
```

A condição `CASH` não aceita campos de parcelamento.

A condição `CREDIT_INSTALLMENT` exige quantidade positiva de parcelas, valor da parcela e total do parcelamento.

Os métodos de pagamento identificados são mantidos como uma lista estruturada e imutável.

------------------------------------------------------------------------

## 6. `DealEvaluation`

`DealEvaluation` permanece como registro estrutural do resultado de uma avaliação.

Campos:

```text
id
offerSnapshot
eligible
rejectionReason
filterVersion
score
momentum
evaluatedAt
```

As invariantes continuam sendo estruturais. Os motores de filtros, score e momentum não foram antecipados.

------------------------------------------------------------------------

## 7. `RejectionReason`

Catálogo controlado:

```text
SELLER_UNKNOWN
SELLER_THIRD_PARTY
DELIVERY_UNKNOWN
DELIVERY_THIRD_PARTY
INSUFFICIENT_DATA
```

A aplicação efetiva das regras permanece para as fases correspondentes.

------------------------------------------------------------------------

## 8. `Publication` e `PublicationRequest`

`Publication` permanece responsável apenas pelo estado estrutural de uma publicação gerada a partir de uma avaliação.

Estados:

```text
CREATED
READY
PUBLISHED
FAILED
```

Transições válidas:

```text
CREATED ──► READY
READY ──► PUBLISHED
READY ──► FAILED
FAILED ──► READY
```

`PublicationRequest` continua representando:

```text
dealEvaluation
templateVersion
```

O gerador efetivo pertence à FASE 14.

------------------------------------------------------------------------

## 9. Contratos de coleta

`CollectionRequest` recebe a origem da coleta como URI absoluta.

`CollectionResult` representa:

```text
content
collectedAt
source
```

O conteúdo bruto permanece fora do domínio de negócio.

------------------------------------------------------------------------

## 10. Separação entre coleta, parser, domínio e publicação

O fluxo permanece:

```text
Collector
    │
    ▼
CollectionResult
    │
    ▼
Parser
    │
    ▼
dados normalizados
    │
    ▼
Domain
    │
    ▼
DealEvaluation
    │
    ▼
Publication
```

O collector não decide se uma oferta é boa.

O parser não decide se uma oferta será publicada.

O domínio não interpreta HTML.

A publicação não conhece os canais de entrega.

------------------------------------------------------------------------

## 11. Relação com PostgreSQL

A FASE 3 não cria repositories adicionais para as novas entidades de domínio.

A relação conceitual permanece:

```text
Product
    │
    ▼
OfferSnapshot
    │
    ▼
DealEvaluation
    │
    ▼
Publication
```

A persistência já existente da FASE 2 v2 representa as condições comerciais em estruturas próprias.

------------------------------------------------------------------------

## 12. O que NÃO foi implementado nesta revisão

Para preservar a ordem das fases, não foram implementados:

- collector real;
- cliente HTTP da Amazon;
- parser HTML/JSON;
- extração e normalização completa de ASIN;
- enriquecimento por fonte oficial;
- validação efetiva de vendedor;
- validação efetiva de entrega;
- filtros;
- score;
- ranking;
- momentum;
- selector de condição comercial para publicação;
- repositories das novas entidades de domínio;
- `PublicationGenerator`;
- geração efetiva de link de associado;
- scheduler;
- filas;
- canais;
- observabilidade;
- resiliência;
- segurança operacional.

Esses itens pertencem às fases posteriores.

------------------------------------------------------------------------

## 13. Testes automatizados

A revisão preservou e adaptou a cobertura estrutural existente, incluindo os testes de domínio, contratos, persistência e migrations.

Validação final executada em 15/09/2026:

```text
Tests run: 103
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

O Flyway confirmou a integridade das migrations:

```text
Successfully validated 2 migrations
Current version of schema "public": 2
Schema "public" is up to date. No migration necessary.
```

------------------------------------------------------------------------

## 14. Critérios de conclusão

| Critério | Resultado |
|---|---|
| `OfferSnapshot` alinhado à semântica da FASE 2 v2 | CONCLUÍDO |
| `basisPrice` separado de `previousPrice` | CONCLUÍDO |
| desconto contextual removido do snapshot universal | CONCLUÍDO |
| `PaymentCondition` integrado ao snapshot | CONCLUÍDO |
| condições de pagamento preservadas de forma estruturada | CONCLUÍDO |
| `DealEvaluation` revisado sem antecipar scoring | CONCLUÍDO |
| `PublicationRequest` preservado como contrato | CONCLUÍDO |
| contratos de coleta preservados | CONCLUÍDO |
| domínio sem dependência de infraestrutura | CONCLUÍDO |
| PostgreSQL/Flyway preservados | CONCLUÍDO |
| `mvn clean test` | PASSOU |
| Testes | 103 |
| Falhas | 0 |
| Erros | 0 |

------------------------------------------------------------------------

## 15. Resultado final

**FASE 3 v2 — CONCLUÍDA.**

O domínio e os contratos internos foram revisados para permanecerem coerentes com a semântica comercial consolidada na FASE 2 v2, sem antecipar coleta, parser, filtros, score, histórico de momentum ou publicação efetiva.

O estado verificável ao final da revisão é:

```text
FASE 3 v2
STATUS: CONCLUÍDA

mvn clean test
Tests run: 103
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

------------------------------------------------------------------------

## 16. Próxima fase

A ordem oficial permanece:

```text
FASE 4 → Configuração e segredos
FASE 5 → Coleta
FASE 6 → Parser, ASIN e normalização
FASE 7 → Enriquecimento oficial
FASE 8 → Validação Amazon
FASE 9 → Filtros
FASE 10 → Score
FASE 11 → Histórico e momentum
FASE 12 → Orquestração
FASE 13 → Interface
FASE 14 → Publicação
```

A próxima etapa deve começar somente a partir deste estado validado.
