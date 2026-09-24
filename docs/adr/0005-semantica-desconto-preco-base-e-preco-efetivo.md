# ADR-0005 — Semântica do desconto contra preço-base e preço efetivo

- **Status:** Aceita
- **Data:** 2026-09-23
- **Projeto:** Rasping Amazon
- **Fases relacionadas:** FASE 0 v2, FASE 3 v2, FASE 9, FASE 10, FASE 11 e FASE 13
- **Substitui parcialmente:** ADR-0001 — seções 3, 4 e 7, exclusivamente quanto à semântica do desconto usado pelo filtro comercial
- **Não substitui:** política de apresentação de Pix/NuPay, seleção de parcelamento, separação entre `basisPrice` e `previousPrice`, nem a semântica histórica do `SCORE_V1`

## Contexto

A ADR-0001 definiu que o filtro comercial de desconto mínimo utilizaria apenas o maior desconto à vista explicitamente observado em uma `PaymentCondition` reconhecida, como Pix ou NuPay.

Essa interpretação foi implementada por `MIN_CASH_DISCOUNT` e por `minCashDiscountPercentage`.

Durante a validação live da geração de publicação com páginas de produto renderizadas, foi observado um caso que tornou explícita uma diferença semântica importante:

```text
basisPrice             = R$ 3.599,00
currentPrice           = R$ 1.898,00
cash price observado   = R$ 1.708,20
promoção de pagamento  = 10% off à vista no Pix ou NuPay
```

Pela interpretação anterior, o filtro utilizava `10%` e rejeitava a oferta quando o limiar configurado era `20%`.

Entretanto, o requisito comercial correto é avaliar a redução do **preço efetivamente pagável** em relação ao **preço-base/riscado**. A promoção adicional de 10% associada a Pix/NuPay é um fato comercial distinto e não representa o desconto que deve ser comparado com o limiar do filtro.

A mesma situação ocorre quando não existe diferença de preço entre pagamento à vista e cartão. Ainda assim, pode existir uma redução comercial relevante entre o preço atual da oferta e o preço-base/riscado.

Portanto, é necessário separar definitivamente dois conceitos:

1. **desconto de condição de pagamento**, explicitamente informado pela fonte em uma `PaymentCondition`;
2. **desconto contra preço-base**, calculado pelo domínio a partir de dois preços explicitamente observados.

## Decisão

### 1. O filtro comercial deixa de usar o desconto explícito da `PaymentCondition`

O filtro eliminatório de preço não utilizará mais `PaymentCondition.discountPercentage` como seu valor observado.

Esse percentual continuará existindo e continuará sendo preservado quando explicitamente informado pela Amazon, por exemplo:

```text
10% off à vista no Pix ou NuPay
```

Ele poderá continuar sendo utilizado por apresentação/publicação e por componentes cuja semântica histórica dependa especificamente de promoção de pagamento.

Ele não será mais o percentual comparado com o limite de 20% do filtro comercial.

### 2. O novo conceito do filtro será `basisDiscountPercentage`

O filtro comercial utilizará um percentual derivado denominado:

```text
basisDiscountPercentage
```

Esse percentual representa a redução entre:

```text
basisPrice
```

 e:

```text
effectivePrice
```

A fórmula será:

```text
basisDiscountPercentage =
    ((basisPrice - effectivePrice) / basisPrice) * 100
```

O cálculo deverá utilizar `BigDecimal`.

A escala operacional do percentual derivado será:

```text
scale = 4
roundingMode = HALF_UP
```

O valor calculado é um **resultado derivado de regra de negócio**. Ele não deverá ser apresentado como se fosse um percentual explicitamente declarado pela Amazon.

### 3. `basisPrice` continua significando preço-base/riscado

`basisPrice` continua representando o preço-base/lista explicitamente observado na fonte.

Esta ADR não autoriza interpretar `basisPrice` como:

```text
previousPrice
```

nem como preço histórico anterior.

A separação estabelecida anteriormente continua válida.

### 4. Definição de `effectivePrice`

Para o cálculo do desconto contra `basisPrice`, o domínio deverá selecionar um preço efetivo de forma determinística.

A ordem será:

#### 4.1. Preço CASH explicitamente observado

Quando existir uma ou mais `PaymentCondition` do tipo `CASH` com preço numérico explicitamente observado, será utilizado o menor preço CASH válido.

Exemplo:

```text
currentPrice = 1.898,00
CASH Pix/NuPay = 1.708,20

effectivePrice = 1.708,20
```

A escolha do menor preço representa a melhor condição de pagamento imediato explicitamente disponível.

#### 4.2. Ausência de preço CASH diferenciado

Quando não existir `PaymentCondition.CASH` com preço numérico, o filtro utilizará:

```text
currentPrice
```

como `effectivePrice`.

Isso **não significa** afirmar que `currentPrice` é um preço Pix, NuPay ou qualquer outra modalidade específica.

Significa apenas que, na ausência de um preço à vista diferenciado explicitamente observado, o preço corrente da oferta é o valor disponível para comparação com o preço-base.

