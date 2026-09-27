# Rasping Amazon

Sistema em desenvolvimento para **coleta, normalização, enriquecimento, validação, filtragem, avaliação, score, ranking, histórico, evolução, momentum, orquestração durável, geração auditável de publicações e operação por interface Java** de ofertas da Amazon Brasil.

O projeto prioriza:

- separação de responsabilidades;
- rastreabilidade;
- idempotência;
- auditabilidade;
- evolução incremental;
- persistência durável;
- escalabilidade orientada por necessidade real.

> **Estado atual: FASE 16 concluída localmente.**
>
> O sistema possui pipeline de decisão persistido, orquestração assíncrona durável em PostgreSQL, retry/lease/idempotência, geração de publicação versionada, uma **interface operacional Java em CLI, não bloqueante**, suíte PostgreSQL autocontida e uma camada de **observabilidade operacional correlacionada por execução**, com logs estruturados, métricas persistidas de integrações, detalhe de `ProcessingRun`, alertas sob consulta e prova real contra a fonte Amazon.
>
> Gate local final da FASE 16:
>
> ```text
> Tests run: 931
> Failures: 0
> Errors: 0
> Skipped: 0
>
> BUILD SUCCESS
> ```
>
> A prova real da FASE 16 concluiu com `SUCCESS`, HTTP 200, persistência visível por segunda conexão PostgreSQL e métricas exibidas por `runs show`.
>
> O catálogo Flyway alcança **V22**.
>
> O encerramento remoto da FASE 16 ainda depende de:
>
> ```text
> documentação final
> +
> push da branch
> +
> Pull Request
> +
> CI remoto verde
> ```

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
geração de Publication
  ↓
seleção operacional futura
  ↓
outbox / entrega futura
  ↓
canais
```

permaneçam desacoplados.

A interface operacional observa esse fluxo por fora:

```text
                     ┌──────────────────────┐
                     │ Interface operacional│
                     │ consulta / diagnóstico│
                     └──────────┬───────────┘
                                │
                                ▼
coleta → avaliação → publicação → entrega futura
```

Princípio da FASE 14:

```text
A interface observa e administra o pipeline.
A interface não autoriza o pipeline a funcionar.
```

Princípio da FASE 16:

```text
Observabilidade explica fatos persistidos.
Observabilidade não redefine decisões.
```

A ordem das fases deve ser preservada e responsabilidades futuras não devem ser antecipadas sem decisão explícita.

---

## 2. Estado atual

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
| FASE 16 | Observabilidade | CONCLUÍDA LOCALMENTE |
| FASE 17 | Agendamento e execução contínua | PRÓXIMA APÓS GATE REMOTO |
| FASE 18 | Contrato de canais / outbox | PLANEJADA |
| FASE 19+ | Canais, hardening e escala | PLANEJADA |

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
- `infrastructure`: PostgreSQL, Flyway, JDBC, configuração, HTTP, parsing específico da Amazon, bootstrap e composition roots;
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
Telegram
WhatsApp
```

A apresentação não conhece:

```text
Connection
DriverManager
SQL
adapters JDBC
HTML Amazon
collector
parser
```

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
- Exec Maven Plugin para execução local da CLI operacional
- Playwright apenas no profile de diagnóstico externo

---

## 5. Build

Use o Maven Wrapper versionado.

### Windows

```powershell
.\mvnw.cmd clean test
```

### Linux/macOS/CI

```bash
./mvnw clean test
```

Gate local final da FASE 16:

```text
Tests run: 931
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

A probe real da FASE 16 é explícita e não participa automaticamente da suíte hermética padrão.

O gate normal não executa automaticamente a CLI.

---

## 6. Configuração e segredos

A configuração principal da aplicação permanece baseada em ambiente.

Variáveis documentadas:

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

- `DB_PASSWORD` é obrigatório;
- `AMAZON_ASSOCIATE_TAG` é usado somente no fluxo de publicação;
- `.env` local não é versionado;
- `.env.example` documenta o formato sem conter segredos reais;
- thresholds operacionais da FASE 16 são carregados por configuração explícita, sem defaults de negócio arbitrários.

Exemplo:

```dotenv
DB_HOST=localhost
DB_PORT=5432
DB_NAME=rasping_amazon
DB_USER=rasping
DB_PASSWORD=CHANGE_ME

