# Rasping Amazon

Sistema em desenvolvimento para **coleta, normalização, enriquecimento, validação, filtragem, avaliação, score, ranking, histórico, evolução, momentum, orquestração durável, geração auditável de publicações, observabilidade, agendamento e execução contínua** de ofertas da Amazon Brasil.

O projeto prioriza:

- separação de responsabilidades;
- rastreabilidade;
- idempotência;
- auditabilidade;
- evolução incremental;
- persistência durável;
- configuração explícita;
- segurança operacional;
- escalabilidade guiada por necessidade real.

> **Estado atual: FASE 17 concluída localmente.**
>
> O sistema possui pipeline de decisão persistido, orquestração assíncrona durável em PostgreSQL, retry/lease/idempotência, geração de publicação versionada, interface operacional Java em CLI, observabilidade correlacionada por execução e, a partir da FASE 17, **scheduler persistido, prevenção de sobreposição, pausa operacional e runtime contínuo com scheduler + worker**.
>
> Gate local final da FASE 17:
>
> ```text
> Tests run: 1047
> Failures: 0
> Errors: 0
> Skipped: 0
>
> BUILD SUCCESS
> ```
>
> O catálogo Flyway alcança **V24**.
>
> A FASE 17 possui prova de concorrência entre duas instâncias e prova operacional hermética de múltiplos ciclos automáticos usando PostgreSQL real.
>
> O encerramento remoto da FASE 17 ainda depende de integração da documentação final, commit, push, Pull Request e CI remoto verde.

---

## 1. Objetivo

O Rasping Amazon não é apenas um raspador de ofertas.

O objetivo é construir um sistema em que:

```text
coleta
  ↓
identificação / normalização
  ↓
enriquecimento
  ↓
validação estrutural Amazon
  ↓
filtros comerciais configuráveis
  ↓
score versionado
  ↓
ranking determinístico
  ↓
histórico / evolução temporal
  ↓
momentum versionado
  ↓
orquestração durável
  ↓
agendamento recorrente
  ↓
geração de Publication
  ↓
seleção operacional futura
  ↓
aprovação / outbox futura
  ↓
canais futuros
```

permaneçam desacoplados.

A ordem das fases deve ser preservada.

Responsabilidades futuras não devem ser antecipadas sem decisão explícita.

---

## 2. Estado atual das fases

| Fase | Descrição | Status |
|---|---|---|
| FASE 0 | Levantamento da fonte e regras | CONCLUÍDA |
| FASE 0 v2 | Semântica comercial de preços e pagamento | CONCLUÍDA |
| FASE 1 | Fundação Java | CONCLUÍDA |
| FASE 2 | PostgreSQL, schema e migrations | CONCLUÍDA |
| FASE 2 v2 | Evolução comercial da persistência | CONCLUÍDA |
| FASE 3 | Domínio e contratos internos | CONCLUÍDA |
| FASE 3 v2 | Revisão comercial e estrutural | CONCLUÍDA |
| FASE 4 | Configuração e segredos | CONCLUÍDA |
| FASE 5 | Coleta da página de promoções | CONCLUÍDA |
| FASE 6 | Parser, ASIN e normalização | CONCLUÍDA |
| FASE 7 | Enriquecimento da página individual | CONCLUÍDA |
| FASE 8 | Validação estrutural Amazon | CONCLUÍDA |
| FASE 8.5 | Consolidação do núcleo e preparação dos dados de decisão | CONCLUÍDA |
| FASE 9 | Motor de filtros comerciais configuráveis | CONCLUÍDA |
| FASE 10 | Score, ranking e explicabilidade | CONCLUÍDA |
| FASE 11 | Histórico, evolução e momentum | CONCLUÍDA |
| FASE 12 | Orquestração assíncrona e processamento durável | CONCLUÍDA |
| FASE 13 | Geração de publicação e link de associado | CONCLUÍDA |
| FASE 14 | Interface operacional não bloqueante | CONCLUÍDA |
| FASE 15 | Qualidade integrada | CONCLUÍDA |
| FASE 16 | Observabilidade, auditoria e operação | CONCLUÍDA |
| FASE 17 | Agendamento e execução contínua | CONCLUÍDA LOCALMENTE |
| FASE 18 | Contrato de canais e outbox de publicação | PRÓXIMA APÓS GATE REMOTO |
| FASE 19 | Telegram e WhatsApp | PLANEJADA |
| FASE 20 | Resiliência, recuperação e falhas de produção | PLANEJADA |
| FASE 21 | Segurança, governança e fechamento da versão 1.0 | PLANEJADA |

---

## 3. Arquitetura

Estrutura principal:

```text
src/
├── main/
│   ├── java/
│   │   └── com/raspingamazon/
│   │       ├── application/
│   │       ├── domain/
│   │       ├── infrastructure/
│   │       └── presentation/
│   └── resources/
│       └── db/
│           └── migration/
└── test/
    ├── java/
    │   └── com/raspingamazon/
    └── resources/
        └── amazon/
            └── fixtures/
```

Responsabilidades:

- `domain`: conceitos, invariantes e regras de negócio sem dependência de infraestrutura;
- `application`: contratos, ports, read models e coordenação dos casos de uso;
- `infrastructure`: PostgreSQL, Flyway, JDBC, configuração, HTTP, parsing específico da Amazon, bootstrap, composition roots e runtime;
- `presentation`: adaptadores de interação com o operador, atualmente CLI.

Dependência conceitual:

```text
presentation
     ↓
application
     ↓
domain
```

A infraestrutura implementa ports definidos para dentro:

```text
infrastructure
     ↓
application contracts
```

O domínio não conhece:

```text
HTML
HTTP
PostgreSQL
Flyway
JDBC
CLI
scheduler
Telegram
WhatsApp
```

O scheduler da FASE 17 também não conhece regras de domínio.

O projeto permanece em um único módulo Maven enquanto não houver pressão arquitetural real para decomposição.

---

## 4. Stack

- Java 25
- Maven Wrapper 3.3.4
- Maven 3.9.x
- JUnit 5
- PostgreSQL 18.6
- Flyway 11.14.1
- PostgreSQL JDBC 42.7.8
- Jackson Databind
- jsoup
- Docker / Docker Compose
- GitHub Actions
- Exec Maven Plugin para execução explícita da CLI operacional
- Playwright somente no profile de diagnóstico externo quando aplicável

---

## 5. Build e gate local

O projeto utiliza o Maven Wrapper versionado.

Windows:

```powershell
.\mvnw.cmd clean test
```

Linux/macOS/CI:

```bash
./mvnw clean test
```

Gate local final da FASE 17:

```text
Tests run: 1047
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

A suíte padrão permanece hermética em relação à Amazon real.

Probes externas são executadas de forma explícita e separada.

---

## 6. Configuração e segredos

A configuração principal da aplicação permanece baseada em ambiente.

Variáveis centrais já existentes incluem:

```text
APP_ENV
DB_HOST
DB_PORT
DB_NAME
DB_USER
DB_PASSWORD
AMAZON_ASSOCIATE_TAG
```

Regras:

- `DB_PASSWORD` é segredo e não possui valor real versionado;
- `AMAZON_ASSOCIATE_TAG` pertence ao fluxo de publicação;
- `.env` local não deve ser versionado;
- configuração funcional não deve ser escondida em constantes semânticas;
- segredos nunca devem aparecer em logs estruturados.

A FASE 17 adicionou configuração própria do runtime contínuo, documentada em seção específica.

---

## 7. Modelo de oferta

`OfferSnapshot` representa uma observação temporal de uma oferta.

Campos relevantes:

```text
product
collectedAt
currentPrice
basisPrice
previousPrice
soldPercentage
rating
reviewCount
sellerName
deliveryProvider
sellerType
deliveryType
source
paymentConditions
```

Princípios:

- `basisPrice` é diferente de `previousPrice`;
- ausência não equivale a zero;
- preço Pix/NuPay não é inferido;
- condições comerciais são persistidas de forma estruturada;
- evidências relevantes permanecem rastreáveis.

---

## 8. Condições comerciais

Tipos:

```text
PaymentConditionType.CASH
PaymentConditionType.CREDIT_INSTALLMENT
```

Métodos conhecidos:

```text
PIX
NUPAY_ADDITIONAL_LIMIT
CREDIT_CARD
```

Uma condição pode preservar:

```text
price
discountPercentage
installmentCount
installmentAmount
installmentTotal
interest
paymentMethods
```

Dados não explicitamente observados não são inventados.

---

## 9. Validação estrutural Amazon

Regras:

```text
SELLER_IS_AMAZON
DELIVERY_IS_AMAZON
```

A política é fail closed.

Exemplos de rejeição:

```text
SELLER_UNKNOWN
SELLER_THIRD_PARTY
DELIVERY_UNKNOWN
DELIVERY_THIRD_PARTY
```

Elegibilidade estrutural permanece separada de:

```text
filtros comerciais
score
ranking
momentum
apresentação de publicação
seleção operacional de publicação
scheduling
```

---

## 10. Filtros comerciais

Perfil ativo consolidado:

```text
COMMERCIAL_FILTER_V2
```

A V2 utiliza desconto sobre preço-base como critério comercial principal de desconto.

Configuração introduzida pela evolução V16:

```text
minBasisDiscountPercentage = 20.0000
minRating = 4.30
minReviewCount = 100
```

A versão anterior permanece histórica:

```text
COMMERCIAL_FILTER_V1
```

Mudanças semânticas de filtros são versionadas.

Dado ausente continua diferente de dado presente abaixo do limite.

---

## 11. Score e ranking

Perfil ativo:

```text
SCORE_V2
```

Fatores:

```text
SOLD_PERCENTAGE
BASIS_DISCOUNT
RATING
REVIEW_COUNT
```

Pesos configurados:

```text
SOLD_PERCENTAGE = 30
BASIS_DISCOUNT  = 25
RATING          = 20
REVIEW_COUNT    = 15
```

Características:

- score reproduzível;
- fatores explicáveis;
- fatores persistidos;
- ranking determinístico;
- versão histórica preservada.

`SCORE_V1` permanece como versão histórica.

Histórico de publicação não altera retroativamente o score.

---

## 12. Histórico e momentum

O histórico é baseado em snapshots persistidos.

Componentes principais:

```text
HistoricalOfferObservation
SnapshotEvolution
SnapshotEvolutionCalculator
MomentumEngine
MomentumAudit
```

Versão:

```text
MOMENTUM_V1
```

Momentum:

- interpreta evolução temporal;
- não altera elegibilidade;
- não altera score;
- pode ser indisponível quando falta base histórica;
- possui auditoria própria.

---

## 13. Orquestração durável — FASE 12

A FASE 12 introduziu processamento assíncrono baseado em PostgreSQL.

Entidades:

```text
ProcessingRun
DealCandidate
ProcessingJob
```

Fluxo:

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

A fila suporta:

- jobs persistidos;
- claim concorrente;
- `FOR UPDATE SKIP LOCKED`;
- retry;
- backoff;
- leases de job;
- recuperação prevista pela orquestração;
- múltiplos workers;
- idempotência por etapa;
- falhas transitórias e permanentes.

Estados:

```text
PENDING
RUNNING
RETRY_WAIT
SUCCEEDED
DEAD
```

Tipos:

```text
COLLECT_DEALS
ENRICH_DEAL
EVALUATE_DEAL
```

O scheduler da FASE 17 reutiliza esse pipeline.

---

## 14. Geração de publicação — FASE 13

A FASE 13 transforma uma `DealEvaluation` persistida em `Publication`.

Fluxo:

```text
dealEvaluationId
      ↓
