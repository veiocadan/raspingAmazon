# ADR-0011 — Observabilidade, correlação e métricas operacionais

- **Status:** Aceita
- **Data:** 2026-09-26
- **Projeto:** Rasping Amazon
- **Fase relacionada:** FASE 16 — Observabilidade
- **Complementa:** ADR-0003, ADR-0004, ADR-0009 e ADR-0010

## Contexto

Ao final da FASE 15, o Rasping Amazon possui um pipeline persistente e auditável com:

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
      ↓
Publication
```

A orquestração já possui:

```text
jobs persistidos
retry
backoff
leases
recuperação de leases
idempotência
classificação de falhas
múltiplos workers
```

A interface operacional da FASE 14 já permite consultar:

```text
evaluations
runs
jobs
publications
```

A suíte integrada da FASE 15 também consegue preparar automaticamente um PostgreSQL vazio por meio das migrations reais do projeto.

O sistema já preserva uma quantidade significativa de fatos operacionais, mas ainda não possui uma camada de observabilidade explícita capaz de responder de forma consistente perguntas como:

```text
o que aconteceu nesta execução?

quantas ofertas foram encontradas?

quantas chegaram ao enrichment?

quantas foram avaliadas?

quantas foram rejeitadas?

quantas publicações foram geradas?

qual etapa falhou?

a falha foi externa ou interna?

qual ASIN estava envolvido?

qual job executou a operação?

quanto tempo uma integração externa levou?

quais objetos persistidos pertencem à mesma cadeia operacional?
```

A FASE 16 deve responder essas perguntas sem depender de planilhas, inspeção manual de tabelas ou reconstrução informal a partir de mensagens textuais.

O projeto também deve evitar dois erros arquiteturais:

```text
transformar logs em fonte primária de verdade
```

e:

```text
introduzir infraestrutura de observabilidade
desproporcional à escala atual
```

A observabilidade deve evoluir sobre a arquitetura existente, preservando o PostgreSQL como estado operacional principal e mantendo o domínio desacoplado de tecnologias específicas de logging, métricas ou dashboards.

## Decisão

### 1. Observabilidade será uma responsabilidade explícita, mas não pertencente ao domínio comercial

A observabilidade deverá acompanhar o fluxo da aplicação sem introduzir dependências técnicas no domínio.

O domínio não conhecerá:

```text
logger
SLF4J
Logback
JSON logger
Prometheus
Grafana
OpenTelemetry
dashboard
alert manager
```

A dependência conceitual deverá permanecer:

```text
domain
   ↑
application
   ↑
infrastructure
```

A instrumentação concreta pertence principalmente à infraestrutura e aos pontos de composição.

Quando a camada `application` precisar emitir uma informação operacional explícita, isso deverá ocorrer por contrato próprio, sem dependência de uma biblioteca concreta.

---

### 2. PostgreSQL continuará sendo a fonte operacional durável

Logs estruturados e métricas complementam o estado persistido.

Eles não substituem fatos persistidos como:

```text
ProcessingRun
DealCandidate
ProcessingJob
OfferSnapshot
DealEvaluation
MomentumAudit
Publication
```

Sempre que uma informação representar fato necessário para:

```text
auditoria
correlação histórica
reprodução operacional
consulta posterior
```

ela deverá possuir representação persistente apropriada.

Logs poderão ser descartados, rotacionados ou encaminhados para outra infraestrutura sem destruir a cadeia auditável do sistema.

Princípio:

```text
estado persistido
    =
fonte durável

logs e métricas
    =
visões operacionais derivadas e complementares
```

---

### 3. Correlação será baseada em identidades persistentes

A cadeia operacional desejada será:

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

A correlação não dependerá exclusivamente de texto, timestamp ou ASIN.

IDs persistentes serão usados sempre que disponíveis.

Identificadores relevantes incluem:

```text
processingRunId
processingJobId
dealCandidateId
offerSnapshotId
dealEvaluationId
publicationId
```

O ASIN será também propagado como atributo operacional útil, mas não substituirá as identidades persistentes.

Isso é necessário porque:

```text
mesmo ASIN
    ↓