AMAZON_ASSOCIATE_TAG=CHANGE_ME
```

---

## 7. Modelo de oferta

`OfferSnapshot` representa uma observação temporal da oferta.

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

- `basisPrice` não é `previousPrice`;
- ausência de informação não é convertida em zero;
- preços Pix/NuPay não são inferidos;
- condições comerciais são persistidas de forma estruturada.

---

## 8. Condições comerciais

Tipos:

```text
PaymentConditionType.CASH
PaymentConditionType.CREDIT_INSTALLMENT
```

Métodos reconhecidos incluem:

```text
PIX
NUPAY_ADDITIONAL_LIMIT
CREDIT_CARD
```

Uma condição pode registrar:

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
política futura de seleção
```

---

## 10. Filtros comerciais

Perfil ativo após V16:

```text
COMMERCIAL_FILTER_V2
```

A V2 utiliza desconto sobre preço-base como critério comercial principal de desconto.

Configuração ativada pela migration V16:

```text
minBasisDiscountPercentage = 20.0000
minRating = 4.30
minReviewCount = 100
```

A versão anterior:

```text
COMMERCIAL_FILTER_V1
```

permanece histórica.

O sistema distingue dado ausente de dado presente abaixo do limite.

Mudanças semânticas de filtros são versionadas.

---

## 11. Score e ranking

Perfil ativo após V16:

```text
SCORE_V2
```

Pesos configurados:

```text
SOLD_PERCENTAGE = 30
BASIS_DISCOUNT  = 25
RATING          = 20
REVIEW_COUNT    = 15
```

O limiar de `reviewCount` para pontuação cheia permanece:

```text
1000
```

Características:

- score reproduzível;
- fatores explicáveis;
- fatores persistidos;
- soma das contribuições validada;
- ranking determinístico;
- versão histórica preservada.

`SCORE_V1` permanece disponível como versão histórica.

O score não deve absorver penalidade de recorrência de publicação.

A prioridade operacional futura de publicação é conceito separado.

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
- não altera elegibilidade estrutural;
- não altera o significado do score;
- pode ser indisponível quando não há base histórica suficiente;
- possui auditoria própria;
- é apresentado pela interface a partir do estado persistido.

---

## 13. Orquestração durável — FASE 12

A FASE 12 introduziu processamento assíncrono baseado em PostgreSQL.

Entidades principais:

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

A fila utiliza PostgreSQL e suporta:

- jobs persistidos;
- claim concorrente;
- `FOR UPDATE SKIP LOCKED`;
- retry;
- backoff;
- leases;
- recuperação de leases expirados;
- múltiplos workers;
- idempotência por etapa;
- falhas transitórias e permanentes;
- estados terminais.

Estados de job:

```text
PENDING
RUNNING
RETRY_WAIT
SUCCEEDED
DEAD
```

Tipos atuais:

```text
COLLECT_DEALS
ENRICH_DEAL
EVALUATE_DEAL
```

A fila da FASE 12 não possui um tipo artificial de publicação.

---

## 14. Geração de publicação — FASE 13

A FASE 13 transforma uma `DealEvaluation` persistida em uma `Publication` auditável.

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
seleciona quota de publicação
aplica cooldown
envia para canais
```

---

## 15. Dados de publicação

`PublicationData` é construído a partir de dados persistidos.

A cadeia auditável permanece:

```text
Publication
    ↓
DealEvaluation
    ↓
OfferSnapshot
    ↓
