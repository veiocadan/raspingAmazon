# FASE 16 — RESULTADO CONSOLIDADO

**Projeto:** Rasping Amazon  
**Fase:** 16 — Observabilidade, auditoria e operação  
**Data:** 27/09/2026  
**Status local:** CONCLUÍDA — gate técnico, suíte hermética e prova real aprovados  
**Status remoto:** PENDENTE — branch ainda não publicada/validada por Pull Request e CI remoto

---

## 1. Fonte de verdade da fase

A FASE 16 foi conduzida a partir de:

```text
rasping_Amazon_projeto_revisado.pdf
RELATORIO_AUDITORIA_RASPING_AMAZON_2026-09-17.md
README.md
docs/adr/
docs/phases/
estado real do repositório após a FASE 15
```

A fonte primária define para a FASE 16:

```text
logs estruturados
métricas de quantidade coletada, enriquecida, rejeitada e publicada
métricas de latência e falhas por integração
correlação por execução, ASIN e publicação
auditoria de decisões relevantes
alertas para falhas recorrentes
alertas para mudanças suspeitas na coleta
dashboard operacional simples
```

Critérios oficiais de conclusão:

```text
é possível responder o que aconteceu em uma execução
sem depender de planilhas

falhas externas podem ser diferenciadas de falhas internas
```

O relatório de auditoria acrescenta como refinamentos:

```text
métricas
dashboards
alertas
duração por etapa
taxa de descarte
falhas por fonte
taxa de UNKNOWN
volume de publicação
correlação por run_id
```

A implementação preservou a fonte primária como critério obrigatório.

Os refinamentos sem semântica formal suficiente não receberam fórmulas arbitrárias nesta fase.

---

## 2. Baseline e branch

Baseline da FASE 16:

```text
64e544c
Merge pull request #6 from veiocadan/feat/fase-15-qualidade-integrada
```

Branch:

```text
feat/fase-16-observabilidade
```

Estado local ao final do gate técnico:

```text
working tree clean
```

---

## 3. ADR da FASE 16

Foi criado:

```text
docs/adr/0011-observabilidade-correlacao-e-metricas-operacionais.md
```

Status:

```text
ACEITA
```

Decisões centrais:

```text
PostgreSQL
→ verdade operacional durável

logs estruturados
→ evidência complementar

métricas de integração
→ persistidas quando necessário

domínio
→ sem dependência de vendor de logging/métricas

observabilidade
→ explica o que aconteceu
→ não redefine decisões comerciais
```

A FASE 16 não introduziu uma plataforma externa de métricas apenas por conveniência.

---

## 4. Princípio de correlação

A observabilidade utiliza identidades reais já conhecidas pelo fluxo.

Contexto suportado:

```text
runId
jobId
jobType
candidateId
snapshotId
evaluationId
publicationId
asin
integration
```

Regra:

```text
não inferir ProcessingRun por ASIN
```

O mesmo ASIN pode aparecer em múltiplas execuções.

A correlação deve acompanhar as identidades reais do processamento.

---

## 5. Correlação durável do pipeline

A migration:

```text
V20__processing_observability_correlation.sql
```

completou a correlação necessária entre:

```text
ProcessingRun
      ↓
DealCandidate
      ↓
OfferSnapshot
      ↓
DealEvaluation
      ↓
Publication
```

`DealCandidate` passou a preservar o vínculo com o `OfferSnapshot` produzido pelo enriquecimento.

O vínculo é:

```text
idempotente quando repetido com o mesmo snapshot
```

e:

```text
rejeitado quando tenta reassociar o candidato
a outro snapshot incompatível
```

---

## 6. Índice de leitura operacional

Foi adicionada:

```text
V21__processing_run_observability_read_index.sql
```

Objetivo:

```text
suportar leitura operacional por ProcessingRun
```

sem alterar regras de negócio.

---

## 7. Observações de integração

Foi adicionada:

```text
V22__integration_observation.sql
```

Tabela:

```text
integration_observation
```

Campos operacionais incluem:

```text
observed_at
integration
operation
outcome
duration_ms

processing_run_id
processing_job_id
job_type
deal_candidate_id
offer_snapshot_id
deal_evaluation_id
publication_id
asin

failure_origin
failure_type
error_code
http_status_code
```

A tabela não persiste:

```text
segredos
payload HTTP completo
HTML coletado
credenciais
```

---

## 8. Resultado de integração

