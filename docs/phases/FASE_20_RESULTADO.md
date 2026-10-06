# FASE 20 — Tratamento de erros, resiliência e recuperação

## 1. Status

```text
FASE 20
STATUS LOCAL: CONCLUÍDA

Branch:
feat/fase-20-resiliencia-recuperacao

HEAD local:
acba934 test: validate destructive phase 20 recovery scenarios

Suíte completa:
1786 testes

Failures:
0

Errors:
0

Skipped:
0

Build:
SUCCESS

External probe:
SUCCESS

Working tree:
CLEAN

PostgreSQL:
fonte de verdade operacional

Schema:
versão 37

Última migration:
V37__publication_delivery_resolution_audit.sql

Push:
AINDA NÃO EXECUTADO

Daemon:
AINDA NÃO EXECUTADO
```

---

## 2. Objetivo da fase

A FASE 20 consolidou tratamento de falhas e recuperação operacional sem mover a autoridade do estado para memória local.

Os principais objetivos foram:

- classificar falhas de forma suficiente para decidir retry, rejeição, pausa, alerta, reprocessamento ou intervenção humana;
- distinguir falhas transitórias, permanentes e resultados externos ambíguos;
- preservar evidência antes de chamadas externas;
- impedir retry cego quando uma entrega pode ter ocorrido;
- recuperar trabalho abandonado após restart;
- representar dead-letter de forma persistente;
- permitir reprocessamento controlado e auditável;
- respeitar rate limit preventivo e `Retry-After` do provider;
- preservar idempotência;
- operar fail closed;
- validar os mecanismos com testes destrutivos controlados.

---

## 3. Decisões arquiteturais consolidadas

### 3.1 PostgreSQL permanece a fonte de verdade

O estado operacional crítico permanece persistido.

Não foi introduzida fila externa, cache autoritativo ou estado de recuperação exclusivamente em memória.

As unidades principais são:

```text
processing_job
publication_outbox
publication_attempt
publication_rate_limit_state
processing_job_reprocess_event
publication_delivery_resolution_event
```

---

### 3.2 Falha de processamento e origem da falha permanecem conceitos separados

A taxonomia introduzida na FASE 20 separa:

```text
retry semantics
    TRANSIENT
    PERMANENT

failure category
    NETWORK
    SOURCE_RESTRICTION
    SOURCE_CHANGED
    AUTHENTICATION
    RATE_LIMIT
    DATA_UNAVAILABLE
    DATABASE
    CHANNEL
    CONFIGURATION
    PROCESSING
    UNKNOWN

handling action
    RETRY
    REJECT
    PAUSE
    ALERT
    REPROCESS
    OPERATOR_INTERVENTION
```

O sistema não transforma automaticamente toda exceção externa em retry.

---

### 3.3 Resultado externo ambíguo é distinto de falha transitória

Foi consolidado o estado:

```text
DELIVERY_UNKNOWN
```

Semântica:

```text
a chamada externa pode ter produzido efeito
mas a aplicação não possui confirmação confiável
```

Portanto:

```text
DELIVERY_UNKNOWN
!= FAILED_TRANSIENT
!= FAILED_PERMANENT
```

Um resultado ambíguo não é automaticamente reenviado.

---

## 4. FASE 20-A — contrato de resiliência

Commit:

```text
e19515c docs: define resilience and recovery semantics for phase 20
```

Foram formalizados:

- taxonomia de falhas;
- ação operacional;
- fail closed;
- retry limitado;
- distinção entre erro conhecido e resultado externo desconhecido;
- dead-letter lógico;
- reprocessamento controlado;
- recovery após restart;
- condição para eventual circuit breaker.

Decisão:

```text
circuit breaker NÃO foi introduzido
```

Motivo:

```text
não foi demonstrada necessidade suficiente nesta fase
```

---

## 5. FASE 20-B — modelo operacional de falhas

Commit:

```text
b748e85 feat: introduce operational failure taxonomy
```

Baseline após a etapa:

```text
1624 testes verdes
```

A aplicação passou a trabalhar com classificação operacional explícita em vez de mensagens de exceção livres como autoridade de decisão.

---

## 6. FASE 20-C — falhas da fonte Amazon

Commits:

```text
c7ce687 feat: preserve product page acquisition failure semantics
32c14c9 feat: use rendered product pages in continuous runtime
6d949df feat: fail closed on invalid Amazon product pages
6d23434 feat: distinguish unavailable Amazon products from unknown evidence
```

