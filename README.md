# Rasping Amazon

Sistema em desenvolvimento para **coleta, interpretação, enriquecimento, validação, filtragem, score, ranking, histórico, seleção e publicação automatizada de ofertas da Amazon Brasil**, com PostgreSQL como fonte principal de estado e foco em separação de responsabilidades, rastreabilidade, idempotência, auditabilidade e evolução incremental.

> **Estado atual:** FASE 19 concluída e integrada à `main`. A FASE 20 — Resiliência, recuperação e falhas de produção — está em andamento na branch `feat/fase-20-resiliencia-recuperacao`. A FASE 20-A formalizou o contrato de resiliência no ADR-0015, e a FASE 20-B iniciou a taxonomia operacional de falhas, separando semântica de retry, origem, categoria, ações e códigos diagnósticos. O schema PostgreSQL/Flyway permanece em **V34** enquanto não houver necessidade persistente comprovada para a evolução da FASE 20.
---

## 1. Objetivo

O Rasping Amazon não é apenas um raspador de ofertas.

O objetivo é manter um pipeline em que aquisição de dados, interpretação, regras comerciais, decisão operacional, geração de conteúdo e entrega externa permaneçam desacopladas.

Fluxo atual consolidado:

```text
Amazon
  ↓
coleta
  ↓
parsing / normalização
  ↓
enriquecimento
  ↓
persistência
  ↓
elegibilidade estrutural
  ↓
filtros comerciais
  ↓
score / ranking
  ↓
histórico / momentum
  ↓
seleção operacional
  ↓
PublicationGenerator
  ↓
Publication
  ↓
liberação automática
  ↓
outbox
  ↓
worker
  ↓
Telegram / WhatsApp
  ↓
PublicationAttempt
  ↓
auditoria / histórico
```

O sistema deve continuar operando sem depender de Excel, de edição manual no banco ou de uma interface de usuário no caminho crítico.

---

## 2. Fonte de verdade documental

A documentação do projeto segue esta hierarquia prática:

```text
ROADMAP
→ define a direção planejada das fases

ADRs aceitas
→ registram decisões arquiteturais vigentes e específicas

relatórios de fase
→ registram o que efetivamente foi implementado
```

Quando uma ADR posterior altera uma decisão específica prevista originalmente pelo roadmap ou por documentação histórica, a ADR vigente prevalece naquele assunto.

Relatórios históricos não são reescritos apenas para coincidir com decisões posteriores.

Documentos principais:

```text
docs/phases/ROADMAP_RASPING_AMAZON_V1.md
docs/adr/
docs/phases/FASE_*_RESULTADO.md
README.md
```

O README representa o estado operacional atual do projeto.

---

## 3. Estado das fases

| Fase | Descrição | Estado |
|---|---|---|
| FASE 0 | Levantamento da fonte e regras | CONCLUÍDA |
| FASE 0 v2 | Semântica comercial de preços e pagamento | CONCLUÍDA |
| FASE 1 | Fundação Java | CONCLUÍDA |
| FASE 2 | PostgreSQL, schema e migrations | CONCLUÍDA |
| FASE 2 v2 | Evolução comercial da persistência | CONCLUÍDA |
| FASE 3 | Domínio e contratos internos | CONCLUÍDA |
| FASE 3 v2 | Revisão comercial e estrutural | CONCLUÍDA |
| FASE 4 | Configuração e segredos | CONCLUÍDA |
| FASE 5 | Coleta da página de ofertas | CONCLUÍDA |
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
| FASE 17 | Agendamento e execução contínua | CONCLUÍDA |
| FASE 18 | Contrato de canais e outbox de publicação | CONCLUÍDA |
| FASE 19 | Telegram e WhatsApp | CONCLUÍDA |
| FASE 20 | Resiliência, recuperação e falhas de produção | EM ANDAMENTO |
| FASE 21 | Segurança, governança e fechamento da v1.0 | PLANEJADA |

A FASE 19 também contém alguns componentes antecipados da FASE 20. Eles permanecem no código, mas não significam que a FASE 20 esteja concluída.

