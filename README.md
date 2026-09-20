# Rasping Amazon

Sistema em desenvolvimento para **coleta, normalização, enriquecimento, validação, filtragem, avaliação, score, ranking, histórico, evolução, momentum, seleção e publicação de ofertas da Amazon Brasil**, com foco em separação de responsabilidades, rastreabilidade, idempotência, auditabilidade e evolução escalável.

> **Estado atual: FASE 11 concluída. O sistema possui elegibilidade estrutural Amazon, condições comerciais no fluxo vertical, filtros comerciais versionados, score versionado e reproduzível, fatores explicáveis persistidos, ranking determinístico, histórico por ASIN, evolução entre snapshots, MOMENTUM_V1 auditável, recorrência e detecção de publicação anterior. A validação local está verde com 422 testes. O Pull Request #2 foi integrado à `main` pelo merge commit `46d7e4c`, e o CI pós-merge da `main` (run #21) foi concluído com sucesso. O schema PostgreSQL/Flyway está na versão 10. A próxima fase planejada é a FASE 12 — Orquestração e processamento assíncrono.**

## 1. Objetivo

O projeto não é apenas um raspador de ofertas.

O objetivo é construir um sistema em que **coleta, normalização, enriquecimento, validação, filtros comerciais, score, ranking, histórico, momentum, persistência e publicação permaneçam desacoplados**.

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
evolução temporal
  ↓
momentum versionado
  ↓
seleção / publicação futura
  ↓
canais
```

A ordem das fases deve ser preservada.

Responsabilidades futuras não devem ser antecipadas sem decisão explícita.

---

## 2. Estado atual

| Fase      | Descrição                                                | Status              |
|-----------|----------------------------------------------------------|---------------------|
| FASE 0    | Levantamento da fonte e regras                           | CONCLUÍDA           |
| FASE 0 v2 | Semântica comercial de preços e pagamento                | CONCLUÍDA           |
| FASE 1    | Fundação Java                                            | CONCLUÍDA           |
| FASE 2    | PostgreSQL, schema e migrations                          | CONCLUÍDA           |
| FASE 2 v2 | Evolução comercial da persistência                       | CONCLUÍDA           |
| FASE 3    | Domínio e contratos internos                             | CONCLUÍDA           |
| FASE 3 v2 | Revisão comercial e estrutural                           | CONCLUÍDA           |
| FASE 4    | Configuração e segredos                                  | CONCLUÍDA           |
| FASE 5    | Coleta da página de promoções                            | CONCLUÍDA           |
| FASE 6    | Parser, ASIN e normalização                              | CONCLUÍDA           |
| FASE 7    | Enriquecimento da página individual                      | CONCLUÍDA           |
| FASE 8    | Validação estrutural Amazon                              | CONCLUÍDA           |
| FASE 8.5  | Consolidação do núcleo e preparação dos dados de decisão | CONCLUÍDA           |
| FASE 9    | Motor de filtros comerciais configuráveis                | CONCLUÍDA           |
| FASE 10   | Score, ranking e explicabilidade                         | CONCLUÍDA           |
| FASE 11   | Histórico, evolução e momentum                           | CONCLUÍDA           |
| FASE 12   | Orquestração e processamento assíncrono                  | PRÓXIMA             |

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

- `domain`: conceitos, regras, normalização, evolução histórica, momentum, score, ranking e invariantes de negócio, sem dependência de infraestrutura;
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

Gate local final da FASE 11:

```text
Tests run: 422
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

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
├── history/
│   ├── HistoricalOfferObservation
│   ├── SnapshotEvolution
│   └── SnapshotEvolutionCalculator
├── momentum/
│   ├── MomentumAudit
│   ├── MomentumEngine
│   ├── MomentumResult
│   └── MomentumUnavailableReason
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

A FASE 11 adicionou dois conceitos deliberadamente separados do score:

```text
history
→ comparação temporal entre snapshots

momentum
→ interpretação versionada da evolução histórica
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

Na FASE 11 ele também passou a ser utilizado como sinal temporal para `MOMENTUM_V1`.

A identidade persistente da observação permanece:

```text
product_id
+
collected_at
+
source
```

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
momentumVersion          = MOMENTUM_V1, quando disponível
```

Contrato de momentum:

```text
disponível
→ momentum != null
→ momentumVersion = MOMENTUM_V1

indisponível
→ momentum = null
→ momentumVersion = null
```

A razão de indisponibilidade não é perdida. Ela permanece na auditoria específica:

```text
deal_evaluation_momentum_audit
```

Uma avaliação rejeitada permanece sem score:

```text
score = null
scoreVersion = null
scoreFactors = []
```

mas pode possuir momentum histórico.

Portanto:

```text
momentum não altera elegibilidade
momentum não altera SCORE_V1
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

Essa política permanece deliberadamente separada dos filtros comerciais, score e momentum.

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

Esse princípio é preservado pelo score e pelo momentum.

---

## 22. Desconto à vista

O filtro, o score e a comparação histórica de desconto reutilizam somente condições:

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

O mesmo seletor é reutilizado:

```text
filtros comerciais
score
evolução histórica
```

evitando definições concorrentes de melhor desconto à vista.

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
utilizado no SCORE_V1
utilizado no MOMENTUM_V1
```

mas:

```text
não elimina ofertas
```

No `SCORE_V1`, representa sinal da observação atual.

No `MOMENTUM_V1`, sua variação temporal representa evolução histórica.

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

`AmazonDealEvaluationApplicationService` executa atualmente:

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

independentemente da elegibilidade
        ↓
MomentumCalculationService
        ↓
OfferHistoryQueryPort
        ↓
SnapshotEvolutionCalculator
        ↓
MomentumEngine
        ↓
MomentumCalculation
        ↓
DealEvaluation
        ↓
DealEvaluationRepository
        ↓
MomentumAuditRepository
```

A ordem das regras eliminatórias continua:

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

Momentum é calculado separadamente e não participa da decisão de elegibilidade.

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

O momentum pode ser calculado se houver histórico suficiente, porque é um sinal independente.

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

A FASE 11 não alterou esse contrato.

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

Ele é diferente do filtro mínimo:

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

Perfis históricos não são modificados retroativamente.

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

A FASE 11 preserva o mesmo princípio para momentum.

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

Momentum não participa do ranking atual.

---

## 41. Histórico por ASIN

A FASE 11 introduziu a porta:

```text
OfferHistoryQueryPort
```

e a implementação:

```text
OfferHistoryJdbcRepository
```

Consultas disponíveis:

```text
findHistoryByAsin
findFirstByAsin
findLatestByAsin
findPreviousByAsin
countByAsin
```

A projeção histórica é:

```text
HistoricalOfferObservation
```

Campos principais:

```text
snapshotId
asin
collectedAt
currentPrice
soldPercentage
source
paymentConditions
```

Ordenação determinística:

```text
collected_at ASC
id ASC
```

Para comparação:

```text
previous.collectedAt < current.collectedAt
```

O snapshot atual nunca é tratado como seu próprio predecessor.

---

## 42. Evolução entre snapshots

Foi criado:

```text
SnapshotEvolutionCalculator
```

Entrada:

```text
HistoricalOfferObservation previous
HistoricalOfferObservation current
```

Saída:

```text
SnapshotEvolution
```

O cálculo produz:

```text
soldPercentageDelta
currentPriceDelta
currentPriceDeltaPercentage
cashDiscountDelta
elapsedSeconds
```

O componente é puro.

Ele não consulta banco, não decide elegibilidade, não calcula score e não publica.

---

## 43. Variação de percentual vendido

Fórmula:

```text
soldPercentageDelta
=
current.soldPercentage
-
previous.soldPercentage
```

Exemplo:

```text
62% → 68%
```

Resultado:

```text
+6 pontos percentuais
```

O delta pode ser positivo, zero ou negativo.

Se alguma observação não possuir percentual vendido:

```text
soldPercentageDelta = null
```

Ausência não é convertida em zero observado.

---

## 44. Variação de preço

Variação absoluta:

```text
currentPriceDelta
=
currentPrice
-
previousPrice
```

Exemplo:

```text
100,00 → 90,00
```

Resultado:

```text
-10,00
```

Variação percentual:

```text
currentPriceDeltaPercentage
=
currentPriceDelta
/
previousPrice
×
100
```

Exemplo:

```text
100,00 → 90,00
```

Resultado:

```text
-10.0000%
```

Quando o preço anterior é zero:

```text
currentPriceDeltaPercentage = null
```

Matemática:

```text
scale = 4
HALF_UP
```

---

## 45. Variação do desconto

A comparação histórica reutiliza:

```text
BestCashDiscountSelector
```

Fórmula:

```text
cashDiscountDelta
=
currentBestCashDiscount
-
previousBestCashDiscount
```

Se alguma observação não possuir desconto reconhecido:

```text
cashDiscountDelta = null
```

Nenhum desconto é inferido pela diferença entre preços.

---

## 46. Intervalo temporal

`SnapshotEvolution` preserva:

```text
previousCollectedAt
currentCollectedAt
```

e calcula:

```text
elapsedSeconds
```

A relação obrigatória é:

```text
currentCollectedAt > previousCollectedAt
```

---

## 47. `MOMENTUM_V1`

Foi criado:

```text
MomentumEngine
```

Versão atual:

```text
MOMENTUM_V1
```

O indicador mede:

```text
velocidade da variação do percentual vendido
em pontos percentuais por hora
```

Fórmula:

```text
momentum
=
soldPercentageDelta × 3600
/
elapsedSeconds
```

Precisão:

```text
BigDecimal
scale = 4
HALF_UP
```

Exemplo:

```text
10:00 → 62%
13:00 → 68%