PublicationDataQueryPort
      ↓
PublicationData
      ↓
CommercialPresentationPolicy
      ↓
AffiliateLinkGenerator
      ↓
PublicationTemplate
      ↓
Publication
      ↓
PublicationRepository
      ↓
PostgreSQL
```

A geração não:

```text
consulta novamente a Amazon
reexecuta enrichment
reavalia elegibilidade
reexecuta filtros
recalcula score
recalcula momentum
envia para canal
```

---

## 15. Dados de publicação

`PublicationData` é construído a partir do estado persistido.

Cadeia:

```text
Publication
    ↓
DealEvaluation
    ↓
OfferSnapshot
    ↓
Product
```

A publicação não duplica todos os fatos históricos.

Ela referencia a decisão que a originou.

---

## 16. Política comercial de apresentação

Contrato:

```text
CommercialPresentationPolicy
```

Implementação:

```text
AmazonCommercialPresentationV1
```

Versão:

```text
AMAZON_COMMERCIAL_PRESENTATION_V1
```

Essa política não decide:

```text
elegibilidade
score
ranking
recorrência
quota
cadência de scheduler
```

Ela define como fatos já aceitos são apresentados.

---

## 17. Template de publicação

Contrato:

```text
PublicationTemplate
```

A versão de template é persistida.

Templates não são misturados com a coleta, avaliação ou scheduler.

A reprodução da mensagem usa fatos persistidos e versão explícita.

---

## 18. Link de associado

Contrato:

```text
AffiliateLinkGenerator
```

A tag de associado vem de configuração externa.

O restante da aplicação não concatena parâmetros de afiliado manualmente.

---

## 19. `Publication`

A entidade `Publication` preserva relação com:

```text
DealEvaluation
templateVersion
conteúdo gerado
affiliateLink
status
timestamps
```

Geração não equivale a entrega externa.

Outbox e canais continuam em fases posteriores.

---

## 20. Idempotência da publicação

A geração de publicação preserva identidade lógica por avaliação e versão/política aplicável.

Reprocessar o mesmo comando não deve produzir duplicação arbitrária.

Idempotência de **entrega em canal** continua sendo responsabilidade futura da outbox.

---

## 21. Interface operacional — FASE 14

A interface operacional segue:

```text
presentation
      ↓
application
      ↓
ports
      ↓
