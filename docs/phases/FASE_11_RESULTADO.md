# FASE 11 — RESULTADO CONSOLIDADO

**Projeto:** Rasping Amazon
**Fase:** 11 — Histórico, evolução e momentum
**Data:** 20/09/2026
**Status:** CONCLUÍDA LOCALMENTE — validação local e CI remoto aprovados, merge ainda pendentes

---

## 1. Objetivo

A FASE 11 introduziu o histórico como parte obrigatória do processamento das ofertas.

A fase passou a responder perguntas temporais que o `SCORE_V1` deliberadamente não respondia.

Separação preservada:

```text
FASE 10
fatos da observação atual
        ↓
SCORE_V1
        ↓
priorização

FASE 11
snapshots históricos
        ↓
evolução temporal
        ↓
MOMENTUM_V1
```

Momentum não substitui:

```text
elegibilidade estrutural
filtros comerciais
score
ranking
```

Ele representa exclusivamente um sinal histórico derivado da evolução observada.

---

## 2. Requisitos atendidos

A FASE 11 passou a suportar:

```text
histórico por ASIN
variação de percentual vendido
variação absoluta de preço
variação percentual de preço
variação de desconto à vista
tempo desde primeira detecção
tempo desde última atualização
momentum
detecção de recorrência
detecção de publicação anterior
```

O sistema preserva snapshots históricos em vez de sobrescrever observações anteriores.

---

## 3. ADR da FASE 11

A semântica histórica foi formalizada em:

```text
docs/adr/0003-semantica-historico-e-momentum.md
```

O ADR define:

```text
unidade histórica = OfferSnapshot
```

Identidade persistente da observação:

```text
product_id
+
collected_at
+
source
```

Ordenação determinística:

```text
collected_at ASC
id ASC
```

Para comparação temporal:

```text
previous.collectedAt < current.collectedAt
```

Ou seja, o snapshot atual nunca pode ser utilizado como seu próprio predecessor.

---

## 4. Consulta histórica por ASIN

Foi criada a porta:

```text
OfferHistoryQueryPort
```

e a implementação:

```text
OfferHistoryJdbcRepository
```

O histórico pode responder:

```text
findHistoryByAsin
findFirstByAsin
findLatestByAsin
findPreviousByAsin
countByAsin
```

A aplicação não conhece SQL nem JDBC.

Fluxo:

```text
application
    ↓
OfferHistoryQueryPort
    ↓
OfferHistoryJdbcRepository
    ↓
PostgreSQL
```

---

## 5. `HistoricalOfferObservation`

Foi criada a projeção histórica:

```text
HistoricalOfferObservation
```

Ela representa somente os dados necessários para comparação temporal.

Campos principais:

```text
snapshotId
asin
collectedAt
currentPrice
soldPercentage
source
paymentConditions
```

O histórico não reutiliza diretamente toda a entidade de persistência.

Isso mantém o cálculo desacoplado da infraestrutura.

---

## 6. Comparação entre snapshots

Foi criado:

```text
SnapshotEvolutionCalculator
```

Entrada:

```text
HistoricalOfferObservation previous
HistoricalOfferObservation current
```

Saída:

```text
SnapshotEvolution
```

O componente é puro:

```text
não consulta banco
não calcula score
não decide elegibilidade
não publica
não calcula momentum
```

Ele produz somente fatos derivados da comparação.

---

## 7. Variação de percentual vendido

Fórmula:

```text
soldPercentageDelta
=
current.soldPercentage
-
previous.soldPercentage
```

Exemplo:

```text
62% → 68%
```

Resultado:

```text
+6 pontos percentuais
```

O delta pode ser:

```text
positivo
zero
negativo
```

Se alguma das observações não possuir percentual vendido:

```text
soldPercentageDelta = null
```

Ausência não é convertida em zero observado.

---

## 8. Variação absoluta de preço

Fórmula:

```text
currentPriceDelta
=
currentPrice
-
previousPrice
```

Exemplo:

```text
R$ 100,00 → R$ 90,00
```

Resultado:

```text
-10,00
```

Valor negativo representa queda de preço.

Valor positivo representa aumento.

---

## 9. Variação percentual de preço

Fórmula:

```text
currentPriceDeltaPercentage
=
currentPriceDelta
/
previousPrice
×
100
```

Exemplo:

```text
100,00 → 90,00
```

Resultado:

```text
-10.0000%
```

Regra matemática:

```text
scale = 4
rounding = HALF_UP
```

Quando o preço anterior é zero:

```text
currentPriceDeltaPercentage = null
```

porque a divisão percentual não é definida.

---

## 10. Variação do desconto à vista

A FASE 11 reutiliza:

```text
BestCashDiscountSelector
```

Portanto, a definição histórica de desconto é a mesma já utilizada pelos filtros comerciais e pelo score.

São reconhecidas somente condições:

```text
PaymentConditionType.CASH
```

com métodos:

```text
PIX
NUPAY_ADDITIONAL_LIMIT
```

e desconto percentual explicitamente observado.

Fórmula:

```text
cashDiscountDelta
=
currentBestCashDiscount
-
previousBestCashDiscount
```

Se alguma observação não possuir desconto reconhecido:

```text
cashDiscountDelta = null
```

Nenhum desconto é inferido artificialmente por diferença entre preços.

---

## 11. Intervalo temporal

`SnapshotEvolution` preserva:

```text
previousCollectedAt
currentCollectedAt
```

e calcula:

```text
elapsedSeconds
```

O intervalo precisa ser positivo.

A relação obrigatória é:

```text
currentCollectedAt > previousCollectedAt
```

---

## 12. `MOMENTUM_V1`

Foi criado:

```text
MomentumEngine
```

Versão atual:

```text
MOMENTUM_V1
```

O indicador mede:

```text
velocidade da variação do percentual vendido
em pontos percentuais por hora
```

Fórmula conceitual:

```text
momentum
=
soldPercentageDelta
/
elapsedHours
```

A implementação utiliza:

```text
soldPercentageDelta × 3600
--------------------------
elapsedSeconds
```

para não perder precisão em intervalos que não sejam horas inteiras.

Precisão:

```text
scale = 4
rounding = HALF_UP
```

---

## 13. Exemplo do `MOMENTUM_V1`

Exemplo:

```text
10:00 → 62%
13:00 → 68%
```

Delta:

```text
+6 p.p.
```

Intervalo:

```text
3 horas
```

Momentum:

```text
6 / 3
=
2.0000 p.p./hora
```

Momentum negativo é permitido.

Não existe clamp para zero.

---

## 14. Zero observado versus indisponibilidade

A FASE 11 preserva a mesma distinção semântica usada anteriormente:

```text
ausência != zero observado
```

Exemplo de momentum disponível:

```text
sold delta = 0
momentum = 0.0000
```

Isso significa que havia evidência suficiente e não ocorreu variação.

Exemplo de indisponibilidade:

```text
soldPercentage ausente
```

Nesse cenário:

```text
momentum = null
```

Os casos não representam a mesma informação.

---

## 15. `MomentumResult`

Foi criado:

```text
MomentumResult
```

O resultado pode ser:

```text
AVAILABLE
```

ou:

```text
UNAVAILABLE
```

Motivos atualmente modelados:

```text
NO_PREVIOUS_SNAPSHOT
SOLD_PERCENTAGE_UNAVAILABLE
```

O resultado preserva a versão do algoritmo mesmo quando o cálculo não produz valor.

---

## 16. Primeira observação

Quando um ASIN ainda não possui snapshot anterior:

```text
SnapshotEvolution não existe
```

e:

```text
MomentumResult
status = UNAVAILABLE
reason = NO_PREVIOUS_SNAPSHOT
version = MOMENTUM_V1
```

Em `DealEvaluation`, o contrato permanece:

```text
momentum = null
momentumVersion = null
```

A tentativa versionada permanece preservada na auditoria específica de momentum.

---

## 17. Momentum com percentual indisponível

Quando há snapshot anterior, mas alguma observação não possui percentual vendido:

```text
SnapshotEvolution existe
```

mas:

```text
soldPercentageDelta = null
```

O resultado fica:

```text
status = UNAVAILABLE
reason = SOLD_PERCENTAGE_UNAVAILABLE
```

Outros fatos históricos disponíveis, como preço e tempo, continuam preservados.

---

## 18. Momentum não altera elegibilidade

A FASE 11 mantém explicitamente:

```text
momentum ≠ filtro
```

Uma oferta inelegível pode possuir momentum.

Exemplo:

```text
seller terceiro
→ eligible = false

histórico suficiente
→ momentum = 2.0000
```

O momentum não transforma a oferta em elegível.

---

## 19. Momentum não altera `SCORE_V1`

Também permanece:

```text
momentum ≠ score
```

O `SCORE_V1` continua utilizando somente:

```text
SOLD_PERCENTAGE
CASH_DISCOUNT
RATING
REVIEW_COUNT
```

com os mesmos pesos e regras definidos na FASE 10.

O momentum não é incluído como quinto fator.

Uma eventual combinação futura entre score e momentum exige novo contrato/versionamento.

---

## 20. Orquestração do cálculo histórico

Foi criado:

```text
MomentumCalculationService
```

Fluxo:

```text
OfferSnapshot atual persistido
        ↓
OfferHistoryQueryPort
        ↓
snapshot anterior
        ↓
SnapshotEvolutionCalculator
        ↓
SnapshotEvolution
        ↓
MomentumEngine
        ↓
MomentumResult
        ↓
MomentumCalculation
```

O snapshot atual não é relido desnecessariamente do banco.

Somente a observação anterior precisa ser consultada.

---

## 21. Integração com `DealEvaluation`

`AmazonDealEvaluationApplicationService` passou a calcular momentum depois das regras e do score.

Fluxo conceitual:

```text
elegibilidade estrutural
        ↓
filtros comerciais
        ↓
score, se elegível
        ↓
cálculo histórico
        ↓
MOMENTUM_V1
        ↓
DealEvaluation
```

A tentativa de momentum ocorre independentemente de:

```text
eligible = true
```

ou:

```text
eligible = false
```

---

## 22. Auditoria persistente de momentum

A migration:

```text
V9__momentum_audit.sql
```

criou:

```text
deal_evaluation_momentum_audit
```

A tabela preserva:

```text
deal_evaluation_id
calculation_version
status
unavailable_reason
previous_offer_snapshot_id
elapsed_seconds
sold_percentage_delta
current_price_delta
current_price_delta_percentage
cash_discount_delta
momentum
created_at
```

Assim é possível explicar como o indicador foi produzido.

---

## 23. Separação entre resultado agregado e auditoria

`deal_evaluation` continua armazenando:

```text
momentum
momentum_version
```

para acesso agregado.

A tabela:

```text
deal_evaluation_momentum_audit
```

preserva a base histórica do cálculo.

Exemplo disponível:

```text
deal_evaluation
momentum = 2.0000
momentum_version = MOMENTUM_V1

audit
status = AVAILABLE
previous_offer_snapshot_id = ...
elapsed_seconds = 10800
sold_percentage_delta = 6
momentum = 2.0000
```

Exemplo indisponível:

```text
deal_evaluation
momentum = null
momentum_version = null

audit
calculation_version = MOMENTUM_V1
status = UNAVAILABLE
unavailable_reason = NO_PREVIOUS_SNAPSHOT
```

---

## 24. Ordem transacional

A persistência ocorre na seguinte ordem:

```text
calcular avaliação
        ↓
persistir DealEvaluation
        ↓
obter deal_evaluation.id
        ↓
construir MomentumAudit
        ↓
persistir MomentumAudit
```

Todos os repositories envolvidos utilizam a mesma `Connection`.

Assim, a avaliação e sua auditoria pertencem à mesma unidade transacional.

Uma falha na auditoria pode provocar rollback da operação completa.

---

## 25. Recorrência

Foi criado:

```text
OfferHistoryStatus
```

Uma oferta é considerada recorrente quando:

```text
snapshotCount > 1
```

Não existe heurística subjetiva ou inferência por preço.

Recorrência representa somente a existência de múltiplas observações históricas do mesmo ASIN.

---

## 26. Primeira detecção

`OfferHistoryStatus` preserva:

```text
firstDetectedAt
```

obtido a partir de:

```text
MIN(offer_snapshot.collected_at)
```

Também permite calcular:

```text
timeSinceFirstDetection(referenceTime)
```

O instante de referência é explícito para manter determinismo e testabilidade.

---

## 27. Última atualização

O status histórico preserva:

```text
lastUpdatedAt
```

obtido por:

```text
MAX(offer_snapshot.collected_at)
```

Também permite calcular:

```text
timeSinceLastUpdate(referenceTime)
```

---

