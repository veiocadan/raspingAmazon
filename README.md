# Rasping Amazon

Sistema em desenvolvimento para **coleta, normalização, enriquecimento, validação, filtragem, avaliação, histórico, seleção e publicação de ofertas da Amazon Brasil**, com foco em separação de responsabilidades, rastreabilidade, idempotência, auditabilidade e evolução escalável.

> **Estado atual: FASE 9 concluída. O sistema possui elegibilidade estrutural Amazon, condições comerciais no fluxo vertical, perfil de filtros versionado e persistido, motor determinístico de filtros comerciais e avaliação auditável com cinco resultados de regra. A próxima fase é a FASE 10 — Score, após confirmação do CI remoto deste fechamento.**

## 1. Objetivo

O projeto não é apenas um raspador de ofertas.

O objetivo é construir um sistema em que **coleta, normalização, enriquecimento, validação, filtros comerciais, score, persistência e publicação permaneçam desacoplados**.

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
score / seleção
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

| Fase      | Descrição                                                | Status                          |
| --------- | -------------------------------------------------------- | ------------------------------- |
| FASE 0    | Levantamento da fonte e regras                           | CONCLUÍDA                       |
| FASE 0 v2 | Semântica comercial de preços e pagamento                | CONCLUÍDA                       |
| FASE 1    | Fundação Java                                            | CONCLUÍDA                       |
| FASE 2    | PostgreSQL, schema e migrations                          | CONCLUÍDA                       |
| FASE 2 v2 | Evolução comercial da persistência                       | CONCLUÍDA                       |
| FASE 3    | Domínio e contratos internos                             | CONCLUÍDA                       |
| FASE 3 v2 | Revisão comercial e estrutural                           | CONCLUÍDA                       |
| FASE 4    | Configuração e segredos                                  | CONCLUÍDA                       |
| FASE 5    | Coleta da página de promoções                            | CONCLUÍDA                       |
| FASE 6    | Parser, ASIN e normalização                              | CONCLUÍDA                       |
| FASE 7    | Enriquecimento da página individual                      | CONCLUÍDA                       |
| FASE 8    | Validação estrutural Amazon                              | CONCLUÍDA                       |
| FASE 8.5  | Consolidação do núcleo e preparação dos dados de decisão | CONCLUÍDA                       |
| FASE 9    | Motor de filtros comerciais configuráveis                | CONCLUÍDA                       |
| FASE 10   | Score                                                    | PRÓXIMA APÓS GATE DE FECHAMENTO |

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

* `domain`: conceitos, regras e invariantes de negócio, sem dependência de infraestrutura;
* `application`: contratos e coordenação dos casos de uso;
* `infrastructure`: PostgreSQL, Flyway, JDBC, configuração, HTTP, parsing específico da Amazon e adapters tecnológicos;
* `presentation`: interfaces de entrada e exposição operacional futura.

O domínio não conhece HTML, HTTP, PostgreSQL, Flyway ou JDBC.

O projeto permanece em um único módulo Maven enquanto não houver pressão arquitetural real para decomposição.

---

## 4. Stack

* Java 25
* Maven Wrapper 3.3.4
* Maven 3.9.16
* JUnit 5
* PostgreSQL 18.6
* Flyway 11.14.1
* PostgreSQL JDBC 42.7.8
* Jackson Databind 2.20.0
* Docker / Docker Compose
* GitHub Actions

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

`soldPercentage` é coletado e persistido, mas **não participa dos filtros eliminatórios da FASE 9**.

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
momentum
momentumVersion
evaluatedAt
```

Na FASE 9:

```text
eligibilityPolicyVersion
= AMAZON_SELLER_DELIVERY_V1
```

```text
filterProfileVersion
= COMMERCIAL_FILTER_V1
```

Score e momentum ainda permanecem:

```text
null
```

porque pertencem às fases posteriores.

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

Condições comerciais passaram a fazer parte do enriquecimento real da oferta.

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

O cartão não participa do filtro de desconto à vista.

---

## 16. Validação estrutural Amazon

Seller e delivery continuam sendo política estrutural.

Fluxo:

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

Essa política é deliberadamente separada dos filtros comerciais configuráveis.

---

## 17. Perfil de filtros

A FASE 9 introduziu:

```text
FilterProfile
```

Campos atuais:

```text
version
minCashDiscountPercentage
minRating
minReviewCount
```

Perfil inicial:

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

## 18. Persistência do perfil

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

* versão única;
* percentual entre 0 e 100;
* rating entre 0 e 5;
* `min_review_count >= 0`;
* no máximo um perfil ativo.

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

A FASE 9 diferencia explicitamente:

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

---

## 22. Desconto à vista

O filtro de desconto utiliza somente condições:

```text
PaymentConditionType.CASH
```

associadas a:

```text
PIX
```

ou:

```text
NUPAY_ADDITIONAL_LIMIT
```

e exige:

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

O fluxo de seleção é:

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

`soldPercentage` foi deliberadamente excluído do motor de filtros.

Estado:

```text
coletado
normalizado
persistido
auditável
```

mas:

```text
não elimina ofertas
```

Ele fica reservado para a FASE 10 como possível sinal de:

```text
popularidade
tração
demanda
```

Ausência não equivale a zero.

---

## 27. Motor comercial

Foi criado:

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
        +
CommercialFilterEngine
```

A ordem agregada é:

```text
1. SELLER_IS_AMAZON
2. DELIVERY_IS_AMAZON
3. MIN_CASH_DISCOUNT
4. MIN_RATING
5. MIN_REVIEW_COUNT
```

A ordem é determinística.

A primeira falha determina:

```text
DealEvaluation.rejectionReason
```

mas todos os cinco resultados permanecem persistidos.

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

---

## 30. Persistência

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
          ↓
      deal_evaluation_rule_result
