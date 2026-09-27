# ADR-0012 — Agendamento e execução contínua

- **Status:** Aceita
- **Data:** 2026-09-27
- **Projeto:** Rasping Amazon
- **Fase principal:** FASE 17 — Agendamento e execução contínua
- **Relacionada a:** ADR-0009, ADR-0010 e ADR-0011
- **Depende de:** FASE 12 — Orquestração assíncrona e processamento durável
- **Próximas fronteiras:** FASE 18 — Contrato de canais e outbox; FASE 20 — Resiliência e recuperação

---

## Contexto

Ao final da FASE 16, o Rasping Amazon já possui um pipeline durável e observável.

A FASE 12 introduziu:

```text
ProcessingRun
ProcessingJob
ProcessingWorker
COLLECT_DEALS
ENRICH_DEAL
EVALUATE_DEAL
retry
backoff
leases de job
idempotência
PostgreSQL como fila durável
```

A FASE 14 introduziu interface operacional não bloqueante.

A FASE 16 introduziu:

```text
correlação por execução
logs estruturados
observações de integração
métricas operacionais
alertas sob consulta
```

Entretanto, a criação de uma nova `ProcessingRun` ainda precisava ser acionada externamente ou por código de teste.

O roadmap da versão 1.0 define para a FASE 17:

```text
scheduler
   ↓
ProcessingRun
   ↓
orquestração da FASE 12
```

e exige:

```text
frequência configurável
prevenção de sobreposição indesejada
pausa operacional
execução recorrente
tratamento de restrições da fonte
ausência de intervenção manual em ciclos normais
```

A decisão desta ADR define como introduzir recorrência sem mover regras de coleta ou negócio para o scheduler.

---

## Problema

O sistema precisa responder de forma durável às perguntas:

```text
qual rotina recorrente existe?
quando ela deve rodar novamente?
está habilitada?
alguma instância já adquiriu esta janela?
qual foi a última janela confirmada?
qual ProcessingRun foi criada para essa janela?
```

Também precisa continuar correto quando:

```text
duas instâncias iniciam ao mesmo tempo
uma instância cai após adquirir o lease
o processo fica horas desligado
o operador pausa a rotina
o operador muda o intervalo
a fonte retorna 429 / 403 / CAPTCHA / challenge
o processo recebe shutdown
```

Essas respostas não podem depender somente de memória da JVM.

---

## Drivers arquiteturais

A decisão prioriza:

1. PostgreSQL como fonte operacional já existente;
2. reutilização da orquestração da FASE 12;
3. idempotência;
4. coordenação concorrente atômica;
5. rastreabilidade;
6. configuração fora do código;
7. separação de responsabilidades;
8. ausência de infraestrutura distribuída sem evidência;
9. compatibilidade com múltiplas instâncias;
10. preservação das fronteiras das FASES 18, 19 e 20.

---

# Decisão

## 1. O schedule será persistido no PostgreSQL

Será utilizado um modelo equivalente a:

```text
ProcessingSchedule
```

com estado persistido:

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

O PostgreSQL será a fonte operacional de verdade do schedule.

O scheduler não manterá a verdade somente em:

```text
Timer
ScheduledExecutorService
cron em memória
variável estática
arquivo local
```

Esses mecanismos podem acordar a aplicação, mas não substituem o estado durável.

---

## 2. A migration não criará frequência funcional hardcoded

A tabela de schedules não terá uma linha semântica obrigatória com intervalo fixo embutido na migration.

A cadência inicial será fornecida por configuração externa.

Depois da primeira criação:

```text
PostgreSQL
=
fonte operacional de verdade
```

Um restart não deve sobrescrever alteração feita por controle operacional.

---

## 3. O scheduler não executará o pipeline

O scheduler termina sua responsabilidade ao criar trabalho durável.

Fluxo:

```text
scheduler
      ↓
ProcessingRun
      ↓
COLLECT_DEALS
      ↓
FASE 12
```

O scheduler não executa diretamente:

```text
HTTP Amazon
parser
enrichment
eligibility
filters
score
momentum
PublicationGenerator
channel
```

---

## 4. A orquestração da FASE 12 será reutilizada

Não será criado um segundo pipeline para execução recorrente.