---

## 4. Arquitetura

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

- `domain`: conceitos, invariantes e políticas de negócio sem dependência de infraestrutura;
- `application`: casos de uso, ports, coordenação, seleção e contratos operacionais;
- `infrastructure`: PostgreSQL, Flyway, JDBC, HTTP, Amazon, scheduler e adapters de canal;
- `presentation`: interface operacional, atualmente CLI.

Dependências devem apontar para dentro.

O domínio não conhece detalhes de HTML, JDBC, PostgreSQL, Flyway, Telegram ou WhatsApp.

O projeto permanece em um único módulo Maven enquanto não houver pressão arquitetural real para decomposição.

---

## 5. Stack

- Java 25
- Maven Wrapper
- JUnit 5
- PostgreSQL 18.6
- Flyway
- PostgreSQL JDBC
- Jackson
- Docker / Docker Compose
- GitHub Actions
- HTTP/JSON para integrações externas

O PostgreSQL continua sendo a fonte principal de estado durável.

---

## 6. Build

Use o Maven Wrapper versionado.

Windows:

```powershell
.\mvnw.cmd clean test
```

Linux/macOS/CI:

```bash
./mvnw clean test
```

Gate local final da FASE 19:

```text
Tests run: 1608
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

Também foi executado:

```text
git diff --check
```

sem problemas.

---

## 7. Configuração e segredos

Segredos não pertencem ao repositório.

O arquivo:

```text
.env
```

permanece local e não versionado.

O arquivo:

```text
.env.example
```

documenta o contrato de configuração sem conter credenciais reais.

Entre as configurações atualmente relevantes estão:

```text
DB_HOST
DB_PORT
DB_NAME
DB_USER
DB_PASSWORD

AMAZON_ASSOCIATE_TAG

TELEGRAM_ENABLED
TELEGRAM_BOT_TOKEN
TELEGRAM_API_BASE_URI
TELEGRAM_REQUEST_TIMEOUT
TELEGRAM_DESTINATION

WHATSAPP_MANUAL_ENABLED
WHATSAPP_MANUAL_TELEGRAM_DESTINATION

WHATSAPP_ENABLED
WHATSAPP_ACCESS_TOKEN
WHATSAPP_PHONE_NUMBER_ID
WHATSAPP_GRAPH_API_VERSION
WHATSAPP_GRAPH_API_BASE_URI
WHATSAPP_TEMPLATE_NAME
WHATSAPP_TEMPLATE_LANGUAGE
WHATSAPP_REQUEST_TIMEOUT

PUBLICATION_OUTBOX_MAX_ATTEMPTS
PUBLICATION_OUTBOX_RETRY_INITIAL_BACKOFF
PUBLICATION_OUTBOX_RETRY_MAX_BACKOFF

PUBLICATION_RATE_LIMIT_TELEGRAM_BOT_API_MIN_INTERVAL
PUBLICATION_RATE_LIMIT_WHATSAPP_CLOUD_API_MIN_INTERVAL

PUBLICATION_PRIMARY_CHANNEL
PUBLICATION_PRIMARY_DESTINATION

PUBLICATION_SELECTION_VERSION
PUBLICATION_SELECTION_HARD_COOLDOWN
PUBLICATION_SELECTION_PREFERRED_COOLDOWN

PUBLICATION_QUOTA_VERSION
PUBLICATION_QUOTA_MAX_PER_DAY
PUBLICATION_QUOTA_ZONE

PUBLICATION_CADENCE_VERSION
PUBLICATION_CADENCE_INTERVAL
PUBLICATION_CADENCE_WINDOW_START
PUBLICATION_CADENCE_WINDOW_END
PUBLICATION_CADENCE_ZONE
```

Tokens, senhas e identificadores sensíveis nunca devem aparecer em código, documentação operacional versionada ou logs.

---

## 8. Núcleo comercial

O núcleo de decisão preserva a separação:

```text
elegibilidade estrutural
        ↓
filtros comerciais
        ↓
score
        ↓
