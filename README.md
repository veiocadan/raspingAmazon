# Rasping Amazon

Sistema em desenvolvimento para **coleta, seleção, avaliação, histórico
e publicação de ofertas da Amazon Brasil**, com foco em separação de
responsabilidades, rastreabilidade, idempotência e evolução escalável.

> **Estado atual: FASE 3 --- Domínio + Contratos internos concluída.**

O objetivo do projeto não é simplesmente "raspar ofertas da Amazon". O
objetivo é construir um sistema no qual **coleta, validação, regras de
negócio, persistência e canais de comunicação sejam módulos
independentes**.

------------------------------------------------------------------------

## 1. Objetivo do projeto

O sistema deverá evoluir para o seguinte fluxo conceitual:

``` text
[ EXECUTAR / ATUALIZAR OFERTAS ]
                |
                v
        coleta de ofertas
                |
                v
        identificação / dados
                |
                v
       validação da oferta
                |
                v
          filtros + score
                |
                v
          BANCO SQL
       histórico / estado
                |
                v
       revisão / seleção
                |
                v
      geração de publicação
                |
                v
        canais de publicação
        /                 \
       v                   v
 WhatsApp              Telegram
        \                 /
         v               v
          STATUS + AUDITORIA
```

A arquitetura deve permitir que cada etapa evolua sem acoplar o domínio
às tecnologias externas utilizadas para coleta, persistência ou
publicação.

------------------------------------------------------------------------

## 2. Estado atual

### Fases concluídas

Fase     Descrição                            Status
  -------- ------------------------------------ -----------
FASE 0   Levantamento da fonte e das regras   CONCLUÍDA
FASE 1   Fundação do projeto Java             CONCLUÍDA
FASE 2   PostgreSQL, schema e migrations      CONCLUÍDA
FASE 3   Domínio e contratos internos         CONCLUÍDA

### Próxima fase

**FASE 4 --- Configuração e segredos**

A ordem de desenvolvimento deve ser preservada. Não antecipar
implementações de fases posteriores sem uma decisão explícita de mudança
de fase.

------------------------------------------------------------------------

## 3. Arquitetura

A estrutura principal do projeto segue a separação:

``` text
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

### Responsabilidades

-   **`domain`** --- conceitos e regras de negócio, sem dependência de
    infraestrutura.
-   **`application`** --- contratos e casos de uso que coordenam o fluxo
    da aplicação.
-   **`infrastructure`** --- banco de dados, migrations e demais
    adaptadores tecnológicos.
-   **`presentation`** --- interfaces de entrada e exposição operacional
    do sistema.

A arquitetura foi construída para manter o domínio independente de:

-   HTML;
-   JSON de fontes externas;
-   HTTP;
-   PostgreSQL;
-   Flyway;
-   JDBC;
-   Excel;
-   WhatsApp;
-   Telegram;
-   provedores externos.

------------------------------------------------------------------------

## 4. Stack atual

-   **Java 25**
-   **Maven**
-   **JUnit 5**
-   **PostgreSQL 18.6**
-   **Flyway 11.14.1**
-   **PostgreSQL JDBC 42.7.8**
-   **Docker / Docker Compose** para infraestrutura local

O projeto é empacotado como artefato Maven `jar`.

------------------------------------------------------------------------

## 5. Domínio implementado na FASE 3

A FASE 3 criou a primeira camada efetiva de domínio.

``` text
domain/
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

### `Product`

Representa o produto identificado no sistema.

Principais dados:

``` text
id
asin
title
imageUrl
productUrl
```

O ID interno e o ASIN permanecem separados.

### `Asin`

Value object para representar o identificador ASIN.

Validações atuais:

-   não nulo;
-   não vazio;
-   máximo de 10 caracteres.

### `Money`

Value object para valores monetários.

Validações atuais:

-   utiliza `BigDecimal`;
-   não nulo;
-   não aceita valores negativos.

### `Percentage`

Value object para percentuais.

Validações atuais:

-   não nulo;
-   intervalo de `0` a `100`;
-   suporte à criação a partir de texto.

### `SellerType`

Vocabulário controlado:

``` text
AMAZON
THIRD_PARTY
UNKNOWN
```

`UNKNOWN` é mantido para permitir a política **fail closed** quando não
houver evidência suficiente.

### `DeliveryType`

Vocabulário controlado:

``` text
AMAZON
THIRD_PARTY
UNKNOWN
```

A aplicação efetiva da validação permanece para a fase correspondente.

------------------------------------------------------------------------

## 6. `OfferSnapshot`

`OfferSnapshot` representa uma ocorrência temporal de uma oferta.

Campos:

``` text
id
product
collectedAt
currentPrice
previousPrice
discountPercentage
soldPercentage
rating
reviewCount
sellerName
deliveryProvider
sellerType
deliveryType
source
```

O modelo utiliza snapshots para preservar o histórico das observações,
em vez de simplesmente substituir o estado anterior.

O campo `% vendidos` permanece opcional quando não existe evidência
confiável. Ausência de informação não deve ser convertida em zero ou em
um valor estimado.

------------------------------------------------------------------------

## 7. `DealEvaluation`