Product
```

A publicação não duplica todos os dados históricos.

O gerador recebe uma avaliação já selecionada.

Política futura de seleção não pertence ao `PublicationGenerator`.

---

## 16. Política comercial de apresentação

Contrato:

```text
CommercialPresentationPolicy
```

Implementação atual:

```text
AmazonCommercialPresentationV1
```

Versão:

```text
AMAZON_COMMERCIAL_PRESENTATION_V1
```

A política de apresentação não decide:

```text
elegibilidade
filtros
score
ranking
recorrência
quota
cadência
```

Ela somente define como fatos já aceitos serão apresentados.

---

## 17. Template de publicação

Contrato:

```text
PublicationTemplate
```

Implementação:

```text
AmazonPublicationV1
```

Versão:

```text
AMAZON_PUBLICATION_V1
```

O template recebe dados já preparados e não conhece:

```text
SQL
JDBC
score
filtros
geração de URL de afiliado
Telegram
WhatsApp
```

Exemplo conceitual:

```text
Produto exemplo
Preço atual: R$ 99,90
Link patrocinado: https://www.amazon.com.br/dp/ASIN?tag=...
```

---

## 18. Link de associado

Contrato:

```text
AffiliateLinkGenerator
```

Implementação usada para novas publicações:

```text
AmazonAffiliateLinkGeneratorV2
```

A V2 preserva paths já percent-encoded sem dupla codificação.

A versão anterior permanece disponível para reprodução histórica:

```text
AmazonAffiliateLinkGeneratorV1
```

Fluxo:

```text
Product.productUrl
        ↓
AffiliateLinkGenerator
        ↓
AffiliateLink
```

O associate tag é fornecido por configuração externa.

---

## 19. `Publication`

`Publication` preserva:

```text
DealEvaluation
templateVersion
commercialPresentationVersion
affiliateLinkVersion
generatedText
affiliateUrl
status
createdAt
```

Estados existentes:

```text
CREATED
READY
PUBLISHED
FAILED
```

A geração da FASE 13 cria inicialmente:

```text
CREATED
```

Semântica arquitetural adotada pela ADR-0009:

```text
CREATED
Publication gerada e persistida.

READY
Publication liberada pelas regras automáticas aplicáveis
para prosseguir ao mecanismo de despacho futuro.

PUBLISHED
Entrega confirmada pelo mecanismo responsável pelo canal.

FAILED
Falha posterior tratável conforme as regras da etapa responsável.
```

Esses estados não representam etapas obrigatórias de aprovação humana.

A FASE 16 não implementa scheduler, outbox ou entrega por canal.

---

## 20. Idempotência da publicação

Identidade lógica:

```text
deal_evaluation_id
+
template_version
+
commercial_presentation_version
+
affiliate_link_version
```

Proteção no PostgreSQL:

```text
UNIQUE (...)
```

Persistência:

```text
INSERT
ON CONFLICT DO NOTHING
RETURNING id
```

Reentrada da mesma geração:

```text
mesma identidade
      ↓
mesma Publication
      ↓
sem sobrescrever o fato histórico original
```

---

## 21. Composition root de publicação

Composition root:

```text
AmazonPublicationComposition
```

Composição atual:

```text
JdbcOfferSnapshotEvaluationLoadAdapter
        ↓
JdbcPublicationDataQueryAdapter
        ↓
AmazonCommercialPresentationV1
        ↓
AmazonAffiliateLinkGeneratorV2
        ↓
AmazonPublicationV1
        ↓
PublicationJdbcRepository
        ↓
