# FASE 9 â€” RESULTADO CONSOLIDADO

**Projeto:** Rasping Amazon
**Fase:** 9 â€” Motor de filtros comerciais configurÃ¡veis
**Data:** 20/09/2026
**Status:** CONCLUÃDA â€” validaÃ§Ã£o local, CI remoto e probe externa concluÃ­dos com sucesso

---

## 1. Objetivo

A FASE 9 implementou o motor de filtros comerciais configurÃ¡veis utilizado para decidir se uma oferta estruturalmente vÃ¡lida possui requisitos comerciais mÃ­nimos para continuar no pipeline.

A fase preserva a separaÃ§Ã£o entre:

```text
elegibilidade estrutural Amazon
```

e:

```text
filtros comerciais configurÃ¡veis
```

A implementaÃ§Ã£o final avalia:

```text
desconto mÃ­nimo Ã  vista
rating mÃ­nimo
quantidade mÃ­nima de avaliaÃ§Ãµes
```

Sem incluir score, ranking ou momentum.

---

## 2. PrincÃ­pios preservados

A FASE 9 manteve as decisÃµes arquiteturais centrais do projeto:

- domÃ­nio independente de PostgreSQL, JDBC, HTTP e HTML;
- configuraÃ§Ã£o comercial fora do cÃ³digo;
- versionamento explÃ­cito das polÃ­ticas aplicadas;
- valores ausentes nÃ£o sÃ£o inventados;
- parser descreve fatos, nÃ£o toma decisÃµes de elegibilidade;
- seller e delivery permanecem elegibilidade estrutural;
- filtros comerciais permanecem etapa independente;
- todas as regras avaliadas sÃ£o auditÃ¡veis;
- ordem das regras determinÃ­stica;
- ausÃªncia de dado Ã© diferente de valor insuficiente;
- migrations aplicadas permanecem imutÃ¡veis;
- score e momentum permanecem fora desta fase.

---

## 3. ADR da semÃ¢ntica comercial

As decisÃµes centrais estÃ£o registradas em:

```text
docs/adr/0001-semantica-filtros-comerciais-e-apresentacao-pagamentos.md
```

A separaÃ§Ã£o adotada Ã©:

```text
seller / delivery
â†’ elegibilidade estrutural

desconto Ã  vista
rating
reviewCount
â†’ filtros comerciais

soldPercentage
â†’ nÃ£o Ã© filtro
â†’ reservado para score
```

A seleÃ§Ã£o utilizada para filtrar tambÃ©m Ã© distinta da seleÃ§Ã£o futura utilizada para apresentaÃ§Ã£o/publicaÃ§Ã£o.

Commit de referÃªncia:

```text
fe4e890 docs: registra ADR de filtros comerciais e pagamentos
```

---

## 4. `soldPercentage`

`soldPercentage` foi deliberadamente removido dos filtros eliminatÃ³rios.

Estado:

```text
coletado
normalizado
persistido
auditÃ¡vel
```

Mas:

```text
nÃ£o rejeita oferta na FASE 9
```

O dado permanece disponÃ­vel para a FASE 10 como possÃ­vel sinal de:

```text
popularidade
traÃ§Ã£o
demanda observada
```

AusÃªncia de `soldPercentage` nÃ£o equivale a zero.

---

## 5. `FilterProfile`

Foi criado:

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

O perfil nÃ£o possui:

```text
minSoldPercentage
```

nem:

```text
onlyAmazon
```

Seller e delivery pertencem Ã  polÃ­tica estrutural da FASE 8.

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

---

## 6. Perfil persistido

A migration:

```text
V7__commercial_filter_profile.sql
```

criou:

```text
filter_profile
```

Campos:

```text
version
min_cash_discount_percentage
min_rating
min_review_count
active
created_at
```

O schema protege:

- versÃ£o Ãºnica;
- percentual entre 0 e 100;
- rating entre 0 e 5;
- quantidade mÃ­nima de avaliaÃ§Ãµes nÃ£o negativa;
- no mÃ¡ximo um perfil ativo.

Perfil inicial:

```text
COMMERCIAL_FILTER_V1
20.00
4.30
100
active = true
```

Perfis histÃ³ricos nÃ£o devem ser modificados.

