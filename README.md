# Rasping Amazon

Sistema em desenvolvimento para **coleta, seleção, avaliação, histórico e publicação de ofertas da Amazon Brasil**, com foco em separação de responsabilidades, rastreabilidade, idempotência e evolução escalável.

> **Estado atual: FASE 8 — Validação Amazon concluída; FASE 7 — Enriquecimento da página individual concluída; FASE 6 — Parser, ASIN e normalização concluída; FASE 5 — Coleta da página de promoções concluída; FASE 4 — Configuração e segredos concluída; FASE 3 v2 — Domínio + Contratos internos revisados e concluídos; FASE 2 v2 — evolução comercial da persistência concluída.**

## 1. Objetivo

O projeto não é apenas um raspador de ofertas. O objetivo é construir um sistema em que **coleta, normalização, validação, regras de negócio, persistência e publicação permaneçam desacopladas**.

Fluxo conceitual:

```text
coleta
  ↓
identificação / normalização
  ↓
validação
  ↓
filtros + avaliação
  ↓
PostgreSQL / histórico
  ↓
seleção
  ↓
geração de publicação
  ↓
canais
```

A ordem das fases deve ser preservada; responsabilidades futuras não devem ser antecipadas sem decisão explícita.

## 2. Estado atual

| Fase | Descrição | Status |
|---|---|---|
| FASE 0 | Levantamento da fonte e regras | CONCLUÍDA |
| FASE 1 | Fundação Java | CONCLUÍDA |
| FASE 2 | PostgreSQL, schema e migrations | CONCLUÍDA |
| FASE 2 v2 | Evolução comercial da persistência | CONCLUÍDA |
| FASE 3 | Domínio e contratos internos | CONCLUÍDA |
| FASE 3 v2 | Revisão comercial e estrutural | CONCLUÍDA |
| FASE 4 | Configuração e segredos | CONCLUÍDA |
| FASE 5 | Coleta da página de promoções | CONCLUÍDA |
| FASE 6 | Parser, ASIN e normalização | CONCLUÍDA |
| FASE 7 | Enriquecimento da página individual | CONCLUÍDA |
| FASE 8 | Validação Amazon | CONCLUÍDA |

**Próxima etapa: FASE 9 — Motor de filtros configuráveis.**

## 3. Arquitetura

```text
src/
├── main/
│   ├── java/
│   │   └── com/raspingamazon/
│   │       ├── application/
│   │       ├── domain/
│   │       ├── infrastructure/
│   │       └── presentation/
│   └── resources/
│       └── db/
│           └── migration/
└── test/
    ├── java/
    │   └── com/raspingamazon/
    └── resources/
        └── amazon/
```

Responsabilidades principais:

- `domain`: conceitos e invariantes de negócio, sem dependência de infraestrutura;
- `application`: contratos e coordenação do fluxo;
- `infrastructure`: PostgreSQL, Flyway, JDBC, configuração e adaptadores tecnológicos;
- `presentation`: interfaces de entrada e exposição operacional futura.

O domínio não conhece HTML, JSON externo, HTTP, PostgreSQL, Flyway, JDBC, Excel ou canais de publicação.

## 4. Stack

- Java 25
- Maven
- JUnit 5
- PostgreSQL 18.6
- Flyway 11.14.1
- PostgreSQL JDBC 42.7.8
- Jackson Databind 2.20.0
- Docker / Docker Compose

## 5. Configuração e segredos

A configuração da aplicação é centralizada em:

```text
EnvironmentConfigProvider
        ↓
ApplicationConfig
```

Variáveis de configuração:

```text
APP_ENV
DB_HOST
DB_PORT
DB_NAME
DB_USER
```

Segredo:

```text
DB_PASSWORD
```

`DB_PASSWORD` é obrigatório e não possui valor padrão no código.

A leitura de variáveis de ambiente fica centralizada no `EnvironmentConfigProvider`. Os componentes de infraestrutura recebem `ApplicationConfig` em vez de ler o ambiente diretamente.

O `.env` local não é versionado. O `.env.example` documenta as variáveis necessárias sem conter segredo real.