## 28. Detecção de oferta já publicada

A FASE 11 definiu:

```text
já publicada
=
existe Publication
do mesmo ASIN
com status = PUBLISHED
```

Estados:

```text
CREATED
READY
FAILED
```

não representam publicação concluída.

Portanto:

```text
READY != publishedBefore
```

e:

```text
PUBLISHED = publishedBefore
```

---

## 29. `OfferHistoryStatusQueryPort`

Foi criada a porta:

```text
OfferHistoryStatusQueryPort
```

Implementação:

```text
OfferHistoryStatusJdbcRepository
```

A consulta agrega em uma ida ao banco:

```text
snapshotCount
firstDetectedAt
lastUpdatedAt
publishedBefore
```

O read model não altera nenhuma entidade do domínio.

---

## 30. Prevenção de multiplicação de linhas

A consulta de publicação utiliza:

```sql
EXISTS (...)
```

em vez de colocar `publication` no join principal da agregação histórica.

Isso evita que múltiplas publicações de uma mesma avaliação multipliquem artificialmente:

```text
COUNT
MIN
MAX
```

dos snapshots.

---

## 31. Índices históricos

A migration:

```text
V10__historical_read_indexes.sql
```

adicionou índices específicos para os novos caminhos de leitura.

Índice histórico:

```text
idx_offer_snapshot_history

(product_id, collected_at DESC, id DESC)
```

Atende:

```text
snapshot anterior
primeiro snapshot
último snapshot
histórico ordenado
```

---

## 32. Índices de avaliação e publicação

Também foram adicionados:

```text
idx_deal_evaluation_offer_snapshot
```

sobre:

```text
deal_evaluation(offer_snapshot_id)
```

e:

```text
idx_publication_evaluation_status
```

sobre:

```text
publication(deal_evaluation_id, status)
```

Isso prepara os caminhos utilizados para detectar publicações históricas.

---

## 33. Idempotência preservada

A identidade persistente do snapshot continua:

```text
product_id
+
collected_at
+
source
```

Um reprocessamento da mesma observação não cria novo snapshot.

Consequentemente, também não duplica:

```text
PaymentConditions
Evidence
DealEvaluation
RuleResults
ScoreFactors
MomentumAudit
```

O teste vertical da FASE 11 comprova esse comportamento.

---

## 34. Teste vertical de momentum

Foi criado:

```text
AmazonDealProcessingMomentumEndToEndTest
```

O teste utiliza servidor HTTP local e o fluxo real de produção.

Cenário:

```text
10:00 → 62% vendidos
13:00 → 68% vendidos
```

Resultado esperado:

```text
delta = +6 p.p.
elapsed = 3 horas
momentum = 2.0000 p.p./hora
```

---

## 35. Primeira coleta no teste vertical

A primeira observação produz:

```text
DealEvaluation
momentum = null
momentumVersion = null
```

e auditoria:

```text
calculationVersion = MOMENTUM_V1
status = UNAVAILABLE
unavailableReason = NO_PREVIOUS_SNAPSHOT
```

---

## 36. Segunda coleta no teste vertical

A segunda observação produz:

```text
soldPercentageDelta = 6
elapsedSeconds = 10800
momentum = 2.0000
momentumVersion = MOMENTUM_V1
```

Auditoria:

```text
status = AVAILABLE
previousOfferSnapshotId = snapshot anterior
soldPercentageDelta = 6
momentum = 2.0000
```

---

## 37. Score preservado no teste vertical

O teste comprova simultaneamente que:

```text
SCORE_V1
```

continua funcionando normalmente.

Primeira observação:

```text
soldPercentage = 62
score = 58.2500
```

Segunda observação:

```text
soldPercentage = 68
score = 60.0500
```

A diferença decorre somente da alteração do fato atual:

```text
soldPercentage
```

O momentum não entra na fórmula do score.

---

## 38. Reprocessamento no teste vertical

A segunda observação é processada novamente com a mesma identidade:

```text
ASIN
collectedAt
source
```

Resultado final:

```text
2 snapshots
2 DealEvaluations
2 MomentumAudits
```

Nenhuma terceira observação é criada.

---

## 39. Testes de domínio

A fase adicionou cobertura específica para:

```text
SnapshotEvolutionCalculator
MomentumEngine
MomentumCalculationService
MomentumAudit
```

