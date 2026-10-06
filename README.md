# Rasping Amazon

Sistema em desenvolvimento para **coleta, interpretação, enriquecimento, validação, filtragem, score, ranking, histórico, seleção e publicação automatizada de ofertas da Amazon Brasil**, com PostgreSQL como fonte principal de estado e foco em separação de responsabilidades, rastreabilidade, idempotência, auditabilidade, resiliência e evolução incremental.

> **Estado atual:** FASE 20 concluída localmente na branch `feat/fase-20-resiliencia-recuperacao`. O pipeline já cobre processamento durável, geração versionada de `Publication`, interface operacional não bloqueante, observabilidade, execução contínua, seleção operacional, outbox de publicação, adapters de Telegram e WhatsApp, classificação operacional de falhas, recuperação após restart, `DELIVERY_UNKNOWN`, dead-letter lógico, reprocessamento controlado, `Retry-After`, rate limit persistente compartilhado e testes destrutivos de recuperação. O gate local final executou **1786 testes**, com **0 falhas, 0 erros e 0 ignorados**. O schema PostgreSQL/Flyway está em **V37**. O `external-probe` está verde. O fechamento remoto da FASE 20 ainda depende de commit documental final, push da branch e CI remoto. A próxima fase oficial é a **FASE 21 — Segurança, governança e fechamento da v1.0**.

---

## 1. Objetivo

O Rasping Amazon não é apenas um raspador de ofertas.

O objetivo é manter um pipeline em que aquisição de dados, interpretação, regras comerciais, decisão operacional, geração de conteúdo, entrega externa e recuperação de falhas permaneçam desacopladas.

Fluxo consolidado:

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
publication_outbox
  ↓
worker
  ↓
start barrier persistente
  ↓
Telegram / WhatsApp
  ↓
PublicationAttempt
  ↓
resultado conhecido
ou
DELIVERY_UNKNOWN
  ↓
retry / terminalidade / intervenção
  ↓
auditoria / histórico / recovery
```

O sistema deve continuar operando sem depender de Excel, de edição manual no banco ou de interface de usuário no caminho crítico.

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

README
→ representa o estado operacional atual
```

Quando uma ADR posterior altera uma decisão específica prevista originalmente pelo roadmap ou por documentação histórica, a ADR vigente prevalece naquele assunto.

Relatórios históricos não são reescritos apenas para coincidir com decisões posteriores.

Estrutura documental relevante:

```text
docs/phases/ROADMAP_RASPING_AMAZON_V1.md
docs/adr/
docs/phases/FASE_*_RESULTADO.md
FASE_20_RESULTADO.md
README.md
```

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
| FASE 20 | Resiliência, recuperação e falhas de produção | CONCLUÍDA LOCALMENTE |
| FASE 21 | Segurança, governança e fechamento da v1.0 | PRÓXIMA |

O gate remoto da FASE 20 ainda está pendente.

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
├── test/
│   ├── java/
│   │   └── com/raspingamazon/
│   └── resources/
│       └── amazon/
│           └── fixtures/
└── external-probe/
    └── java/
```

Responsabilidades:

- `domain`: conceitos, invariantes e políticas de negócio sem dependência de infraestrutura;
- `application`: casos de uso, ports, coordenação, seleção, resiliência e contratos operacionais;
- `infrastructure`: PostgreSQL, Flyway, JDBC, HTTP, Amazon, scheduler, composition roots e adapters de canal;
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
- Flyway 11.14.1
- PostgreSQL JDBC 42.7.8
- Jackson
- Jsoup
- HTTP Client da plataforma Java
- Docker / Docker Compose
- GitHub Actions
- Playwright onde a composição renderizada da fonte exige navegador real

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

Gate local final da FASE 20:

```text
Tests run: 1786
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

Probe externa:

```powershell
.\mvnw.cmd -Pexternal-probe -DskipTests test
```

Resultado:

```text
BUILD SUCCESS
```

Verificação de whitespace:

```powershell
git diff --check
```

O gate local final terminou com working tree limpa.

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

Entre as configurações relevantes estão:

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

