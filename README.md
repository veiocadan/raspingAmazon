# Rasping Amazon

Sistema em desenvolvimento para **coleta, normalização, enriquecimento, validação, seleção, avaliação, histórico e publicação de ofertas da Amazon Brasil**, com foco em separação de responsabilidades, rastreabilidade, idempotência, auditabilidade e evolução escalável.

> **Estado atual: FASE 8.5 concluída localmente. A consolidação do núcleo, provenance, versionamento da avaliação, fluxo transacional, idempotência, CI, Maven Wrapper, fixtures mínimas e higiene do repositório foram implementados. A abertura formal da FASE 9 depende apenas da validação do CI remoto após o próximo push.**

## 1. Objetivo

O projeto não é apenas um raspador de ofertas. O objetivo é construir um sistema em que **coleta, normalização, enriquecimento, validação, regras de negócio, persistência e publicação permaneçam desacopladas**.

Fluxo conceitual:

```text
coleta
  ↓
identificação / normalização
  ↓
enriquecimento
  ↓
validação estrutural Amazon
  ↓
filtros configuráveis
  ↓
score / seleção
  ↓
PostgreSQL / histórico
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
| FASE 8 | Validação Amazon | CONCLUÍDA |
| FASE 8.5 | Consolidação do núcleo e preparação dos dados de decisão | CONCLUÍDA LOCALMENTE — CI REMOTO PENDENTE |
| FASE 9 | Motor de filtros configuráveis | PRÓXIMA APÓS CI VERDE |

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
    ├── java/
    │   └── com/raspingamazon/
    └── resources/
        └── amazon/
            └── fixtures/
                ├── deals/
                └── product/
```

Responsabilidades principais:

- `domain`: conceitos e invariantes de negócio, sem dependência de infraestrutura;
- `application`: contratos e coordenação dos casos de uso;
- `infrastructure`: PostgreSQL, Flyway, JDBC, configuração, HTTP e adapters tecnológicos;
- `presentation`: interfaces de entrada e exposição operacional futura.

O domínio não conhece HTML, JSON externo, HTTP, PostgreSQL, Flyway, JDBC, Excel ou canais de publicação.

O projeto permanece em um único módulo Maven enquanto não houver pressão arquitetural real para decomposição.

## 4. Stack

- Java 25
- Maven Wrapper 3.3.4
- Maven 3.9.16
- JUnit 5
- PostgreSQL 18.6
- Flyway 11.14.1
- PostgreSQL JDBC 42.7.8
- Jackson Databind 2.20.0
- Docker / Docker Compose
- GitHub Actions

## 5. Build

O projeto deve ser executado pelo Maven Wrapper versionado.

Windows:

```powershell
.\mvnw.cmd clean test
```

Linux/macOS/CI:

```bash
./mvnw clean test
```

A instalação global de Maven não é requisito do build versionado.

## 6. Configuração e segredos

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

O `.env` local não é versionado. O `.env.example` documenta as variáveis necessárias sem conter segredo real.

No Docker Compose:

```yaml
image: postgres:18.6

environment:
  POSTGRES_PASSWORD: ${DB_PASSWORD}
```

## 7. Domínio atual

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
│   ├── EvaluationRuleResult
│   └── RejectionReason
├── product/
│   ├── Asin
│   └── Product
├── publication/
├── shared/
└── validation/
    ├── DeliveryType
    └── SellerType
```

### `OfferSnapshot`

Representa uma observação temporal de uma oferta.

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

`basisPrice` continua semanticamente distinto de `previousPrice`.

### `DealEvaluation`

Registra a decisão de elegibilidade e os metadados necessários à sua reprodução e auditoria.

A FASE 8.5 separou os conceitos de versionamento necessários para impedir que a futura versão dos filtros da FASE 9 seja confundida com a política estrutural Amazon.

Resultados de regras podem ser persistidos separadamente da decisão agregada.

## 8. Coleta da Amazon

A FASE 5 implementou a coleta da página funcional de promoções:

```text
https://www.amazon.com.br/deals
```

Fluxo:

```text
AmazonDealsCollector
        ↓
HttpCollectionCollector
        ↓
JavaHttpTransport
        ↓
CollectionResult
```

O conteúdo coletado permanece bruto na fronteira de coleta.

O acesso real à Amazon não participa da suíte hermética padrão.

## 9. Parser, ASIN e normalização

Fluxo:

```text
CollectionResult
      ↓
AmazonDealsParser
      ↓