MudanÃ§as futuras devem criar novas versÃµes:

```text
COMMERCIAL_FILTER_V2
COMMERCIAL_FILTER_V3
...
```

Commit de referÃªncia:

```text
9dc20e9 feat: adiciona perfil de filtros comerciais persistido
```

---

## 7. Porta de configuraÃ§Ã£o

A aplicaÃ§Ã£o depende de:

```text
FilterProfileProvider
```

ImplementaÃ§Ã£o atual:

```text
FilterProfileJdbcRepository
```

Fluxo:

```text
PostgreSQL
    â†“
filter_profile
    â†“
FilterProfileJdbcRepository
    â†“
FilterProfileProvider
    â†“
FilterProfile
```

Assim a camada de aplicaÃ§Ã£o nÃ£o conhece JDBC ou PostgreSQL.

---

## 8. SemÃ¢ntica das rejeiÃ§Ãµes

A fase diferencia ausÃªncia de dado de valor abaixo do mÃ­nimo.

### Desconto

```text
ausente
â†’ CASH_DISCOUNT_UNAVAILABLE

abaixo do mÃ­nimo
â†’ CASH_DISCOUNT_BELOW_MINIMUM
```

### Rating

```text
ausente ou invÃ¡lido
â†’ RATING_UNAVAILABLE

abaixo do mÃ­nimo
â†’ RATING_BELOW_MINIMUM
```

### AvaliaÃ§Ãµes

```text
ausente ou invÃ¡lido
â†’ REVIEW_COUNT_UNAVAILABLE

abaixo do mÃ­nimo
â†’ REVIEW_COUNT_BELOW_MINIMUM
```

AusÃªncia nÃ£o Ã© transformada em zero.

Commit de referÃªncia:

```text
333d0f8 feat: define semantica de rejeicao dos filtros comerciais
```

---

## 9. `MinRatingRule`

Comportamento:

```text
rating ausente
â†’ RATING_UNAVAILABLE

rating invÃ¡lido
â†’ RATING_UNAVAILABLE

rating < minRating
â†’ RATING_BELOW_MINIMUM

rating >= minRating
â†’ passa
```

Intervalo vÃ¡lido:

```text
0..5
```

---

## 10. `MinReviewCountRule`

Comportamento:

```text
reviewCount ausente
â†’ REVIEW_COUNT_UNAVAILABLE

reviewCount negativo
â†’ REVIEW_COUNT_UNAVAILABLE

reviewCount < minReviewCount
â†’ REVIEW_COUNT_BELOW_MINIMUM

reviewCount >= minReviewCount
â†’ passa
```

---

## 11. Desconto mÃ­nimo Ã  vista

A regra considera somente condiÃ§Ãµes:

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

e exige desconto explicitamente observado.

NÃ£o participam do filtro:

```text
CREDIT_CARD
parcelamento
desconto inferido
diferenÃ§a matemÃ¡tica de preÃ§os
promoÃ§Ãµes fora do contexto reconhecido
```

---

## 12. `BestCashDiscountSelector`

Fluxo:

```text
PaymentCondition[]
        â†“
somente CASH
        â†“
somente PIX / NUPAY_ADDITIONAL_LIMIT
        â†“
somente desconto explÃ­cito
        â†“
maior percentual
        â†“
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

Os descontos nÃ£o sÃ£o somados.

Em empate, os mÃ©todos observados permanecem preservados de forma determinÃ­stica.

---

## 13. `MinCashDiscountRule`

Comportamento:

```text
nenhuma condiÃ§Ã£o CASH vÃ¡lida
â†’ CASH_DISCOUNT_UNAVAILABLE

melhor desconto < mÃ­nimo
â†’ CASH_DISCOUNT_BELOW_MINIMUM

melhor desconto >= mÃ­nimo
â†’ passa
```

Commit de referÃªncia:

```text
28531dc feat: adiciona filtro de desconto a vista
```

---

## 14. Pagamento e publicaÃ§Ã£o futura

A FASE 9 nÃ£o implementa publicaÃ§Ã£o.

O ADR apenas preserva a semÃ¢ntica que serÃ¡ utilizada posteriormente.

Para pagamento Ã  vista:

```text
NuPay > Pix
â†’ NuPay pode ser destacado junto com Pix