Após o scheduler criar `ProcessingRun` e `COLLECT_DEALS`:

```text
ProcessingWorker
```

continua responsável por despachar:

```text
COLLECT_DEALS
ENRICH_DEAL
EVALUATE_DEAL
```

O retry funcional continua pertencendo à FASE 12.

---

## 5. A coordenação concorrente será feita por lease PostgreSQL

Uma instância tenta adquirir uma janela por operação atômica equivalente a:

```text
UPDATE ... WHERE
enabled = true
AND next_run_at <= now
AND lease ausente ou expirado
```

Quando a aquisição vence:

```text
lease_owner = schedulerInstanceId
lease_expires_at = now + leaseDuration
```

Somente a instância proprietária pode confirmar a janela adquirida.

---

## 6. Lease expirado poderá ser retomado

Um lease não representa lock permanente.

Se a instância desaparecer:

```text
leaseExpiresAt < now
```

outra instância pode assumir a janela.

Isso impede que uma falha entre aquisição e confirmação deixe o schedule bloqueado indefinidamente.

---

## 6.1. A próxima janela respeitará o trabalho de coleta anterior

O lease do scheduler resolve concorrência de aquisição da mesma janela, mas não é suficiente para impedir sobreposição entre janelas consecutivas.

Depois que uma janela é confirmada, `lastProcessingRunId` identifica a execução automática anterior.

Antes de adquirir uma nova janela, o scheduler verificará o `COLLECT_DEALS` associado a essa run.

Estados técnicos não terminais:

```text
PENDING
RUNNING
RETRY_WAIT
```

Enquanto qualquer um desses estados estiver presente para o `COLLECT_DEALS` da última run:

```text
tryAcquireDue
→ não adquire a próxima janela
```

Estados terminais:

```text
SUCCEEDED
DEAD
```

Nesses estados, o scheduler pode avançar quando `nextRunAt` estiver devido.

A decisão usa o estado do job, e não somente `ProcessingRun.status`.

Motivo:

```text
ProcessingRun = FAILED
COLLECT_DEALS = RETRY_WAIT
```

é um estado possível durante retry transitório. Nesse caso, a coleta anterior ainda possui trabalho elegível e uma nova coleta automática representaria sobreposição indesejada.

A verificação permanece no PostgreSQL e participa da mesma operação atômica de aquisição.

---

## 7. A transformação da janela em trabalho será transacional

A unidade lógica será:

```text
tryAcquireDue
      ↓
create ProcessingRun
      ↓
enqueue COLLECT_DEALS
      ↓
confirmScheduled
```

Essas operações pertencem à mesma transação JDBC.

Falha intermediária implica:

```text
rollback
```

Não é aceitável persistir estado parcial.

---

## 8. `ProcessingRun` terá identidade determinística por janela

Formato:

```text
scheduled:<scheduleKey>:<scheduledForInstant>
```

A identidade usa `Instant`.

Offsets diferentes que representam o mesmo instante produzem a mesma chave.

Essa identidade complementa o lease.

O lease previne a corrida normal.

A unique constraint da run permanece uma defesa adicional.

---

## 9. `COLLECT_DEALS` continuará idempotente

A chave inicial será:

```text
collect:<processingRunId>
```

A fila existente continua protegendo:

```text
job_type + idempotency_key
```

Não será criado um job especial de scheduler.

---

## 10. O sistema não fará catch-up ilimitado

Regra:

```text
naturalNext = scheduledFor + interval
```

Se a janela natural ainda está no futuro:

```text
nextRunAt = naturalNext
```

Se o sistema retornou depois que essa janela já passou:

```text
nextRunAt = now + interval
```

Não serão materializadas automaticamente todas as janelas intermediárias perdidas.

---

## 11. Razão para não fazer catch-up storm

Executar todas as janelas perdidas após downtime poderia causar:

```text
rajada de ProcessingRuns
rajada de HTTP
rajada de enrichment
pressão de banco
bloqueio da fonte
perda de utilidade operacional
```

O sistema prefere retomar a cadência a partir da recuperação.

Se uma política futura precisar reprocessar períodos históricos, ela deverá ser explícita e separada.

---

## 12. Pausa atuará sobre novas execuções

`pause` significa:

```text
não adquirir novas janelas
```

Não significa:

