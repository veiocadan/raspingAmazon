# Rasping Amazon

Sistema em desenvolvimento para **coleta, normalização, enriquecimento, validação, filtragem, avaliação, score, ranking, histórico, evolução, momentum, seleção e publicação de ofertas da Amazon Brasil**, com foco em separação de responsabilidades, rastreabilidade, idempotência, auditabilidade e evolução escalável.

> **Estado atual: FASE 12 concluída local e remotamente. O sistema preserva o motor de decisão consolidado até a FASE 11 e acrescenta orquestração por etapas, fila durável em PostgreSQL, reprocessamento seletivo, classificação de falhas, retry com backoff, leases, recuperação de jobs, worker genérico, preparação para múltiplos workers e reforços de idempotência/concorrência. A validação local está verde com 514 testes. O schema PostgreSQL/Flyway está na versão 13. O Pull Request #3 foi integrado à `main` pelo merge commit `cb220c7`, e o CI pós-merge da `main` (run #25) foi concluído com sucesso. A próxima fase é a FASE 13 — Geração de publicação e link de associado.**

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

A ordem vigente das fases é definida pelo roadmap versionado da versão 1.0 e deve ser preservada até que exista uma mudança deliberada de planejamento.

Responsabilidades futuras não devem ser antecipadas sem decisão explícita.

---

## 2. Estado atual

| Fase      | Descrição                                                   | Status                         |
|-----------|-------------------------------------------------------------|--------------------------------|
| FASE 0    | Levantamento da fonte e regras                              | CONCLUÍDA                      |
| FASE 0 v2 | Semântica comercial de preços e pagamento                   | CONCLUÍDA                      |
| FASE 1    | Fundação Java                                               | CONCLUÍDA                      |
| FASE 2    | PostgreSQL, schema e migrations                             | CONCLUÍDA                      |
| FASE 2 v2 | Evolução comercial da persistência                          | CONCLUÍDA                      |
| FASE 3    | Domínio e contratos internos                                | CONCLUÍDA                      |
| FASE 3 v2 | Revisão comercial e estrutural                              | CONCLUÍDA                      |
| FASE 4    | Configuração e segredos                                     | CONCLUÍDA                      |
| FASE 5    | Coleta da página de promoções                               | CONCLUÍDA                      |
| FASE 6    | Parser, ASIN e normalização                                 | CONCLUÍDA                      |
| FASE 7    | Enriquecimento da página individual                         | CONCLUÍDA                      |
| FASE 8    | Validação estrutural Amazon                                 | CONCLUÍDA                      |
| FASE 8.5  | Consolidação do núcleo e preparação dos dados de decisão    | CONCLUÍDA                      |
| FASE 9    | Motor de filtros comerciais configuráveis                   | CONCLUÍDA                      |
| FASE 10   | Score, ranking e explicabilidade                            | CONCLUÍDA                      |
| FASE 11   | Histórico, evolução e momentum                              | CONCLUÍDA                      |
| FASE 12   | Orquestração e processamento assíncrono                     | CONCLUÍDA                      |
| FASE 13   | Geração de publicação e link de associado                   | PLANEJADA                      |
| FASE 14   | Interface operacional                                       | PLANEJADA                      |
| FASE 15   | Qualidade integrada e regressão de sistema                  | PLANEJADA                      |
| FASE 16   | Observabilidade e auditoria operacional                     | PLANEJADA                      |
| FASE 17   | Agendamento e execução contínua                             | PLANEJADA                      |
| FASE 18   | Contrato de canais e outbox de publicação                   | PLANEJADA                      |
| FASE 19   | Telegram e WhatsApp                                         | PLANEJADA                      |
| FASE 20   | Resiliência, recuperação e falhas de produção               | PLANEJADA                      |
| FASE 21   | Segurança, governança e fechamento da versão 1.0            | PLANEJADA                      |

O planejamento das FASES 13 a 21 está consolidado em:

```text
docs/phases/ROADMAP_RASPING_AMAZON_V1.md
```

A FASE 12 está concluída local e remotamente, com 514 testes verdes, PR #3 integrado à `main` e CI pós-merge aprovado.

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
- `application`: contratos, coordenação dos casos de uso e orquestração assíncrona por etapas;
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

Gate local técnico da FASE 12:

```text
Tests run: 514
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

Estado do gate:

```text
local  = FECHADO
remoto = FECHADO
```

O fechamento remoto foi concluído com CI verde no Pull Request #3, merge em `main` pelo commit `cb220c7` e CI pós-merge verde no GitHub Actions run #25.

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

O estado de negócio continua organizado em torno de produtos, snapshots, avaliações e histórico:

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

A FASE 12 acrescentou o estado operacional durável da orquestração:

```text
processing_run
      ↓
deal_candidate
      ↓
processing_job
```

Responsabilidades:

```text
processing_run
→ execução lógica de coleta


deal_candidate
→ resultado durável do parsing
→ permite repetir enrichment sem repetir coleta


processing_job
→ fila persistente
→ retry
→ ownership
→ lease
→ recuperação
```

Configurações versionadas permanecem separadas:

```text
filter_profile
score_profile
```

A unidade histórica de negócio continua sendo:

```text
OfferSnapshot
```

`processing_job` representa estado operacional e não substitui o histórico das ofertas.

---

## 57. Fluxo vertical e orquestração atual

O fluxo de negócio consolidado até a FASE 11 permanece válido, mas a FASE 12 separou sua execução operacional em estágios duráveis.

Fluxo orquestrado:

```text
ProcessingRun
      ↓
COLLECT_DEALS
      ↓
coleta
      ↓
AmazonDealsParser
      ↓
DealCandidate
      ↓
ENRICH_DEAL
      ↓
AmazonProductPageEnrichmentClient
      ├── seller
      ├── delivery
      └── paymentConditions
      ↓
Product
      ↓
OfferSnapshot
      ├── PaymentConditions
      └── Evidence
      ↓
EVALUATE_DEAL
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

A separação semântica continua:

```text
SCORE_V1
→ fatos da observação atual

MOMENTUM_V1
→ evolução histórica entre observações
```

A separação operacional adicionada pela FASE 12 é:

```text
COLLECT_DEALS
≠
ENRICH_DEAL
≠
EVALUATE_DEAL
```

Uma falha posterior não exige repetir automaticamente as etapas anteriores já persistidas.

---

## 58. Transações

`JdbcTransactionAdapter` continua protegendo unidades de trabalho JDBC.

Quando recebe `autoCommit=true`, controla:

```text
begin
commit
rollback
restauração de autoCommit
```

Quando recebe `autoCommit=false`, utiliza Savepoint e não interfere indevidamente na transação externa.

A mesma `Connection` JDBC é compartilhada pelos componentes pertencentes à mesma unidade de trabalho.

Na avaliação de negócio isso continua permitindo persistir atomicamente, conforme o caso:

```text
DealEvaluation
+
MomentumAudit
```

A FASE 12 acrescentou novas fronteiras transacionais.

Coleta:

```text
DealCandidate
+
ENRICH_DEAL
+
ProcessingRun COMPLETED
```

Enrichment:

```text
Product
+
OfferSnapshot
+
PaymentConditions
+
Evidence
+
EVALUATE_DEAL
```

Avaliação:

```text
lock do OfferSnapshot
+
rechecagem idempotente
+
DealEvaluation
+
MomentumAudit
```

Chamadas HTTP de coleta e enrichment permanecem fora de transações JDBC longas.

---

## 59. Idempotência

A idempotência é composta por várias proteções complementares.

### `OfferSnapshot`

A identidade da observação permanece:

```text
product_id
+
collected_at
+
source
```

### `ProcessingRun`

Uma execução lógica de coleta possui:

```text
run_key
```

única no banco.

Uma run já `COMPLETED` evita repetir coleta e parsing apenas porque o worker caiu antes do ACK.

