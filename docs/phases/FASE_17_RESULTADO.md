# FASE 17 — RESULTADO CONSOLIDADO

**Projeto:** Rasping Amazon  
**Fase:** 17 — Agendamento e execução contínua  
**Data:** 27/09/2026  
**Status local:** CONCLUÍDA — implementação, testes de concorrência, prova de múltiplos ciclos e gate local aprovados  
**Status remoto:** PENDENTE — documentação final ainda precisa ser integrada à branch, publicada em Pull Request e validada pelo CI remoto  
**Baseline inicial da fase:** 931 testes  
**Gate local final:** 1047 testes, 0 falhas, 0 erros, 0 ignorados  
**Schema Flyway ao final da fase:** V24  

---

## 1. Fonte de verdade da fase

A FASE 17 foi conduzida a partir das fontes arquiteturais e operacionais atuais do projeto:

```text
docs/phases/ROADMAP_RASPING_AMAZON_V1.md
docs/adr/0010-politica-selecao-recorrencia-cadencia-publicacoes.md
docs/adr/0011-observabilidade-correlacao-e-metricas-operacionais.md
docs/adr/0012-agendamento-e-execucao-continua.md
docs/phases/FASE_12_RESULTADO.md
docs/phases/FASE_16_RESULTADO.md
RELATORIO_AUDITORIA_RASPING_AMAZON_2026-09-17.md
README.md
estado real da branch da FASE 17
```

O roadmap define para a FASE 17:

```text
scheduler
   ↓
ProcessingRun
   ↓
orquestração da FASE 12
```

Também exige:

```text
frequência configurável
prevenção contra sobreposição indesejada
lock/lease quando necessário
pausa operacional
tratamento explícito de 429 / 403 / challenge / CAPTCHA / bloqueio / timeout
nenhum mecanismo destinado a contornar proteções da fonte
execução recorrente sem intervenção manual em ciclos normais
```

O critério de conclusão oficial é executar ciclos recorrentes sem:

```text
duplicar observações indevidamente
sobrepor execuções de forma incorreta
perder rastreabilidade
exigir intervenção em ciclos normais
```

Esses critérios foram usados como gate técnico da fase.

---

## 2. Baseline da implementação

A FASE 17 começou sobre o estado consolidado após a FASE 16.

Referência histórica da `main` utilizada como baseline:

```text
11b44d31716adfc95aa75acbd10b546375ed5822
```

Branch local da fase:

```text
feat/fase-17-agendamento-execucao-continua
```

Baseline da suíte:

```text
Tests run: 931
Failures: 0
Errors: 0
Skipped: 0
```

A fase não reescreveu a orquestração durável da FASE 12.

O trabalho foi acrescentado sobre:

```text
ProcessingRun
ProcessingJob
ProcessingWorker
DefaultProcessingJobExecutor
ProcessingJobFailureHandler
retry/backoff existente
PostgreSQL
observabilidade da FASE 16
```

---

## 3. ADR da FASE 17

Foi criado:

```text
docs/adr/0012-agendamento-e-execucao-continua.md
```

Status final:

```text
ACEITA
```

A decisão central é:

```text
PostgreSQL
→ fonte operacional persistida do schedule

scheduler
→ transforma janela vencida em ProcessingRun + COLLECT_DEALS

ProcessingWorker
→ continua responsável pela execução das etapas da FASE 12

runtime
→ coordena loops e lifecycle
```

O scheduler não contém:

```text
HTML Amazon
parser
enrichment
elegibilidade
filtros
score
momentum
geração de publicação
seleção de publicação
outbox
canal
```

---

## 4. Relação com ADR-0010

A ADR-0010 trata de:

```text
seleção de publicação
recorrência de publicação
cooldown
quota
cadência futura de publicações
```

A FASE 17 implementou somente a infraestrutura de agendamento necessária para execução recorrente do processamento.

Não foram implementados na FASE 17:

```text
PublicationSelectionPolicy
PUBLICATION_SELECTION_V1
hardCooldownDays
preferredCooldownDays
maxPublicationsPerDay
maxPublicationsPerHour
minimumIntervalMinutes de publicação
quota por canal
quota por destino
outbox
aprovação
envio
```

Essas responsabilidades continuam reservadas às fases posteriores, principalmente FASE 18 e FASE 19.

A frequência do `ProcessingSchedule` não deve ser confundida com cadência de publicação.

---

## 5. Fronteira com a FASE 20

A FASE 20 permanece responsável pelo hardening amplo de produção.

A FASE 17 não introduziu antecipadamente:

```text
circuit breaker
nova taxonomia global de falhas
novo motor de retry
dead-letter adicional
nova fila externa
Kafka
RabbitMQ
SQS
cache distribuído
coordenação externa
microserviços
```

A política foi:

```text
reutilizar capacidades duráveis já existentes
+
implementar somente o necessário para recorrência segura
+
medir antes de adicionar infraestrutura
```

---

## 6. Modelo de `ProcessingSchedule`

Foi introduzido o modelo de scheduling persistido.

Campos:

```text
scheduleKey
source
enabled
interval
nextRunAt
leaseOwner
leaseExpiresAt
lastScheduledFor
lastProcessingRunId
createdAt
updatedAt
```

Invariantes principais:

```text
scheduleKey não vazio
source absoluta
interval positivo
nextRunAt obrigatório
leaseOwner e leaseExpiresAt aparecem juntos
lastScheduledFor e lastProcessingRunId aparecem juntos
lastProcessingRunId positivo quando presente
```

O modelo representa o estado operacional de uma rotina recorrente.