PublicationGenerator
```

A composição apenas monta dependências.

Ela não executa regras de seleção, recorrência, quota ou canal.

---

## 22. Interface operacional — FASE 14

A FASE 14 implementou a primeira apresentação operacional do projeto como CLI Java.

Objetivo:

```text
observação
consulta histórica
diagnóstico
administração explícita
```

Sem tornar o operador parte obrigatória do pipeline.

A CLI opera sobre casos de uso e read models da camada `application`.

Não existe SQL na apresentação.

Não existe recomputação de decisão histórica na apresentação.

---

## 23. Read side operacional de avaliações

Pacote:

```text
application/operation/evaluation
```

Principais contratos:

```text
DealEvaluationCursor
DealEvaluationPage
DealEvaluationSearchCriteria
DealEvaluationSummary
DealEvaluationDetail
OperationalEvaluationRuleResult
OperationalScoreFactorResult
OperationalMomentumAudit
ListDealEvaluationsUseCase
GetDealEvaluationDetailUseCase
DealEvaluationOperationalQueryPort
DealEvaluationOperationalDetailQueryPort
```

Listagem disponível:

```text
evaluations list
```

Filtros incluem:

```text
eligible
ASIN
minScore
maxScore
evaluatedFrom
evaluatedUntil
cursor
limit
```

Ordenação keyset:

```text
evaluatedAt DESC
evaluationId DESC
```

Detalhe:

```text
evaluations show <evaluation-id>
```

O detalhe apresenta fatos persistidos de:

```text
oferta
elegibilidade
versões
rule results
score factors
momentum audit
```

sem executar novamente os motores de decisão.

---

## 24. Read side operacional de runs e jobs

Runs:

```text
runs list
runs show <run-id>
```

Ordenação:

```text
requestedAt DESC
runId DESC
```

O detalhe de uma execução, ampliado na FASE 16, expõe:

```text
RUN
PIPELINE
JOBS
INTEGRATIONS
```

Jobs:

```text
jobs list
```

Filtros incluem:

```text
type
status
lastFailureType
processingRunId
dealCandidateId
offerSnapshotId
createdFrom
createdUntil
cursor
limit
```

Ordenação:

```text
createdAt DESC
jobId DESC
```

O filtro por `processingRunId` representa o vínculo direto persistido no job.

A FASE 16 ampliou a correlação operacional sem transformar ASIN em identidade de execução.

A interface não adiciona retry arbitrário nem pause/resume falso à CLI.

---

## 25. Read side operacional de publicações

Comandos:

```text
publications list
publications show <publication-id>
```

Filtros da listagem incluem:

```text
status
ASIN
dealEvaluationId
createdFrom
createdUntil
cursor
limit
```

Ordenação:

```text
createdAt DESC
publicationId DESC
```

O resumo evita carregar desnecessariamente:

```text
generatedText
affiliateUrl
```

O detalhe apresenta esses campos somente quando solicitado.

---

## 26. Composition root operacional

Composition root:

```text
OperationalInterfaceComposition
```

Casos de uso expostos:

```text
listDealEvaluations()
getDealEvaluationDetail()
listProcessingRuns()
getProcessingRunDetail()
listProcessingJobs()
listPublications()
getPublicationDetail()
getOperationalAlerts()
```

A composição:

- abre a conexão;
- compartilha a conexão entre os adapters operacionais;
- implementa `AutoCloseable`;
- é responsável pelo fechamento da conexão;
- não contém regra comercial;
- não executa score;
- não executa momentum;
- não faz retry;
- não implementa scheduler;
- não altera status de `Publication`.

---

## 27. CLI operacional

Pacote:

```text
com.raspingamazon.presentation.cli
```

Núcleo:

```text
CliExitCode
CliUsageException
CliCommandHandler
CliText
CliValueParser
OperationalCliUsage
OperationalCli
OperationalCliFactory
```

Recursos disponíveis:

```text
evaluations list
evaluations show <evaluation-id>

runs list
runs show <run-id>

jobs list

publications list
publications show <publication-id>

alerts list
```

Códigos de saída:

```text
0 SUCCESS
1 OPERATIONAL_ERROR
2 USAGE_ERROR
3 NOT_FOUND
```

`OperationalCli` retorna o código de saída.

Ele não chama `System.exit()`.

---

## 28. Bootstrap e entrypoint

Pacote:

```text
com.raspingamazon.infrastructure.bootstrap
```

Componentes:

```text
OperationalCliBootstrap
OperationalCliMain
```

Fluxo:

```text
OperationalCliMain
        ↓
OperationalCliBootstrap
        ↓
OperationalInterfaceComposition
        ↓
application use cases
        ↓
OperationalCliFactory
        ↓
OperationalCli
```

Ajuda global e comando top-level inválido não precisam abrir PostgreSQL.

Comandos operacionais reais abrem a composição.

`System.exit()` fica restrito ao entrypoint de processo.

---

## 29. Executando a CLI

O projeto usa `exec-maven-plugin` somente quando solicitado explicitamente.

### Ajuda

```powershell
.\mvnw.cmd compile exec:java "-Dexec.args=help"
```

### Avaliações

```powershell
.\mvnw.cmd compile exec:java "-Dexec.args=evaluations list --limit 5"
```

```powershell
.\mvnw.cmd compile exec:java "-Dexec.args=evaluations show 123"
```

### Runs

```powershell
.\mvnw.cmd compile exec:java "-Dexec.args=runs list --limit 5"
```

```powershell
.\mvnw.cmd compile exec:java "-Dexec.args=runs show 123"
```

### Jobs

```powershell
.\mvnw.cmd compile exec:java "-Dexec.args=jobs list --limit 5"
```

### Publicações

```powershell
.\mvnw.cmd compile exec:java "-Dexec.args=publications list --limit 5"
```

```powershell
.\mvnw.cmd compile exec:java "-Dexec.args=publications show 123"
```

### Alertas

```powershell
.\mvnw.cmd compile exec:java "-Dexec.args=alerts list"
```

O plugin não está associado a uma fase padrão do lifecycle.

`clean test` continua sendo apenas o gate de build/testes.

---

## 30. Smoke tests reais da FASE 14

Ajuda sem banco:

```text
mvn compile exec:java -Dexec.args=help