Pix > NuPay
â†’ Pix Ã© preferido

Pix == NuPay
â†’ Pix Ã© preferido

somente um disponÃ­vel
â†’ utiliza o disponÃ­vel
```

Essa lÃ³gica nÃ£o altera o filtro comercial.

---

## 15. CartÃ£o de crÃ©dito

CondiÃ§Ãµes de cartÃ£o permanecem separadas do desconto Ã  vista.

O cartÃ£o:

```text
nÃ£o participa de MIN_CASH_DISCOUNT
```

mas permanece:

```text
coletado
normalizado
transportado
persistido
auditÃ¡vel
```

Quando disponÃ­vel, sÃ£o preservados:

```text
installmentCount
installmentAmount
installmentTotal
interest
paymentMethod = CREDIT_CARD
```

---

## 16. `CommercialFilterEngine`

Foi criado:

```text
CommercialFilterEngine
```

Ordem comercial fixa:

```text
1. MIN_CASH_DISCOUNT
2. MIN_RATING
3. MIN_REVIEW_COUNT
```

NÃ£o existe short-circuit.

As trÃªs regras sÃ£o sempre avaliadas para produzir auditoria completa.

Commit de referÃªncia:

```text
eca8405 feat: adiciona motor deterministico de filtros comerciais
```

---

## 17. IntegraÃ§Ã£o de condiÃ§Ãµes comerciais

Durante a FASE 9 foi identificada uma lacuna no fluxo vertical.

Antes:

```text
pÃ¡gina do produto
    â†“
seller / delivery
    â†“
ProductEnrichmentResult
    â†“
OfferSnapshot
    â†“
paymentConditions = []
```

Isso faria ofertas reais falharem artificialmente em:

```text
CASH_DISCOUNT_UNAVAILABLE
```

A lacuna foi corrigida com:

```text
AmazonPaymentConditionParser
```

---

## 18. Parsing das condiÃ§Ãµes comerciais

Para promoÃ§Ã£o Ã  vista sÃ£o consideradas regiÃµes especÃ­ficas da pÃ¡gina:

```text
promotionMessageInsideBuyBox_feature_div
```

com fallback:

```text
oneTimePaymentPrice_feature_div
```

SÃ£o reconhecidos no contexto correto:

```text
percentual explÃ­cito Ã  vista
Pix
NuPay Limite Adicional
```

O parser nÃ£o busca percentuais indiscriminadamente por toda a pÃ¡gina.

Isso evita confusÃ£o com:

```text
Prime
cupom
cartÃ£o Amazon
primeira compra
outras promoÃ§Ãµes independentes
```

---

## 19. Parcelamento

O parcelamento utiliza a regiÃ£o:

```text
InstallmentCalculatorTableCredit
```

Quando os dados existem explicitamente, sÃ£o preservados:

```text
installmentCount
installmentAmount
installmentTotal
interest
CREDIT_CARD
```

Valores ausentes nÃ£o sÃ£o reconstruÃ­dos matematicamente.

---

## 20. Enrichment comercial

`ProductEnrichmentResult` passou a transportar:

```text
paymentConditions
```

Fluxo:

```text
pÃ¡gina individual
        â†“
AmazonProductPageParser
        â”œâ”€â”€ seller
        â””â”€â”€ delivery

AmazonPaymentConditionParser
        â””â”€â”€ paymentConditions
                â†“
ProductEnrichmentResult
                â†“
OfferSnapshotFactory
                â†“
OfferSnapshot
```

AusÃªncia de condiÃ§Ã£o permanece:

```text
List.of()
```

---

## 21. PersistÃªncia das condiÃ§Ãµes

Fluxo completo:

```text
HTML
 â†“
AmazonPaymentConditionParser
 â†“
PaymentCondition[]
 â†“
ProductEnrichmentResult
 â†“
AmazonDealProcessingService
 â†“
OfferSnapshot
 â†“
PaymentConditionJdbcPersistenceAdapter
 â†“
OfferPaymentConditionRepository
 â†“
PostgreSQL
```

Estruturas:

```text
offer_payment_condition
offer_payment_condition_method
```

Exemplo E2E:

```text
CASH
â”œâ”€â”€ price = 79.90
â”œâ”€â”€ discount = 25
â”œâ”€â”€ PIX
â””â”€â”€ NUPAY_ADDITIONAL_LIMIT