ParsedDeal
```

O ASIN é normalizado e validado como:

```text
[A-Z0-9]{10}
```

Links relativos são normalizados usando a origem da coleta.

O parser preserva a distinção entre:

```text
currentPrice
basisPrice
previousPrice
```

Nenhum `pixPrice` é inferido.

Registros sem dados mínimos confiáveis são descartados.

A deduplicação considera o contexto da oferta, incluindo ASIN, URL, `currentPrice` e `basisPrice`.

## 10. Fixtures mínimas

As capturas HTML completas deixaram de ser dependência normal dos testes.

Fixtures versionadas:

```text
src/test/resources/amazon/fixtures/
├── deals/
│   ├── basic-deal.html
│   ├── duplicate-deal.html
│   ├── end-to-end-deal.html
│   └── invalid-deal.html
└── product/
    ├── amazon-amazon.html
    ├── amazon-global.html
    ├── thirdparty-amazon.html
    └── thirdparty-thirdparty.html
```

Capturas completas de diagnóstico devem permanecer fora do fluxo normal, por exemplo em `target/diagnostics/`.

## 11. Enriquecimento da página individual

Fluxo:

```text
ParsedDeal
    ↓
ProductEnrichmentClient
    ↓
AmazonProductPageEnrichmentClient
    ↓
AmazonProductPageParser
    ↓
ProductEnrichmentResult
```

Seller e delivery são conceitos independentes.

A FASE 8.5 consolidou os contratos de enriquecimento e a provenance necessária para auditabilidade.

A implementação atual utiliza a página individual do produto como fonte de enriquecimento. Isso não significa que uma API oficial da Amazon já esteja integrada.

## 12. Validação Amazon

A política estrutural permanece fail closed:

```text
seller == Amazon
AND
deliveryProvider == Amazon
    ↓
eligible = true

qualquer outra combinação
    ↓
eligible = false
```

As razões de rejeição permanecem controladas e seller é validado antes de delivery.

Essa política é estrutural e não substitui os filtros configuráveis da FASE 9.

## 13. Provenance

Seller e delivery possuem evidência auditável persistida.

Campos relevantes incluem:

```text
evidence_type
raw_value
normalized_value
source_adapter
source_component
observed_at
```

A decisão pode ser examinada posteriormente sem depender de nova coleta da página externa.

## 14. Persistência

PostgreSQL é a persistência operacional principal.

O schema evolui exclusivamente por migrations Flyway versionadas.

Estado atual:

```text
PostgreSQL: 18.6
Migrations: 6
Schema: versão 6
```

Estruturas relevantes:

```text
product
  ↓
offer_snapshot
  ├── offer_payment_condition
  │      ↓
  │   offer_payment_condition_method
  │
  ├── offer_evidence
  │
  └── deal_evaluation
          ↓
      deal_evaluation_rule_result
```

## 15. Fluxo vertical

A FASE 8.5 implementou o processamento vertical:

```text
coleta
  ↓
parser
  ↓
enriquecimento
  ↓
Product
  ↓
OfferSnapshot
  ↓
PaymentConditions
  ↓
Evidence
  ↓
DealEvaluation
  ↓
PostgreSQL
```

A composição usa uma mesma `Connection` para os adapters JDBC envolvidos na unidade de trabalho.

## 16. Transações

`JdbcTransactionAdapter` diferencia propriedade da transação.

Quando recebe `autoCommit=true`, o adapter controla begin, commit, rollback e restauração de `autoCommit`.

Quando recebe `autoCommit=false`, a transação pertence ao chamador. O adapter cria um Savepoint, não executa commit externo e, em falha, faz rollback apenas até o Savepoint.

## 17. Idempotência

`Product` utiliza upsert atômico no PostgreSQL.

A identidade da observação de `OfferSnapshot` é:

```text
product_id
+
collected_at
+
source
```

O banco protege essa identidade com restrição única.

Quando a mesma observação é reprocessada, o sistema reutiliza o snapshot já persistido e não duplica indevidamente evidências, condições comerciais ou avaliação.

Há testes específicos de idempotência, concorrência e processamento ponta a ponta.

## 18. Teste ponta a ponta

O teste `AmazonDealProcessingEndToEndTest` exercita:

```text
fixture Deals
        ↓
HTTP local
        ↓
coleta
        ↓
parser
        ↓
fixture de produto
        ↓
enrichment
        ↓
persistência
        ↓
avaliação
        ↓