ranking
        ↓
histórico
        ↓
momentum
        ↓
seleção operacional
```

Esses conceitos não são intercambiáveis.

### Elegibilidade estrutural

Seller e delivery continuam sendo avaliados separadamente e com política fail closed.

Regras:

```text
SELLER_IS_AMAZON
DELIVERY_IS_AMAZON
```

Sem evidência suficiente de venda e entrega compatíveis com a política ativa, a oferta não prossegue como elegível.

### Filtros comerciais

A camada de filtros permanece versionada.

Entre os sinais avaliados estão:

```text
desconto à vista
rating
quantidade de avaliações
```

Ausência de dado permanece diferente de valor zero observado.

### Score

O score continua versionado, explicável e reproduzível.

A avaliação preserva fatores individuais em vez de persistir somente o total agregado.

### Histórico e momentum

`OfferSnapshot` permanece a unidade histórica.

A aplicação consegue reconstruir histórico por ASIN e calcular evolução entre observações.

`MOMENTUM_V1` permanece separado do score e da elegibilidade.

---

## 9. Semântica comercial da publicação

A geração de publicação utiliza somente dados persistidos e auditáveis.

Dados ausentes não são inventados.

Princípios preservados:

```text
basisPrice != previousPrice
ausência != zero
cartão != desconto à vista
parcelamento não é inferido
Pix e NuPay preservam semânticas próprias
```

Para apresentação à vista:

- quando somente Pix estiver disponível, Pix pode ser apresentado;
- quando somente NuPay estiver disponível, NuPay pode ser apresentado;
- quando Pix e NuPay existirem e Pix tiver vantagem igual ou maior, Pix é a referência principal;
- quando NuPay tiver vantagem estritamente maior, NuPay pode ser destacado e Pix preservado como alternativa quando disponível.

Para parcelamento, a política inicial prioriza condições sem juros e, entre elas, a maior quantidade de parcelas explicitamente observada.

Nenhum preço, desconto ou parcelamento é fabricado a partir de hipótese.

---

## 10. FASE 12 — Orquestração durável

A FASE 12 removeu a dependência de uma única execução síncrona longa.

Fluxo operacional básico:

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

A camada de orquestração suporta:

```text
estado persistido
jobs independentes
retry por etapa
falhas transitórias e permanentes
idempotência
leases
recuperação de trabalho abandonado
múltiplos workers
```

A falha de uma etapa não exige reiniciar desnecessariamente todo o pipeline.

---

## 11. FASE 13 — Geração de publicação

A geração de conteúdo é um caso de uso independente de canal.

Fluxo:

```text
DealEvaluation selecionada
        ↓
dados persistidos
        ↓
política de apresentação comercial
        ↓
AffiliateLinkGenerator
        ↓
template versionado
        ↓
Publication
        ↓
persistência
```

A geração não acessa novamente a Amazon para reconstruir dados.

Uma `Publication` preserva informações suficientes para auditoria e reprodutibilidade.

A branch atual também contém evolução versionada da apresentação e do template:

```text
AmazonCommercialPresentationV2
AmazonPublicationV2
```

---

## 12. FASE 14 — Interface operacional

A interface inicial é uma CLI Java.

Princípio central:

```text
A interface observa e administra o pipeline.
A interface não autoriza o pipeline a funcionar.
```

A CLI utiliza contratos da camada `application`.

Ela não deve:

```text
consultar JDBC diretamente
consultar tabelas diretamente
reimplementar filtros
recalcular score
recalcular momentum
montar Publication manualmente
controlar o caminho crítico do pipeline
```

A interface permanece substituível por outra apresentação futura sem reescrever regras de negócio.

---

## 13. Publicação sem aprovação manual

A aprovação humana obrigatória não faz parte do fluxo normal.

A decisão vigente foi registrada pela ADR de interface operacional não bloqueante.

Estados de `Publication` são estados de ciclo de vida, não etapas obrigatórias de aprovação humana.

Semântica atual:

```text
CREATED
Publication gerada e persistida
        ↓