### 5. Ausência e consistência dos preços

O desconto contra preço-base será considerado indisponível quando:

- `basisPrice` estiver ausente;
- `basisPrice <= 0`;
- `effectivePrice` estiver ausente;
- `effectivePrice < 0`;
- `effectivePrice > basisPrice`.

Quando:

```text
effectivePrice == basisPrice
```

o desconto será validamente:

```text
0%
```

Ausência ou inconsistência não deverá ser convertida em zero.

### 6. Novo código de regra

A regra comercial vigente passará a utilizar o código:

```text
MIN_BASIS_DISCOUNT
```

O código histórico:

```text
MIN_CASH_DISCOUNT
```

não deverá ser removido do vocabulário persistente, pois avaliações históricas podem ter sido gravadas com essa identificação.

Ele deixa de ser produzido por novas avaliações quando `COMMERCIAL_FILTER_V2` estiver ativo.

### 7. Novos motivos de rejeição

Serão introduzidos:

```text
BASIS_DISCOUNT_UNAVAILABLE
BASIS_DISCOUNT_BELOW_MINIMUM
```

Os motivos históricos:

```text
CASH_DISCOUNT_UNAVAILABLE
CASH_DISCOUNT_BELOW_MINIMUM
```

permanecerão no enum para leitura e auditoria de avaliações antigas.

### 8. Novo perfil comercial versionado

A mudança altera o significado do filtro e, portanto, não poderá reutilizar semanticamente:

```text
COMMERCIAL_FILTER_V1
```

Será criado:

```text
COMMERCIAL_FILTER_V2
```

com:

```text
minBasisDiscountPercentage = 20.00
minRating                  = 4.30
minReviewCount             = 100
```

`COMMERCIAL_FILTER_V1` permanecerá armazenado como histórico e será desativado.

### 9. Persistência do perfil não reescreverá a semântica histórica

A coluna histórica:

```text
min_cash_discount_percentage
```

não será renomeada para `min_basis_discount_percentage`, pois isso faria registros antigos parecerem possuir uma semântica que nunca tiveram.

Será criada uma nova coluna:

```text
min_basis_discount_percentage
```

`COMMERCIAL_FILTER_V1` preservará seu valor histórico em `min_cash_discount_percentage`.

`COMMERCIAL_FILTER_V2` utilizará `min_basis_discount_percentage`.

A coluna antiga poderá permanecer `NULL` para perfis novos após a migration correspondente.

### 10. Auditoria do valor derivado

O resultado de `MIN_BASIS_DISCOUNT` deverá preservar, no `observedValue`, informação suficiente para explicar o cálculo.

Formato auditável recomendado:

```text
DISCOUNT=52.5368|BASIS=3599.00|EFFECTIVE=1708.20|SOURCE=CASH_CONDITION
```

ou, quando não houver preço CASH diferenciado:

```text
DISCOUNT=40.0075|BASIS=5499.00|EFFECTIVE=3298.99|SOURCE=CURRENT_PRICE
```

O formato faz parte da explicabilidade da regra, não da apresentação ao usuário final.

### 11. `PaymentCondition.discountPercentage` permanece contextual

Esta ADR não transforma `discountPercentage` em atributo universal de `OfferSnapshot`.

Exemplos como:

```text
10% off à vista no Pix ou NuPay
```

continuam sendo fatos contextuais de uma condição comercial.

O sistema continuará proibido de inventar um percentual de promoção como se ele tivesse sido declarado pela Amazon.

O que passa a ser permitido é calcular um percentual **derivado para uma regra de negócio**, desde que:

- `basisPrice` seja observado;
- `effectivePrice` seja observado;
- a fórmula seja explícita;
- os dois preços de origem sejam preservados para auditoria;
- o resultado seja identificado como cálculo do sistema.

### 12. Publicação não é alterada por esta ADR

A política de publicação continua separada do filtro.

A apresentação poderá continuar exibindo, quando houver evidência:

- preço atual;
- preço-base;
- preço à vista;
- promoção explícita Pix/NuPay;
- parcelamento.

Esta ADR não determina que `basisDiscountPercentage` deva ser publicado como se fosse um desconto informado pela Amazon.

Caso o produto venha a exibir esse percentual calculado na publicação, isso deverá ser uma decisão explícita e separada.

### 13. `BestCashDiscountSelector` deixa de ser dependência do filtro V2

`BestCashDiscountSelector` preserva a semântica de desconto de condição de pagamento.

Ele poderá continuar sendo utilizado por componentes que realmente precisem dessa informação explícita.

`MIN_BASIS_DISCOUNT` deverá utilizar um componente próprio para calcular o desconto contra preço-base, evitando misturar as duas semânticas.

### 14. Histórico e momentum não serão reinterpretados silenciosamente

Campos e cálculos históricos que atualmente significam variação do desconto CASH explícito não deverão passar a significar desconto contra preço-base sem nova versão semântica.

