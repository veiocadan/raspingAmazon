# ADR 0012 — Agendamento e execução contínua

## Status

ACEITA

## Contexto

Após a FASE 16, o Rasping Amazon possui:

- processamento persistente por `ProcessingRun`;
- fila durável de `ProcessingJob` em PostgreSQL;
- idempotência por execução e por job;
- retry e backoff;
- leases de processamento;
- recuperação de jobs abandonados;
- `ProcessingWorker` capaz de executar uma unidade de trabalho;
- observabilidade correlacionada por execução;
- interface operacional independente do pipeline.

A FASE 17 transforma esse processamento em execução recorrente sem criar um segundo pipeline.

A fonte de planejamento vigente para esta fase é:

```text
docs/phases/ROADMAP_RASPING_AMAZON_V1.md
```

O fluxo definido pelo roadmap é:

```text
scheduler
   ↓
ProcessingRun
   ↓
orquestração da FASE 12
```

O scheduler não contém regras de coleta, parsing, enriquecimento ou avaliação.

## Decisão

A execução contínua será formada por duas responsabilidades separadas:

```text
scheduler recorrente
        │
        ▼
solicitação idempotente de ProcessingRun
        │
        ▼
fila durável existente
```

e:

```text
fila durável existente
        │
        ▼
ProcessingWorker.runOnce()
        │
        ▼
processamento existente
```

O scheduler decide quando uma nova execução lógica deve ser solicitada.

O worker continua decidindo apenas como processar uma unidade de trabalho já persistida.

## Estado do agendamento

O estado operacional do agendamento será persistido no PostgreSQL.

O modelo deverá possuir, conceitualmente:

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

O PostgreSQL continuará sendo a fonte de verdade operacional.

Estado crítico do scheduler não dependerá somente de memória da JVM.

## Frequência

A frequência de execução será configurável.

Ela não ficará hardcoded no runtime.

A configuração persistida determinará quando o próximo ciclo pode ser solicitado.

A versão 1.0 não requer expressão cron arbitrária.

Um intervalo configurável é suficiente enquanto atender ao requisito operacional real.

## Janela lógica

Cada disparo possui um instante lógico denominado:

```text
scheduledFor
```

Esse instante participa da identidade da execução agendada.

A chave idempotente será derivada de forma determinística a partir de:

```text
scheduleKey
+
scheduledFor
```

Uma mesma janela lógica não poderá produzir duas `ProcessingRun` diferentes.

A idempotência já existente de `ProcessingRun.runKey` continuará sendo a barreira final contra duplicação.

## Execuções atrasadas

A FASE 17 não implementará catch-up ilimitado.

Se a aplicação permanecer desligada por múltiplos intervalos, o retorno do processo poderá executar no máximo uma janela vencida antes de reposicionar a próxima execução no futuro.

Exemplo:

```text
intervalo = 15 minutos

nextRunAt = 10:00

aplicação retorna = 13:00

resultado:

um ciclo vencido pode ser solicitado
+
nextRunAt volta a apontar para instante futuro
```

Não serão criadas automaticamente todas as execuções intermediárias perdidas.

Essa decisão evita rajadas de chamadas externas após indisponibilidade do processo.

## Concorrência entre instâncias

A prevenção de sobreposição não dependerá de:

```text
synchronized
AtomicBoolean
estado estático
arquivo local
```

A aquisição da janela será coordenada pelo PostgreSQL através de lease persistente.

Somente uma instância poderá possuir a janela agendada em determinado instante.

O lease terá expiração.

Se a instância proprietária encerrar antes de concluir o disparo, outra instância poderá recuperar a janela após a expiração.

## Atomicidade e idempotência

A coordenação deverá tolerar falha nos pontos:

```text
lease adquirido
    ↓
ProcessingRun persistida
    ↓
COLLECT_DEALS submetido
    ↓
janela confirmada
```

A recuperação não poderá criar uma segunda execução lógica para o mesmo `scheduledFor`.

A combinação de:

```text
lease do scheduler
+
runKey determinística
+
idempotencyKey do ProcessingJob
```

será utilizada para garantir reentrada segura.

## Sobreposição de ProcessingRun

A FASE 17 impede a criação duplicada para a mesma janela lógica.

A política também deverá impedir uma nova coleta automática quando houver uma execução automática anterior ainda em estado que represente processamento ativo para o mesmo schedule, quando isso caracterizar sobreposição indesejada.

Jobs já persistidos continuam possuindo ciclo de vida próprio.

A política de agendamento não modifica retroativamente jobs existentes.

## Pausa operacional

A pausa suspende:

```text
novas execuções automáticas
```

A pausa não apaga nem cancela:

```text
ProcessingRun existentes
ProcessingJob existentes
snapshots
avaliações
publicações
histórico
observabilidade
```

Trabalho já persistido continua pertencendo à orquestração existente.

Portanto:

```text
PAUSED
≠
DELETE
```

e:

```text
PAUSED
≠
cancelamento destrutivo da fila
```

A retomada apenas volta a permitir novas janelas automáticas.

## Worker contínuo

`ProcessingWorker` permanecerá sem loop interno.

O runtime operacional chamará:

```text
ProcessingWorker.runOnce()
```

repetidamente.

