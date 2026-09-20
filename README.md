# Rasping Amazon

Sistema em desenvolvimento para **coleta, normalização, enriquecimento, validação, filtragem, avaliação, score, ranking, histórico, seleção e publicação de ofertas da Amazon Brasil**, com foco em separação de responsabilidades, rastreabilidade, idempotência, auditabilidade e evolução escalável.

> **Estado atual: FASE 10 concluída. O sistema possui elegibilidade estrutural Amazon, condições comerciais no fluxo vertical, filtros comerciais versionados, score versionado e reproduzível, fatores explicáveis persistidos e ranking determinístico. A validação local e o CI remoto da `main` estão verdes. A próxima fase é a FASE 11 — Histórico e momentum.**

## 1. Objetivo

O projeto não é apenas um raspador de ofertas.

O objetivo é construir um sistema em que **coleta, normalização, enriquecimento, validação, filtros comerciais, score, ranking, persistência e publicação permaneçam desacoplados**.

Fluxo conceitual:

```text
coleta
  ↓
identificação / normalização
  ↓
enriquecimento
  ↓
validação estrutural Amazon
  ↓
filtros comerciais configuráveis
  ↓
score versionado
  ↓
ranking determinístico
  ↓
PostgreSQL / histórico
  ↓
geração de publicação
  ↓
canais
```

A ordem das fases deve ser preservada.

Responsabilidades futuras não devem ser antecipadas sem decisão explícita.

---

## 2. Estado atual

| Fase      | Descrição                                                | Status    |
|-----------|----------------------------------------------------------|-----------|
| FASE 0    | Levantamento da fonte e regras                           | CONCLUÍDA |
| FASE 0 v2 | Semântica comercial de preços e pagamento                | CONCLUÍDA |
| FASE 1    | Fundação Java                                            | CONCLUÍDA |
| FASE 2    | PostgreSQL, schema e migrations                          | CONCLUÍDA |
| FASE 2 v2 | Evolução comercial da persistência                       | CONCLUÍDA |
| FASE 3    | Domínio e contratos internos                             | CONCLUÍDA |
| FASE 3 v2 | Revisão comercial e estrutural                           | CONCLUÍDA |
| FASE 4    | Configuração e segredos                                  | CONCLUÍDA |
| FASE 5    | Coleta da página de promoções                            | CONCLUÍDA |
| FASE 6    | Parser, ASIN e normalização                              | CONCLUÍDA |
| FASE 7    | Enriquecimento da página individual                      | CONCLUÍDA |
| FASE 8    | Validação estrutural Amazon                              | CONCLUÍDA |
| FASE 8.5  | Consolidação do núcleo e preparação dos dados de decisão | CONCLUÍDA |
| FASE 9    | Motor de filtros comerciais configuráveis                | CONCLUÍDA |
| FASE 10   | Score, ranking e explicabilidade                         | CONCLUÍDA |
| FASE 11   | Histórico e momentum                                     | PRÓXIMA   |

---

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
            └── fixtures/
                ├── deals/
                └── product/
```

Responsabilidades principais:

- `domain`: conceitos, regras, normalização, score, ranking e invariantes de negócio, sem dependência de infraestrutura;
- `application`: contratos e coordenação dos casos de uso;
- `infrastructure`: PostgreSQL, Flyway, JDBC, configuração, HTTP, parsing específico da Amazon e adapters tecnológicos;
- `presentation`: interfaces de entrada e exposição operacional futura.

O domínio não conhece HTML, HTTP, PostgreSQL, Flyway ou JDBC.

O projeto permanece em um único módulo Maven enquanto não houver pressão arquitetural real para decomposição.

---

## 4. Stack

- Java 25
- Maven Wrapper 3.3.4
- Maven 3.9.16
- JUnit 5
- PostgreSQL 18.6
- Flyway 11.14.1
- PostgreSQL JDBC 42.7.8
- Jackson Databind 2.20.0
- Docker / Docker Compose
- GitHub Actions

---

## 5. Build

O projeto deve ser executado pelo Maven Wrapper versionado.

Windows:

```powershell
.\mvnw.cmd clean test
```

Linux/macOS/CI:

```bash
./mvnw clean test
```

A instalação global de Maven não é requisito do build versionado.

---

## 6. Configuração e segredos

A configuração da aplicação permanece centralizada em:

```text
EnvironmentConfigProvider
        ↓