pode possuir múltiplos snapshots
    ↓
em diferentes momentos
    ↓
e em diferentes execuções
```

---

### 4. A FASE 16 completará a correlação entre DealCandidate e OfferSnapshot

A arquitetura atual preserva explicitamente:

```text
ProcessingRun
    ↓
DealCandidate
```

e:

```text
OfferSnapshot
    ↓
DealEvaluation
    ↓
Publication
```

A FASE 16 deverá permitir navegar de forma determinística entre essas duas partes.

Será introduzido vínculo persistente apropriado entre:

```text
DealCandidate
    ↓
OfferSnapshot
```

sem alterar migrations já aplicadas.

A mudança deverá utilizar nova migration posterior à V19.

A associação deverá preservar cenários de:

```text
retry
reentrada
idempotência
reutilização de snapshot já persistido
```

e não deverá impor unicidade artificial incompatível com esses cenários.

---

### 5. Logs estruturados utilizarão eventos com campos estáveis

Os logs operacionais deverão possuir formato estruturado.

Um evento poderá conter, quando aplicável:

```text
timestamp
level
event
component
operation

processingRunId
processingJobId
jobType

dealCandidateId
offerSnapshotId
dealEvaluationId
publicationId

asin

integration
outcome
durationMs

failureOrigin
failureType
errorCode
```

Campos ausentes deverão permanecer ausentes ou nulos conforme o formato escolhido.

Valores inexistentes não deverão ser inventados.

Mensagens humanas poderão acompanhar o evento, mas a interpretação operacional não deverá depender exclusivamente do texto livre.

---

### 6. Eventos possuirão nomes estáveis

Eventos estruturados deverão utilizar identificadores explícitos.

Exemplos conceituais:

```text
processing.run.started
processing.run.completed
processing.run.failed

processing.job.claimed
processing.job.succeeded
processing.job.retry_scheduled
processing.job.dead

amazon.deals.request.started
amazon.deals.request.succeeded
amazon.deals.request.failed

amazon.product.request.started
amazon.product.request.succeeded
amazon.product.request.failed

deal.candidate.persisted
offer.snapshot.persisted
deal.evaluation.persisted
publication.generated
```

O conjunto definitivo será introduzido incrementalmente.

Não será necessário emitir eventos redundantes em todo método da aplicação.

A instrumentação deverá priorizar fronteiras operacionais relevantes.

---

### 7. Métricas operacionais deverão ser derivadas de fatos bem definidos

A FASE 16 deverá permitir obter pelo menos:

```text
quantidade coletada
quantidade enriquecida
quantidade avaliada
quantidade elegível
quantidade rejeitada
quantidade de publicações geradas

jobs por estado
jobs por tipo
tentativas
retries
jobs DEAD

