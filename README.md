# Rasping Amazon

Sistema em desenvolvimento para **coleta, normalização, enriquecimento, validação, filtragem, avaliação, score, ranking, histórico, evolução, momentum, orquestração durável, geração auditável de publicações, seleção operacional, aprovação, outbox, observabilidade, agendamento e execução contínua** de ofertas da Amazon Brasil.

O projeto prioriza:

- separação de responsabilidades;
- rastreabilidade;
- idempotência;
- auditabilidade;
- evolução incremental;
- persistência durável;
- configuração explícita;
- segurança operacional;
- escalabilidade guiada por necessidade real;
- distinção entre regra de negócio e mecanismo operacional.

> **Estado atual: FASE 18 CONCLUÍDA LOCALMENTE.**
>
> A FASE 17 está integrada à `main` e teve CI remoto verde. A FASE 18 concluiu seleção operacional versionada, cooldown, quota, auditoria, aprovação, contratos de canais, fake channel, outbox persistida, reserva concorrente de quota, claim/lease, completion atômico, vínculo entre outbox e tentativas de publicação e worker genérico de entrega.
>
> O catálogo Flyway local da FASE 18 alcança **V27**.
>
> Gate local final da FASE 18:
>
> ```text
> Tests run: 1229
> Failures: 0
> Errors: 0
> Skipped: 0
>
> BUILD SUCCESS
> ```
>
> `git diff --check` também foi executado sem apontar problemas.
>
> A validação externa real com 30 ofertas da Amazon confirmou 12 ofertas elegíveis com score, quota simulada de 5 e seleção determinística dos cinco primeiros candidatos. A investigação também levou à decisão arquitetural de utilizar **DOM renderizado com Playwright/Chromium na página individual em produção**, enquanto não existir API/fonte estruturada igualmente confiável.
>
> O fechamento remoto da FASE 18 ainda depende de commit, push da branch, Pull Request e CI remoto verde.

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
seleção operacional
  ↓
aprovação
  ↓
outbox persistida
  ↓
canal de publicação
```

permaneçam desacoplados.

Princípio central:

```text
qualidade da oferta
    ≠
prioridade operacional de publicação
    ≠
entrega externa
```

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
| FASE 17 | Agendamento e execução contínua | CONCLUÍDA |
| FASE 18 | Contrato de canais e outbox de publicação | CONCLUÍDA LOCALMENTE |
| FASE 19 | Telegram e WhatsApp | PRÓXIMA APÓS GATE REMOTO |
| FASE 20 | Resiliência, recuperação e falhas de produção | PLANEJADA |
| FASE 21 | Segurança, governança e fechamento da versão 1.0 | PLANEJADA |

A fonte de planejamento continua sendo:

```text
docs/phases/ROADMAP_RASPING_AMAZON_V1.md
```

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
├── test/
│   ├── java/
│   │   └── com/raspingamazon/
│   └── resources/
│       └── amazon/
│           └── fixtures/
└── external-probe/
    └── java/
        └── com/raspingamazon/
            └── infrastructure/
                └── diagnostic/
```

Responsabilidades:

- `domain`: conceitos, invariantes e regras de negócio sem dependência de infraestrutura;
- `application`: contratos, ports, read models e coordenação dos casos de uso;
- `infrastructure`: PostgreSQL, Flyway, JDBC, configuração, HTTP, parsing específico da Amazon, browser/renderização, bootstrap, composition roots e runtime;
- `presentation`: adaptadores de interação com o operador, atualmente CLI;
- `external-probe`: validações deliberadamente externas e temporais contra a Amazon real.

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
Playwright
PostgreSQL
Flyway
JDBC
CLI
scheduler
Telegram
WhatsApp
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
- Playwright / Chromium para DOM renderizado
- Docker / Docker Compose
- GitHub Actions
- Exec Maven Plugin para execução explícita da CLI operacional

---

## 5. Build e gates

O projeto utiliza o Maven Wrapper versionado.

Windows:

```powershell
.\mvnw.cmd clean test
```

Linux/macOS/CI:

```bash
./mvnw clean test
```

O último gate remoto integral já fechado pertence à FASE 17.

A FASE 18 possui diversos blocos locais verdes, incluindo contratos de canal, fake channel, outbox, quota, concorrência e migrations, mas ainda precisa registrar um `clean test` completo no estado final da branch antes do fechamento formal.

A suíte padrão deve permanecer hermética em relação à Amazon real.

Probes externas são executadas explicitamente e separadas.

---

## 6. Configuração e segredos

A configuração principal da aplicação permanece baseada em ambiente.

Variáveis centrais incluem:

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