ApplicationConfig
```

Variáveis:

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

O `.env` local não é versionado.

O `.env.example` documenta as variáveis necessárias sem conter segredo real.

---

## 7. Domínio atual

Estruturas principais:

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
│   ├── EvaluationRuleResult
│   └── RejectionReason
├── filter/
│   ├── BestCashDiscountSelector
│   ├── CashDiscountObservation
│   ├── CommercialFilterEngine
│   ├── CommercialFilterRuleCode
│   ├── FilterProfile
│   ├── MinCashDiscountRule
│   ├── MinRatingRule
│   └── MinReviewCountRule
├── product/
│   ├── Asin
│   └── Product
├── scoring/
│   ├── DealEvaluationRanking
│   ├── ScoreEngine
│   ├── ScoreFactorCode
│   ├── ScoreFactorResult
│   ├── ScoreFactorStatus
│   ├── ScoreInput
│   ├── ScoreNormalizer
│   ├── ScoreProfile
│   └── ScoreResult
├── publication/
├── shared/
└── validation/
    ├── AmazonEligibilityValidator
    ├── DeliveryType
    └── SellerType
```

---

## 8. `OfferSnapshot`

`OfferSnapshot` representa uma observação temporal de uma oferta.

Campos relevantes:

```text
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

`basisPrice` permanece semanticamente distinto de `previousPrice`.

Valores ausentes não são inventados.

`soldPercentage` é coletado, normalizado e persistido.

Na FASE 9 ele não participava dos filtros eliminatórios.

Na FASE 10 ele passou a participar do score como sinal de popularidade/tração, sem se tornar filtro eliminatório.

---

## 9. `DealEvaluation`

`DealEvaluation` registra:

```text
eligible
rejectionReason
eligibilityPolicyVersion
filterProfileVersion
ruleResults
score
scoreVersion
scoreFactors
momentum
momentumVersion
evaluatedAt
```

Versões atuais:

```text
eligibilityPolicyVersion = AMAZON_SELLER_DELIVERY_V1
filterProfileVersion     = COMMERCIAL_FILTER_V1
scoreVersion             = SCORE_V1
```

Momentum permanece:

```text
null
```

porque pertence à FASE 11.

Uma avaliação rejeitada permanece:

```text
score = null
scoreVersion = null
scoreFactors = []
```

Uma avaliação pontuada possui:

```text
score != null
scoreVersion != null
scoreFactors não vazio
```

O domínio valida que o score agregado corresponde à soma das contribuições dos fatores persistidos.

---

## 10. Coleta da Amazon

A fonte funcional investigada permanece:

```text
https://www.amazon.com.br/deals
```

Fluxo:

```text
AmazonDealsCollector
        ↓
HttpCollectionCollector
        ↓
JavaHttpTransport
        ↓
CollectionResult
```

O conteúdo coletado permanece bruto na fronteira de coleta.

O acesso real à Amazon não participa da suíte hermética padrão.

---

## 11. Parser de Deals

Fluxo:

```text
CollectionResult
      ↓
AmazonDealsParser
      ↓
ParsedDeal
```

O ASIN é normalizado e validado como:

```text
[A-Z0-9]{10}
```

Links relativos são normalizados usando a origem da coleta.

O parser preserva:

```text
currentPrice
basisPrice
previousPrice
soldPercentage
rating
reviewCount
```

Nenhum preço Pix é inferido.

Registros sem dados mínimos confiáveis são descartados.

---

## 12. Enriquecimento da página individual

Fluxo atual:

```text
ParsedDeal
    ↓
AmazonProductPageEnrichmentClient
    ├── AmazonProductPageParser
    │      ├── seller
    │      └── delivery
    │
    └── AmazonPaymentConditionParser
           └── paymentConditions
                   ↓
          ProductEnrichmentResult
