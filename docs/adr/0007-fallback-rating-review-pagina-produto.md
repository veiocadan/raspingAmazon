# ADR-0007 — Fallback de rating e reviewCount pela página individual

- **Status:** Accepted
- **Data:** 2026-09-24
- **Decisores:** projeto Rasping Amazon
- **Escopo:** enriquecimento da página individual, montagem de `OfferSnapshot` e provenance de `rating` / `reviewCount`

## 1. Contexto

O pipeline já extrai `rating` e `reviewCount` da resposta de `/deals` por meio de `AmazonDealsParser` e transporta esses valores em `ParsedDeal`.

A investigação live mostrou, porém, que esses campos podem variar entre respostas da mesma página de Deals. Em uma execução um produto pode chegar sem `customerReviews`; em outra execução temporalmente próxima o mesmo ASIN pode chegar com rating e quantidade de avaliações.

Ao mesmo tempo, o DOM renderizado da página individual do produto contém uma estrutura diretamente vinculada ao ASIN da oferta principal. A evidência observada inclui:

```html
<div
    id="averageCustomerReviews_feature_div"
    data-csa-c-asin="B0GVT7QXF7">

    <div
        id="averageCustomerReviews"
        data-asin="B0GVT7QXF7">

        <span
            id="acrPopover"
            title="4,8 de 5 estrelas">

        <span
            id="acrCustomerReviewText"
            aria-label="618 Análises">
```

Também foram observadas páginas nas quais a mesma estrutura aparece mais de uma vez para o mesmo ASIN.

A página completa pode conter avaliações, estrelas e contagens relacionadas a recomendações, carrosséis ou outros produtos. Portanto, procurar globalmente por textos como `4,8 de 5 estrelas`, por `acrPopover` sem escopo de ASIN ou por qualquer número de avaliações seria semanticamente inseguro.

A arquitetura existente já separa:

```text
coleta
→ parsing
→ enrichment
→ OfferSnapshot
→ persistence
→ evaluation
```

e a tabela `offer_evidence` já foi criada para preservar provenance com:

```text
evidence_type
raw_value
normalized_value
source_adapter
source_component
observed_at
```

Além disso, `evidence_type` é `TEXT` sem `CHECK` rígido, exatamente para permitir novos tipos de evidência sem reescrever a migration histórica.

## 2. Problema

Precisamos responder de forma determinística a quatro perguntas:

1. O que acontece quando `/deals` já fornece `rating`?
2. O que acontece quando `/deals` já fornece `reviewCount`?
3. O que acontece quando um ou ambos estão ausentes?
4. Como preservar a origem da observação da página individual sem misturar parsing Amazon com regra de negócio?

Também precisamos evitar:

- segunda chamada HTTP apenas para rating/reviewCount;
- sobrescrita silenciosa de um valor já observado em `/deals`;
- busca global por estrelas ou contagens na página;
- inferência de valores ausentes;
- alteração retroativa de migrations;
- acoplamento do domínio ao HTML Amazon.

## 3. Decisão

### 3.1. `/deals` permanece fonte primária

A precedência é definida **independentemente por campo**.

Para `rating`:

```text
ParsedDeal.rating != null
    → preservar ParsedDeal.rating

ParsedDeal.rating == null
    e página individual possui rating válido
    → usar rating do enrichment

nenhuma fonte possui valor válido
    → null
```

Para `reviewCount`:

```text
ParsedDeal.reviewCount != null
    → preservar ParsedDeal.reviewCount

ParsedDeal.reviewCount == null
    e página individual possui reviewCount válido
    → usar reviewCount do enrichment

nenhuma fonte possui valor válido
    → null
```

Não existe decisão em bloco para os dois campos.

Exemplo válido:

```text
/deals:
rating      = 4.8
reviewCount = null

product page:
rating      = 4.8
reviewCount = 618

OfferSnapshot:
rating      = 4.8   ← preservado de /deals
reviewCount = 618   ← fallback da página individual
```

### 3.2. A página individual nunca sobrescreve um valor já presente em `/deals`

Se as duas fontes trouxerem valores diferentes:

```text
/deals.rating        = 4.7
product page.rating  = 4.8
```

o snapshot continuará usando:

```text
rating = 4.7
```

A evidência `4.8` da página individual será persistida para auditoria, mas não substituirá silenciosamente o valor primário.

Conflitos de fonte poderão alimentar observabilidade futura, mas não alteram esta precedência.

### 3.3. O parsing da página individual será estrutural e ancorado por ASIN

Será criado um parser dedicado:

```text
AmazonCustomerReviewParser
```

