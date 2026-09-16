# Rasping Amazon

Sistema em desenvolvimento para **coleta, seleção, avaliação, histórico e publicação de ofertas da Amazon Brasil**, com foco em separação de responsabilidades, rastreabilidade, idempotência e evolução escalável.

> **Estado atual: FASE 5 — Coleta da página de promoções concluída; FASE 4 — Configuração e segredos concluída; FASE 3 v2 — Domínio + Contratos internos revisados e concluídos; FASE 2 v2 — evolução comercial da persistência concluída.**

## 1. Objetivo

O projeto não é apenas um raspador de ofertas. O objetivo é construir um sistema em que **coleta, normalização, validação, regras de negócio, persistência e publicação permaneçam desacopladas**.

Fluxo conceitual:

```text
coleta
  ↓
identificação / normalização
  ↓
validação
  ↓
filtros + avaliação
  ↓
PostgreSQL / histórico
  ↓
seleção
  ↓
geração de publicação
  ↓
canais
```

A ordem das fases deve ser preservada; responsabilidades futuras não devem ser antecipadas sem decisão explícita.

## 2. Estado atual

| Fase | Descrição | Status |
|---|---|---|
| FASE 0 | Levantamento da fonte e regras | CONCLUÍDA |
| FASE 1 | Fundação Java | CONCLUÍDA |
| FASE 2 | PostgreSQL, schema e migrations | CONCLUÍDA |
| FASE 2 v2 | Evolução comercial da persistência | CONCLUÍDA |
| FASE 3 | Domínio e contratos internos | CONCLUÍDA |
| FASE 3 v2 | Revisão comercial e estrutural | CONCLUÍDA |
| FASE 4 | Configuração e segredos | CONCLUÍDA |
| FASE 5 | Coleta da página de promoções | CONCLUÍDA |

**Próxima etapa: FASE 6 — Parser, ASIN e normalização.**

## 3. Arquitetura

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
    └── java/
        └── com/raspingamazon/
```

Responsabilidades principais:

- `domain`: conceitos e invariantes de negócio, sem dependência de infraestrutura;
- `application`: contratos e coordenação do fluxo;
- `infrastructure`: PostgreSQL, Flyway, JDBC, configuração e adaptadores tecnológicos;
- `presentation`: interfaces de entrada e exposição operacional futura.

O domínio não conhece HTML, JSON externo, HTTP, PostgreSQL, Flyway, JDBC, Excel ou canais de publicação.

## 4. Stack

- Java 25
- Maven
- JUnit 5
- PostgreSQL 18.6
- Flyway 11.14.1
- PostgreSQL JDBC 42.7.8
- Docker / Docker Compose

## 5. Configuração e segredos

A configuração da aplicação é centralizada em:

```text
EnvironmentConfigProvider
        ↓
ApplicationConfig
```

Variáveis de configuração:

```text
APP_ENV
DB_HOST
DB_PORT
DB_NAME
DB_USER
```

Segredo:

```text
DB_PASSWORD
```

`DB_PASSWORD` é obrigatório e não possui valor padrão no código.

A leitura de variáveis de ambiente fica centralizada no `EnvironmentConfigProvider`. Os componentes de infraestrutura recebem `ApplicationConfig` em vez de ler o ambiente diretamente.

O `.env` local não é versionado. O `.env.example` documenta as variáveis necessárias sem conter segredo real.

No Docker Compose, a senha do PostgreSQL é recebida por variável de ambiente:

```yaml
POSTGRES_PASSWORD: ${DB_PASSWORD}
```

## 6. Domínio atual

```text
domain/
├── commercial/
│   ├── PaymentCondition
│   ├── PaymentConditionType
│   └── PaymentMethod
├── deal/
│   └── OfferSnapshot
├── evaluation/
│   ├── DealEvaluation
│   └── RejectionReason
├── product/
│   ├── Asin
│   └── Product
├── publication/
│   ├── Publication
│   ├── PublicationStatus
│   └── contract/
│       └── PublicationRequest
├── shared/
│   ├── Money
│   └── Percentage
└── validation/
    ├── DeliveryType
    └── SellerType
```

### OfferSnapshot

Representa uma ocorrência temporal de uma oferta e preserva histórico.

Campos principais:

```text
id
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

`basisPrice` continua semanticamente distinto de `previousPrice`.

`paymentConditions` representa as condições comerciais estruturadas. Desconto contextual não é tratado como atributo universal do snapshot.

### PaymentCondition

Representa uma condição comercial de pagamento, incluindo quando aplicável:

```text
type
price
discountPercentage
installmentCount
installmentAmount
installmentTotal
interest
paymentMethods
```

A modelagem permite separar condição à vista de parcelamento, sem inventar preço específico de Pix quando a fonte não fornecer esse dado.

### DealEvaluation

Registra o resultado estrutural de uma avaliação:

```text
id
offerSnapshot
eligible
rejectionReason
filterVersion
score
momentum
evaluatedAt
```

Os motores de filtros, score e momentum ainda não foram implementados.

## 7. Coleta da Amazon

A FASE 5 implementou a coleta da página funcional de promoções:

```text
https://www.amazon.com.br/deals
```

A arquitetura da coleta é:

```text
AmazonDealsCollector
        ↓
HttpCollectionCollector
        ↓
JavaHttpTransport
        ↓
HTTP
        ↓
CollectionResult
```

### Responsabilidades

`AmazonDealsCollector` define a fonte específica da Amazon e não interpreta o conteúdo.

`HttpCollectionCollector` adapta o transporte HTTP ao contrato de coleta e aceita somente respostas `2xx`.