```

Seller e delivery permanecem conceitos independentes.

Condições comerciais fazem parte do enriquecimento real da oferta.

---

## 13. Condições comerciais

O domínio suporta:

```text
PaymentConditionType.CASH
PaymentConditionType.CREDIT_INSTALLMENT
```

Métodos atualmente conhecidos:

```text
PIX
NUPAY_ADDITIONAL_LIMIT
CREDIT_CARD
```

Uma condição pode registrar, conforme aplicável:

```text
price
discountPercentage
installmentCount
installmentAmount
installmentTotal
interest
paymentMethods
```

Dados não explicitamente observados não são calculados ou inventados.

---

## 14. Parsing comercial

A extração de pagamento à vista utiliza regiões da página individual identificadas durante a FASE 0 v2.

Fonte principal:

```text
promotionMessageInsideBuyBox_feature_div
```

Fallback:

```text
oneTimePaymentPrice_feature_div
```

Dentro desse contexto podem ser reconhecidos:

```text
desconto percentual explícito
à vista
Pix
NuPay Limite Adicional
```

O parser não busca percentuais indiscriminadamente por toda a página.

Isso evita misturar a condição principal com outras promoções independentes.

---

## 15. Parcelamento

Condições de cartão são interpretadas separadamente.

Estrutura observada:

```text
InstallmentCalculatorTableCredit
```

Quando a fonte fornece evidência suficiente podem ser registrados:

```text
installmentCount
installmentAmount
installmentTotal
interest
paymentMethod = CREDIT_CARD
```

O cartão não participa do filtro nem do fator de desconto à vista.

---

## 16. Validação estrutural Amazon

Seller e delivery continuam sendo política estrutural.

Regras:

```text
SELLER_IS_AMAZON
DELIVERY_IS_AMAZON
```

A política é fail closed.

Exemplos de rejeição estrutural:

```text
SELLER_UNKNOWN
SELLER_THIRD_PARTY
DELIVERY_UNKNOWN
DELIVERY_THIRD_PARTY
```

Essa política é deliberadamente separada dos filtros comerciais e do score.

---

## 17. Perfil de filtros

A FASE 9 introduziu:

```text
FilterProfile
```

Campos:

```text
version
minCashDiscountPercentage
minRating
minReviewCount
```

Perfil ativo:

```text
COMMERCIAL_FILTER_V1
```

Limites:

```text
minCashDiscountPercentage = 20
minRating = 4.3
minReviewCount = 100
```

Os valores não ficam hardcoded no composition root.

---

## 18. Persistência do perfil de filtros

A migration:

```text
V7__commercial_filter_profile.sql
```

criou:

```text
filter_profile
```

com:

```text
version
min_cash_discount_percentage
min_rating
min_review_count
active
created_at
```

O banco garante:

- versão única;
- percentual entre 0 e 100;
- rating entre 0 e 5;
- `min_review_count >= 0`;
- no máximo um perfil ativo.

A versão utilizada em uma avaliação é preservada historicamente.

---

## 19. `FilterProfileProvider`

A aplicação depende da porta:

```text
FilterProfileProvider
```

Implementação atual:

```text
FilterProfileJdbcRepository
```

Fluxo:

```text
PostgreSQL
    ↓
filter_profile
    ↓
FilterProfileJdbcRepository
    ↓
FilterProfileProvider
    ↓
FilterProfile
```

Assim a camada de aplicação não conhece JDBC nem PostgreSQL.

---

## 20. Filtros comerciais

A FASE 9 implementou:

```text
MIN_CASH_DISCOUNT
MIN_RATING
MIN_REVIEW_COUNT
```

Cada filtro retorna:

```text
EvaluationRuleResult
```

contendo:

```text
ruleCode
passed
observedValue
threshold
reasonCode
```

---

## 21. Ausência e valor insuficiente

O sistema diferencia explicitamente:

```text
dado indisponível
```

de:

```text
dado presente abaixo do limite
```

Desconto:

```text
CASH_DISCOUNT_UNAVAILABLE
CASH_DISCOUNT_BELOW_MINIMUM
```

Rating:

```text
RATING_UNAVAILABLE
RATING_BELOW_MINIMUM
```

Avaliações:

```text
REVIEW_COUNT_UNAVAILABLE
REVIEW_COUNT_BELOW_MINIMUM
```

Ausência não é convertida em zero.

Esse princípio também é preservado pelo score.

---

## 22. Desconto à vista

O filtro e o score utilizam somente condições:

```text
PaymentConditionType.CASH
```

associadas a:

```text
PIX
NUPAY_ADDITIONAL_LIMIT
```

e exigem:

```text
discountPercentage explícito
```

Não são utilizados:

```text
desconto inferido por diferença de preços
parcelamento
CREDIT_CARD
promoções sem contexto reconhecido
```

---

## 23. `BestCashDiscountSelector`

Fluxo:

```text
PaymentCondition[]
        ↓
