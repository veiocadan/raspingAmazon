# ADR-0006 — Semântica do SCORE_V2 com desconto contra preço-base

- **Status:** Proposta para aceitação junto da ativação de `COMMERCIAL_FILTER_V2`
- **Data:** 2026-09-23
- **Projeto:** Rasping Amazon
- **Fases relacionadas:** FASE 10, FASE 11, FASE 13 e correção semântica pós-FASE 13
- **Complementa:** ADR-0002 — Semântica do score, ranking e explicabilidade
- **Depende de:** ADR-0005 — Semântica do desconto contra preço-base e preço efetivo
- **Não reinterpreta:** `SCORE_V1`, `CASH_DISCOUNT`, avaliações históricas, histórico/momentum da FASE 11 nem promoções explícitas de `PaymentCondition`

## Contexto

A ADR-0002 definiu o `SCORE_V1` utilizando quatro fatores:

```text
SOLD_PERCENTAGE
CASH_DISCOUNT
RATING
REVIEW_COUNT
```

Naquele contrato, `CASH_DISCOUNT` significa o desconto à vista explicitamente observado em uma condição comercial reconhecida, por exemplo:

```text
10% off à vista no Pix ou NuPay
```

A ADR-0005 corrigiu uma semântica diferente: o filtro comercial mínimo de preço deve comparar o preço efetivamente pagável com o `basisPrice`, utilizando o percentual derivado:

```text
basisDiscountPercentage =
    ((basisPrice - effectivePrice) / basisPrice) * 100
```

Essa alteração permite que `COMMERCIAL_FILTER_V2` aprove ofertas sem promoção CASH explícita, desde que a redução contra o preço-base seja suficiente.

Exemplo:

```text
basisPrice   = 5499.00
currentPrice = 3298.99
CASH explícito = ausente

basisDiscountPercentage = 40.0075%
```

Essa oferta pode passar por `MIN_BASIS_DISCOUNT`, mas não possui um valor válido para o fator histórico `CASH_DISCOUNT` do `SCORE_V1`.

Portanto, ativar `COMMERCIAL_FILTER_V2` mantendo `SCORE_V1` como score obrigatório criaria uma inconsistência arquitetural: uma oferta poderia ser aprovada pelo filtro e falhar ao construir o input de score.

É necessária uma nova versão de score compatível com a nova semântica comercial.

## Decisão

### 1. Será criada a versão `SCORE_V2`

A nova versão operacional será identificada como:

```text
SCORE_V2
```

`SCORE_V1` permanecerá imutável e interpretável historicamente.

Nenhuma avaliação persistida com:

```text
score_version = SCORE_V1
```

será reinterpretada segundo esta ADR.

### 2. `CASH_DISCOUNT` será substituído por `BASIS_DISCOUNT` no SCORE_V2

O `SCORE_V2` utilizará os fatores:

```text
SOLD_PERCENTAGE
BASIS_DISCOUNT
RATING
REVIEW_COUNT
```

O fator:

```text
CASH_DISCOUNT
```

continua existindo no vocabulário histórico e continua significando exclusivamente o desconto explícito de condição de pagamento utilizado pelo `SCORE_V1`.

O novo fator:

```text
BASIS_DISCOUNT
```

representa exclusivamente o `basisDiscountPercentage` definido pela ADR-0005.

Os dois códigos não são sinônimos e não deverão ser reutilizados um no lugar do outro.

### 3. Pesos do SCORE_V2

Para manter a distribuição econômica do `SCORE_V1` sem reponderar os demais sinais, o peso anteriormente reservado ao desconto será transferido semanticamente para `BASIS_DISCOUNT`:

```text
SOLD_PERCENTAGE = 30
BASIS_DISCOUNT  = 25
RATING          = 20
REVIEW_COUNT    = 15
```

A soma ativa continuará sendo:

```text
90
```

Os 10 pontos não utilizados continuarão sem redistribuição.

### 4. Normalização de `BASIS_DISCOUNT`

`basisDiscountPercentage` já pertence ao intervalo de domínio:

```text
0 <= basisDiscountPercentage <= 100
```

Quando disponível:

```text
normalizedBasisDiscount = basisDiscountPercentage
```

Exemplo:

```text
basisDiscountPercentage = 52.5368
normalizedValue         = 52.5368
weight                  = 25
contribution            = 13.1342
```

A contribuição seguirá a mesma fórmula geral:

```text
contribution = normalizedValue / 100 * weight
```

O arredondamento final continuará seguindo a política já definida para o score:

```text
scale = 4
roundingMode = HALF_UP
```

### 5. Disponibilidade de `BASIS_DISCOUNT`

O `SCORE_V2` somente será calculado após aprovação de `COMMERCIAL_FILTER_V2`.

