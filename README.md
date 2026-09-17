# Rasping Amazon

Sistema em desenvolvimento para **coleta, seleção, avaliação, histórico e publicação de ofertas da Amazon Brasil**, com foco em separação de responsabilidades, rastreabilidade, idempotência e evolução escalável.

> **Estado atual: FASE 6 — Parser, ASIN e normalização concluída; FASE 5 — Coleta da página de promoções concluída; FASE 4 — Configuração e segredos concluída; FASE 3 v2 — Domínio + Contratos internos revisados e concluídos; FASE 2 v2 — evolução comercial da persistência concluída.**

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
| FASE 6 | Parser, ASIN e normalização | CONCLUÍDA |

**Próxima etapa: FASE 7 — Enriquecimento oficial.**

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
- Jackson Databind 2.20.0
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

O conteúdo coletado permanece bruto nesta etapa.

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

## 8. Parser, ASIN e normalização

A FASE 6 implementou a interpretação do conteúdo bruto coletado pela FASE 5.

Fluxo:

```text
CollectionResult
      ↓
DealsParser
      ↓
ParsedDeal
```

Implementações principais:

```text
DealsParser
ParsedDeal
AmazonDealsParser
AmazonDealsParsingException
```

### Estrutura observada

A fixture real confirmou a presença de:

```text
productSearchResponse
    └── products
          ├── product
          ├── product
          └── ...
```

O conteúdo da página não é um JSON puro. O parser localiza `productSearchResponse` dentro do documento e extrai o objeto correspondente antes de interpretar a estrutura com Jackson.

### ASIN

O parser:

1. localiza `asin`;
2. remove espaços externos;
3. normaliza para maiúsculas;
4. valida:

```text
[A-Z0-9]{10}
```

5. descarta registros sem ASIN confiável.

O parser não fabrica ou infere ASIN ausente.

### URL

Links relativos são normalizados utilizando a origem da coleta.

Exemplo:

```text
/dp/B087WLJH8Y
```

torna-se:

```text
https://www.amazon.com.br/dp/B087WLJH8Y
```

A FASE 6 não gera link de associado.

### Preços

O preço atual é extraído de:

```text
price.priceToPay.price
```

e convertido para `BigDecimal`.

O `basisPrice` é extraído de:

```text
price.basisPrice.price
```

e permanece semanticamente distinto de `previousPrice`.

Na FASE 6:

```text
basisPrice → preenchido quando fornecido
previousPrice → null
```

Nenhum `pixPrice` é inferido.

### Percentual de ofertas reivindicadas

Quando disponível, o parser utiliza:

```text
dealDetails.percentClaimed
```

como:

```text
soldPercentage
```

O valor deve permanecer entre `0` e `100`.

Esse percentual não é confundido com desconto nem com percentuais relacionados às avaliações.

### Desconto

Valores observados em `dealBadge`, como:

```text
70% off
66% off
```

não são transformados automaticamente em regra de negócio ou atributo universal de desconto.

### Imagem

A imagem de alta resolução é preferida:

```text
image.hiRes.baseUrl
image.hiRes.extension
```

com fallback para baixa resolução quando necessário.

### Registros inválidos

São descartados registros sem dados mínimos confiáveis, incluindo:

```text
ASIN ausente ou inválido
título ausente
URL ausente
preço atual ausente ou inválido
```

### Deduplicação

A deduplicação considera:

```text
ASIN
+
URL
+
currentPrice
+
basisPrice
```

Isso permite preservar contextos de oferta diferentes para o mesmo ASIN.

### Fixture real

O parser é testado com conteúdo local:

```text
src/test/resources/amazon/deals-sample.html
```

A fixture representa conteúdo real coletado da Amazon Brasil e permite executar os testes sem nova chamada de rede.

## 9. Contratos internos

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

### Parsing

```text
DealsParser
ParsedDeal
```

O contrato de parsing mantém a interpretação separada da infraestrutura específica da Amazon.

### Publicação

```text
PublicationRequest
```

O contrato representa a entrada para o processo de publicação sem antecipar o gerador efetivo.

## 10. Persistência

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

## 11. Testes

Após a conclusão da FASE 6, a suíte consolidada atual:

```text
Tests run: 161
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

Os testes abrangem domínio, contratos, infraestrutura, transporte HTTP, coleta, parser e fixture real.

A fixture do parser é local e não torna a suíte dependente da disponibilidade da Amazon.

O Flyway validou 2 migrations, o schema está na versão 2 e o PostgreSQL permaneceu funcional durante os testes.

## 12. O que ainda não foi implementado

Para preservar a separação entre fases, permanecem para etapas posteriores:

- enriquecimento por fonte oficial;
- validação efetiva de vendedor e entrega;
- filtros;
- score;
- ranking;
- momentum;
- repositories das novas entidades de domínio;
- persistência específica dos resultados normalizados;
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

## 13. Fonte Amazon

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

## 14. Roadmap

```text
FASE 4  → Configuração e segredos              [CONCLUÍDA]
FASE 5  → Coleta                               [CONCLUÍDA]
FASE 6  → Parser, ASIN e normalização          [CONCLUÍDA]
FASE 7  → Enriquecimento oficial               [PRÓXIMA]
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

## 15. Documentação de fases

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
└── FASE_6_RESULTADO.md
```

A documentação de cada fase deve registrar o estado verificável antes da passagem para a seguinte.

## 16. Estado atual da FASE 6

```text
FASE 6 — Parser, ASIN e normalização
STATUS: CONCLUÍDA

Parser:
OK

ASIN:
OK

URL:
OK

Título:
OK

Preço atual:
OK

basisPrice:
OK

previousPrice:
SEPARADO

soldPercentage:
OK

Imagem:
OK

Deduplicação:
OK

Fixture real:
OK

Testes:
161

Falhas:
0

Erros:
0

Ignorados:
0

Build:
SUCCESS

Próxima etapa:
FASE 7 — Enriquecimento oficial
```