infrastructure
```

Ela não executa SQL direto.

Ela não chama parser Amazon diretamente.

Princípio:

```text
A interface observa e administra o pipeline.
A interface não autoriza o pipeline a funcionar.
```

Esse princípio continua válido após a introdução do daemon da FASE 17.

---

## 22. Read side operacional de avaliações

A interface consegue consultar avaliações persistidas sem recalcular decisão.

Informações podem incluir:

```text
ASIN
produto
preço
desconto
rating
reviewCount
seller
delivery
score
momentum
regras avaliadas
motivos de rejeição
```

Consulta não altera o pipeline.

---

## 23. Read side operacional de runs e jobs

A CLI expõe leitura de:

```text
ProcessingRun
ProcessingJob
```

O operador pode consultar estado sem acessar PostgreSQL manualmente.

A observabilidade da FASE 16 ampliou o detalhe de uma run.

---

## 24. Read side operacional de publicações

A interface consegue consultar:

```text
Publication
status
template
conteúdo
affiliateLink
relação com avaliação
timestamps
```

sem gerar novamente a publicação apenas para exibição.

---

## 25. CLI operacional

Pacote:

```text
com.raspingamazon.presentation.cli
```

Áreas consolidadas incluem:

```text
evaluations
runs
jobs
publications
alerts
schedules
```

A CLI permanece não bloqueante.

O daemon contínuo possui entrypoint separado.

---

## 26. Schedules na CLI

A FASE 17 adicionou comandos de administração de schedule equivalentes a:

```text
status
pause
resume
interval
```

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

A CLI não contém SQL.

---

## 27. PostgreSQL e Flyway

PostgreSQL continua sendo o estado operacional principal.

Estado estrutural ao final da FASE 17:

```text
Flyway V24
```

A evolução preserva migrations anteriores.

Mudanças estruturais novas devem receber nova migration versionada.

---

## 28. Migrations de orquestração e operação

Migrations relevantes das fases recentes incluem:

```text
V11  processing orchestration
V12  evaluation processing idempotency
V13  deal candidate idempotency
V14  publication generation audit
V15  preparação de basis discount / score V2
V16  ativação de basis discount / score V2
V17  leitura operacional de evaluation
V18  índices de leitura de processing
V19  índices de leitura de publication
V20  correlação de observabilidade
V21  índice de leitura por ProcessingRun
V22  integration observation
V23  processing schedule
V24  índice do guard de sobreposição do scheduler
```

Nenhuma migration histórica é reescrita para introduzir scheduler.

---

## 29. Qualidade integrada — FASE 15

A FASE 15 consolidou a execução autocontida dos testes PostgreSQL.

O gate normal prepara o schema necessário e valida jornadas críticas.

Princípios:

```text
testes herméticos
fixtures pequenas
Amazon real separada
schema reproduzível
```

A FASE 17 continua usando essa base.

---

## 30. Observabilidade — FASE 16

Princípio:

```text
Observabilidade explica fatos persistidos.
Observabilidade não redefine decisões.
```

A correlação utiliza identidades reais do pipeline.

Contexto suportado pode incluir:

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

---

## 31. Logs estruturados

Componentes:

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

A observabilidade do worker é best effort.

Falha de logging não deve transformar sucesso funcional em falha.

---

## 32. Observações de integração

Modelo:

```text
IntegrationObservation
```

Persistência:

```text
integration_observation
```

Dados incluem:

```text
observedAt
integration
operation
outcome
durationMs
processingRunId
processingJobId
jobType
candidateId
snapshotId
evaluationId
publicationId
asin
failureOrigin
failureType
errorCode
httpStatusCode
```

Não são persistidos nessa tabela:

```text
segredos
payload HTTP completo
HTML integral
credenciais
```

---

## 33. Origem e tipo de falha

Origem operacional:

```text
EXTERNAL
INTERNAL
```

Classificação para retry:

```text
TRANSIENT
PERMANENT
```

As duas dimensões são diferentes.

A FASE 17 preserva essa separação.

---

## 34. Métricas por `ProcessingRun`

`ProcessingRunDetail` agrega:

```text
summary
pipeline
jobs
integrations
```

Métricas podem expor:

```text
candidates coletados
candidates enriquecidos
evaluations
eligible/rejected
publications geradas
jobs por status
tentativas
falhas de integração
latência média/máxima por integração
```

O scheduler não recalcula essas métricas.

---

## 35. Alertas operacionais

Tipos consolidados na FASE 16 incluem:

```text
REPEATED_EXTERNAL_FAILURES
DEAD_JOBS
ZERO_CANDIDATES
SUSPICIOUS_COLLECTION_DROP
```

Alertas são derivados de fatos persistidos.

Eles não agendam processamento.

---

# FASE 17 — AGENDAMENTO E EXECUÇÃO CONTÍNUA

## 36. Objetivo da FASE 17

A FASE 17 transforma a aplicação em um sistema capaz de executar ciclos automaticamente.

Fluxo oficial:

```text
scheduler
   ↓
ProcessingRun
   ↓
orquestração da FASE 12
```

O scheduler não contém regras de coleta, parser ou avaliação.

---

## 37. Modelo `ProcessingSchedule`

Estado persistido:

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

O modelo descreve scheduling.

Não descreve regra de negócio.

---

## 38. `ProcessingSchedulePort`

Porta de aplicação:

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

O PostgreSQL implementa a coordenação concreta.

---

## 39. Migration V23

Migration:

```text
V23__processing_schedule.sql
```

Tabela:

```text
processing_schedule
```

A migration não semeia uma frequência funcional fixa.

O schedule inicial é criado por caso de uso de bootstrap.

### 39.1. Migration V24 — suporte ao guard de sobreposição

Migration:

```text
V24__processing_schedule_overlap_guard_index.sql
```

Índice parcial:

```text
idx_processing_job_active_collect_by_run
```

O índice cobre somente jobs:

```text
job_type = COLLECT_DEALS
status IN (PENDING, RUNNING, RETRY_WAIT)
processing_run_id IS NOT NULL
```

A finalidade é sustentar a consulta executada pelo scheduler a cada tentativa de aquisição sem transformar `processing_job` em varredura completa.

A V24 não introduz uma segunda fila, não altera o ciclo de vida dos jobs e não muda a semântica da FASE 12. Ela apenas otimiza a verificação de que a coleta automática anterior ainda não terminou.

---

## 40. Frequência configurável

A frequência funcional do processamento fica em:

```text
ProcessingSchedule.interval
```

O valor inicial é recebido por configuração externa.

Variável obrigatória:

```text
PROCESSING_SCHEDULE_INTERVAL
```

Formato:

```text
Duration ISO-8601
```

Exemplos:

```text
PT15M
PT1H
PT30S
```

A frequência não fica hardcoded.

---

## 41. Fonte operacional de verdade

Regra:

```text
configuração externa
→ cria o schedule quando ausente