```

Configuração comercial:

```text
filter_profile
```

---

## 31. Resultados individuais das regras

Cada avaliação persiste cinco linhas em:

```text
deal_evaluation_rule_result
```

Campos:

```text
rule_order
rule_code
passed
observed_value
threshold_value
reason_code
```

Isso permite reconstruir historicamente:

```text
qual regra foi executada
qual valor foi observado
qual limite foi exigido
se passou
se falhou
por qual motivo
```

---

## 32. Fluxo vertical atual

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
DealEvaluation
        ↓
PostgreSQL
```

---

## 33. Transações

`JdbcTransactionAdapter` continua protegendo a unidade de trabalho.

Quando recebe `autoCommit=true`, controla:

```text
begin
commit
rollback
restauração de autoCommit
```

Quando recebe `autoCommit=false`, utiliza Savepoint e não interfere indevidamente na transação externa.

---

## 34. Idempotência

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
```

---

## 35. Testes ponta a ponta

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
```

A avaliação persiste cinco resultados de regra.

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

---

## 36. Fixtures

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

## 37. Schema

Estado atual:

```text
PostgreSQL: 18.6
Flyway: OK
Migrations: 7
Schema: versão 7
```

A migration mais recente é:

```text
V7__commercial_filter_profile.sql
```

Migrations aplicadas permanecem imutáveis.

---

## 38. Testes externos

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

A indisponibilidade da Amazon real não deve quebrar o CI hermético.

---

## 39. CI

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

A confirmação do workflow remoto do fechamento da FASE 9 é gate para a abertura formal da FASE 10.

---

## 40. Testes

Validação local mais recente:

```text
Tests run: 301
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

Flyway:

```text
Successfully validated 7 migrations
Current version of schema "public": 7
Schema "public" is up to date.
```

---

## 41. ADR comercial

A semântica da FASE 9 e de partes das fases adjacentes está registrada em:

```text
docs/adr/0001-semantica-filtros-comerciais-e-apresentacao-pagamentos.md
```

O ADR separa explicitamente:

```text
elegibilidade estrutural
filtros comerciais
score
decisão de apresentação/publicação
```

---

## 42. Documentação de fases

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
└── FASE_9_RESULTADO.md
```

A documentação de cada fase registra o estado verificável antes da passagem para a seguinte.

---

## 43. O que ainda não foi implementado

Para preservar a separação entre fases, permanecem:

* score;
* ranking;
* momentum;
* histórico analítico das fases posteriores;
* execução recorrente;
* interface operacional;
* seleção final para publicação;
* `PublicationGenerator`;
* geração efetiva de link de associado;
* scheduler;
* filas;
* WhatsApp/Telegram;
* observabilidade completa;
* resiliência de produção;
* segurança e governança operacional;
* Excel/CSV opcional;
* mecanismos de escala guiados por métricas reais.

---

## 44. FASE 10

A próxima fase é:

```text
FASE 10 — Score
```

A FASE 10 não deve adicionar novos filtros implicitamente.

Ela deve responder:

```text
Entre as ofertas que já passaram pela elegibilidade
estrutural e pelos filtros comerciais,
quais merecem maior prioridade?
```

Sinais disponíveis:

```text
discountPercentage
rating
reviewCount
soldPercentage
```

Antes de implementar deverão ser definidos:

```text
scoreVersion
normalização
pesos
faixas
tratamento de null
arredondamento
ordem de desempate
auditabilidade
```

`soldPercentage` poderá entrar como sinal de popularidade/tração, não como requisito eliminatório.

---

## 45. Roadmap

```text
FASE 4   → Configuração e segredos                 [CONCLUÍDA]
FASE 5   → Coleta                                  [CONCLUÍDA]
FASE 6   → Parser, ASIN e normalização             [CONCLUÍDA]
FASE 7   → Enriquecimento da página individual     [CONCLUÍDA]
FASE 8   → Validação Amazon                        [CONCLUÍDA]
FASE 8.5 → Consolidação do núcleo                  [CONCLUÍDA]
FASE 9   → Filtros comerciais configuráveis        [CONCLUÍDA]
FASE 10  → Score                                   [PRÓXIMA]
FASE 11  → Histórico e momentum
FASE 12  → Orquestração
FASE 13  → Interface
FASE 14  → Publicação
FASE 15+ → qualidade integrada, observabilidade,
            agendamento, canais, resiliência,
            segurança, integrações opcionais e escala
```

---

## 46. Estado atual

```text
FASE 9 — Motor de filtros comerciais configuráveis

STATUS:
CONCLUÍDA

Perfil:
COMMERCIAL_FILTER_V1

Filtros:
MIN_CASH_DISCOUNT
MIN_RATING
MIN_REVIEW_COUNT

Elegibilidade estrutural:
SELLER_IS_AMAZON
DELIVERY_IS_AMAZON

Ordem total:
1 SELLER_IS_AMAZON
2 DELIVERY_IS_AMAZON
3 MIN_CASH_DISCOUNT
4 MIN_RATING
5 MIN_REVIEW_COUNT

Limites comerciais:
cash discount >= 20%
rating >= 4.3
reviewCount >= 100

soldPercentage:
COLETADO
PERSISTIDO
NÃO É FILTRO
RESERVADO PARA SCORE

Fluxo vertical: OK
Condições comerciais: OK
Persistência: OK
Transações: OK
Idempotência: OK
Auditabilidade: OK
Perfil versionado: OK

Suíte hermética:
301 testes
0 falhas
0 erros
0 ignorados

Build:
SUCCESS

PostgreSQL:
18.6

Flyway:
OK

Migrations:
7

Schema:
versão 7

Próximo gate:
README versionado + CI remoto verde

Próxima fase:
FASE 10 — Score
```