PostgreSQL
```

O mesmo evento é processado duas vezes para comprovar estabilidade da decisão e ausência de duplicação imprópria.

## 19. Testes externos

A suíte padrão é hermética:

```text
./mvnw clean test
```

A probe externa é separada:

```text
./mvnw --batch-mode -Pexternal-probe test
```

Workflow:

```text
.github/workflows/amazon-source-probe.yml
```

Artefato diagnóstico:

```text
target/diagnostics/amazon-deals-real.html
```

## 20. CI

Workflow hermético:

```text
.github/workflows/ci.yml
```

Ambiente:

```text
Ubuntu
JDK 25
PostgreSQL 18.6
Maven Wrapper
```

Comando:

```text
./mvnw --batch-mode clean test
```

A configuração está versionada. A primeira validação remota deste fechamento ainda depende do próximo `push`.

## 21. Testes

Validação local mais recente em 19/09/2026:

```text
Tests run: 233
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

O Flyway confirmou:

```text
Successfully validated 6 migrations
Current version of schema "public": 6
Schema "public" is up to date.
```

Os testes abrangem domínio, contratos, persistência, transações, concorrência, idempotência, transporte HTTP, coleta, parser, fixtures mínimas, enriquecimento, provenance, avaliação Amazon e fluxo ponta a ponta.

## 22. Higiene do repositório

A consolidação da FASE 8.5 incluiu:

- Maven Wrapper;
- `.editorconfig`;
- `.gitattributes`;
- normalização de EOL;
- remoção de `.idea/workspace.xml` do versionamento;
- PostgreSQL fixado em `18.6`;
- redução das fixtures HTML;
- remoção de tokens internos `filecite`;
- correção do encoding de `FASE_4_RESULTADO.md`;
- separação entre suíte hermética e probe externa.

## 23. Fonte Amazon

A fonte funcional investigada permanece:

```text
https://www.amazon.com.br/deals
```

A estratégia de uso automatizado de páginas Amazon continua sendo um gate de produção.

Interfaces oficiais devem ser priorizadas quando fornecerem o dado necessário.

A arquitetura mantém a fonte externa isolada dos contratos de domínio para permitir substituição futura sem reescrita do núcleo.

## 24. O que ainda não foi implementado

Para preservar a separação entre fases, permanecem:

- motor de filtros configuráveis;
- score;
- ranking;
- momentum;
- histórico analítico correspondente às fases posteriores;
- execução recorrente;
- interface operacional;
- `PublicationGenerator`;
- geração efetiva de link de associado;
- scheduler;
- filas;
- WhatsApp/Telegram;
- observabilidade completa;
- resiliência de produção;
- segurança e governança operacional;
- Excel/CSV opcional;
- mecanismos de escala guiados por métricas reais.

## 25. Roadmap

```text
FASE 4   → Configuração e segredos                 [CONCLUÍDA]
FASE 5   → Coleta                                  [CONCLUÍDA]
FASE 6   → Parser, ASIN e normalização             [CONCLUÍDA]
FASE 7   → Enriquecimento da página individual     [CONCLUÍDA]
FASE 8   → Validação Amazon                        [CONCLUÍDA]
FASE 8.5 → Consolidação do núcleo e auditabilidade [CONCLUÍDA LOCALMENTE]
            └── CI remoto                           [AGUARDANDO PUSH]
FASE 9   → Filtros configuráveis                   [PRÓXIMA APÓS CI]
FASE 10  → Score
FASE 11  → Histórico e momentum
FASE 12  → Orquestração
FASE 13  → Interface
FASE 14  → Publicação
FASE 15+ → qualidade integrada, observabilidade,
            agendamento, canais, resiliência,
            segurança, integrações opcionais e escala
```

## 26. Documentação de fases

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
├── FASE_5_RESULTADO.md
├── FASE_6_RESULTADO.md
├── FASE_7_RESULTADO.md
├── FASE_8_RESULTADO.md
└── FASE_8_5_RESULTADO.md
```

A documentação de cada fase deve registrar o estado verificável antes da passagem para a seguinte.

## 27. Estado atual

```text
FASE 8.5 — Consolidação do núcleo e preparação dos dados de decisão

STATUS LOCAL:
CONCLUÍDA

CI:
CONFIGURADO
AGUARDANDO PRIMEIRA VALIDAÇÃO REMOTA DESTE FECHAMENTO

Fluxo vertical: OK
Provenance: OK
Persistência: OK
Transações: OK
Idempotência: OK
Concorrência: OK
Maven Wrapper: OK
Fixtures mínimas: OK

Suíte hermética:
233 testes
0 falhas
0 erros
0 ignorados

Build:
SUCCESS

PostgreSQL:
18.6

Flyway:
OK

Migrations:
6

Schema:
versão 6

Próximo gate:
push + CI verde

Próxima fase:
FASE 9 — Motor de filtros configuráveis
```