latência por integração
falhas por integração
```

Quando a informação puder ser calculada com segurança a partir de fatos persistidos, ela não deverá ser duplicada desnecessariamente em tabelas de contadores.

Por exemplo:

```text
COUNT(deal_candidate)
```

pode representar candidatos persistidos de uma execução.

Da mesma forma:

```text
ProcessingJob.status
ProcessingJob.attemptCount
ProcessingJob.lastFailureType
```

já fornecem fatos relevantes para observabilidade da fila.

Novas estruturas persistentes serão adicionadas somente quando os fatos atuais forem insuficientes.

---

### 8. Métrica de publicação distinguirá geração de entrega

Na FASE 16 ainda não existe mecanismo de entrega real para canais.

A FASE 13 produz:

```text
Publication
status = CREATED
```

Isso significa:

```text
conteúdo gerado e persistido
```

e não:

```text
mensagem efetivamente entregue ao público
```

Portanto, a métrica atual deverá utilizar semântica equivalente a:

```text
publicationsGenerated
```

A FASE 16 não deverá apresentar essa quantidade como:

```text
publicationsDelivered
publicationsSent
publicationsSuccessfullyPublished
```

Esses conceitos dependerão do modelo de outbox, tentativa e canal das fases posteriores.

---

### 9. Falhas externas e internas terão classificação operacional distinta

A FASE 16 deverá permitir distinguir origem de falha.

Conceitualmente:

```text
EXTERNAL
INTERNAL
```

Falhas externas incluem situações equivalentes a:

```text
timeout HTTP
conexão externa
HTTP 429
HTTP 5xx
falha de comunicação com Amazon
```

Falhas internas incluem situações equivalentes a:

```text
parsing inesperado
violação de estado interno
persistência
SQL
erro de aplicação
```

Essa classificação operacional não substitui a classificação existente de retry:

```text
TRANSIENT
PERMANENT
```

Os conceitos respondem perguntas diferentes.

Exemplo:

```text
origem = EXTERNAL
tipo de retry = TRANSIENT
```

ou:

```text
origem = INTERNAL
tipo de retry = PERMANENT
```

A solução deverá preservar essa separação.

---

### 10. Latência de integração será medida na fronteira externa

A latência de uma integração deverá ser medida onde a chamada externa realmente ocorre.

Exemplos:

```text
HTTP de coleta da página de promoções
HTTP da página individual de produto
```

Não será correto chamar de:

```text
amazonLatency
```

o tempo total de:

```text
HTTP
+
parsing
+
persistência
+
avaliação
```

Cada métrica deverá possuir significado operacional explícito.

A duração será registrada em unidade estável, inicialmente:

```text
durationMs
```

---

### 11. Dados de integração poderão exigir persistência específica

Logs não são suficientes para todas as necessidades históricas de latência e falha.

Quando necessário para consultas operacionais duráveis, será criado modelo persistente específico para observações de integração.

Esse modelo deverá registrar somente fatos operacionais necessários, como:

```text
integração
operação
instante
duração
resultado
status externo quando disponível
classificação da falha
códigos de erro
identidades de correlação
```

O modelo não deverá duplicar desnecessariamente:

```text
HTML
objetos completos de domínio
texto completo de publicação
snapshot comercial
```

A criação dessa estrutura deverá ocorrer somente por nova migration.

---

### 12. Auditoria comercial existente não será duplicada

A aplicação já persiste dados de auditoria relacionados a:

```text
eligibilidade
filtros
score
momentum
versões utilizadas
```

A FASE 16 não criará uma segunda representação dessas decisões.

A observabilidade deverá consultar os resultados persistidos existentes.

A interface não recalculará:

```text
eligibilidade
filtros
score
ranking
momentum
apresentação comercial
template
```

Esse princípio permanece alinhado à ADR-0009.

---

### 13. A visão operacional da execução será fornecida pela application

Será criado read side específico para observabilidade.

A apresentação não fará SQL diretamente.

Fluxo:

```text
CLI
  ↓
use case
  ↓
observability query port
  ↓
adapter JDBC
  ↓
PostgreSQL
```

A primeira visão deverá ser capaz de responder, a partir de uma `ProcessingRun`, informações equivalentes a:

```text
run

candidatos coletados
snapshots relacionados
avaliações relacionadas
publicações geradas

jobs por tipo
jobs por estado
retries
jobs DEAD

falhas
latências conhecidas

linhagem dos principais objetos
```

O nome concreto dos read models poderá ser refinado durante a implementação.

---

### 14. O dashboard inicial permanecerá na interface operacional existente

A FASE 16 não introduzirá aplicação web somente para satisfazer o requisito de dashboard operacional.

A primeira visão operacional será disponibilizada na CLI existente.

Exemplo conceitual:

```text
runs show <run-id>
```

ou comando equivalente claramente associado à observabilidade.

Isso preserva:

```text
presentation
      ↓
application
      ↓
ports
      ↓
