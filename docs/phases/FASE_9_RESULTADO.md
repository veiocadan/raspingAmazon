# FASE 9 — RESULTADO CONSOLIDADO

**Projeto:** Rasping Amazon
**Fase:** 9 — Motor de filtros comerciais configuráveis
**Data:** 20/09/2026
**Status:** CONCLUÍDA LOCALMENTE E VERSIONADA — validação remota do CI deste fechamento deve ser confirmada antes da abertura formal da FASE 10

---

## 1. Objetivo

A FASE 9 teve como objetivo implementar o motor de filtros comerciais configuráveis utilizado para decidir se uma oferta estruturalmente válida possui dados comerciais mínimos suficientes para continuar no fluxo de seleção.

A fase foi construída sobre os contratos consolidados na FASE 8.5, preservando a separação entre:

```text
elegibilidade estrutural Amazon
```

e:

```text
filtros comerciais configuráveis
```

A implementação final passou a avaliar:

```text
desconto mínimo à vista
rating mínimo
quantidade mínima de avaliações
```

sem incorporar score, ranking ou momentum.

---

## 2. Princípios preservados

A FASE 9 preservou os princípios arquiteturais já estabelecidos:

* domínio independente de PostgreSQL, JDBC, HTML e HTTP;
* configuração comercial fora do código;
* versionamento explícito das regras aplicadas;
* valores ausentes não são inventados;
* parser descreve fatos, não decide aprovação;
* seller e delivery permanecem elegibilidade estrutural;
* filtros comerciais permanecem uma etapa distinta;
* todas as regras avaliadas permanecem auditáveis;
* ordem de avaliação determinística;
* ausência de informação comercial é semanticamente distinta de valor abaixo do limite;
* migrations já aplicadas não são alteradas;
* evolução do schema ocorre somente por novas migrations;
* score e momentum permanecem fora desta fase.

---

## 3. Decisão arquitetural registrada

Antes da implementação foi criado o ADR:

```text
docs/adr/0001-semantica-filtros-comerciais-e-apresentacao-pagamentos.md
```

O ADR registra as decisões relacionadas às FASES 8, 8.5, 9 e 10.

Entre as decisões consolidadas estão:

```text
seller/delivery Amazon
→ elegibilidade estrutural

desconto à vista
rating
reviewCount
→ filtros comerciais

soldPercentage
→ não é filtro
→ reservado para score da FASE 10
```

Também foi formalizada a distinção entre:

```text
seleção para filtro
```

e:

```text
seleção para publicação
```

Esses dois conceitos não devem ser confundidos.

Commit de referência:

```text
fe4e890 docs: registra ADR de filtros comerciais e pagamentos
```

---

## 4. `soldPercentage`

A proposta inicial da FASE 9 considerava:

```text
minSoldPercentage = 50
```

Essa regra foi removida do motor de filtros.

A razão é semântica: o mural de ofertas da Amazon não possui comportamento suficientemente regular para transformar `soldPercentage` em requisito eliminatório confiável.

A decisão final é:

```text
soldPercentage
→ coletado
→ normalizado
→ persistido
→ auditável
→ NÃO rejeita oferta na FASE 9
```

O campo fica reservado para:

```text
FASE 10 — Score
```

onde poderá atuar como sinal de:

```text
popularidade
tração
demanda observada
```

Ausência de `soldPercentage` também não equivale a zero.

---

## 5. `FilterProfile`

Foi criado o conceito:

```text
FilterProfile
```

Ele representa uma configuração comercial versionada.

Campos atuais:

```text
version
minCashDiscountPercentage
minRating
minReviewCount
```

O perfil não possui:

```text
minSoldPercentage
```

porque esse conceito foi reservado para score.

Também não possui:

```text
onlyAmazon
```

porque seller e delivery pertencem à política estrutural de elegibilidade da FASE 8.

A configuração inicial formal da FASE 9 é:

```text
version = COMMERCIAL_FILTER_V1

minCashDiscountPercentage = 20
minRating = 4.3
minReviewCount = 100
```

Commit de referência:

```text
882b50f feat: adiciona perfil configuravel de filtros comerciais
```

---

## 6. Configuração persistida

Os limites comerciais não são codificados no composition root.

Foi criada a migration:

```text
V7__commercial_filter_profile.sql
```

Ela introduziu:

```text
filter_profile
```

com os campos:

```text
version
min_cash_discount_percentage
min_rating
min_review_count
active
created_at
```

O banco protege:

* versão única;
* percentual entre 0 e 100;
* rating entre 0 e 5;
* quantidade de avaliações não negativa;
* no máximo um perfil ativo.

O perfil inicial inserido pela migration é:

```text
COMMERCIAL_FILTER_V1
20.00
4.30
100
active = true
```