CREDIT_INSTALLMENT
â”œâ”€â”€ installmentCount = 10
â”œâ”€â”€ installmentAmount = 9.99
â”œâ”€â”€ installmentTotal = 99.90
â”œâ”€â”€ interest = 0
â””â”€â”€ CREDIT_CARD
```

Commit:

```text
041328d feat: integra condicoes comerciais ao pipeline
```

---

## 22. IntegraÃ§Ã£o com `DealEvaluation`

`AmazonDealEvaluationApplicationService` combina:

```text
AmazonEligibilityValidator
+
CommercialFilterEngine
```

DependÃªncias:

```text
AmazonEligibilityValidator
CommercialFilterEngine
FilterProfileProvider
DealEvaluationRepository
```

O perfil comercial ativo Ã© carregado por `FilterProfileProvider`.

---

## 23. Ordem agregada

Ordem total:

```text
1. SELLER_IS_AMAZON
2. DELIVERY_IS_AMAZON
3. MIN_CASH_DISCOUNT
4. MIN_RATING
5. MIN_REVIEW_COUNT
```

A ordem Ã© determinÃ­stica.

A primeira falha define:

```text
DealEvaluation.rejectionReason
```

Mas os cinco resultados permanecem armazenados.

---

## 24. AusÃªncia de short-circuit

Mesmo quando seller ou delivery falham, os filtros comerciais continuam sendo avaliados.

Exemplo:

```text
SELLER_IS_AMAZON
â†’ falha

DELIVERY_IS_AMAZON
â†’ falha

MIN_CASH_DISCOUNT
â†’ passa

MIN_RATING
â†’ passa

MIN_REVIEW_COUNT
â†’ passa
```

Isso preserva uma visÃ£o completa da oferta.

---

## 25. Versionamento da avaliaÃ§Ã£o

A avaliaÃ§Ã£o registra separadamente:

```text
eligibilityPolicyVersion
```

e:

```text
filterProfileVersion
```

Valores atuais:

```text
eligibilityPolicyVersion
= AMAZON_SELLER_DELIVERY_V1
```

```text
filterProfileVersion
= COMMERCIAL_FILTER_V1
```

Ainda permanecem:

```text
score = null
scoreVersion = null
momentum = null
momentumVersion = null
```

---

## 26. PersistÃªncia dos resultados

Cada avaliaÃ§Ã£o persiste cinco resultados em:

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

Isso permite reconstruir:

```text
qual regra foi usada
qual valor foi observado
qual limite foi aplicado
qual regra passou
qual regra falhou
por qual motivo
```

---

## 27. Fail-closed comercial

Foi validado o cenÃ¡rio:

```text
seller = Amazon
delivery = Amazon
rating suficiente
reviewCount suficiente
desconto explÃ­cito ausente
```

Resultado:

```text
eligible = false
rejectionReason = CASH_DISCOUNT_UNAVAILABLE
```

Isso impede aprovaÃ§Ã£o com dados comerciais insuficientes.

---

## 28. Testes ponta a ponta

### `AmazonDealProcessingEndToEndTest`

Valida:

```text
seller Amazon
delivery Amazon
rating suficiente
reviewCount suficiente
sem desconto Pix/NuPay explÃ­cito
```

Resultado:

```text
CASH_DISCOUNT_UNAVAILABLE
```

TambÃ©m comprova idempotÃªncia no reprocessamento.

### `AmazonDealPaymentConditionsEndToEndTest`

Valida:

```text
Pix
NuPay
cartÃ£o
OfferSnapshot
persistÃªncia
```

As condiÃ§Ãµes comerciais atravessam o pipeline completo.

---

## 29. Composition root

`AmazonDealProcessingComposition` conecta:

```text
FilterProfileJdbcRepository
CommercialFilterEngine
AmazonEligibilityValidator
DealEvaluationJdbcRepository
```

ao:

```text
AmazonDealEvaluationApplicationService
```

Nenhum limite comercial Ã© hardcoded no composition root.

---

## 30. Schema

Estado final:

```text
PostgreSQL: 18.6
Flyway: OK
Migrations: 7
Schema: versÃ£o 7
Migration pendente: nÃ£o
```

A migration mais recente Ã©:

```text
V7__commercial_filter_profile.sql
```

---

## 31. Probe externa da Amazon

A probe externa permanece separada da suÃ­te hermÃ©tica:

```text
./mvnw --batch-mode -Pexternal-probe test
```

Workflow:

```text
.github/workflows/amazon-source-probe.yml
```

Durante o fechamento da FASE 9 foi observado HTTP 503 no GitHub Actions.

A requisiÃ§Ã£o jÃ¡ utilizava:

```text
User-Agent: RaspingAmazon/1.0
```

A coleta foi endurecida com headers adicionais legÃ­timos:

```text
Accept
Accept-Language
```

TambÃ©m foi adicionada preservaÃ§Ã£o diagnÃ³stica de:

```text
HTTP status
trecho limitado do corpo de erro
```

e artifact em:

```text
target/diagnostics/
```

ApÃ³s a correÃ§Ã£o:

```text
probe local: SUCCESS
probe GitHub Actions: SUCCESS
```

Commits de referÃªncia:

```text
2a1a3f1 fix: melhora diagnostico da probe externa amazon
dd02904 fix: estabiliza probe externa da amazon
```

---

## 32. CI

Workflow:

```text
.github/workflows/ci.yml
```

Ambiente:

```text
JDK 25
PostgreSQL 18.6
Maven Wrapper
```

O workflow principal tambÃ©m foi atualizado para versÃµes atuais das GitHub Actions utilizadas pelo projeto.

Commit:

```text
060ad50 ci: atualiza actions do workflow principal
```

Resultado remoto:

```text
CI: SUCCESS
```

SuÃ­te executada no GitHub Actions:

```text
Tests run: 303
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

