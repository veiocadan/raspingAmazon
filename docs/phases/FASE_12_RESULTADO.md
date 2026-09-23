# FASE 12 — ORQUESTRAÇÃO ASSÍNCRONA E PROCESSAMENTO DURÁVEL

## 1. Objetivo da fase

A FASE 12 teve como objetivo remover a dependência de uma única execução síncrona longa para o processamento completo de ofertas.

Até o encerramento da FASE 11, coleta, parsing, enriquecimento, avaliação e persistência já possuíam responsabilidades bem definidas, porém ainda faltava uma camada operacional capaz de:

- separar as etapas em unidades de trabalho independentes;
- persistir o estado da execução;
- repetir somente a etapa que falhou;
- evitar repetição desnecessária de chamadas externas;
- diferenciar falhas transitórias de falhas permanentes;
- suportar múltiplos workers;
- recuperar trabalhos abandonados por workers interrompidos;
- garantir idempotência por etapa;
- preservar segurança em reprocessamentos e concorrência.

A FASE 12 implementou essa camada.

O fluxo assíncrono resultante é:

```text
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

Cada transição é persistida e pode ser executada independentemente.

---

## 2. Resultado geral

Ao final da FASE 12, o projeto passou a possuir uma infraestrutura de processamento assíncrono durável baseada em PostgreSQL.

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
- transição para `RETRY_WAIT`;
- transição para `DEAD`;
- leases de processamento;
- recuperação de leases expirados;
- política de duração de lease;
- worker genérico;
- dispatcher por tipo de job;
- testes de integração com PostgreSQL;
- testes com múltiplos workers;
- reforço final da idempotência de candidatos;
- serialização de avaliações concorrentes do mesmo snapshot.

A baseline final da fase é:

```text
Tests run: 514
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

---

# 3. Arquitetura adotada

## 3.1 PostgreSQL como fila durável

A FASE 12 não introduziu RabbitMQ, Kafka, SQS ou outro broker externo.

O PostgreSQL já fazia parte da infraestrutura operacional do projeto e passou também a armazenar os jobs da orquestração.

A aplicação continua desacoplada desse detalhe por meio de portas.

A arquitetura é:

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

Essa decisão permite substituir a implementação da fila futuramente sem alterar os casos de uso da aplicação.

---

## 3.2 Modelo de processamento

Os jobs reconhecidos nesta fase são:

```text
COLLECT_DEALS
ENRICH_DEAL
EVALUATE_DEAL
```

Publicação não faz parte da FASE 12.

Cada tipo aponta para um único sujeito:

```text
COLLECT_DEALS
    → processing_run_id

ENRICH_DEAL
    → deal_candidate_id

EVALUATE_DEAL
    → offer_snapshot_id
```

O próprio schema valida essa relação.

---

# 4. Persistência da orquestração

## 4.1 `processing_run`

`processing_run` representa uma execução lógica de coleta.

Estados suportados:

```text
PENDING
RUNNING
COMPLETED
FAILED
```

Cada run possui uma `run_key` idempotente.

Uma run concluída não precisa executar novamente coleta ou parsing caso o worker tenha sido interrompido somente depois do commit.

---

## 4.2 `deal_candidate`

`deal_candidate` preserva de forma durável o resultado do parsing.

Sua existência é fundamental para permitir:

```text
coleta concluída
      ↓
candidate persistido
      ↓
falha de enrichment
      ↓
retry de enrichment
```

sem executar novamente:

```text
HTTP de coleta
+
parsing
```

Ao final da revisão técnica da fase, a identidade idempotente do candidato foi fortalecida para:

```text
processing_run_id
+
asin
+
source
```

`collected_at` permanece armazenado como fato da observação, mas deixou de fazer parte da identidade idempotente.

Isso evita que uma reentrada da mesma `ProcessingRun` gere outro candidato apenas porque o timestamp da coleta mudou.

---

## 4.3 `processing_job`

`processing_job` representa a fila persistente.

Principais atributos:

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

Estados suportados:

```text
PENDING
RUNNING
RETRY_WAIT
SUCCEEDED
DEAD
```

---

# 5. Idempotência dos jobs

A identidade de criação de trabalho é protegida por:

```text
job_type + idempotency_key
```

