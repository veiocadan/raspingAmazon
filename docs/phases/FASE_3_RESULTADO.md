# FASE 3 — RESULTADO

**Projeto:** Rasping Amazon  
**Fase:** 3 — Domínio + Contratos internos  
**Data de conclusão:** 13/09/2026  
**Status:** CONCLUÍDA

---

## 1. Objetivo

A FASE 3 teve como objetivo modelar os conceitos de negócio e estabelecer os contratos internos antes das integrações externas.

A especificação da fase determina:

- criar `Product`, `OfferSnapshot`, `DealEvaluation`, `Publication` e estados relacionados;
- definir objetos de entrada/saída para os módulos de coleta e publicação;
- definir razões de rejeição como códigos controlados;
- definir contratos que escondam detalhes de HTML, API e banco do domínio;
- definir versionamento das regras que influenciam avaliação e publicação.

O critério de conclusão estabelecido pela fonte é que os principais casos de uso possam ser testados com dados falsos.

---

## 2. Resultado geral

A FASE 3 foi concluída com sucesso.

Foi criada a primeira camada efetiva de domínio do projeto, mantendo separação entre:

```text
Fontes / integrações externas
          │
          ▼
     contratos de aplicação
          │
          ▼
       domínio
          │
          ▼
 persistência / canais / infraestrutura
```

O domínio não depende de PostgreSQL, Flyway, JDBC, HTML, APIs externas, Excel ou canais de mensageria.

---

## 3. Estrutura criada

```text
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

application/
└── collection/
    └── contract/
        ├── CollectionRequest
        └── CollectionResult
```

Os contratos de coleta foram mantidos na camada de aplicação para que conteúdo bruto de coleta não entre no domínio de negócio.

---

## 4. Conceitos de domínio

### `Product`

Representa o produto identificado.

Campos:

```text
id
asin
title
imageUrl
productUrl
```

O ASIN é tratado como identificador externo e o ID interno permanece separado.

### `Asin`

Value object para representar o ASIN.

Regras:

- não aceita `null`;
- não aceita vazio;
- máximo de 10 caracteres.

### `Money`

Value object para valores monetários.

Regras:

- utiliza `BigDecimal`;
- não aceita `null`;
- não aceita valores negativos.

### `Percentage`

Value object para percentuais.

Regras:

- não aceita `null`;
- intervalo de `0` a `100`;
- criação a partir de `String`.

### `SellerType`

Vocabulário controlado:

```text
AMAZON
THIRD_PARTY
UNKNOWN
```

`UNKNOWN` permite preservar a política fail-closed.

### `DeliveryType`

Vocabulário controlado:

```text
AMAZON
THIRD_PARTY
UNKNOWN
```

A validação efetiva permanece para fase posterior.

---

## 5. `OfferSnapshot`

Representa uma ocorrência temporal da oferta.

Campos:

```text
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

A modelagem preserva snapshots históricos em vez de substituir uma coleta anterior.

O `% vendidos` permanece opcional. Ausência de evidência não é convertida em zero ou outro valor estimado.

O objeto não conhece HTML, JSON, HTTP ou PostgreSQL.

---

## 6. `DealEvaluation`

Representa o resultado estrutural da avaliação de uma oferta.

Campos:

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

Nesta fase foram definidos os campos e invariantes, mas não foram implementados os motores de filtros, score ou momentum.

`filterVersion` preserva a versão das regras utilizadas na avaliação.

---

## 7. `RejectionReason`

Catálogo controlado:

```text
SELLER_UNKNOWN
SELLER_THIRD_PARTY
DELIVERY_UNKNOWN
DELIVERY_THIRD_PARTY
INSUFFICIENT_DATA
```

Isso evita depender somente de textos livres para registrar decisões de rejeição.

A aplicação efetiva dessas regras permanece nas fases correspondentes.

---

## 8. `Publication`

Representa uma publicação gerada a partir de uma avaliação.

Campos:

```text
id
dealEvaluation
templateVersion
generatedText
affiliateUrl
status
createdAt
```

A entidade não conhece WhatsApp, Telegram, filas, HTTP, banco ou provedores externos.

O `templateVersion` permite rastrear a versão do template utilizada.

A implementação efetiva do `PublicationGenerator` permanece para a FASE 14.

---

## 9. Estados da publicação

Estados definidos:

```text
CREATED
READY
PUBLISHED
FAILED
```

Transições válidas:

```text
CREATED ──► READY