delta = +6 p.p.
intervalo = 3h

momentum = 2.0000 p.p./hora
```

Momentum negativo é permitido.

Não existe clamp para zero.

---

## 48. Disponibilidade do momentum

Foi criado:

```text
MomentumResult
```

O resultado pode ser disponível ou indisponível.

Motivos modelados:

```text
NO_PREVIOUS_SNAPSHOT
SOLD_PERCENTAGE_UNAVAILABLE
```

Primeira observação:

```text
DealEvaluation
momentum = null
momentumVersion = null

MomentumAudit
calculationVersion = MOMENTUM_V1
status = UNAVAILABLE
unavailableReason = NO_PREVIOUS_SNAPSHOT
```

Se houver snapshot anterior, mas sold percentage insuficiente:

```text
status = UNAVAILABLE
unavailableReason = SOLD_PERCENTAGE_UNAVAILABLE
```

Outros fatos históricos disponíveis permanecem preservados.

---

## 49. Momentum independente de elegibilidade

Regra central:

```text
momentum ≠ filtro
```

Uma oferta inelegível pode possuir momentum.

Momentum não transforma uma oferta em elegível.

A FASE 11 não enfraqueceu as regras da FASE 8 ou FASE 9.

---

## 50. Momentum independente do score

Regra central:

```text
momentum ≠ SCORE_V1
```

O `SCORE_V1` permanece com os mesmos quatro fatores:

```text
SOLD_PERCENTAGE
CASH_DISCOUNT
RATING
REVIEW_COUNT
```

Momentum não é quinto fator.

Uma combinação futura entre score e momentum exigirá nova decisão semântica e novo versionamento.

---

## 51. Orquestração do cálculo histórico

Foi criado:

```text
MomentumCalculationService
```

Fluxo:

```text
OfferSnapshot atual persistido
        ↓
OfferHistoryQueryPort
        ↓
snapshot anterior
        ↓
SnapshotEvolutionCalculator
        ↓
SnapshotEvolution
        ↓
MomentumEngine
        ↓
MomentumResult
        ↓
MomentumCalculation
```

O snapshot atual não é relido desnecessariamente.

Somente a observação anterior é consultada no hot path do momentum.

---

## 52. Auditoria persistente de momentum

A migration:

```text
V9__momentum_audit.sql
```

criou:

```text
deal_evaluation_momentum_audit
```

Campos:

```text
id
deal_evaluation_id
calculation_version
status
unavailable_reason
previous_offer_snapshot_id
elapsed_seconds
sold_percentage_delta
current_price_delta
current_price_delta_percentage
cash_discount_delta
momentum
created_at
```

O schema protege coerência entre:

```text
AVAILABLE
UNAVAILABLE
```

e os campos correspondentes.

---

## 53. Resultado agregado e auditoria

`deal_evaluation` preserva:

```text
momentum
momentum_version
```

para acesso agregado.

`deal_evaluation_momentum_audit` preserva a base do cálculo.

Exemplo disponível:

```text
deal_evaluation
momentum = 2.0000
momentum_version = MOMENTUM_V1