A criação utiliza comportamento idempotente no PostgreSQL.

Assim, solicitar novamente o mesmo trabalho lógico não gera uma nova unidade de processamento.

Exemplos:

```text
enrich:<dealCandidateId>
evaluate:<offerSnapshotId>
```

A idempotência da fila funciona em conjunto com a idempotência de cada estágio.

---

# 6. Claim concorrente

O claim utiliza PostgreSQL:

```sql
FOR UPDATE SKIP LOCKED
```

e faz, de forma atômica:

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

Isso permite que diferentes workers consultem a mesma fila sem receber simultaneamente o mesmo job.

O critério de ordenação utiliza:

```text
available_at
id
```

permitindo priorizar trabalhos já disponíveis há mais tempo.

---

# 7. Caso de uso `COLLECT_DEALS`

Foi criado um caso de uso dedicado à etapa de coleta.

Responsabilidades:

1. carregar a `ProcessingRun`;
2. verificar idempotência da etapa;
3. colocar a run em `RUNNING`;
4. executar coleta externa;
5. executar parsing;
6. persistir `DealCandidate`;
7. criar jobs `ENRICH_DEAL`;
8. marcar a run como `COMPLETED`.

A chamada externa e o parsing permanecem fora da transação JDBC longa.

A persistência de:

```text
DealCandidate
+
ENRICH_DEAL
+
ProcessingRun COMPLETED
```

é feita dentro da fronteira transacional adequada.

---

## 7.1 Reentrada da coleta

Quando uma run já está:

```text
COMPLETED
```

o caso de uso retorna imediatamente.

Esse comportamento cobre o cenário:

```text
transação da coleta commitou
        ↓
JVM caiu
        ↓
job não recebeu ACK
        ↓
job é executado novamente
        ↓
run já COMPLETED
        ↓
nenhuma nova chamada externa
```

Runs em `RUNNING` também podem ser retomadas para cobrir interrupções ocorridas depois da mudança de estado e antes da conclusão.

---

# 8. Caso de uso `ENRICH_DEAL`

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

Ela apenas deixa o trabalho seguinte disponível na fila.

---

## 8.1 Reentrada do enrichment

Antes de executar nova chamada externa, o caso de uso verifica se já existe snapshot persistido para o candidato.

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

Isso cobre o cenário em que o enrichment foi commitado, mas o worker caiu antes do ACK do job.

---

# 9. Caso de uso `EVALUATE_DEAL`

A avaliação foi separada em um caso de uso próprio.

Essa etapa utiliza somente dados já persistidos.

Ela não executa chamada externa à Amazon.

Responsabilidades:

```text
OfferSnapshot persistido
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

## 9.1 Idempotência da avaliação

Antes de calcular novamente uma oferta, o caso de uso verifica se já existe `DealEvaluation` para o snapshot.

Quando já existe:

```text
DealEvaluation encontrada
      ↓
retorno imediato
```

Esse comportamento cobre:

```text
avaliação commitou
      ↓
worker caiu antes do ACK
      ↓
job é executado novamente
      ↓
avaliação já existe
      ↓
nenhum recálculo
```

---

## 9.2 Concorrência na avaliação

Durante a revisão final da FASE 12 foi identificada uma janela de concorrência:

```text
worker A → lookup vazio
worker B → lookup vazio
```

Se ambos prosseguissem simultaneamente, os dois poderiam tentar persistir uma avaliação para o mesmo snapshot.

Embora o banco já possuísse unicidade por snapshot, o worker perdedor receberia erro de persistência.

A correção implementada utiliza lock transacional:

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

Consequentemente:

```text
worker A
   ↓
lock
   ↓
persiste avaliação
   ↓
commit

worker B
   ↓
aguarda lock
   ↓
lookup após commit de A
   ↓
avaliação encontrada
   ↓