`JavaHttpTransport` executa o GET, trata falhas de transporte e utiliza o User-Agent estável:

```text
RaspingAmazon/1.0
```

O User-Agent é fixo para preservar reprodutibilidade e rastreabilidade.

### Contratos de coleta

```text
CollectionCollector
CollectionRequest
CollectionResult
CollectionException
HttpTransport
HttpTransportResponse
```

O conteúdo coletado permanece bruto nesta fase.

### Tratamento de falhas

A coleta trata:

- timeout;
- falhas de conexão;
- interrupção da requisição;
- respostas HTTP fora de `2xx`;
- respostas vazias ou em branco;
- redirecionamento através do `HttpClient`.

Uma falha de fonte não é entregue às fases seguintes como se fosse uma coleta válida.

### Diagnóstico

Existe uma probe manual:

```text
AmazonDealsCollectorRealSourceProbe
```

que pode salvar a resposta bruta em:

```text
target/diagnostics/amazon-deals-real.html
```

O artefato é diagnóstico e não participa do fluxo de negócio.

### Fonte técnica observada

A investigação da FASE 0 também observou tecnicamente:

```text
/d2b/api/v1/products/search
```

Esse endpoint interno não foi tratado automaticamente como interface autorizada de produção. Interfaces oficiais aplicáveis continuam sendo priorizadas.

## 8. Contratos internos

### Coleta

```text
CollectionRequest
CollectionResult
```

Fluxo conceitual:

```text
Collector → CollectionResult → Parser → dados normalizados → Domain
```

O collector não decide se uma oferta é boa, o parser não decide se ela será publicada e o domínio não interpreta HTML.

### Publicação

```text
PublicationRequest
```

O contrato representa a entrada para o processo de publicação sem antecipar o gerador efetivo.

## 9. Persistência

PostgreSQL é a persistência SQL principal. O schema evolui por migrations versionadas e a V1 não é editada retroativamente.

Estrutura comercial relevante:

```text
product
  ↓
offer_snapshot
  ↓
offer_payment_condition
  ↓
offer_payment_condition_method
```

A persistência de condições comerciais permanece separada das regras de seleção.

## 10. Testes

A suíte consolidada atual:

```text
Tests run: 136
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

Os testes abrangem domínio, contratos, infraestrutura, transporte HTTP, coleta e integração local.

A coleta contra a fonte real possui teste/probe diagnóstico para não tornar toda a suíte dependente da disponibilidade da Amazon.

O Flyway validou 2 migrations, o schema está na versão 2 e o PostgreSQL permaneceu funcional durante os testes.

## 11. O que ainda não foi implementado

Para preservar a separação entre fases, permanecem para etapas posteriores:

- parser HTML/JSON;
- extração e normalização completa de ASIN;
- enriquecimento por fonte oficial;
- validação efetiva de vendedor e entrega;
- filtros;
- score;
- ranking;
- momentum;
- repositories das novas entidades de domínio;
- persistência específica dos resultados de coleta;
- `PublicationGenerator`;
- geração efetiva de link de associado;
- scheduler;
- filas;
- interface operacional;
- WhatsApp/Telegram;
- observabilidade;
- resiliência;
- segurança e governança operacional;
- Excel/CSV opcional;
- mecanismos de escalabilidade e evolução.

## 12. Fonte Amazon

A investigação identificou a página funcional de promoções da Amazon Brasil:

```text
https://www.amazon.com.br/deals
```

A coleta real da FASE 5 foi implementada e validada.

A implementação utiliza:

```text
User-Agent: RaspingAmazon/1.0
```

Também foi observado tecnicamente um endpoint interno JSON. Ele não deve ser tratado automaticamente como interface autorizada de produção. A implementação futura deve priorizar interfaces oficiais aplicáveis e preservar a separação entre coleta e domínio.

## 13. Roadmap

```text
FASE 4  → Configuração e segredos              [CONCLUÍDA]
FASE 5  → Coleta                               [CONCLUÍDA]
FASE 6  → Parser, ASIN e normalização          [PRÓXIMA]
FASE 7  → Enriquecimento oficial
FASE 8  → Validação Amazon
FASE 9  → Filtros
FASE 10 → Score
FASE 11 → Histórico e momentum
FASE 12 → Orquestração
FASE 13 → Interface
FASE 14 → Publicação
FASE 15+ → testes integrados, observabilidade,
           agendamento, canais, resiliência,
           segurança, Excel/CSV e escalabilidade
```

## 14. Documentação de fases

```text
docs/phases/
├── FASE_0_RESULTADO.md
├── FASE_0_RESULTADO_v2.md
├── FASE_0_v2_PRECOS_PARCELAMENTO_PIX.md
├── FASE_1_RESULTADO.md
├── FASE_2_RESULTADO.md
├── FASE_2_RESULTADO_v2.md
├── FASE_3_RESULTADO.md
├── FASE_3_RESULTADO_v2.md
├── FASE_4_RESULTADO.md
└── FASE_5_RESULTADO.md
```

A documentação de cada fase deve registrar o estado verificável antes da passagem para a seguinte.

## 15. Estado atual da FASE 5

```text
FASE 5 — Coleta da página de promoções
STATUS: CONCLUÍDA

Fonte real:
OK

Coleta:
OK

User-Agent:
RaspingAmazon/1.0

Conteúdo bruto:
OK

Tratamento de falhas:
OK

Diagnóstico:
OK

Testes:
136

Falhas:
0

Erros:
0

Ignorados:
0

Build:
SUCCESS

PostgreSQL/Flyway:
OK

Próxima etapa:
FASE 6 — Parser, ASIN e normalização
```
