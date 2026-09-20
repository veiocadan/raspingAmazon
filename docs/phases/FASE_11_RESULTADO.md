# FASE 11 — RESULTADO CONSOLIDADO

**Projeto:** Rasping Amazon  
**Fase:** 11 — Histórico, evolução e momentum  
**Data:** 20/09/2026  
**Status:** CONCLUÍDA — validação local, CI remoto do Pull Request, merge em `main` e CI pós-merge aprovados

---

## 1. Objetivo

A FASE 11 tornou o histórico parte obrigatória do processamento das ofertas.

A fase passou a responder perguntas temporais que o `SCORE_V1` deliberadamente não responde.

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

Identidade persistente:

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

Comparação temporal:

```text
previous.collectedAt < current.collectedAt
```

O snapshot atual nunca é utilizado como seu próprio predecessor.

---

## 4. Consulta histórica por ASIN

Porta:

```text
OfferHistoryQueryPort
```

Implementação:

```text
OfferHistoryJdbcRepository
```

Consultas:

```text
findHistoryByAsin
findFirstByAsin
findLatestByAsin
findPreviousByAsin
countByAsin
```

A aplicação não conhece SQL nem JDBC.

---

## 5. Projeção histórica

Foi criada:

```text
HistoricalOfferObservation
```

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

Ela representa apenas os dados necessários para comparação temporal.

---

## 6. Evolução entre snapshots

Foram criados:

```text
SnapshotEvolution
SnapshotEvolutionCalculator
```

O cálculo produz:

```text
soldPercentageDelta
currentPriceDelta
currentPriceDeltaPercentage
cashDiscountDelta
elapsedSeconds
```

O componente é puro:

```text
não consulta banco
não calcula score
não decide elegibilidade
não publica
não calcula momentum
```

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

Se algum percentual estiver ausente:

```text
soldPercentageDelta = null
```

Ausência não é zero observado.

---

## 8. Variação de preço

Variação absoluta:

```text
currentPriceDelta
=
currentPrice
-
previousPrice
```

Variação percentual:

```text
currentPriceDeltaPercentage
=
currentPriceDelta
/
previousPrice
×
100
```

Matemática:

```text
scale = 4
HALF_UP
```

Quando o preço anterior é zero:

```text
currentPriceDeltaPercentage = null
```

---

## 9. Variação de desconto à vista

A FASE 11 reutiliza:

```text
BestCashDiscountSelector
```

São consideradas apenas condições:

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

Ausência permanece `null`.

---

## 10. Intervalo temporal

`SnapshotEvolution` preserva:

```text
previousCollectedAt
currentCollectedAt
elapsedSeconds
```

Relação obrigatória:

```text
currentCollectedAt > previousCollectedAt
```

---

## 11. `MOMENTUM_V1`

Foi criado:

```text
MomentumEngine
```

Versão:

```text
MOMENTUM_V1
```

Fórmula:

```text
momentum
=
soldPercentageDelta × 3600
/
elapsedSeconds
```

Unidade:

```text
pontos percentuais por hora
```

Precisão:

```text
BigDecimal
scale = 4
HALF_UP
```

Momentum negativo é permitido.

---

## 12. Exemplo de momentum

```text
10:00 → 62%
13:00 → 68%

delta = +6 p.p.
intervalo = 3 horas

momentum = 2.0000 p.p./hora
```

---

## 13. Zero versus indisponibilidade

Preservado:

```text
ausência != zero observado
```

Exemplo disponível:

```text
sold delta = 0
momentum = 0.0000
```

Exemplo indisponível:

```text
soldPercentage ausente
momentum = null
```

---

## 14. `MomentumResult`

Estados:

```text
AVAILABLE
UNAVAILABLE
```

Motivos atuais:

```text
NO_PREVIOUS_SNAPSHOT
SOLD_PERCENTAGE_UNAVAILABLE
```

A versão do algoritmo permanece auditável mesmo quando o valor não pode ser calculado.

---

## 15. Primeira observação

Quando não existe snapshot anterior:

```text
MomentumResult
status = UNAVAILABLE
reason = NO_PREVIOUS_SNAPSHOT
version = MOMENTUM_V1
```

Em `DealEvaluation`:

```text
momentum = null
momentumVersion = null
```

A tentativa versionada permanece na auditoria.

---

## 16. Momentum não altera elegibilidade

Regra:

```text
momentum ≠ filtro
```

Uma oferta inelegível pode possuir momentum.

Momentum não transforma uma oferta em elegível.

---

## 17. Momentum não altera `SCORE_V1`

Regra:

```text
momentum ≠ score
```

O `SCORE_V1` continua usando somente:

```text
SOLD_PERCENTAGE
CASH_DISCOUNT
RATING
REVIEW_COUNT
```

Momentum não é quinto fator.

---

## 18. Orquestração

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

---

## 19. Integração com `DealEvaluation`

Fluxo:

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

O cálculo histórico ocorre independentemente de:

```text
eligible = true
```

ou:

```text
eligible = false
```

---

## 20. Auditoria persistente

Migration:

```text
V9__momentum_audit.sql
```

Tabela:

```text
deal_evaluation_momentum_audit
```

Campos principais:

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

---

## 21. Ordem transacional

Persistência:

```text
calcular avaliação
        ↓
persistir DealEvaluation
        ↓
obter deal_evaluation.id
        ↓
persistir MomentumAudit
```

Todos os repositories envolvidos utilizam a mesma `Connection`.

Falha na auditoria pode causar rollback da unidade completa.

---

## 22. Recorrência

Foi criado:

```text
OfferHistoryStatus
```

Uma oferta é recorrente quando:

```text
snapshotCount > 1
```

Não existe heurística subjetiva.

---

## 23. Primeira detecção e última atualização

São preservados:

```text
firstDetectedAt
lastUpdatedAt
```

Obtidos por:

```text
MIN(offer_snapshot.collected_at)
MAX(offer_snapshot.collected_at)
```

Também são calculáveis:

```text
timeSinceFirstDetection(referenceTime)
timeSinceLastUpdate(referenceTime)
```

---

## 24. Oferta já publicada

Regra:

```text
já publicada
=
existe Publication
do mesmo ASIN
com status = PUBLISHED
```

Portanto:

```text
CREATED != publicada
READY != publicada
FAILED != publicada
PUBLISHED = publicada
```

---

## 25. Status histórico

Porta:

```text
OfferHistoryStatusQueryPort
```

Implementação:

```text
OfferHistoryStatusJdbcRepository
```

A consulta agrega:

```text
snapshotCount
firstDetectedAt
lastUpdatedAt
publishedBefore
```

em uma única ida ao banco.

---

## 26. Prevenção de multiplicação de linhas

A consulta utiliza:

```sql
EXISTS (...)
```

para detectar publicação anterior.

Isso evita multiplicar artificialmente:

```text
COUNT
MIN
MAX
```

dos snapshots.

---

## 27. Índices históricos

Migration:

```text
V10__historical_read_indexes.sql
```

Índices:

```text
idx_offer_snapshot_history
(product_id, collected_at DESC, id DESC)

idx_deal_evaluation_offer_snapshot
(offer_snapshot_id)

idx_publication_evaluation_status
(deal_evaluation_id, status)
```

---

## 28. Idempotência

Identidade:

```text
product_id
+
collected_at
+
source
```

Reprocessar a mesma observação não duplica:

```text
PaymentConditions
Evidence
DealEvaluation
RuleResults
ScoreFactors
MomentumAudit
```

---

## 29. Teste vertical

Foi criado:

```text
AmazonDealProcessingMomentumEndToEndTest
```

Cenário:

```text
primeira coleta → 62%
segunda coleta → 68%
intervalo → 3 horas
```

Resultado:

```text
soldPercentageDelta = 6
elapsedSeconds = 10800
momentum = 2.0000
momentumVersion = MOMENTUM_V1
```

---

## 30. Primeira avaliação vertical

```text
momentum = null
momentumVersion = null
```

Auditoria:

```text
calculationVersion = MOMENTUM_V1
status = UNAVAILABLE
unavailableReason = NO_PREVIOUS_SNAPSHOT
```

---

## 31. Segunda avaliação vertical

```text
momentum = 2.0000
momentumVersion = MOMENTUM_V1
```

Auditoria:

```text
status = AVAILABLE
previousOfferSnapshotId = snapshot anterior
soldPercentageDelta = 6
elapsedSeconds = 10800
```

---

## 32. Score preservado

Primeira observação:

```text
score = 58.2500
```

Segunda observação:

```text
score = 60.0500
```

A mudança decorre da alteração do `soldPercentage` atual.

Momentum não entra na fórmula do score.

---

## 33. Reprocessamento vertical

Após reprocessar a segunda observação:

```text
2 snapshots
2 DealEvaluations
2 MomentumAudits
```

Nenhuma terceira observação é criada.

---

## 34. Testes adicionados

Cobertura específica para:

```text
SnapshotEvolutionCalculator
MomentumEngine
MomentumCalculationService
MomentumAudit
OfferHistoryJdbcRepository
MomentumAuditJdbcRepository
OfferHistoryStatusJdbcRepository
MomentumAuditMigrationTest
HistoricalReadIndexesMigrationTest
AmazonDealProcessingMomentumEndToEndTest
```