retorna com sucesso
```

A constraint única permanece como defesa adicional do schema.

---

# 10. Classificação de falhas

Foi criado um mecanismo explícito para separar:

```text
TRANSIENT
PERMANENT
```

Exemplos de falhas transitórias:

- timeout;
- erro de conexão;
- HTTP 408;
- HTTP 425;
- HTTP 429;
- HTTP 5xx;
- falhas SQL transitórias;
- falhas SQL recuperáveis.

Exemplos de falhas permanentes:

- entrada inválida;
- estado inválido;
- HTTP 4xx não classificado como temporário;
- falha desconhecida sem evidência de transitoriedade.

Falhas desconhecidas não são automaticamente repetidas.

A política é conservadora para evitar loops indefinidos sobre erros determinísticos.

---

# 11. Política de retry

Falhas transitórias podem ser reagendadas quando:

```text
attempt_count < max_attempts
```

O reagendamento produz:

```text
RUNNING
   ↓
RETRY_WAIT
```

e define novo:

```text
available_at
```

O lock do worker é removido.

O job volta a ser elegível somente após o horário calculado.

---

## 11.1 Backoff

Foi implementada política de backoff exponencial limitada.

Conceitualmente:

```text
attempt 1 → atraso base
attempt 2 → atraso maior
attempt 3 → atraso maior
...
```

até o limite configurado.

A fila não precisa conhecer a política de tempo.

Ela apenas recebe o novo `available_at`.

---

# 12. Falhas permanentes e esgotamento

Quando uma falha é permanente:

```text
RUNNING
   ↓
DEAD
```

Quando uma falha é transitória, mas o job não possui mais tentativas disponíveis:

```text
RUNNING
   ↓
DEAD
```

A causa permanece registrada através de:

```text
last_failure_type
last_error_code
last_error_message
```

e o instante terminal é registrado em:

```text
finished_at
```

---

# 13. Lease de worker

Ao reivindicar um job, o worker grava:

```text
locked_at
locked_by
```

Esse par representa o lease operacional.

Enquanto o job estiver `RUNNING`, outro worker não pode reivindicá-lo.

As operações de finalização verificam ownership antes de modificar o job.

---

# 14. Recuperação de leases expirados

Foi implementada recuperação de jobs que ficaram `RUNNING` após interrupção do worker.

Exemplo:

```text
worker A
   ↓
claim
   ↓
RUNNING
   ↓
processo morre
   ↓
nenhum ACK
   ↓
lease expira
   ↓
recovery
```

O recovery utiliza processamento em lote e `FOR UPDATE SKIP LOCKED`.

Jobs expirados são divididos entre:

```text
ainda possui tentativa
      ↓
RETRY_WAIT

tentativas esgotadas
      ↓
DEAD
```

A recuperação não incrementa `attempt_count`, pois a tentativa já foi contabilizada no momento do claim.

---

# 15. Política de duração de lease

O cálculo de expiração não ficou espalhado pelos workers.

Foi criado serviço de aplicação que encapsula:

```text
leaseExpiredBefore
    =
now - leaseDuration
```

Além disso, a política recebe:

```text
leaseDuration
batchSize
Clock
```

O adapter JDBC continua responsável apenas pela persistência e recuperação das linhas elegíveis.

---

# 16. Worker

Foi implementado `ProcessingWorker`.

Uma rodada executa:

```text
claimNext()
      ↓
job encontrado?
   ├─ não → IDLE
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

O worker processa uma unidade por chamada.

Ele não contém:

- scheduler;
- loop infinito;
- `sleep`;
- timer de aplicação;
- publicação.

Essas responsabilidades permanecem separadas.

---

# 17. Dispatcher

Foi criado um dispatcher entre tipo do job e caso de uso.

Mapeamento:

```text
COLLECT_DEALS
    → processingRunId
    → CollectDealsUseCase

ENRICH_DEAL
    → dealCandidateId
    → EnrichDealUseCase

EVALUATE_DEAL
    → offerSnapshotId
    → EvaluateDealUseCase
```

O dispatcher utiliza handlers funcionais e não depende diretamente das implementações concretas dos casos de uso.

Isso mantém o componente simples e testável sem necessidade de framework de mocks.

---

# 18. ACK após processamento

Uma decisão importante foi manter:

```text
markSucceeded()
```

fora do bloco que captura falha do processamento funcional.

Isso evita o seguinte erro:

```text
worker termina a regra
      ↓
perdeu ownership do lease
      ↓
markSucceeded falha
      ↓
erro interpretado como falha funcional
      ↓
retry incorreto
```

Falha no ACK não é transformada automaticamente em falha de negócio.

