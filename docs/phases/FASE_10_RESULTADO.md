# FASE 10 — RESULTADO CONSOLIDADO

**Projeto:** Rasping Amazon
**Fase:** 10 — Score, ranking e explicabilidade
**Data:** 20/09/2026
**Status:** CONCLUÍDA LOCALMENTE — validação local concluída com sucesso; CI remoto pendente

---

## 1. Objetivo

A FASE 10 implementou o mecanismo versionado de score, ranking determinístico e explicabilidade das ofertas que já passaram pelas etapas eliminatórias anteriores.

A fase responde à pergunta:

```text
entre as ofertas estruturalmente elegíveis
e aprovadas pelos filtros comerciais,
quais merecem maior prioridade?
```

A separação arquitetural preservada é:

```text
elegibilidade estrutural
        ↓
filtros comerciais
        ↓
score
        ↓
ranking
```

Score não substitui elegibilidade nem filtros.

Uma oferta rejeitada pelas etapas anteriores não recebe score.

Momentum, evolução histórica e tendência temporal permanecem fora desta fase e pertencem à FASE 11.

---

## 2. Princípios preservados

A FASE 10 manteve as decisões arquiteturais centrais do projeto:

* domínio independente de PostgreSQL, JDBC, HTTP e HTML;
* configuração de score fora do código de aplicação;
* versão explícita da regra de score;
* cálculo determinístico e reproduzível;
* uso de `BigDecimal` na matemática do score;
* ausência de dado diferente de zero observado;
* fatores individuais preservados para auditoria;
* score calculado somente depois de elegibilidade e filtros comerciais;
* ranking separado do cálculo do score;
* desempate determinístico sem introduzir preferência comercial oculta;
* nenhuma variável utilizada sem fonte semântica confiável;
* migrations anteriores preservadas;
* momentum e histórico não antecipados.

---

## 3. ADR da FASE 10

A semântica do score foi formalizada em:

```text
docs/adr/0002-semantica-score-ranking-explicabilidade.md
```

O ADR estabelece:

```text
SCORE_V1
```

com os seguintes fatores:

```text
SOLD_PERCENTAGE = 30 pontos
CASH_DISCOUNT   = 25 pontos
RATING          = 20 pontos
REVIEW_COUNT    = 15 pontos
```

Peso ativo total:

```text
90 pontos
```

Os 10 pontos restantes permanecem reservados para:

```text
PRICE_ATTRACTIVENESS
```

A atratividade de preço não foi implementada na versão atual porque o projeto ainda não possui uma fonte semântica independente e confiável para essa variável.

Não houve redistribuição desses 10 pontos entre os demais fatores.

Commit de referência do contrato operacional:

```text
96a690a docs: formaliza perfil operacional score v1
```

---

## 4. Por que atratividade de preço não entrou no SCORE_V1

A proposta inicial da fase previa:

```text
30% percentual vendido
25% desconto
20% avaliação
15% quantidade de avaliações
10% atratividade de preço
```

Durante a formalização do contrato foi preservada a regra de não inventar sinais.

Usar preço absoluto produziria viés entre categorias diferentes.

Exemplo:

```text
R$ 99,00
```

não é necessariamente mais atraente que:

```text
R$ 999,00
```

porque podem representar categorias de produto completamente diferentes.

Usar novamente a relação entre preço atual e preço-base duplicaria semanticamente o fator de desconto.

Portanto:

```text
PRICE_ATTRACTIVENESS
```

permanece reservado para versão futura, quando existir uma fonte adequada.

---

## 5. `ScoreProfile`

Foi criado o conceito:

```text
ScoreProfile
```

Campos:

```text
version
soldPercentageWeight
cashDiscountWeight
ratingWeight
reviewCountWeight
reviewCountFullScoreThreshold
```

Perfil operacional atual:

```text
SCORE_V1
```

Configuração:

```text
soldPercentageWeight            = 30
cashDiscountWeight              = 25
ratingWeight                    = 20
reviewCountWeight               = 15
reviewCountFullScoreThreshold   = 1000
```

Peso ativo:

```text
90
```

O domínio protege:

* versão obrigatória;
* pesos entre 0 e 100;
* soma ativa não superior a 100;
* threshold de reviews maior que zero.

---

## 6. Threshold de `reviewCount`

A FASE 9 utiliza:

```text
minReviewCount = 100
```

Esse valor possui semântica eliminatória:

```text
quantidade mínima para a oferta continuar
```

A FASE 10 utiliza:

```text
reviewCountFullScoreThreshold = 1000
```

Esse valor possui semântica diferente:

```text
quantidade de reviews necessária
para saturar o fator REVIEW_COUNT
```

Assim:

```text
100 reviews
→ 10% do fator REVIEW_COUNT

500 reviews
→ 50% do fator REVIEW_COUNT

1000 reviews
→ 100% do fator REVIEW_COUNT

mais de 1000 reviews
→ continua em 100%
```

Os dois thresholds não representam a mesma regra.

---

## 7. Persistência do perfil

A migration:

```text
V8__score_profile_and_factors.sql
```

criou:

```text
score_profile
```

Campos:

```text
id
version
sold_percentage_weight
cash_discount_weight
rating_weight
review_count_weight
review_count_full_score_threshold
active
created_at
```

O schema protege:

* versão única;
* pesos entre 0 e 100;
* soma ativa não superior a 100;
* threshold positivo;
* no máximo um perfil ativo.

Perfil inicial:

```text
version                           = SCORE_V1
sold_percentage_weight            = 30
cash_discount_weight              = 25
rating_weight                     = 20
review_count_weight               = 15
review_count_full_score_threshold = 1000
active                            = true
```

Mudanças futuras de semântica devem utilizar novas versões:

```text
SCORE_V2
SCORE_V3
...
```

Perfis históricos não devem ser modificados retroativamente.

---

## 8. Porta de configuração do score

A aplicação depende da porta:

```text
ScoreProfileProvider
```

Implementação atual:

```text
ScoreProfileJdbcRepository
```

Fluxo:

```text
PostgreSQL
    ↓
score_profile
    ↓
ScoreProfileJdbcRepository
    ↓
ScoreProfileProvider
    ↓
ScoreProfile
```

Assim, o serviço de aplicação não conhece JDBC nem SQL para carregar a configuração.

---

## 9. Fatores de score

Foram introduzidos:

```text
ScoreFactorCode
ScoreFactorStatus
ScoreFactorResult
```

Fatores atuais:

```text
SOLD_PERCENTAGE
CASH_DISCOUNT
RATING
REVIEW_COUNT
```

Status possíveis:

```text
AVAILABLE
UNAVAILABLE
```

Cada fator auditável registra:

```text
code
status
rawValue
normalizedValue
weight
contribution
```

Isso permite reconstruir não apenas o score final, mas também a participação de cada sinal.

---

## 10. Semântica de ausência

A FASE 10 preserva explicitamente:

```text
ausência != zero observado
```

Exemplo:

```text
soldPercentage ausente
```

é representado como:

```text
status = UNAVAILABLE
rawValue = null
normalizedValue = null
contribution = 0
```

e não como:

```text
rawValue = 0
```

Um valor observado de:

```text
soldPercentage = 0
```

é diferente:

```text
status = AVAILABLE
rawValue = 0
normalizedValue = 0
contribution = 0
```

Os dois casos podem possuir a mesma contribuição numérica, mas mantêm significado auditável distinto.

---

## 11. `ScoreInput`

Foi criado:

```text
ScoreInput
```

Campos:

```text
soldPercentage
cashDiscountPercentage
rating
reviewCount
```

No contrato atual:

```text
soldPercentage
→ opcional
```

Enquanto:

```text
cashDiscountPercentage
rating
reviewCount
→ obrigatórios
```

Isso decorre diretamente da ordem do pipeline.

Uma oferta somente chega ao score depois de passar pelos filtros comerciais da FASE 9.

Portanto, nesse ponto:

```text
cash discount
rating
reviewCount
```

já precisam estar disponíveis.

`soldPercentage`, por outro lado, não é filtro eliminatório e pode legitimamente continuar ausente.

---

## 12. Normalização

Foi criado:

```text
ScoreNormalizer
```

As normalizações do `SCORE_V1` são determinísticas.

### `SOLD_PERCENTAGE`

```text
normalized = soldPercentage
```

Intervalo:

```text
0 .. 100
```

### `CASH_DISCOUNT`

```text
normalized = cashDiscountPercentage
```

Intervalo:

```text
0 .. 100
```

Somente desconto à vista explicitamente reconhecido participa.

Não é calculado desconto por diferença implícita entre preços.

### `RATING`

Fórmula:

```text
normalized = rating / 5 × 100
```