```text
apagar histórico
cancelar run já criada
apagar job já durável
reverter execução concluída
```

A pausa modifica o schedule.

Ela não modifica retroativamente o pipeline.

---

## 13. Pausa será permitida durante lease ativo

Uma pausa operacional pode ser registrada enquanto a janela atual já está em processo de transformação em trabalho durável.

Objetivo:

```text
deixar a unidade atual concluir de modo consistente
+
impedir a próxima aquisição
```

A confirmação da janela atual não deve ser invalidada somente porque o schedule foi pausado durante a transação concorrente.

---

## 14. Resume e mudança de intervalo respeitarão lease ativo

Operações que alteram o futuro temporal do schedule não devem sobrescrever uma aquisição concorrente.

Assim:

```text
resume
changeInterval
```

serão protegidos atomicamente contra lease incompatível.

O operador recebe falha controlada em vez de corrupção silenciosa.

---

## 15. A frequência funcional será configurável

A configuração inicial virá de:

```text
PROCESSING_SCHEDULE_INTERVAL
```

usando `Duration` ISO-8601.

Exemplos:

```text
PT15M
PT1H
PT30S
```

Não existe frequência funcional default embutida no código.

---

## 16. Parâmetros técnicos poderão possuir defaults operacionais

São diferentes da regra funcional de frequência.

Exemplos técnicos:

```text
schedulerPollInterval
schedulerLeaseDuration
workerIdleDelay
retryBaseDelay
retryMaxDelay
maxAttempts
```

Defaults técnicos podem existir desde que não transformem uma regra comercial/frequência funcional em constante oculta.

---

## 17. O schedule inicial será criado apenas se ausente

Será utilizado caso de uso equivalente a:

```text
EnsureProcessingScheduleUseCase
```

Comportamento:

```text
schedule ausente
→ criar

schedule existente
→ retornar estado persistido sem sobrescrever
```

A configuração externa é bootstrap.

Não é reconciliação autoritária a cada restart.

---

## 18. O primeiro schedule novo será imediatamente devido

Na primeira criação:

```text
nextRunAt = now
```

Assim, iniciar o daemon com schedule ainda inexistente produz uma primeira execução sem esperar um intervalo completo.

Depois da primeira confirmação, a cadência normal é aplicada.

---

## 19. O scheduler contínuo dependerá de um trigger abstrato

Contrato:

```text
ScheduledProcessingTrigger
```

O runner não depende diretamente de JDBC.

A implementação normal será:

```text
ScheduleProcessingRunUseCase::execute
```

Essa fronteira permite teste do loop sem banco.

---

## 20. O scheduler fará polling com espera

O scheduler não executará busy-loop.

Fluxo:

```text
trigger
→ espera pollInterval
→ trigger
```

inclusive quando uma janela acabou de ser criada.

`pollInterval` é técnico.

`ProcessingSchedule.interval` é funcional.

---

## 21. O worker contínuo envolverá `ProcessingWorker.runOnce()`

O loop do worker será:

```text
runOnce
   ↓
job encontrado?
   ├─ sim → tentar imediatamente a próxima unidade
   └─ não → aguardar workerIdleDelay
```

O runner não conhece retry.

O retry continua dentro da infraestrutura e casos de uso existentes da FASE 12.

---

## 22. Scheduler e worker usarão Connections diferentes

O processo contínuo possuirá pelo menos:

```text
schedulerConnection
workerConnection
```

A mesma instância de `Connection` não será compartilhada entre os dois loops concorrentes.

Cada lado mantém sua própria fronteira transacional.

---

## 23. A composition não criará threads

`ContinuousProcessingComposition` monta o grafo.

Ela não:

```text
starta thread
registra shutdown hook
chama System.exit
fecha Connections
```

Essas responsabilidades pertencem ao lifecycle/bootstrap.

---

## 24. O runtime coordenará duas threads

`ContinuousProcessingRuntime` possuirá:

```text
scheduler thread
worker thread
```

e um sinal compartilhado:

```text
keepRunning
```

O runtime não conhece regras funcionais.

---

## 25. Falha inesperada de um loop encerra o runtime

Não é aceitável manter:

```text
scheduler vivo + worker morto
```

nem:

```text
worker vivo + scheduler morto
```