São validados, entre outros:

```text
delta positivo
delta negativo
delta zero
percentual vendido ausente
desconto ausente
preço anterior zero
intervalo fracionário
arredondamento HALF_UP
momentum positivo
momentum negativo
momentum zero
primeira observação
```

---

## 40. Testes de persistência

Foram adicionados testes JDBC para:

```text
OfferHistoryJdbcRepository
MomentumAuditJdbcRepository
OfferHistoryStatusJdbcRepository
```

São comprovados:

```text
histórico por ASIN
snapshot anterior
primeiro snapshot
último snapshot
contagem
auditoria AVAILABLE
auditoria NO_PREVIOUS_SNAPSHOT
auditoria SOLD_PERCENTAGE_UNAVAILABLE
recorrência
primeira detecção
última atualização
Publication READY
Publication PUBLISHED
```

---

## 41. Testes de migrations

Foram adicionados:

```text
MomentumAuditMigrationTest
HistoricalReadIndexesMigrationTest
```

Eles verificam os contratos estruturais das migrations:

```text
V9
V10
```

incluindo:

```text
tabela
colunas
constraints
índices
versão Flyway aplicada
```

---

## 42. Estado das migrations

Ao final da FASE 11:

```text
Migrations: 10
Schema: versão 10
```

Migrations introduzidas:

```text
V9__momentum_audit.sql
V10__historical_read_indexes.sql
```

As migrations anteriores não foram modificadas retroativamente.

---

## 43. Resultado da suíte local

Gate final da FASE 11:

```text
Tests run: 422
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

Também foi executado:

```text
git diff --check
```

sem erros.

Estado final:

```text
working tree clean
```

---

## 44. Crescimento da suíte

Baseline da FASE 10:

```text
391 testes
```

Gate final da FASE 11:

```text
422 testes
```

A FASE 11 adicionou:

```text
31 testes líquidos
```

mantendo:

```text
0 falhas
0 erros
0 ignorados
```

---

## 45. Commits da branch

Branch:

```text
fase-11-historico-momentum
```

Commits observados sobre `main`:

```text
f1d8227 docs: define semantica de historico e momentum
33f6921 feat: adiciona consulta historica de ofertas
ecc49b9 feat: calcula evolucao entre snapshots
867881b feat: implementa momentum v1
4217f90 feat: adiciona auditoria persistente de momentum
fac1758 feat: persiste auditoria de momentum
ac3a866 feat: persiste auditoria de momentum
ec1ae59 feat: orquestra calculo historico de momentum
d259a71 feat: integra momentum na avaliacao de ofertas
87e45d7 test: valida momentum no fluxo vertical
356e431 feat: adiciona status historico de ofertas
0fd47a9 perf: adiciona indices para consultas historicas
```

Os commits representam a evolução incremental da fase.

---

## 46. Tamanho da alteração

Comparação:

```text
main...fase-11-historico-momentum
```

Resultado:

```text
32 files changed
9795 insertions
81 deletions
```

A maior parte do crescimento corresponde a:

```text
contratos históricos
domínio temporal
persistência
migrations
testes unitários
testes JDBC
teste vertical
documentação semântica
```

---

## 47. Critérios de conclusão

Critério:

```text
consultar histórico de um ASIN
```

Resultado:

```text
ATENDIDO
```

Implementado por:

```text
OfferHistoryQueryPort
OfferHistoryJdbcRepository
```

---

Critério:

```text
calcular variação entre dois snapshots
```

Resultado:

```text
ATENDIDO
```

Implementado por:

```text
SnapshotEvolutionCalculator
SnapshotEvolution
```

com:

```text
soldPercentageDelta
currentPriceDelta
currentPriceDeltaPercentage
cashDiscountDelta
elapsedSeconds
```

---

Critério:

```text
produzir indicador de momentum
sem alterar a regra principal de elegibilidade
```

Resultado:

```text
ATENDIDO
```

Implementado por:

```text
MOMENTUM_V1
```

e comprovado por testes em ofertas:

```text
elegíveis
inelegíveis
```

sem modificar a decisão estrutural/comercial.

---

## 48. Requisitos complementares da fase

Também foram atendidos:

```text
tempo desde primeira detecção
tempo desde última atualização
detecção de recorrência
detecção de publicação anterior
```

por:

```text
OfferHistoryStatus
OfferHistoryStatusQueryPort
OfferHistoryStatusJdbcRepository
```

---

## 49. Separação arquitetural preservada

A FASE 11 mantém:

```text
SQL
→ lê fatos