audit
status = AVAILABLE
previous_offer_snapshot_id = ...
elapsed_seconds = 10800
sold_percentage_delta = 6
momentum = 2.0000
```

Exemplo indisponível:

```text
deal_evaluation
momentum = null
momentum_version = null

audit
calculation_version = MOMENTUM_V1
status = UNAVAILABLE
unavailable_reason = NO_PREVIOUS_SNAPSHOT
```

---

## 54. Recorrência e status histórico

Foram criados:

```text
OfferHistoryStatus
OfferHistoryStatusQueryPort
OfferHistoryStatusJdbcRepository
```

O read model informa:

```text
asin
snapshotCount
firstDetectedAt
lastUpdatedAt
publishedBefore
```

Uma oferta é recorrente quando:

```text
snapshotCount > 1
```

Também são calculáveis:

```text
timeSinceFirstDetection(referenceTime)
timeSinceLastUpdate(referenceTime)
```

O instante de referência é explícito para preservar determinismo e testabilidade.

---

## 55. Oferta já publicada

A FASE 11 define:

```text
já publicada
=
existe Publication
do mesmo ASIN
com status = PUBLISHED
```

Estados:

```text
CREATED
READY
FAILED
```

não representam publicação concluída.

Portanto:

```text
READY != publishedBefore
PUBLISHED = publishedBefore
```

A consulta utiliza `EXISTS`, evitando multiplicação indevida das linhas de snapshots.

---

## 56. Persistência atual

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
          ├── deal_evaluation_score_factor
          ├── deal_evaluation_momentum_audit
          └── publication
                  ↓
             publication_attempt
```

Configurações versionadas:

```text
filter_profile
score_profile
```

A unidade histórica continua sendo:

```text
OfferSnapshot
```

Não existe uma cópia paralela do histórico.

---

## 57. Fluxo vertical atual

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
        SCORE_V1

independentemente da elegibilidade
        ↓
OfferHistoryQueryPort
        ↓
snapshot anterior
        ↓
SnapshotEvolutionCalculator
        ↓
MomentumEngine
        ↓
MOMENTUM_V1
        ↓
DealEvaluation
        ↓
MomentumAudit
        ↓
PostgreSQL
```

Separação:

```text
SCORE_V1
→ fatos da observação atual

MOMENTUM_V1
→ evolução histórica entre observações
```

---

## 58. Transações

`JdbcTransactionAdapter` continua protegendo a unidade de trabalho.

Quando recebe `autoCommit=true`, controla:

```text
begin
commit
rollback
restauração de autoCommit
```

Quando recebe `autoCommit=false`, utiliza Savepoint e não interfere indevidamente na transação externa.

A mesma `Connection` JDBC é compartilhada pelos componentes da unidade de trabalho.

Na FASE 11 isso inclui:

```text
OfferSnapshot
PaymentConditions
Evidence
DealEvaluation
MomentumAudit
```

Sequência relevante:

```text
persistir DealEvaluation
        ↓
obter deal_evaluation.id
        ↓
persistir MomentumAudit
```

Se a auditoria falhar, a unidade transacional pode sofrer rollback, evitando estado parcial.

---

## 59. Idempotência

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
MomentumAudit
```

O teste vertical de momentum reprocessa a segunda observação e comprova que permanecem:

```text
2 OfferSnapshots
2 DealEvaluations
2 MomentumAudits
```

---

## 60. Índices históricos

A migration:

```text
V10__historical_read_indexes.sql
```

adicionou:

```text
idx_offer_snapshot_history
```

sobre:

```text
(product_id, collected_at DESC, id DESC)
```

Atende:

```text
snapshot anterior
primeiro snapshot
último snapshot
histórico ordenado
```

Também foram adicionados:

```text
idx_deal_evaluation_offer_snapshot
```

sobre:

```text
deal_evaluation(offer_snapshot_id)
```

e:

```text
idx_publication_evaluation_status
```

sobre:

```text
publication(deal_evaluation_id, status)
```

---

## 61. Testes ponta a ponta

### `AmazonDealProcessingEndToEndTest`

Comprova o cenário fail-closed comercial.

Resultado relevante:

```text
eligible = false
rejectionReason = CASH_DISCOUNT_UNAVAILABLE
score = null
scoreVersion = null
scoreFactors = []
```

### `AmazonDealPaymentConditionsEndToEndTest`

Comprova o fluxo de condições comerciais até o PostgreSQL.

Exemplo:

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

Comprova o caminho positivo do `SCORE_V1`.

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

Score:

```text
50.7500
```

Versão:

```text
SCORE_V1
```

### `AmazonDealProcessingMomentumEndToEndTest`

Comprova o caminho histórico da FASE 11.

Cenário:

```text
primeira observação
62%

segunda observação, 3 horas depois
68%
```

Evolução:

```text
soldPercentageDelta = 6
elapsedSeconds = 10800
```

Momentum:

```text
2.0000
```

Primeira avaliação:

```text
momentum = null
momentumVersion = null

audit
status = UNAVAILABLE
reason = NO_PREVIOUS_SNAPSHOT
version = MOMENTUM_V1
```

Segunda avaliação:

```text
momentum = 2.0000
momentumVersion = MOMENTUM_V1

audit
status = AVAILABLE
previous snapshot preservado
delta preservado
elapsedSeconds preservado
```

O teste também comprova idempotência no reprocessamento.

---

## 62. Fixtures

Estrutura relevante:

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

## 63. Schema

Estado local ao final da FASE 11:

```text
PostgreSQL: 18.6
Flyway: OK
Migrations: 10
Schema: versão 10
```

Migrations relevantes:

```text
V7__commercial_filter_profile.sql
V8__score_profile_and_factors.sql
V9__momentum_audit.sql
V10__historical_read_indexes.sql
```

As migrations anteriores não foram alteradas retroativamente.

---

## 64. Testes externos

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

## 65. CI

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

Gatilhos:

```text
pull_request
push em main
```

A FASE 10 permanece integrada à `main` com CI remoto aprovado.

Estado final da FASE 11:

```text
validação local = SUCCESS
CI remoto do Pull Request #2 = SUCCESS
Pull Request #2 = MERGED
merge em main = SUCCESS
merge commit = 46d7e4c
CI pós-merge da main = SUCCESS
GitHub Actions run = #21
```

A FASE 11 está formalmente encerrada.

---

## 66. Testes

Gate local final da FASE 11:

```text
Tests run: 422
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

Baseline final da FASE 10:

```text
391 testes
```

Crescimento líquido:

```text
31 testes
```

Também foi executado:

```text
git diff --check
```

sem erros.

Estado do repositório após o gate:

```text
working tree clean
```

---

## 67. Cobertura específica da FASE 11

A fase possui testes para:

```text
OfferHistoryJdbcRepository
HistoricalOfferObservation
SnapshotEvolution
SnapshotEvolutionCalculator
MomentumEngine
MomentumResult
MomentumCalculation
MomentumCalculationService
MomentumAudit
MomentumAuditJdbcRepository
OfferHistoryStatus
OfferHistoryStatusJdbcRepository
```

São cobertos, entre outros:

```text
histórico por ASIN
snapshot anterior
primeiro snapshot
último snapshot
contagem
delta positivo
delta negativo
delta zero
soldPercentage ausente
preço anterior zero
desconto ausente
intervalo temporal
momentum positivo
momentum negativo
momentum zero
primeira observação
auditoria AVAILABLE
auditoria UNAVAILABLE
recorrência
publicação READY
publicação PUBLISHED
idempotência vertical
```

---

## 68. ADRs

Semântica comercial:

```text
docs/adr/0001-semantica-filtros-comerciais-e-apresentacao-pagamentos.md
```

Semântica de score, ranking e explicabilidade:

```text
docs/adr/0002-semantica-score-ranking-explicabilidade.md
```

Semântica de histórico e momentum:

```text
docs/adr/0003-semantica-historico-e-momentum.md
```

Os ADRs separam explicitamente:

```text
elegibilidade estrutural
filtros comerciais
score
ranking
histórico
momentum
decisão de apresentação/publicação
```

---

## 69. Documentação de fases

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
├── FASE_10_RESULTADO.md
└── FASE_11_RESULTADO.md
```

A documentação de cada fase registra o estado verificável antes da passagem para a seguinte.

---

