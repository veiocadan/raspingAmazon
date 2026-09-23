# FASE 12 — ORQUESTRAÇÃO ASSÍNCRONA E PROCESSAMENTO DURÁVEL

## 1. Objetivo da fase

A FASE 12 teve como objetivo remover a dependência de uma única execução síncrona longa para o processamento completo de ofertas.

Até o encerramento da FASE 11, coleta, parsing, enriquecimento, avaliação e persistência já possuíam responsabilidades bem definidas. Faltava uma camada operacional capaz de:

- separar as etapas em unidades de trabalho independentes;
- persistir o estado da execução;
- repetir somente a etapa que falhou;
- evitar repetição desnecessária de chamadas externas;
- diferenciar falhas transitórias de falhas permanentes;
- suportar múltiplos workers;
- recuperar trabalhos abandonados;
- garantir idempotência por etapa;
- preservar segurança em reprocessamentos e concorrência.

A FASE 12 implementou essa camada.

Fluxo resultante:

```text
ProcessingRun
      ↓
COLLECT_DEALS
      ↓
DealCandidate
      ↓
ENRICH_DEAL
      ↓
OfferSnapshot
      ↓
EVALUATE_DEAL
      ↓
DealEvaluation
```

Cada transição relevante passa a possuir estado persistido e pode ser executada independentemente.

---

## 2. Resultado geral

Ao final da FASE 12, o projeto possui uma infraestrutura de processamento assíncrono durável baseada em PostgreSQL.

Foram implementados:

- `ProcessingRun`;
- `DealCandidate`;
- `ProcessingJob`;
- fila durável em PostgreSQL;
- claim concorrente com `FOR UPDATE SKIP LOCKED`;
- idempotência na criação de jobs;
- caso de uso isolado de coleta;
- caso de uso isolado de enriquecimento;
- caso de uso isolado de avaliação;
- classificação de falhas;
- política de retry com backoff;
- estados `RETRY_WAIT` e `DEAD`;
- leases de processamento;
- recuperação de leases expirados;
- política de duração de lease;
- worker genérico;
- dispatcher por tipo de job;
- testes de integração com PostgreSQL;
- testes de concorrência;
- reforço da idempotência de candidatos;
- serialização de avaliações concorrentes do mesmo snapshot;
- preparação explícita do schema no CI antes da suíte JDBC.

Baseline final:

```text
Tests run: 514
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

Estado formal:

```text
gate local  = FECHADO
gate remoto = FECHADO
FASE 12     = CONCLUÍDA
```

---

## 3. Arquitetura adotada

### 3.1 PostgreSQL como fila durável

A FASE 12 não introduziu RabbitMQ, Kafka, SQS ou outro broker externo.

O PostgreSQL já fazia parte da infraestrutura operacional do projeto e passou também a armazenar os jobs da orquestração.

A aplicação permanece desacoplada dessa implementação por meio de porta:

```text
Application
    ↓
ProcessingJobQueuePort
    ↓
Infrastructure
    ↓
JdbcProcessingJobQueueAdapter
    ↓
PostgreSQL
```

Essa decisão preserva a possibilidade de substituir a implementação da fila futuramente, caso volume e métricas demonstrem necessidade real.

A fase não antecipou infraestrutura distribuída sem evidência operacional.

---

### 3.2 Tipos de job

Os tipos reconhecidos são:

```text
COLLECT_DEALS
ENRICH_DEAL
EVALUATE_DEAL
```

Cada tipo aponta para um único sujeito:

```text
COLLECT_DEALS
    → processing_run_id

ENRICH_DEAL
    → deal_candidate_id

EVALUATE_DEAL
    → offer_snapshot_id
```

O schema valida essa relação.

Publicação não faz parte da FASE 12.

---

## 4. Persistência da orquestração

### 4.1 `processing_run`

`processing_run` representa uma execução lógica de coleta.

Estados:

```text
PENDING
RUNNING
COMPLETED
FAILED
```

Cada run possui uma `run_key` idempotente.

Uma run concluída pode ser reconhecida em reentrada, evitando nova coleta quando o commit já havia ocorrido antes de uma interrupção do worker.

---

### 4.2 `deal_candidate`

`deal_candidate` preserva de forma durável o resultado do parsing.

Isso permite:

```text
coleta concluída
      ↓