Baseline:

```text
1667 testes verdes
```

Foram distinguidos cenários como:

- página indisponível;
- estrutura inesperada;
- produto removido;
- evidência insuficiente;
- HTML inválido;
- aquisição falhou;
- produto realmente indisponível;
- ausência de evidência confiável.

A política permaneceu fail closed.

---

## 7. FASE 20-D — publicação externa e resultado ambíguo

Commits:

```text
b75f30e feat: define unknown publication delivery persistence
5dc140c feat: add durable publication attempt start barrier
86992ba feat: complete durable publication attempts
f3bbbf4 feat: persist publication attempt before provider call
ffa983b feat: recover ambiguous publication deliveries safely
f63f56e feat: model unknown publication delivery result
f12d6c7 feat: classify ambiguous channel outcomes as unknown
```

Baseline:

```text
1694 testes verdes
```

### 7.1 Start barrier persistente

Antes de chamar um provider externo:

```text
publication_attempt = STARTED
COMMIT
↓
provider
```

Nenhuma transação JDBC permanece aberta durante HTTP externo.

---

### 7.2 Classificação de resultado dos canais

Consolidado:

```text
transport exception
    → DELIVERY_UNKNOWN

HTTP 408 genérico
    → DELIVERY_UNKNOWN

HTTP 5xx genérico
    → DELIVERY_UNKNOWN

2xx sem confirmação suficiente
    → DELIVERY_UNKNOWN

HTTP 429
    → FAILED_TRANSIENT

rejeição explícita conhecida do provider
    → FAILED_TRANSIENT ou FAILED_PERMANENT
```

Isso impede que uma chamada cujo efeito externo é desconhecido seja repetida cegamente.

---

### 7.3 Recovery de tentativa STARTED abandonada

Um `publication_attempt` abandonado em `STARTED` após expiração do lease é transformado em evidência de ambiguidade.

A outbox também passa para:

```text
DELIVERY_UNKNOWN
```

e deixa de ser automaticamente reclamável.

---

## 8. FASE 20-E — recuperação no startup

Commits:

```text
d6c2621 feat: define continuous startup recovery
df46c51 feat: support publication dispatch in continuous composition
28d0fa0 feat: recover durable work before continuous startup
16b5c74 fix: drain startup recovery backlog before runtime
```

O startup passou a executar recovery síncrono antes do runtime normal.

Ordem:

```text
1. publication_outbox recovery
2. processing_job lease recovery
3. PUBLICATION_DISPATCH reconciliation
4. somente então runtime normal
```

Se qualquer etapa falhar:

```text
startup falha fechado
workers/scheduler não iniciam
```

---

### 8.1 Drain até quiescência

O commit:

```text
16b5c74 fix: drain startup recovery backlog before runtime
```

corrigiu uma lacuna detectada antes do gate final.

Os parâmetros:

```text
PROCESSING_JOB_RECOVERY_BATCH_SIZE
PUBLICATION_DISPATCH_RECONCILIATION_LIMIT
```

passaram a ser tamanhos de página, e não teto total de recovery.

Agora:

```text
página cheia
→ continua drenando

página parcial
→ quiescência

reconciliation:
página cheia sem progresso durável
→ fail closed
```

Baseline após a correção:

```text
1780 testes verdes
```

---

## 9. FASE 20-F — terminalidade, dead-letter e reprocessamento

### 9.1 ProcessingJob DEAD

Commits:

```text
875084f feat: define controlled processing job reprocessing
8392eb7 feat: reprocess dead processing jobs atomically
002f9ec feat: compose controlled processing job reprocessing
```

Semântica:

```text
ProcessingJob.DEAD
=
dead-letter lógico persistente
```

Não foi criada uma fila paralela desnecessária.

---

### 9.2 Reprocessamento controlado

O reprocessamento:

- exige decisão explícita;
- exige `requestKey`;
- registra operador/motivo/timestamp;
- preserva snapshot do DEAD anterior;
- reabre a mesma identidade persistida;
- não cria um segundo job lógico;
- é atômico;
- é idempotente por `requestKey`.

Transição:

```text
DEAD
↓
decisão operacional auditável
↓
mesmo processing_job
↓
PENDING
```

---

### 9.3 Resolução de DELIVERY_UNKNOWN

Commits:

```text
29a194f feat: define ambiguous publication delivery resolution
ab45a99 feat: resolve ambiguous publication deliveries atomically
9aaab44 feat: compose ambiguous publication delivery resolution
```