Exemplo:

```text
rating = 4.5

4.5 / 5 × 100
= 90
```

### `REVIEW_COUNT`

Fórmula:

```text
normalized =
    min(reviewCount, threshold)
    / threshold
    × 100
```

No `SCORE_V1`:

```text
threshold = 1000
```

Exemplo:

```text
reviewCount = 500

500 / 1000 × 100
= 50
```

Para:

```text
reviewCount >= 1000
```

o fator fica saturado em:

```text
100
```

---

## 13. Contribuição dos fatores

A contribuição é calculada por:

```text
contribution =
    normalizedValue
    / 100
    × weight
```

Exemplo:

```text
RATING

normalizedValue = 90
weight = 20

90 / 100 × 20
= 18
```

O score final é:

```text
soma das contribuições
```

---

## 14. Precisão e arredondamento

A matemática utiliza:

```text
BigDecimal
```

Regra operacional:

```text
scale = 4
rounding = HALF_UP
```

São normalizados deterministicamente:

```text
normalizedValue
contribution
score final
```

Isso impede que o resultado dependa de diferenças de representação binária de ponto flutuante.

O campo histórico:

```text
OfferSnapshot.rating
```

permanece como `Double`.

A conversão para o domínio do score ocorre explicitamente com:

```text
BigDecimal.valueOf(...)
```

na fronteira da aplicação.

Não foi alterado o contrato histórico de `OfferSnapshot` apenas para acomodar a FASE 10.

---

## 15. `ScoreResult`

Foi criado:

```text
ScoreResult
```

Campos:

```text
version
score
factors
```

O domínio protege:

* versão não vazia;
* score entre 0 e 100;
* lista de fatores não vazia;
* ausência de fatores duplicados;
* soma das contribuições igual ao score final;
* cópia defensiva da lista;
* escala determinística do score.

Assim, o valor agregado e sua explicação não podem divergir silenciosamente.

---

## 16. `ScoreEngine`

Foi implementado:

```text
ScoreEngine
```

Entrada:

```text
ScoreProfile
ScoreInput
```

Saída:

```text
ScoreResult
```

O motor:

* não conhece `OfferSnapshot`;
* não conhece PostgreSQL;
* não conhece JDBC;
* não conhece Pix ou NuPay;
* não conhece filtros eliminatórios;
* não conhece publicação;
* não conhece ranking;
* não conhece momentum.

Sua responsabilidade é exclusivamente aplicar o contrato matemático versionado.

Ordem estável dos fatores:

```text
1. SOLD_PERCENTAGE
2. CASH_DISCOUNT
3. RATING
4. REVIEW_COUNT
```

---

## 17. Integração com `DealEvaluation`

`DealEvaluation` passou a carregar:

```text
score
scoreVersion
scoreFactors
```

As invariantes foram fortalecidas.

Quando não existe score:

```text
score = null
scoreVersion = null
scoreFactors = []
```

Quando existe score:

```text
score != null
scoreVersion != null
scoreFactors não vazio
```

Além disso:

```text
avaliação inelegível
→ não pode possuir score
```

Uma avaliação pontuada é revalidada por `ScoreResult`, garantindo que:

```text
score
=
soma das contribuições persistidas
```

---

## 18. Integração com filtros comerciais

O score é calculado somente depois que todas as regras eliminatórias passam.

Fluxo atual:

```text
AmazonEligibilityValidator
        ↓
CommercialFilterEngine
        ↓
todas as regras passaram?
        ├── não
        │    ↓
        │ DealEvaluation sem score
        │
        └── sim
             ↓
        ScoreProfileProvider
             ↓
        ScoreInput
             ↓
        ScoreEngine
             ↓
        ScoreResult
             ↓
        DealEvaluation pontuada
```

Isso preserva a separação entre:

```text
rejeição
```

e:

```text
priorização
```

---

## 19. Reutilização da semântica de desconto

A FASE 10 utiliza:

```text
BestCashDiscountSelector
```

para construir o fator:

```text
CASH_DISCOUNT
```

O mesmo conceito já é utilizado pela regra comercial da FASE 9.

Assim não existem duas definições concorrentes de:

```text
melhor desconto à vista
```

São reconhecidas somente condições explícitas compatíveis com:

```text
PIX
NUPAY_ADDITIONAL_LIMIT
```

Não é inferido desconto por simples diferença entre preços.

---