PROCESSING_JOB_RECOVERY_LEASE_DURATION
PROCESSING_JOB_RECOVERY_BATCH_SIZE
PUBLICATION_OUTBOX_RECOVERY_LEASE_DURATION
PUBLICATION_DISPATCH_MAX_ATTEMPTS
PUBLICATION_DISPATCH_RECONCILIATION_LIMIT
```

Defaults de recovery consolidados na FASE 20:

```text
PROCESSING_JOB_RECOVERY_LEASE_DURATION=PT15M
PROCESSING_JOB_RECOVERY_BATCH_SIZE=100
PUBLICATION_OUTBOX_RECOVERY_LEASE_DURATION=PT5M
PUBLICATION_DISPATCH_MAX_ATTEMPTS=3
PUBLICATION_DISPATCH_RECONCILIATION_LIMIT=100
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

## 10. Coleta, parsing e enriquecimento

A fonte funcional investigada permanece:

```text
https://www.amazon.com.br/deals
```

Fluxo:

```text
AmazonDealsCollector
        ↓
CollectionResult
        ↓
AmazonDealsParser
        ↓
ParsedDeal
        ↓
AmazonProductPageEnrichmentClient
        ↓
AmazonProductPageParser
        ↓
ProductEnrichmentResult
```

O ASIN é validado e normalizado.

O parser preserva, conforme a evidência disponível:

```text
currentPrice
basisPrice
previousPrice
soldPercentage
rating
reviewCount
```

O enriquecimento preserva seller, delivery, condições comerciais e provenance.

Dados ausentes não são inferidos.

A FASE 20 reforçou que estrutura inválida, bloqueio da fonte, indisponibilidade e ausência de evidência precisam permanecer semanticamente distintos.

---

## 11. FASE 12 — Orquestração durável

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

## 12. FASE 13 — Geração de publicação

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

---

## 13. FASE 14 — Interface operacional

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
```

A interface permanece substituível por outra apresentação futura sem reescrever regras de negócio.

---

## 14. Publicação sem aprovação manual

A aprovação humana obrigatória não faz parte do fluxo normal.

Estados de `Publication` são estados de ciclo de vida, não etapas obrigatórias de aprovação humana.

Semântica:

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

Intervenção humana permanece reservada a situações operacionais que realmente exigem decisão, como resolução de entrega ambígua ou reprocessamento controlado.

---

## 15. FASE 15 — Qualidade integrada

A suíte valida componentes isolados e jornadas completas.

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
restart recovery
rate limit
testes destrutivos
```

A suíte padrão permanece separada de probes externas reais sempre que o teste depende de disponibilidade de terceiros.

---

## 16. FASE 16 — Observabilidade

A aplicação possui base de observabilidade operacional para correlacionar execução, trabalho e resultado.

Conceitos relevantes incluem:

```text
runId
jobId
asin
snapshotId
evaluationId
publicationId
publicationOutboxId
publicationAttemptId
```

Logs e diagnósticos devem permitir diferenciar falhas internas de falhas externas.

Segredos nunca devem ser registrados.

A observabilidade permanece desacoplada das regras de negócio.

---

## 17. FASE 17 — Execução contínua

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

A FASE 20 acrescentou um requisito adicional:

```text
recovery síncrono
ANTES
de iniciar o runtime normal
```

---

## 18. Seleção operacional, quota e cadência

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

## 19. FASE 18 — Contrato de canais e outbox

A FASE 18 estabeleceu o contrato de entrega independente de provider.

Componentes centrais:

```text
PublicationCommand
PublicationChannel
PublicationResult
PublicationOutbox
PublicationAttempt
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

## 20. FASE 19 — Telegram

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

O conteúdo específico do Telegram não modifica os fatos comerciais da `Publication`.

---

## 21. FASE 19 — WhatsApp

Foi implementado adapter oficial:

```text
WhatsAppChannel
```

com configuração própria:

```text
WhatsAppChannelConfig
WhatsAppChannelConfigProvider
```

A integração foi preparada para API oficial compatível com a configuração definida pelo projeto.

O canal oficial permanece operacionalmente condicionado ao onboarding e à configuração real válidos junto ao provider.

Credenciais permanecem fora do código.

---

## 22. `WHATSAPP_MANUAL`

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

O sistema registra sucesso do staging quando a mensagem chega ao Telegram privado.

Ele não afirma que a cópia posterior para o WhatsApp ocorreu.

---

## 23. Ativação de canais

Os canais podem ser ativados independentemente.

Identidades atuais:

```text
TELEGRAM
WHATSAPP_MANUAL
WHATSAPP
```

Quando um canal está desabilitado, a composição utiliza comportamento explícito de canal desabilitado.

Isso evita espalhar condicionais específicas de Telegram ou WhatsApp pela aplicação.

---

## 24. Composition root de entrega

A composição concreta é centralizada em:

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
attempt start
completion
retry
rate limit
worker
```