READY
liberada automaticamente pelas regras aplicáveis
        ↓
outbox
        ↓
entrega
```

O antigo:

```text
PublicationApprovalService
```

foi removido.

A liberação normal utiliza componentes de readiness automático.

Nenhuma publicação normal deve depender de clique, confirmação ou aprovação manual de um operador.

---

## 14. FASE 15 — Qualidade integrada

A suíte de testes valida componentes isolados e jornadas completas.

Cobertura inclui, entre outros:

```text
parsing
persistência
transações
migrations
idempotência
concorrência
rollback
processamento repetido
reprocessamento
publicação
outbox
adapters
falhas HTTP
falhas de provider
```

A suíte padrão permanece separada de probes externas reais sempre que o teste depende de disponibilidade de terceiros.

---

## 15. FASE 16 — Observabilidade

A aplicação possui base de observabilidade operacional para correlacionar execução, trabalho e resultado.

Conceitos relevantes incluem:

```text
runId
jobId
asin
snapshotId
evaluationId
publicationId
```

Logs e diagnósticos devem permitir diferenciar falhas internas de falhas externas.

Segredos nunca devem ser registrados.

A observabilidade permanece desacoplada das regras de negócio.

---

## 16. FASE 17 — Execução contínua

A execução contínua utiliza scheduler e estado durável.

O scheduler inicia ciclos; ele não reimplementa regras de coleta, filtros, score ou publicação.

Responsabilidades consolidadas:

```text
frequência configurável
ProcessingRun
prevenção de sobreposição indevida
lease/lock persistente
pausa operacional
shutdown seguro
workers
múltiplos ciclos
```

Estratégias de espera com `Thread.sleep` pertencem à infraestrutura operacional do scheduler/worker idle wait e não ao publisher nem ao rate limiter.

---

## 17. Seleção operacional, quota e cadência

Qualidade comercial e prioridade operacional de publicação são conceitos distintos.

```text
score
=
quão atrativa é a oferta?

prioridade de publicação
=
quão apropriado é publicar esta oferta agora?
```

A seleção considera dados persistidos e auditáveis.

Escopo de histórico de publicação:

```text
ASIN
+
channel
+
destination
```

Somente entrega efetivamente bem-sucedida conta como publicação histórica para recorrência.

A política preserva:

```text
hardCooldown
preferredCooldown
quota
cadência
desempate determinístico
```

A quota não transforma uma oferta comercialmente aprovada em rejeitada.

Quota e schedule permanecem responsabilidades diferentes.

---

## 18. FASE 18 — Contrato de canais e outbox

A FASE 18 estabeleceu o contrato de entrega independente de provider.

Componentes centrais:

```text
PublicationCommand
PublicationChannel
PublicationResult
PublicationOutbox
PublicationAttempt
```

Estados estruturados de resultado:

```text
SUCCESS
FAILED_TRANSIENT
FAILED_PERMANENT
```

A outbox representa trabalho a entregar.

`PublicationAttempt` representa evidência histórica de uma chamada externa.

Portanto:

```text
outbox != attempt
```

A identidade idempotente considera, no mínimo:

```text
publication
+
channel
+
destination
```

O PostgreSQL é utilizado para fila durável, concorrência, lease e proteção transacional.

---

## 19. FASE 19 — Telegram

Foi implementado adapter concreto:

```text
TelegramChannel
```

Responsabilidades:

```text
validar configuração
validar destino
formatar conteúdo
chamar Telegram Bot API
interpretar resposta
classificar falha
preservar providerReference
```

Configuração:

```text
TelegramChannelConfig
TelegramChannelConfigProvider
```

Transporte:

```text
PublicationHttpTransport
JavaPublicationHttpTransport
```

Formatação:

```text
PublicationContentFormatter
TelegramPublicationFormatter
```

Link preview é configurável.

O conteúdo específico do Telegram não modifica os fatos comerciais da `Publication`.

---

## 20. FASE 19 — WhatsApp

Foi implementado adapter oficial:

```text
WhatsAppChannel
```

com configuração própria:

```text
WhatsAppChannelConfig
WhatsAppChannelConfigProvider
```

A integração foi preparada para a API oficial compatível com a configuração definida pelo projeto.

O canal oficial permanece operacionalmente desabilitado até existir onboarding e configuração real válidos junto ao provider.

Credenciais permanecem fora do código.

---

## 21. `WHATSAPP_MANUAL`

Também existe o fluxo:

```text
WHATSAPP_MANUAL
```

Ele prepara conteúdo para WhatsApp e o envia para staging privado no Telegram.

Fluxo:

```text
Publication
    ↓