No Docker Compose, a senha do PostgreSQL é recebida por variável de ambiente:

```yaml
POSTGRES_PASSWORD: ${DB_PASSWORD}
```

## 6. Domínio atual

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
```

### OfferSnapshot

Representa uma ocorrência temporal de uma oferta e preserva histórico.

Campos principais:

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

`basisPrice` continua semanticamente distinto de `previousPrice`.

`paymentConditions` representa as condições comerciais estruturadas. Desconto contextual não é tratado como atributo universal do snapshot.

### DealEvaluation

Registra o resultado estrutural de uma avaliação:

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

A FASE 8 implementou a validação Amazon e a persistência da avaliação. Score e momentum permanecem para fases posteriores.

## 7. Validação Amazon

A FASE 8 implementou a política fail closed para vendedor e entrega.

Regra:

```text
seller == Amazon
AND
deliveryProvider == Amazon
    ↓
eligible = true

qualquer outra combinação
    ↓
eligible = false
```

Componentes principais:

```text
AmazonEligibilityResult
AmazonEligibilityValidator
AmazonDealEvaluationApplicationService
DealEvaluationRepository
DealEvaluationJdbcRepository
```

A combinação:

```text
AMAZON + AMAZON
```

é aprovada.

As demais combinações são rejeitadas com motivo controlado:

```text
THIRD_PARTY → SELLER_THIRD_PARTY
UNKNOWN → SELLER_UNKNOWN
AMAZON + THIRD_PARTY → DELIVERY_THIRD_PARTY
AMAZON + UNKNOWN → DELIVERY_UNKNOWN
```

A validação do vendedor ocorre antes da entrega.

A versão atual da regra é:

```text
AMAZON_SELLER_DELIVERY_V1
```

A persistência utiliza a tabela existente:

```text
deal_evaluation
```

Nenhuma migration nova foi necessária na FASE 8.

## 8. Coleta da Amazon

A FASE 5 implementou a coleta da página funcional de promoções:

```text
https://www.amazon.com.br/deals
```

A arquitetura da coleta é:

```text
AmazonDealsCollector
        ↓
HttpCollectionCollector
        ↓
JavaHttpTransport
        ↓
HTTP
        ↓
CollectionResult
```

O conteúdo coletado permanece bruto nesta etapa.

## 9. Parser, ASIN e normalização

A FASE 6 implementou a interpretação do conteúdo bruto coletado pela FASE 5.

Fluxo:

```text
CollectionResult
      ↓
DealsParser
      ↓
ParsedDeal
```

O parser localiza `productSearchResponse` dentro do documento e extrai a estrutura correspondente antes de interpretar os dados com Jackson.

O ASIN é normalizado e validado como:

```text
[A-Z0-9]{10}
```

Links relativos são normalizados utilizando a origem da coleta.

O parser preserva a distinção entre:

```text
currentPrice
basisPrice
previousPrice
```

Nenhum `pixPrice` é inferido.

Quando disponível:

```text
dealDetails.percentClaimed
```

é tratado como:

```text
soldPercentage
```

Registros sem dados mínimos confiáveis são descartados.

A deduplicação considera:

```text
ASIN
+
URL
+
currentPrice
+
basisPrice
```

## 10. Enriquecimento da página individual

A FASE 7 implementou o enriquecimento da oferta normalizada utilizando a URL do produto.

Fluxo:

```text
ParsedDeal
    ↓
ProductEnrichmentClient
    ↓
AmazonProductPageEnrichmentClient
    ↓
AmazonProductPageParser
    ↓
ProductEnrichmentResult
```

Seller e delivery são extraídos de forma independente a partir das evidências da oferta principal.

A implementação utiliza a página individual do produto como fonte de enriquecimento. Isso não significa que uma API oficial da Amazon já esteja integrada.

Fixtures utilizadas:

```text
amazon-amazonglobal.html
misto.html
totalamazon.html
totalterceiro.html
```

## 11. Persistência

PostgreSQL é a persistência SQL principal. O schema evolui por migrations versionadas e a V1 não é editada retroativamente.

Estrutura comercial relevante:

```text
product
  ↓