A aplicação continua dependendo de contratos e não de detalhes dos providers.

---

## 25. PublicationAttempt e start barrier

A FASE 20 consolidou a tentativa externa como evidência durável.

Antes de chamar um provider:

```text
publication_attempt = STARTED
COMMIT
↓
provider HTTP
```

Nenhuma transação JDBC permanece aberta durante a chamada HTTP externa.

Depois da chamada, a tentativa é concluída conforme a evidência disponível.

Estados atuais relevantes:

```text
STARTED
SUCCESS
FAILED_TRANSIENT
FAILED_PERMANENT
DELIVERY_UNKNOWN
```

Dados persistidos incluem, conforme aplicável:

```text
publication
publicationOutbox
channel
destination
attemptNumber
status
providerReference
errorCode
startedAt
finishedAt
```

Esse start barrier permite distinguir:

```text
processo morreu antes da chamada
```

de:

```text
processo morreu depois de registrar que a chamada começaria
```

---

## 26. Taxonomia operacional de falhas

A FASE 20 separa três dimensões.

### Semântica de retry

```text
TRANSIENT
PERMANENT
```

### Categoria de origem

```text
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
```

### Ação operacional

```text
RETRY
REJECT
PAUSE
ALERT
REPROCESS
OPERATOR_INTERVENTION
```

O sistema não transforma automaticamente toda exceção externa em retry.

---

## 27. Resiliência da fonte Amazon

A FASE 20 reforçou o contrato fail closed para a fonte.

Cenários tratados de forma explícita incluem:

```text
página indisponível
restrição da fonte
mudança estrutural
HTML inesperado
produto removido
evidência insuficiente
ausência de seller/delivery
falha de aquisição
```

A arquitetura não deve contornar CAPTCHA, challenge ou bloqueio.

Uma mudança de layout não pode produzir dados falsos silenciosamente.

Quando não houver evidência suficiente, o sistema preserva ausência ou falha classificada.

---

## 28. `DELIVERY_UNKNOWN`

Resultado externo ambíguo é um estado próprio:

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

DELIVERY_UNKNOWN
!= FAILED_PERMANENT
```

Casos típicos classificados como ambíguos:

```text
falha de transporte após início da chamada
HTTP 408 genérico
HTTP 5xx genérico sem rejeição confiável
2xx cujo corpo não confirma semanticamente a entrega
STARTED abandonado após crash/restart
```

Regra central:

```text
DELIVERY_UNKNOWN
→ não retry automático
```

---

## 29. Classificação dos canais

A política consolidada de publicação externa é:

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

rejeição explícita e conhecida do provider
→ FAILED_TRANSIENT ou FAILED_PERMANENT
```

Essa separação impede reenvio cego de uma mensagem que pode ter sido entregue.

---

## 30. Recovery de publicação abandonada

Quando existe:

```text
publication_outbox = PROCESSING
publication_attempt = STARTED
```

e o lease do worker expira, o startup recovery trata o cenário como ambíguo.

Resultado:

```text
publication_outbox = DELIVERY_UNKNOWN
publication_attempt = DELIVERY_UNKNOWN
```

A outbox deixa de ser automaticamente reclamável.

O sistema exige evidência posterior para decidir se pode reenviar.

---

## 31. Resolução operacional de entrega ambígua

A FASE 20 introduziu decisão auditável para `DELIVERY_UNKNOWN`.

Decisões:

```text
CONFIRMED_DELIVERED
→ outbox = SUCCEEDED
→ não reenviar

CONFIRMED_NOT_DELIVERED
→ outbox = PENDING
→ redelivery permitida

REMAINS_UNKNOWN
→ outbox = DELIVERY_UNKNOWN
→ não reenviar
```

