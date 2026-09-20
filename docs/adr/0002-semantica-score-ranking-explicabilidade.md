# ADR-0002 — Semântica do score, ranking e explicabilidade

**Status:** Aceito
**Data:** 2026-09-20
**Fase:** 10 — Score, ranking e explicabilidade

## Contexto

A FASE 10 introduz priorização entre ofertas que já passaram pelas etapas anteriores do pipeline.

O score não substitui:

* elegibilidade estrutural;
* filtros comerciais;
* validação dos dados coletados.

O score somente deve ser calculado para ofertas que já tenham sido aprovadas por essas etapas.

O objetivo é produzir uma pontuação determinística, versionada, reproduzível e explicável.

Além do valor final do score, o sistema deve preservar os fatores individuais que participaram do cálculo.

## Decisão

### 1. Momento de cálculo

O score somente será calculado quando a oferta:

1. for estruturalmente elegível; e
2. passar por todos os filtros comerciais ativos.

Quando a oferta não for elegível:

```text
score = null
scoreVersion = null
scoreFactors = vazio
```

Não será produzido score parcial para ofertas rejeitadas.

### 2. Primeira versão

A primeira versão operacional será identificada como:

```text
SCORE_V1
```

A versão faz parte do histórico da avaliação.

Alterações posteriores em:

* pesos;
* fatores;
* normalizações;
* parâmetros;
* arredondamento;
* política de ausência;
* semântica do cálculo;

não devem modificar retroativamente o significado de `SCORE_V1`.

Uma alteração incompatível deverá gerar uma nova versão.

### 3. Fatores ativos no SCORE_V1

O `SCORE_V1` utilizará quatro fatores:

```text
SOLD_PERCENTAGE
CASH_DISCOUNT
RATING
REVIEW_COUNT
```

O fator de atratividade de preço previsto conceitualmente no planejamento inicial não será utilizado no `SCORE_V1`.

A razão é que o projeto ainda não possui uma definição independente e suficientemente confiável para esse fator.

Utilizar apenas preço absoluto introduziria viés entre categorias.

Utilizar a relação entre preço atual e preço-base reproduziria parcialmente o sinal já representado por desconto.

O fator poderá ser introduzido em uma versão futura quando sua semântica e fonte estiverem formalizadas.

### 4. Pesos do SCORE_V1

Os pesos ativos serão:

```text
SOLD_PERCENTAGE = 30
CASH_DISCOUNT   = 25
RATING          = 20
REVIEW_COUNT    = 15
```

A soma dos pesos ativos é:

```text
90
```

Os 10 pontos originalmente reservados à atratividade de preço não serão redistribuídos.

Consequentemente, o máximo nominal alcançável pelo `SCORE_V1` será:

```text
90.0000
```

A ausência do quinto fator não deverá aumentar artificialmente a participação relativa dos fatores restantes.

### 5. Normalização de percentual vendido

Quando `soldPercentage` estiver disponível:

```text
normalizedSoldPercentage = soldPercentage
```

O intervalo válido é:

```text
0 <= soldPercentage <= 100
```

Portanto:

```text
0   → 0 normalizado
50  → 50 normalizado
100 → 100 normalizado
```

### 6. Ausência de percentual vendido

`soldPercentage` pode não estar disponível na fonte.

Ausência não equivale a zero observado.

Quando ausente, o fator será representado como:

```text
factorCode      = SOLD_PERCENTAGE
status          = UNAVAILABLE
rawValue        = null
normalizedValue = null
weight          = 30
contribution    = 0
```

Quando a fonte informar explicitamente zero:

```text
factorCode      = SOLD_PERCENTAGE
status          = AVAILABLE
rawValue        = 0
normalizedValue = 0
weight          = 30
contribution    = 0
```

Os dois casos possuem a mesma contribuição matemática no `SCORE_V1`, mas evidências semanticamente diferentes.

Essa diferença deverá ser preservada para auditoria.

### 7. Normalização do desconto à vista

Será utilizado somente desconto à vista explicitamente observado segundo as regras comerciais já existentes.

Não será inferido desconto a partir de preços sem evidência comercial correspondente.

Quando disponível:

```text
normalizedCashDiscount = cashDiscountPercentage
```

O intervalo válido é:

```text
0 <= cashDiscountPercentage <= 100
```

### 8. Normalização da avaliação

A avaliação possui escala de 0 a 5.

Sua normalização será:

```text
normalizedRating =
    rating
    / 5
    * 100
```

Exemplo:

```text
rating = 4.5

4.5 / 5 * 100 = 90
```

Portanto:

```text
rawValue        = 4.5
normalizedValue = 90
```

### 9. Normalização da quantidade de avaliações

A quantidade de avaliações será normalizada linearmente até um limiar de saturação definido pelo `ScoreProfile`.

A fórmula será:

```text
normalizedReviewCount =
    min(reviewCount, fullScoreThreshold)
    / fullScoreThreshold
    * 100
```

No `SCORE_V1`, o limiar será:

```text
reviewCountFullScoreThreshold = 1000
```

Consequentemente:

```text
0 avaliações    →   0
100 avaliações  →  10
500 avaliações  →  50
1000 avaliações → 100
5000 avaliações → 100
```

O valor 1000 pertence à configuração versionada do `SCORE_V1`.

Ele não deverá ser codificado diretamente dentro do algoritmo de normalização.

Uma alteração futura desse limiar exigirá nova versão de score, preservando a reprodutibilidade histórica.

### 10. Contribuição ponderada

Cada fator disponível produzirá uma contribuição:

```text
contribution =
    normalizedValue
    / 100
    * weight
```

Exemplo:

```text
rating = 4.5
normalizedRating = 90
ratingWeight = 20

contribution =
    90 / 100 * 20
    = 18
```

### 11. Score final

O score será a soma das contribuições individuais:

```text
score =
    soldPercentageContribution
    + cashDiscountContribution
    + ratingContribution
    + reviewCountContribution
```

No `SCORE_V1`:

```text
0 <= score <= 90
```

O domínio poderá aceitar tecnicamente perfis cuja soma seja de até 100 para permitir versões futuras.

### 12. Precisão e arredondamento

Os cálculos deverão utilizar:

```text
BigDecimal
```

O score final será armazenado com:

```text
scale = 4
roundingMode = HALF_UP
```

Os valores normalizados e contribuições também utilizarão precisão determinística compatível com essa política.

Não serão utilizados `double` ou `float` para o cálculo do score.

### 13. Explicabilidade

Cada avaliação pontuada deverá permitir reconstruir o score.

Para cada fator deverão ser preservados, no mínimo:

```text
factorCode
status
rawValue
normalizedValue
weight
contribution
```

Exemplo:

```text
factorCode      = RATING
status          = AVAILABLE
rawValue        = 4.5
normalizedValue = 90
weight          = 20
contribution    = 18
```

Isso permitirá responder perguntas como:

```text
Por que esta oferta recebeu este score?
```

sem recalcular o passado utilizando regras atuais.

### 14. Reprodutibilidade

Uma avaliação histórica deverá permanecer interpretável utilizando:

```text
scoreVersion
score final
fatores persistidos
pesos persistidos
valores normalizados
contribuições
```

O sistema não deverá depender exclusivamente da configuração ativa atual para explicar avaliações históricas.

### 15. Ranking

Somente avaliações elegíveis e pontuadas participarão do ranking.

A ordenação principal será:

```text
score DESC
```

Em caso de empate:

```text
ASIN ASC
```

O desempate por ASIN existe somente para garantir ordenação determinística.

Ele não representa preferência comercial adicional.

### 16. Momentum e histórico

Momentum, velocidade de mudança, tendência temporal ou comparação entre snapshots não pertencem à FASE 10.

Esses conceitos serão tratados na FASE 11.

O `SCORE_V1` utilizará somente fatos disponíveis na avaliação corrente.

## Consequências

### Positivas

* score determinístico;
* cálculo reproduzível;
* pesos versionados;
* normalização explícita;
* ausência diferenciada de valor zero;
* fatores individualmente auditáveis;
* capacidade de explicação histórica;
* possibilidade de evoluir para novas versões sem reinterpretar avaliações antigas.

### Custos

Será necessário persistir:

* configuração do perfil;
* versão do score;
* score final;
* fatores individuais;
* valores brutos;
* valores normalizados;
* pesos;
* contribuições;
* status de disponibilidade.

Também serão necessárias migrations adicionais e integração explícita com o pipeline de avaliação.

## Ordem de implementação

A implementação deverá seguir esta sequência:

1. domínio de scoring;
2. invariantes e testes unitários;
3. normalizadores;
4. motor determinístico;
5. persistência do `ScoreProfile`;
6. persistência dos fatores;
7. integração com `DealEvaluation`;
8. integração com o pipeline;
9. ranking determinístico;
10. testes de integração e regressão;
11. documentação de encerramento da FASE 10.