→ ajuda exibida
→ BUILD SUCCESS
```

Consulta real com PostgreSQL:

```text
mvn compile exec:java -Dexec.args=evaluations list --limit 5

→ composição aberta
→ PostgreSQL consultado
→ cabeçalho operacional exibido
→ BUILD SUCCESS
```

No smoke test observado, não havia linhas correspondentes no banco e apenas o cabeçalho foi retornado.

O caminho real foi validado:

```text
CLI
↓
bootstrap
↓
composition
↓
JDBC
↓
PostgreSQL
↓
application
↓
presentation
```

---

## 31. PostgreSQL e Flyway

Estado estrutural atual:

```text
PostgreSQL 18.6
catálogo de migrations até V22
```

Migrations:

```text
V1__initial_schema.sql
V2__commercial_offer_conditions.sql
V3__offer_evidence_provenance.sql
V4__deal_evaluation_versions.sql
V5__deal_evaluation_rule_results.sql
V6__offer_snapshot_idempotency.sql
V7__commercial_filter_profile.sql
V8__score_profile_and_factors.sql
V9__momentum_audit.sql
V10__historical_read_indexes.sql
V11__processing_orchestration.sql
V12__evaluation_processing_idempotency.sql
V13__deal_candidate_idempotency.sql
V14__publication_generation_audit.sql
V15__prepare_basis_discount_filter_and_score_v2.sql
V16__activate_basis_discount_filter_and_score_v2.sql
V17__operational_deal_evaluation_read.sql
V18__operational_processing_read_indexes.sql
V19__operational_publication_read_indexes.sql
V20__processing_observability_correlation.sql
V21__processing_run_observability_read_index.sql
V22__integration_observation.sql
```

Migrations diretamente atribuídas ao read side da FASE 14:

```text
V17
V18
V19
```

Migrations diretamente atribuídas à FASE 16:

```text
V20
V21
V22
```

V15 e V16 pertencem à evolução comercial anterior de filtro/score.

Regra de evolução:

```text
não editar migrations aplicadas
```

Toda mudança estrutural deve ser feita por nova migration versionada.

---

## 32. ADRs relevantes

Documentação arquitetural versionada em:

```text
docs/adr/
```

Catálogo atual:

```text
ADR-0001 — Semântica de filtros comerciais e apresentação de pagamentos
ADR-0002 — Semântica de score, ranking e explicabilidade
ADR-0003 — Semântica de histórico e momentum
ADR-0004 — Semântica de geração de publicação e link de associado
ADR-0005 — Semântica de desconto, preço-base e preço efetivo
ADR-0006 — Semântica de SCORE_V2 / desconto sobre preço-base
ADR-0007 — Fallback de rating/review na página de produto
ADR-0008 — Correção versionada do link de associado / percent-encoding
ADR-0009 — Interface operacional não bloqueante
ADR-0010 — Política de seleção, recorrência e cadência de publicações
ADR-0011 — Observabilidade, correlação e métricas operacionais
```

### ADR-0009

Status:

```text
ACEITA
```

Princípio:

```text
A interface observa e administra o pipeline.
A interface não autoriza o pipeline a funcionar.
```

Aprovação humana obrigatória não faz parte do caminho crítico.

### ADR-0010

Status:

```text
PROPOSTA
```

Relacionada principalmente a:

```text
FASE 17
FASE 18
```

Define fronteiras futuras para:

```text
PublicationSelectionPolicy
recorrência
cooldown
quota
cadência
escopo por canal/destino
auditabilidade da seleção
```

Essas regras não pertencem à CLI nem à observabilidade da FASE 16.

### ADR-0011

Status:

```text
ACEITA
```

Princípios:

```text
PostgreSQL = verdade operacional durável
logs estruturados = evidência complementar
correlação usa identidades reais
ASIN é atributo, não identidade de execução
EXTERNAL / INTERNAL é distinto de TRANSIENT / PERMANENT
observabilidade explica fatos sem recalcular decisões
alertas da FASE 16 não introduzem scheduler
```

---

## 33. Resultado consolidado da FASE 14

Documento:

```text
docs/phases/FASE_14_RESULTADO.md
```

Capacidades concluídas:

```text
evaluations list          OK
evaluations show <id>     OK
runs list                 OK
jobs list                 OK
publications list         OK
publications show <id>    OK
composition operacional   OK
factory da CLI            OK
bootstrap                 OK
entrypoint                OK
execução Maven            OK
smoke real PostgreSQL     OK
```

Gate:

```text
Tests run: 820
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