A tentativa original permanece imutável como verdade histórica.

Exemplo:

```text
publication_attempt.status = DELIVERY_UNKNOWN
```

não é reescrito retroativamente para `SUCCESS`.

O conhecimento posterior é armazenado separadamente em:

```text
publication_delivery_resolution_event
```

---

## 32. Recovery no startup

Antes de iniciar loops normais, o runtime executa recovery síncrono.

Ordem:

```text
1. publication_outbox recovery
2. processing_job lease recovery
3. PUBLICATION_DISPATCH reconciliation
4. somente então runtime normal
```

Se qualquer etapa lançar exceção:

```text
startup falha fechado
```

Não há transação global entre as três autoridades.

Cada componente mantém sua própria unidade transacional.

---

## 33. Drain do backlog até quiescência

A FASE 20 corrigiu o comportamento de recovery paginado.

Parâmetros como:

```text
PROCESSING_JOB_RECOVERY_BATCH_SIZE
PUBLICATION_DISPATCH_RECONCILIATION_LIMIT
```

representam tamanho de página, não limite total de recovery.

Semântica:

```text
página cheia
→ continuar

página cheia
→ continuar

página parcial
→ quiescência
```

Na reconciliação de `PUBLICATION_DISPATCH`:

```text
página cheia sem progresso durável
→ fail closed
```

Isso evita loops infinitos e evita liberar o runtime com backlog escondido atrás de uma página sem progresso.

---

## 34. `ProcessingJob.DEAD` como dead-letter lógico

A FASE 20 não introduziu uma fila externa apenas para dead-letter.

O estado:

```text
ProcessingJob.DEAD
```

é o dead-letter lógico persistente.

Ele preserva:

```text
identidade
attemptCount
maxAttempts
última falha
timestamps
sujeito do job
```

Um job em `DEAD` não retorna automaticamente para processamento.

---

## 35. Reprocessamento controlado

Reprocessamento de `ProcessingJob.DEAD` exige uma decisão explícita.

O contrato preserva:

```text
requestKey
requestedBy
reason
requestedAt
snapshot do estado DEAD anterior
```

Semântica:

```text
DEAD
↓
decisão auditável
↓
mesmo processing_job
↓
PENDING
```

Não é criada uma nova identidade lógica.

O reprocessamento é:

```text
atômico
idempotente por requestKey
auditável
```

Auditoria:

```text
processing_job_reprocess_event
```

---

## 36. Retry e backoff de publicação

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
FAILED_TRANSIENT
    ↓
há tentativa local disponível?
    ├── não
    │   ↓
    │ estado terminal
    │
    └── sim
        ↓
        backoff
        ↓
        mesma outbox volta a PENDING
```

Retry não cria nova `Publication`, não recalcula score, não repete seleção e não cria nova outbox lógica.

---

## 37. `Retry-After`

A FASE 20 passou a preservar headers HTTP relevantes.

`PublicationHttpResponse` carrega:

```text
statusCode
body
headers
```

O parser aceita:

```text
Retry-After: delay-seconds
Retry-After: HTTP-date
```

Se o header estiver ausente ou inválido:

```text
a política local continua valendo
```

Se houver mais de um valor válido, o mais conservador é utilizado.

---

## 38. Provider retry floor

Um `HTTP 429` permanece:

```text
FAILED_TRANSIENT
```

e pode carregar:

```text
retryNotBefore
```

A política usa:

```text
nextRetryAt =
max(
    backoff local,
    retryNotBefore do provider
)
```

Regra importante:

```text
provider pode atrasar
provider não pode antecipar
```

Outra regra:

```text
sem orçamento local de retry
+
Retry-After do provider
=
sem nova tentativa
```

O provider não aumenta `maxAttempts`.

---

## 39. Rate limiting persistente compartilhado

O rate limiter preventivo permanece persistido em:

```text
publication_rate_limit_state
```

Escopo:

```text
integrationKey
```

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

Quando um provider responde `429 + Retry-After`, o floor também é propagado para a integração física.

Semântica:

```text
next_allowed_at =
max(
    next_allowed_at persistido,
    provider retry floor,
    instante observado
)
```

Assim um worker que recebeu 429 protege outros workers que usam a mesma integração.

O rate limiter não depende de memória local.

---

## 40. Fanout de entrega

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

## 41. Persistência atual

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
processing_job_reprocess_event

publication
publication_attempt
publication_outbox
publication_delivery_resolution_event

publication selection/configuration state
publication cadence/configuration state
publication_rate_limit_state
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
recovery
dead-letter lógico
reprocessamento
rate limit compartilhado
```