## 20. Persistência da explicabilidade

A migration V8 também criou:

```text
deal_evaluation_score_factor
```

Campos:

```text
id
deal_evaluation_id
factor_order
factor_code
status
raw_value
normalized_value
weight
contribution
```

O schema protege:

* ordem não negativa;
* código único por avaliação;
* ordem única por avaliação;
* status reconhecido;
* normalized value entre 0 e 100 quando presente;
* peso entre 0 e 100;
* contribuição não negativa;
* contribuição não superior ao peso;
* coerência entre `AVAILABLE` e valores presentes;
* coerência entre `UNAVAILABLE` e valores nulos;
* `UNAVAILABLE` com contribuição zero.

A FK utiliza:

```text
ON DELETE CASCADE
```

Assim os fatores seguem o ciclo de vida da avaliação correspondente.

---

## 21. Persistência JDBC

`DealEvaluationJdbcRepository` passou a persistir:

```text
DealEvaluation
    ↓
deal_evaluation

EvaluationRuleResult
    ↓
deal_evaluation_rule_result

ScoreFactorResult
    ↓
deal_evaluation_score_factor
```

O repository não recalcula nenhum fator.

Ele persiste exatamente o resultado produzido pelo domínio.

Isso mantém a regra:

```text
domínio calcula
infraestrutura persiste
```

---

## 22. Composition root

`AmazonDealProcessingComposition` foi evoluído para conectar:

```text
FilterProfileJdbcRepository
ScoreProfileJdbcRepository
CommercialFilterEngine
ScoreEngine
BestCashDiscountSelector
DealEvaluationJdbcRepository
```

ao:

```text
AmazonDealEvaluationApplicationService
```

A mesma `Connection` JDBC continua sendo compartilhada pelos componentes da unidade de trabalho.

`FilterProfile` e `ScoreProfile` são lidos durante a avaliação.

---

## 23. Ranking determinístico

Foi criado:

```text
DealEvaluationRanking
```

Contrato:

```text
1. somente avaliações com score participam
2. score DESC
3. ASIN ASC em caso de empate
```

Exemplo:

```text
ASIN B000000003 — score 70
ASIN B000000002 — score 50
ASIN B000000001 — score 50
```

Ranking:

```text
B000000003
B000000001
B000000002
```

O ASIN é apenas um desempate técnico determinístico.

Não representa:

* peso comercial;
* preferência de produto;
* popularidade;
* momentum;
* critério adicional de score.

O ranking:

* não altera a lista recebida;
* retorna lista imutável;
* exclui avaliações sem score.

---

## 24. Teste vertical positivo de score

Foi introduzido:

```text
AmazonDealProcessingScoreEndToEndTest
```

O teste utiliza servidor HTTP local e fixtures existentes, sem acesso externo à Amazon.

Fixture comercial:

```text
amazon/fixtures/product/amazon-commercial.html
```

Ela contém:

```text
25% off à vista no Pix ou NuPay Limite Adicional
```

O deal utilizado fornece:

```text
soldPercentage = 37
rating = 4.6
reviewCount = 58363
```

A oferta:

```text
passa elegibilidade estrutural
passa filtros comerciais
recebe SCORE_V1
é persistida com fatores
```

Score esperado:

```text
SOLD_PERCENTAGE

37 / 100 × 30
= 11.1000
```

```text
CASH_DISCOUNT

25 / 100 × 25
= 6.2500
```

```text
RATING

4.6 / 5 × 100
= 92

92 / 100 × 20
= 18.4000
```

```text
REVIEW_COUNT

58363 >= 1000

normalized = 100

100 / 100 × 15
= 15.0000
```

Total:

```text
11.1000
+ 6.2500
+ 18.4000
+ 15.0000
= 50.7500
```

O teste comprovou no PostgreSQL:

```text
eligible = true
rejection_reason = null
eligibility_policy_version = AMAZON_SELLER_DELIVERY_V1
filter_profile_version = COMMERCIAL_FILTER_V1
score = 50.7500
score_version = SCORE_V1
```

e exatamente:

```text
4
```

linhas em:

```text
deal_evaluation_score_factor
```

---

## 25. Caminho negativo preservado

O teste vertical anterior continua cobrindo uma oferta sem condição comercial explícita Pix/NuPay.

Nesse cenário:

```text
MIN_CASH_DISCOUNT
→ falha
```

com:

```text
CASH_DISCOUNT_UNAVAILABLE
```

A oferta permanece:

```text
eligible = false
score = null
score_version = null
scoreFactors = []
```

Portanto, a introdução do score não enfraqueceu o comportamento fail closed estabelecido anteriormente.

---

## 26. Testes

Ao final da implementação local:

```text
Tests run: 391
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

Foram cobertos, entre outros:

* invariantes de fatores;
* disponibilidade e ausência de fatores;
* soma das contribuições;
* perfil de score;
* normalização de percentuais;
* normalização de rating;
* saturação de review count;
* cálculo de contribuição;
* cálculo completo do score;
* `soldPercentage` ausente;
* diferença entre ausência e zero observado;
* persistência do perfil ativo;
* persistência dos fatores;
* persistência de fator `UNAVAILABLE`;
* integração do score no serviço de avaliação;
* ausência de score para ofertas rejeitadas;
* uso da porta pelo fluxo vertical;
* composição concreta das dependências;
* ranking por score;
* desempate por ASIN;
* imutabilidade do ranking;
* fluxo ponta a ponta positivo com score e PostgreSQL;
* preservação do fluxo ponta a ponta negativo da FASE 9.

---

## 27. Estado das migrations

Ao final da validação local:

```text
Migrations validadas: 8
Schema atual: versão 8
Migration pendente: não
```

Migration introduzida nesta fase:

```text
V8__score_profile_and_factors.sql
```

As migrations anteriores não foram alteradas retroativamente.

---

## 28. Commits de referência conhecidos

ADR operacional:

```text
96a690a docs: formaliza perfil operacional score v1
```

Teste vertical positivo:

```text
3443e3c test: valida score no fluxo vertical
```

Os demais commits intermediários da branch compõem incrementalmente a implementação da FASE 10.

---

## 29. Critérios de conclusão

Critério:

```text
ofertas ordenadas por score
```

Resultado:

```text
ATENDIDO
```

Implementado por ranking determinístico:

```text
score DESC
ASIN ASC
```

---

Critério:

```text
avaliação reproduzível com versão da regra
```

Resultado:

```text
ATENDIDO
```

A avaliação registra:

```text
score
scoreVersion
```

e o perfil operacional correspondente é:

```text
SCORE_V1
```

com normalização, pesos, threshold e arredondamento formalizados.

---

Critério:

```text
fatores disponíveis para auditoria/UI
```

Resultado:

```text
ATENDIDO
```

Cada avaliação pontuada pode preservar:

```text
factorCode
status
rawValue
normalizedValue
weight
contribution
```

em persistência dedicada.

---

## 30. Escopo deliberadamente não implementado

A FASE 10 não implementa:

```text
momentum
evolução temporal
comparação entre snapshots históricos
tendência
aceleração de vendas
mudança recente de preço
ranking histórico
política de publicação baseada em momentum
```

Esses conceitos pertencem à:

```text
FASE 11
```

Também não foi implementada:

```text
PRICE_ATTRACTIVENESS
```

sem fonte semanticamente confiável.

---

## 31. Estado final local

Estado validado:

```text
score versionado
normalização determinística
fatores explicáveis
persistência auditável
ranking determinístico
integração com filtros
integração com pipeline
teste vertical positivo
teste vertical negativo preservado
391 testes verdes
8 migrations válidas
working tree limpa antes do fechamento documental
```

Resultado:

```text
FASE 10 CONCLUÍDA LOCALMENTE
```

---

## 32. Validação remota

No momento deste fechamento documental:

```text
CI remoto ainda não foi validado após o fechamento final da FASE 10
```

Portanto, o status não deve ser promovido ainda para:

```text
CONCLUÍDA — CI remoto validado
```

A promoção deve ocorrer somente depois de:

```text
push da branch
        ↓
execução do GitHub Actions
        ↓
workflow verde
```

Após essa evidência, este documento pode ser atualizado para registrar a conclusão remota.

---

## 33. Próxima fase

A próxima etapa prevista é:

```text
FASE 11 — evolução histórica e momentum
```

A FASE 11 poderá utilizar o histórico de snapshots para adicionar sinais temporais sem alterar retroativamente o contrato do `SCORE_V1`.

Separação preservada:

```text
FASE 10
score sobre fatos da observação atual

FASE 11
momentum derivado de evolução temporal
```

O score atual permanece reproduzível e versionado independentemente da evolução futura.