Resultado suportado:

```text
SUCCESS
FAILURE
```

Uma observação de sucesso não carrega metadados de falha.

Uma observação de falha preserva:

```text
failureOrigin
failureType
errorCode
```

quando aplicável.

---

## 9. Origem da falha

A FASE 16 formalizou:

```text
OperationalFailureOrigin.EXTERNAL
OperationalFailureOrigin.INTERNAL
```

Essa dimensão é independente da classificação do pipeline:

```text
ProcessingFailureType.TRANSIENT
ProcessingFailureType.PERMANENT
```

Portanto:

```text
EXTERNAL / INTERNAL
→ origem operacional

TRANSIENT / PERMANENT
→ política de processamento/retry
```

As duas dimensões não foram fundidas.

---

## 10. Latência de integração

A duração é observada na fronteira externa real.

Integrações instrumentadas:

```text
amazon-deals-http
amazon-product-page
```

Regra:

```text
duration_ms
→ mede a operação externa observada

não mede artificialmente toda a duração do job
```

Isso evita misturar:

```text
espera
retry
persistência
regra de negócio
latência externa
```

em um único número sem semântica clara.

---

## 11. Coleta observável

`HttpCollectionCollector` passou a registrar observações da integração:

```text
amazon-deals-http
```

Contexto propagado quando conhecido:

```text
processingRunId
jobType = COLLECT_DEALS
```

A integração continua responsável por medir sua própria latência.

---

## 12. Enriquecimento observável

`AmazonProductPageEnrichmentClient` passou a registrar:

```text
amazon-product-page
```

Contexto propagado quando conhecido:

```text
processingRunId
jobType = ENRICH_DEAL
dealCandidateId
asin
```

O `OfferSnapshot` ainda não existe no momento da chamada externa e, por isso, não é inventado no contexto.

---

## 13. Propagação de contexto

Os contratos:

```text
CollectionCollector
ProductEnrichmentClient
```

receberam variantes contextuais compatíveis com os contratos existentes.

Foi preservado:

```text
um único método abstrato
```

onde necessário para manter compatibilidade com lambdas e implementações existentes.

A correlação é passada por invocação.

Não foi introduzido:

```text
ThreadLocal
estado mutável global
contexto compartilhado entre requests
```

---

## 14. Logs estruturados

Foram introduzidos:

```text
OperationalLogLevel
OperationalFailureOrigin
OperationalLogContext
OperationalLogEvent
StructuredOperationalLogPort
JsonStructuredOperationalLogAdapter
```

Formato:

```text
JSON Lines
```

Campos estruturados podem incluir:

```text
timestamp
level
event
component
operation
runId
jobId
jobType
candidateId
snapshotId
evaluationId
publicationId
asin
integration
outcome
durationMs
failureOrigin
failureType
errorCode
```

Valores ausentes são omitidos em vez de inventados.

---

## 15. Logs do worker

`ProcessingWorker` passou a emitir eventos operacionais estruturados para fatos como:

```text
job claimed
job succeeded
retry scheduled
job dead
failure handled
```

A observabilidade é best effort.

Falha na emissão de log não redefine o resultado comercial nem o estado do job.

---

## 16. Métricas da execução

Foi criado o detalhe operacional:

```text
ProcessingRunDetail
```

Com:

```text
summary
pipeline
jobs
integrations
```

---

## 17. Métricas do pipeline por run

`ProcessingRunPipelineMetrics` expõe:

```text
collectedCandidates
enrichedCandidates
pendingEnrichmentCandidates
evaluations
eligibleEvaluations
rejectedEvaluations
publicationsGenerated
```

`publicationsGenerated` significa:

```text
Publication gerada/persistida
```

Não significa:

```text
mensagem entregue a Telegram
mensagem entregue a WhatsApp
confirmação de canal
```

Essa distinção preserva a semântica das fases 13, 18 e 19.

---

## 18. Métricas de jobs por run

`ProcessingRunJobMetrics` expõe:

```text
totalJobs
pendingJobs
runningJobs
retryWaitJobs
succeededJobs
deadJobs
totalAttempts
retryAttempts
```

Assim, uma execução pode ser diagnosticada sem varrer manualmente as tabelas do pipeline.

---

## 19. Métricas de integração por run

Foi criado:

```text
ProcessingRunIntegrationMetrics
```

Agregação por integração:

```text
integration
observations
successes
failures
externalFailures
internalFailures
averageDurationMs
maximumDurationMs
```