na infraestrutura Amazon.

Ele utilizará Jsoup e receberá:

```text
html
expectedAsin
```

A busca seguirá esta prioridade:

```text
1. #averageCustomerReviews_feature_div
   com data-csa-c-asin == expectedAsin

2. #averageCustomerReviews[data-asin]
   com data-asin == expectedAsin
```

Dentro de um root aceito:

```text
rating:
#acrPopover
@title

reviewCount:
#acrCustomerReviewText
@aria-label
```

O parser não procurará rating ou quantidade de avaliações globalmente pela página.

### 3.4. Normalização do rating

Exemplo observado:

```text
raw:
4,8 de 5 estrelas

normalized:
4.8
```

O valor normalizado somente será aceito no intervalo:

```text
0 <= rating <= 5
```

Se o atributo existir mas não puder ser interpretado com segurança:

```text
raw_value        = valor observado
normalized value = indisponível
```

Nenhum valor será inventado.

### 3.5. Normalização do reviewCount

Exemplos:

```text
618 Análises
→ 618

1.607 Análises
→ 1607
```

Separadores localizados são removidos apenas da sequência numérica da contagem.

O resultado deve ser um inteiro não negativo.

Se não houver interpretação confiável, o valor normalizado permanece indisponível.

### 3.6. Novos contratos tipados

O contrato de enrichment passa a transportar:

```text
RatingEvidence
ReviewCountEvidence
```

além de:

```text
SellerEvidence
DeliveryEvidence
PaymentCondition
```

Cada nova evidência preserva:

```text
rawValue
normalizedValue
source
```

O `ProductEnrichmentResult` continuará não tomando decisões de filtro, score ou publicação.

### 3.7. O mesmo HTML é reutilizado

`AmazonProductPageEnrichmentClient` continuará adquirindo a página uma única vez:

```text
ProductPageContentProvider.load(...)
               ↓
              html
       ┌───────┼────────┐
       ↓       ↓        ↓
 seller/     payment   rating/
 delivery              reviews
 parser       parser    parser
```

Não haverá nova chamada HTTP para rating/reviewCount.

Isso preserva custo, consistência temporal e a fronteira já criada entre aquisição e parsing.

### 3.8. A precedência será aplicada no `OfferSnapshotFactory`

O parser apenas descreve o que encontrou.

O `ProductEnrichmentResult` apenas transporta as observações.

A regra de composição:

```text
/deals primeiro
fallback do enrichment apenas para ausência
```

será aplicada no `OfferSnapshotFactory`, que já é responsável por combinar `ParsedDeal` e enrichment em `OfferSnapshot`.

Assim:

```text
infraestrutura Amazon
→ extrai fatos

aplicação
→ compõe fontes

domínio
→ recebe snapshot pronto para avaliação
```

### 3.9. Provenance será persistida em `offer_evidence`

Serão acrescentados novos valores semânticos ao enum de aplicação:

```text
RATING
REVIEW_COUNT
```

A migration histórica V3 **não será alterada**.

Também **não será criada nova migration** apenas para esses valores, porque:

```text
offer_evidence.evidence_type
```

já é `TEXT` e foi intencionalmente projetado para aceitar novos tipos.

Para evidência válida:

```text
evidence_type     = RATING
raw_value         = 4,8 de 5 estrelas
normalized_value  = 4.8
source_adapter    = AMAZON_PRODUCT_PAGE
source_component  = averageCustomerReviews_feature_div/acrPopover@title
observed_at       = enrichedAt
```

e:

```text
evidence_type     = REVIEW_COUNT
raw_value         = 618 Análises
normalized_value  = 618
source_adapter    = AMAZON_PRODUCT_PAGE
source_component  = averageCustomerReviews_feature_div/acrCustomerReviewText@aria-label
observed_at       = enrichedAt
```

Quando a página individual não fornecer valor utilizável:

```text
raw_value         = null, ou o texto bruto não interpretável
normalized_value  = UNAVAILABLE
source_component  = null, ou o componente onde o valor bruto foi observado
```

A ausência continua auditável e não é convertida em zero.

### 3.10. Reconstrução da origem

No pipeline operacional assíncrono:

```text
deal_candidate
```

preserva os valores originalmente vindos de `/deals`.

```text
offer_evidence
```

preserva a observação da página individual.

```text
offer_snapshot
```

preserva o valor selecionado segundo a precedência desta ADR.

Portanto, a origem da decisão pode ser reconstruída sem guardar o HTML bruto.

O fluxo síncrono legado continua compatível, mas a evolução operacional deve privilegiar o pipeline persistente por etapas, no qual `DealCandidate` conserva explicitamente a observação de `/deals`.