Ele não representa regra comercial.

---

## 7. Lease de scheduling

Foi criado:

```text
ProcessingScheduleLease
```

O lease representa a posse temporária de uma janela de execução.

Conceito:

```text
schedule devido
      ↓
scheduler A tenta adquirir
      ↓
UPDATE condicional no PostgreSQL
      ↓
leaseOwner = scheduler A
leaseExpiresAt = now + leaseDuration
```

Outra instância concorrente não deve receber a mesma janela enquanto o lease estiver válido.

Após expiração, a janela pode ser recuperada por outra instância.

---

## 8. Porta de scheduling

Foi criada:

```text
ProcessingSchedulePort
```

Operações:

```text
saveIfAbsent
findByKey
tryAcquireDue
confirmScheduled
releaseLease
pause
resume
changeInterval
```

A aplicação não conhece SQL.

A coordenação concorrente concreta pertence ao adapter PostgreSQL.

---

## 9. Migration V23

Foi adicionada:

```text
V23__processing_schedule.sql
```

Tabela:

```text
processing_schedule
```

Responsabilidades persistidas:

```text
identidade do schedule
origem
habilitado/pausado
intervalo
próxima execução
lease
última janela confirmada
última ProcessingRun
timestamps
```

O schema protege combinações inválidas por constraints.

A tabela não é populada com cadência funcional hardcoded.

Não existe schedule inicial escondido na migration.

---

## 9.1. Migration V24 — índice do guard de sobreposição

Foi adicionada:

```text
V24__processing_schedule_overlap_guard_index.sql
```

Índice:

```text
idx_processing_job_active_collect_by_run
```

O índice é parcial e cobre somente:

```text
job_type = COLLECT_DEALS
status IN (
    PENDING,
    RUNNING,
    RETRY_WAIT
)
processing_run_id IS NOT NULL
```

A V24 existe porque `tryAcquireDue()` passou a verificar, em cada tentativa de aquisição, se a última `ProcessingRun` ainda possui coleta automática não terminal.

A migration não cria uma segunda fila e não altera os estados da FASE 12.

Ela apenas mantém eficiente o caminho crítico de prevenção de sobreposição.

---

## 10. Estado do catálogo Flyway

Antes da FASE 17:

```text
V22
```

Ao final da FASE 17:

```text
V24
```

Migrations anteriores não foram editadas.

A evolução permaneceu append-only.

---

## 11. Aquisição atômica de janela

`JdbcProcessingScheduleAdapter.tryAcquireDue()` executa aquisição condicional no PostgreSQL.

Uma janela só pode ser adquirida quando, de acordo com o contrato:

```text
schedule existe
enabled = true
nextRunAt <= now
não existe lease válido concorrente
não existe COLLECT_DEALS não terminal da lastProcessingRunId
```

O guard do trabalho anterior considera não terminais:

```text
PENDING
RUNNING
RETRY_WAIT
```

e terminais:

```text
SUCCEEDED
DEAD
```

A verificação usa o estado do job de coleta, não somente `ProcessingRun.status`.

Isso é necessário porque uma run pode estar `FAILED` enquanto seu `COLLECT_DEALS` permanece em `RETRY_WAIT`.

A operação é atômica no banco.

A concorrência não depende de:

```text
synchronized
lock local da JVM
singleton
arquivo local
memória compartilhada
```

Essa decisão permite múltiplas instâncias do processo disputarem o mesmo schedule.

---

## 12. `ScheduledProcessingRun`

Foi introduzido o resultado de scheduling:

```text
ScheduledProcessingRun
```

Ele preserva:

```text
scheduleKey
scheduledFor
processingRunId
processingJobId
nextRunAt
```

Assim, o resultado da transformação de uma janela de tempo em trabalho durável é explícito.

---

## 13. `ScheduleProcessingRunUseCase`

Foi criado:

```text
ScheduleProcessingRunUseCase
```

Responsabilidades:

```text
1. adquirir atomicamente uma janela vencida
2. criar ProcessingRun idempotente
3. criar COLLECT_DEALS idempotente
4. confirmar a janela
5. avançar nextRunAt
```

O caso de uso não executa coleta.

Fluxo:

```text
tryAcquireDue
      ↓
ProcessingRun.save
      ↓
ProcessingJobQueue.enqueue(COLLECT_DEALS)
      ↓
confirmScheduled
```

---

## 14. Unidade transacional do scheduler

As operações de scheduling pertencem à mesma fronteira transacional:

```text
lease
+
ProcessingRun
+
COLLECT_DEALS
+
confirmação da janela
```

Em falha intermediária:

```text
rollback
```

O sistema não deve deixar estado parcial como:

```text
lease persistido sem run
run sem job
job sem confirmação do schedule
schedule avançado sem trabalho durável
```

A integração JDBC foi testada tanto no caminho de sucesso quanto em rollback induzido.

---

## 15. `TransactionPort` compartilhado

Foi consolidada uma porta transacional compartilhada:

```text
application/shared/port/TransactionPort
```

Contrato:

```text
<T> T execute(Supplier<T>)
```

A porta histórica do pacote de deals foi preservada como compatibilidade.

O `JdbcTransactionAdapter` continua sendo a implementação concreta.

Essa alteração permitiu ao scheduler utilizar a mesma abstração transacional sem duplicar mecanismo.

---

## 16. Identidade idempotente da `ProcessingRun`

A run gerada pelo scheduler utiliza:

```text
scheduled:<scheduleKey>:<scheduledFor.toInstant()>
```

Exemplo conceitual:

```text
scheduled:amazon-deals:2026-09-27T20:00:00Z
```