Critérios arquiteturais da ADR-0009:

```text
10 / 10 atendidos localmente
```

---

## 34. Qualidade integrada — FASE 15

A FASE 15 resolveu o débito de preparação de schema identificado ao final da FASE 14.

O problema observado era a existência de testes JDBC que dependiam de um PostgreSQL previamente migrado.

Foi criada infraestrutura compartilhada em:

```text
src/test/java/com/raspingamazon/testsupport/database/
```

Componentes:

```text
PostgresIntegrationTest
PostgresSchemaExtension
PostgresTestDatabase
```

Foram normalizadas:

```text
38 classes de integração PostgreSQL
```

Os testes comuns passaram a declarar:

```text
@PostgresIntegrationTest
```

A infraestrutura aplica as migrations reais da aplicação antes da execução dos consumidores PostgreSQL.

Os testes cujo objeto de verificação é a própria migration continuam executando `DatabaseMigration.migrate(...)` explicitamente.

A solução foi validada contra um database PostgreSQL completamente vazio:

```text
database vazio
    ↓
mvn clean test
    ↓
primeiro teste PostgreSQL
    ↓
Flyway
    ↓
restante da suíte
```

Resultado da FASE 15:

```text
Tests run: 820
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

O CI também deixou de executar `DatabaseMigrationTest` como bootstrap separado antes da suíte.

Commit principal:

```text
9895220 test: make PostgreSQL integration suite self-contained
```

Baseline integrado usado para iniciar a FASE 16:

```text
64e544c
Merge pull request #6 from veiocadan/feat/fase-15-qualidade-integrada
```

---

## 35. Observabilidade, auditoria e operação — FASE 16

A fonte primária da FASE 16 exige:

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

Critérios oficiais:

```text
é possível responder o que aconteceu em uma execução
sem depender de planilhas

falhas externas podem ser diferenciadas de falhas internas
```

Os dois critérios estão atendidos localmente.

### Correlação

Contexto operacional suportado:

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
ASIN = atributo
IDs persistidos = identidade operacional
```

A correlação não infere `ProcessingRun` pelo ASIN.

A migration V20 completou a cadeia persistida necessária para relacionar:

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

### Logs estruturados

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

A observabilidade do worker é best effort e não redefine o resultado de processamento.

### Observações de integração

Modelo:

```text
IntegrationObservation
```

Persistência:

```text
integration_observation
```

Adapter:

```text
JdbcIntegrationObservationPersistenceAdapter
```

Integrações instrumentadas:

```text
amazon-deals-http
amazon-product-page
```

A latência é medida na fronteira externa concreta.

Não são persistidos:

```text
segredos
payload HTTP completo
HTML
credenciais
```

### Origem da falha

Origem operacional:

```text
OperationalFailureOrigin.EXTERNAL
OperationalFailureOrigin.INTERNAL
```

Classificação de processamento preservada:

```text
ProcessingFailureType.TRANSIENT
ProcessingFailureType.PERMANENT
```

As dimensões são deliberadamente separadas.

### Detalhe operacional por run

Modelo:

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

`ProcessingRunPipelineMetrics`:

```text
collectedCandidates
enrichedCandidates
pendingEnrichmentCandidates
evaluations
eligibleEvaluations
rejectedEvaluations
publicationsGenerated
```