Não há dependência de Kafka ou RabbitMQ na versão atual.

---

## 42. Migrations

O schema local da branch da FASE 20 está em:

```text
V37
```

Flyway validou:

```text
37 migrations
```

Entre as evoluções relevantes recentes estão:

```text
publication attempt timing
publication outbox fanout
cadence
PUBLICATION_DISPATCH
publication rate-limit state
processing job reprocess audit
publication delivery resolution audit
```

Última migration:

```text
V37__publication_delivery_resolution_audit.sql
```

Migrations aplicadas não devem ser reescritas retroativamente.

Toda evolução de schema deve ocorrer por nova migration versionada.

---

## 43. Testes

Gate local integral da FASE 20:

```text
Tests run: 1786
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
Retry-After
rate limiting
recovery de restart
DEAD / reprocessamento
DELIVERY_UNKNOWN
resolução operacional
migrations
integrações end-to-end
testes destrutivos
```

---

## 44. Testes destrutivos da FASE 20

O gate destrutivo usa PostgreSQL real e fabrica estados equivalentes a crash/restart.

Foram validados seis cenários:

```text
1. vários ProcessingJobs RUNNING expirados
   → recovery drena mais de uma página

2. job esgotado
   → DEAD
   → reprocessamento controlado
   → mesma identidade volta a PENDING

3. publication_outbox PROCESSING
   + publication_attempt STARTED
   + worker desaparece
   → DELIVERY_UNKNOWN
   → nenhum retry cego
   → CONFIRMED_NOT_DELIVERED libera redelivery

4. REMAINS_UNKNOWN
   → permanece DELIVERY_UNKNOWN
   → fora do claim automático

5. várias ProcessingRuns sem PUBLICATION_DISPATCH
   + reconciliation page size = 1
   → todos os jobs são criados
   → replay não duplica

6. provider retry floor persistido
   → Connection fecha
   → nova Connection continua respeitando o limite
```

Gate isolado:

```text
Tests run: 6
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

---

## 45. Diagnóstico do shutdown do Surefire

Na suíte completa da FASE 20 foi observado uma vez:

```text
Surefire is going to kill self fork JVM.
The exit has elapsed 30 seconds after System.exit(0).
```

O Maven ainda terminou:

```text
BUILD SUCCESS
```

O thread dump correspondente mostrou apenas:

```text
threads padrão da JVM
threads internas do Maven Surefire
```

A thread `main` estava em:

```text
ForkedBooter.acquireOnePermit
ForkedBooter.acknowledgedExit
```

Não apareceu thread da aplicação, worker do Rasping Amazon, scheduler, HTTP server, Playwright ou executor da aplicação impedindo shutdown.

Os seis testes destrutivos foram reexecutados isoladamente e não reproduziram o aviso.

Classificação:

```text
bloqueador da FASE 20:
NÃO

regressão demonstrada da FASE 20:
NÃO

observação de tooling:
SIM
```

O timeout não foi aumentado para mascarar o comportamento.

---

## 46. Testes externos

A suíte padrão não deve depender da Amazon real.

A probe externa permanece separada do gate hermético.

Objetivo:

```text
suíte padrão
→ determinística e reproduzível

external-probe
→ diagnóstico da fonte real
```

Na FASE 20:

```text
external-probe
→ BUILD SUCCESS
```

Falhas externas não devem ser mascaradas como regras de negócio internas.

---

## 47. CI

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

A FASE 20 está concluída localmente.

O fechamento remoto ainda depende de:

```text
commit dos documentos finais
push da branch
CI remoto
Pull Request conforme fluxo do repositório
merge em main
CI pós-merge quando aplicável
```

Até esse ciclo terminar, o status correto é:

```text
FASE 20
→ GATE LOCAL FECHADO
→ GATE REMOTO PENDENTE
```

---

## 48. Documentação

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

FASE_20_RESULTADO.md
README.md
```