infrastructure
```

Uma futura interface web poderá consumir os mesmos contratos.

---

### 15. Alertas serão inicialmente regras consultáveis

A FASE 16 deverá definir condições operacionais capazes de representar alertas.

Exemplos iniciais:

```text
falhas externas recorrentes
quantidade elevada de jobs DEAD
coleta sem candidatos
queda anormal do volume coletado
```

Entretanto, a FASE 16 não deverá implementar:

```text
scheduler permanente
loop infinito
cron interno
envio periódico automático de alerta
```

A execução periódica dessas verificações pertence à FASE 17.

Na FASE 16, os alertas deverão ser:

```text
calculáveis
testáveis
consultáveis
```

sem antecipar o mecanismo contínuo de execução.

---

### 16. Mudança suspeita de coleta será baseada em dados observados

Um alerta de mudança suspeita não deverá utilizar uma constante arbitrária sem semântica documentada.

A implementação inicial deverá utilizar fatos persistidos e uma regra explícita.

Exemplos possíveis incluem:

```text
execução concluída sem candidatos

redução de volume em relação a execuções anteriores
acima de um limite configurável
```

A estratégia exata deverá ser definida durante o bloco específico de alertas e coberta por testes.

A ausência de ofertas não será automaticamente interpretada como erro externo sem evidência adicional.

---

### 17. Não será introduzido stack externo de métricas sem necessidade demonstrada

A FASE 16 não exige automaticamente:

```text
Prometheus
Grafana
OpenTelemetry Collector
Elastic Stack
Loki
Datadog
New Relic
```

Essas ferramentas poderão ser introduzidas futuramente quando houver necessidade operacional comprovada.

A implementação inicial deverá priorizar:

```text
contratos estáveis
dados estruturados
correlação
testabilidade
baixo acoplamento
```

de forma que uma integração posterior com ferramentas externas não exija alterar regras de domínio.

---

### 18. Logging será tratado como infraestrutura substituível

A aplicação não deverá espalhar dependência de uma implementação concreta de logging por contratos de domínio.

A infraestrutura poderá utilizar uma fachada apropriada de logging e formato estruturado.

A escolha da implementação concreta deverá respeitar:

```text
baixo acoplamento
configuração externa
execução local
execução em CI
possibilidade de evolução futura
```

Logs não deverão conter segredos.

Dados como:

```text
DB_PASSWORD
AMAZON_ASSOCIATE_TAG
cookies
tokens
credenciais
```

não poderão ser emitidos.

---

### 19. Cardinalidade de métricas deverá ser controlada

Identificadores de alta cardinalidade são úteis para logs e consultas de correlação, mas não devem ser transformados indiscriminadamente em dimensões de métricas agregadas.

Portanto:

```text
runId
jobId
ASIN
publicationId
```

podem aparecer em eventos estruturados e consultas detalhadas.

Métricas agregadas deverão preferir dimensões controladas, como:

```text
jobType
status
integration
operation
failureOrigin
failureType
outcome
```

Esse princípio mantém a arquitetura compatível com futuros sistemas especializados de métricas.

---

### 20. A FASE 16 não implementará scheduler

A observabilidade deverá funcionar durante:

```text
execuções manuais
testes
workers executados explicitamente
```

A FASE 16 não adicionará:

```text
execução periódica
frequência de coleta
janela temporal de execução
pause/resume de scheduler
prevenção global de ciclos agendados concorrentes
```

Esses assuntos permanecem na FASE 17.

---

### 21. A FASE 16 não implementará outbox ou canais

Também permanecem fora desta fase:

```text
PublicationChannel
outbox
worker de entrega
Telegram
WhatsApp
confirmação de entrega
retry de canal
```

A observabilidade deve ser preparada para receber esses fatos futuramente, mas não deverá inventá-los antes de existirem.

---

## Modelo conceitual

A observabilidade deverá permitir navegar pelo menos por:

```text
ProcessingRun
      │
      ├── ProcessingJob: COLLECT_DEALS
      │
      ▼
DealCandidate
      │
      ├── ProcessingJob: ENRICH_DEAL
      │
      ▼
OfferSnapshot
      │
      ├── ProcessingJob: EVALUATE_DEAL
      │
      ▼
DealEvaluation
      │
      ▼
Publication
```

Em paralelo:

```text
integrações externas
      ↓
observações de latência / resultado
      ↓
correlação persistente
```

E:

```text
estado persistido
      ↓