`DealEvaluation` representa o resultado da avaliação de uma oferta.

Campos:

``` text
id
offerSnapshot
eligible
rejectionReason
filterVersion
score
momentum
evaluatedAt
```

Na FASE 3 foram definidos o modelo e seus invariantes.

**Ainda não foram implementados nesta fase:**

-   motor de filtros;
-   cálculo de score;
-   cálculo de momentum.

`filterVersion` permite registrar futuramente qual versão das regras
produziu determinada avaliação.

------------------------------------------------------------------------

## 8. Razões de rejeição

O domínio possui um catálogo controlado:

``` text
SELLER_UNKNOWN
SELLER_THIRD_PARTY
DELIVERY_UNKNOWN
DELIVERY_THIRD_PARTY
INSUFFICIENT_DATA
```

A utilização de códigos controlados evita que decisões importantes
dependam exclusivamente de textos livres.

------------------------------------------------------------------------

## 9. Publicação

`Publication` representa uma publicação gerada a partir de uma
avaliação.

Campos:

``` text
id
dealEvaluation
templateVersion
generatedText
affiliateUrl
status
createdAt
```

A publicação permanece desacoplada dos canais.

Ela não conhece:

-   WhatsApp;
-   Telegram;
-   filas;
-   HTTP;
-   banco de dados;
-   provedores externos.

A geração efetiva das publicações pertence à **FASE 14**.

------------------------------------------------------------------------

## 10. Estados da publicação

Estados definidos no domínio:

``` text
CREATED
READY
PUBLISHED
FAILED
```

Transições válidas:

``` text
CREATED ──> READY

READY ──> PUBLISHED
READY ──> FAILED

FAILED ──> READY
```

Transições inválidas são rejeitadas pelo domínio.

A persistência utiliza `publication.status` como texto no PostgreSQL.
Não foi criado um enum PostgreSQL.

O mapeamento previsto é:

``` text
PublicationStatus -> status.name()
status do banco -> PublicationStatus.valueOf(...)
```

------------------------------------------------------------------------

## 11. Contratos internos

### Coleta

Foram definidos:

``` text
CollectionRequest
CollectionResult
```

Fluxo conceitual:

``` text
Collector
    |
    v
CollectionResult
    |
    v
Parser
    |
    v
dados normalizados
    |
    v
Domain
```

O collector não decide se uma oferta é boa.

O parser não decide se uma oferta deve ser publicada.

O domínio não interpreta HTML.

### Publicação

Foi definido:

``` text
PublicationRequest
```

O contrato representa a entrada necessária para o processo de publicação
sem antecipar o gerador efetivo.

------------------------------------------------------------------------

## 12. Persistência

O PostgreSQL é a persistência SQL principal do projeto.

Estrutura inicial criada na FASE 2:

``` text
product
    |
    v
offer_snapshot
    |
    v
deal_evaluation
    |
    v
publication
    |
    v
publication_attempt
```

Também existe a estrutura de:

``` text
configuration_version
```

As migrations são controladas pelo Flyway.

O Excel/CSV não participa do núcleo de persistência ou processamento.

------------------------------------------------------------------------

## 13. Fonte Amazon

A investigação da FASE 0 identificou:

-   página funcional de promoções da Amazon Brasil;
-   comportamento dinâmico da página;
-   carregamento de ofertas em lotes;
-   necessidade de tratamento adequado de paginação/carregamento;
-   identificação por ASIN;
-   dados de vendedor e entrega;
-   indicador de percentual vendido quando disponível.

A página observada foi:

``` text
https://www.amazon.com.br/deals
```

Também foi observado tecnicamente um endpoint JSON interno utilizado
pela aplicação web:

``` text
GET https://www.amazon.com.br/d2b/api/v1/products/search
```

Esse endpoint foi classificado como **observado tecnicamente e não
aprovado automaticamente como fonte de produção**.

A implementação definitiva deve priorizar interfaces oficiais da Amazon
quando elas fornecerem os dados necessários e respeitar as regras
contratuais e técnicas aplicáveis.

------------------------------------------------------------------------

## 14. Regras arquiteturais importantes

As seguintes decisões devem ser preservadas durante a evolução do
projeto:

1.  **Java + SQL são o núcleo do processamento e do estado.**
2.  **Não depender do Excel.**
3.  **Credenciais e segredos não ficam no código-fonte.**
4.  **Seller e delivery devem seguir política fail closed.**
5.  **Publicação não deve ser acoplada ao domínio.**
6.  **WhatsApp e Telegram devem ser adaptadores substituíveis.**
7.  **Execuções importantes devem ser auditáveis.**
8.  **Reexecuções devem buscar comportamento idempotente.**
9.  **Interfaces oficiais devem ser priorizadas quando aplicáveis.**
10. **Coleta, parser, regras de negócio e canais devem permanecer
    separados.**

------------------------------------------------------------------------

## 15. Testes

A FASE 3 adicionou testes para os principais objetos de domínio e
contratos:

``` text
AsinTest
MoneyTest
PercentageTest
SellerTypeTest
DeliveryTypeTest
RejectionReasonTest
ProductTest
OfferSnapshotTest
DealEvaluationTest
PublicationTest
PublicationRequestTest
CollectionRequestTest
CollectionResultTest
```