somente CASH
        ↓
somente PIX / NUPAY_ADDITIONAL_LIMIT
        ↓
somente desconto explícito
        ↓
maior percentual
        ↓
CashDiscountObservation
```

Exemplo:

```text
Pix = 20%
NuPay = 25%
```

Resultado:

```text
25%
```

Não existe soma de descontos.

Em empate, os métodos observados são preservados deterministicamente.

O mesmo seletor é reutilizado na construção do `ScoreInput`, evitando duas interpretações diferentes de desconto à vista.

---

## 24. Rating mínimo

`MinRatingRule` considera válido o intervalo:

```text
0..5
```

Comportamento:

```text
ausente/inválido
→ RATING_UNAVAILABLE

abaixo do mínimo
→ RATING_BELOW_MINIMUM

igual/acima
→ passa
```

---

## 25. Quantidade mínima de avaliações

`MinReviewCountRule` possui comportamento:

```text
ausente/inválido
→ REVIEW_COUNT_UNAVAILABLE

abaixo do mínimo
→ REVIEW_COUNT_BELOW_MINIMUM

igual/acima
→ passa
```

Valores negativos são tratados como indisponíveis.

---

## 26. `soldPercentage`

`soldPercentage` continua fora do motor de filtros.

Estado:

```text
coletado
normalizado
persistido
auditável
utilizado no score
```

mas:

```text
não elimina ofertas
```

No `SCORE_V1`, ele representa sinal de popularidade/tração.

Ausência não equivale a zero observado.

---

## 27. Motor comercial

Foi criado na FASE 9:

```text
CommercialFilterEngine
```

Ordem fixa:

```text
1. MIN_CASH_DISCOUNT
2. MIN_RATING
3. MIN_REVIEW_COUNT
```

Não existe short-circuit.

Todas as três regras são avaliadas para produzir auditoria completa.

---

## 28. Avaliação agregada

`AmazonDealEvaluationApplicationService` executa:

```text
AmazonEligibilityValidator
        ↓
FilterProfileProvider
        ↓
CommercialFilterEngine
        ↓
todas as regras passaram?
        ├── não
        │    ↓
        │ DealEvaluation sem score
        │
        └── sim
             ↓
        ScoreProfileProvider
             ↓
        ScoreInput
             ↓
        ScoreEngine
             ↓
        ScoreResult
             ↓
        DealEvaluation pontuada
```

A ordem das regras eliminatórias é:

```text
1. SELLER_IS_AMAZON
2. DELIVERY_IS_AMAZON
3. MIN_CASH_DISCOUNT
4. MIN_RATING
5. MIN_REVIEW_COUNT
```

A primeira falha determina:

```text
DealEvaluation.rejectionReason
```

Todos os cinco resultados permanecem persistidos.

Score só é calculado quando todos passam.

---

## 29. Sem short-circuit estrutural/comercial

Mesmo quando seller ou delivery falham, as regras comerciais também são avaliadas.

Exemplo:

```text
SELLER_IS_AMAZON
→ falha

DELIVERY_IS_AMAZON
→ falha

MIN_CASH_DISCOUNT
→ passa

MIN_RATING
→ passa

MIN_REVIEW_COUNT
→ passa
```

O resultado agregado continua estruturalmente rejeitado, mas a auditoria comercial permanece disponível.

O score não é calculado nesse cenário.

---

## 30. `SCORE_V1`

A FASE 10 introduziu:

```text
SCORE_V1
```

Fatores e pesos:

```text
SOLD_PERCENTAGE = 30
CASH_DISCOUNT   = 25
RATING          = 20
REVIEW_COUNT    = 15
```

Peso ativo total:

```text
90
```

Os 10 pontos restantes permanecem reservados para:

```text
PRICE_ATTRACTIVENESS
```

Esse fator não foi implementado porque ainda não existe fonte semântica independente e confiável.

Os pontos reservados não foram redistribuídos.

---

## 31. `ScoreProfile`

Campos:

```text
version
soldPercentageWeight
cashDiscountWeight
ratingWeight
reviewCountWeight
reviewCountFullScoreThreshold
```

Perfil atual:

```text
version = SCORE_V1