### `DealCandidate`

A identidade final reforçada na revisão técnica da FASE 12 é:

```text
processing_run_id
+
asin
+
source
```

`collected_at` continua sendo fato da observação, mas não participa da identidade idempotente do candidato dentro da mesma run.

### `ProcessingJob`

A criação de trabalho é protegida por:

```text
job_type
+
idempotency_key
```

Exemplos:

```text
enrich:<dealCandidateId>
evaluate:<offerSnapshotId>
```

### `ENRICH_DEAL`

Antes de executar nova chamada externa, o caso de uso verifica se o snapshot já foi persistido.

Se já existir:

```text
não repete enrichment externo
→ garante EVALUATE_DEAL
→ retorna
```

### `EVALUATE_DEAL`

A avaliação possui fast path por avaliação existente e uma segunda verificação dentro da transação.

Para fechar a corrida entre workers concorrentes, a linha do snapshot é serializada com:

```sql
SELECT id
FROM offer_snapshot
WHERE id = ?
FOR UPDATE
```

Depois do lock, o segundo lookup decide se a avaliação ainda precisa ser produzida.

A constraint única da avaliação permanece como defesa adicional do schema.

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

Estado local após a implementação técnica da FASE 12:

```text
PostgreSQL: 18.6
Flyway: OK
Migrations: 13
Schema: versão 13
```

Migrations relevantes do motor de decisão e da orquestração:

```text
V7  → commercial filter profile
V8  → score profile and factors
V9  → momentum audit
V10 → historical read indexes
V11 → processing orchestration
V12 → idempotência da avaliação
V13 → idempotência forte de deal_candidate
```

As migrations anteriores não foram alteradas retroativamente.

A V13 fortalece a identidade de `deal_candidate` para:

```text
processing_run_id + asin + source
```

sem remover silenciosamente dados incompatíveis.

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

Sequência principal do CI:

```text
./mvnw --batch-mode -Dtest=DatabaseMigrationTest test
./mvnw --batch-mode test
```

A primeira etapa aplica e valida explicitamente as migrations antes da suíte completa, evitando dependência acidental da ordem de execução dos testes.

Gatilhos:

```text
pull_request
push em main
```

Gate remoto da FASE 12:

```text
Pull Request #3 = MERGED
branch = fase-12-orquestracao-assincrona
CI do PR = SUCCESS
merge commit = cb220c7
CI pós-merge da main = SUCCESS
GitHub Actions run = #25
```

Estado final da FASE 12:

```text
gate local = FECHADO
gate remoto = FECHADO
514 testes verdes
FASE 12 = CONCLUÍDA
```

---

## 66. Testes

Gate local técnico da FASE 12:

```text
Tests run: 514
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

Baseline final da FASE 11:

```text
422 testes
```

Crescimento líquido durante a FASE 12:

```text
92 testes
```

Além da suíte completa, foram executados testes específicos para:

```text
fila JDBC
persistência de ProcessingRun
persistência de DealCandidate
casos de uso de coleta
enrichment assíncrono
avaliação assíncrona
classificação de falhas
backoff
failure handler
lease recovery
política de lease
worker
dispatcher
integração worker + PostgreSQL
claim com conexões independentes
idempotência reforçada de DealCandidate
lock de OfferSnapshot para avaliação
```

Também foi executado:

```text
git diff --check
```

sem erros no gate técnico.

---

## 67. Cobertura específica das FASES 11 e 12

A cobertura histórica da FASE 11 permanece preservada para:

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

A FASE 12 acrescentou cobertura específica para:

```text
ProcessingRun
DealCandidate
ProcessingJob
JdbcProcessingJobQueueAdapter
JdbcProcessingRunRepositoryAdapter
JdbcDealCandidateRepositoryAdapter
CollectDealsUseCase
EnrichDealUseCase
EvaluateDealUseCase
DefaultProcessingFailureClassifier
ExponentialRetryBackoffPolicy
ProcessingJobFailureHandler
JdbcProcessingJobLeaseRecoveryAdapter
ProcessingJobLeaseRecoveryService
DefaultProcessingJobExecutor
ProcessingWorker
JdbcOfferSnapshotEvaluationLockAdapter
```

São cobertos, entre outros:

```text
enqueue idempotente
claim
SUCCEEDED
RETRY_WAIT
DEAD
retry transitório
falha permanente
backoff
ownership do worker
lease expirado
recuperação com tentativas restantes
recuperação com tentativas esgotadas
worker idle
worker success
ACK separado de falha funcional
múltiplas conexões JDBC
reentrada de coleta
reentrada de enrichment
reentrada de avaliação
candidate com timestamp diferente na mesma run
lock antes do segundo lookup de avaliação
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
├── FASE_11_RESULTADO.md
├── FASE_12_RESULTADO.md
└── ROADMAP_RASPING_AMAZON_V1.md
```

Os relatórios de fase registram o que efetivamente foi implementado.

O roadmap da versão 1.0 registra o planejamento deliberado para as FASES 13 a 21.

Relatórios históricos não devem ser reescritos retroativamente apenas para acompanhar mudanças posteriores do planejamento.

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

Com a FASE 12 implementada tecnicamente, deixam de estar pendentes:

```text
execução assíncrona por etapas
reprocessamento independente por etapa
fila durável de processamento
retry/backoff
leases
lease recovery
worker genérico
preparação para múltiplos workers
```

Permanecem para as fases posteriores do roadmap da versão 1.0:

- seleção final orientada ao fluxo de publicação;
- `PublicationGenerator`;
- templates versionados de publicação;
- política de apresentação de condições comerciais;
- geração efetiva e encapsulada de link de associado;
- interface operacional;
- jornadas completas de regressão envolvendo publicação;
- observabilidade operacional completa;
- scheduler e execução contínua;
- contrato de canais;
- outbox de publicação;
- aprovação/revisão operacional da publicação;
- Telegram;
- WhatsApp;
- resiliência de produção ampliada;
- taxonomia operacional de falhas mais rica;
- segurança e governança do release;
- backup e restauração documentados para a versão 1.0;
- empacotamento operacional final;
- mecanismos adicionais de escala somente quando justificados por métricas reais.

Também permanece fora do `SCORE_V1`:

```text
PRICE_ATTRACTIVENESS
```

Esse fator somente deverá ser implementado quando houver uma fonte semântica independente e confiável.

Excel/CSV não possui fase própria no roadmap da versão 1.0 e, se necessário futuramente, deve permanecer apenas como funcionalidade auxiliar, nunca como fonte de estado operacional.

Itens explicitamente pós-v1.0 incluem Amazon Creators API, Mercado Livre, hospedagem contínua em Raspberry Pi/servidor remoto e escala distribuída guiada por métricas.

---

## 74. FASE 12 — Orquestração e processamento assíncrono

A FASE 12 separou o processamento em unidades de trabalho duráveis e reexecutáveis.

Tipos de job:

```text
COLLECT_DEALS
ENRICH_DEAL
EVALUATE_DEAL
```

Fluxo:

```text
ProcessingRun
      ↓
COLLECT_DEALS
      ↓
DealCandidate
      ↓
ENRICH_DEAL
      ↓
OfferSnapshot
      ↓
EVALUATE_DEAL
      ↓
DealEvaluation
```

Persistência operacional:

```text
processing_run

deal_candidate