A identidade utiliza `Instant`.

Consequência:

```text
2026-09-27T17:00:00-03:00
e
2026-09-27T20:00:00Z
```

representam a mesma janela lógica.

Offsets diferentes não criam identidades diferentes para o mesmo instante.

---

## 17. Identidade do `COLLECT_DEALS`

O primeiro job criado pelo scheduler utiliza:

```text
collect:<processingRunId>
```

A FASE 12 já protege a idempotência dos jobs por:

```text
job_type
+
idempotency_key
```

Portanto a proteção do scheduler atua em conjunto com as constraints já existentes.

---

## 18. Política após downtime

Não foi implementado catch-up ilimitado.

Regra:

```text
naturalNext = scheduledFor + interval
```

Se:

```text
naturalNext > now
```

então:

```text
nextRunAt = naturalNext
```

Se:

```text
naturalNext <= now
```

então:

```text
nextRunAt = now + interval
```

Consequência:

```text
processo fica 3 horas parado
cadência = 15 minutos
```

não produz automaticamente uma rajada com todas as janelas perdidas.

A recuperação gera uma execução e avança a cadência a partir do momento atual.

---

## 19. Razão da política sem catch-up storm

O objetivo é impedir:

```text
downtime
   ↓
dezenas de janelas vencidas
   ↓
dezenas de ProcessingRuns imediatas
   ↓
rajada de chamadas externas
```

A política adotada preserva:

```text
rastreabilidade
previsibilidade
controle de carga
```

sem inventar uma fila de janelas históricas que o roadmap não exige.

---

## 20. Runner contínuo do worker

Foi implementado:

```text
ContinuousProcessingWorkerRunner
```

Ele transforma:

```text
ProcessingWorker.runOnce()
```

em um loop operacional.

Regras:

```text
job processado
→ tentar imediatamente o próximo job

fila vazia
→ aguardar workerIdleDelay

interrupt durante idle
→ restaurar flag
→ encerrar loop

RuntimeException inesperada
→ propagar
```

O runner não conhece scheduling.

---

## 21. Reuso integral da FASE 12

O runtime contínuo não criou uma segunda orquestração.

O worker continua reutilizando:

```text
CollectDealsUseCase
EnrichDealUseCase
EvaluateDealUseCase
DefaultProcessingJobExecutor
ProcessingJobFailureHandler
DefaultProcessingFailureClassifier
ExponentialRetryBackoffPolicy
ProcessingWorker
JdbcProcessingJobQueueAdapter
```

Essa decisão preserva o comportamento já provado de:

```text
retry
backoff
idempotência
claim concorrente
leases de job
estados RETRY_WAIT / DEAD
```

---

## 22. Restrições operacionais da fonte Amazon

Foi criada a taxonomia específica:

```text
SourceRestrictionType
```

Valores atuais:

```text
CHALLENGE
CAPTCHA
BLOCKED
```

E a exceção:

```text
SourceRestrictionException
```

Esses conceitos representam restrição da fonte.

Não representam regra de negócio.

---

## 23. `AmazonDealsCollector` como decorator da fonte

`AmazonDealsCollector` passou a funcionar como fronteira específica Amazon sobre o collector HTTP genérico.

Fluxo de produção da FASE 17:

```text
JavaHttpTransport
      ↓
HttpCollectionCollector
      ↓
AmazonDealsCollector
      ↓
CollectDealsUseCase
```

O collector específico pode identificar conservadoramente sinais de:

```text
challenge
CAPTCHA
bloqueio
```

em respostas HTTP tecnicamente bem-sucedidas.

Nenhum bypass foi adicionado.

---

## 24. Normalização para detecção de restrição

A análise do corpo recebido normaliza texto antes da detecção.

O objetivo é reconhecer marcadores mesmo quando HTML e whitespace dividem a expressão em múltiplas linhas.

A normalização não altera a semântica do parser de ofertas.

Ela é utilizada apenas para classificação de restrição operacional.

---

## 25. Classificação de falhas de coleta

`DefaultProcessingFailureClassifier` passou a tratar explicitamente os cenários exigidos pela FASE 17.

Mapeamento relevante:

```text
SourceRestrictionException
→ PERMANENT
→ SOURCE_RESTRICTION_<TYPE>

HTTP 429
→ TRANSIENT
→ COLLECTION_HTTP_429

timeout
→ TRANSIENT

HTTP 403
→ PERMANENT
→ COLLECTION_HTTP_403
```

O classificador não tenta contornar a restrição.

Ele apenas fornece semântica operacional para o pipeline durável existente.

---

## 26. Princípio de não contorno

A FASE 17 preserva explicitamente:

```text
429
403
challenge
CAPTCHA
bloqueio
timeout
```

como falhas ou restrições observáveis.

Não foram implementados:

```text
CAPTCHA solver
proxy rotation para evasão
fingerprint spoofing
bypass de challenge
troca automatizada de identidade para contornar bloqueio
```

A arquitetura continua preparada para substituir a fonte no futuro.

---

## 27. Operações administrativas do schedule

Foram criados casos de uso:

```text
GetProcessingScheduleUseCase
PauseProcessingScheduleUseCase
ResumeProcessingScheduleUseCase
ChangeProcessingScheduleIntervalUseCase
```

Também foi criada:

```text
ProcessingScheduleNotFoundException
```

As operações administrativas passam pela camada de aplicação.

A CLI não executa SQL diretamente.

---

## 28. Status operacional

A interface operacional passou a suportar consulta de schedule.

Informações relevantes incluem:

```text
scheduleKey
source
enabled
interval
nextRunAt
leaseOwner
leaseExpiresAt
lastScheduledFor
lastProcessingRunId
updatedAt
```

O operador consegue observar o estado sem acessar PostgreSQL manualmente.

---

## 29. Pausa operacional

A operação de pausa:

```text
pause
```

impede novas aquisições futuras.

Ela não:

```text
apaga histórico
remove ProcessingRun existente
remove jobs existentes
invalida observações persistidas
cancela retroativamente trabalho já durável
```

A pausa atua sobre a criação de novas execuções.

---

## 30. Pausa durante lease ativo

A pausa pode ser aplicada mesmo quando já existe uma aquisição em andamento.

Objetivo:

```text
não invalidar arbitrariamente a unidade transacional atual
+
impedir novas execuções depois dela
```

Assim, uma pausa administrativa não transforma a confirmação da janela atual em inconsistência artificial.

---

## 31. Resume operacional

`resume` reabilita o schedule.

A operação recebe o novo `nextRunAt` de maneira explícita.

Não existe data/hora inventada silenciosamente pela apresentação.

Resume é protegido contra interferência indevida em lease ativo.

---

## 32. Alteração de intervalo

O intervalo pode ser alterado operacionalmente.

Contrato:

```text
changeInterval
```

A frequência funcional não fica hardcoded.

A alteração preserva:

```text
histórico
última ProcessingRun
última janela confirmada
```

e modifica apenas o estado operacional necessário para os ciclos futuros.

---

## 33. Proteção de `resume` e `changeInterval`

`resume` e mudança de intervalo não podem invalidar atomicamente uma aquisição ativa que precisa confirmar sua janela.

O adapter PostgreSQL protege essa concorrência.

Assim são evitados cenários como:

```text
scheduler adquire janela
      ↓
operador muda intervalo
      ↓
scheduler tenta confirmar estado que já foi sobrescrito
```

A operação administrativa falha de forma controlada quando a alteração seria incompatível com lease ativo.

---

## 34. CLI de schedules

Foi criada a apresentação operacional:

```text
SchedulesCliCommand
SchedulesCliUsage
```

A CLI operacional passou a expor ações equivalentes a:

```text
status
pause
resume
interval
```

A implementação segue a mesma arquitetura da FASE 14:

```text
CLI
 ↓
use case
 ↓
port
 ↓
JDBC adapter
```

---

## 35. Bootstrap da CLI

`OperationalCliFactory`, `OperationalInterfaceComposition` e `OperationalCliBootstrap` foram estendidos para incluir os casos de uso de schedule.

A assinatura histórica da factory foi preservada por overload quando necessário.

A integração não quebra os comandos operacionais anteriores.

---

## 36. `EnsureProcessingScheduleUseCase`

Foi criado:

```text
EnsureProcessingScheduleUseCase
```

Responsabilidade:

```text
garantir que o schedule exista
```

na inicialização do processo contínuo.

Se ainda não existir:

```text
enabled = true
nextRunAt = now
interval = configuração inicial externa
```

A primeira janela fica imediatamente elegível.

---

## 37. PostgreSQL como fonte operacional de verdade

A configuração de ambiente é usada somente para a criação inicial.

Depois que a linha existe:

```text
PostgreSQL
=
fonte operacional de verdade
```

Reiniciar o processo não sobrescreve:

```text
pause
interval alterado pela CLI
nextRunAt
última janela
última ProcessingRun
```

Essa decisão impede que um restart reverta uma alteração administrativa legítima.

---

## 38. Trigger abstrato do scheduler

Foi criado:

```text
ScheduledProcessingTrigger
```

Contrato:

```text
Optional<ScheduledProcessingRun> execute(
    String scheduleKey,
    String schedulerInstanceId
)
```

`ScheduleProcessingRunUseCase` satisfaz esse contrato.

Isso mantém o runner contínuo independente do PostgreSQL e da composição concreta.

---

## 39. Runner contínuo do scheduler

Foi criado:

```text
ContinuousProcessingSchedulerRunner
```

Responsabilidade:

```text
verificar periodicamente se existe janela a disparar
```

O runner conhece apenas:

```text
ScheduledProcessingTrigger
scheduleKey
schedulerInstanceId
pollInterval
waitStrategy
```

Ele não conhece:

```text
ProcessingRunRepository
ProcessingJobQueue
Amazon
parser
enrichment
avaliação
```

---

## 40. Polling técnico versus frequência funcional

Existem duas durações semanticamente diferentes.

Frequência funcional:

```text
ProcessingSchedule.interval
```

Define quando um novo ciclo de processamento deve existir.

Polling técnico:

```text
schedulerPollInterval
```

Define com que frequência o processo verifica o PostgreSQL.

Esses conceitos não são confundidos.

---

## 41. Wait strategy do scheduler

Foram criados:

```text
ProcessingSchedulerWaitStrategy
ThreadSleepProcessingSchedulerWaitStrategy
```

Essa abstração torna o loop testável sem sleeps reais nos testes unitários.

O scheduler sempre aguarda entre polls.

Isso evita busy-loop contra o banco.

---

## 42. Composition root contínuo

Foi criado:

```text
ContinuousProcessingComposition
```

Ele monta dois lados independentes:

```text
scheduler
e
worker
```

A composition não inicia threads.

Ela monta dependências.

---

## 43. Conexões JDBC separadas

A composition exige:

```text
schedulerConnection
workerConnection
```

e rejeita a mesma instância de `Connection` para os dois lados.

Motivo:

```text
scheduler e worker executam concorrentemente
```