soldPercentageWeight = 30
cashDiscountWeight = 25
ratingWeight = 20
reviewCountWeight = 15

reviewCountFullScoreThreshold = 1000
```

O threshold de `1000` possui semântica de saturação do fator.

Ele é diferente do filtro mínimo da FASE 9:

```text
minReviewCount = 100
```

---

## 32. Persistência do perfil de score

A migration:

```text
V8__score_profile_and_factors.sql
```

criou:

```text
score_profile
```

com:

```text
version
sold_percentage_weight
cash_discount_weight
rating_weight
review_count_weight
review_count_full_score_threshold
active
created_at
```

O banco protege:

- versão única;
- pesos entre 0 e 100;
- soma ativa não superior a 100;
- threshold positivo;
- no máximo um perfil ativo.

Mudanças futuras de regra exigem nova versão:

```text
SCORE_V2
SCORE_V3
...
```

---

## 33. `ScoreProfileProvider`

A aplicação depende da porta:

```text
ScoreProfileProvider
```

Implementação atual:

```text
ScoreProfileJdbcRepository
```

Fluxo:

```text
PostgreSQL
    ↓
score_profile
    ↓
ScoreProfileJdbcRepository
    ↓
ScoreProfileProvider
    ↓
ScoreProfile
```

O serviço de aplicação não conhece JDBC nem SQL para obter o perfil.

---

## 34. Normalização do score

Foi criado:

```text
ScoreNormalizer
```

### `SOLD_PERCENTAGE`

```text
normalized = soldPercentage
```

### `CASH_DISCOUNT`

```text
normalized = cashDiscountPercentage
```

### `RATING`

```text
normalized =
    rating / 5 × 100
```

Exemplo:

```text
rating = 4.5
normalized = 90
```

### `REVIEW_COUNT`

```text
normalized =
    min(reviewCount, 1000)
    / 1000
    × 100
```

Exemplos:

```text
100 reviews  → 10
500 reviews  → 50
1000 reviews → 100
1500 reviews → 100
```

---

## 35. Contribuição dos fatores

A contribuição é:

```text
contribution =
    normalizedValue
    / 100
    × weight
```

Score final:

```text
score =
    soma das contribuições
```

A matemática utiliza:

```text
BigDecimal
```

com:

```text
scale = 4
rounding = HALF_UP
```

---

## 36. Ausência no score

A FASE 10 preserva:

```text
ausência != zero observado
```

Exemplo ausente:

```text
SOLD_PERCENTAGE
status = UNAVAILABLE
rawValue = null
normalizedValue = null
contribution = 0
```

Exemplo observado:

```text
SOLD_PERCENTAGE
status = AVAILABLE
rawValue = 0
normalizedValue = 0
contribution = 0
```

A contribuição pode ser igual, mas a explicação histórica permanece diferente.

---

## 37. Fatores explicáveis

Cada fator é representado por:

```text
ScoreFactorResult
```

Campos:

```text
code
status
rawValue
normalizedValue
weight
contribution
```

Códigos atuais:

```text
SOLD_PERCENTAGE
CASH_DISCOUNT
RATING
REVIEW_COUNT
```

Status:

```text
AVAILABLE
UNAVAILABLE
```

---

## 38. `ScoreEngine`

Entrada:

```text
ScoreProfile
ScoreInput
```

Saída:

```text
ScoreResult
```

O `ScoreEngine` não conhece:

```text
OfferSnapshot
PostgreSQL
JDBC
HTML
Pix
NuPay
ranking
momentum
publicação
```

Ele executa exclusivamente o contrato matemático versionado.

Ordem estável:

```text
1. SOLD_PERCENTAGE
2. CASH_DISCOUNT
3. RATING
4. REVIEW_COUNT
```

---

## 39. Persistência dos fatores

A migration V8 também criou:

```text
deal_evaluation_score_factor
```

Campos:

```text
deal_evaluation_id
factor_order
factor_code
status
raw_value
normalized_value
weight
contribution
```

Fluxo:

```text
ScoreFactorResult
        ↓