## 4. Consequências

### 4.1. Positivas

- `rating` e `reviewCount` deixam de falhar apenas porque `/deals` omitiu `customerReviews`;
- a página individual é usada como fallback, não como sobrescrita indiscriminada;
- cada campo possui precedência independente;
- não existe segunda chamada de rede;
- a seleção é ancorada por ASIN;
- reviews de outros produtos não contaminam a oferta principal;
- ausência permanece ausência;
- raw e normalized permanecem separados;
- provenance é persistida;
- nenhuma migration histórica é alterada;
- o domínio continua sem conhecimento de HTML/Jsoup.

### 4.2. Custos

- `ProductEnrichmentResult` ganha dois conceitos adicionais;
- `OfferEvidenceJdbcRepository` passa a persistir quatro evidências por enrichment em vez de duas;
- testes que esperavam exatamente duas linhas em `offer_evidence` precisam ser atualizados;
- o parser estrutural passa a depender dos containers observados da Amazon, que continuam sujeitos a mudança externa.

### 4.3. Riscos controlados

A Amazon pode alterar:

```text
averageCustomerReviews_feature_div
averageCustomerReviews
acrPopover
acrCustomerReviewText
```

Por isso:

- o parser permanece isolado em infraestrutura;
- a suíte hermética cobre a estrutura;
- o probe externo continua separado da suíte comum;
- ausência inesperada deve resultar em `UNAVAILABLE`, não em valor fabricado.

## 5. Alternativas rejeitadas

### 5.1. Usar somente `/deals`

Rejeitado porque a investigação mostrou ausência temporal de `customerReviews` para produtos cuja página individual continha os dados necessários.

### 5.2. Sempre preferir a página individual

Rejeitado porque alteraria silenciosamente uma observação já recebida de `/deals` e tornaria a precedência menos previsível.

### 5.3. Procurar estrelas e números em toda a página

Rejeitado porque a página contém outros produtos, carrosséis, recomendações e reviews fora da oferta principal.

### 5.4. Criar nova chamada HTTP para customer reviews

Rejeitado porque o enrichment já possui o mesmo HTML necessário.

### 5.5. Guardar HTML bruto no banco

Rejeitado. O modelo de provenance existente foi desenhado para guardar o mínimo auditável:

```text
tipo
raw
normalizado
fonte
componente
timestamp
```

### 5.6. Criar migration apenas para novos `evidence_type`

Rejeitado porque a própria V3 deixou `evidence_type` como `TEXT` extensível.

## 6. Testes obrigatórios

A mudança somente será considerada validada quando a suíte hermética comprovar pelo menos:

1. parser escolhe o ASIN esperado mesmo quando outro produto aparece primeiro;
2. `4,8 de 5 estrelas` normaliza para `4.8`;
3. `1.607 Análises` normaliza para `1607`;
4. ASIN diferente não é usado como fallback;
5. valor bruto não interpretável não é inventado;
6. `ParsedDeal.rating` presente vence rating da página;
7. `ParsedDeal.reviewCount` presente vence reviewCount da página;
8. os dois campos são resolvidos independentemente;
9. ausência em ambas as fontes continua `null`;
10. `offer_evidence` persiste `SELLER`, `DELIVERY`, `RATING` e `REVIEW_COUNT`;
11. testes existentes de payment conditions, score, filtros, persistência e orquestração continuam verdes.

Depois da suíte hermética, o probe renderizado poderá validar novamente o ASIN:

```text
B0GVT7QXF7
```

O resultado esperado, quando `/deals` omitir customer reviews e o DOM da página continuar contendo a evidência observada, é:

```text
rating      = 4.8
reviewCount = 618
```

no `OfferSnapshot`.

## 7. Fora de escopo

Esta ADR não altera:

- semântica de desconto contra `basisPrice`;
- `PaymentCondition.discountPercentage`;
- filtros comerciais além de fornecer os campos já existentes;
- score além de disponibilizar os mesmos campos com fallback;
- momentum;
- publicação;
- link de associado;
- scheduler;
- canais;
- política de seller/delivery;
- estratégia de produção para uso permitido das fontes Amazon.

Também não migra seller/delivery do parser legado para Jsoup. Essa correção estrutural continua sendo uma mudança separada para evitar mistura de escopos.

## 8. Regra de evolução

Mudanças futuras na precedência de fontes, nos seletores de customer reviews ou na semântica de provenance devem criar nova ADR ou substituir explicitamente esta decisão.

Não reescrever migrations históricas para acomodar novos tipos de evidência.