---

## 35. Estado das migrations

Ao final:

```text
Migrations: 10
Schema: versão 10
```

Novas migrations:

```text
V9__momentum_audit.sql
V10__historical_read_indexes.sql
```

Migrations anteriores permanecem imutáveis.

---

## 36. Gate local

Resultado:

```text
Tests run: 422
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

Também validado:

```text
git diff --check
working tree clean
```

---

## 37. Crescimento da suíte

Baseline da FASE 10:

```text
391 testes
```

FASE 11:

```text
422 testes
```

Crescimento líquido:

```text
31 testes
```

---

## 38. Commits da branch

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
0ac1908 docs: encerra localmente a fase 11
cbfeb96 docs: atualiza readme apos fase 11
9c7007f docs: registra ci remoto verde da fase 11
7aa3319 docs: registra ci remoto verde da fase 11
```

Merge:

```text
46d7e4c
Merge pull request #2 from veiocadan/fase-11-historico-momentum
```

---

## 39. Pull Request

Pull Request:

```text
#2 — FASE 11 — Histórico, evolução e momentum
```

Base:

```text
main
```

Head:

```text
fase-11-historico-momentum
```

Estado final:

```text
MERGED
```

---

## 40. CI remoto do Pull Request

Execuções relevantes:

```text
run #18 → SUCCESS
run #20 → SUCCESS
```

O head final antes do merge foi:

```text
7aa3319
```

O último check do PR ficou verde antes do merge.

---

## 41. Merge em `main`

Merge commit:

```text
46d7e4c
```

Mensagem:

```text
Merge pull request #2 from veiocadan/fase-11-historico-momentum
```

Estado:

```text
SUCCESS
```

---

## 42. CI pós-merge

O merge disparou o workflow principal da `main`.

Execução:

```text
GitHub Actions run #21
```

Head:

```text
46d7e4c
```

Resultado:

```text
status = completed
conclusion = success
Maven tests = success
```

O aviso do runner sobre futura migração de `ubuntu-latest` é somente informativo.

---

## 43. Critérios de conclusão

```text
consultar histórico de um ASIN
→ ATENDIDO

calcular variação entre dois snapshots
→ ATENDIDO

produzir indicador de momentum
sem alterar a regra principal de elegibilidade
→ ATENDIDO

tempo desde primeira detecção
→ ATENDIDO

tempo desde última atualização
→ ATENDIDO

detecção de recorrência
→ ATENDIDO

detecção de publicação anterior
→ ATENDIDO
```

---

## 44. Separação arquitetural

Preservado:

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

O domínio de momentum não conhece JDBC, PostgreSQL, Flyway, HTML ou HTTP.

---

## 45. Itens deliberadamente fora da FASE 11

Não foram implementados:

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

Também permanece fora do `SCORE_V1`:

```text
PRICE_ATTRACTIVENESS
```

---

## 46. Estado final do CI

```text
validação local = SUCCESS
CI remoto do Pull Request #2 = SUCCESS
Pull Request #2 = MERGED
merge em main = SUCCESS
merge commit = 46d7e4c
CI pós-merge = SUCCESS
GitHub Actions run = #21
```

Todos os gates previstos foram concluídos.

---

## 47. Estado consolidado

```text
FASE 11 — Histórico, evolução e momentum

STATUS:
CONCLUÍDA

Histórico por ASIN:
IMPLEMENTADO

Evolução entre snapshots:
IMPLEMENTADA

Variação de vendidos:
IMPLEMENTADA

Variação de preço:
IMPLEMENTADA

Variação de desconto:
IMPLEMENTADA

Primeira detecção:
IMPLEMENTADA

Última atualização:
IMPLEMENTADA

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

Suíte hermética:
422 testes
0 falhas
0 erros
0 ignorados

Build local:
SUCCESS

CI remoto:
SUCCESS

Pull Request:
#2 — MERGED

Merge em main:
SUCCESS — 46d7e4c

CI pós-merge:
SUCCESS — run #21

Gate local:
FECHADO

Gate remoto:
FECHADO

Próxima fase:
FASE 12 — Orquestração e processamento assíncrono
```

---

## 48. Encerramento da FASE 11

A FASE 11 atingiu seus critérios técnicos locais e remotos.

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

Ciclo concluído:

```text
implementação
        ↓
422 testes locais verdes
        ↓
Pull Request #2
        ↓
CI remoto verde
        ↓
merge em main
        ↓
46d7e4c
        ↓
CI da main verde
        ↓
run #21
        ↓
FASE 11 CONCLUÍDA
```

A próxima etapa do projeto é:

```text
FASE 12 — Orquestração e processamento assíncrono
```