candidate persistido
      ↓
falha de enrichment
      ↓
retry de enrichment
```

sem repetir:

```text
HTTP de coleta
+
parsing
```

Durante a auditoria final da fase, a identidade idempotente foi fortalecida para:

```text
processing_run_id
+
asin
+
source
```

`collected_at` continua sendo fato da observação, mas não participa da identidade idempotente.

Isso impede que uma reentrada lógica da mesma `ProcessingRun` produza outro candidato somente porque o timestamp mudou.

---

### 4.3 `processing_job`

`processing_job` representa a fila persistente.

Campos operacionais principais:

```text
job_type
status

processing_run_id
deal_candidate_id
offer_snapshot_id

idempotency_key

attempt_count
max_attempts

available_at

locked_at
locked_by

last_failure_type
last_error_code
last_error_message

created_at
updated_at
finished_at
```

Estados:

```text
PENDING
RUNNING
RETRY_WAIT
SUCCEEDED
DEAD
```

---

## 5. Idempotência dos jobs

A identidade de criação de trabalho é protegida por:

```text
job_type + idempotency_key
```

A criação possui comportamento idempotente no PostgreSQL.

Exemplos:

```text
enrich:<dealCandidateId>
evaluate:<offerSnapshotId>
```

A proteção da fila atua em conjunto com a idempotência de cada estágio.

---

## 6. Claim concorrente

O claim utiliza:

```sql
FOR UPDATE SKIP LOCKED
```

e realiza de forma atômica:

```text
PENDING / RETRY_WAIT
        ↓
RUNNING
        ↓
attempt_count + 1
        ↓
locked_at
locked_by
```

O critério de seleção utiliza:

```text
available_at
id
```

permitindo priorizar trabalhos já disponíveis há mais tempo.

O uso de `SKIP LOCKED` prepara múltiplos workers para consumir a mesma fila sem distribuição simultânea da mesma unidade de trabalho.

---

## 7. Caso de uso `COLLECT_DEALS`

Foi criado um caso de uso dedicado à coleta.

Responsabilidades:

1. carregar a `ProcessingRun`;
2. verificar o estado idempotente da etapa;
3. colocar a run em `RUNNING`;
4. executar coleta externa;
5. executar parsing;
6. persistir `DealCandidate`;
7. criar jobs `ENRICH_DEAL`;
8. marcar a run como `COMPLETED`.

A chamada externa e o parsing permanecem fora de uma transação JDBC longa.

A persistência de:

```text
DealCandidate
+
ENRICH_DEAL
+
ProcessingRun COMPLETED
```

ocorre dentro da fronteira transacional.

---

### 7.1 Reentrada da coleta

Quando a run já está:

```text
COMPLETED
```

o caso de uso retorna sem repetir coleta.

Cenário protegido:

```text
commit da coleta
      ↓
worker cai antes do ACK
      ↓
job volta a executar
      ↓
ProcessingRun já COMPLETED
      ↓
nenhuma nova chamada externa
```

Runs em `RUNNING` também podem ser retomadas para cobrir interrupções entre a mudança de estado e a conclusão.

---

## 8. Caso de uso `ENRICH_DEAL`

O enrichment passou a possuir caso de uso próprio.

Fluxo:

```text
DealCandidate
      ↓
verifica snapshot existente
      ↓
enrichment externo
      ↓
Product
      ↓
OfferSnapshot
      ↓
PaymentConditions
      ↓
OfferEvidence
      ↓
EVALUATE_DEAL
```

A etapa não executa avaliação diretamente.

Ela deixa o estágio seguinte disponível na fila.

---

### 8.1 Reentrada do enrichment

Antes de executar nova chamada externa, o caso de uso verifica se o candidato já possui snapshot persistido.

Quando já existe:

```text
snapshot encontrado
      ↓
não chama Amazon novamente
      ↓
garante EVALUATE_DEAL
      ↓
retorna
```

Isso cobre o cenário em que o enrichment foi commitado e o worker caiu antes do ACK.

---

## 9. Caso de uso `EVALUATE_DEAL`

A avaliação foi separada em caso de uso próprio.

Essa etapa utiliza apenas dados persistidos e não realiza chamada externa à Amazon.

Responsabilidades:

```text
OfferSnapshot
      ↓
