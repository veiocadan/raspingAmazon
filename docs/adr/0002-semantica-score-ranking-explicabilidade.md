# ADR-0002 — Semântica do score, ranking e explicabilidade

* **Status:** Proposta
* **Data:** 2026-09-20
* **Projeto:** Rasping Amazon
* **Fase relacionada:** FASE 10

## Contexto

A FASE 9 encerrou a elegibilidade estrutural e os filtros comerciais configuráveis.

Uma oferta somente deve chegar ao score depois de passar por:

1. elegibilidade de vendedor e entrega pela Amazon;
2. desconto mínimo à vista;
3. rating mínimo;
4. quantidade mínima de avaliações.

O score não é um novo mecanismo de elegibilidade.

Sua responsabilidade é ordenar, por prioridade, ofertas que já foram consideradas elegíveis.

Os sinais atualmente disponíveis de forma estruturada são:

* desconto à vista explícito;
* rating;
* quantidade de avaliações;
* percentual vendido (`soldPercentage`).

`soldPercentage` não é filtro eliminatório. Ele representa um possível sinal de popularidade, tração ou demanda observada.

## Decisão

### 1. Score somente para ofertas elegíveis

Uma `DealEvaluation` inelegível não receberá score.

Nesse caso:

```text
score = null
scoreVersion = null
```

Nenhum fator de score será persistido para essa avaliação.

### 2. Versão inicial

A primeira versão será identificada como:

```text
SCORE_V1
```

Qualquer alteração futura de fórmula, peso, normalização, política de ausência ou arredondamento exigirá nova versão.

Exemplo:

```text
SCORE_V2
```

Avaliações históricas nunca deverão ser reinterpretadas silenciosamente pela fórmula mais nova.

### 3. Fatores do SCORE_V1

O SCORE_V1 utilizará:

```text
SOLD_PERCENTAGE
CASH_DISCOUNT
RATING
REVIEW_COUNT
```

A futura `PRICE_ATTRACTIVENESS` não participará do SCORE_V1 enquanto não existir definição independente, fonte confiável e normalização aprovada para esse conceito.

### 4. Pesos iniciais

O SCORE_V1 utilizará:

```text
SOLD_PERCENTAGE = 30
CASH_DISCOUNT   = 25
RATING          = 20
REVIEW_COUNT    = 15
```

Os 10 pontos conceitualmente reservados para atratividade de preço não serão redistribuídos silenciosamente.

Assim, o SCORE_V1 possui pontuação máxima nominal de:

```text
90
```

Essa característica pertence à versão e não constitui erro matemático.

Uma futura versão poderá introduzir o quinto fator e possuir escala diferente.

### 5. Desconto à vista

O fator utiliza somente o desconto explicitamente selecionado pela política comercial já existente.

Não será inferido desconto por diferença matemática entre preços.

Normalização:

```text
0%   -> 0
25%  -> 25
100% -> 100
```

Ou seja, o percentual observado já pertence à escala normalizada de 0 a 100.

### 6. Rating

O rating válido possui intervalo de 0 a 5.

Normalização:

```text
normalizedRating = rating / 5 * 100
```

Exemplos:

```text
4.0 -> 80
4.5 -> 90
5.0 -> 100
```

### 7. Percentual vendido

`soldPercentage` possui intervalo de 0 a 100 e será utilizado diretamente como valor normalizado.

Ausência de `soldPercentage` continuará significando:

```text
UNAVAILABLE
```

e não:

```text
soldPercentage = 0
```

No SCORE_V1, um fator indisponível produzirá contribuição zero, mas a ausência será registrada explicitamente para auditoria.

Portanto, zero observado e dado ausente são estados distintos.

### 8. Quantidade de avaliações

`reviewCount` é uma variável sem limite superior natural.

Ela não será usada diretamente no cálculo, pois isso permitiria que produtos com milhões de avaliações dominassem indevidamente o score.

A normalização será limitada por um parâmetro versionado:

```text
reviewCountFullScoreThreshold
```

Fórmula:

```text
normalizedReviewCount =
    min(reviewCount, reviewCountFullScoreThreshold)
    / reviewCountFullScoreThreshold
    * 100
```

O valor inicial do threshold será armazenado no `ScoreProfile`, e não hardcoded no motor.

### 9. Fórmula agregada

Cada fator produzirá:

```text
rawValue
normalizedValue
weight
contribution
status
```

A contribuição será:

```text
contribution =
    normalizedValue / 100 * weight
```

O score final será:

```text
score = soma das contribuições
```

Fatores indisponíveis preservam `rawValue` e `normalizedValue` como ausentes e contribuição igual a zero conforme a política explícita do SCORE_V1.

### 10. Precisão

Todos os cálculos utilizarão `BigDecimal`.

Não será utilizado `double` como representação persistida do resultado.

Os valores intermediários deverão usar precisão suficiente e o score final será arredondado de forma determinística.

A política inicial será:

```text
scale = 4
rounding = HALF_UP
```

### 11. Explicabilidade

O sistema deverá persistir cada fator individual do score.

Uma avaliação deverá permitir responder:

* qual versão do score foi utilizada;
* qual fator participou;
* qual era o valor observado;
* qual era o valor normalizado;
* qual peso foi aplicado;
* qual contribuição foi produzida;
* se o fator estava disponível.

Não será suficiente persistir somente o número final.

### 12. Ranking

Somente avaliações elegíveis e com score calculado participarão do ranking.

Ordem principal:

```text
score DESC
```

Empates serão resolvidos inicialmente por:

```text
ASIN ASC
```

Esse desempate é técnico e determinístico. Ele não adiciona peso comercial oculto.

### 13. Relação com momentum

O SCORE_V1 não utilizará:

* histórico;
* variação temporal;
* velocidade de vendas;
* variação de preço;
* momentum.

Esses conceitos permanecem reservados para a FASE 11.

## Consequências

O score permanece separado dos filtros.

Uma oferta não será rejeitada porque possui baixo `soldPercentage`.

Alterações de pesos ou fórmula exigem nova versão.

O resultado poderá ser reproduzido historicamente a partir do perfil e dos fatores persistidos.

A persistência precisará ser evoluída por nova migration Flyway, sem alterar migrations anteriores.

## Próximas implementações

A implementação deverá introduzir, em ordem:

1. domínio de scoring;
2. `ScoreProfile`;
3. configuração persistida e versionada;
4. normalizadores;
5. `ScoreEngine`;
6. persistência dos fatores;
7. integração com `DealEvaluation`;
8. ranking;
9. testes de reprodutibilidade;
10. documentação de encerramento da FASE 10.