---

# 19. Múltiplos workers

A FASE 12 foi preparada para múltiplas instâncias de worker.

A combinação:

```text
fila durável
+
transação
+
FOR UPDATE SKIP LOCKED
+
locked_by
+
lease
+
recovery
```

permite escala horizontal sem distribuição simultânea do mesmo job.

Foram adicionados testes utilizando conexões JDBC independentes para verificar que diferentes workers conseguem reivindicar jobs diferentes.

---

# 20. Reprocessamento seletivo

A principal consequência arquitetural da FASE 12 é que uma falha posterior não obriga reiniciar toda a pipeline.

Exemplo:

```text
COLLECT_DEALS   SUCCEEDED
      ↓
ENRICH_DEAL     falhou
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

Da mesma forma:

```text
COLLECT_DEALS   SUCCEEDED
ENRICH_DEAL     SUCCEEDED
EVALUATE_DEAL   falhou
```

Retry:

```text
EVALUATE_DEAL
```

sem repetir:

```text
coleta
parsing
enrichment externo
```

---

# 21. Transações

As fronteiras transacionais foram preservadas.

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

Operações relacionadas permanecem atômicas.

Exemplo da coleta:

```text
DealCandidate
+
ENRICH_DEAL
+
ProcessingRun COMPLETED
```

Exemplo do enrichment:

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

Exemplo da avaliação:

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

# 22. Migrations relacionadas à FASE 12

A fase introduziu e utilizou migrations para suportar:

## V11

Fundação da orquestração:

```text
processing_run
deal_candidate
processing_job
```

Incluindo:

- constraints;
- índices;
- idempotência;
- suporte a claim;
- suporte a lease recovery.

---

## V12

Reforços necessários à avaliação idempotente.

Entre as proteções introduzidas está a unicidade da avaliação por `offer_snapshot`.

A migration mantém o banco como última linha de defesa contra duplicidade.

---

## V13

Reforço final da identidade idempotente de `deal_candidate`.

A identidade passou de:

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

A migration falha explicitamente caso encontre dados antigos incompatíveis com a nova constraint, em vez de remover ou escolher registros silenciosamente.

---

# 23. Auditoria técnica final

Antes de encerrar a fase, foram revisados dois riscos específicos.

## 23.1 Duplicação de candidatos

Problema identificado:

```text
mesma ProcessingRun
+
mesmo ASIN
+
mesma source
+
collected_at diferente
```

poderia resultar em outro `DealCandidate`.

Consequência potencial:

```text
novo candidateId
      ↓
novo ENRICH_DEAL
      ↓
trabalho externo duplicado
```

Correção:

```text
UNIQUE (
    processing_run_id,
    asin,
    source
)
```

---

## 23.2 Avaliações concorrentes

Problema identificado:

```text
worker A → lookup vazio
worker B → lookup vazio
```

ambos poderiam tentar persistir avaliação do mesmo snapshot.

Correção:

```text
SELECT offer_snapshot
FOR UPDATE
```

antes do segundo lookup transacional.

A segunda execução passa a esperar a primeira e termina idempotentemente quando encontra a avaliação já persistida.

---

# 24. Estratégia de testes

A FASE 12 adicionou testes em múltiplos níveis.

## Testes unitários

Cobrem:

- contratos;
- casos de uso;
- classificação de falhas;
- backoff;
- failure handler;
- política de lease;
- worker;
- dispatcher;
- ordem lock → lookup → avaliação.

---

## Testes JDBC

Cobrem:

- persistência de runs;
- persistência de candidates;
- idempotência de candidate;
- enqueue idempotente;
- claim;
- success;
- retry;
- dead;
- leases;
- recuperação;
- lock de snapshot;
- constraints.

---

## Testes integrados

Cobrem a combinação real:

```text
PostgreSQL
+
JdbcProcessingJobQueueAdapter
+
ProcessingWorker
+
FailureHandler
```

Foram validados:

- sucesso;
- retry transitório;
- falha permanente;
- estado terminal;
- limpeza de lease;
- múltiplos workers;
- idempotência.

---

# 25. Baseline final

Ao encerrar a implementação técnica:

```text
Tests run: 514
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