Consequentemente, uma oferta elegível sob `COMMERCIAL_FILTER_V2` deve possuir um `BasisDiscountObservation` válido.

Se a camada de score receber uma oferta considerada elegível, mas não conseguir reconstruir `basisDiscountPercentage`, isso representa uma inconsistência interna e deverá falhar explicitamente.

Ausência não deverá ser convertida em zero.

### 6. Fontes do valor bruto

O valor bruto persistido no fator `BASIS_DISCOUNT` será:

```text
basisDiscountPercentage
```

A explicação detalhada da origem:

```text
basisPrice
effectivePrice
effectivePriceSource
```

permanece preservada pela regra `MIN_BASIS_DISCOUNT` em seu `observedValue` e pelos dados do snapshot/condições persistidas.

A tabela de fatores do score continuará armazenando o valor percentual necessário para reconstrução matemática do score.

### 7. `ScoreFactorCode` será evoluído sem remover códigos históricos

O enum deverá conter:

```text
SOLD_PERCENTAGE
CASH_DISCOUNT
BASIS_DISCOUNT
RATING
REVIEW_COUNT
```

`CASH_DISCOUNT` permanece necessário para reconstruir avaliações `SCORE_V1`.

`BASIS_DISCOUNT` será utilizado por novas avaliações `SCORE_V2`.

### 8. `ScoreProfile` passa a representar qual semântica de desconto está ativa

A persistência de `score_profile` não renomeará a coluna histórica:

```text
cash_discount_weight
```

Será adicionada:

```text
basis_discount_weight
```

Para as versões existentes:

```text
SCORE_V1
cash_discount_weight  = 25
basis_discount_weight = NULL
```

Para a nova versão:

```text
SCORE_V2
cash_discount_weight  = NULL
basis_discount_weight = 25
```

Um perfil deverá possuir exatamente uma das duas semânticas de desconto configurada.

A soma de pesos deverá utilizar os pesos efetivamente configurados, tratando a coluna de desconto não aplicável como ausente, e não como evidência de valor zero.

### 9. Compatibilidade do objeto `ScoreProfile`

O domínio deverá preservar a leitura de perfis `SCORE_V1` e permitir perfis `SCORE_V2`.

O perfil deverá expor deterministicamente qual fator de desconto está ativo.

Exemplo conceitual:

```text
SCORE_V1 -> CASH_DISCOUNT
SCORE_V2 -> BASIS_DISCOUNT
```

O motor não deverá deduzir essa escolha pelo nome textual da versão quando os pesos versionados já contêm informação suficiente.

### 10. `ScoreInput` não deverá misturar as duas semânticas

O input entregue ao motor deverá conter o percentual correspondente ao fator de desconto ativo no perfil.

Uma avaliação `SCORE_V1` deverá continuar usando promoção CASH explícita.

Uma avaliação `SCORE_V2` deverá usar `basisDiscountPercentage`.

A camada de aplicação poderá reconstruir o valor necessário a partir do `OfferSnapshot` conforme o perfil ativo, mas não deverá substituir silenciosamente uma semântica pela outra.

### 11. `ScoreEngine` deverá produzir somente os fatores da versão ativa

Para `SCORE_V1`, a ordem permanece:

```text
0 SOLD_PERCENTAGE
1 CASH_DISCOUNT
2 RATING
3 REVIEW_COUNT
```

Para `SCORE_V2`, a ordem será:

```text
0 SOLD_PERCENTAGE
1 BASIS_DISCOUNT
2 RATING
3 REVIEW_COUNT
```

Uma avaliação não deverá persistir simultaneamente `CASH_DISCOUNT` e `BASIS_DISCOUNT` na mesma versão enquanto esta ADR estiver vigente.

### 12. Persistência histórica dos fatores não exige nova tabela

A tabela:

```text
deal_evaluation_score_factor
```

já persiste `factor_code` como texto e permite novos códigos sem reescrever fatores antigos.

`BASIS_DISCOUNT` poderá ser persistido como um novo valor de `factor_code`.

As constraints existentes de unicidade por avaliação e código continuam válidas.

### 13. Ativação coordenada com `COMMERCIAL_FILTER_V2`

`COMMERCIAL_FILTER_V2` e `SCORE_V2` deverão ser ativados na mesma migration operacional.

A migration deverá:

1. preservar `COMMERCIAL_FILTER_V1`;
2. preservar `SCORE_V1`;
3. adicionar a configuração necessária para desconto contra base;
4. inserir `COMMERCIAL_FILTER_V2`;
5. inserir `SCORE_V2`;
6. desativar os perfis V1;
7. ativar os perfis V2 na mesma transação Flyway.

Isso evita qualquer janela em que o filtro V2 esteja ativo com score V1 obrigatório, ou vice-versa.