depois:
PostgreSQL
→ fonte operacional de verdade
```

Reiniciar o processo não sobrescreve:

```text
pause
interval alterado
nextRunAt
lastScheduledFor
lastProcessingRunId
```

---

## 42. `EnsureProcessingScheduleUseCase`

Responsabilidade:

```text
garantir schedule inicial sem sobrescrever estado existente
```

Novo schedule:

```text
enabled = true
nextRunAt = now
```

A primeira janela fica imediatamente disponível.

---

## 43. Lease de scheduling

Uma janela de execução possui ownership temporário.

Conceito:

```text
schedule devido
      ↓
tryAcquireDue
      ↓
leaseOwner
leaseExpiresAt
```

O lease é persistido.

Coordenação não depende de lock local da JVM.

---

## 44. Prevenção de sobreposição

A proteção possui duas camadas distintas.

A primeira impede duplicação da mesma janela lógica:

```text
mesma janela
2 schedulers
→ 1 vencedor do lease
→ 1 ProcessingRun
→ 1 COLLECT_DEALS
```

A segunda impede que a janela seguinte seja criada enquanto o `COLLECT_DEALS` da `lastProcessingRunId` ainda estiver não terminal.

Estados que bloqueiam nova aquisição:

```text
PENDING
RUNNING
RETRY_WAIT
```

Estados terminais que liberam o avanço da próxima janela:

```text
SUCCEEDED
DEAD
```

A regra é aplicada atomicamente dentro de `JdbcProcessingScheduleAdapter.tryAcquireDue()` por `NOT EXISTS` sobre o job `COLLECT_DEALS` da última `ProcessingRun`.

Isso é deliberadamente diferente de olhar apenas `ProcessingRun.status`.

Uma `ProcessingRun` pode estar `FAILED` enquanto o job técnico responsável pela coleta continua em `RETRY_WAIT`. Nesse cenário, criar nova coleta automática geraria sobreposição indevida. O estado técnico do `COLLECT_DEALS` é, portanto, a autoridade usada pelo guard.

O teste JDBC de concorrência prova:

```text
PENDING
→ próxima janela bloqueada

RUNNING
→ próxima janela bloqueada

RETRY_WAIT
→ próxima janela bloqueada

SUCCEEDED
→ próxima janela liberada

janela liberada + 2 schedulers
→ exatamente 1 nova ProcessingRun
→ exatamente 1 novo COLLECT_DEALS
```

A V24 adiciona índice parcial específico para esse caminho crítico.

---

## 45. `ScheduleProcessingRunUseCase`

Responsabilidades:

```text
adquirir janela
criar ProcessingRun
criar COLLECT_DEALS
confirmar janela
calcular nextRunAt
```

As operações são tratadas como uma unidade transacional.

O caso de uso não executa coleta.

---

## 46. Identidade de janela

`ProcessingRun.run_key`:

```text
scheduled:<scheduleKey>:<scheduledFor.toInstant()>
```

A identidade usa instante normalizado.

Offsets diferentes que representam o mesmo instante não criam duplicação lógica.

---

## 47. Idempotência do primeiro job

O `COLLECT_DEALS` inicial utiliza:

```text
collect:<processingRunId>
```

A fila durável continua protegida por:

```text
job_type + idempotency_key
```

Lease e constraints trabalham em conjunto.

---

## 48. Atomicidade do scheduling

Unidade:

```text
lease
+
run
+
job
+
confirmação
```

Falha intermediária:

```text
rollback
```

A FASE 17 possui prova JDBC de rollback e reaproveitamento posterior da janela.

---

## 49. Política após downtime

Não existe catch-up ilimitado.

Regra:

```text
naturalNext = scheduledFor + interval
```

Se `naturalNext` ainda está no futuro:

```text
nextRunAt = naturalNext
```

Se já ficou no passado:

```text
nextRunAt = now + interval
```

Downtime não gera automaticamente uma rajada de janelas históricas.

---

## 50. Pausa operacional

`pause` impede novas execuções.

Não apaga:

```text
ProcessingRun
ProcessingJob
histórico
observações
última execução
```

Trabalho já durável não é apagado por pausa.

---

## 51. Resume e alteração de intervalo

O operador pode:

```text
resume
changeInterval
```

As operações preservam histórico.

Conflitos com lease ativo são protegidos atomicamente.

---

## 52. Scheduler runner

Classe:

```text
ContinuousProcessingSchedulerRunner
```

Dependências:

```text
ScheduledProcessingTrigger
scheduleKey
schedulerInstanceId
pollInterval
ProcessingSchedulerWaitStrategy
```

O runner não conhece PostgreSQL diretamente.

Ele não conhece Amazon.

---

## 53. Polling técnico

`PROCESSING_SCHEDULER_POLL_INTERVAL` define a frequência técnica de consulta ao schedule.

Isso é diferente de:

```text
PROCESSING_SCHEDULE_INTERVAL
```

que define a cadência funcional das runs.

Os dois conceitos não são misturados.

---

## 54. Worker runner

Classe:

```text
ContinuousProcessingWorkerRunner
```

Comportamento:

```text
ProcessingWorker.runOnce()
      ↓
