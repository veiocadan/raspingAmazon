# Rasping Amazon

Sistema em desenvolvimento para **coleta, normalização, enriquecimento, validação, filtragem, avaliação, score, ranking, histórico, evolução, momentum, orquestração durável e geração auditável de publicações de ofertas da Amazon Brasil**, com foco em separação de responsabilidades, rastreabilidade, idempotência, auditabilidade e evolução escalável.

> **Estado atual: FASE 13 concluída localmente.** O sistema possui pipeline de decisão persistido, orquestração assíncrona durável em PostgreSQL, retry/lease/idempotência, geração de publicação versionada, política comercial de apresentação, template versionado, link de associado encapsulado e persistência idempotente de `Publication`. O gate local final está verde com **558 testes**, **0 falhas**, **0 erros** e **0 ignorados**. O Flyway validou **14 migrations** e o schema PostgreSQL está na **versão 14**. A próxima fase planejada é a **FASE 14 — Interface operacional**.

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
geração de publicação
  ↓
revisão operacional
  ↓
canais futuros
```

permaneçam desacoplados.

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
| FASE 14 | Interface operacional | PRÓXIMA |
| FASE 15 | Qualidade integrada | PLANEJADA |
| FASE 16 | Observabilidade | PLANEJADA |
| FASE 17 | Agendamento | PLANEJADA |
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
│   │       └── infrastructure/
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
- `application`: contratos, ports e coordenação dos casos de uso;
- `infrastructure`: PostgreSQL, Flyway, JDBC, configuração, HTTP, parsing específico da Amazon e composition roots.

O domínio não conhece:

```text
HTML
HTTP
PostgreSQL
Flyway
JDBC
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
- Docker / Docker Compose
- GitHub Actions

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

Gate local final da FASE 13:

```text
Tests run: 558
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

Compilação observada no gate:

```text
167 source files
113 test source files
```

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
- `.env.example` documenta o formato sem conter segredos reais.

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

Métodos reconhecidos:

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

Elegibilidade estrutural permanece separada de filtros comerciais, score, momentum e apresentação de publicação.

---

## 10. Filtros comerciais

Perfil ativo:

```text
COMMERCIAL_FILTER_V1
```

Filtros:

```text
MIN_CASH_DISCOUNT
MIN_RATING
MIN_REVIEW_COUNT
```

Limites persistidos no perfil:

```text
minCashDiscountPercentage = 20
minRating = 4.3
minReviewCount = 100
```

O sistema distingue dado ausente de dado presente abaixo do limite.

---

## 11. Score e ranking

Versão atual:

```text
SCORE_V1
```

Características:

- score reproduzível;
- fatores explicáveis;
- fatores persistidos;
- soma das contribuições validada pelo domínio;
- ranking determinístico.

Score só existe quando a avaliação é elegível após todas as regras aplicáveis.

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

Versão atual:

```text
MOMENTUM_V1
```

Momentum:

- interpreta evolução temporal;
- não altera elegibilidade;
- não altera `SCORE_V1`;
- pode ser indisponível quando não há base histórica suficiente;
- possui auditoria própria.

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

A FASE 12 não incluiu publicação.

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

Regras principais para condições à vista:

```text
NuPay > Pix
    → destaca NuPay
    → Pix pode aparecer como alternativa

Pix > NuPay
    → destaca Pix

Pix = NuPay
    → destaca Pix

somente uma disponível
    → apresenta a existente
```

Para parcelamento, a apresentação prioriza a maior quantidade de parcelas sem juros quando disponível.

A política de apresentação não decide elegibilidade.

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

O template recebe dados já preparados e não conhece SQL, JDBC ou regras de geração da URL de afiliado.

Exemplo de saída:

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

Implementação:

```text
AmazonAffiliateLinkGeneratorV1
```

Versão:

```text
AMAZON_AFFILIATE_LINK_V1
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

Estados:

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

`READY` permanece reservado para revisão/liberação posterior.

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

Foi criado:

```text
AmazonPublicationComposition
```

Composição:

```text
JdbcOfferSnapshotEvaluationLoadAdapter
        ↓
JdbcPublicationDataQueryAdapter
        ↓
AmazonCommercialPresentationV1
        ↓
AmazonAffiliateLinkGeneratorV1
        ↓
AmazonPublicationV1
        ↓
PublicationJdbcRepository
        ↓
PublicationGenerator
```

A composição apenas monta dependências.

Não executa regras de negócio.

---

## 22. PostgreSQL e Flyway

Estado atual:

```text
PostgreSQL 18.6
schema version = 14
migrations validadas = 14
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
```

