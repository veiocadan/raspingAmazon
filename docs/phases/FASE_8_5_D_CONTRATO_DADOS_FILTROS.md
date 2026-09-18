# FASE 8.5-D — Contrato de dados para filtros

## Objetivo

Esta subfase fecha semanticamente os campos que serão consumidos pelos
filtros configuráveis da FASE 9.

Nenhum filtro deve ser implementado antes que cada campo tenha definição
clara de:

- fonte;
- semântica;
- fallback;
- política de ausência;
- contrato de aplicação;
- representação no domínio;
- persistência;
- provenance.

A responsabilidade desta fase é descrever e transportar fatos observados.
A decisão comercial continua pertencendo às fases de avaliação.

---

## 1. currentPrice

### Semântica

Principal preço comercial observado na oferta no momento da coleta.

Não representa automaticamente:

- preço à vista;
- preço Pix;
- preço parcelado;
- preço histórico;
- preço de lista.

### Fonte atual

Amazon Deals:

`product.price.priceToPay.price`

### Obrigatoriedade

Obrigatório para uma oferta ser considerada um `ParsedDeal` válido.

Quando ausente ou inválido, o produto não deve gerar `ParsedDeal`.

### Contrato de aplicação

`ParsedDeal.currentPrice`

### Domínio

`OfferSnapshot.currentPrice`

Tipo:

`Money`

### Persistência

`offer_snapshot.current_price`

### Política de ausência

Fail parse.

Uma oferta sem preço atual não possui dados comerciais mínimos suficientes
para entrar no pipeline.

---

## 2. basisPrice

### Semântica

Preço-base ou preço de referência apresentado pela fonte.

Não é sinônimo de `previousPrice`.

Não deve ser interpretado automaticamente como preço histórico anterior.

### Fonte atual

Amazon Deals:

`product.price.basisPrice.price`

### Obrigatoriedade

Opcional.

### Contrato de aplicação

`ParsedDeal.basisPrice`

### Domínio

`OfferSnapshot.basisPrice`

Tipo:

`Money`, opcional.

### Persistência

`offer_snapshot.basis_price`

### Política de ausência

`null`.

A ausência não invalida a oferta.

---

## 3. previousPrice

### Semântica

Preço anterior somente quando houver evidência explícita de histórico
ou preço comercial anterior.

`basisPrice` não pode ser copiado para `previousPrice`.

### Fonte atual

Nenhuma fonte explícita confiável foi fechada no parser Amazon Deals atual.

### Obrigatoriedade

Opcional.

### Contrato de aplicação

`ParsedDeal.previousPrice`

### Domínio

`OfferSnapshot.previousPrice`

Tipo:

`Money`, opcional.

### Persistência

`offer_snapshot.previous_price`

### Política de ausência

`null`.

Não inferir.

---

## 4. soldPercentage

### Semântica

Percentual da oferta reivindicado/vendido conforme informado pela fonte.

Representa um percentual no intervalo de 0 a 100.

### Fonte atual

Amazon Deals:

`product.dealDetails.percentClaimed`

### Obrigatoriedade

Opcional.

### Contrato de aplicação

`ParsedDeal.soldPercentage`

### Domínio

`OfferSnapshot.soldPercentage`

Tipo:

`Percentage`, opcional.

### Persistência

`offer_snapshot.sold_percentage`

### Política de ausência

`null`.

Ausência não invalida a oferta.

A decisão sobre aceitar ou rejeitar uma oferta sem esse dado pertence
ao perfil de filtros da FASE 9.

### Validação obrigatória

Valores menores que 0 ou maiores que 100 devem ser tratados como inválidos
e convertidos em ausência de dado.

O parser não deve transportar um percentual fora do domínio válido.

---

## 5. discountPercentage

### Semântica

Não existe um desconto universal único para toda a oferta.

Desconto é contextual e deve estar associado à condição comercial que
o produziu.

Exemplos:

- desconto à vista;
- desconto Pix;
- desconto ligado a um método de pagamento;
- desconto relativo a uma condição promocional específica.

### Fonte atual

A estrutura atual do parser Amazon Deals ainda não possui contrato fechado
para uma condição de desconto contextual.

### Contrato de aplicação

Não deve ser adicionado como campo universal em `ParsedDeal`.

Quando a fonte comercial correspondente for implementada, deverá produzir
`PaymentCondition`.

### Domínio

`PaymentCondition.discountPercentage`

Tipo:

`Percentage`, opcional dentro de uma condição comercial.