read side operacional
      ↓
métricas / diagnóstico / alertas
      ↓
CLI operacional
```

---

## Consequências positivas

A decisão permite:

- diagnosticar uma execução sem planilhas;
- correlacionar os principais objetos do pipeline;
- diferenciar falhas externas e internas;
- medir integrações externas;
- reaproveitar fatos já persistidos;
- evitar duplicação de auditoria comercial;
- manter a observabilidade fora do domínio;
- evoluir futuramente para sistemas especializados de métricas;
- preservar a CLI como primeiro dashboard operacional;
- preparar a FASE 17 sem antecipar scheduler;
- preparar fases de canais sem inventar entrega antes da hora.

---

## Consequências e cuidados

A implementação exigirá:

- nova correlação persistente entre candidato e snapshot;
- novos read models operacionais;
- instrumentação explícita das fronteiras externas;
- definição cuidadosa dos eventos estruturados;
- classificação independente de origem de falha;
- testes para evitar métricas com semântica incorreta;
- cuidado com cardinalidade;
- cuidado para não registrar segredos;
- novas migrations quando novos fatos persistentes forem necessários.

Também será necessário evitar:

```text
duplicar fatos comerciais
recalcular decisões históricas
usar texto de log como contrato
espalhar SQL pela apresentação
criar dashboard web prematuramente
introduzir scheduler na FASE 16
```

---

## Fora do escopo

Não pertencem à FASE 16:

- scheduler definitivo;
- execução periódica contínua;
- quota de publicação;
- cooldown;
- política de recorrência;
- cadência;
- pausa/retomada do scheduler;
- outbox;
- canais;
- Telegram;
- WhatsApp;
- confirmação real de entrega;
- autenticação de interface web;
- dashboard analítico sofisticado;
- infraestrutura distribuída de métricas sem necessidade comprovada;
- alteração de filtros;
- alteração de score;
- alteração de momentum;
- alteração de template;
- alteração da política comercial de apresentação.

---

## Critérios de aceite

A FASE 16 somente será considerada concluída quando:

1. for possível correlacionar uma execução aos principais objetos produzidos;
2. a correlação não depender somente de ASIN ou texto de log;
3. logs operacionais relevantes forem estruturados;
4. integrações externas possuírem latência observável;
5. falhas externas puderem ser diferenciadas de falhas internas;
6. for possível obter quantidade coletada;
7. for possível obter quantidade enriquecida;
8. for possível obter quantidade avaliada;
9. for possível distinguir avaliações elegíveis e rejeitadas;
10. for possível obter quantidade de publicações geradas;
11. publicação gerada não for apresentada como entrega externa concluída;
12. retries e jobs `DEAD` forem observáveis;
13. decisões comerciais existentes não forem recalculadas pela observabilidade;
14. alertas operacionais iniciais forem calculáveis e testáveis;
15. uma visão de execução estiver disponível pela interface operacional;
16. a apresentação não acessar JDBC diretamente;
17. o domínio permanecer sem dependência de logging ou métricas;
18. nenhuma migration histórica for alterada;
19. nenhuma responsabilidade de scheduler for antecipada;
20. a suíte completa e o CI remoto permanecerem verdes.

---

## Resultado esperado

Ao final da FASE 16, um operador deverá conseguir partir de uma execução:

```text
ProcessingRun #N
```

e responder:

```text
quando começou?
quando terminou?
qual foi o resultado?

quantos candidatos foram encontrados?
quais ASINs participaram?

quais snapshots foram produzidos?
quais avaliações foram produzidas?
quantas foram elegíveis?
quantas foram rejeitadas?

quais jobs executaram?
quais repetiram?
quais morreram?

houve falha externa?
houve falha interna?

qual integração falhou?
quanto tempo a integração levou?

quais Publications foram geradas?
```

sem depender de:

```text
planilha
consulta SQL manual
reexecução das regras de negócio
interpretação informal de texto de log
```

Princípio final:

```text
A observabilidade explica o que o sistema fez.

Ela não redefine o que o sistema decidiu.
```