eligibility
      ↓
filters
      ↓
commercial rules
      ↓
score
      ↓
momentum
      ↓
DealEvaluation
      ↓
MomentumAudit
```

---

### 9.1 Idempotência da avaliação

Antes de recalcular uma oferta, o caso de uso verifica se já existe `DealEvaluation` para o snapshot.

Quando existe:

```text
DealEvaluation encontrada
      ↓
retorno imediato
```

Isso cobre:

```text
avaliação commitada
      ↓
worker cai antes do ACK
      ↓
job volta a executar
      ↓
avaliação já existe
      ↓
nenhum recálculo
```

---

### 9.2 Concorrência da avaliação

Na auditoria técnica final foi identificada uma janela residual:

```text
worker A → lookup vazio
worker B → lookup vazio
```

Sem serialização adicional, ambos poderiam tentar persistir avaliação para o mesmo snapshot.

Embora o schema já possuísse unicidade por `offer_snapshot`, o worker perdedor poderia receber erro de persistência.

A correção utiliza lock transacional:

```text
transaction
      ↓
SELECT offer_snapshot
FOR UPDATE
      ↓
lookup DealEvaluation
      ↓
já existe?
   ├─ sim → retorna
   └─ não → evaluateAndPersist
```

Com isso:

```text
worker A
   ↓
lock
   ↓
persiste
   ↓
commit

worker B
   ↓
aguarda
   ↓
adquire lock
   ↓
lookup encontra avaliação
   ↓
retorna
```

A constraint única permanece como defesa adicional do banco.

---

## 10. Classificação de falhas

Foi criado mecanismo explícito de classificação:

```text
TRANSIENT
PERMANENT
```

Exemplos transitórios:

- timeout;
- erro de conexão;
- HTTP 408;
- HTTP 425;
- HTTP 429;
- HTTP 5xx;
- falhas SQL transitórias;
- falhas SQL recuperáveis.

Exemplos permanentes:

- entrada inválida;
- estado inválido;
- HTTP 4xx não temporário;
- falha desconhecida sem evidência de transitoriedade.

Falhas desconhecidas não entram automaticamente em retry.

A política evita loops indefinidos sobre erros determinísticos.

---

## 11. Política de retry

Falhas transitórias podem ser reagendadas quando:

```text
attempt_count < max_attempts
```

A transição é:

```text
RUNNING
   ↓
RETRY_WAIT
```

com novo:

```text
available_at
```

e remoção do lock do worker.

O job volta a ser elegível somente após o instante calculado.

---

### 11.1 Backoff

Foi implementado backoff exponencial limitado.

Conceitualmente:

```text
attempt 1 → atraso base
attempt 2 → atraso maior
attempt 3 → atraso maior
...
```

até o limite configurado.

A fila recebe o novo `available_at`; ela não conhece a política de cálculo do atraso.

---

## 12. Falhas permanentes e esgotamento

Falha permanente:

```text
RUNNING
   ↓
DEAD
```

Falha transitória sem tentativas restantes:

```text
RUNNING
   ↓
DEAD
```

A causa fica registrada em:

```text
last_failure_type
last_error_code
last_error_message
```

e o instante terminal em:

```text
finished_at
```

---

## 13. Lease de worker

Ao reivindicar um job, o worker grava:

```text
locked_at
locked_by
```

Esse par representa ownership operacional.

As operações de finalização verificam ownership antes de modificar o job.

---

## 14. Recuperação de leases expirados

Jobs que ficaram `RUNNING` depois da interrupção do worker podem ser recuperados.

Fluxo:

```text
worker A
   ↓
claim
   ↓
RUNNING
   ↓
processo morre
   ↓
lease expira
   ↓
recovery
```

O recovery processa em lote com `FOR UPDATE SKIP LOCKED`.

Resultado:

```text
possui tentativa restante
      ↓
RETRY_WAIT

tentativas esgotadas
      ↓
DEAD
```

A recuperação não incrementa `attempt_count`, pois a tentativa foi contabilizada no claim.

---

## 15. Política de duração de lease

O cálculo de expiração foi encapsulado em serviço de aplicação.

Conceito:

```text
leaseExpiredBefore
    =