A suíte permanece utilizando PostgreSQL real onde a persistência precisa ser validada.

---

# 26. Commits principais da FASE 12

Sequência de commits da branch:

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
```

---

# 27. Critérios de aceite

## Enrichment falha sem repetir coleta

Atendido.

O resultado do parsing é persistido como `DealCandidate`.

Retry de `ENRICH_DEAL` trabalha a partir do candidato persistido.

---

## Estágios podem ser reexecutados com segurança

Atendido.

Foram implementados mecanismos idempotentes em:

- `ProcessingRun`;
- `DealCandidate`;
- `ProcessingJob`;
- enrichment;
- avaliação.

---

## Reprocessar uma oferta sem repetir chamadas externas desnecessárias

Atendido.

Snapshot persistido impede novo enrichment externo quando a etapa já havia sido concluída.

Avaliação persistida impede novo cálculo completo quando já houver resultado.

---

## Diferenciar falha transitória e permanente

Atendido.

Existe classificador explícito e políticas diferentes para:

```text
TRANSIENT
PERMANENT
```

---

## Retry controlado

Atendido.

Existe:

```text
attempt_count
max_attempts
available_at
RETRY_WAIT
backoff
```

---

## Preparar múltiplos workers

Atendido.

A fila utiliza:

```text
FOR UPDATE SKIP LOCKED
```

com ownership de lease e recuperação de trabalhos expirados.

---

## Evitar duplicidade na escala horizontal

Atendido nos pontos críticos da FASE 12 através de:

- idempotency key dos jobs;
- identidade forte de candidate;
- claim transacional;
- locks;
- constraints;
- serialização de avaliação por snapshot.

---

# 28. Fora do escopo da FASE 12

A FASE 12 não implementa:

- interface de operador;
- endpoint HTTP para operação;
- interface CLI definitiva;
- publicação automática;
- integração com Telegram;
- integração com WhatsApp;
- integração com Discord;
- scheduler definitivo;
- cron operacional;
- observabilidade completa;
- dashboards;
- métricas;
- tracing distribuído;
- broker externo de mensagens.

Esses itens permanecem para fases posteriores.

---

# 29. Estado arquitetural ao final da fase

A aplicação passa a possuir a seguinte estrutura operacional:

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

Todos os estágios são coordenados pela fila persistente:

```text
processing_job
```

e executados por:

```text
ProcessingWorker
```

---

# 30. Conclusão

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
locks por agregado
+
constraints
```

estabelece a base necessária para execução operacional escalável.

A FASE 12 é considerada concluída tecnicamente com:

```text
514 testes verdes
0 failures
0 errors
0 skipped
```

---

# 31. Próxima fase

O planejamento das FASES 13 a 21 passa a ser definido por:

```text
docs/phases/ROADMAP_RASPING_AMAZON_V1.md
```

A próxima etapa é:

```text
FASE 13 — Geração de publicação e link de associado
```

A mudança de ordem em relação ao planejamento anterior é deliberada.

A interface operacional não será responsável por introduzir regras de geração de publicação.

Primeiro será consolidado um caso de uso completo e independente de canal:

```text
Product
OfferSnapshot
DealEvaluation
PaymentConditions
PublicationTemplate
AffiliateLink
        ↓
PublicationGenerator
        ↓
Publication persistida
```

Somente depois a interface será construída sobre casos de uso já completos.

A sequência inicial do roadmap atualizado passa a ser:

```text
FASE 12
orquestração
    ↓
FASE 13
geração de publicação
    ↓
FASE 14
interface operacional
    ↓
FASE 15
qualidade integrada
```

A FASE 13 deverá preservar a arquitetura estabelecida até aqui:

- domínio independente de infraestrutura;
- templates separados dos dados da oferta;
- versionamento explícito;
- geração de link de associado encapsulada;
- rastreabilidade até `OfferSnapshot` e `DealEvaluation`;
- persistência reproduzível da publicação;
- independência de Telegram, WhatsApp ou outro canal.

Telegram, WhatsApp, scheduler e envio automático permanecem fora do escopo da FASE 13.

O roadmap atualizado deve ser tratado como referência de planejamento das FASES 13 a 21, enquanto os relatórios de resultado continuam registrando o que efetivamente ocorreu em cada fase.