Compartilhar a mesma `Connection` entre threads quebraria fronteiras transacionais e criaria acoplamento perigoso.

---

## 44. Lado scheduler da composition

O lado scheduler monta:

```text
JdbcProcessingScheduleAdapter
JdbcProcessingRunRepositoryAdapter
JdbcProcessingJobQueueAdapter
JdbcTransactionAdapter
ScheduleProcessingRunUseCase
ContinuousProcessingSchedulerRunner
```

Todos usam a conexão exclusiva do scheduler.

---

## 45. Lado worker da composition

O lado worker reutiliza a orquestração da FASE 12.

Fluxo principal:

```text
JdbcProcessingJobQueueAdapter
      ↓
CollectDealsUseCase
EnrichDealUseCase
EvaluateDealUseCase
      ↓
DefaultProcessingJobExecutor
      ↓
ProcessingJobFailureHandler
      ↓
ProcessingWorker
      ↓
ContinuousProcessingWorkerRunner
```

Todos os adapters transacionais relevantes desse lado compartilham a conexão exclusiva do worker.

---

## 46. Wiring do decorator Amazon

Na composition de produção, o `CollectDealsUseCase` recebe:

```text
AmazonDealsCollector
```

e não somente o collector HTTP genérico.

Isso torna efetiva, no runtime contínuo, a classificação de restrições adicionada nesta fase.

---

## 47. Observabilidade preservada

O bootstrap contínuo preserva a observabilidade da FASE 16.

Componentes reutilizados:

```text
JsonStructuredOperationalLogAdapter
JdbcIntegrationObservationPersistenceAdapter
BestEffortIntegrationObservationRecorder
DefaultProcessingFailureClassifier
```

A coleta e o enrichment continuam produzindo observações de integração.

Scheduling não remove a correlação existente.

---

## 48. Runtime concorrente

Foi criado:

```text
ContinuousProcessingRuntime
```

Ele coordena exatamente dois loops:

```text
scheduler thread
worker thread
```

As threads são independentes.

O runtime não conhece:

```text
SQL
Amazon
ProcessingSchedule
parser
score
Publication
```

Ele conhece somente os runners.

---

## 49. Sinal de continuidade compartilhado

Scheduler e worker recebem o mesmo:

```text
BooleanSupplier keepRunning
```

O lifecycle controla esse sinal.

O loop não depende de estado global oculto.

---

## 50. Falha terminal de um loop

Se scheduler ou worker termina inesperadamente enquanto o runtime deveria estar ativo:

```text
runtime registra a primeira causa
      ↓
solicita parada do outro loop
      ↓
aguarda os dois terminarem
      ↓
propaga ContinuousProcessingRuntimeException
```

O processo não permanece parcialmente ativo com apenas metade do runtime.

---

## 51. Shutdown coordenado

O shutdown:

```text
keepRunning = false
```

e acorda waits bloqueados por interrupção.

Depois do pedido de parada:

```text
scheduler não deve iniciar nova janela
worker não deve iniciar nova unidade após observar o sinal/interrupção
```

A durabilidade dos jobs continua pertencendo à FASE 12 e ao PostgreSQL.

O runtime não introduz estado operacional apenas em memória.

---

## 52. Ownership das conexões

Foi criada:

```text
ContinuousProcessingApplication
```

Ela possui:

```text
ContinuousProcessingRuntime
schedulerConnection
workerConnection
```

A composition monta o grafo, mas não fecha conexões.

A application encerra o runtime e depois fecha os recursos JDBC.

---

## 53. Bootstrap executável

Foi criado:

```text
ContinuousProcessingBootstrap
```

Responsabilidades:

```text
carregar configuração
abrir duas Connections
garantir schedule inicial
montar observabilidade
montar HTTP
montar AmazonDealsCollector
montar parser
montar enrichment
montar ContinuousProcessingComposition
montar ContinuousProcessingRuntime
transferir ownership para ContinuousProcessingApplication
```

Em falha durante a montagem, conexões parcialmente abertas são fechadas best effort.

---

## 54. Entrypoint contínuo

Foi criado:

```text
ContinuousProcessingMain
```

Responsabilidades da fronteira JVM:

```text
abrir application
registrar shutdown hook
start
awaitTermination
traduzir falha operacional para exit code
executar cleanup final
```

O entrypoint operacional da CLI não foi transformado em daemon.

Continuam existindo responsabilidades separadas:

```text
OperationalCliMain
→ administração e consulta

ContinuousProcessingMain
→ execução recorrente
```

---

## 55. Configuração do runtime contínuo

Foi criado:

```text
ContinuousProcessingEnvironmentConfig
```

Campos:

```text
scheduleKey
source
initialScheduleInterval
schedulerInstanceId
workerId
schedulerPollInterval
schedulerLeaseDuration
workerIdleDelay
collectionMaxAttempts
enrichmentMaxAttempts
evaluationMaxAttempts
retryBaseDelay
retryMaxDelay
```

A configuração valida invariantes antes de montar o runtime.

---

## 56. Provider de configuração

Foi criado:

```text
ContinuousProcessingEnvironmentConfigProvider
```

A cadência funcional inicial é obrigatória.

Variável:

```text
PROCESSING_SCHEDULE_INTERVAL
```

Formato:

```text
Duration ISO-8601
```

Exemplos válidos:

```text
PT15M
PT1H
PT30S
```

Não existe frequência funcional padrão escondida no código.

---

## 57. Variáveis da FASE 17

Variáveis suportadas:

```text
PROCESSING_SCHEDULE_INTERVAL
PROCESSING_SCHEDULE_KEY
PROCESSING_SOURCE_URI
PROCESSING_SCHEDULER_INSTANCE_ID
PROCESSING_WORKER_ID
PROCESSING_SCHEDULER_POLL_INTERVAL
PROCESSING_SCHEDULER_LEASE_DURATION
PROCESSING_WORKER_IDLE_DELAY
PROCESSING_COLLECTION_MAX_ATTEMPTS
PROCESSING_ENRICHMENT_MAX_ATTEMPTS
PROCESSING_EVALUATION_MAX_ATTEMPTS
PROCESSING_RETRY_BASE_DELAY
PROCESSING_RETRY_MAX_DELAY
```

A cadência funcional inicial é obrigatória.

Parâmetros técnicos possuem defaults operacionais controlados quando apropriado.

---

## 58. Identidades de instância

Quando não são explicitamente fornecidos:

```text
schedulerInstanceId
workerId
```

o provider gera identidades únicas por runtime.

Objetivo:

```text
rastrear ownership
evitar identidade fixa compartilhada entre processos
```

A identidade da instância não participa da identidade lógica da janela.

---

## 59. Fonte inicial

O bootstrap aceita:

```text
PROCESSING_SOURCE_URI
```

O default operacional atual aponta para a fonte Amazon de deals conhecida pelo projeto.

Entretanto, a fonte fica transportada pelo `ProcessingSchedule`.

O scheduler não possui regra específica de Amazon.

---

## 60. Arquitetura preparada para fonte futura

O roadmap determina que a v1 não depende da Amazon Creators API.

A arquitetura continua compatível com adaptação futura equivalente a:

```text
AmazonHtmlSource
AmazonCreatorsApiSource
```

ou combinação controlada.

A decisão da FASE 17 não prende o domínio ao HTML.

---

## 61. Prova JDBC de atomicidade

A fase adicionou prova real com PostgreSQL para:

```text
lease
+
ProcessingRun
+
COLLECT_DEALS
+
confirmScheduled
```

Cenário de sucesso:

```text
1 janela
→ 1 run
→ 1 job
→ schedule confirmado
```

Cenário de falha injetada:

```text
falha intermediária
→ rollback
→ lease não fica preso
→ run não fica órfã
→ job não fica órfão
→ janela pode ser tentada novamente
```

---

## 62. Prova de duas instâncias na mesma janela

Foi criado teste JDBC concorrente.

Cenário:

```text
scheduler A
scheduler B
      ↓
mesmo schedule
mesmo instante
```

Resultado:

```text
exatamente 1 ScheduledProcessingRun
exatamente 1 ProcessingRun
exatamente 1 COLLECT_DEALS
```

O outro scheduler recebe ausência de aquisição.

---

## 63. Prova de recorrência com concorrência

O teste executa duas janelas consecutivas, com competição entre instâncias em cada uma.

Resultado:

```text
janela 1
→ 1 run
→ 1 job

janela 2
→ 1 run
→ 1 job

total
→ 2 runs
→ 2 jobs
```

Cada janela preserva sua própria `run_key`.

---

## 64. Prova de takeover após lease expirado

Cenário:

```text
scheduler A adquire lease
      ↓
desaparece antes de criar a run
      ↓
lease expira
      ↓
scheduler B tenta
```

Resultado:

```text
scheduler B adquire
→ cria 1 run
→ cria 1 COLLECT_DEALS
```

Tentativa adicional na mesma janela:

```text
→ nenhuma duplicação
```

---

## 65. Prova de ausência de catch-up burst

Cenário:

```text
intervalo = 15 minutos
processo fica 3 horas parado
```

Ao retornar:

```text
1 execução de recuperação
```

não:

```text
12 execuções imediatas
```

A próxima janela é movida para:

```text
recoveredAt + interval
```

quando a janela natural já ficou no passado.

---

## 66. Prova operacional de múltiplos ciclos

Foi criado:

```text
ContinuousProcessingMultipleCyclesJdbcIntegrationTest
```

A prova utiliza:

```text
PostgreSQL real
scheduler real
worker real
threads reais
composition real
runtime real
```

mas usa fonte hermética para não depender da rede Amazon.

---

## 67. Estratégia hermética da prova contínua

O collector de teste retorna:

```text
CollectionResult válido
```

O parser retorna:

```text
[]
```

Isso produz:

```text
ProcessingRun
      ↓
COLLECT_DEALS
      ↓
coleta
      ↓
parsing vazio
      ↓
ProcessingRun COMPLETED
```

sem criar:

```text
ENRICH_DEAL
EVALUATE_DEAL
```

A prova continua exercitando scheduler e worker completos.

---

## 68. Resultado da prova de múltiplos ciclos

Sem chamadas manuais entre ciclos:

```text
application.start()
      ↓
ciclo 1
      ↓
COMPLETED
      ↓
ciclo 2
      ↓
COMPLETED
      ↓
ciclo 3
      ↓
COMPLETED
```

A prova exige pelo menos três runs concluídas automaticamente.

Também verifica:

```text
1 COLLECT_DEALS por run
run_key única
collector chamado em múltiplos ciclos
nenhum enrichment indevido
shutdown controlado
nenhum lease de scheduler restante
```

---

## 69. Testes específicos adicionados

Cobertura introduzida ou ampliada na FASE 17 inclui:

```text
ProcessingScheduleTest
JdbcProcessingScheduleAdapterTest
ScheduleProcessingRunUseCaseTest
ScheduleProcessingRunJdbcIntegrationTest
ContinuousProcessingWorkerRunnerTest
DefaultProcessingFailureClassifierTest
AmazonDealsCollectorTest
ProcessingScheduleOperationalUseCasesTest
ProcessingScheduleOperationalControlJdbcTest
SchedulesCliCommandTest
OperationalCliSchedulesBootstrapContractTest
OperationalSchedulesCliIntegrationTest
EnsureProcessingScheduleUseCaseTest
ContinuousProcessingSchedulerRunnerTest
ContinuousProcessingCompositionTest
ContinuousProcessingRuntimeTest
ContinuousProcessingEnvironmentConfigProviderTest
ScheduleProcessingRunConcurrencyJdbcIntegrationTest
ContinuousProcessingMultipleCyclesJdbcIntegrationTest
```

Além disso, testes históricos foram preservados.

---

## 70. Progressão da suíte

Baseline:

```text
931
```

Após modelo/ports:

```text
946
```

Após persistência de schedule:

```text
954
```

Após trigger de scheduling:

```text
964
```

Após prova de atomicidade JDBC:

```text
966
```

Após worker contínuo:

```text
972
```

Após restrições Amazon e controles operacionais:

```text
1010
```

Após scheduler runner e bootstrap inicial:

```text
1021
```

Após composition root:

```text
1028
```

Após lifecycle:

```text
1037
```

Após bootstrap/configuração contínua:

```text
1042
```

Após concorrência/recorrência:

```text
1046
```

Gate final da prova de múltiplos ciclos:

```text
1047
```

---

## 71. Crescimento líquido da suíte

Baseline:

```text
931 testes
```

Gate final:

```text
1047 testes
```

Crescimento líquido:

```text
116 testes
```

A fase adicionou cobertura sem remover os testes históricos.

---

## 72. Gate local final

Resultado final:

```text
Tests run: 1047
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

Horário registrado do gate final:

```text
2026-09-27T16:43:20-03:00
```

---

## 73. `git diff --check`

O gate executou:

```text
git diff --check
```

Não foram reportados erros de whitespace no diff.

Foram observados apenas avisos de normalização de EOL:

```text
AmazonDealsCollector.java
AmazonDealsCollectorTest.java

CRLF será convertido para LF quando o Git tocar os arquivos
```

Esses avisos não representam falha funcional nem erro do `git diff --check`.

A padronização geral de EOL não foi transformada em refatoração paralela da FASE 17.

---

## 74. Estado do working tree no fechamento local

O fechamento local ainda contém mudanças da FASE 17 não integradas.

Isso é esperado antes do commit final da fase.

A documentação não declara:

```text
working tree clean
```

antes que os arquivos finais sejam incorporados e o gate de Git seja concluído.

---

## 75. Critério: frequência configurável

Atendido.

Evidência:

```text
PROCESSING_SCHEDULE_INTERVAL obrigatório no bootstrap inicial
ProcessingSchedule.interval persistido
changeInterval operacional
nenhuma frequência funcional fixa na migration
```

---

## 76. Critério: prevenção de sobreposição

Atendido.

A prevenção cobre duas categorias.

### Mesma janela lógica

Evidência:

```text
lease PostgreSQL
aquisição atômica
duas instâncias testadas
1 run por janela
1 COLLECT_DEALS por janela
```

### Janelas consecutivas

A próxima janela permanece bloqueada enquanto o `COLLECT_DEALS` da última run estiver não terminal:

```text
PENDING
RUNNING
RETRY_WAIT
```

Ela volta a poder ser adquirida quando o job estiver terminal:

```text
SUCCEEDED
DEAD
```

O teste JDBC da FASE 17 prova explicitamente a sequência:

```text
PENDING
→ bloqueado

RUNNING
→ bloqueado

RETRY_WAIT
→ bloqueado

SUCCEEDED
→ liberado

2 schedulers disputam a janela liberada
→ exatamente 1 vencedor
→ exatamente 1 nova ProcessingRun
→ exatamente 1 novo COLLECT_DEALS
```

A consulta é sustentada pelo índice parcial da V24:

```text
idx_processing_job_active_collect_by_run
```

---

## 77. Critério: pausa operacional

Atendido.

Evidência:

```text
pause
resume
status
changeInterval
```

com histórico preservado e proteção contra conflito de lease.

---

## 78. Critério: execução recorrente

Atendido.

Evidência:

```text
ContinuousProcessingSchedulerRunner
ContinuousProcessingWorkerRunner
ContinuousProcessingRuntime
ContinuousProcessingApplication
ContinuousProcessingMain
```

e prova automática de múltiplos ciclos.

---

## 79. Critério: integração com FASE 12

Atendido.

Fluxo:

```text
scheduler
      ↓
ProcessingRun
      ↓
COLLECT_DEALS
      ↓
ProcessingWorker
      ↓