Se houver necessidade de momentum baseado em `basisDiscountPercentage`, isso deverá ser introduzido por evolução versionada própria.

### 15. `SCORE_V1` não será reinterpretado

A ADR-0002 definiu `CASH_DISCOUNT` no `SCORE_V1` como desconto à vista explicitamente observado.

Esta ADR não altera retroativamente esse significado.

Contudo, `COMMERCIAL_FILTER_V2` poderá aprovar ofertas que não possuam desconto CASH explícito, desde que possuam desconto suficiente entre `basisPrice` e `effectivePrice`.

Portanto, `COMMERCIAL_FILTER_V2` não deverá ser ativado operacionalmente em um pipeline que ainda exija obrigatoriamente `CASH_DISCOUNT` explícito para calcular `SCORE_V1`.

Antes da ativação operacional completa, deverá existir uma versão de score compatível com a nova semântica comercial, sem reescrever `SCORE_V1`.

A evolução recomendada será uma nova versão, por exemplo:

```text
SCORE_V2
```

formalizada separadamente.

## Exemplo principal

Dados observados:

```text
basisPrice   = 3599.00
currentPrice = 1898.00
cashPrice    = 1708.20
cashPromo    = 10%
```

A promoção explícita continua sendo:

```text
10% à vista no Pix ou NuPay
```

O preço efetivo para o filtro é:

```text
1708.20
```

O filtro calcula:

```text
((3599.00 - 1708.20) / 3599.00) * 100
```

O resultado calculado é comparado ao limiar:

```text
minBasisDiscountPercentage = 20
```

A existência da promoção explícita de 10% não reduz nem substitui o desconto derivado contra `basisPrice`.

## Consequências positivas

- alinha o filtro com o requisito comercial real;
- ofertas com forte redução contra o preço-base deixam de ser rejeitadas apenas porque Pix e cartão possuem o mesmo preço;
- preserva separadamente promoção de pagamento e desconto contra preço-base;
- mantém `basisPrice` distinto de `previousPrice`;
- torna o cálculo reproduzível e auditável;
- evita reinterpretar avaliações históricas;
- permite evolução segura de filtro e score por versões.

## Custos e impactos

Será necessário:

- introduzir `BasisDiscountObservation`;
- introduzir um calculador/seletor de preço efetivo;
- substituir `MinCashDiscountRule` no motor vigente por `MinBasisDiscountRule`;
- adicionar `MIN_BASIS_DISCOUNT`;
- adicionar novos motivos de rejeição;
- evoluir `FilterProfile` para `minBasisDiscountPercentage`;
- criar nova migration de schema/configuração;
- ativar `COMMERCIAL_FILTER_V2` somente junto de score compatível;
- atualizar testes unitários, integração e documentação;
- preservar tipos e códigos históricos para leitura de dados antigos.

## Alternativas consideradas

### Continuar usando o percentual explícito de Pix/NuPay

Rejeitada.

Esse percentual mede a vantagem de uma modalidade de pagamento sobre outra referência comercial, não necessariamente a redução total da oferta em relação ao preço-base.

### Calcular sempre usando `currentPrice`

Rejeitada.

Quando existe um preço CASH explicitamente menor, ignorá-lo produziria uma avaliação mais fraca do benefício efetivamente disponível à vista.

### Exigir obrigatoriamente uma `PaymentCondition.CASH`

Rejeitada.

Quando o preço à vista e o preço corrente são iguais, a ausência de uma condição CASH diferenciada não elimina a redução existente contra o preço-base.

### Renomear a coluna histórica existente

Rejeitada.

Isso alteraria retrospectivamente a interpretação de `COMMERCIAL_FILTER_V1`.

### Alterar silenciosamente `SCORE_V1`

Rejeitada.

A versão faz parte do contrato histórico de explicabilidade e reprodutibilidade do score.

## Ordem de implementação

A implementação deverá seguir esta sequência:

1. adicionar esta ADR;
2. introduzir o domínio do desconto contra `basisPrice` e seus testes;
3. introduzir `MinBasisDiscountRule` e seus testes;
4. atualizar `CommercialFilterEngine` para a nova regra;
5. preservar códigos/motivos históricos e adicionar os novos códigos;
6. evoluir `FilterProfile` e sua persistência;
7. criar migration com `COMMERCIAL_FILTER_V2`, sem ativá-lo antes da compatibilidade do score;
8. criar e formalizar score compatível com o novo filtro;
9. ativar atomicamente as novas versões de filtro e score;
10. executar suíte hermética completa;
11. repetir o diagnóstico live com rollback;
12. somente depois prosseguir para a correção de rating/review.

## Regra de evolução desta ADR

Esta ADR registra uma correção de semântica comercial descoberta por validação live.

A ADR-0001 permanece preservada como registro histórico da decisão anterior.

Novas mudanças na definição de preço-base, preço efetivo, fórmula de desconto ou política de fallback deverão ser registradas em nova ADR, sem reescrever silenciosamente esta decisão.