`ProcessingRunJobMetrics`:

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

`ProcessingRunIntegrationMetrics`:

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

`publicationsGenerated` significa Publication gerada/persistida, não entrega em canal.

### Dashboard operacional simples

Comando:

```text
runs show <run-id>
```

Seções:

```text
RUN
PIPELINE
JOBS
INTEGRATIONS
```

A seção `INTEGRATIONS` apresenta:

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

### Alertas

Tipos:

```text
REPEATED_EXTERNAL_FAILURES
DEAD_JOBS
ZERO_CANDIDATES
SUSPICIOUS_COLLECTION_DROP
```

Comando:

```text
alerts list
```

A avaliação é pontual.

Não existe na FASE 16:

```text
loop
polling
scheduler
daemon
sleep operacional
```

Agendamento pertence à FASE 17.

### Métricas deliberadamente não inventadas

O relatório de auditoria também cita:

```text
duração por etapa
taxa de descarte
taxa de UNKNOWN
```

Essas métricas não receberam fórmula arbitrária.

Motivos:

```text
duração por etapa
→ retries e múltiplas tentativas exigem semântica explícita

taxa de descarte
→ denominador precisa ser formalizado

taxa de UNKNOWN
→ população e etapa de referência precisam ser formalizadas
```

A FASE 16 expõe os fatos concretos necessários para evolução posterior.

### Prova real

Classe:

```text
Phase16ObservabilityRealSourceIT
```

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
PostgreSQL
    ↓
segunda conexão JDBC
    ↓
agregação por ProcessingRun
    ↓
runs show
```

Resultado observado em 27/09/2026:

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

JUnit da prova real:

```text
Tests run: 1
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

A probe externa é executada explicitamente e não participa da suíte hermética diária.

### Gate local

```text
Tests run: 931
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

Flyway:

```text
Successfully validated 22 migrations
Current version of schema "public": 22
Schema "public" is up to date
```

Diff da implementação da FASE 16 contra o baseline `64e544c`, antes da documentação final:

```text
76 files changed
21208 insertions(+)
288 deletions(-)
```

---

## 36. O que ainda não foi implementado

Para preservar a ordem do roadmap, permanecem fora do escopo após a FASE 16:

- aprovação humana obrigatória;
- `PublicationSelectionPolicy` efetivamente aplicada;
- cooldown de publicação;
- quota temporal;
- cadência de mensagens;
- scheduler definitivo;
- execução periódica contínua;
- prevenção de execuções concorrentes indesejadas da FASE 17;
- pause/resume real;
- outbox de canais;
- worker de entrega;
- `PublicationChannel`;
- Telegram;
- WhatsApp;
- envio automático;
- autenticação/autorização de uma futura interface web;
- dashboard analítico sofisticado;
- plataforma externa de métricas sem necessidade concreta;
- circuit breaker sem evidência operacional;
- hardening de produção;
- empacotamento final de distribuição da CLI;
- Excel/CSV como integração opcional;
- mecanismos adicionais de escala sem evidência operacional.

Esses itens pertencem às fases posteriores.

---

## 37. Fluxo vertical atual

```text
Amazon / deals
      ↓
coleta
      ↓
parser
      ↓
DealCandidate
      ↓
enrichment
      ↓
Product + OfferSnapshot + PaymentConditions + Evidence
      ↓
eligibility
      ↓
filters
      ↓
score
      ↓
history / momentum
      ↓
DealEvaluation
      ↓
PublicationGenerator
      ↓
Publication
```

Persistência:

```text
PostgreSQL
```

Orquestração:

```text
ProcessingRun
ProcessingJob
```

Operação:

```text
CLI Java
```

Observabilidade:

```text
logs estruturados
integration_observation
runs show
métricas por run
alerts list
```

Entrega automática em canais:

```text
AINDA NÃO
```

---

## 38. Roadmap simplificado

```text
FASES 0–8
Fundação + coleta + dados + validação
              ↓
FASE 8.5
Consolidação do núcleo
              ↓
FASES 9–11
Motor de decisão
              ↓
FASE 12
Orquestração durável
              ↓
FASE 13
Geração de publicação
              ↓
FASE 14
Interface operacional
              ↓