### 14. `COMMERCIAL_FILTER_V1` e `SCORE_V1` continuam legíveis

Repositories deverão continuar conseguindo reconstruir perfis históricos quando consultados explicitamente no futuro.

A consulta operacional `activeProfile()` retornará somente a versão ativa.

A mudança de versão não autoriza alteração retroativa de avaliações já persistidas.

### 15. Histórico e momentum permanecem fora desta mudança

Esta ADR não altera o significado de métricas históricas já baseadas em promoção CASH explícita.

Em particular, `SnapshotEvolutionCalculator` não deverá passar silenciosamente a interpretar `cashDiscountDelta` como variação de `basisDiscountPercentage`.

Uma futura métrica temporal baseada em `BASIS_DISCOUNT` exigirá evolução semântica própria.

### 16. Publicação permanece independente do score

`BASIS_DISCOUNT` no score não obriga a publicação a exibir o percentual calculado.

A publicação poderá continuar apresentando os fatos comerciais já definidos:

- preço atual;
- preço-base;
- preço à vista, quando disponível;
- promoção explícita Pix/NuPay;
- parcelamento.

Se o percentual derivado contra preço-base vier a ser publicado, isso deverá ser uma decisão separada e explícita.

## Exemplo principal

Entrada persistida:

```text
basisPrice      = 3599.00
currentPrice    = 1898.00
cashPrice       = 1708.20
cashPromotion   = 10
soldPercentage  = 47
rating          = 4.8
reviewCount     = 618
```

`COMMERCIAL_FILTER_V2` calcula:

```text
basisDiscountPercentage = 52.5368
```

`SCORE_V2` utiliza:

```text
SOLD_PERCENTAGE = 47
BASIS_DISCOUNT  = 52.5368
RATING          = 4.8
REVIEW_COUNT    = 618
```

O valor:

```text
10
```

da promoção Pix/NuPay continua preservado na `PaymentCondition`, mas não é o fator de desconto do `SCORE_V2`.

## Consequências positivas

- filtro e score passam a compartilhar a mesma semântica de atratividade de preço;
- ofertas aprovadas sem promoção CASH explícita podem ser pontuadas normalmente;
- `SCORE_V1` permanece reproduzível;
- `CASH_DISCOUNT` e `BASIS_DISCOUNT` permanecem semanticamente separados;
- pesos gerais do score não são redistribuídos;
- a migration pode ativar filtro e score V2 atomicamente;
- avaliações históricas continuam explicáveis pelos fatores persistidos.

## Custos e riscos

- `ScoreProfile` passa a suportar duas famílias históricas de desconto;
- a tabela `score_profile` precisa de nova coluna;
- `ScoreEngine` precisa produzir conjuntos de fatores diferentes por perfil;
- a camada de aplicação precisa construir o input correto conforme o perfil ativo;
- testes de V1 devem continuar existindo para impedir regressão histórica;
- testes de V2 precisam cobrir ofertas com e sem promoção CASH explícita.

## Alternativas rejeitadas

### Reinterpretar `CASH_DISCOUNT` no SCORE_V1

Rejeitado porque quebraria a semântica histórica e a reprodutibilidade das avaliações já persistidas.

### Manter SCORE_V1 e usar zero quando não houver CASH explícito

Rejeitado porque ausência não equivale a zero e porque penalizaria artificialmente ofertas que o filtro V2 considera válidas.

### Manter SCORE_V1 e usar `basisDiscountPercentage` dentro de `CASH_DISCOUNT`

Rejeitado porque reutilizaria o mesmo código persistido para duas grandezas diferentes.

### Adicionar simultaneamente `CASH_DISCOUNT` e `BASIS_DISCOUNT` ao SCORE_V2

Rejeitado nesta versão porque contaria duas vezes sinais de preço parcialmente relacionados e alteraria a distribuição de pesos sem requisito comercial explícito.

## Ordem de implementação

1. aceitar e versionar esta ADR;
2. evoluir `ScoreFactorCode` preservando `CASH_DISCOUNT`;
3. evoluir `ScoreProfile` para pesos de desconto mutuamente exclusivos;
4. evoluir `ScoreInput` sem misturar semânticas;
5. evoluir `ScoreEngine` preservando a ordem do `SCORE_V1` e definindo a ordem do `SCORE_V2`;
6. integrar `BasisDiscountCalculator` à construção do input do score V2;
7. criar migration conjunta de `COMMERCIAL_FILTER_V2` e `SCORE_V2`;
8. atualizar repositories de perfil;
9. executar testes unitários de V1 e V2;
10. executar testes JDBC/Flyway;
11. executar suíte completa;
12. somente então reexecutar o probe live vertical.