Perfis utilizados historicamente não devem ser alterados.

Uma mudança futura deve criar nova versão:

```text
COMMERCIAL_FILTER_V2
COMMERCIAL_FILTER_V3
...
```

Commit de referência:

```text
9dc20e9 feat: adiciona perfil de filtros comerciais persistido
```

---

## 7. Porta de configuração

A camada de aplicação utiliza:

```text
FilterProfileProvider
```

A aplicação não conhece a origem concreta da configuração.

A implementação atual é:

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

Assim o motor comercial pode evoluir sem acoplamento ao PostgreSQL.

---

## 8. Semântica das rejeições

A FASE 9 passou a distinguir informação ausente de informação presente mas insuficiente.

Para desconto:

```text
desconto ausente
→ CASH_DISCOUNT_UNAVAILABLE

desconto presente abaixo do limite
→ CASH_DISCOUNT_BELOW_MINIMUM
```

Para rating:

```text
rating ausente/inválido
→ RATING_UNAVAILABLE

rating presente abaixo do limite
→ RATING_BELOW_MINIMUM
```

Para quantidade de avaliações:

```text
reviewCount ausente/inválido
→ REVIEW_COUNT_UNAVAILABLE

reviewCount presente abaixo do limite
→ REVIEW_COUNT_BELOW_MINIMUM
```

Isso evita transformar ausência em zero e preserva uma explicação mais correta da decisão.

Commit de referência:

```text
333d0f8 feat: define semantica de rejeicao dos filtros comerciais
```

---

## 9. Rating mínimo

Foi implementada:

```text
MinRatingRule
```

A regra recebe:

```text
OfferSnapshot
FilterProfile
```

e retorna:

```text
EvaluationRuleResult
```

Comportamento:

```text
rating ausente
→ falha
→ RATING_UNAVAILABLE

rating inválido
→ falha
→ RATING_UNAVAILABLE

rating < minRating
→ falha
→ RATING_BELOW_MINIMUM

rating >= minRating
→ passa
```

O intervalo válido do rating permanece:

```text
0..5
```

---

## 10. Quantidade mínima de avaliações

Foi implementada:

```text
MinReviewCountRule
```

Comportamento:

```text
reviewCount ausente
→ REVIEW_COUNT_UNAVAILABLE

reviewCount negativo
→ REVIEW_COUNT_UNAVAILABLE

reviewCount < minReviewCount
→ REVIEW_COUNT_BELOW_MINIMUM

reviewCount >= minReviewCount
→ passa
```

A regra não converte ausência em zero.

---

## 11. Desconto mínimo à vista

A regra comercial utiliza:

```text
minCashDiscountPercentage
```

O filtro considera somente descontos explicitamente observados em condições:

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

Não participam do filtro:

```text
CREDIT_CARD
parcelamento
descontos inferidos
diferença matemática entre preços
promoções sem contexto reconhecido
```

---

## 12. Seleção do melhor desconto à vista

Foi criado:

```text
BestCashDiscountSelector
```

Fluxo:

```text
PaymentCondition[]
        ↓
seleciona somente CASH
        ↓
seleciona somente PIX / NUPAY_ADDITIONAL_LIMIT
        ↓
exige desconto explícito
        ↓
seleciona maior percentual
        ↓
CashDiscountObservation
```

Se Pix e NuPay apresentarem o mesmo percentual, os métodos são preservados de forma determinística.

A regra não soma descontos.

Exemplo:

```text
Pix = 20%
NuPay = 25%
```

Resultado do filtro:

```text
25%
```

Não:

```text
45%
```

---

## 13. `MinCashDiscountRule`

Foi implementada:

```text
MinCashDiscountRule
```

Comportamento:

```text
nenhuma condição CASH reconhecida com desconto explícito
→ CASH_DISCOUNT_UNAVAILABLE

melhor desconto < minCashDiscountPercentage
→ CASH_DISCOUNT_BELOW_MINIMUM

melhor desconto >= minCashDiscountPercentage
→ regra aprovada
```

Commit de referência:

```text
28531dc feat: adiciona filtro de desconto a vista
```

---

## 14. Política de pagamento para publicação

A FASE 9 não implementa publicação, mas o ADR registrou as regras necessárias para preservar a semântica futura.

Para condições à vista:

```text
NuPay > Pix
→ publicação futura pode mostrar NuPay + Pix

Pix > NuPay
→ publicação futura mostra Pix entre os métodos à vista

Pix == NuPay
→ publicação futura prefere Pix

somente um disponível
→ utiliza o disponível
```

A razão para não preferir NuPay quando não existe vantagem econômica é sua disponibilidade mais restrita.

Essa regra pertence à apresentação/publicação e não altera o filtro.

---

## 15. Cartão de crédito

Condições de cartão continuam separ