A leitura usa:

```text
integration_observation.processing_run_id
```

Não usa ASIN para inferir run.

---

## 20. Leitura operacional JDBC

Foi criado:

```text
JdbcProcessingRunOperationalDetailQueryAdapter
```

A leitura do detalhe da run combina:

```text
fatos do pipeline
+
fatos dos jobs
+
observações de integração
```

sem recalcular:

```text
elegibilidade
filtros
score
momentum
publicação
```

---

## 21. Dashboard operacional simples

O comando:

```text
runs show <run-id>
```

passou a expor:

```text
RUN
PIPELINE
JOBS
INTEGRATIONS
```

A seção de integrações apresenta:

```text
INTEGRATION
OBSERVATIONS
SUCCESSES
FAILURES
EXTERNAL_FAILURES
INTERNAL_FAILURES
AVERAGE_DURATION_MS
MAXIMUM_DURATION_MS
```

Essa saída representa o dashboard operacional simples exigido pela fase.

Não foi introduzido um dashboard analítico sofisticado.

---

## 22. Alertas operacionais

Foram definidos os tipos:

```text
REPEATED_EXTERNAL_FAILURES
DEAD_JOBS
ZERO_CANDIDATES
SUSPICIOUS_COLLECTION_DROP
```

Os alertas são derivados de fatos persistidos.

Eles não alteram o pipeline.

---

## 23. Política de alertas

Foi criada:

```text
OperationalAlertPolicy
```

A política define explicitamente parâmetros como:

```text
threshold de falhas externas repetidas
janela temporal
quantidade de runs anteriores
fração de queda suspeita
baseline mínimo de candidatos
```

Os limites não foram escondidos em regras comerciais.

A composição de produção carrega a política a partir do ambiente.

---

## 24. Detecção de falhas externas repetidas

O adapter PostgreSQL conta somente observações com:

```text
outcome = FAILURE
failure_origin = EXTERNAL
```

dentro da janela configurada.

Falhas internas não entram nesse contador.

---

## 25. Jobs DEAD

O alerta:

```text
DEAD_JOBS
```

reconstrói a `ProcessingRun` alcançável pela linhagem persistida de cada tipo de job.

Não é criado um vínculo falso apenas para observabilidade.

---

## 26. Coleta vazia

O alerta:

```text
ZERO_CANDIDATES
```

é baseado na última `ProcessingRun` concluída sem candidatos coletados.

---

## 27. Queda suspeita de coleta

O alerta:

```text
SUSPICIOUS_COLLECTION_DROP
```

compara a última run concluída com as runs concluídas anteriores exigidas pela política.

A comparação exige histórico suficiente.

A referência de baseline foi preservada sem truncamento inadequado.

---

## 28. Alertas sem scheduler

Foi criado:

```text
alerts list
```

O comando executa uma avaliação pontual.

Não foi introduzido:

```text
loop
polling
scheduler
sleep operacional
daemon
```

A execução recorrente pertence à FASE 17.

---

## 29. Auditoria de decisões

A FASE 16 não criou um segundo motor de decisão.

A observabilidade reutiliza fatos persistidos por fases anteriores:

```text
evaluation rule results
score factors
momentum audit
publication versions
processing state
integration observations
```

Princípio:

```text
observabilidade explica a decisão
observabilidade não refaz a decisão
```

---

## 30. Correlação por ASIN e publicação

A correlação operacional preserva:

```text
ASIN
→ atributo de observação

IDs persistidos
→ identidade de execução/entidades
```

A cadeia já persistida permite navegar entre:

```text
Publication
DealEvaluation
OfferSnapshot
DealCandidate
ProcessingRun
```

sem usar ASIN como identidade substituta.

---

## 31. Refinamentos não inventados

O relatório de auditoria sugere:

```text
duração por etapa
taxa de descarte
taxa de UNKNOWN
```

Essas três métricas não receberam uma fórmula arbitrária nesta fase.

Motivos:

```text
duração por etapa
→ retries e múltiplas tentativas exigem semântica explícita

taxa de descarte
→ denominador precisa ser formalizado

taxa de UNKNOWN
→ população e etapa de referência precisam ser formalizadas
```

O que foi implementado de forma factual:

```text
latência por integração
contagens de pipeline por run
rejeições absolutas
sucessos/falhas por integração
origem EXTERNAL/INTERNAL
jobs/attempts/retries
```