quando o processo deveria operar como um único daemon.

A primeira falha terminal é preservada.

O outro loop recebe pedido de parada.

---

## 26. Shutdown será coordenado

O shutdown muda o sinal para falso e acorda waits bloqueados.

Depois do pedido:

```text
scheduler não abre nova janela
worker não inicia nova unidade depois de observar a parada
```

O estado funcional permanece persistido no PostgreSQL.

A ADR não transforma interrupção de thread em mecanismo de transação.

---

## 27. O bootstrap possuirá o lifecycle dos recursos

Responsabilidades do bootstrap/application:

```text
abrir Connections
montar composition
montar runtime
registrar shutdown hook no entrypoint
fechar runtime
fechar Connections
```

A ownership de recursos não fica espalhada entre adapters.

---

## 28. A CLI administrativa permanecerá separada do daemon

Serão mantidos entrypoints distintos:

```text
OperationalCliMain
ContinuousProcessingMain
```

A CLI não precisa permanecer bloqueada para o scheduler funcionar.

O daemon não transforma a CLI em servidor.

---

## 29. Status, pause, resume e interval serão casos de uso

A apresentação não executará SQL.

Fluxo:

```text
SchedulesCliCommand
      ↓
application use case
      ↓
ProcessingSchedulePort
      ↓
JdbcProcessingScheduleAdapter
```

A interface continua seguindo a ADR-0009.

---

## 30. Restrições Amazon serão representadas explicitamente

Serão distinguidos cenários como:

```text
challenge
CAPTCHA
blocked
```

por conceito de aplicação equivalente a:

```text
SourceRestrictionException
```

A classificação de falha poderá então registrar código operacional explícito.

---

## 31. HTTP 429 continuará transitório

`429` representa evidência de rate limiting.

A política corrente o classifica como falha transitória do pipeline.

A FASE 17 não cria retry paralelo.

O retry continua usando o mecanismo da FASE 12.

---

## 32. HTTP 403 será tratado como restrição/permanente na política atual

A política local da FASE 17 classifica:

```text
403
→ permanente
```

até que exista evidência operacional que justifique regra diferente.

Isso evita loops automáticos indefinidos sobre uma restrição sem indício de recuperação.

---

## 33. Timeout continuará transitório

Timeout permanece compatível com retry já existente.

A FASE 17 apenas garante que o runtime recorrente não esconda essa semântica.

---

## 34. Nenhum bypass será implementado

Esta decisão rejeita mecanismos destinados a contornar proteção da fonte.

Não fazem parte da solução:

```text
CAPTCHA solving
proxy rotation para evasão
fingerprint spoofing
automação destinada a contornar bloqueio
```

Restrições são fatos operacionais a observar e tratar.

---

## 35. A arquitetura permanecerá substituível por fonte futura

A versão 1.0 não depende da Amazon Creators API.

A fronteira deve continuar apta a receber no futuro:

```text
AmazonHtmlSource
AmazonCreatorsApiSource
```

ou composição equivalente.

Nenhuma regra de scheduling depende de HTML Amazon.

---

## 36. Observabilidade da FASE 16 será preservada

O runtime de produção continuará usando:

```text
StructuredOperationalLogPort
IntegrationObservationRecorder
JdbcIntegrationObservationPersistenceAdapter
```

A introdução do scheduler não remove correlação do pipeline.

---

## 37. O scheduler não criará uma nova taxonomia global de resiliência

A FASE 20 continua responsável por ampliar:

```text
SOURCE_CHANGED
AUTHENTICATION
RATE_LIMIT
DATA_UNAVAILABLE
DATABASE
CHANNEL
CONFIGURATION
```

quando necessário.

A FASE 17 adiciona apenas a semântica diretamente necessária para a fonte recorrente atual.

---

## 38. Seleção de publicação não faz parte desta ADR

Apesar da relação com ADR-0010, esta ADR não implementa:

```text
PublicationSelectionPolicy
cooldown
quota diária
quota horária
prioridade de republicação
histórico de publicação por canal
```

Esses conceitos pertencem ao fluxo de publicação.

O scheduler da FASE 17 não deve conter essa lógica.

---

## 39. Outbox não faz parte desta ADR

A FASE 18 introduzirá contrato de canal e outbox.