offer_snapshot
  ↓
offer_payment_condition
  ↓
offer_payment_condition_method
```

A persistência de condições comerciais permanece separada das regras de seleção.

A FASE 8 utiliza a estrutura existente:

```text
deal_evaluation
```

para registrar:

```text
offer_snapshot_id
eligible
rejection_reason
filter_version
score
momentum
evaluated_at
```

## 12. Testes

A validação mais recente da suíte foi executada em 18/09/2026:

```text
Tests run: 190
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

O Flyway confirmou:

```text
Successfully validated 2 migrations
Current version of schema "public": 2
Schema "public" is up to date. No migration necessary.
```

Os testes abrangem domínio, contratos, persistência, transporte HTTP, coleta, parser, enriquecimento e validação Amazon.

## 13. O que ainda não foi implementado

Para preservar a separação entre fases, permanecem para etapas posteriores:

- filtros configuráveis;
- score;
- ranking;
- momentum;
- histórico;
- orquestração;
- processamento assíncrono;
- interface operacional;
- `PublicationGenerator`;
- geração efetiva de link de associado;
- scheduler;
- filas;
- WhatsApp/Telegram;
- observabilidade;
- resiliência;
- segurança e governança operacional;
- Excel/CSV opcional;
- mecanismos específicos de escalabilidade e evolução.

## 14. Fonte Amazon

A investigação identificou a página funcional de promoções da Amazon Brasil:

```text
https://www.amazon.com.br/deals
```

A coleta real da FASE 5 foi implementada e validada.

A implementação utiliza:

```text
User-Agent: RaspingAmazon/1.0
```

Também foi observado tecnicamente um endpoint interno JSON. Ele não deve ser tratado automaticamente como interface autorizada de produção. A implementação futura deve priorizar interfaces oficiais aplicáveis e preservar a separação entre coleta e domínio.

## 15. Roadmap

```text
FASE 4  → Configuração e segredos              [CONCLUÍDA]
FASE 5  → Coleta                               [CONCLUÍDA]
FASE 6  → Parser, ASIN e normalização          [CONCLUÍDA]
FASE 7  → Enriquecimento da página individual  [CONCLUÍDA]
FASE 8  → Validação Amazon                     [CONCLUÍDA]
FASE 9  → Filtros configuráveis                [PRÓXIMA]
FASE 10 → Score
FASE 11 → Histórico e momentum
FASE 12 → Orquestração
FASE 13 → Interface
FASE 14 → Publicação
FASE 15+ → testes integrados, observabilidade,
           agendamento, canais, resiliência,
           segurança, Excel/CSV e escalabilidade
```

## 16. Documentação de fases

```text
docs/phases/
├── FASE_0_RESULTADO.md
├── FASE_0_RESULTADO_v2.md
├── FASE_0_v2_PRECOS_PARCELAMENTO_PIX.md
├── FASE_1_RESULTADO.md
├── FASE_2_RESULTADO.md
├── FASE_2_RESULTADO_v2.md
├── FASE_3_RESULTADO.md
├── FASE_3_RESULTADO_v2.md
├── FASE_4_RESULTADO.md
├── FASE_5_RESULTADO.md
├── FASE_6_RESULTADO.md
├── FASE_7_RESULTADO.md
└── FASE_8_RESULTADO.md
```

A documentação de cada fase deve registrar o estado verificável antes da passagem para a seguinte.

## 17. Estado atual

```text
FASE 8 — Validação Amazon
STATUS: CONCLUÍDA

Política fail closed:
OK

Seller:
OK

Delivery:
OK

Seller / Delivery independentes:
OK

Razões de rejeição:
OK

DealEvaluation:
OK

Persistência JDBC:
OK

Testes:
190

Falhas:
0

Erros:
0

Ignorados:
0

Build:
SUCCESS

PostgreSQL:
18.6

Flyway:
OK

Schema:
versão 2

Próxima etapa:
FASE 9 — Motor de filtros configuráveis
```