---

## 32. Migrations da FASE 16

Foram adicionadas:

```text
V20__processing_observability_correlation.sql
V21__processing_run_observability_read_index.sql
V22__integration_observation.sql
```

Estado validado pelo Flyway:

```text
Successfully validated 22 migrations
Current version of schema "public": 22
Schema "public" is up to date
```

Migrations anteriores não foram alteradas retroativamente.

---

## 33. Suíte hermética

Gate técnico final executado com:

```powershell
.\mvnw.cmd clean test
```

Resultado:

```text
Tests run: 931
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

O `clean` removeu o diretório `target` e forçou recompilação completa.

Compilação observada:

```text
267 source files
179 test source files
```

---

## 34. Prova real de observabilidade

Antes do fechamento local foi adicionada:

```text
Phase16ObservabilityRealSourceIT
```

Ela é uma probe explícita e não participa automaticamente da suíte hermética padrão.

Fluxo comprovado:

```text
Amazon real
    ↓
HTTP real
    ↓
HttpCollectionCollector
    ↓
IntegrationObservation
    ↓
PostgreSQL real
    ↓
segunda conexão JDBC
    ↓
agregação por ProcessingRun
    ↓
runs show
```

---

## 35. Resultado da prova real

Execução observada em 27/09/2026:

```text
Status: SUCCESS
Source: https://www.amazon.com.br/deals
HTTP status: 200
Content length: 647728
Integration: amazon-deals-http
Observations: 1
Successes: 1
Failures: 0
Average duration ms: 2146
Maximum duration ms: 2146
Cross-connection persistence visibility: true
runs show integration visibility: true
```

Resultado JUnit:

```text
Tests run: 1
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

A prova real confirmou persistência após commit usando outra conexão PostgreSQL.

---

## 36. Isolamento da probe externa

A probe real usa sufixo:

```text
*IT
```

e é executada explicitamente.

A suíte normal permaneceu:

```text
931 testes
```

Isso evita transformar disponibilidade da Amazon em pré-condição diária do build.

---

## 37. Critérios oficiais de conclusão

### Critério 1

```text
É possível responder o que aconteceu em uma execução
sem depender de planilhas.
```

Resultado:

```text
ATENDIDO
```

Evidências:

```text
runs show <run-id>

RUN
PIPELINE
JOBS
INTEGRATIONS
```

O detalhe apresenta fatos persistidos e correlacionados.

### Critério 2

```text
Falhas externas podem ser diferenciadas de falhas internas.
```

Resultado:

```text
ATENDIDO
```

Evidências:

```text
OperationalFailureOrigin.EXTERNAL
OperationalFailureOrigin.INTERNAL

externalFailures
internalFailures
```

Além disso, alertas de falhas externas repetidas filtram explicitamente somente `EXTERNAL`.

---

## 38. Checklist funcional da FASE 16

| Requisito | Resultado |
|---|---|
| Logs estruturados | ATENDIDO |
| Quantidade coletada | ATENDIDO |
| Quantidade enriquecida | ATENDIDO |
| Quantidade rejeitada | ATENDIDO |
| Volume de Publication gerada | ATENDIDO |
| Latência por integração | ATENDIDO |
| Falhas por integração | ATENDIDO |
| EXTERNAL versus INTERNAL | ATENDIDO |
| Correlação por execução | ATENDIDO |
| Correlação por ASIN | ATENDIDO |
| Correlação por publicação através da cadeia persistida | ATENDIDO |
| Auditoria de decisões sem recomputação | ATENDIDO |
| Alertas de falhas recorrentes | ATENDIDO |
| Alerta de coleta vazia | ATENDIDO |
| Alerta de queda suspeita | ATENDIDO |
| Dashboard operacional simples | ATENDIDO |
| Prova real contra Amazon + PostgreSQL + CLI | ATENDIDO |
| Scheduler | FORA DA FASE 16 |
| Outbox/canais | FORA DA FASE 16 |

---

## 39. Limites deliberados da FASE 16

Não foram introduzidos:

```text
scheduler
execução periódica contínua
polling de alertas
outbox de entrega
PublicationChannel
Telegram
WhatsApp
confirmação de entrega
dashboard analítico sofisticado
plataforma externa de métricas sem necessidade concreta
machine learning operacional
circuit breaker sem evidência operacional
```

Essas responsabilidades permanecem para fases posteriores.

---