DealEvaluation
        ↓
DealEvaluationJdbcRepository
        ↓
deal_evaluation_score_factor
```

O repository não recalcula score.

Ele persiste exatamente o resultado produzido pelo domínio.

---

## 40. Ranking determinístico

Foi criado:

```text
DealEvaluationRanking
```

Contrato:

```text
1. somente avaliações com score participam
2. score DESC
3. ASIN ASC
```

Exemplo:

```text
B000000003 score 70
B000000002 score 50
B000000001 score 50
```

Resultado:

```text
B000000003
B000000001
B000000002
```

O ASIN é somente desempate técnico determinístico.

Ele não adiciona peso comercial ao score.

---

## 41. Persistência

Estado principal:

```text
product
  ↓
offer_snapshot
  ├── offer_payment_condition
  │      ↓
  │   offer_payment_condition_method
  │
  ├── offer_evidence
  │
  └── deal_evaluation
          ├── deal_evaluation_rule_result
          └── deal_evaluation_score_factor
```

Configurações versionadas:

```text
filter_profile
score_profile
```

---

## 42. Fluxo vertical atual

```text
CollectionRequest
        ↓
coleta
        ↓
AmazonDealsParser
        ↓
ParsedDeal
        ↓
AmazonProductPageEnrichmentClient
        ├── seller
        ├── delivery
        └── paymentConditions
        ↓
Product
        ↓
OfferSnapshot
        ↓
PaymentConditions
        ↓
Evidence
        ↓
AmazonEligibilityValidator
        ↓
FilterProfileProvider
        ↓
CommercialFilterEngine
        ↓
aprovada?
        ├── não
        │    ↓
        │ DealEvaluation sem score
        │
        └── sim
             ↓
        ScoreProfileProvider
             ↓
        ScoreEngine
             ↓
        DealEvaluation pontuada
             ↓
        PostgreSQL
```

---

## 43. Transações

`JdbcTransactionAdapter` continua protegendo a unidade de trabalho.

Quando recebe `autoCommit=true`, controla:

```text
begin
commit
rollback
restauração de autoCommit
```

Quando recebe `autoCommit=false`, utiliza Savepoint e não interfere indevidamente na transação externa.

`FilterProfile` e `ScoreProfile` são lidos durante a avaliação.

Os dados persistentes da observação e da avaliação continuam pertencendo à mesma unidade de trabalho.

---

## 44. Idempotência

A identidade da observação de `OfferSnapshot` permanece:

```text
product_id
+
collected_at
+
source
```

O PostgreSQL protege essa identidade.

Quando uma observação já existe, o sistema não duplica indevidamente:

```text
PaymentConditions
Evidence
DealEvaluation
EvaluationRuleResults
ScoreFactors
```

---

## 45. Testes ponta a ponta

### `AmazonDealProcessingEndToEndTest`

Comprova o cenário fail-closed comercial:

```text
seller Amazon
delivery Amazon
rating suficiente
reviewCount suficiente
sem desconto explícito Pix/NuPay
```

Resultado:

```text
eligible = false
rejectionReason = CASH_DISCOUNT_UNAVAILABLE
score = null
scoreVersion = null
scoreFactors = []
```

O reprocessamento continua idempotente.

### `AmazonDealPaymentConditionsEndToEndTest`

Comprova:

```text
fixture Deals
        ↓
produto comercial
        ↓
Pix/NuPay
        ↓
cartão
        ↓
OfferSnapshot
        ↓
PostgreSQL
```

Exemplo persistido:

```text
CASH
├── price = 79.90
├── discount = 25
├── PIX
└── NUPAY_ADDITIONAL_LIMIT