Foi criada a migration:

```text
V37__publication_delivery_resolution_audit.sql
```

Decisões permitidas:

```text
CONFIRMED_DELIVERED
    → SUCCEEDED
    → NÃO reenviar

CONFIRMED_NOT_DELIVERED
    → PENDING
    → reenviar permitido

REMAINS_UNKNOWN
    → DELIVERY_UNKNOWN
    → NÃO reenviar
```

A tentativa externa original não é reescrita retroativamente.

Exemplo:

```text
publication_attempt.status = DELIVERY_UNKNOWN
```

permanece como verdade histórica do que a aplicação sabia no momento da execução.

O conhecimento posterior é registrado separadamente em:

```text
publication_delivery_resolution_event
```

---

## 10. FASE 20-G — rate limit e Retry-After

Commits:

```text
6bd1bf3 feat: preserve publication retry after metadata
7996af4 feat: honor provider retry floors for publication
0591860 feat: propagate provider rate limits across publication workers
```

### 10.1 Headers HTTP preservados

`PublicationHttpResponse` passou a preservar headers.

Foi implementada interpretação de:

```text
Retry-After: delay-seconds
Retry-After: HTTP-date
```

Header inválido ou ausente não quebra a resposta.

---

### 10.2 Piso do provider e backoff local

O agendamento do retry respeita:

```text
max(
    backoff local,
    retryNotBefore do provider
)
```

O `Retry-After` pode atrasar o retry.

Ele nunca pode antecipar o backoff local.

---

### 10.3 Retry-After não aumenta orçamento de tentativas

Regra:

```text
política local decidiu que não há mais retry
+
provider enviou Retry-After
=
não há nova tentativa
```

O provider controla o instante mínimo de uma tentativa permitida, não o orçamento máximo de tentativas.

---

### 10.4 Rate limit persistente compartilhado

O `Retry-After` de uma outbox também é propagado para:

```text
publication_rate_limit_state
```

por `integrationKey`.

Assim, um 429 recebido por um worker protege outros workers que usam a mesma integração física.

A persistência utiliza:

```text
next_allowed_at =
max(
    next_allowed_at persistido,
    provider floor,
    instante observado
)
```

O limiter nunca é reduzido por uma resposta posterior.

Baseline após a G1C:

```text
1773 testes verdes
```

---

## 11. FASE 20-H — testes destrutivos controlados

Commit:

```text
acba934 test: validate destructive phase 20 recovery scenarios
```

Foram adicionados testes PostgreSQL que fabricam estados equivalentes a processo morto/restart.

### Cenários validados

#### 11.1 Backlog de ProcessingJob maior que uma página

```text
RUNNING expirado
↓
recovery em páginas
↓
RETRY_WAIT ou DEAD
↓
backlog completamente drenado
```

---

#### 11.2 DEAD seguido de reprocessamento controlado

```text
RUNNING esgotado
↓
lease expira
↓
DEAD
↓
decisão operacional
↓
mesma identidade
↓
PENDING
```

Sem duplicação lógica.

---

#### 11.3 Crash após start barrier da publicação

Estado fabricado:

```text
publication_outbox = PROCESSING
publication_attempt = STARTED
worker desaparece
```

Após recovery:

```text
publication_outbox = DELIVERY_UNKNOWN
publication_attempt = DELIVERY_UNKNOWN
```

A outbox não volta automaticamente para claim.

---

#### 11.4 Redelivery somente após confirmação explícita

Após:

```text
CONFIRMED_NOT_DELIVERED
```

a mesma outbox pode voltar a:

```text
PENDING
```

A tentativa ambígua histórica permanece intacta.

---

#### 11.5 Ambiguidade não resolvida permanece bloqueada

Após:

```text
REMAINS_UNKNOWN
```

o item continua:

```text
DELIVERY_UNKNOWN
```

e não entra no claim automático.

---

#### 11.6 Reconciliation após restart maior que uma página

Várias `ProcessingRun` sem `PUBLICATION_DISPATCH` foram reconciliadas com:

```text
page size = 1
```

Todas receberam trabalho durável.

Uma segunda reconciliação não criou duplicatas.

---

#### 11.7 Rate limit sobrevive a nova conexão

O teste:

1. grava o piso de provider;
2. fecha a `Connection`;
3. abre uma nova `Connection`;
4. confirma que outro worker continua respeitando o mesmo `next_allowed_at`.