Configuração de scheduler, seleção, cooldown e quota pertence aos respectivos contratos e perfis.

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
- evidências relevantes permanecem rastreáveis;
- valores ausentes não devem ser inventados.

---

## 8. Aquisição da página de Deals

A página de Deals continua sendo coletada por HTTP enquanto essa fonte permanecer suficiente para o contrato necessário.

Fluxo:

```text
https://www.amazon.com.br/deals
        ↓
coleta HTTP
        ↓
AmazonDealsParser
        ↓
ParsedDeal
```

`ParsedDeal` preserva fatos como:

```text
asin
productUrl
title
imageUrl
currentPrice
basisPrice
previousPrice
soldPercentage
rating
reviewCount
collectedAt
source
```

Seller e delivery permanecem fatos da página individual.

---

## 9. Página individual e DOM renderizado

A investigação da FASE 18 demonstrou que HTML HTTP bruto pode não carregar, de forma confiável, seller/delivery que estão claramente presentes no navegador.

Foi adotada a decisão arquitetural:

```text
página individual em produção
    ↓
DOM renderizado
    ↓
Playwright / Chromium
```

A decisão está formalizada em:

```text
docs/adr/0014-aquisicao-dom-renderizado-pagina-produto-amazon.md
```

Fluxo alvo:

```text
ParsedDeal
    ↓
productUrl
    ↓
ProductPageContentProvider
    ↓
Playwright / Chromium
    ↓
DOM renderizado
    ↓
AmazonProductPageEnrichmentClient
```

O projeto permanecerá aberto para substituir Playwright por API/fonte estruturada menos custosa quando houver confiabilidade equivalente.

---

## 10. Evidências de enrichment

O enrichment da página individual produz contratos tipados para:

```text
SellerEvidence
DeliveryEvidence
RatingEvidence
ReviewCountEvidence
PaymentCondition
```

Exemplo real validado por probe renderizada:

```text
Seller raw: Amazon.com.br
Seller type: AMAZON

Delivery raw: Amazon
Delivery type: AMAZON

Rating normalized: 4.8
Review count normalized: 183
Payment conditions: 12
```

`UNKNOWN` continua semanticamente válido quando evidência suficiente realmente não existe.

---

## 11. Validação estrutural Amazon

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
seleção operacional
scheduling
```

---

## 12. Condições comerciais

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

## 13. Filtros comerciais

Perfil ativo consolidado:

```text
COMMERCIAL_FILTER_V2
```

A V2 utiliza desconto sobre preço-base como critério comercial principal de desconto.

Configuração consolidada:

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

---

## 14. Score e ranking

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

Pesos:

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

Histórico de publicação não altera retroativamente o score.

---

## 15. Histórico e momentum

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

Momentum interpreta evolução temporal, mas não substitui score nem elegibilidade.

---

## 16. Orquestração durável — FASE 12

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
- leases;
- recuperação de jobs abandonados;
- múltiplos workers;
- idempotência por etapa;
- falhas transitórias e permanentes.

---

## 17. Geração de publicação — FASE 13

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
```

A geração não reconsulta a Amazon e não executa entrega externa.

---

## 18. `Publication`

A entidade `Publication` preserva relação com:

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

Na FASE 18 foi consolidada a aprovação explícita:

```text
CREATED
    ↓
aprovação
    ↓
READY
```

`READY` significa autorizada para seguir ao processo de entrega, mas não significa que uma linha de outbox já exista.

---

## 19. Interface operacional — FASE 14

A interface operacional permanece separada das regras de domínio.

O sistema possui CLI Java para operações administrativas e execução explícita de casos de uso.

A interface não mantém transações longas nem incorpora lógica comercial.

---

## 20. Qualidade integrada — FASE 15

A FASE 15 consolidou testes verticais e integração entre camadas já implementadas.

A suíte padrão permanece baseada em ambientes e fontes controladas.

Dados reais da Amazon pertencem às probes externas, não ao gate hermético.

---

## 21. Observabilidade — FASE 16

A observabilidade correlaciona operações externas, processamento e contexto operacional.

Princípios:

- instrumentação não altera regra de negócio;
- falha de observabilidade não substitui falha funcional;
- segredos não são registrados;
- integração externa e falha interna são distinguíveis.

---

## 22. Scheduler — FASE 17

A FASE 17 implementou execução contínua com estado persistido.

Responsabilidades:

```text
ProcessingSchedule
    ↓
janela vencida
    ↓
ProcessingRun
    ↓
COLLECT_DEALS
    ↓
worker da FASE 12
```

Inclui:

- frequência configurável;
- prevenção de sobreposição;
- lock/lease;
- pausa operacional;
- runtime contínuo;
- prova concorrente entre instâncias.

O scheduler não decide quota de publicação.

---

## 23. Seleção operacional — FASE 18

A FASE 18 introduz política própria de seleção de publicação.

Versão:

```text
PUBLICATION_SELECTION_V1
```

Ordem conceitual:

```text
elegibilidade
    ↓
filtros
    ↓
score
    ↓
ranking
    ↓
histórico de publicação
    ↓
PublicationSelectionPolicy
    ↓
candidatos priorizados
```

Critério inicial:

```text
1. nunca publicado com sucesso
2. publicação bem-sucedida mais antiga
3. maior score
4. desempate determinístico
```

---

## 24. Histórico de publicação bem-sucedida

Somente entrega externa bem-sucedida conta para recorrência.

Escopo:

```text
ASIN
+
channel
+
destination
```

Não contam como sucesso:

```text
geração
aprovação
outbox criada
claim
falha transitória
falha permanente
```

---

## 25. Cooldown e quota

Cooldown e quota possuem configuração própria.

Estados de decisão incluem:

```text
SELECTED
DEFERRED_DUE_TO_HARD_COOLDOWN
NOT_SELECTED_DUE_TO_QUOTA
```

A quota não é scheduler.

A seleção `SELECTED` é provisória até a reserva efetiva na outbox.

Candidato que perde a reserva por quota concorrente:

```text
não recebe outbox
não carrega prioridade para o futuro
volta a ser reavaliado em ciclo posterior
```

---

## 26. Auditoria da seleção

A migration:

```text
V25__publication_selection_audit.sql
```

introduz:

```text
publication_selection_profile
publication_quota_profile
publication_selection_run
publication_selection_decision
```

A decisão de seleção permanece auditável sem confundir resultado da política com reserva definitiva de quota.

---

## 27. Contrato de canais

A FASE 18 criou contratos independentes de provider:

```text
PublicationCommand
PublicationChannel
PublicationResult
PublicationResultStatus
```

`PublicationCommand` contém:

```text
publicationId
channel
destination
content
```

O canal não consulta repositórios internos para reconstruir a mensagem.

Resultados:

```text
SUCCESS
FAILED_TRANSIENT
FAILED_PERMANENT
```

---

## 28. Fake channel

Foi criado:

```text
FakePublicationChannel
```

O fake recebe comandos reais e retorna resultado configurável.

Ele existe para provar a arquitetura da FASE 18 sem introduzir Telegram ou WhatsApp antes da FASE 19.

---

## 29. Outbox de publicação

A migration:

```text
V26__publication_outbox.sql
```

cria a outbox persistida.

Estados:

```text
PENDING
PROCESSING
SUCCEEDED
FAILED_TRANSIENT
FAILED_PERMANENT
```

A outbox congela:

```text
publication
selection run
position
channel
destination
content
quota profile
quota date
```

A identidade mínima de entrega é protegida por:

```text
publication + channel + destination
```

---

## 30. Reserva atômica de quota

A existência da linha de outbox representa reserva efetiva de quota.

Fluxo:

```text
SELECTED provisório
    ↓
enqueue transacional
    ↓
quota disponível?
    ├─ sim → ENQUEUED
    └─ não → QUOTA_EXHAUSTED
```

Resultados de enqueue:

```text
ENQUEUED
ALREADY_ENQUEUED
QUOTA_EXHAUSTED
STALE_SELECTION
```

A concorrência foi testada com dois workers disputando uma única vaga.

---

## 31. Claim e lease da outbox

O claim utiliza PostgreSQL:

```sql
FOR UPDATE SKIP LOCKED
```

Transição:

```text
PENDING
    ↓
PROCESSING
```

com ownership por:

```text
locked_at
locked_by
```

Itens abandonados podem voltar a `PENDING` após expiração do lease.

---

## 32. Outbox e tentativas

A migration:

```text
V27__publication_attempt_outbox_link.sql
```

relaciona tentativa concreta ao trabalho lógico da outbox.

Conceitos:

```text
outbox
    → trabalho autorizado

publication_attempt
    → execução concreta do trabalho
```

A proteção de tentativa utiliza:

```text
publication_outbox_id + attempt_number
```

---

## 33. Completion e worker de publicação

O fluxo final da FASE 18 é:

```text
claim
    ↓
PublicationCommand
    ↓
PublicationChannel
    ↓
PublicationResult
    ↓
publication_attempt
+
outbox terminal
```

O completion persiste tentativa e resultado terminal da outbox de forma atômica e somente pelo owner atual do lease.