Quando houver trabalho, a fila continuará avançando.

Quando não houver trabalho elegível, o runtime aguardará um intervalo de polling antes da próxima tentativa.

Loop, temporização, threads e lifecycle permanecem fora do worker de aplicação.

## Concorrência JDBC

Scheduler e worker contínuo podem operar simultaneamente.

Eles não compartilharão uma única `java.sql.Connection` entre threads.

Cada responsabilidade operacional deverá utilizar sua própria conexão JDBC ou composição equivalente.

A FASE 17 não introduzirá pool externo de conexões sem necessidade operacional comprovada.

## Retry

A FASE 17 não implementará um segundo motor de retry.

Continuam sendo fonte de verdade:

```text
ProcessingFailureClassifier
ProcessingJobFailureHandler
RetryBackoffPolicy
RETRY_WAIT
DEAD
leases dos ProcessingJob
```

O runtime contínuo apenas permite que jobs que voltaram a ficar elegíveis sejam novamente consumidos.

## Restrições da fonte Amazon

A execução contínua deve tratar como falha ou restrição da fonte situações como:

```text
HTTP 429
HTTP 403
timeout
challenge
CAPTCHA
bloqueio
```

Nenhum mecanismo destinado a contornar proteções da Amazon será implementado.

Falhas HTTP e de transporte continuarão utilizando os contratos existentes.

Respostas HTTP tecnicamente bem-sucedidas que representem challenge, CAPTCHA ou bloqueio deverão ser detectadas em uma fronteira específica da fonte Amazon.

O coletor HTTP genérico não receberá conhecimento específico da Amazon.

## Fonte futura

A FASE 17 não depende da Amazon Creators API.

A fonte operacional permanece compatível com HTML.

A arquitetura deverá continuar permitindo futuramente adapters equivalentes a:

```text
AmazonHtmlSource
AmazonCreatorsApiSource
```

sem alterar domínio ou scheduler.

O scheduler conhece uma fonte de processamento, não detalhes de aquisição de cada implementação.

## Observabilidade

O scheduler reutilizará a infraestrutura de observabilidade da FASE 16.

Quando uma janela produzir uma `ProcessingRun`, deverá ser possível correlacionar a decisão de agendamento com a execução resultante.

A observabilidade não poderá alterar o resultado funcional do scheduler.

## Controle operacional

As operações de administração do scheduler serão expostas por casos de uso.

A interface operacional poderá oferecer comandos equivalentes a:

```text
scheduler status
scheduler pause
scheduler resume
```

A interface não acessará SQL diretamente.

A interface continuará obedecendo:

```text
presentation
    ↓
application
    ↓
ports
    ↓
infrastructure
```

## Runtime

A aplicação contínua possuirá composição própria.

Conceitualmente:

```text
ContinuousProcessingMain
        │
        ├── scheduler runtime
        │       ↓
        │   scheduling use cases
        │
        └── worker runtime
                ↓
          ProcessingWorker.runOnce()
```

O runtime será responsável pelo lifecycle dos componentes operacionais.

## Shutdown

No encerramento normal:

```text
parar novos ticks do scheduler
        ↓
parar novas iterações do worker
        ↓
aguardar encerramento controlado
        ↓
fechar recursos JDBC
```

O shutdown não precisa transformar estado durável em estado de memória.

Jobs persistidos continuam recuperáveis por execução posterior.

## Fora do escopo da FASE 17

Não pertencem a esta fase:

```text
outbox de publicação
PublicationChannel
Telegram
WhatsApp
taxonomia ampla de falhas de produção
dead-letter genérica adicional
circuit breaker
broker externo
Kafka
RabbitMQ
microserviços
cluster
deploy em Raspberry Pi
serviço Linux
backup e restore
release v1.0
```

Essas responsabilidades permanecem nas fases posteriores definidas pelo roadmap.

## Dependências externas

A FASE 17 utilizará inicialmente:

```text
JDK
PostgreSQL
JDBC
infraestrutura já existente
```

Nenhuma biblioteca externa de scheduler será adicionada enquanto as capacidades da JDK forem suficientes.

Nenhuma fila externa será adicionada enquanto PostgreSQL atender às necessidades medidas.

## Consequências

O sistema passa a:

```text
iniciar ciclos automaticamente
evitar duplicação da mesma janela
coordenar múltiplas instâncias
sobreviver a restart com estado persistido
permitir pausa sem apagar histórico
continuar processando retries existentes
preservar rastreabilidade por ProcessingRun
```

Ao mesmo tempo:

```text
ProcessingWorker permanece pequeno
scheduler permanece sem regra de negócio
coleta permanece desacoplada do scheduler
PostgreSQL continua sendo a fonte de verdade
infraestrutura pesada não é antecipada
```

## Critério de conclusão

A FASE 17 estará concluída quando o sistema puder executar ciclos recorrentes sem:

```text
duplicar observações indevidamente
sobrepor execuções de forma incorreta
perder rastreabilidade
exigir intervenção em ciclos normais
```

e quando for possível demonstrar:

```text
frequência configurável
+
disparo automático
+
ProcessingRun persistida
+
processamento contínuo da fila
+
coordenação entre instâncias
+
pausa e retomada
+
tratamento explícito de restrições da fonte
+
shutdown controlado
```