processing_job
```

Estados de job:

```text
PENDING
RUNNING
RETRY_WAIT
SUCCEEDED
DEAD
```

Claim concorrente:

```text
FOR UPDATE SKIP LOCKED
```

Idempotência de criação de job:

```text
job_type + idempotency_key
```

Falhas:

```text
TRANSIENT
PERMANENT
```

Falhas transitórias podem retornar a `RETRY_WAIT` com backoff enquanto houver tentativas.

Falhas permanentes ou tentativas esgotadas terminam em `DEAD`.

O lease utiliza:

```text
locked_at
locked_by
```

Jobs `RUNNING` abandonados podem ser recuperados quando o lease expira.

O worker processa uma unidade por rodada e utiliza dispatcher explícito:

```text
COLLECT_DEALS → processingRunId
ENRICH_DEAL   → dealCandidateId
EVALUATE_DEAL → offerSnapshotId
```

Revisão técnica final da fase:

```text
DealCandidate
→ UNIQUE(processing_run_id, asin, source)

EVALUATE_DEAL
→ lock transacional do OfferSnapshot
→ segundo lookup
→ persistência somente se ainda necessária
```

Baseline local final da implementação técnica:

```text
514 testes
0 failures
0 errors
0 skipped
```

Relatório detalhado:

```text
docs/phases/FASE_12_RESULTADO.md
```

Commits técnicos principais da branch:

```text
05a3f9f feat: adiciona persistencia da orquestracao
cab3194 feat: define contratos da orquestracao assincrona
90d8637 feat: implementa fila JDBC de processamento
47dbb30 feat: implementa fila JDBC de processamento1
b9227d4 feat: persiste runs e candidatos de processamento
45f41f5 feat: separa caso de uso de coleta assincrona
2f057b3 feat: separa caso de uso de enriquecimento assincrono
50e5d1f feat: prepara avaliacao assincrona idempotente
c4f0523 feat: separa caso de uso de avaliacao assincrona
3c7e401 feat: define politica de falhas e retry
848cf46 feat: aplica politica de falhas aos jobs
9a3e957 feat: recupera leases expirados de jobs
1003975 feat: define politica de expiracao de leases
ba25738 feat: implementa worker de processamento
e791531 test: integra orquestracao assincrona
30b8e93 fix: fortalece idempotencia de candidatos
2a95e75 fix: serializa avaliacoes concorrentes
```

Estado final:

```text
gate técnico local = FECHADO
gate remoto = FECHADO
Pull Request #3 = MERGED
merge commit = cb220c7
CI pós-merge = SUCCESS — run #25
```

---

## 75. Roadmap

O planejamento das FASES 13 a 21 é mantido em:

```text
docs/phases/ROADMAP_RASPING_AMAZON_V1.md
```

Esse documento representa o planejamento atual da versão 1.0.

Os relatórios históricos de cada fase permanecem como registro do que efetivamente foi implementado.

```text
FASE 4   → Configuração e segredos                         [CONCLUÍDA]
FASE 5   → Coleta                                          [CONCLUÍDA]
FASE 6   → Parser, ASIN e normalização                     [CONCLUÍDA]
FASE 7   → Enriquecimento da página individual             [CONCLUÍDA]
FASE 8   → Validação Amazon                                [CONCLUÍDA]
FASE 8.5 → Consolidação do núcleo                          [CONCLUÍDA]
FASE 9   → Filtros comerciais configuráveis                [CONCLUÍDA]
FASE 10  → Score, ranking e explicabilidade                [CONCLUÍDA]
FASE 11  → Histórico, evolução e momentum                  [CONCLUÍDA]
FASE 12  → Orquestração e processamento assíncrono         [CONCLUÍDA]
FASE 13  → Geração de publicação e link de associado       [PRÓXIMA]
FASE 14  → Interface operacional
FASE 15  → Qualidade integrada e regressão de sistema
FASE 16  → Observabilidade e auditoria operacional
FASE 17  → Agendamento e execução contínua
FASE 18  → Contrato de canais e outbox de publicação
FASE 19  → Telegram e WhatsApp
FASE 20  → Resiliência, recuperação e falhas de produção
FASE 21  → Segurança, governança e fechamento da versão 1.0
          ↓
        v1.0.0
```

A ordem atual coloca geração de publicação antes da interface operacional.

A decisão arquitetural é:

```text
primeiro:
casos de uso completos