domínio
→ calcula evolução e momentum

aplicação
→ orquestra

infraestrutura
→ persiste
```

O domínio de momentum não conhece:

```text
JDBC
PostgreSQL
Flyway
HTML
HTTP
Publication repository
```

---

## 50. O que deliberadamente não foi implementado

A FASE 11 não implementou:

```text
combinação de momentum com SCORE_V1
novo ranking baseado em momentum
aceleração de segunda ordem
previsão de vendas
machine learning
paginação da interface de histórico
scheduler
fila
workers assíncronos
publicação automática
controle de frequência por canal
dashboard
```

O histórico completo ainda pode ser consultado pelo repository.

Paginação deverá ser introduzida quando o histórico for exposto pela interface operacional, evitando antecipar um contrato de apresentação sem consumidor definido.

---

## 51. Relação com fases futuras

A FASE 12 deverá tratar:

```text
orquestração
processamento assíncrono
reprocessamento por etapa
jobs
fila quando necessária
múltiplos workers
```

A FASE 13 poderá utilizar as leituras históricas implementadas nesta fase para expor:

```text
histórico
momentum
recorrência
tempos
publicação anterior
```

na interface operacional.

---

## 52. Estado do CI

Neste momento:

```text
validação local = APROVADA
```

Ainda não devem ser registrados como concluídos:

```text
CI remoto da branch
Pull Request
merge em main
CI pós-merge
```

Esses fatos somente deverão ser adicionados após ocorrerem de fato.

---

## 53. Estado consolidado

```text
FASE 11 — Histórico, evolução e momentum

STATUS:
CONCLUÍDA LOCALMENTE

Histórico por ASIN:
IMPLEMENTADO

Snapshot anterior:
IMPLEMENTADO

Primeira observação:
IMPLEMENTADA

Última observação:
IMPLEMENTADA

Contagem de snapshots:
IMPLEMENTADA

Variação de vendidos:
IMPLEMENTADA

Variação absoluta de preço:
IMPLEMENTADA

Variação percentual de preço:
IMPLEMENTADA

Variação de desconto:
IMPLEMENTADA

Tempo desde primeira detecção:
IMPLEMENTADO

Tempo desde última atualização:
IMPLEMENTADO

Recorrência:
IMPLEMENTADA

Detecção de publicação anterior:
IMPLEMENTADA

MOMENTUM_V1:
IMPLEMENTADO

Fórmula:
soldPercentageDelta × 3600 / elapsedSeconds

Unidade:
pontos percentuais por hora

Matemática:
BigDecimal
scale = 4
HALF_UP

Momentum negativo:
PERMITIDO

Ausência:
DIFERENTE DE ZERO OBSERVADO

Elegibilidade:
NÃO ALTERADA

SCORE_V1:
NÃO ALTERADO

Auditoria:
IMPLEMENTADA

Migration de auditoria:
V9

Índices históricos:
V10

Schema:
versão 10

Fluxo vertical:
OK

Idempotência:
OK

Transações:
OK

PostgreSQL:
OK

Suíte hermética:
422 testes
0 falhas
0 erros
0 ignorados

Build:
SUCCESS

Working tree:
CLEAN

CI remoto:
PENDENTE

PR:
PENDENTE

Merge em main:
PENDENTE

Gate local da FASE 11:
FECHADO

Próxima fase:
FASE 12 — Orquestração e processamento assíncrono
```

---

## 54. Encerramento local

A FASE 11 atingiu seus critérios técnicos locais.

O projeto passou de avaliações baseadas somente na observação atual para um modelo capaz de explicar também a evolução temporal da oferta.

A separação central permanece:

```text
SCORE_V1
=
qualidade/prioridade da observação atual

MOMENTUM_V1
=
velocidade histórica do percentual vendido
```

Os dois conceitos permanecem independentes.

A próxima fase somente deve ser iniciada após o fechamento do ciclo remoto da FASE 11:

```text
commit documental
        ↓
push da branch
        ↓
Pull Request
        ↓
CI verde
        ↓
merge em main
        ↓
CI da main
        ↓
registro final
        ↓
FASE 12
```