now - leaseDuration
```

Dependências da política:

```text
leaseDuration
batchSize
Clock
```

O adapter JDBC permanece responsável por persistência e seleção das linhas elegíveis.

---

## 16. Worker

Foi implementado `ProcessingWorker`.

Uma rodada executa:

```text
claimNext()
      ↓
job encontrado?
   ├─ não → idle
   └─ sim
        ↓
      execute
        ↓
   ┌────┴────┐
sucesso    falha
   ↓          ↓
SUCCEEDED   failure handler
              ├─ RETRY_WAIT
              └─ DEAD
```

O worker não contém:

- scheduler;
- loop infinito;
- `sleep`;
- publicação.

Essas responsabilidades pertencem a fases posteriores.

---

## 17. Dispatcher

O dispatcher associa tipo do job ao sujeito correto:

```text
COLLECT_DEALS
    → processingRunId

ENRICH_DEAL
    → dealCandidateId

EVALUATE_DEAL
    → offerSnapshotId
```

A composição pode associar esses handlers aos casos de uso correspondentes.

O dispatcher utiliza handlers funcionais, mantendo o componente simples e testável.

---

## 18. ACK após processamento

`markSucceeded()` permanece fora do bloco que captura falha funcional do processamento.

Isso evita transformar erro de ownership/ACK em retry indevido da regra de negócio.

Exemplo evitado:

```text
regra terminou
      ↓
ownership expirou
      ↓
ACK falha
      ↓
erro tratado como falha funcional
      ↓
retry incorreto
```

---

## 19. Múltiplos workers

A combinação:

```text
fila durável
+
FOR UPDATE SKIP LOCKED
+
locked_by
+
locked_at
+
lease recovery
+
idempotência
```

prepara execução horizontal.

Foram adicionados testes com conexões JDBC independentes.

A FASE 12 prepara o consumo por múltiplos workers; scheduler e composição contínua pertencem a fases posteriores.

---

## 20. Reprocessamento seletivo

A consequência central da FASE 12 é que uma falha posterior não obriga reiniciar todo o pipeline.

Exemplo:

```text
COLLECT_DEALS  SUCCEEDED
ENRICH_DEAL    falhou
```

Retry:

```text
ENRICH_DEAL
```

e não:

```text
COLLECT_DEALS
+
ENRICH_DEAL
```

Outro cenário:

```text
COLLECT_DEALS  SUCCEEDED
ENRICH_DEAL    SUCCEEDED
EVALUATE_DEAL  falhou
```

Retry:

```text
EVALUATE_DEAL
```

sem repetir coleta, parsing ou enrichment externo já concluídos.

---

## 21. Transações

A camada de aplicação utiliza:

```text
TransactionPort
```

e não conhece diretamente:

```text
Connection
commit
rollback
JDBC
```

Unidades relacionadas permanecem atômicas.

Coleta:

```text
DealCandidate
+
ENRICH_DEAL
+
ProcessingRun COMPLETED
```

Enrichment:

```text
Product
+
OfferSnapshot
+
PaymentConditions
+
OfferEvidence
+
EVALUATE_DEAL
```

Avaliação:

```text
lock OfferSnapshot
+
verificação idempotente
+
DealEvaluation
+
MomentumAudit
```

---

## 22. Migrations da FASE 12

### V11 — `processing_orchestration`

Fundação da orquestração:

```text
processing_run
deal_candidate
processing_job
```

Inclui constraints, índices, idempotência, claim e suporte a lease.

### V12 — `evaluation_processing_idempotency`

Reforça a idempotência da avaliação e a relação entre avaliação e snapshot.

A unicidade no banco permanece defesa final.

### V13 — `deal_candidate_idempotency`

Fortalece a identidade de candidato de:

```text
processing_run_id
+
asin
+
collected_at
+
source
```

para:

```text
processing_run_id
+
asin
+
source
```

A migration não remove silenciosamente registros incompatíveis.

---

## 23. Auditoria técnica final

Antes da documentação final, foram revisados dois riscos específicos.

### 23.1 Duplicação de candidatos

Risco:

```text
mesma run
+
mesmo ASIN
+
mesma source
+
collected_at diferente
```

poderia gerar outro `DealCandidate`.

Consequência:

```text
novo candidateId
      ↓