depois:
interface consumindo esses casos de uso
```

A interface não deve se tornar o local onde regras de publicação são criadas.

---

## 76. Estado atual consolidado

```text
FASE 12 — Orquestração e processamento assíncrono

STATUS:
CONCLUÍDA

GATE LOCAL:
FECHADO

GATE REMOTO:
FECHADO


Motor de decisão preservado:

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

Perfil de score:
SCORE_V1

Ranking:
score DESC
ASIN ASC

Histórico por ASIN:
IMPLEMENTADO

Momentum:
MOMENTUM_V1


Orquestração:

ProcessingRun:
IMPLEMENTADO

DealCandidate:
IMPLEMENTADO

ProcessingJob:
IMPLEMENTADO

Fila durável:
PostgreSQL

Tipos de job:
COLLECT_DEALS
ENRICH_DEAL
EVALUATE_DEAL

Estados de job:
PENDING
RUNNING
RETRY_WAIT
SUCCEEDED
DEAD

Claim concorrente:
FOR UPDATE SKIP LOCKED

Idempotência de job:
job_type + idempotency_key

Reprocessamento por etapa:
IMPLEMENTADO

Separação coleta / enrichment / avaliação:
IMPLEMENTADA


COLLECT_DEALS:

ProcessingRun idempotente:
IMPLEMENTADA

Persistência de DealCandidate:
IMPLEMENTADA

Criação de ENRICH_DEAL:
IMPLEMENTADA

Coleta repetida após run COMPLETED:
EVITADA


DealCandidate:

Identidade final:
processing_run_id + asin + source

collected_at:
FATO DA OBSERVAÇÃO
NÃO PARTICIPA DA IDENTIDADE IDEMPOTENTE


ENRICH_DEAL:

Caso de uso independente:
IMPLEMENTADO

Reutilização de snapshot já persistido:
IMPLEMENTADA

Nova chamada externa após enrichment já concluído:
EVITADA

Criação de EVALUATE_DEAL:
IMPLEMENTADA


EVALUATE_DEAL:

Caso de uso independente:
IMPLEMENTADO

Fast path por avaliação existente:
IMPLEMENTADO

Segunda verificação transacional:
IMPLEMENTADA

Serialização concorrente por OfferSnapshot:
SELECT ... FOR UPDATE

Duplicação concorrente de DealEvaluation:
PROTEGIDA


Falhas:

Classificação:
TRANSIENT
PERMANENT

Retry:
IMPLEMENTADO

Backoff:
EXPONENCIAL LIMITADO

Tentativas máximas:
IMPLEMENTADAS

RETRY_WAIT:
IMPLEMENTADO

DEAD:
IMPLEMENTADO

Registro da última falha:
IMPLEMENTADO


Lease:

locked_at:
IMPLEMENTADO

locked_by:
IMPLEMENTADO

Lease recovery:
IMPLEMENTADO

Recuperação em lote:
IMPLEMENTADA

Jobs expirados com tentativas restantes:
RETRY_WAIT

Jobs expirados sem tentativas restantes:
DEAD

Política de duração de lease:
IMPLEMENTADA


Worker:

ProcessingWorker:
IMPLEMENTADO

Execução por rodada:
IMPLEMENTADA

Dispatcher:
IMPLEMENTADO

Mapeamento:
COLLECT_DEALS  -> processingRunId
ENRICH_DEAL    -> dealCandidateId
EVALUATE_DEAL  -> offerSnapshotId

ACK de sucesso:
IMPLEMENTADO

Falha funcional:
ProcessingJobFailureHandler

Falha de ownership durante ACK:
NÃO CONVERTIDA EM RETRY FUNCIONAL


Escala horizontal:

Múltiplos workers:
PREPARADOS

Distribuição concorrente:
FOR UPDATE SKIP LOCKED

Ownership:
IMPLEMENTADO

Lease recovery:
IMPLEMENTADO

Idempotência:
IMPLEMENTADA

Testes com conexões JDBC independentes:
IMPLEMENTADOS