FASE 15
Qualidade integrada
              ↓
FASE 16
Observabilidade
              ↓
FASE 17
Agendamento e execução contínua
              ↓
FASE 18
Contrato de canais / outbox
              ↓
FASES 19+
Canais + hardening + escala
```

---

## 39. Princípios preservados

O projeto mantém:

- Java como núcleo;
- PostgreSQL como estado operacional principal;
- Flyway;
- JDBC explícito na infraestrutura;
- migrations imutáveis;
- domínio sem dependência de infraestrutura;
- presentation sem JDBC;
- adapters Amazon isolados;
- seller/delivery fail closed;
- dados ausentes não inventados;
- versões auditáveis;
- idempotência no banco;
- score semanticamente separado de recorrência;
- publicação desacoplada de canais;
- interface desacoplada do caminho crítico;
- observabilidade desacoplada das decisões comerciais;
- origem EXTERNAL/INTERNAL separada de TRANSIENT/PERMANENT;
- segredos fora do código;
- evolução incremental;
- escalabilidade guiada por necessidade real.

---

## 40. Comandos úteis

### Suíte completa

```powershell
.\mvnw.cmd clean test
```

### Ajuda da interface operacional

```powershell
.\mvnw.cmd compile exec:java "-Dexec.args=help"
```

### Consulta operacional simples

```powershell
.\mvnw.cmd compile exec:java "-Dexec.args=evaluations list --limit 5"
```

### Detalhe operacional de uma run

```powershell
.\mvnw.cmd compile exec:java "-Dexec.args=runs show 123"
```

### Alertas operacionais

```powershell
.\mvnw.cmd compile exec:java "-Dexec.args=alerts list"
```

### Ver status Git

```powershell
git status
```

### Validar whitespace/diff

```powershell
git diff --check
```

### Histórico recente

```powershell
git log --oneline -25
```

---

## 41. Estado consolidado

```text
Java 25
PostgreSQL 18.6
Flyway migrations até V22

Filtros ativos:
COMMERCIAL_FILTER_V2

Score ativo:
SCORE_V2

Momentum:
MOMENTUM_V1

Geração:
AMAZON_COMMERCIAL_PRESENTATION_V1
AMAZON_PUBLICATION_V1
AmazonAffiliateLinkGeneratorV2

FASE 12:
orquestração durável
retry
lease
idempotência
workers

FASE 13:
PublicationData
PublicationGenerator
PublicationJdbcRepository
AmazonPublicationComposition

FASE 14:
read models operacionais
evaluations list/show
runs list
jobs list
publications list/show
OperationalInterfaceComposition
OperationalCli
OperationalCliFactory
OperationalCliBootstrap
OperationalCliMain

FASE 15:
PostgresIntegrationTest
PostgresSchemaExtension
PostgresTestDatabase
38 testes PostgreSQL normalizados
schema autocontido por Flyway
CI sem bootstrap separado de DatabaseMigrationTest

FASE 16:
ADR-0011
logs estruturados
correlação durável
integration_observation
runs show
métricas de pipeline/jobs/integrações
falhas EXTERNAL/INTERNAL
alerts list
prova real Amazon + PostgreSQL + CLI

Gate local:
931 testes
0 falhas
0 erros
0 ignorados
BUILD SUCCESS

Flyway:
22 migrations
schema V22

Gate remoto:
PENDENTE

Próximo gate:
documentação final
+
push
+
Pull Request
+
CI remoto verde

Próxima fase após gate remoto:
FASE 17 — Agendamento e execução contínua
```

---

## 42. Commits da FASE 16

Implementação local da FASE 16:

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

## 43. Regra operacional atual

> **Coletar fatos sem inventá-los.**
>
> **Persistir antes de decidir.**
>
> **Versionar decisões auditáveis.**
>
> **Separar elegibilidade, filtros, score, histórico, apresentação, seleção e publicação.**
>
> **Usar PostgreSQL como defesa final de idempotência.**
>
> **Gerar publicação somente a partir de fatos persistidos.**
>
> **A interface observa e administra; não autoriza o pipeline a funcionar.**
>
> **Observabilidade explica fatos persistidos; não redefine decisões.**
>
> **Não antecipar scheduler, outbox ou canais antes das fases correspondentes.**