novo ENRICH_DEAL
      ↓
trabalho duplicado
```

Correção:

```text
UNIQUE (
    processing_run_id,
    asin,
    source
)
```

Commit:

```text
30b8e93 fix: fortalece idempotencia de candidatos
```

---

### 23.2 Avaliações concorrentes

Risco:

```text
worker A → lookup vazio
worker B → lookup vazio
```

Correção:

```text
SELECT offer_snapshot
FOR UPDATE
```

antes do segundo lookup transacional.

Commit:

```text
2a95e75 fix: serializa avaliacoes concorrentes
```

---

## 24. Estratégia de testes

A FASE 12 adicionou testes unitários, JDBC e integrados.

Cobertura inclui:

- contratos de orquestração;
- persistência de runs;
- persistência e idempotência de candidates;
- fila JDBC;
- enqueue idempotente;
- claim;
- success;
- retry;
- dead;
- classificação de falhas;
- backoff;
- failure handler;
- leases;
- lease recovery;
- política de expiração;
- worker;
- dispatcher;
- carga de snapshot para avaliação;
- lookup de avaliação;
- lock de snapshot;
- concorrência;
- reprocessamento.

Baseline final:

```text
514 testes
0 falhas
0 erros
0 ignorados
```

---

## 25. Ajuste do CI revelado pela FASE 12

O primeiro CI remoto do Pull Request revelou uma fragilidade da preparação do ambiente.

O PostgreSQL do GitHub Actions iniciava vazio e o workflow executava diretamente:

```text
./mvnw --batch-mode clean test
```

Os testes JDBC da FASE 12 passaram a depender de tabelas como:

```text
processing_run
processing_job
deal_candidate
```

e alguns deles podiam executar antes de `DatabaseMigrationTest`.

O problema não estava na regra da FASE 12, mas na dependência implícita da ordem dos testes para preparar o schema.

A correção tornou a preparação explícita:

```text
./mvnw --batch-mode -Dtest=DatabaseMigrationTest test
        ↓
./mvnw --batch-mode test
```

Resultado:

- Flyway aplica/valida migrations antes da suíte JDBC;
- o CI não depende da ordem escolhida pelo Surefire/JUnit;
- o PostgreSQL limpo do runner reproduz o schema esperado;
- a suíte completa continua com 514 testes.

Commit corretivo:

```text
549144f ci: aplica migrations antes dos testes
```

Após esse commit, o CI do Pull Request ficou verde.

---

## 26. Commits principais da FASE 12

```text
05a3f9f feat: adiciona persistencia da orquestracao
cab3194 feat: define contratos da orquestracao assincrona
90d8637 feat: implementa fila JDBC de processamento
47dbb30 feat: implementa fila JDBC de processamento1
b9227d4 feat: persiste runs e candidatos de processamento
45f41f5 feat: separa caso de uso de coleta assincrona
2f057b3 feat: separa caso de uso de enriquecimento assincrono
50e5d1f feat: prepara avaliacao assincrona idempotente
c4f0523 feat: separa caso de uso de avaliacao assincrona
3c7e401 feat: define politica de falhas e retry
848cf46 feat: aplica politica de falhas aos jobs
9a3e957 feat: recupera leases expirados de jobs
1003975 feat: define politica de expiracao de leases
ba25738 feat: implementa worker de processamento
e791531 test: integra orquestracao assincrona
30b8e93 fix: fortalece idempotencia de candidatos
2a95e75 fix: serializa avaliacoes concorrentes
b195716 docs: finaliza fase 12 e atualiza roadmap
549144f ci: aplica migrations antes dos testes
```

Merge remoto:

```text
cb220c7 Merge pull request #3 from veiocadan/fase-12-orquestracao-assincrona
```

---

## 27. Critérios de aceite

### Enrichment falha sem repetir coleta

Atendido.

O parsing é persistido como `DealCandidate`, permitindo retry do enrichment sem repetir coleta.

### Estágios reexecutáveis com segurança

Atendido.

Existem mecanismos idempotentes em run, candidate, job, enrichment e avaliação.

### Evitar chamadas externas desnecessárias

Atendido.

Snapshot já persistido evita novo enrichment externo após conclusão anterior.

### Diferenciar falha transitória e permanente

Atendido.

Classificador explícito com comportamento distinto.

### Retry controlado

Atendido.

Existem:

```text
attempt_count
max_attempts
available_at
RETRY_WAIT
backoff
```

### Preparar múltiplos workers

Atendido.

A fila usa `FOR UPDATE SKIP LOCKED`, ownership e lease recovery.

### Evitar duplicidade nos pontos críticos

Atendido através de:

- idempotency key dos jobs;
- identidade forte de candidate;
- constraints;
- locks;
- serialização da avaliação.

---

## 28. Fora do escopo da FASE 12

Não foram implementados nesta fase:

- geração definitiva de publicação;
- link de associado;
- interface operacional;
- Telegram;
- WhatsApp;
- outbox de canais;
- scheduler definitivo;
- cron operacional;
- execução contínua automática;
- observabilidade completa;
- dashboards;
- tracing distribuído;
- broker externo dedicado.

Esses itens permanecem para as fases posteriores definidas no roadmap v1.

---

## 29. Gate local

Resultado final local:

```text
Tests run: 514
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