O schedule de processamento não será reutilizado como outbox.

São problemas diferentes:

```text
quando coletar/processar
≠
qual Publication entregar
```

---

## 40. Broker externo não será introduzido

PostgreSQL continuará sendo utilizado para:

```text
estado de schedule
fila de processing jobs
coordenação
```

enquanto volume e métricas não demonstrarem necessidade diferente.

Kafka/RabbitMQ/SQS não são necessários para fechar a FASE 17.

---

# Alternativas consideradas

## A. `ScheduledExecutorService` como fonte de verdade

Alternativa:

```text
JVM inicia
→ agenda tarefa em memória
```

Rejeitada como fonte de verdade porque perde estado em restart e não coordena múltiplas instâncias por si só.

Pode ser útil como mecanismo técnico de wake-up, mas não substitui o schedule persistido.

---

## B. Cron externo sem estado persistido

Alternativa:

```text
cron
→ inicia comando a cada N minutos
```

Custos:

```text
coordenação entre instâncias
pausa operacional
última janela
rastreabilidade
lease
mudança de intervalo
```

ficariam espalhados fora da aplicação.

Rejeitada como modelo principal.

---

## C. Lock somente na JVM

Alternativa:

```text
synchronized
ReentrantLock
```

Rejeitada porque coordena somente threads da mesma JVM.

Não atende duas instâncias.

---

## D. Advisory lock sem linha de schedule

PostgreSQL advisory locks poderiam impedir concorrência.

Entretanto, sozinhos não preservam:

```text
nextRunAt
interval
enabled
lastScheduledFor
lastProcessingRunId
```

A linha persistida é necessária de qualquer forma.

O lease explícito torna o estado observável e administrável.

---

## E. Criar um job `SCHEDULE`

Alternativa:

```text
ProcessingJobType.SCHEDULE
```

Rejeitada.

Scheduling não é uma etapa de processamento de uma oferta.

A fila da FASE 12 deve continuar contendo trabalho funcional:

```text
COLLECT_DEALS
ENRICH_DEAL
EVALUATE_DEAL
```

---

## F. Executar `AmazonDealProcessingService` síncrono em loop

Rejeitada.

Isso duplicaria a composição e ignoraria a orquestração durável da FASE 12.

A execução contínua deve consumir a fila durável já existente.

---

## G. Criar nova fila/broker

Rejeitada por ausência de necessidade demonstrada.

PostgreSQL já fornece:

```text
transações
constraints
SKIP LOCKED
estado durável
coordenação
```

suficientes para o volume atual.

---

## H. Catch-up de todas as janelas perdidas

Rejeitada como comportamento padrão.

Um downtime prolongado poderia gerar rajada de trabalho e chamadas externas.

Reprocessamento histórico, se necessário, deve ser um caso de uso explícito.

---

## I. Sobrescrever schedule a cada restart

Alternativa:

```text
env
→ sempre UPDATE processing_schedule
```

Rejeitada.

Isso faria restart desfazer:

```text
pause
alteração de intervalo
nextRunAt operacional
```

A configuração externa deve criar o estado inicial, não substituir continuamente o estado administrado.

---

# Consequências positivas

A decisão produz:

- execução recorrente;
- schedule auditável;
- frequência configurável;
- coordenação entre múltiplas instâncias;
- proteção contra sobreposição entre instâncias e entre janelas consecutivas;
- pause/resume;
- reutilização da FASE 12;
- ausência de segunda orquestração;
- PostgreSQL como fonte de verdade;
- bootstrap reproduzível;
- lifecycle explícito;
- shutdown coordenado;
- testes herméticos dos loops;
- testes JDBC de concorrência;
- preparo para fonte futura;
- ausência de dependência de broker externo.

---

# Custos e trade-offs

## Polling

O scheduler consulta periodicamente PostgreSQL.

Para a escala atual isso é deliberado.

Se métricas futuras mostrarem custo relevante, o mecanismo de wake-up poderá evoluir sem alterar a semântica persistida.

## Duas Connections

O processo contínuo precisa manter conexões separadas para scheduler e worker.

Isso aumenta minimamente o uso de conexões, mas preserva segurança transacional entre threads.

## Lease exige relógio coerente

A semântica depende de timestamps.