formatação WhatsApp
    ↓
Telegram privado de staging
    ↓
cópia manual posterior para WhatsApp
```

`MANUAL` refere-se somente à cópia final para o WhatsApp.

Não significa aprovação humana.

Componentes:

```text
WhatsAppManualStagingChannel
WhatsAppManualStagingConfig
WhatsAppManualStagingConfigProvider
WhatsAppManualPublicationFormatter
```

O sistema registra sucesso do staging quando a mensagem chega ao Telegram privado.

Ele não afirma que a cópia posterior para o WhatsApp ocorreu.

---

## 22. Ativação de canais

Os canais podem ser ativados independentemente.

Identidades atuais:

```text
TELEGRAM
WHATSAPP_MANUAL
WHATSAPP
```

Configuração:

```text
PublicationChannelActivationConfig
PublicationChannelActivationConfigProvider
```

Quando um canal está desabilitado, a composição utiliza comportamento explícito de canal desabilitado.

Isso evita espalhar condicionais específicas de Telegram ou WhatsApp pela aplicação.

---

## 23. Composition root de entrega

A composição de entrega concreta é centralizada em:

```text
PublicationDeliveryComposition
```

Ela coordena:

```text
configuração
transport HTTP
adapters concretos
resolver de canais
outbox queue
completion
retry
rate-limit
worker
```

A aplicação continua dependendo de contratos e não de detalhes dos providers.

---

## 24. Tentativas, status e referência do provider

Cada chamada efetiva ao provider pode produzir um `PublicationAttempt`.

Dados persistidos incluem, quando aplicável:

```text
publication
outbox
channel
destination
attemptNumber
status
providerReference
errorCode
startedAt
finishedAt
```

Estados:

```text
SUCCESS
FAILED_TRANSIENT
FAILED_PERMANENT
```

A referência retornada pelo provider é preservada quando existe.

Isso permite correlacionar o estado local com a evidência externa.

---

## 25. Retry e backoff de publicação

Falhas transitórias podem ser repetidas.

Falhas permanentes não entram em loop infinito.

Componentes:

```text
PublicationOutboxRetryPolicy
BoundedExponentialPublicationOutboxRetryPolicy
PublicationOutboxRetryConfig
PublicationOutboxRetryConfigProvider
```

Semântica:

```text
falha transitória
    ↓
PublicationAttempt persistido
    ↓
há tentativa disponível?
    ├── sim
    │   ↓
    │ backoff exponencial limitado
    │   ↓
    │ mesma outbox volta para PENDING
    │
    └── não
        ↓
      estado terminal
```

Retry não cria nova `Publication`, não recalcula score, não repete seleção e não cria nova outbox lógica.

---

## 26. Fanout de entrega

A outbox suporta trabalho derivado para os destinos operacionais configurados.

Componentes relevantes:

```text
PublicationOutboxDerivedEnqueueRequest
PublicationOutboxDerivedTarget
PublicationOutboxDerivedEnqueuePort
PublicationOutboxFanoutEnqueueService
```

O fanout preserva identidade, seleção e ordem sem consumir indevidamente uma nova decisão comercial.

No fluxo atual:

```text
TELEGRAM
→ entrega pública primária

