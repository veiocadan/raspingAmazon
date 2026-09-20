# ADR 0003 — Semântica de histórico, evolução e momentum

**Projeto:** Rasping Amazon
**Fase:** 11 — Histórico e momentum
**Status:** ACEITO PARA IMPLEMENTAÇÃO
**Data:** 20/09/2026

---

## 1. Contexto

A FASE 10 consolidou o score versionado, o ranking determinístico e a explicabilidade da observação atual de uma oferta.

O `SCORE_V1` responde à pergunta:

```text
quão prioritária é esta oferta
considerando os fatos observados agora?
```

A FASE 11 possui responsabilidade diferente.

Ela deve responder:

```text
como esta oferta está evoluindo ao longo do tempo?
```

O projeto já preserva observações temporais por meio de `OfferSnapshot`.

Cada snapshot representa uma coleta específica e não deve ser sobrescrito por uma observação posterior.

O histórico passa, portanto, a ser construído pela sequência ordenada de snapshots do mesmo produto.

---

## 2. Separação de responsabilidades

A FASE 11 não altera a semântica das etapas anteriores.

```text
elegibilidade estrutural
        ↓
filtros comerciais
        ↓
score atual
        ↓
histórico / evolução
        ↓
momentum
```

Momentum:

* não aprova oferta rejeitada;
* não transforma oferta rejeitada em elegível;
* não substitui filtros;
* não altera `SCORE_V1`;
* não altera os pesos do score;
* não modifica snapshots históricos.

O histórico descreve evolução.

O momentum produz um indicador temporal derivado dessa evolução.

---

## 3. Unidade histórica

A unidade histórica permanece:

```text
OfferSnapshot
```

Uma nova coleta relevante produz uma nova observação.

A identidade idempotente atualmente utilizada permanece:

```text
product_id
+
collected_at
+
source
```

Reprocessar exatamente a mesma observação não deve criar um novo ponto histórico.

Uma nova observação temporal do mesmo ASIN deve produzir um novo snapshot.

---

## 4. Ordem histórica

O histórico de um produto é ordenado por:

```text
collected_at ASC
```

Quando for necessário desempate técnico, utiliza-se:

```text
id ASC
```

O ID não possui significado temporal ou comercial independente.

Ele é somente desempate determinístico.

---

## 5. Comparação entre snapshots

Uma evolução compara:

```text
snapshot anterior
        ↓
snapshot atual
```

Somente snapshots do mesmo ASIN podem ser comparados.

Para cálculo de evolução temporal, o snapshot anterior precisa possuir:

```text
previous.collectedAt < current.collectedAt
```

Snapshots com o mesmo instante não serão utilizados para calcular velocidade temporal.

---

## 6. Variação do percentual vendido

Quando ambos os snapshots possuem `soldPercentage`:

```text
soldPercentageDelta
=
current.soldPercentage
-
previous.soldPercentage
```

A unidade é:

```text
pontos percentuais
```

Exemplo:

```text
anterior = 62%
atual    = 83%

delta = +21 p.p.
```

Ausência de `soldPercentage` em qualquer uma das observações resulta em:

```text
soldPercentageDelta = null
```

Ausência nunca será convertida em zero.

---

## 7. Variação de preço

A variação absoluta é:

```text
currentPriceDelta
=
current.currentPrice
-
previous.currentPrice
```

Exemplo:

```text
anterior = 100.00
atual    = 90.00

delta = -10.00
```

Valor negativo representa queda de preço.

A variação percentual é:

```text
currentPriceDeltaPercentage
=
(
    currentPrice - previousPrice
)
/
previousPrice
*
100
```

Quando o preço anterior for zero, a variação percentual será:

```text
null
```

Nenhuma divisão artificial será realizada.

---

## 8. Variação de desconto

Desconto permanece um conceito contextual.

Não será calculado por simples diferença entre preço atual e preço-base.

A FASE 11 reutilizará:

```text
BestCashDiscountSelector
```

já utilizado pelas FASES 9 e 10.

Somente condições comerciais válidas para a semântica existente poderão fornecer o desconto histórico.

O delta será:

```text
cashDiscountDelta
=
currentBestCashDiscount
-
previousBestCashDiscount
```

Quando uma das observações não possuir desconto válido:

```text
cashDiscountDelta = null
```

Ausência não será convertida em zero.

---

## 9. Primeira detecção

Para um ASIN:

```text
firstDetectedAt
=
MIN(offer_snapshot.collected_at)
```

Esse instante representa a primeira observação conhecida pelo sistema.

Ele não representa necessariamente o início real da promoção na Amazon.

---

## 10. Última observação

Para um ASIN:

```text
lastObservedAt
=
MAX(offer_snapshot.collected_at)
```

A idade desde a última observação poderá ser calculada em relação a um instante de referência fornecido pela aplicação.

O domínio não utilizará implicitamente o relógio do sistema.

---

## 11. Recorrência

Uma oferta será considerada recorrente no histórico local quando existir mais de um snapshot para o mesmo produto.