job?
 ├─ sim → próxima rodada imediatamente
 └─ não → aguardar idleDelay
```

O runner não implementa retry.

Retry continua pertencendo à FASE 12.

---

## 55. Restrições da fonte Amazon

A FASE 17 formalizou:

```text
SourceRestrictionType
SourceRestrictionException
```

Tipos atuais:

```text
CHALLENGE
CAPTCHA
BLOCKED
```

O objetivo é classificar restrição.

Não contornar proteção.

---

## 56. HTTP 429, 403 e timeout

Classificação atual:

```text
429
→ TRANSIENT

timeout
→ TRANSIENT

403
→ PERMANENT

challenge/CAPTCHA/blocked
→ PERMANENT com código de source restriction
```

A política usa o retry já existente.

Nenhum segundo mecanismo de retry foi criado.

---

## 57. Decorator Amazon no runtime contínuo

Fluxo de produção:

```text
JavaHttpTransport
      ↓
HttpCollectionCollector
      ↓
AmazonDealsCollector
      ↓
CollectDealsUseCase
```

Assim, restrições específicas da Amazon passam pela fronteira correta.

---

## 58. Não contorno

Não fazem parte do projeto:

```text
CAPTCHA solver
proxy rotation para evasão
fingerprint spoofing
bypass de challenge
```

Restrições são tratadas como falhas ou limitações da fonte.

---

## 59. Fonte futura

A arquitetura continua apta a receber futuramente:

```text
AmazonHtmlSource
AmazonCreatorsApiSource
```

ou combinação equivalente.

A v1 não depende da Creators API.

O scheduler permanece independente do tipo concreto de fonte.

---

## 60. Composition root contínuo

Classe:

```text
ContinuousProcessingComposition
```

Ela monta:

```text
scheduler side
worker side
```

sem iniciar threads.

---

## 61. Duas Connections

A composition exige duas instâncias distintas:

```text
schedulerConnection
workerConnection
```

Razão:

```text
scheduler e worker executam concorrentemente
```

Transações dos dois loops não devem compartilhar a mesma `Connection`.

---

## 62. Lado scheduler

Componentes:

```text
JdbcProcessingScheduleAdapter
JdbcProcessingRunRepositoryAdapter
JdbcProcessingJobQueueAdapter
JdbcTransactionAdapter
ScheduleProcessingRunUseCase
ContinuousProcessingSchedulerRunner
```

Esse lado cria trabalho durável.

Não processa o trabalho.

---

## 63. Lado worker

O worker reutiliza a FASE 12:

```text
CollectDealsUseCase
EnrichDealUseCase
EvaluateDealUseCase
DefaultProcessingJobExecutor
ProcessingJobFailureHandler
ProcessingWorker
ContinuousProcessingWorkerRunner
```

Não existe pipeline paralelo de recorrência.

---

## 64. Observabilidade no daemon

O bootstrap contínuo monta:

```text
JsonStructuredOperationalLogAdapter
JdbcIntegrationObservationPersistenceAdapter
BestEffortIntegrationObservationRecorder
```

Coleta e enrichment continuam observáveis.

A FASE 17 não desabilita a FASE 16.

---

## 65. Runtime

Classe:

```text
ContinuousProcessingRuntime
```

Coordena:

```text
scheduler thread
worker thread
```

Sinal compartilhado:

```text
keepRunning
```

O runtime não contém regra de negócio.

---

## 66. Falha terminal

Se um dos loops termina inesperadamente durante operação ativa:

```text
primeira falha é preservada
outro loop recebe parada
runtime termina
```

O sistema não deve continuar silenciosamente com somente metade do daemon.

---

## 67. Shutdown coordenado

Pedido de shutdown:

```text
keepRunning = false
```

Waits são acordados por interrupção.

Após observar a parada:

```text
scheduler não cria nova janela
worker não inicia nova unidade
```

O estado funcional permanece durável no PostgreSQL.

---

## 68. `ContinuousProcessingApplication`

Classe responsável pelo ownership operacional de:

```text
runtime
schedulerConnection
workerConnection
```

A composition não fecha Connections.

A application encerra runtime e recursos.

---

## 69. `ContinuousProcessingBootstrap`

Responsabilidades:

```text
carregar configuração
abrir Connections
garantir schedule
montar observabilidade
montar integrações Amazon
montar composition
montar runtime
transferir ownership
```

Se a montagem falha, recursos parciais são fechados best effort.

---

## 70. `ContinuousProcessingMain`

Entrypoint do daemon.

Responsabilidades:

```text
bootstrap
shutdown hook
start
awaitTermination
cleanup
exit code
```

O entrypoint da CLI permanece separado:

```text
OperationalCliMain
```

---

## 71. Configuração da FASE 17

Variáveis:

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

`PROCESSING_SCHEDULE_INTERVAL` é obrigatório para o bootstrap inicial.

Parâmetros técnicos podem possuir defaults operacionais.

---

## 72. Prova de concorrência

Teste:

```text
ScheduleProcessingRunConcurrencyJdbcIntegrationTest
```

Cenários:

```text
duas instâncias / mesma janela
duas janelas recorrentes
takeover após lease expirado
downtime sem catch-up burst
```

Todos passaram contra PostgreSQL real.

---

## 73. Prova de múltiplos ciclos

Teste:

```text
ContinuousProcessingMultipleCyclesJdbcIntegrationTest
```

Utiliza:

```text
scheduler real
worker real
runtime real
threads reais
PostgreSQL real
collector hermético
parser hermético
```

Resultado exigido:

```text
pelo menos 3 ciclos automáticos COMPLETED
```

Sem intervenção manual entre ciclos.

---

## 74. Por que a prova é hermética

A prova de scheduler não precisa validar disponibilidade atual da Amazon.

Ela precisa validar:

```text
recorrência
coordenação
pipeline durável
worker
shutdown
```

Por isso a fonte do teste é determinística.

A Amazon real permanece em probes separadas.

---

## 75. ADR-0010 e seleção de publicação

ADR-0010 continua proposta para:

```text
PublicationSelectionPolicy
cooldown
quota
cadência de publicações
priorização de itens nunca publicados
```

A FASE 17 não implementa essa política.

O scheduler de processamento e a política de publicação são conceitos diferentes.

---

## 76. ADR-0011 e observabilidade

ADR-0011 permanece válida.

A FASE 17 preserva:

```text
PostgreSQL como verdade durável
logs como evidência complementar
métricas persistidas quando necessário
observabilidade sem redefinir negócio
```

---

## 77. ADR-0012

Documento:

```text
docs/adr/0012-agendamento-e-execucao-continua.md
```

Status final da decisão:

```text
ACEITA
```

A ADR formaliza:

```text
schedule persistido
lease PostgreSQL
frequência configurável
atomicidade
idempotência
pause/resume
sem catch-up storm
runtime scheduler + worker
duas Connections
restrições Amazon
fronteiras com FASE 18/20
```

---

## 78. Testes da FASE 17

Cobertura específica inclui:

```text
ProcessingSchedule
lease
adapter JDBC
atomicidade
rollback
scheduler use case
worker runner
scheduler runner
source restrictions
failure classifier
operational use cases
CLI schedules
composition
runtime
environment config
concorrência de duas instâncias
takeover de lease
downtime
múltiplos ciclos
```

Os testes históricos continuam executando.

---

## 79. Progressão da suíte

```text
baseline FASE 17     931
modelo/ports         946
persistência         954
trigger              964
atomicidade JDBC     966
worker contínuo      972
controle operacional 1010
scheduler runner     1021
composition          1028
runtime              1037
bootstrap/config     1042
concorrência         1046
múltiplos ciclos     1047
```

Crescimento líquido:

```text
116 testes
```

---

## 80. Gate final local da FASE 17

```text
Tests run: 1047
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