READY ──► PUBLISHED
READY ──► FAILED

FAILED ──► READY
```

Transições inválidas são rejeitadas pelo domínio.

### Decisão de persistência

O banco continua utilizando `publication.status` como texto.

Não foi criado enum PostgreSQL.

O mapeamento futuro será:

```text
PublicationStatus -> status.name()
status do banco -> PublicationStatus.valueOf(...)
```

Não foi criada migration adicional para essa decisão.

---

## 10. Contrato de publicação

Foi criado:

```text
PublicationRequest
├── dealEvaluation
└── templateVersion
```

O contrato representa a entrada necessária para o processo de publicação sem antecipar o gerador efetivo da FASE 14.

---

## 11. Contratos de coleta

Foram criados:

```text
CollectionRequest
CollectionResult
```

### `CollectionRequest`

Campo:

```text
source
```

A origem deve ser uma URI absoluta.

### `CollectionResult`

Campos:

```text
content
collectedAt
source
```

O resultado exige conteúdo, timestamp e origem válidos.

O conteúdo bruto permanece fora do domínio. Sua interpretação ficará para o parser de fase posterior.

---

## 12. Separação entre coleta e domínio

O fluxo previsto é:

```text
Collector
    │
    ▼
CollectionResult
    │
    ▼
Parser
    │
    ▼
dados normalizados
    │
    ▼
Domain
```

O collector não decide se uma oferta é boa.

O parser não decide se uma oferta deve ser publicada.

O domínio não interpreta HTML.

---

## 13. Versionamento

A fase estabeleceu versionamento estrutural para:

```text
DealEvaluation.filterVersion
Publication.templateVersion
```

Isso permite rastrear posteriormente as versões das regras e templates responsáveis pelos resultados.

O cálculo das regras e a geração efetiva do conteúdo ainda não foram implementados.

---

## 14. Relação com PostgreSQL

A FASE 3 não criou repositories para as novas entidades.

O schema da FASE 2 permanece como base de persistência.

A relação conceitual é:

```text
Product
    │
    ▼
OfferSnapshot
    │
    ▼
DealEvaluation
    │
    ▼
Publication
    │
    ▼
PublicationAttempt
```

A implementação dos mapeamentos e repositories das novas entidades será realizada nas fases correspondentes.

---

## 15. Testes automatizados

Foram criados testes para:

```text
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

Os testes cobrem invariantes, valores obrigatórios e opcionais, limites, contratos e transições de estado.

### Resultado final