```text
snapshotCount > 1
→ recurring = true
```

Recorrência não significa automaticamente uma nova promoção Amazon.

Ela significa apenas que o sistema observou o mesmo ASIN em momentos distintos.

---

## 12. Oferta já publicada

A indicação de publicação anterior será obtida por consulta ao histórico persistido:

```text
Product
    ↓
OfferSnapshot
    ↓
DealEvaluation
    ↓
Publication
```

A FASE 11 somente consulta essa informação.

Ela não cria publicações, textos, links de associado ou integrações com canais.

Essas responsabilidades permanecem na fase de publicação.

---

## 13. MOMENTUM_V1

A primeira versão de momentum será:

```text
MOMENTUM_V1
```

Ela utilizará somente a velocidade do percentual vendido.

Motivo:

O documento arquitetural apresenta explicitamente a evolução de `% vendidos` como sinal de momentum.

Não existe atualmente uma especificação aprovada de pesos que combine preço, desconto e percentual vendido.

Criar pesos arbitrários violaria o princípio do projeto de não inventar semântica de negócio sem evidência.

---

## 14. Fórmula do MOMENTUM_V1

Quando existirem duas observações válidas de `soldPercentage`:

```text
momentum
=
soldPercentageDelta
/
elapsedHours
```

A unidade é:

```text
pontos percentuais por hora
```

Exemplo:

```text
10:00 → 62%
19:00 → 83%

delta:
21 p.p.

tempo:
9 horas

momentum:
21 / 9
=
2.3333 p.p./hora
```

A implementação utilizará `BigDecimal`.

Precisão:

```text
scale = 4
rounding = HALF_UP
```

---

## 15. Momentum indisponível

`MOMENTUM_V1` não será calculado quando:

```text
não existir snapshot anterior
```

ou:

```text
soldPercentage anterior estiver ausente
```

ou:

```text
soldPercentage atual estiver ausente
```

ou:

```text
o intervalo temporal não for positivo
```

Nesses casos:

```text
momentum = null
momentumVersion = null
```

Isso preserva a regra:

```text
ausência != zero observado
```

---

## 16. Valores negativos

O momentum poderá ser negativo.

Uma queda no `% vendidos` pode ocorrer por mudança, reinício ou substituição da promoção observada pela fonte.

A FASE 11 não corrigirá silenciosamente esse valor para zero.

O dado será preservado como observado.

Versões futuras poderão introduzir classificação adicional para reinício ou mudança de promoção.

---

## 17. Preço e desconto no MOMENTUM_V1

As variações de preço e desconto serão calculadas e disponibilizadas pelo histórico.

Entretanto, elas não participarão da fórmula de `MOMENTUM_V1`.

Elas permanecem disponíveis para:

```text
auditoria
interface futura
análise histórica
MOMENTUM_V2 ou posterior
```

Assim evitamos introduzir pesos comerciais arbitrários.

---

## 18. Persistência

`deal_evaluation` já possui:

```text
momentum
momentum_version
```

Esses campos serão utilizados para o resultado agregado.

Uma nova migration da FASE 11 deverá preservar também a base auditável do cálculo.

A estrutura deverá permitir identificar pelo menos:

```text
deal_evaluation_id
previous_offer_snapshot_id
elapsed_seconds
sold_percentage_delta
current_price_delta
current_price_delta_percentage
cash_discount_delta
```

A migration não modificará migrations anteriores.

---

## 19. Reprodutibilidade

Uma avaliação histórica com momentum deve permitir determinar:

```text
qual versão foi utilizada?
qual snapshot atual foi avaliado?
qual snapshot anterior foi utilizado?
qual era o intervalo temporal?
quais deltas estavam disponíveis?
qual valor de momentum foi produzido?
```

A inserção posterior de novos snapshots não pode alterar silenciosamente a explicação de um momentum já persistido.

---

## 20. Critérios da FASE 11

A fase será considerada concluída quando o sistema conseguir:

1. consultar o histórico ordenado de um ASIN;
2. identificar primeira e última observação;
3. identificar recorrência;
4. comparar deterministicamente dois snapshots;
5. calcular variação de percentual vendido;
6. calcular variação absoluta e percentual de preço;
7. calcular variação do desconto à vista quando disponível;
8. produzir `MOMENTUM_V1`;
9. preservar ausência de dados sem converter ausência em zero;
10. persistir momentum e sua base auditável;
11. consultar se o ASIN possui publicação histórica;
12. preservar elegibilidade, filtros e `SCORE_V1` sem alteração semântica;
13. manter o processamento idempotente;
14. passar pela suíte hermética completa e pelo CI remoto.

---

## 21. Consequências

A FASE 11 introduz uma leitura temporal sobre dados que já são persistidos.

Não será criado um segundo sistema de histórico.

`OfferSnapshot` continua sendo a fonte histórica principal.

O novo código deverá separar:

```text
consulta histórica
comparação de snapshots
cálculo de momentum
persistência do resultado
```

Nenhuma dessas responsabilidades será movida para parser, collector ou repository de escrita.