O aviso remanescente sobre futura migraÃ§Ã£o de:

```text
ubuntu-latest
```

para Ubuntu 26 Ã© informativo e nÃ£o bloqueia o projeto.

---

## 33. ValidaÃ§Ã£o final

Estado confirmado:

```text
SuÃ­te hermÃ©tica:
303 testes
0 falhas
0 erros
0 ignorados
BUILD SUCCESS

Probe externa:
1 teste
0 falhas
0 erros
0 ignorados
BUILD SUCCESS

CI remoto:
SUCCESS

Probe remota:
SUCCESS

PostgreSQL:
18.6

Flyway:
OK

Migrations:
7

Schema:
versÃ£o 7
```

---

## 34. CritÃ©rios de saÃ­da

| CritÃ©rio | Resultado |
|---|---|
| Perfil comercial configurÃ¡vel | CONCLUÃDO |
| Perfil versionado | CONCLUÃDO |
| ConfiguraÃ§Ã£o persistida | CONCLUÃDO |
| Desconto mÃ­nimo Ã  vista | CONCLUÃDO |
| Rating mÃ­nimo | CONCLUÃDO |
| Review count mÃ­nimo | CONCLUÃDO |
| AusÃªncia distinta de valor insuficiente | CONCLUÃDO |
| Pix reconhecido | CONCLUÃDO |
| NuPay reconhecido | CONCLUÃDO |
| CartÃ£o separado do filtro Ã  vista | CONCLUÃDO |
| CondiÃ§Ãµes comerciais no pipeline | CONCLUÃDO |
| Motor determinÃ­stico | CONCLUÃDO |
| Sem short-circuit | CONCLUÃDO |
| Seller/delivery separados dos filtros | CONCLUÃDO |
| `soldPercentage` fora dos filtros | CONCLUÃDO |
| Cinco resultados auditÃ¡veis | CONCLUÃDO |
| `filterProfileVersion` persistido | CONCLUÃDO |
| E2E fail-closed | CONCLUÃDO |
| E2E condiÃ§Ãµes comerciais | CONCLUÃDO |
| SuÃ­te hermÃ©tica | 303 / PASSOU |
| CI remoto | PASSOU |
| Probe externa local | PASSOU |
| Probe externa remota | PASSOU |

---

## 35. Limites da FASE 9

NÃ£o fazem parte desta fase:

```text
score
ranking
momentum
seleÃ§Ã£o final para publicaÃ§Ã£o
geraÃ§Ã£o de mensagem
link de associado
canais
scheduler
filas
execuÃ§Ã£o recorrente
```

A FASE 9 responde:

```text
Esta oferta possui os requisitos estruturais e
comerciais mÃ­nimos para continuar no pipeline?
```

Ela nÃ£o responde:

```text
Qual oferta aprovada deve receber maior prioridade?
```

Essa pergunta pertence Ã  FASE 10.

---

## 36. Pipeline apÃ³s a FASE 9

```text
coleta
  â†“
parser
  â†“
enriquecimento
  â”œâ”€â”€ seller
  â”œâ”€â”€ delivery
  â””â”€â”€ paymentConditions
  â†“
OfferSnapshot
  â†“
elegibilidade estrutural
  â”œâ”€â”€ SELLER_IS_AMAZON
  â””â”€â”€ DELIVERY_IS_AMAZON
  â†“
FilterProfile
  â†“
filtros comerciais
  â”œâ”€â”€ MIN_CASH_DISCOUNT
  â”œâ”€â”€ MIN_RATING
  â””â”€â”€ MIN_REVIEW_COUNT
  â†“
DealEvaluation
  â”œâ”€â”€ eligible
  â”œâ”€â”€ rejectionReason
  â”œâ”€â”€ eligibilityPolicyVersion
  â”œâ”€â”€ filterProfileVersion
  â””â”€â”€ 5 ruleResults
  â†“
PostgreSQL
```

---

## 37. Commits principais da FASE 9

```text
fe4e890  docs: registra ADR de filtros comerciais e pagamentos
882b50f  feat: adiciona perfil configuravel de filtros comerciais
333d0f8  feat: define semantica de rejeicao dos filtros comerciais
5c1343b  feat: adiciona filtros de rating e quantidade de avaliacoes
28531dc  feat: adiciona filtro de desconto a vista
eca8405  feat: adiciona motor deterministico de filtros comerciais
9dc20e9  feat: adiciona perfil de filtros comerciais persistido
041328d  feat: integra condicoes comerciais ao pipeline
dc590dc  feat: integra filtros comerciais a avaliacao de ofertas
d4af8d9  docs: consolida resultado da fase 9
59a782e  docs: atualiza estado do projeto apos fase 9
2a1a3f1  fix: melhora diagnostico da probe externa amazon
dd02904  fix: estabiliza probe externa da amazon
060ad50  ci: atualiza actions do workflow principal
```

---

## 38. PrÃ³xima fase

A prÃ³xima fase Ã©:

```text
FASE 10 â€” Score
```

Sinais jÃ¡ disponÃ­veis:

```text
discountPercentage
rating
reviewCount
soldPercentage
```

Antes da implementaÃ§Ã£o deverÃ£o ser definidos formalmente:

```text
scoreVersion
normalizaÃ§Ã£o
pesos
tratamento de null
arredondamento
limites
desempates
persistÃªncia
auditabilidade
```

A FASE 10 nÃ£o deverÃ¡ introduzir filtros ocultos.

`soldPercentage` poderÃ¡ participar como sinal de popularidade/traÃ§Ã£o, mas nÃ£o como requisito eliminatÃ³rio.

---

## 39. Registro final

```text
FASE 9 â€” MOTOR DE FILTROS COMERCIAIS CONFIGURÃVEIS

STATUS:
CONCLUÃDA

STATUS LOCAL:
CONCLUÃDO

STATUS REMOTO:
CONCLUÃDO

CI HERMÃ‰TICO:
VERDE

PROBE EXTERNA:
VERDE

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

Limites:
cash discount >= 20%
rating >= 4.3
reviewCount >= 100

soldPercentage:
COLETADO
PERSISTIDO
NÃƒO Ã‰ FILTRO
RESERVADO PARA SCORE

Testes hermÃ©ticos:
303

Falhas:
0

Erros:
0

Ignorados:
0

Probe externa:
PASSOU LOCALMENTE
PASSOU NO GITHUB ACTIONS

Build:
SUCCESS

PostgreSQL:
18.6

Flyway:
OK

Migrations:
7

Schema:
versÃ£o 7

Gate da FASE 9:
FECHADO

PrÃ³xima fase:
FASE 10 â€” Score
```
