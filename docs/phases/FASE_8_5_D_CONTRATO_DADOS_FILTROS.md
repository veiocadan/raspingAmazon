# FASE 8.5-D — Contrato de dados para filtros

## Atualização D2

A investigação do fixture real confirmou que `rating` e `reviewCount`
estão dentro da mesma estrutura `productSearchResponse.products[]`
já consumida pelo parser Amazon Deals.

### rating

Fonte:

`product.customerReviews.rating.shortDisplayString`

Semântica:

nota agregada do produto apresentada pela Amazon Deals.

Normalização:

o valor é localizado, por exemplo `4,7`, e deve ser convertido para
valor decimal.

Intervalo válido:

0 a 5.

Política de ausência:

`null`.

Valores malformados ou fora de 0..5 também resultam em `null`.

Contrato de aplicação:

`ParsedDeal.rating`

Domínio:

`OfferSnapshot.rating`

Persistência:

`offer_snapshot.rating`

---

### reviewCount

Fonte:

`product.customerReviews.count.value`

Semântica:

quantidade agregada de avaliações/reviews do produto.

Normalização:

usar o campo numérico `value`, nunca `displayString`, para não depender
de separadores localizados.

Intervalo válido:

inteiro maior ou igual a zero.

Política de ausência:

`null`.

Valores negativos ou não inteiros resultam em `null`.

Contrato de aplicação:

`ParsedDeal.reviewCount`

Domínio:

`OfferSnapshot.reviewCount`

Persistência:

`offer_snapshot.review_count`

---

## Regras mantidas da D1

- `soldPercentage` vem de `dealDetails.percentClaimed`.
- percentuais válidos estão entre 0 e 100.
- `basisPrice` não é `previousPrice`.
- ausência de rating não significa nota zero.
- ausência de reviewCount não significa zero reviews.
- o parser descreve fatos observados e não aplica filtros.
- desconto permanece contextual e pertence a `PaymentCondition`.