Schema:

```text
PostgreSQL: 18.6
Flyway: OK
Migrations: 13
Schema: versão 13
```

Estado:

```text
gate local = FECHADO
```

---

## 30. Gate remoto

Pull Request:

```text
#3 — FASE 12 — Orquestração e processamento assíncrono
```

Resultado do CI do PR:

```text
SUCCESS
```

Merge:

```text
cb220c7
Merge pull request #3 from veiocadan/fase-12-orquestracao-assincrona
```

CI pós-merge:

```text
GitHub Actions
workflow: CI
event: push
branch: main
run: #25
conclusion: SUCCESS
```

Estado:

```text
gate remoto = FECHADO
```

---

## 31. Estado arquitetural ao final da fase

```text
                ┌──────────────────────┐
                │   processing_run     │
                └──────────┬───────────┘
                           │
                           ▼
                  ┌────────────────┐
                  │ COLLECT_DEALS  │
                  └───────┬────────┘
                          │
                          ▼
                 ┌─────────────────┐
                 │ deal_candidate  │
                 └───────┬─────────┘
                         │
                         ▼
                  ┌───────────────┐
                  │ ENRICH_DEAL   │
                  └───────┬───────┘
                          │
                          ▼
                 ┌─────────────────┐
                 │ offer_snapshot  │
                 └───────┬─────────┘
                         │
                         ▼
                  ┌───────────────┐
                  │ EVALUATE_DEAL │
                  └───────┬───────┘
                          │
                          ▼
                ┌──────────────────┐
                │ deal_evaluation  │
                └──────────────────┘
```

A coordenação durável é representada por:

```text
processing_job
```

e a execução de uma unidade é realizada por:

```text
ProcessingWorker
```

---

## 32. Conclusão

A FASE 12 removeu da arquitetura a necessidade de tratar o pipeline completo como uma única execução indivisível.

A aplicação agora possui unidades de trabalho persistentes, independentes e reexecutáveis.

Uma falha em uma etapa não obriga repetir etapas anteriores já concluídas.

A combinação de:

```text
PostgreSQL durable queue
+
idempotência
+
transações
+
retry
+
backoff
+
leases
+
lease recovery
+
FOR UPDATE SKIP LOCKED
+
worker
+
locks transacionais
+
constraints
```

estabelece a base operacional para as próximas fases.

Fechamento formal:

```text
514 testes verdes
PR #3 MERGED
merge commit cb220c7
CI do PR SUCCESS
CI da main SUCCESS — run #25
gate local FECHADO
gate remoto FECHADO
FASE 12 CONCLUÍDA
```

---

## 33. Próxima fase

O planejamento das FASES 13 a 21 está definido em:

```text
docs/phases/ROADMAP_RASPING_AMAZON_V1.md
```

A próxima fase é:

```text
FASE 13 — Geração de publicação e link de associado
```

Objetivo central:

```text
DealEvaluation
      ↓
PublicationGenerator
      ↓
Publication persistida
```

A FASE 13 deverá consolidar geração reproduzível de publicação, template versionado e link de associado encapsulado, mantendo geração de conteúdo separada da entrega em canais.

A interface operacional permanece planejada para a FASE 14.