FASE 12
```

Nenhuma segunda orquestração foi introduzida.

---

## 80. Critério: rastreabilidade

Atendido localmente.

O scheduling persiste:

```text
scheduleKey
lastScheduledFor
lastProcessingRunId
nextRunAt
timestamps
```

e cada execução continua usando `ProcessingRun`, jobs e observabilidade existentes.

---

## 81. Critério: restrições Amazon

Atendido.

Tratamento explícito para:

```text
429
403
timeout
challenge
CAPTCHA
blocked
```

sem mecanismos de contorno.

---

## 82. Critério: intervenção manual em ciclos normais

Atendido na prova hermética.

Depois de:

```text
application.start()
```

nenhum comando manual dispara os ciclos 2 e 3.

O runtime produz automaticamente as próximas runs conforme o schedule.

---

## 83. O que não foi implementado

Permanecem fora da FASE 17:

```text
PublicationSelectionPolicy
cooldown de publicação
quota de publicação
agenda de mensagens por canal
outbox
Telegram
WhatsApp
adapters de canal
PublicationAttempt de canal
novo circuit breaker
broker externo
microserviços
cache distribuído
particionamento
réplica de leitura
```

---

## 84. FASE 18 permanece separada

Próxima fase do roadmap:

```text
FASE 18 — Contrato de canais e outbox de publicação
```

Ela deverá trabalhar conceitos equivalentes a:

```text
PublicationChannel
PublicationCommand
PublicationResult
Outbox
aprovação
idempotência de entrega
```

A FASE 17 não antecipou esses componentes.

---

## 85. FASE 19 permanece separada

A FASE 19 continua responsável pelos adapters concretos:

```text
TelegramChannel
WhatsAppChannel
destinos
tentativas
providerReference
retry de canal
formatação específica de canal
```

Nenhum desses detalhes entrou no scheduler.

---

## 86. FASE 20 permanece separada

A FASE 20 deverá ampliar resiliência e recuperação com base em evidência operacional.

Escopo futuro inclui, quando necessário:

```text
taxonomia ampla
restart/recovery
dead-letter
reprocessamento operacional
alertas adicionais
circuit breaker somente se demonstrado
```

A FASE 17 não antecipou essa camada.

---

## 87. Escalabilidade preservada

O projeto continua:

```text
um módulo Maven
PostgreSQL como fila e coordenação
ports como fronteira
composition roots explícitos
```

Não houve evidência que justificasse:

```text
Kafka
RabbitMQ
Redis distribuído
microserviços
orquestrador externo
```

A decisão permanece:

```text
medir primeiro
otimizar depois
```

---

## 88. Arquitetura resultante

Fluxo operacional após a FASE 17:

```text
ContinuousProcessingMain
        ↓
ContinuousProcessingBootstrap
        ↓
EnsureProcessingScheduleUseCase
        ↓
PostgreSQL processing_schedule
        ↓
ContinuousProcessingRuntime
        ├───────────────────────────────┐
        ↓                               ↓
scheduler thread                    worker thread
        ↓                               ↓
ContinuousProcessingSchedulerRunner ContinuousProcessingWorkerRunner
        ↓                               ↓
ScheduleProcessingRunUseCase         ProcessingWorker
        ↓                               ↓
ProcessingRun                         COLLECT_DEALS
        ↓                               ↓
COLLECT_DEALS                         ENRICH_DEAL
                                        ↓
                                     EVALUATE_DEAL
```

---

## 89. Separação de responsabilidades final

Scheduler:

```text
quando criar a próxima ProcessingRun?
```

Worker:

```text
qual job durável executar agora?
```

Casos de uso da FASE 12:

```text
como executar cada etapa?
```

Domínio:

```text
quais regras comerciais e de decisão se aplicam?
```

CLI:

```text
como o operador consulta/administra o estado?
```

PostgreSQL:

```text
qual é o estado durável?
```

Essas respostas permanecem em componentes diferentes.

---

## 90. Resultado consolidado

A FASE 17 transformou o Rasping Amazon de um pipeline durável acionável manualmente em um processo capaz de criar e consumir ciclos recorrentes automaticamente.

Resultado técnico:

```text
schedule persistido
+
frequência configurável
+
lease PostgreSQL
+
ProcessingRun idempotente
+
COLLECT_DEALS idempotente
+
worker contínuo
+
scheduler contínuo
+
duas conexões JDBC independentes
+
lifecycle coordenado
+
shutdown hook
+
pausa/resume/intervalo
+
restrições Amazon explícitas
+
concorrência testada
+
múltiplos ciclos testados
+
1047 testes verdes
```

Critério local:

```text
ATENDIDO
```

---

## 91. Estado formal da fase

```text
gate técnico local = FECHADO
gate funcional local = FECHADO
gate de concorrência = FECHADO
gate de recorrência = FECHADO
gate de múltiplos ciclos = FECHADO
documentação final = GERADA
commit final = PENDENTE
push = PENDENTE
Pull Request = PENDENTE
CI remoto = PENDENTE
merge em main = PENDENTE
```

Portanto:

```text
FASE 17 = CONCLUÍDA LOCALMENTE
```

O encerramento remoto somente deverá ser declarado após:

```text
commit
push
Pull Request
CI verde
merge
CI pós-merge, quando aplicável
```

---

## 92. Próxima fase

Após o gate remoto da FASE 17:

```text
FASE 18 — Contrato de canais e outbox de publicação
```

A próxima fase não deverá modificar a semântica do scheduler apenas para acomodar Telegram ou WhatsApp.

A camada de canais deverá ser construída sobre contratos próprios, preservando o scheduler como infraestrutura de recorrência do processamento.

---

# Conclusão

A FASE 17 fecha a lacuna entre processamento durável e operação contínua.

O sistema passa a possuir uma linha operacional clara:

```text
configuração inicial
      ↓
schedule persistido
      ↓
scheduler recorrente
      ↓
ProcessingRun
      ↓
fila durável
      ↓
worker contínuo
      ↓
pipeline da FASE 12
      ↓
estado persistido e observável
```

A execução contínua foi introduzida sem deslocar regras de negócio para o scheduler, sem criar uma segunda orquestração e sem antecipar a camada de publicação externa.

O gate local final é:

```text
1047 testes
0 failures
0 errors
0 skipped
BUILD SUCCESS
```

Com isso, a implementação local da FASE 17 está encerrada e pronta para o gate remoto.