Resultado final da FASE 3:

``` text
Tests run: 87
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

O Maven também confirmou a compilação dos fontes e testes da fase.

------------------------------------------------------------------------

## 16. O que ainda NÃO foi implementado

Para manter a separação entre fases, os seguintes itens permanecem para
etapas posteriores:

-   collector real;
-   cliente HTTP da Amazon;
-   acesso automatizado ao endpoint técnico;
-   parser HTML/JSON;
-   extração e normalização completa de ASIN;
-   enriquecimento por fonte oficial;
-   validação efetiva de vendedor;
-   validação efetiva de entrega;
-   filtros;
-   score;
-   ranking;
-   momentum;
-   repositories das novas entidades de domínio;
-   `PublicationGenerator`;
-   geração efetiva de link de associado;
-   scheduler;
-   processamento assíncrono;
-   filas;
-   interface operacional;
-   canais WhatsApp/Telegram;
-   observabilidade;
-   resiliência;
-   segurança e governança;
-   Excel/CSV opcional;
-   mecanismos de escalabilidade e evolução.

------------------------------------------------------------------------

## 17. Roadmap

A ordem planejada permanece:

``` text
FASE 0  → Levantamento da fonte e das regras
FASE 1  → Fundação do projeto Java
FASE 2  → Banco SQL e migrations
FASE 3  → Domínio e contratos internos
FASE 4  → Configuração e segredos
FASE 5  → Coleta da página
FASE 6  → Parser, ASIN e normalização
FASE 7  → Enriquecimento por fonte oficial
FASE 8  → Validação Amazon
FASE 9  → Filtros
FASE 10 → Score
FASE 11 → Histórico e momentum
FASE 12 → Orquestração e processamento assíncrono
FASE 13 → Interface operacional Java
FASE 14 → Geração de publicações
FASE 15 → Testes integrados
FASE 16 → Observabilidade
FASE 17 → Agendamento
FASE 18 → Contrato de canais
FASE 19 → Bot WhatsApp/Telegram
FASE 20 → Resiliência
FASE 21 → Segurança e governança
FASE 22 → Excel/CSV opcional
FASE 23 → Escalabilidade e evolução
```

------------------------------------------------------------------------

## 18. Configuração local

As credenciais locais não devem ser armazenadas no repositório.

O projeto utiliza um arquivo `.env` local e mantém um modelo:

``` text
.env.example
```

Exemplo conceitual:

``` text
DB_HOST=localhost
DB_PORT=5432
DB_NAME=rasping_amazon
DB_USER=rasping
DB_PASSWORD=CHANGE_ME
```

O arquivo `.env` real deve permanecer fora do Git.

------------------------------------------------------------------------

## 19. Banco de desenvolvimento

Configuração utilizada na FASE 2:

``` text
Banco:       rasping_amazon
Usuário:     rasping
Schema:      public
Porta:       5432
PostgreSQL:  18.6
Container:   rasping-amazon-postgres
```

A infraestrutura local utiliza Docker Compose.

------------------------------------------------------------------------

## 20. Desenvolvimento

A verificação básica do projeto pode ser feita com:

``` powershell
mvn clean test
```

O build do projeto pode ser realizado com:

``` powershell
mvn package
```

Antes de avançar de fase, a expectativa é manter:

-   build reproduzível;
-   testes automatizados;
-   mudanças pequenas e rastreáveis;
-   documentação atualizada;
-   separação entre domínio e infraestrutura;
-   histórico Git organizado.

------------------------------------------------------------------------

## 21. Documentação por fase

Os resultados das fases são mantidos em documentos separados para
preservar o histórico das decisões:

``` text
FASE_0_RESULTADO.md
FASE_1_RESULTADO.md
FASE_2_RESULTADO.md
FASE_3_RESULTADO.md
```

Esses documentos registram o que foi efetivamente concluído em cada
etapa e ajudam a impedir que decisões importantes fiquem somente no
código ou no histórico da conversa.

------------------------------------------------------------------------

## 22. Princípio de evolução

O projeto deve evoluir **uma fase por vez**.

A implementação de uma fase não deve antecipar silenciosamente
funcionalidades de fases posteriores.

Cada etapa deve possuir:

1.  objetivo definido;
2.  implementação correspondente;
3.  testes verificáveis;
4.  documentação do resultado;
5.  decisões registradas;
6.  estado versionado no Git.

Isso mantém o projeto auditável e reduz o risco de acoplamento
prematuro.

------------------------------------------------------------------------

## 23. Estado de encerramento da FASE 3

``` text
FASE 3 — Domínio + Contratos internos
STATUS: CONCLUÍDA

87 testes
0 falhas
0 erros

PostgreSQL + Flyway: preservados
Domínio independente de infraestrutura: OK
Contratos internos: OK
Versionamento de avaliação/publicação: OK
```

**Próxima etapa: FASE 4 --- Configuração e segredos.**

------------------------------------------------------------------------

## Repositório

Projeto no GitHub:

https://github.com/veiocadan/raspingAmazon