A implementação final foi comprovada pela suíte hermética:

```text
PublicationOutboxWorkerTest
Tests run: 7
Failures: 0
Errors: 0
Skipped: 0

JdbcPublicationOutboxCompletionAdapterTest
Tests run: 6
Failures: 0
Errors: 0
Skipped: 0

PublicationOutboxEndToEndTest
Tests run: 1
Failures: 0
Errors: 0
Skipped: 0
```

O E2E cobre o caminho até `FakePublicationChannel`, completion da outbox e persistência da tentativa, sem provider externo real.

---

## 34. Probe real de ranking

A FASE 18 inclui uma probe externa sobre as primeiras 30 ofertas reais da Amazon Deals.

Aquisição:

```text
/deals
    → HTTP

página individual
    → PLAYWRIGHT_RENDERED_DOM
```

Perfis:

```text
COMMERCIAL_FILTER_V2
SCORE_V2
```

Resultado final observado:

```text
Deals inspected: 30
Processing failures: 1
Ineligible after evaluation: 17
Eligible with score: 12
Daily quota simulated: 5
```

Top 5:

```text
#1 B00NHQFA1I  72.0146
#2 6555321806  67.1734
#3 B08R91NTHY  65.3868
#4 B01IT28KG6  47.5386
#5 B0DD1KD5JP  45.4735
```

Sete candidatos elegíveis adicionais permaneceram como:

```text
NOT_SELECTED_DUE_TO_QUOTA
```

A probe concluiu com `BUILD SUCCESS`.

---

## 35. Probes externas versionadas

As probes reais que fundamentaram decisões arquiteturais devem permanecer no repositório:

```text
AmazonRenderedProductPageExternalProbeIT
AmazonRenderedSellerDeliveryExternalProbeIT
AmazonRenderedProductEnrichmentExternalProbeIT
AmazonDealsRankingExternalProbeIT
```

Elas não entram automaticamente em `mvn test`.

Execução ocorre pelo profile:

```text
-Pexternal-probe
```

---

## 36. Operação planejada da raspagem

A cadência operacional prevista é aproximadamente:

```text
2 ciclos por dia
>10 horas entre ciclos
```

A ordem de grandeza atual discutida é de aproximadamente 450 produtos por ciclo.

Nesse cenário, maior custo do DOM renderizado é aceitável.

O sistema poderá ser executado futuramente em hardware dedicado, incluindo Raspberry Pi dedicado ao projeto.

A prioridade é obter dados corretos; otimizações de throughput serão guiadas por medição.

---

## 37. PostgreSQL e Flyway

O PostgreSQL é utilizado para:

```text
produtos
snapshots
condições de pagamento
evidências
elegibilidade
filtros
score
momentum
processing runs
jobs
observabilidade
schedules
publications
selection audit
quota profiles
outbox
publication attempts
```

Schema local conhecido da FASE 18:

```text
V27
```

Migrations da FASE 18:

```text
V25__publication_selection_audit.sql
V26__publication_outbox.sql
V27__publication_attempt_outbox_link.sql
```

Migrations aplicadas não devem ser editadas retroativamente.

---

## 38. ADRs principais

ADRs existentes incluem:

```text
0001 — semântica de filtros comerciais e apresentação de pagamentos
0002 — score, ranking e explicabilidade
0003 — histórico e momentum
0004 — geração de publicação e link associado
0005 — desconto, preço-base e preço efetivo
0006 — SCORE_V2
0007 — fallback de rating/review na página do produto
0008 — percent-encoding do link associado
0009 — interface operacional não bloqueante
0010 — seleção, recorrência e cadência de publicações
0011 — observabilidade, correlação e métricas operacionais
0012 — agendamento e execução contínua
0013 — seleção operacional, outbox e entrega de publicação
0014 — aquisição da página individual por DOM renderizado
```

ADRs registram decisões arquiteturais e não devem ser tratados como documentação descartável.

---

## 39. Política de testes

A suíte hermética deve:

- usar fixtures;
- usar banco controlado;
- não depender da Amazon real;
- não depender da disponibilidade de ofertas reais;
- não depender de Chromium externo em testes comuns;
- produzir resultado determinístico.

Probes externas podem ser temporais e dependentes da fonte real, mas devem produzir diagnóstico explícito.

Falha externa real não deve ser convertida artificialmente em sucesso.

---

## 40. Comandos úteis

### Suíte padrão

Windows:

```powershell
.\mvnw.cmd clean test
```

### Probe renderizada de página individual

```powershell
.\mvnw.cmd --batch-mode -Pexternal-probe "-Dtest=AmazonRenderedProductPageExternalProbeIT" "-Damazon.probe.product-url=https://www.amazon.com.br/dp/ASIN" test
```