CREDIT_INSTALLMENT
├── installmentCount = 10
├── installmentAmount = 9.99
├── installmentTotal = 99.90
├── interest = 0
└── CREDIT_CARD
```

### `AmazonDealProcessingScoreEndToEndTest`

Comprova o caminho positivo da FASE 10.

Dados observados:

```text
soldPercentage = 37
cashDiscount = 25
rating = 4.6
reviewCount = 58363
```

Contribuições:

```text
SOLD_PERCENTAGE = 11.1000
CASH_DISCOUNT   =  6.2500
RATING          = 18.4000
REVIEW_COUNT    = 15.0000
```

Score persistido:

```text
50.7500
```

Versão:

```text
SCORE_V1
```

Também são persistidas exatamente quatro linhas em:

```text
deal_evaluation_score_factor
```

---

## 46. Fixtures

Estrutura relevante atual:

```text
src/test/resources/amazon/fixtures/
├── deals/
│   ├── basic-deal.html
│   ├── duplicate-deal.html
│   ├── end-to-end-deal.html
│   ├── invalid-deal.html
│   └── payment-conditions-end-to-end-deal.html
└── product/
    ├── amazon-amazon.html
    ├── amazon-commercial.html
    ├── amazon-global.html
    ├── thirdparty-amazon.html
    └── thirdparty-thirdparty.html
```

Capturas completas não fazem parte da dependência normal da suíte.

---

## 47. Schema

Estado atual:

```text
PostgreSQL: 18.6
Flyway: OK
Migrations: 8
Schema: versão 8
```

Migration mais recente:

```text
V8__score_profile_and_factors.sql
```

Migrations aplicadas permanecem imutáveis.

---

## 48. Testes externos

A suíte padrão permanece hermética:

```text
./mvnw clean test
```

A probe externa permanece separada:

```text
./mvnw --batch-mode -Pexternal-probe test
```

Workflow:

```text
.github/workflows/amazon-source-probe.yml
```

A probe utiliza requisição real à Amazon e permanece fora da suíte hermética.

---

## 49. CI

Workflow:

```text
.github/workflows/ci.yml
```

Ambiente:

```text
Ubuntu
JDK 25
PostgreSQL 18.6
Maven Wrapper
```

Comando:

```text
./mvnw --batch-mode clean test
```

Gatilhos atuais:

```text
pull_request
push em main
```

Validação da FASE 10:

```text
PR #1
FASE 10 - score, ranking e explicabilidade
CI: SUCCESS
```

Após o merge:

```text
main
merge commit:
567fb771f26cec3d93e93da1d9f2dec335b5bc8c
```

O CI foi executado novamente por `push` na `main`.

Resultado:

```text
CI #16
SUCCESS
```

O aviso sobre futura mudança de `ubuntu-latest` é informativo e não bloqueia o projeto.

---

## 50. Testes

Validação final da FASE 10:

```text
Tests run: 391
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

Flyway:

```text
Successfully validated 8 migrations
Current version of schema "public": 8
Schema "public" is up to date.
```

---

## 51. ADRs

Semântica comercial:

```text
docs/adr/0001-semantica-filtros-comerciais-e-apresentacao-pagamentos.md
```

Semântica de score, ranking e explicabilidade:

```text
docs/adr/0002-semantica-score-ranking-explicabilidade.md
```

Os ADRs separam explicitamente:

```text
elegibilidade estrutural
filtros comerciais
score
ranking
momentum
decisão de apresentação/publicação
```

---