```text
Tests run: 87
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

O Maven também confirmou a compilação de:

```text
17 source files
16 test source files
```

A integração existente com Flyway/PostgreSQL permaneceu funcional.

---

## 16. O que NÃO foi implementado

Para preservar a separação entre fases, não foram implementados:

- collector real;
- cliente HTTP da Amazon;
- acesso ao endpoint técnico;
- parser HTML/JSON;
- extração de ASIN;
- normalização da Amazon;
- enriquecimento por fonte oficial;
- validação real de vendedor;
- validação real de entrega;
- filtros;
- score;
- ranking;
- momentum;
- repositories das novas entidades;
- `PublicationGenerator`;
- geração efetiva de link de associado;
- scheduler;
- filas;
- canais;
- WhatsApp;
- Telegram;
- observabilidade;
- resiliência;
- segurança operacional.

Esses itens pertencem às fases posteriores.

---

## 17. Decisões preservadas

- Java + SQL como núcleo;
- PostgreSQL como persistência principal;
- Excel fora do fluxo crítico;
- domínio independente de infraestrutura;
- ASIN como identificador externo;
- snapshots para histórico;
- seller/delivery preparados para fail-closed;
- razões de rejeição como códigos controlados;
- publicação desacoplada dos canais;
- versionamento de regras;
- versionamento de templates;
- contratos internos antes das integrações;
- preferência por interfaces oficiais;
- idempotência como princípio;
- auditoria como requisito operacional futuro.

---

## 18. Pendências para fases posteriores

A ordem permanece:

```text
FASE 4  → Configuração e segredos
FASE 5  → Coleta da página
FASE 6  → Parser, ASIN e normalização
FASE 7  → Enriquecimento por fonte oficial
FASE 8  → Validação Amazon
FASE 9  → Filtros
FASE 10 → Score
FASE 11 → Histórico e momentum
FASE 12 → Orquestração
FASE 13 → Interface operacional
FASE 14 → Geração de publicações
FASE 15 → Testes integrados
FASE 16 → Observabilidade
FASE 17 → Agendamento
FASE 18 → Contrato de canais
FASE 19 → WhatsApp/Telegram
FASE 20 → Resiliência
FASE 21 → Segurança e governança
FASE 22 → Excel/CSV opcional
FASE 23 → Escalabilidade e evolução
```

A ordem das fases deve ser preservada.

---

## 19. Critérios de conclusão

| Critério | Resultado |
|---|---|
| `Product` modelado | CONCLUÍDO |
| `OfferSnapshot` modelado | CONCLUÍDO |
| `DealEvaluation` modelado | CONCLUÍDO |
| `Publication` modelado | CONCLUÍDO |
| Estados da publicação definidos | CONCLUÍDO |
| Razões de rejeição controladas | CONCLUÍDO |
| Contrato de coleta definido | CONCLUÍDO |
| Contrato de publicação definido | CONCLUÍDO |
| Versionamento de avaliação definido | CONCLUÍDO |
| Versionamento de publicação definido | CONCLUÍDO |
| Domínio sem dependência de infraestrutura | CONCLUÍDO |
| Coleta separada do domínio | CONCLUÍDO |
| Casos de uso testáveis com dados falsos | CONCLUÍDO |
| `mvn test` | PASSOU |
| Testes executados | 87 |
| Falhas | 0 |
| Erros | 0 |
| Flyway/PostgreSQL preservados | CONCLUÍDO |
| Integrações externas antecipadas | NÃO |

---

## 20. Estado final

```text
FASE 3 — Domínio + Contratos internos
STATUS: CONCLUÍDA

Java
  │
  ├── Domain
  │    ├── Product
  │    ├── OfferSnapshot
  │    ├── DealEvaluation
  │    ├── Publication
  │    ├── Validation Types
  │    └── Value Objects
  │
  ├── Application Contracts
  │    ├── CollectionRequest
  │    ├── CollectionResult
  │    └── PublicationRequest
  │
  └── Infrastructure existente
       ├── Flyway
       ├── JDBC
       └── PostgreSQL
```

---

## 21. Registro de encerramento

**Data:** 13/09/2026  
**Fase:** 3  
**Status:** CONCLUÍDA  
**Build/Testes:** OK  
**Testes:** 87 — 0 falhas — 0 erros  
**PostgreSQL/Flyway:** OK  
**Próxima fase:** FASE 4 — Configuração e segredos

---

## 22. Snapshot recomendado

Antes de iniciar a FASE 4, recomenda-se registrar este estado em um commit Git específico da FASE 3.

Também é recomendado manter um snapshot textual do código-fonte associado ao mesmo commit. Isso facilita a revisão do estado exato do projeto quando uma nova fase for iniciada.