O projeto usa `Clock` injetável na aplicação e timestamps PostgreSQL/JDBC de forma controlada.

Ambientes distribuídos futuros devem preservar relógios razoavelmente sincronizados.

## Pausa não cancela retroativamente trabalho

Isso é intencional.

Uma pausa impede novas execuções, mas trabalho já persistido segue a semântica durável da fila.

## Sem catch-up automático

Janelas intermediárias perdidas não são materializadas automaticamente.

Essa é uma escolha de proteção operacional, não limitação acidental.

---

# Invariantes resultantes

1. Um `ProcessingSchedule` possui intervalo positivo.
2. Um schedule pausado não pode ser adquirido para nova execução.
3. Lease owner e expiration aparecem juntos.
4. Apenas o owner do lease confirma sua janela.
5. A criação de run/job/confirmação é atômica.
6. Uma janela lógica possui `run_key` determinística.
7. Um `COLLECT_DEALS` inicial possui idempotency key determinística por run.
8. Reiniciar o processo não sobrescreve schedule persistido.
9. Scheduler e worker não compartilham a mesma instância JDBC `Connection`.
10. Scheduler não executa regras de coleta ou avaliação.
11. Worker contínuo reutiliza `ProcessingWorker`.
12. Pausa preserva histórico.
13. Restrição de fonte não aciona bypass.
14. Runtime não permanece intencionalmente com somente um dos dois loops após falha terminal.
15. PostgreSQL continua sendo a fonte de verdade operacional.
16. Uma nova janela não é adquirida enquanto o `COLLECT_DEALS` da `lastProcessingRunId` estiver em `PENDING`, `RUNNING` ou `RETRY_WAIT`.
17. `SUCCEEDED` e `DEAD` são estados terminais do `COLLECT_DEALS` que permitem avanço do schedule quando a próxima janela estiver devida.

---

# Configuração operacional

Variáveis da implementação:

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

`PROCESSING_SCHEDULE_INTERVAL` é obrigatória para o primeiro bootstrap.

A persistência existente não é sobrescrita por esse valor em restart.

---

# Persistência

Migration principal do estado de scheduling:

```text
V23__processing_schedule.sql
```

Tabela:

```text
processing_schedule
```

Migration de suporte ao caminho crítico do guard de sobreposição:

```text
V24__processing_schedule_overlap_guard_index.sql
```

Índice parcial:

```text
idx_processing_job_active_collect_by_run
```

O índice inclui apenas:

```text
COLLECT_DEALS
+
PENDING / RUNNING / RETRY_WAIT
+
processing_run_id presente
```

A V24 não modifica a semântica da fila da FASE 12. Ela sustenta de forma eficiente a consulta atômica que impede a próxima janela enquanto a coleta anterior ainda estiver não terminal.

Migrations anteriores permanecem imutáveis.

---

# Evidência de implementação

O gate final local da FASE 17 registrou:

```text
Tests run: 1047
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

Também foram provados:

```text
duas instâncias disputando a mesma janela
→ exatamente uma run

janela seguinte com COLLECT_DEALS PENDING / RUNNING / RETRY_WAIT
→ aquisição bloqueada

COLLECT_DEALS terminal em SUCCEEDED
→ próxima janela pode avançar

duas janelas recorrentes sem sobreposição
→ uma run por janela

lease expirado
→ takeover por outra instância

downtime prolongado
→ sem catch-up burst

runtime real + PostgreSQL
→ pelo menos três ciclos automáticos completos
```

---

# Relação com o roadmap

A decisão atende diretamente o fluxo definido:

```text
scheduler
   ↓
ProcessingRun
   ↓
orquestração da FASE 12
```

e preserva as fronteiras:

```text
FASE 18
→ outbox / contratos de canal

FASE 19
→ Telegram / WhatsApp

FASE 20
→ resiliência ampla / recovery / hardening
```

---

# Estado final da decisão

A decisão foi implementada e validada localmente durante a FASE 17.

Status:

```text
ACEITA
```

Alterações futuras devem preservar a semântica desta ADR ou registrar nova decisão arquitetural quando houver mudança incompatível, principalmente em:

```text
identidade da janela
política de catch-up
semântica de pause
ownership de lease
fonte operacional de verdade
fronteira scheduler / worker
```