A FASE 20 também possui ADR específica de resiliência e recuperação.

Decisões documentadas incluem:

```text
taxonomia operacional
falhas de fonte
DELIVERY_UNKNOWN
start barrier
restart recovery
dead-letter lógico
reprocessamento controlado
resolução de ambiguidade
Retry-After
rate limit compartilhado
```

A documentação histórica deve permanecer preservada.

---

## 49. Fonte Amazon e limites operacionais

A fonte da Amazon permanece desacoplada do domínio.

O projeto não deve implementar mecanismos para contornar:

```text
CAPTCHA
challenge
bloqueio
restrição operacional
```

Respostas e evidências como:

```text
403
429
challenge
CAPTCHA
timeout
layout inesperado
produto removido
HTML inválido
```

devem ser classificadas explicitamente.

Mudança de layout não deve produzir dados falsos silenciosamente.

Quando faltar evidência crítica, preservar fail closed.

---

## 50. Idempotência

Idempotência continua sendo requisito transversal.

Ela protege:

```text
snapshots
avaliações
ProcessingJobs
seleção
Publication
outbox
PublicationAttempt
reprocessamento
resolução de entrega ambígua
```

Reexecutar trabalho não deve criar duplicidade indevida.

Identidades persistentes e constraints do banco são preferidas a verificações somente em memória.

Para publicação, identidade importante:

```text
publication
+
channel
+
destination
```

Para reprocessamento e resolução operacional:

```text
requestKey
```

é parte do contrato idempotente.

---

## 51. Transações e concorrência

O PostgreSQL é utilizado para garantir consistência em operações críticas.

Princípios:

```text
transação explícita
unique constraints
FOR UPDATE
SKIP LOCKED quando aplicável
leases
estado persistido
desempate determinístico
```

A arquitetura evita:

```text
transação JDBC aberta durante HTTP externo
```

A sequência externa é deliberadamente:

```text
persistir STARTED
COMMIT
↓
provider
↓
persistir resultado
```

Concorrência não deve permitir:

```text
duas reservas da última vaga de quota
dois workers possuindo normalmente o mesmo trabalho
duplicação de enqueue lógico
duplicação de reprocessamento por requestKey
reenvio automático de DELIVERY_UNKNOWN
```

A arquitetura não promete exactly-once externo absoluto quando o provider não oferece mecanismo correspondente.

---

## 52. Circuit breaker

A FASE 20 avaliou explicitamente o tema.

Decisão atual:

```text
circuit breaker
→ NÃO INTRODUZIDO
```

Motivo:

```text
não houve necessidade demonstrada suficiente
```

O sistema já possui:

```text
retry limitado
backoff
rate limit
Retry-After
persistência durável
classificação de falhas
fail closed
restart recovery
```

Circuit breaker só deve ser introduzido futuramente quando métricas e comportamento real demonstrarem benefício concreto.

---

## 53. O que não foi introduzido na FASE 20

Para evitar complexidade sem evidência:

```text
Kafka
RabbitMQ
microservices
fila externa
retry infinito
retry cego de DELIVERY_UNKNOWN
circuit breaker especulativo
transação aberta durante HTTP
reescrita retroativa de tentativa histórica
estado de recovery somente em memória
```

O PostgreSQL continua suficiente para a escala e o contrato atuais.

---

## 54. Regras de desenvolvimento

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
15. Resultado externo ambíguo não deve ser reenviado cegamente.
16. Recovery deve ocorrer antes do runtime normal.
17. Provider pode aumentar um retry floor, mas não o orçamento de tentativas.
18. Infraestrutura distribuída só entra quando métricas demonstrarem necessidade.

---

## 55. Commits funcionais da FASE 20

Sequência local de implementação da FASE 20:

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

Documentação final da fase deve ser versionada antes do push.

---

## 56. Estado consolidado