### Persistência

`offer_payment_condition.discount_percentage`

### Política de ausência

Lista de condições vazia ou condição sem desconto.

Nunca inferir desconto apenas a partir de:

`currentPrice` e `basisPrice`

sem que a semântica da fonte esteja explicitamente comprovada.

---

## 6. rating

### Semântica

Nota agregada de avaliação do produto observada na fonte.

### Fonte atual

Não existe contrato fechado de ponta a ponta no parser Amazon Deals atual.

A fonte precisa ser confirmada antes de ser transportada para `ParsedDeal`.

### Obrigatoriedade

Opcional.

### Contrato de aplicação desejado

`ParsedDeal.rating`

### Domínio

`OfferSnapshot.rating`

Tipo atual:

`Double`, opcional.

### Persistência

`offer_snapshot.rating`

### Política de ausência

`null`.

Ausência não deve ser transformada em zero.

Zero e ausência possuem significados diferentes.

---

## 7. reviewCount

### Semântica

Quantidade agregada de avaliações/reviews observada na fonte.

### Fonte atual

A estrutura da página Amazon possui informações relacionadas a reviews,
mas o parser Amazon Deals atual ainda não fechou uma fonte confiável
de ponta a ponta.

### Obrigatoriedade

Opcional.

### Contrato de aplicação desejado

`ParsedDeal.reviewCount`

### Domínio

`OfferSnapshot.reviewCount`

Tipo:

`Long`, opcional.

### Persistência

`offer_snapshot.review_count`

### Política de ausência

`null`.

Ausência não deve ser interpretada como zero reviews.

---

## 8. seller e delivery

### Semântica

São dados de elegibilidade estrutural e não filtros comerciais da FASE 9.

### Fonte

Página individual de produto.

### Contrato de aplicação

`SellerEvidence`

`DeliveryEvidence`

### Provenance

Persistida em:

`offer_evidence`

### Política de ausência

Fail closed.

`UNKNOWN` implica rejeição estrutural.

---

## 9. Matriz resumida

| Campo | Fonte atual | Aplicação | Domínio | Persistência | Ausência |
|---|---|---|---|---|---|
| currentPrice | Deals priceToPay | ParsedDeal | OfferSnapshot.currentPrice | offer_snapshot.current_price | invalida ParsedDeal |
| basisPrice | Deals basisPrice | ParsedDeal | OfferSnapshot.basisPrice | offer_snapshot.basis_price | null |
| previousPrice | sem fonte explícita | ParsedDeal | OfferSnapshot.previousPrice | offer_snapshot.previous_price | null |
| soldPercentage | dealDetails.percentClaimed | ParsedDeal | OfferSnapshot.soldPercentage | offer_snapshot.sold_percentage | null |
| discountPercentage | condição comercial contextual | PaymentCondition | PaymentCondition | offer_payment_condition.discount_percentage | condição ausente |
| rating | ainda não fechado | desejado em ParsedDeal | OfferSnapshot.rating | offer_snapshot.rating | null |
| reviewCount | ainda não fechado | desejado em ParsedDeal | OfferSnapshot.reviewCount | offer_snapshot.review_count | null |
| seller | página de produto | SellerEvidence | SellerType | offer_evidence | UNKNOWN |
| delivery | página de produto | DeliveryEvidence | DeliveryType | offer_evidence | UNKNOWN |

---

## 10. Regras arquiteturais

1. Parser descreve fatos observados; não decide elegibilidade ou filtro.
2. Ausência de dado deve permanecer ausência, salvo regra estrutural
   explicitamente fail-closed.
3. `basisPrice` e `previousPrice` são conceitos distintos.
4. Desconto não é propriedade universal do snapshot.
5. Rating ausente não significa rating zero.
6. Review count ausente não significa zero reviews.
7. Percentuais inválidos não devem atravessar a fronteira do parser.
8. Toda nova fonte deve possuir provenance identificável.
9. A FASE 9 só poderá consumir campos cujo contrato esteja fechado.
10. Nenhum valor deve ser inferido apenas para preencher campo ausente.

---

## Próximos passos técnicos

A implementação desta subfase será feita em etapas:

1. reforçar validação de `soldPercentage`;
2. transportar `rating` e `reviewCount` somente depois de identificar
   fontes concretas na estrutura Amazon Deals;
3. manter desconto em `PaymentCondition`;
4. adicionar testes de contrato para valores ausentes e inválidos;
5. só então liberar esses campos para os filtros configuráveis.