WHATSAPP_MANUAL
→ staging derivado
```

---

## 27. Rate limiting preventivo

A branch da FASE 19 contém uma base persistente de rate limiting, registrada como antecipação da FASE 20.

Componentes:

```text
PublicationRateLimitPolicy
PublicationRateLimitRule
PublicationRateLimitReservation
PublicationRateLimitReservationPort
PublicationRateLimitConfig
PublicationRateLimitConfigProvider
JdbcPublicationRateLimitReservationAdapter
MapPublicationRateLimitPolicy
```

Semântica não bloqueante:

```text
slot disponível
→ chamada externa permitida

slot indisponível
→ provider não é chamado
→ PublicationAttempt não é criado
→ outbox volta para PENDING
→ availableAt indica nova tentativa de admissão
```

Não há `Thread.sleep` dentro do publisher ou do rate limiter.

Integrações físicas:

```text
TELEGRAM
WHATSAPP_MANUAL
    ↓
TELEGRAM_BOT_API

WHATSAPP
    ↓
WHATSAPP_CLOUD_API
```

Telegram público e staging manual compartilham a mesma capacidade física do Telegram Bot API.

---

## 28. Trabalho antecipado da FASE 20

A branch atual também contém preparação de dispatch durável e reconciliação.

Entre os componentes antecipados estão:

```text
ProcessingRunPublicationReadiness
PublicationDispatchJobService
PublicationDispatchReconciliationService
PublicationProcessingRunDispatchService
```

A orquestração passou a conhecer:

```text
PUBLICATION_DISPATCH
```

Objetivo:

```text
descobrir trabalho durável
reconciliar dispatch
preparar recuperação após interrupção
```

Esses componentes não significam que a FASE 20 esteja concluída.

Eles deverão ser auditados e fechados formalmente dentro da FASE 20.

---

## 29. Persistência atual

O modelo persistente inclui, entre outros:

```text
product
offer_snapshot
offer_payment_condition
offer_payment_condition_method
offer_evidence
deal_evaluation
deal_evaluation_rule_result
deal_evaluation_score_factor
deal_evaluation_momentum_audit
processing_run
processing_job
publication
publication_attempt
publication_outbox
publication selection/configuration state
publication cadence/configuration state
publication rate-limit state
```

O PostgreSQL permanece responsável por:

```text
estado durável
idempotência
transações
leases
fila operacional
quota
auditoria
concorrência
```

Não há dependência de uma fila externa para a versão 1.0 neste momento.

---

## 30. Migrations

O schema local da branch da FASE 19 está em:

```text
V34
```

Evoluções recentes da publicação:

```text
V28__publication_attempt_timing.sql
V29__publication_outbox_delivery_fanout.sql
V30__publication_cadence_profile.sql
V31__publication_cadence_reservation_index.sql
V32__publication_outbox_cadence_audit.sql
V33__processing_publication_dispatch_job.sql
V34__publication_rate_limit_state.sql
```

Migrations aplicadas não devem ser reescritas retroativamente.

Toda evolução de schema deve ocorrer por nova migration versionada.

---

## 31. Testes

Gate local integral da FASE 19:

```text
Tests run: 1608
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

A suíte cobre, entre outros:

```text
domínio
filtros
score
ranking
histórico
momentum
orquestração
leases
concorrência
CLI
observabilidade
scheduler
seleção
quota
cadência
geração de Publication
outbox
fanout
PublicationAttempt
Telegram
WhatsApp
WhatsApp manual staging
HTTP de publicação
retry
backoff
rate limiting
migrations
integrações end-to-end
```

O gate também validou:

```text
git diff --check
```

sem problemas após os ajustes finais.

---

## 32. Testes externos

A suíte padrão não deve depender da Amazon real.

A probe externa permanece separada do gate hermético.

Objetivo:

```text
suíte padrão
→ determinística e reproduzível

probe externa
→ diagnóstico da fonte real
```

Falhas externas não devem ser mascaradas como regras de negócio internas.

---

## 33. CI

Workflow principal:

```text
.github/workflows/ci.yml
```

Ambiente de referência:

```text
JDK 25
PostgreSQL
Maven Wrapper
```

A FASE 19 está concluída localmente.

O fechamento remoto ainda depende de:

```text
commit
push da branch
Pull Request
CI do PR
merge em main
CI pós-merge
```