## 70. Commits de referência da FASE 10

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

## 71. Commits de referência da FASE 11

Contrato semântico:

```text
f1d8227
docs: define semantica de historico e momentum
```

Consulta histórica:

```text
33f6921
feat: adiciona consulta historica de ofertas
```

Evolução entre snapshots:

```text
ecc49b9
feat: calcula evolucao entre snapshots
```

Momentum:

```text
867881b
feat: implementa momentum v1
```

Auditoria persistente:

```text
4217f90
feat: adiciona auditoria persistente de momentum
```

Commits intermediários de persistência:

```text
fac1758
feat: persiste auditoria de momentum

ac3a866
feat: persiste auditoria de momentum
```

Orquestração histórica:

```text
ec1ae59
feat: orquestra calculo historico de momentum
```

Integração na avaliação:

```text
d259a71
feat: integra momentum na avaliacao de ofertas
```

Teste vertical:

```text
87e45d7
test: valida momentum no fluxo vertical
```

Status histórico:

```text
356e431
feat: adiciona status historico de ofertas
```

Índices históricos:

```text
0fd47a9
perf: adiciona indices para consultas historicas
```

Fechamento documental local:

```text
0ac1908
docs: encerra localmente a fase 11
```

Atualização do README na branch:

```text
cbfeb96
docs: atualiza readme apos fase 11
```

Registro do primeiro CI remoto verde:

```text
9c7007f
docs: registra ci remoto verde da fase 11
```

Registro documental final antes do merge:

```text
7aa3319
docs: registra ci remoto verde da fase 11
```

Merge para `main`:

```text
46d7e4c
Merge pull request #2 from veiocadan/fase-11-historico-momentum
```

---

## 72. Critérios de conclusão da FASE 11

Critério:

```text
consultar histórico de um ASIN
```

Resultado:

```text
ATENDIDO
```

Implementado por:

```text
OfferHistoryQueryPort
OfferHistoryJdbcRepository
```

Critério:

```text
calcular variação entre dois snapshots
```

Resultado:

```text
ATENDIDO
```

Implementado por:

```text
SnapshotEvolutionCalculator
SnapshotEvolution
```

Critério:

```text
produzir momentum sem alterar a regra principal de elegibilidade
```

Resultado:

```text
ATENDIDO
```

Implementado por:

```text
MOMENTUM_V1
```

Também foram atendidos:

```text
tempo desde primeira detecção
tempo desde última atualização
detecção de recorrência
detecção de publicação anterior
```

---

## 73. O que ainda não foi implementado

Para preservar a separação entre fases, permanecem:

- combinação entre `SCORE_V1` e momentum em uma nova regra de priorização;
- aceleração histórica de segunda ordem;
- previsão de vendas;
- modelos estatísticos ou machine learning;
- paginação do histórico para interface operacional;
- execução assíncrona;
- reprocessamento independente por etapa;
- scheduler;
- filas;
- múltiplos workers;
- interface operacional;
- seleção final para publicação;
- `PublicationGenerator`;
- geração efetiva de link de associado;
- publicação automática em canais;
- WhatsApp/Telegram;
- observabilidade completa;
- resiliência de produção;
- segurança e governança operacional;
- Excel/CSV opcional;
- mecanismos adicionais de escala guiados por métricas reais.

Também permanece fora do `SCORE_V1`:

```text
PRICE_ATTRACTIVENESS
```

Esse fator somente deverá ser implementado quando houver uma fonte semântica independente e confiável.

O histórico completo ainda não possui paginação porque essa necessidade pertence à interface operacional futura.

---

## 74. FASE 12

A próxima fase planejada é:

```text
FASE 12 — Orquestração e processamento assíncrono
```

A FASE 12 deverá permitir separar etapas e repetir somente o que falhou.

Responsabilidades previstas:

```text
casos de uso explícitos por etapa
idempotência por etapa
reprocessamento seguro
separação entre falhas transitórias e permanentes
jobs e/ou fila conforme necessidade
preparação para múltiplos workers
```

A FASE 12 não deve misturar novamente os contratos já separados de:

```text
coleta
enriquecimento
avaliação
histórico
momentum
publicação
```

O início da FASE 12 deve ocorrer somente após o fechamento remoto da FASE 11.

---

## 75. Roadmap