Persistência:

PostgreSQL:
18.6

Flyway:
OK

Migrations:
13

Schema:
versão 13


Testes:

Suíte:
514 testes

Falhas:
0

Erros:
0

Ignorados:
0

Build local:
SUCCESS


Auditoria final da FASE 12:

Idempotência de DealCandidate:
REFORÇADA

Concorrência de DealEvaluation:
SERIALIZADA

Reprocessamento seguro:
OK

Retry seletivo:
OK

Recuperação de jobs:
OK

Múltiplos workers:
OK

Persistência durável:
OK

Gate local:
FECHADO

Gate remoto:
FECHADO

Pull Request:
#3 — MERGED

Merge em main:
SUCCESS — cb220c7

CI do PR:
SUCCESS

CI pós-merge:
SUCCESS — run #25


Próxima fase:
FASE 13 — Geração de publicação e link de associado
```

---

## 77. Encerramento da FASE 12

A FASE 12 atingiu os critérios técnicos locais e remotos definidos para orquestração e processamento assíncrono.

O projeto passou a possuir:

- processamento dividido em estágios duráveis;
- fila persistente em PostgreSQL;
- claim concorrente com `FOR UPDATE SKIP LOCKED`;
- retries seletivos;
- backoff;
- classificação explícita entre falhas transitórias e permanentes;
- ownership de jobs;
- leases;
- recuperação de leases expirados;
- worker genérico;
- dispatcher por tipo de job;
- reprocessamento seguro;
- idempotência por estágio;
- proteção contra duplicidade em `deal_candidate`;
- serialização concorrente da avaliação por `OfferSnapshot`.

O gate local foi executado com:

```text
Tests run: 514
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

A revisão técnica final acrescentou duas proteções antes do fechamento documental:

```text
DealCandidate
→ UNIQUE(processing_run_id, asin, source)

EvaluateDealUseCase
→ SELECT offer_snapshot ... FOR UPDATE
→ segundo lookup
→ avaliação idempotente sob concorrência
```

O resultado detalhado está em:

```text
docs/phases/FASE_12_RESULTADO.md
```

O planejamento posterior está em:

```text
docs/phases/ROADMAP_RASPING_AMAZON_V1.md
```

O ciclo remoto foi concluído:

```text
branch fase-12-orquestracao-assincrona
        ↓
Pull Request #3
        ↓
CI do PR verde
        ↓
merge em main
        ↓
cb220c7
        ↓
CI da main verde
        ↓
GitHub Actions run #25
        ↓
FASE 12 CONCLUÍDA
```

Durante o primeiro CI do PR, os testes JDBC da FASE 12 revelaram que o workflow iniciava a suíte sobre um PostgreSQL vazio sem aplicar migrations explicitamente. A correção foi registrada no commit:

```text
549144f ci: aplica migrations antes dos testes
```

O workflow passou a aplicar e validar as migrations antes da suíte completa, eliminando a dependência acidental da ordem dos testes.

---

## 78. Próxima fase

Com a FASE 12 concluída, o roadmap da versão 1.0 libera:

```text
FASE 13 — Geração de publicação e link de associado
```

Objetivo:

```text
DealEvaluation
      ↓
PublicationGenerator
      ↓
Publication persistida
```

A publicação deverá preservar relação reproduzível com os dados que a originaram, incluindo `Product`, `OfferSnapshot`, `DealEvaluation`, condição comercial apresentada, template/versionamento e link de associado.

Conceitos previstos pelo roadmap:

```text
PublicationGenerator
PublicationTemplate
PublicationTemplateVersion
AffiliateLinkGenerator
Publication
```

A FASE 13 não deve antecipar:

```text
Telegram
WhatsApp
scheduler
envio automático
```

A separação permanece:

```text
geração de conteúdo
≠
entrega em canal
```

A interface operacional passa para a FASE 14 e deverá consumir casos de uso já completos, sem incorporar regras de geração de publicação.