## 52. Documentação de fases

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
├── FASE_8_RESULTADO.md
├── FASE_8_5_RESULTADO.md
├── FASE_9_RESULTADO.md
└── FASE_10_RESULTADO.md
```

A documentação de cada fase registra o estado verificável antes da passagem para a seguinte.

---

## 53. Commits de referência da FASE 10

ADR operacional:

```text
96a690a
docs: formaliza perfil operacional score v1
```

Ranking determinístico:

```text
58db753
feat: adiciona ranking deterministico por score
```

Teste vertical positivo:

```text
3443e3c
test: valida score no fluxo vertical
```

Fechamento documental local:

```text
eef64cc
docs: encerra localmente a fase 10
```

Registro do CI remoto:

```text
f8aa2c0
docs: registra ci remoto verde da fase 10
```

Merge para `main`:

```text
567fb77
Merge pull request #1 from veiocadan/fase-10-score
```

---

## 54. O que ainda não foi implementado

Para preservar a separação entre fases, permanecem:

- momentum;
- evolução temporal;
- comparação analítica entre snapshots históricos;
- tendência;
- aceleração de vendas;
- mudança recente de preço;
- combinação entre score atual e sinais históricos;
- execução recorrente;
- interface operacional;
- seleção final para publicação;
- `PublicationGenerator`;
- geração efetiva de link de associado;
- scheduler;
- filas;
- WhatsApp/Telegram;
- observabilidade completa;
- resiliência de produção;
- segurança e governança operacional;
- Excel/CSV opcional;
- mecanismos de escala guiados por métricas reais.

Também permanece fora do `SCORE_V1`:

```text
PRICE_ATTRACTIVENESS
```

Esse fator somente deverá ser implementado quando houver uma fonte semântica independente e confiável.

---

## 55. FASE 11

A próxima fase é:

```text
FASE 11 — Histórico e momentum
```

A FASE 11 deve responder a questões temporais que a FASE 10 deliberadamente não responde.

Exemplos:

```text
o desconto está aumentando?
o percentual vendido está acelerando?
o preço caiu recentemente?
a oferta ganhou ou perdeu tração?
como a observação atual se compara ao histórico?
```

A FASE 11 não deve alterar retroativamente o contrato de `SCORE_V1`.

Separação esperada:

```text
FASE 10
score sobre os fatos da observação atual

FASE 11
momentum derivado da evolução histórica
```

---

## 56. Roadmap

```text
FASE 4   → Configuração e segredos                 [CONCLUÍDA]
FASE 5   → Coleta                                  [CONCLUÍDA]
FASE 6   → Parser, ASIN e normalização             [CONCLUÍDA]
FASE 7   → Enriquecimento da página individual     [CONCLUÍDA]
FASE 8   → Validação Amazon                        [CONCLUÍDA]
FASE 8.5 → Consolidação do núcleo                  [CONCLUÍDA]
FASE 9   → Filtros comerciais configuráveis        [CONCLUÍDA]
FASE 10  → Score, ranking e explicabilidade        [CONCLUÍDA]
FASE 11  → Histórico e momentum                    [PRÓXIMA]
FASE 12  → Orquestração
FASE 13  → Interface
FASE 14  → Publicação
FASE 15+ → qualidade integrada, observabilidade,
            agendamento, canais, resiliência,
            segurança, integrações opcionais e escala
```

---

## 57. Estado atual consolidado

```text
FASE 10 — Score, ranking e explicabilidade

STATUS:
CONCLUÍDA

Elegibilidade estrutural:
AMAZON_SELLER_DELIVERY_V1

Regras estruturais:
SELLER_IS_AMAZON
DELIVERY_IS_AMAZON

Perfil comercial:
COMMERCIAL_FILTER_V1

Filtros comerciais:
MIN_CASH_DISCOUNT
MIN_RATING
MIN_REVIEW_COUNT

Limites comerciais:
cash discount >= 20%
rating >= 4.3
reviewCount >= 100

Perfil de score:
SCORE_V1

Fatores:
SOLD_PERCENTAGE = 30
CASH_DISCOUNT   = 25
RATING          = 20
REVIEW_COUNT    = 15

Peso ativo:
90

Preço atrativo:
10 pontos reservados
NÃO IMPLEMENTADO NO SCORE_V1

reviewCountFullScoreThreshold:
1000

Normalização:
DETERMINÍSTICA

Matemática:
BigDecimal
scale = 4
HALF_UP

Null:
AUSÊNCIA != ZERO OBSERVADO

Ranking:
score DESC
ASIN ASC

Momentum:
NÃO IMPLEMENTADO
RESERVADO PARA FASE 11

Fluxo vertical:
OK

Persistência:
OK

ScoreProfile:
OK

ScoreFactors:
OK

Ranking:
OK

Transações:
OK

Idempotência:
OK

Auditabilidade:
OK

Teste vertical positivo:
OK

Teste vertical negativo:
OK

Suíte hermética:
391 testes
0 falhas
0 erros
0 ignorados

Build:
SUCCESS

CI do PR:
SUCCESS

Merge em main:
SUCCESS

CI da main:
SUCCESS

PostgreSQL:
18.6

Flyway:
OK

Migrations:
8

Schema:
versão 8

Gate da FASE 10:
FECHADO

Próxima fase:
FASE 11 — Histórico e momentum
```