```text
FASE 4   → Configuração e segredos                 [CONCLUÍDA]
FASE 5   → Coleta                                  [CONCLUÍDA]
FASE 6   → Parser, ASIN e normalização             [CONCLUÍDA]
FASE 7   → Enriquecimento da página individual     [CONCLUÍDA]
FASE 8   → Validação Amazon                        [CONCLUÍDA]
FASE 8.5 → Consolidação do núcleo                  [CONCLUÍDA]
FASE 9   → Filtros comerciais configuráveis        [CONCLUÍDA]
FASE 10  → Score, ranking e explicabilidade        [CONCLUÍDA]
FASE 11  → Histórico, evolução e momentum          [CONCLUÍDA]
FASE 12  → Orquestração e processamento assíncrono [PRÓXIMA]
FASE 13  → Interface operacional
FASE 14  → Publicação
FASE 15+ → qualidade integrada, observabilidade,
            agendamento, canais, resiliência,
            segurança, integrações opcionais e escala
```

---

## 76. Estado atual consolidado

```text
FASE 11 — Histórico, evolução e momentum

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

Fatores do SCORE_V1:
SOLD_PERCENTAGE = 30
CASH_DISCOUNT   = 25
RATING          = 20
REVIEW_COUNT    = 15

Peso ativo:
90

PRICE_ATTRACTIVENESS:
10 pontos reservados
NÃO IMPLEMENTADO NO SCORE_V1

reviewCountFullScoreThreshold:
1000

Ranking:
score DESC
ASIN ASC

Histórico por ASIN:
IMPLEMENTADO

Snapshot anterior:
IMPLEMENTADO

Primeira observação:
IMPLEMENTADA

Última observação:
IMPLEMENTADA

Contagem de snapshots:
IMPLEMENTADA

Variação de vendidos:
IMPLEMENTADA

Variação absoluta de preço:
IMPLEMENTADA

Variação percentual de preço:
IMPLEMENTADA

Variação de desconto:
IMPLEMENTADA

Tempo desde primeira detecção:
IMPLEMENTADO

Tempo desde última atualização:
IMPLEMENTADO

Recorrência:
IMPLEMENTADA

Detecção de publicação anterior:
IMPLEMENTADA

Momentum:
MOMENTUM_V1

Fórmula:
soldPercentageDelta × 3600 / elapsedSeconds

Unidade:
pontos percentuais por hora

Matemática:
BigDecimal
scale = 4
HALF_UP

Momentum negativo:
PERMITIDO

Ausência:
DIFERENTE DE ZERO OBSERVADO

Momentum altera elegibilidade:
NÃO

Momentum altera SCORE_V1:
NÃO

Auditoria de momentum:
IMPLEMENTADA

Migration de auditoria:
V9

Índices históricos:
V10

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

Teste vertical de score:
OK

Teste vertical de momentum:
OK

Suíte hermética:
422 testes
0 falhas
0 erros
0 ignorados

Build local:
SUCCESS

PostgreSQL:
18.6

Flyway:
OK

Migrations:
10

Schema:
versão 10

CI remoto da FASE 11:
SUCCESS

Pull Request:
#2 — MERGED

Merge em main:
SUCCESS — 46d7e4c

CI pós-merge:
SUCCESS — run #21

Gate local da FASE 11:
FECHADO

Gate remoto da FASE 11:
FECHADO

Próxima fase:
FASE 12 — Orquestração e processamento assíncrono
```

---

## 77. Encerramento da FASE 11

A FASE 11 atingiu seus critérios técnicos locais e remotos.

O projeto passou de avaliações baseadas apenas na observação atual para um modelo capaz de explicar também a evolução temporal da oferta.

A separação central permanece:

```text
SCORE_V1
=
qualidade/prioridade da observação atual

MOMENTUM_V1
=
velocidade histórica do percentual vendido
```

Os dois conceitos permanecem independentes.

O ciclo remoto foi concluído:

```text
branch fase-11-historico-momentum
        ↓
Pull Request #2
        ↓
CI do PR verde
        ↓
merge em main
        ↓
46d7e4c
        ↓
CI da main verde
        ↓
GitHub Actions run #21
        ↓
FASE 11 CONCLUÍDA
```

Com esse fechamento, a FASE 12 está liberada para início.