Isso comprova que o limiter não depende de memória local.

---

## 12. Resultado final de testes

Suíte completa executada após 20-H:

```text
Tests run: 1786
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

Também foi executado:

```text
external-probe
```

com:

```text
BUILD SUCCESS
```

O gate destrutivo isolado foi executado novamente:

```text
Tests run: 6
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

---

## 13. Diagnóstico do aviso de shutdown do Surefire

Na execução completa de 1786 testes apareceu:

```text
Surefire is going to kill self fork JVM.
The exit has elapsed 30 seconds after System.exit(0).
```

O aviso não alterou o resultado Maven:

```text
BUILD SUCCESS
```

Foi analisado o thread dump produzido pelo próprio Surefire.

### Threads presentes

O dump mostrou somente:

- `main` do `ForkedBooter`;
- `Reference Handler`;
- `Finalizer`;
- `Signal Dispatcher`;
- `Attach Listener`;
- `Notification Thread`;
- `Common-Cleaner`;
- threads internas do Surefire.

Não foi encontrada thread da aplicação, worker do projeto, Playwright, HTTP server, executor da aplicação ou scheduler do Rasping Amazon impedindo o shutdown.

A thread principal estava em:

```text
org.apache.maven.surefire.booter.ForkedBooter.acquireOnePermit
org.apache.maven.surefire.booter.ForkedBooter.acknowledgedExit
```

Conclusão:

```text
aviso de coordenação/shutdown do fork do Surefire
não indica leak demonstrado da FASE 20
```

Além disso, os seis testes destrutivos da 20-H foram reexecutados isoladamente e encerraram normalmente, sem reproduzir o aviso.

### Classificação operacional

```text
BLOQUEADOR DA FASE 20:
NÃO

REGRESSÃO DEMONSTRADA DA 20-H:
NÃO

OBSERVAÇÃO DE TOOLING:
SIM
```

Não foi aumentado timeout do Surefire para mascarar o comportamento.

---

## 14. Cobertura dos erros previstos para a FASE 20

| Situação | Tratamento consolidado |
|---|---|
| página indisponível | classificada; fail closed/retry conforme evidência |
| mudança na estrutura Amazon | falha de fonte/estrutura distinta |
| produto sem ASIN | rejeição estrutural |
| produto sem preço | ausência de dado tratável |
| produto sem `% vendidos` | ausência preservada, sem inventar dado |
| API/fonte indisponível | falha externa classificada |
| limite de requisições | 429 + backoff + Retry-After + limiter persistente |
| timeout de publicação | DELIVERY_UNKNOWN quando efeito externo é incerto |
| autenticação | categoria explícita |
| produto removido | indisponibilidade distinguida de evidência desconhecida |
| seller/delivery ausentes | fail closed |
| HTML inesperado | erro de estrutura/fonte |
| falha de banco | falha operacional explícita/fail closed |
| falha de fila | estado persistente permite recovery |
| falha do provider | transitória, permanente ou DELIVERY_UNKNOWN |
| mensagem duplicada | idempotência persistente |
| estado inconsistente | fail closed + recovery/reconciliation |
| worker morto | lease recovery |
| tentativa STARTED abandonada | DELIVERY_UNKNOWN |
| job esgotado | DEAD |
| reprocessamento | decisão manual auditável e idempotente |
| restart | recovery antes do runtime |
| backlog maior que batch | drain até quiescência |

---

## 15. Commits da FASE 20

```text
acba934 test: validate destructive phase 20 recovery scenarios
16b5c74 fix: drain startup recovery backlog before runtime
0591860 feat: propagate provider rate limits across publication workers
7996af4 feat: honor provider retry floors for publication
6bd1bf3 feat: preserve publication retry after metadata
9aaab44 feat: compose ambiguous publication delivery resolution
ab45a99 feat: resolve ambiguous publication deliveries atomically
29a194f feat: define ambiguous publication delivery resolution
002f9ec feat: compose controlled processing job reprocessing
8392eb7 feat: reprocess dead processing jobs atomically
875084f feat: define controlled processing job reprocessing
28d0fa0 feat: recover durable work before continuous startup
df46c51 feat: support publication dispatch in continuous composition
d6c2621 feat: define continuous startup recovery
f12d6c7 feat: classify ambiguous channel outcomes as unknown
f63f56e feat: model unknown publication delivery result
ffa983b feat: recover ambiguous publication deliveries safely
f3bbbf4 feat: persist publication attempt before provider call
86992ba feat: complete durable publication attempts
5dc140c feat: add durable publication attempt start barrier
b75f30e feat: define unknown publication delivery persistence
6d23434 feat: distinguish unavailable Amazon products from unknown evidence
6d949df feat: fail closed on invalid Amazon product pages
32c14c9 feat: use rendered product pages in continuous runtime
c7ce687 feat: preserve product page acquisition failure semantics
b748e85 feat: introduce operational failure taxonomy
e19515c docs: define resilience and recovery semantics for phase 20
```