Regra de evolução:

```text
não editar migrations aplicadas
```

Toda mudança estrutural deve ser feita por nova migration versionada.

---

## 23. ADRs relevantes

Documentação arquitetural versionada em:

```text
docs/adr/
```

ADRs atuais relevantes para o fluxo:

```text
ADR-0001 — semântica comercial
ADR-0003 — histórico e momentum
ADR-0004 — geração de publicação e link de associado
```

O ADR-0004 formaliza:

- geração somente com dados persistidos;
- apresentação separada de elegibilidade;
- template versionado;
- estratégia de link versionada;
- persistência antes de entrega;
- publicação desacoplada dos canais.

---

## 24. Testes da FASE 13

Componentes diretamente cobertos:

```text
PublicationData
JdbcPublicationDataQueryAdapter
AmazonCommercialPresentationV1
AmazonPublicationV1
AmazonAffiliateLinkGeneratorV1
Publication
PublicationJdbcRepository
PublicationGenerator
AmazonPublicationComposition
```

Cenários validados incluem:

- dados persistidos obrigatórios;
- Pix/NuPay;
- parcelamento;
- dados opcionais;
- template determinístico;
- formatação monetária;
- geração de link;
- validação de host/HTTPS;
- versões de auditoria;
- persistência JDBC;
- idempotência;
- reentrada;
- `Clock` injetável;
- composição real;
- fluxo vertical com PostgreSQL.

---

## 25. Fluxo vertical atual

Visão consolidada:

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
ProcessingJob
```

Publicação automática em canais:

```text
AINDA NÃO
```

---

## 26. O que ainda não foi implementado

Para preservar a ordem do roadmap, permanecem fora do escopo atual:

- interface operacional;
- revisão/aprovação manual;
- scheduler operacional;
- execução periódica;
- `PublicationChannel`;
- outbox de canais;
- Telegram;
- WhatsApp;
- envio automático;
- provider reference;
- métricas avançadas;
- dashboards;
- alertas;
- hardening de produção;
- Excel/CSV como integração opcional;
- mecanismos adicionais de escala sem evidência operacional.

---

## 27. Próxima fase — FASE 14

Próxima etapa:

```text
FASE 14 — Interface operacional
```

A interface deverá operar sobre casos de uso existentes.

Responsabilidades esperadas:

```text
listar ofertas processadas
consultar avaliações
selecionar avaliação
gerar publicação
visualizar publicação
revisar / liberar publicação
```

A interface não deve acessar diretamente:

```text
collector
parser
JDBC
HTML da Amazon
canais
```

Também não deve reimplementar:

```text
elegibilidade
filtros
score
momentum
apresentação comercial
template
link de associado
persistência
```

---

## 28. Roadmap simplificado

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
FASES 15–17
Qualidade + observabilidade + agendamento
              ↓
FASES 18+
Canais + hardening + escala
```

---

## 29. Princípios preservados

O projeto mantém:

- Java como núcleo;
- PostgreSQL como estado operacional principal;
- Flyway;
- JDBC explícito;
- migrations imutáveis;
- domínio sem dependência de infraestrutura;
- adapters Amazon isolados;
- seller/delivery fail closed;
- dados ausentes não inventados;
- versões auditáveis;
- idempotência no banco;
- publicação desacoplada de canais;
- segredos fora do código;
- evolução incremental;
- escalabilidade guiada por necessidade real.

---

## 30. Comandos úteis

### Suíte completa

```powershell
.\mvnw.cmd clean test
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
git log --oneline -15
```

---

## 31. Estado consolidado

```text
Java 25
PostgreSQL 18.6
Flyway schema 14
14 migrations

FASE 12
orquestração durável
retry
lease
idempotência
workers

FASE 13
PublicationData
AMAZON_COMMERCIAL_PRESENTATION_V1
AMAZON_AFFILIATE_LINK_V1
AMAZON_PUBLICATION_V1
PublicationGenerator
PublicationJdbcRepository
AmazonPublicationComposition

Gate local:
558 testes
0 falhas
0 erros
0 ignorados
BUILD SUCCESS

Próxima fase:
FASE 14 — Interface operacional
```

---

## 32. Regra operacional atual

> **Coletar fatos sem inventá-los.**
>
> **Persistir antes de decidir.**
>
> **Versionar decisões auditáveis.**
>
> **Separar elegibilidade, filtros, score, histórico, apresentação e publicação.**
>
> **Usar PostgreSQL como defesa final de idempotência.**
>
> **Gerar publicação somente a partir de fatos persistidos.**
>
> **Não antecipar canais ou scheduler antes das fases correspondentes.**