Até esse ciclo terminar, o status correto é:

```text
FASE 19
→ CONCLUÍDA LOCALMENTE
→ FECHAMENTO REMOTO PENDENTE
```

---

## 34. Documentação

Estrutura relevante:

```text
docs/
├── adr/
├── phases/
│   ├── ROADMAP_RASPING_AMAZON_V1.md
│   ├── FASE_12_RESULTADO.md
│   ├── FASE_13_RESULTADO.md
│   ├── FASE_14_RESULTADO.md
│   ├── FASE_15_RESULTADO.md
│   ├── FASE_16_RESULTADO.md
│   ├── FASE_17_RESULTADO.md
│   ├── FASE_18_RESULTADO.md
│   └── FASE_19_RESULTADO.md
└── research/
```

ADRs relevantes ao estado atual incluem decisões sobre:

```text
semântica comercial
score e ranking
histórico e momentum
geração de Publication
interface não bloqueante
seleção, recorrência e cadência
observabilidade
agendamento
outbox e entrega
fonte Amazon
```

A documentação histórica deve permanecer preservada.

---

## 35. Decisão sobre aprovação manual

A arquitetura vigente não utiliza aprovação manual obrigatória.

A regra é:

```text
pipeline normal
→ automático

interface operacional
→ observação e administração
→ fora do caminho crítico
```

Assim:

```text
CREATED
→ READY
```

pode ocorrer automaticamente quando as regras aplicáveis forem satisfeitas.

A existência de referências históricas a aprovação manual em roadmap ou relatórios antigos não reintroduz essa etapa no fluxo vigente.

---

## 36. Fonte Amazon e limites operacionais

A fonte da Amazon permanece desacoplada do domínio.

O projeto não deve implementar mecanismos para contornar CAPTCHA, challenge, bloqueio ou outras proteções.

Respostas como:

```text
403
429
challenge
CAPTCHA
timeout
layout inesperado
```

devem ser classificadas como falhas ou restrições operacionais da fonte.

Mudança de layout não deve produzir dados falsos silenciosamente.

Quando faltar evidência crítica, preservar fail closed.

---

## 37. Idempotência

Idempotência continua sendo requisito transversal.

Ela protege:

```text
persistência de snapshots
avaliações
jobs
seleção
Publication
outbox
entregas concluídas
```

Reexecutar trabalho não deve criar duplicidade indevida.

Para publicação, uma identidade importante é:

```text
publication
+
channel
+
destination
```

Banco e aplicação trabalham juntos; verificações apenas em memória não são suficientes.

---

## 38. Transações e concorrência

O PostgreSQL é utilizado para garantir consistência em operações críticas.

Princípios:

```text
transação explícita
unique constraints
locking
leases
SKIP LOCKED quando aplicável
estado persistido
desempate determinístico
```

Concorrência não deve permitir:

```text
duas reservas da última vaga de quota
dois workers possuindo o mesmo trabalho normalmente
duplicação de enqueue lógico
duplicação de sucesso já persistido
```

A arquitetura não promete exactly-once externo absoluto quando o provider não oferece mecanismo correspondente.

---

## 39. Próxima fase — FASE 20

A próxima fase oficial é:

```text
FASE 20 — Resiliência, recuperação e falhas de produção
```

Objetivo:

```text
garantir que falhas externas,
reinicializações
e erros operacionais

não deixem o sistema
em estado inconsistente
```

Responsabilidades a fechar formalmente incluem:

```text
taxonomia final de falhas
políticas por integração
timeout
rate limit
autenticação inválida
destino inexistente
resposta desconhecida
recuperação após restart
trabalho abandonado
reprocessamento operacional
dead-letter quando necessário
testes controlados de falha
circuit breaker somente se necessário
```

A FASE 20 deve começar auditando o que já foi antecipado na FASE 19.

Ela não deve reimplementar componentes que já estão corretos e testados.

---

## 40. FASE 21 e versão 1.0

Depois da FASE 20:

```text
FASE 21
→ segurança
→ governança
→ backup/restore
→ retenção
→ operação reproduzível
→ fechamento da v1.0
```

A versão 1.0 deve conseguir:

```text
configurar
→ iniciar
→ coletar
→ avaliar
→ ranquear
→ acompanhar histórico
→ selecionar
→ gerar Publication
→ publicar em canais configurados
→ evitar duplicações
→ diagnosticar falhas
→ recuperar processamento
→ preservar histórico
```

sem depender da IDE, de Excel ou de manipulação manual do PostgreSQL.

---

## 41. Itens pós-v1.0

Permanecem fora do escopo obrigatório da v1.0:

```text
Mercado Livre
Raspberry Pi / servidor remoto como requisito
escala distribuída
Kafka
RabbitMQ
microservices
read replicas
particionamento prematuro
cache distribuído
Amazon Creators API como dependência obrigatória
```

Princípio:

```text
primeiro medir
depois otimizar
```

A arquitetura deve permitir evolução futura sem antecipar complexidade sem evidência.

---

## 42. Regras de desenvolvimento

1. Uma fase deve possuir resultado verificável antes da próxima ser declarada concluída.
2. Coleta não implementa regra de negócio.
3. Parser descreve fatos; domínio decide.
4. Dados ausentes não são inventados.
5. Seller e delivery preservam fail closed.
6. Excel não é fonte de estado.
7. Segredos ficam fora do repositório.
8. Migrations aplicadas são imutáveis.
9. Reexecução deve preservar idempotência.
10. Toda decisão importante deve ser auditável.
11. Telegram e WhatsApp são adapters substituíveis.
12. A interface operacional não é parte obrigatória do caminho crítico.
13. Publicação normal não depende de aprovação humana.
14. Estado operacional relevante deve ser durável.
15. Infraestrutura distribuída só entra quando métricas demonstrarem necessidade.

---

## 43. Estado consolidado

```text
FASE ATUAL:
19 — Telegram e WhatsApp

STATUS LOCAL:
CONCLUÍDA

BRANCH:
feat/fase-19-telegram-whatsapp

PIPELINE DURÁVEL:
IMPLEMENTADO

INTERFACE OPERACIONAL:
CLI NÃO BLOQUEANTE

EXECUÇÃO CONTÍNUA:
IMPLEMENTADA

PUBLICATION:
VERSIONADA E PERSISTIDA

APROVAÇÃO MANUAL OBRIGATÓRIA:
NÃO

SELEÇÃO OPERACIONAL:
IMPLEMENTADA

QUOTA / CADÊNCIA:
IMPLEMENTADAS

OUTBOX:
IMPLEMENTADA

PUBLICATION ATTEMPT:
IMPLEMENTADO

TELEGRAM:
IMPLEMENTADO

WHATSAPP OFICIAL:
ADAPTER IMPLEMENTADO
ATIVAÇÃO REAL DEPENDE DE CONFIGURAÇÃO DO PROVIDER

WHATSAPP MANUAL STAGING:
IMPLEMENTADO

RETRY:
IMPLEMENTADO

BACKOFF:
IMPLEMENTADO

RATE LIMIT:
BASE PERSISTENTE IMPLEMENTADA
FECHAMENTO FORMAL NA FASE 20

TESTES:
1608

FALHAS:
0

ERROS:
0

IGNORADOS:
0

BUILD:
SUCCESS

POSTGRESQL:
18.6

FLYWAY:
34 MIGRATIONS

SCHEMA:
V34

FECHAMENTO REMOTO DA FASE 19:
PENDENTE

PRÓXIMA FASE:
20 — Resiliência, recuperação e falhas de produção
```

---

## 44. Regra de continuidade

O projeto deve continuar usando:

```text
roadmap
→ direção

ADR
→ decisão arquitetural vigente

relatório de fase
→ execução real

README
→ estado operacional atual
```

Diferenças entre planejamento e execução devem permanecer explícitas.

Não reescrever retrospectivamente documentos históricos para esconder decisões que mudaram ao longo do projeto.