Também foi executado:

```text
git diff --check
```

Sem erro de whitespace.

Foram observados avisos de normalização CRLF → LF em dois arquivos Amazon, sem falha de gate.

---

## 81. Fluxo vertical atual

```text
ContinuousProcessingMain
        ↓
ContinuousProcessingBootstrap
        ↓
EnsureProcessingScheduleUseCase
        ↓
processing_schedule
        ↓
ContinuousProcessingRuntime
        ├───────────────────────────────┐
        ↓                               ↓
scheduler                            worker
        ↓                               ↓
ScheduleProcessingRunUseCase         ProcessingWorker
        ↓                               ↓
ProcessingRun                       COLLECT_DEALS
        ↓                               ↓
COLLECT_DEALS                       DealCandidate
                                        ↓
                                     ENRICH_DEAL
                                        ↓
                                     OfferSnapshot
                                        ↓
                                     EVALUATE_DEAL
                                        ↓
                                     DealEvaluation
                                        ↓
                              Publication pode ser gerada
                              pelos casos de uso existentes
```

A FASE 17 automatiza o processamento.

Ela não transforma geração de publicação em envio automático.

---

## 82. Separação entre processamento e publicação externa

Atualmente:

```text
scheduler de processamento
→ implementado

geração de Publication
→ implementada

outbox
→ ainda não

Telegram
→ ainda não

WhatsApp
→ ainda não
```

Essa separação é deliberada.

---

## 83. O que ainda não foi implementado

Após a FASE 17 continuam fora do escopo:

- `PublicationSelectionPolicy` efetivamente aplicada;
- cooldown operacional de publicação;
- quota diária/horária de publicação;
- outbox;
- aprovação antes de envio;
- `PublicationChannel`;
- `PublicationCommand`;
- `PublicationResult` de canal;
- Telegram;
- WhatsApp;
- `PublicationAttempt` de canal;
- retry específico de canal;
- circuit breaker global;
- nova dead-letter;
- infraestrutura distribuída adicional;
- particionamento;
- cache distribuído;
- read replica;
- microserviços.

---

## 84. Próxima fase — FASE 18

Objetivo do roadmap:

```text
Contrato de canais e outbox de publicação
```