```text
FASE ATUAL:
20 — Resiliência, recuperação e falhas de produção

STATUS LOCAL:
CONCLUÍDA

BRANCH:
feat/fase-20-resiliencia-recuperacao

GATE FUNCIONAL:
acba934

PIPELINE DURÁVEL:
IMPLEMENTADO

INTERFACE OPERACIONAL:
CLI NÃO BLOQUEANTE

EXECUÇÃO CONTÍNUA:
IMPLEMENTADA

STARTUP RECOVERY:
IMPLEMENTADO

DRAIN DE BACKLOG:
IMPLEMENTADO

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

START BARRIER:
IMPLEMENTADO

DELIVERY_UNKNOWN:
IMPLEMENTADO

RESOLUÇÃO DE ENTREGA AMBÍGUA:
IMPLEMENTADA

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

RETRY-AFTER:
IMPLEMENTADO

RATE LIMIT:
PERSISTENTE E COMPARTILHADO POR INTEGRAÇÃO

DEAD-LETTER:
ProcessingJob.DEAD

REPROCESSAMENTO CONTROLADO:
IMPLEMENTADO

CIRCUIT BREAKER:
NÃO INTRODUZIDO
SEM NECESSIDADE DEMONSTRADA

TESTES DESTRUTIVOS:
6 / 6 PASSOU

TESTES COMPLETOS:
1786

FALHAS:
0

ERROS:
0

IGNORADOS:
0

BUILD:
SUCCESS

EXTERNAL PROBE:
SUCCESS

POSTGRESQL:
18.6

FLYWAY:
37 MIGRATIONS

SCHEMA:
V37

WORKING TREE DO GATE FUNCIONAL:
CLEAN

SUREFIRE SHUTDOWN:
OBSERVAÇÃO NÃO BLOQUEANTE
SEM THREAD DA APLICAÇÃO PRESA NO DUMP

DAEMON:
NÃO EXECUTADO

FECHAMENTO REMOTO DA FASE 20:
PENDENTE

PRÓXIMA FASE:
21 — Segurança, governança e fechamento da v1.0
```

---

## 57. Próxima fase — FASE 21

A próxima fase oficial é:

```text
FASE 21 — Segurança, governança e fechamento da v1.0
```

Responsabilidades previstas:

```text
segurança operacional
governança
permissões
backup e restore
retenção
proteção de dados sensíveis
operação reproduzível
fechamento da v1.0
```

A FASE 21 não deve reimplementar decisões de resiliência já fechadas na FASE 20.

---

## 58. Gate antes da FASE 21

Antes de iniciar a FASE 21:

```text
1. versionar FASE_20_RESULTADO.md;
2. versionar este README atualizado;
3. confirmar git diff --check;
4. confirmar working tree limpa após os commits;
5. push da branch da FASE 20;
6. validar CI remoto;
7. abrir/atualizar Pull Request conforme fluxo do repositório;
8. confirmar merge e CI pós-merge quando aplicável;
9. somente então declarar gate remoto da FASE 20 fechado.
```

O daemon de produção não faz parte desse gate.

---

## 59. Itens pós-v1.0

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

## 60. Regra de continuidade

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

---

## 61. Encerramento local da FASE 20

```text
FASE 20 — RESILIÊNCIA, RECUPERAÇÃO E FALHAS DE PRODUÇÃO

GATE LOCAL:
FECHADO

BRANCH:
feat/fase-20-resiliencia-recuperacao

GATE FUNCIONAL:
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

MIGRATIONS:
37

SCHEMA:
V37

TESTES DESTRUTIVOS:
6 / PASSOU

DELIVERY_UNKNOWN:
FECHADO

STARTUP RECOVERY:
FECHADO

DEAD-LETTER / REPROCESSAMENTO:
FECHADO

RETRY-AFTER / RATE LIMIT:
FECHADO

CIRCUIT BREAKER:
NÃO NECESSÁRIO NESTA FASE

AVISO DE SHUTDOWN DO SUREFIRE:
DIAGNOSTICADO
NÃO BLOQUEANTE
SEM THREAD DA APLICAÇÃO PRESA

GATE REMOTO:
PENDENTE

DAEMON:
NÃO EXECUTADO

PRÓXIMA FASE APÓS GATE REMOTO:
FASE 21 — Segurança, governança e fechamento da v1.0
```