### Probe de seller/delivery renderizado

```powershell
.\mvnw.cmd --batch-mode -Pexternal-probe "-Dtest=AmazonRenderedSellerDeliveryExternalProbeIT" "-Damazon.probe.product-url=https://www.amazon.com.br/dp/ASIN" test
```

### Probe de enrichment renderizado

```powershell
.\mvnw.cmd --batch-mode -Pexternal-probe "-Dtest=AmazonRenderedProductEnrichmentExternalProbeIT" "-Damazon.probe.product-url=https://www.amazon.com.br/dp/ASIN" test
```

### Probe de ranking real

```powershell
.\mvnw.cmd --batch-mode -Pexternal-probe "-Dtest=AmazonDealsRankingExternalProbeIT" "-Damazon.probe.deals-ranking.enabled=true" test
```

---

## 41. Princípios de idempotência

O projeto mantém proteção no banco como autoridade final sempre que o efeito é persistente.

Exemplos:

```text
processing jobs
publication generation
selection audit
outbox delivery identity
publication attempts
```

A lógica em memória pode evitar trabalho desnecessário, mas não substitui constraint quando concorrência importa.

---

## 42. Fronteiras de responsabilidade

### Coleta

Responsável por adquirir fonte.

### Parsing

Responsável por interpretar estrutura externa.

### Enrichment

Responsável por completar fatos da página individual.

### Elegibilidade

Responsável por validar seller/delivery estrutural.

### Filtros

Responsáveis por critérios comerciais mínimos.

### Score

Responsável por atratividade relativa.

### Ranking

Responsável por ordenação por score.

### Seleção de publicação

Responsável por recorrência, cooldown, histórico e quota provisória.

### Aprovação

Responsável por `CREATED → READY`.

### Outbox

Responsável por materializar trabalho autorizado e reservar quota.

### Worker de publicação

Responsável por entregar comando já pronto ao canal.

### Canal

Responsável por conversar com provider externo e devolver resultado estruturado.

---

## 43. O que ainda não está implementado como provider real

A FASE 18 não implementa:

```text
TelegramChannel real
WhatsAppChannel real
```

Esses adapters pertencem à FASE 19.

Também ficam para fases posteriores os hardenings específicos de provider, como rate limiting, retry sofisticado, circuit breaker e políticas operacionais próprias de cada canal.

---

## 44. Gate de fechamento da FASE 18

O gate local final da FASE 18 foi concluído.

Comprovações registradas:

```text
completion atômico da entrega
worker genérico de publicação
E2E com FakePublicationChannel
mvnw clean test completo
git diff --check
```

Resultado do gate:

```text
Tests run: 1229
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

O schema Flyway foi validado até:

```text
V27
```

Pendências exclusivamente remotas:

```text
commit
push
Pull Request
CI remoto verde
```

A ADR-0014 registra a decisão de utilizar DOM renderizado com Playwright/Chromium na página individual em produção. A consolidação do provider produtivo em `src/main` permanece sequenciada antes da operação real contínua, sem alterar o critério de conclusão funcional da FASE 18.

---

## 45. Próximos passos

Após o fechamento formal da FASE 18:

```text
FASE 19
    ↓
TelegramChannel
WhatsAppChannel
integrações reais
```

A FASE 19 deverá consumir os contratos existentes sem mover para os adapters externos lógica de:

```text
seleção
cooldown
quota
ranking
reconstrução de mensagem
```

---

## 46. Documentação da FASE 18

Resultado consolidado:

```text
docs/phases/FASE_18_RESULTADO.md
```

ADRs diretamente relacionados:

```text
docs/adr/0010-politica-selecao-recorrencia-cadencia-publicacoes.md
docs/adr/0013-selecao-operacional-outbox-e-entrega-publicacao.md
docs/adr/0014-aquisicao-dom-renderizado-pagina-produto-amazon.md
```

---

## 47. Estado resumido

O sistema já possui:

```text
fonte Amazon
    ↓
coleta
    ↓
parsing
    ↓
enrichment
    ↓
elegibilidade
    ↓
filtros
    ↓
score
    ↓
ranking
    ↓
histórico / momentum
    ↓
orquestração durável
    ↓
scheduler contínuo
    ↓
geração de Publication
    ↓
seleção operacional
    ↓
aprovação READY
    ↓
outbox + quota
    ↓
claim / lease
    ↓
contrato de canal
```

A FASE 18 está concluída localmente. Restam somente commit, push, Pull Request e CI remoto verde para o fechamento remoto da branch.