Conceitos esperados:

```text
PublicationChannel
PublicationCommand
PublicationResult
Outbox
aprovação
idempotência de entrega
```

O canal receberá conteúdo pronto.

Ele não deverá consultar diretamente `OfferSnapshot` ou `DealEvaluation` para remontar a mensagem.

---

## 85. FASE 19

Adapters concretos:

```text
TelegramChannel
WhatsAppChannel
```

Também entram:

```text
destino
tentativa
status
providerReference
retry de canal
formatação específica
```

Credenciais permanecerão externas ao código.

---

## 86. FASE 20

Hardening de produção.

Temas:

```text
taxonomia ampla de falhas
restart
recovery
dead-letter
reprocessamento
alertas
circuit breaker somente se necessário
```

A fase deverá ser guiada por evidência operacional.

---

## 87. FASE 21

Fechamento da v1.

Temas:

```text
segredos
governança
least privilege
backup/restore
retenção
release reproduzível
segurança operacional
```

Não é uma nova fase de regras comerciais.

---

## 88. Roadmap simplificado

```text
FASES 0–8
Fundação + coleta + dados + validação
              ↓
FASE 8.5
Consolidação
              ↓
FASES 9–11
Motor de decisão
              ↓
FASES 12–14
Pipeline durável + publicação + operação
              ↓
FASES 15–17
Qualidade + observabilidade + execução contínua
              ↓
FASES 18–19
Outbox + canais
              ↓
FASES 20–21
Hardening + fechamento v1
```

---

## 89. Princípios preservados

O projeto mantém:

- Java como núcleo;
- PostgreSQL como estado operacional principal;
- migrations Flyway append-only;
- domínio independente de infraestrutura;
- ports entre aplicação e adapters;
- regras comerciais versionadas;
- ausência diferente de zero;
- idempotência explícita;
- testes herméticos como gate principal;
- fonte real separada;
- observabilidade sem redefinir negócio;
- scheduler sem regras de domínio;
- publicação sem dependência de canal;
- segredos fora do código;
- evolução incremental;
- escalabilidade guiada por necessidade real.

---

## 90. Princípio de escala

A arquitetura atual não introduz por antecipação:

```text
Kafka
RabbitMQ
microservices
distributed cache
read replicas
partitioning
```

Decisão:

```text
medir primeiro
otimizar depois
```

PostgreSQL continua adequado para:

```text
estado
fila durável
leases
schedule
coordenação
```

no estágio atual.

---

## 91. Documentação por fases

Documentos de resultado continuam em:

```text
docs/phases/
```

A FASE 17 acrescenta:

```text
docs/phases/FASE_17_RESULTADO.md
```

O documento registra:

```text
decisões
arquitetura
persistência
runtime
concorrência
provas
gate local
fronteiras futuras
```

---

## 92. ADRs relevantes

Diretório:

```text
docs/adr/
```

ADRs particularmente relevantes para o estado atual:

```text
0001 — filtros e apresentação comercial
0002 — score / ranking / explicabilidade
0003 — histórico e momentum
0004 — geração de publicação e link
0005 — desconto / preço-base / preço efetivo
0006 — SCORE_V2 / basis discount
0007 — fallback rating/review
0008 — correção versionada de affiliate link
0009 — interface operacional não bloqueante
0010 — seleção, recorrência e cadência de publicações
0011 — observabilidade e correlação
0012 — agendamento e execução contínua
```

A ADR-0012 está aceita após implementação e gate local.

---

## 93. Estado consolidado

```text
Java 25
PostgreSQL 18.6
Flyway V24
1047 testes verdes

pipeline comercial
→ implementado

orquestração durável
→ implementada

geração de Publication
→ implementada

CLI operacional
→ implementada

observabilidade
→ implementada

scheduler persistido
→ implementado

worker contínuo
→ implementado

runtime scheduler + worker
→ implementado

pausa/resume/intervalo
→ implementados

concorrência entre schedulers
→ validada

múltiplos ciclos automáticos
→ validados

outbox
→ próxima fase

canais
→ fases posteriores
```

---

## 94. Estado formal da FASE 17

```text
gate técnico local = FECHADO
gate funcional local = FECHADO
gate concorrente = FECHADO
gate de múltiplos ciclos = FECHADO
documentação final = GERADA
commit final = PENDENTE
push = PENDENTE
Pull Request = PENDENTE
CI remoto = PENDENTE
merge = PENDENTE
```

Portanto:

```text
FASE 17 = CONCLUÍDA LOCALMENTE
```

---

## 95. Resultado final

O Rasping Amazon agora possui execução contínua estruturada sobre o pipeline durável já existente.

Fluxo resumido:

```text
configuração
      ↓
ProcessingSchedule persistido
      ↓
scheduler
      ↓
ProcessingRun
      ↓
COLLECT_DEALS
      ↓
ProcessingWorker
      ↓
pipeline durável
      ↓
estado persistido
      ↓
observabilidade operacional
```

A implementação preserva separação entre:

```text
agendar
processar
decidir
gerar publicação
entregar em canal
```

O próximo passo arquitetural, depois do gate remoto da FASE 17, é a **FASE 18 — contrato de canais e outbox de publicação**.