## 40. Relação com a FASE 17

A FASE 16 fornece fatos observáveis para que a execução contínua futura não opere às cegas.

Mas a FASE 16 não agenda nada.

Separação:

```text
FASE 16
→ observar
→ medir
→ correlacionar
→ alertar sob consulta

FASE 17
→ agendar
→ executar continuamente
→ evitar sobreposição
→ controlar recorrência operacional
```

---

## 41. Commits da FASE 16

Histórico local da fase:

```text
8a29d6d docs: define phase 16 observability architecture
4861624 feat: persist processing observability correlation
0d3ae48 feat: add processing run operational detail
f052d35 feat: add structured processing observability logs
54addad feat: persist integration observability metrics
013cb23 feat: wire collection observability in production
cb3ad8d feat: observe product page enrichment
bf649f5 feat: define operational alert contracts
ea0470e feat: detect operational alerts from postgres
026b60a feat: load operational alert policy from environment
6f509b2 feat: wire operational alerts in composition
ca6c3bf feat: expose operational alerts through cli
b3c6965 fix: preserve suspicious collection alert reference
84ce5b4 feat: correlate integration observations with processing runs
193e1c2 feat: aggregate integration observability by processing run
f8868e3 feat: show integration observability in run detail
1ecaf9a test: prove phase 16 observability against real source
```

---

## 42. Dimensão da mudança

Diff da FASE 16 contra o baseline `64e544c`:

```text
76 files changed
21208 insertions(+)
288 deletions(-)
```

Validação:

```text
git diff --check 64e544c..HEAD
```

Resultado:

```text
sem erros
```

---

## 43. Estado do Git no gate técnico

Antes da documentação final:

```text
On branch feat/fase-16-observabilidade
nothing to commit, working tree clean
```

Isso garante que o inventário da implementação foi fechado antes da consolidação documental.

---

## 44. Estado local consolidado

```text
FASE 16 — Observabilidade, auditoria e operação

STATUS LOCAL:
CONCLUÍDA

ADR:
0011 — ACEITA

Logs estruturados:
IMPLEMENTADOS

Correlação durável:
IMPLEMENTADA

Run detail:
IMPLEMENTADO

Métricas de pipeline:
IMPLEMENTADAS

Métricas de jobs:
IMPLEMENTADAS

Métricas de integração:
IMPLEMENTADAS

Falha EXTERNAL / INTERNAL:
IMPLEMENTADA

Alertas:
IMPLEMENTADOS

runs show:
IMPLEMENTADO

alerts list:
IMPLEMENTADO

Prova real:
SUCCESS

Suíte hermética:
931 testes
0 falhas
0 erros
0 ignorados

Flyway:
22 migrations

Schema:
V22

Git diff check:
OK

Working tree antes da documentação:
LIMPO

Gate local:
FECHADO

Gate remoto:
PENDENTE
```

---

## 45. Gate remoto pendente

O fechamento remoto depende de:

```text
commit da documentação final
    ↓
push da branch
    ↓
Pull Request
    ↓
CI remoto verde
    ↓
merge em main
    ↓
CI pós-merge, conforme política do projeto
```

Até esse ciclo ocorrer, o status correto é:

```text
FASE 16 CONCLUÍDA LOCALMENTE
```

e não:

```text
FASE 16 CONCLUÍDA REMOTAMENTE
```

---

## 46. Próxima fase

Após o gate remoto:

```text
FASE 17 — Agendamento e execução contínua
```

A próxima fase poderá usar os fatos operacionais agora disponíveis para tomar decisões de execução sem misturar agendamento com observabilidade.

---

## 47. Encerramento local da FASE 16

A FASE 16 transformou o pipeline de um sistema apenas executável em um sistema operacionalmente explicável.

Antes:

```text
processamento ocorre
→ estado existe no banco
→ diagnóstico exige combinar tabelas e conhecimento interno
```

Depois:

```text
ProcessingRun
    ↓
correlação durável
    ↓
logs estruturados
    ↓
observações de integração
    ↓
métricas por run
    ↓
alertas derivados
    ↓
runs show / alerts list
```

A regra central permanece:

```text
observabilidade descreve fatos
observabilidade não redefine decisões
```

E a fronteira entre fases permanece preservada:

```text
FASE 16
observabilidade

FASE 17
agendamento

FASE 18+
canais / outbox / entrega
```

**Resultado local: FASE 16 APROVADA.**