---

## 16. Migrations introduzidas/confirmadas pela FASE 20

A FASE 20 terminou com schema:

```text
V37
```

Entre as evoluções relevantes da fase estão estruturas para:

- resultado ambíguo de publicação;
- auditoria de reprocessamento de `ProcessingJob`;
- auditoria de resolução posterior de `DELIVERY_UNKNOWN`.

A última migration é:

```text
V37__publication_delivery_resolution_audit.sql
```

As migrations anteriores não foram editadas retroativamente.

---

## 17. O que NÃO foi introduzido

Para preservar escopo e evitar complexidade sem evidência:

```text
Circuit breaker:
NÃO

Kafka:
NÃO

RabbitMQ:
NÃO

Fila externa:
NÃO

Retry infinito:
NÃO

Retry cego de DELIVERY_UNKNOWN:
NÃO

Transação JDBC aberta durante HTTP externo:
NÃO

Reescrita retroativa de publication_attempt:
NÃO

Novo daemon:
NÃO

Push automático:
NÃO
```

---

## 18. Critério de conclusão

| Critério | Resultado |
|---|---|
| taxonomia operacional de falhas | CONCLUÍDO |
| fail closed | CONCLUÍDO |
| Amazon source failures | CONCLUÍDO |
| classificação de provider | CONCLUÍDO |
| start barrier antes do provider | CONCLUÍDO |
| DELIVERY_UNKNOWN | CONCLUÍDO |
| restart recovery | CONCLUÍDO |
| recovery antes do runtime | CONCLUÍDO |
| drain de backlog paginado | CONCLUÍDO |
| DEAD como dead-letter lógico | CONCLUÍDO |
| reprocessamento controlado | CONCLUÍDO |
| requestKey idempotente | CONCLUÍDO |
| resolução de entrega ambígua | CONCLUÍDO |
| Retry-After | CONCLUÍDO |
| rate limit persistente compartilhado | CONCLUÍDO |
| testes destrutivos | CONCLUÍDO |
| suite completa | 1786 / PASSOU |
| failures | 0 |
| errors | 0 |
| skipped | 0 |
| external-probe | PASSOU |
| working tree | CLEAN |
| schema | V37 |
| circuit breaker | NÃO NECESSÁRIO / NÃO INTRODUZIDO |
| daemon | NÃO EXECUTADO |
| push | PENDENTE |

---

## 19. Gate local da FASE 20

```text
FASE 20 — TRATAMENTO DE ERROS, RESILIÊNCIA E RECUPERAÇÃO

GATE LOCAL:
FECHADO

HEAD:
acba934

TESTES:
1786

FAILURES:
0

ERRORS:
0

SKIPPED:
0

BUILD:
SUCCESS

EXTERNAL PROBE:
SUCCESS

POSTGRESQL:
OK

FLYWAY:
OK

SCHEMA:
37

WORKING TREE:
CLEAN

TESTES DESTRUTIVOS:
6 / PASSOU

AVISO SUREFIRE:
OBSERVADO NA SUÍTE COMPLETA
NÃO REPRODUZIDO NO GATE DESTRUTIVO ISOLADO
THREAD DUMP SEM THREAD DA APLICAÇÃO PRESA
NÃO BLOQUEANTE

STATUS REMOTO:
PENDENTE
```

---

## 20. Próximo gate

Antes de iniciar a FASE 21:

```text
1. versionar este resultado;
2. confirmar working tree limpa;
3. push da branch;
4. executar CI remoto;
5. confirmar CI verde;
6. somente então fechar o gate remoto da FASE 20.
```

O daemon de produção não deve ser iniciado como parte deste encerramento.

---

## 21. Próxima fase

Após o gate remoto:

```text
FASE 21 — Segurança e governança operacional
```

A FASE 21 deverá tratar responsabilidades próprias de segurança e governança sem reabrir as decisões de resiliência já consolidadas nesta fase.
