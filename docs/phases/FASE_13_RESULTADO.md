# FASE 13 — RESULTADO CONSOLIDADO

**Projeto:** Rasping Amazon  
**Fase:** 13 — Geração de publicação e link de associado  
**Data:** 23/09/2026  
**Status:** CONCLUÍDA — implementação, persistência, idempotência, composição vertical e validação local aprovadas

---

## 1. Objetivo

A FASE 13 teve como objetivo transformar uma `DealEvaluation` já persistida em uma `Publication` reproduzível, auditável e persistida, pronta para revisão posterior.

A fase não reexecuta coleta, enrichment, elegibilidade, filtros, score ou momentum.

Seu ponto de entrada é uma avaliação já existente:

```text
DealEvaluation persistida
        ↓
PublicationGenerator
        ↓
Publication persistida
```

O fluxo resultante passou a possuir separação explícita entre:

```text
dados persistidos
        ↓
política de apresentação comercial
        ↓
link de associado
        ↓
template
        ↓
Publication
        ↓
persistência idempotente
```

---

## 2. Resultado geral

Ao final da FASE 13, o projeto possui geração de publicação versionada e auditável.

Foram implementados:

- contrato de dados de origem da publicação;
- reconstrução dos dados exclusivamente a partir do estado persistido;
- política comercial específica para apresentação;
- template de publicação versionado;
- geração encapsulada de link de associado;
- configuração do identificador de associado fora do código;
- evolução auditável da entidade `Publication`;
- migration V14;
- identidade idempotente da geração;
- repository de publicação;
- persistência JDBC idempotente;
- `PublicationGenerator`;
- composition root dedicado;
- teste vertical real com PostgreSQL;
- validação de reentrada da mesma geração;
- separação entre publicação e canais de entrega.

Fluxo final:

```text
DealEvaluation
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

Baseline final:

```text
Tests run: 558
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

Flyway:

```text
14 migrations validadas
schema version = 14
```

---

## 3. ADR da FASE 13

A semântica da geração foi formalizada em:

```text
docs/adr/0004-semantica-geracao-publicacao-e-link-associado.md
```

Data consolidada:

```text
2026-09-23
```

O ADR estabelece que a geração de publicação:

- é um caso de uso independente;
- utiliza somente fatos já persistidos e auditáveis;
- não consulta novamente a Amazon;
- não inventa dados ausentes;
- mantém apresentação comercial separada da elegibilidade e dos filtros;
- mantém template separado dos dados;
- encapsula a geração do link de associado;
- registra as versões utilizadas;
- persiste a publicação antes de qualquer futura entrega;
- não incorpora canais ou scheduler.

A publicação inicial permanece em:

```text
CREATED
```

O estado:

```text
READY
```

fica reservado para etapa posterior de revisão/liberação.

---

## 4. Dados de origem da publicação

Foi criado:

```text
PublicationData
```

O objeto representa o conjunto de fatos necessário para geração da publicação a partir de uma `DealEvaluation` persistida.

A entrada de geração não recebe dados comerciais arbitrários vindos da interface.

Fluxo:

```text
dealEvaluationId
        ↓
PublicationDataQueryPort
        ↓
persistência
        ↓
PublicationData
```

`PublicationData` exige identidade persistida válida para:

```text
DealEvaluation
OfferSnapshot
Product
```

Isso impede que a publicação seja construída a partir de objetos transitórios sem vínculo auditável.

---

## 5. Reconstrução a partir do PostgreSQL

Foi criado o adapter:

```text
JdbcPublicationDataQueryAdapter
```

Ele carrega a avaliação persistida e utiliza a infraestrutura existente para reconstruir o `OfferSnapshot`.

O snapshot é reconstruído com:

- produto;
- preços;
- rating;
- review count;
- seller;
- delivery;
- payment conditions;
- evidências persistidas;
- metadados de origem.

A geração não executa:

```text
HTTP
HTML parsing
enrichment
nova consulta à Amazon
```

Portanto:

```text
FASE 13
=
transformação de estado persistido
```

e não nova aquisição de dados externos.

---

## 6. Política de apresentação comercial

A apresentação comercial foi separada das regras de elegibilidade e filtragem.

Contrato:

```text
CommercialPresentationPolicy
```

Implementação inicial:

```text
AmazonCommercialPresentationV1
```

Versão:

```text
AMAZON_COMMERCIAL_PRESENTATION_V1
```

A política segue a semântica comercial já definida anteriormente.

Para condições à vista:

```text
NuPay > Pix
    → destaca NuPay
    → Pix pode aparecer como alternativa

Pix > NuPay
    → destaca Pix

Pix = NuPay
    → destaca Pix

somente uma condição disponível
    → apresenta somente a condição existente
```

Ausência de uma condição não é convertida em preço zero.

Para parcelamento:

```text
preferir maior quantidade de parcelas sem juros
```

quando houver condição sem juros disponível.

A apresentação preserva os valores efetivamente observados.

---

## 7. Separação entre filtro e apresentação

Uma decisão importante da fase é:

```text
melhor condição para filtro
≠
necessariamente texto de publicação
```

A política comercial de publicação não altera:

```text
eligibilidade
filtro
score
ranking
momentum
```

Ela somente decide como fatos já aceitos serão apresentados ao leitor.

Essa separação evita que uma mudança editorial modifique retroativamente a decisão de seleção da oferta.

---

## 8. Template versionado

Foi criado o contrato:

```text
PublicationTemplate
```

Entrada:

```text
PublicationTemplateInput
```

Implementação inicial:

```text
AmazonPublicationV1
```

Versão:

```text
AMAZON_PUBLICATION_V1
```

O template recebe dados prontos para apresentação e não possui responsabilidade por:

```text
SQL
JDBC
filtros
score
geração do link de associado
Telegram
WhatsApp
```

O template não inventa campos ausentes.

A geração textual é determinística para a mesma entrada.

---

## 9. Link de associado

A geração do link foi encapsulada em:

```text
AffiliateLinkGenerator
```

Valor retornado:

```text
AffiliateLink
```

Implementação inicial:

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

O template recebe o link pronto.

Ele não conhece:

```text
associate tag
query parameters
estratégia de construção da URL
```

---

## 10. Configuração do associado

O identificador do associado não foi colocado diretamente no código.

Foram criados:

```text
AmazonAffiliateConfig
AmazonAffiliateConfigProvider
```

Variável externa:

```text
AMAZON_ASSOCIATE_TAG
```

O `.env.example` foi atualizado para documentar a configuração.

A configuração de associado permanece separada de `ApplicationConfig`, evitando tornar fluxos não relacionados à publicação dependentes dessa configuração.

---

## 11. Identificação do link patrocinado

O template utiliza identificação explícita:

```text
Link patrocinado:
```

Exemplo:

```text
Link patrocinado: https://www.amazon.com.br/dp/ASIN?tag=...
```

A identificação faz parte do template de publicação e não da lógica de construção da URL.

---

## 12. Evolução do domínio `Publication`

`Publication` passou a preservar explicitamente:

```text
templateVersion
commercialPresentationVersion
affiliateLinkVersion
generatedText
affiliateUrl
status
createdAt
```

Além da associação com:

```text
DealEvaluation
```

O link de associado passou a ser obrigatório para publicações produzidas pelo novo fluxo.

Status inicial:

```text
CREATED
```

Os estados existentes permanecem:

```text
CREATED
READY
PUBLISHED
FAILED
```

A FASE 13 não altera o significado dos estados posteriores.

---

## 13. Migration V14

Foi criada:

```text
V14__publication_generation_audit.sql
```

A migration adiciona:

```text
commercial_presentation_version
affiliate_link_version
```

à tabela:

```text
publication
```

Registros históricos anteriores à migration são marcados com:

```text
LEGACY
```

antes da aplicação de `NOT NULL`.

Isso permite upgrade de bancos existentes sem editar migrations antigas.

A política histórica de migrations do projeto foi preservada:

```text
V1
 ↓
...
 ↓
V13
 ↓
V14
```

Nenhuma migration aplicada anteriormente foi modificada.

---

## 14. Identidade idempotente da publicação

A identidade da geração passou a ser:

```text
deal_evaluation_id
+
template_version
+
commercial_presentation_version
+
affiliate_link_version
```

A V14 cria proteção única no PostgreSQL para essa combinação.

Consequência:

```text
mesma avaliação
+
mesmo template
+
mesma política de apresentação
+
mesma estratégia de afiliado
=
mesma geração lógica
```

Executar novamente essa geração não cria uma segunda linha.

---

## 15. Persistência idempotente

Contrato:

```text
PublicationRepository
```

Implementação:

```text
PublicationJdbcRepository
```

A persistência utiliza:

```sql
INSERT ...
ON CONFLICT (...)
DO NOTHING
RETURNING id
```

O fluxo não utiliza:

```text
SELECT
↓
se não existir
↓
INSERT
```

como mecanismo principal de idempotência.

A proteção definitiva permanece no PostgreSQL.

Quando já existe uma publicação para a mesma identidade:

```text
INSERT não cria nova linha
        ↓
repository lê publicação existente
        ↓
retorna fato histórico original
```

O conteúdo histórico não é sobrescrito.

---

## 16. Semântica da reentrada

Se a primeira execução persistir:

```text
Publication #123
texto A
timestamp A
```

e o mesmo caso de uso for executado novamente com a mesma identidade versionada:

```text
Publication #123
texto A
timestamp A
```

é devolvida.

A segunda execução não deve produzir:

```text
Publication #124
```

nem atualizar silenciosamente o texto persistido.

Isso preserva a auditabilidade da geração original.

---

## 17. PublicationGenerator

Foi criado:

```text
PublicationGenerator
```

Entrada:

```text
dealEvaluationId
```

Responsabilidades:

1. validar o identificador;
2. carregar `PublicationData`;
3. aplicar `CommercialPresentationPolicy`;
4. gerar `AffiliateLink`;
5. renderizar `PublicationTemplate`;
6. criar `Publication` em `CREATED`;
7. persistir via `PublicationRepository`;
8. devolver a publicação persistida.

Fluxo:

```text
dealEvaluationId
      ↓
PublicationData
      ↓
CommercialPresentation
      ↓
AffiliateLink
      ↓
generatedText
      ↓
Publication CREATED
      ↓
PublicationRepository
```

---

## 18. Clock injetável

O horário de geração não utiliza uma dependência oculta do relógio do sistema dentro do caso de uso.

`PublicationGenerator` recebe:

```text
Clock
```

e calcula:

```text
OffsetDateTime.now(clock)
```

Isso permite testes determinísticos.

Em produção:

```text
Clock.systemUTC()
```

pode ser fornecido pela composição.

---

## 19. Composition root

Foi criado:

```text
AmazonPublicationComposition
```

Ele monta:

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

A composição:

- não contém regra de negócio;
- não renderiza texto;
- não constrói URLs manualmente;
- não executa SQL diretamente;
- não executa o caso de uso.

Ela apenas conecta implementações concretas.

---

## 20. Composição separada do processamento de ofertas

A FASE 13 não ampliou `AmazonDealProcessingComposition` para incluir publicação.

Foi criado um composition root específico.

Separação:

```text
AmazonDealProcessingComposition
        ↓
coleta / parsing / enrichment / avaliação

AmazonPublicationComposition
        ↓
geração de publicação
```

Essa decisão evita que a futura interface operacional precise construir:

```text
HTTP client
collector
parser
enrichment
```

somente para gerar ou revisar uma publicação.

---

## 21. Teste vertical com PostgreSQL

Foi criado:

```text
AmazonPublicationCompositionTest
```

O teste utiliza componentes concretos do fluxo.

Caminho validado:

```text
PostgreSQL
      ↓
DealEvaluation persistida
      ↓
JdbcPublicationDataQueryAdapter
      ↓
JdbcOfferSnapshotEvaluationLoadAdapter
      ↓
PublicationData
      ↓
AmazonCommercialPresentationV1
      ↓
AmazonAffiliateLinkGeneratorV1
      ↓
AmazonPublicationV1
      ↓
PublicationGenerator
      ↓
PublicationJdbcRepository
      ↓
PostgreSQL
```

O teste executa a geração duas vezes.

Resultado esperado e confirmado:

```text
primeira execução
    → Publication persistida

segunda execução
    → mesma Publication

COUNT(publication)
    → 1
```

---

## 22. Versionamento auditável

A FASE 13 possui três versões independentes:

```text
AMAZON_COMMERCIAL_PRESENTATION_V1
AMAZON_PUBLICATION_V1
AMAZON_AFFILIATE_LINK_V1
```

Esses conceitos não foram unidos em uma única versão genérica.

Isso permite evoluções independentes.

Exemplo:

```text
mesmo template
+
nova política comercial
```

pode produzir uma nova identidade auditável sem precisar alterar artificialmente a versão do template.

Da mesma forma:

```text
nova estratégia de link
```

pode ser versionada sem modificar a política comercial.

---

## 23. Relação com a avaliação

A publicação mantém referência para:

```text
deal_evaluation_id
```

A partir da avaliação persistida é possível reconstruir a cadeia:

```text
Publication
    ↓
DealEvaluation
    ↓
OfferSnapshot
    ↓
Product
```

A avaliação também preserva suas próprias versões e resultados das fases anteriores.

Assim, a publicação não replica desnecessariamente todos os fatos históricos.

---

## 24. O que NÃO foi implementado

A FASE 13 não implementou:

- interface operacional;
- aprovação manual;
- tela de revisão;
- scheduler;
- execução periódica;
- `PublicationChannel`;
- outbox de canais;
- WhatsApp;
- Telegram;
- envio automático;
- provider reference;
- retry de canal;
- métricas avançadas;
- dashboards;
- alertas;
- hardening de produção.

Essas responsabilidades permanecem em fases posteriores.

Ocorrências textuais de:

```text
Telegram
WhatsApp
scheduler
```

existentes no código da FASE 13 aparecem em documentação/comentários indicando explicitamente que esses componentes não são conhecidos pelos componentes de publicação.

O método:

```text
scheduleRetry
```

existente na orquestração da FASE 12 representa reagendamento de um job após falha e não constitui um scheduler operacional da FASE 17.

---

## 25. Separação de responsabilidades final

A arquitetura consolidada é:

```text
Persistence
    ↓
PublicationData

Presentation policy
    ↓
CommercialPresentation

Affiliate strategy
    ↓
AffiliateLink

Template
    ↓
generatedText

Application
    ↓
PublicationGenerator

Persistence port
    ↓
PublicationRepository

Infrastructure
    ↓
PublicationJdbcRepository
```

Cada componente possui uma responsabilidade específica.

---

## 26. Testes específicos da FASE 13

Foram adicionados testes para:

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

Entre os comportamentos validados:

- dados persistidos obrigatórios;
- reconstrução via PostgreSQL;
- regras Pix/NuPay;
- parcelamento;
- dados opcionais ausentes;
- formatação monetária;
- template determinístico;
- link versionado;
- validação de host e HTTPS;
- remoção de query antiga;
- remoção de fragment;
- versões de auditoria;
- obrigatoriedade do link;
- persistência JDBC;
- identidade idempotente;
- preservação do registro original;
- entrada inválida do generator;
- avaliação inexistente;
- uso de `Clock`;
- composição real;
- execução vertical;
- reentrada idempotente.

---

## 27. Gate final

Foi executado:

```text
.\mvnw.cmd clean test
```

Resultado:

```text
Tests run: 558
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

Compilação:

```text
167 source files
113 test source files
```

PostgreSQL utilizado nos testes:

```text
PostgreSQL 18.6
```

Flyway:

```text
Successfully validated 14 migrations
Current version of schema "public": 14
Schema "public" is up to date
```

---

## 28. Migrations consolidadas

Estado ao encerramento:

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

Schema atual:

```text
14
```

---

## 29. Commits da FASE 13

A implementação foi dividida em commits de responsabilidade clara:

```text
683879a docs: formaliza semantica da geracao de publicacao
6384513 feat: define dados de origem para publicacao
031d37e feat: carrega dados persistidos para publicacao
2a17a06 feat: implementa politica comercial de publicacao
6053326 feat: implementa template versionado de publicacao
eabf555 feat: implementa geracao de link de associado
8cc22b1 feat: versiona auditoria de geracao de publicacao
479d67d feat: persiste publicacao de forma idempotente
a37708b feat: integra geracao de publicacao
5bdc246 test: valida fluxo vertical de publicacao
```

A correção da data do ADR faz parte do encerramento documental da fase.

---

## 30. Critérios de conclusão

| Critério | Resultado |
|---|---|
| `PublicationData` definido | CONCLUÍDO |
| Dados carregados da persistência | CONCLUÍDO |
| Nova consulta à Amazon durante publicação | NÃO |
| Política comercial separada | CONCLUÍDO |
| Política comercial versionada | CONCLUÍDO |
| `AMAZON_COMMERCIAL_PRESENTATION_V1` | CONCLUÍDO |
| Template desacoplado | CONCLUÍDO |
| Template versionado | CONCLUÍDO |
| `AMAZON_PUBLICATION_V1` | CONCLUÍDO |
| Link de associado encapsulado | CONCLUÍDO |
| Link versionado | CONCLUÍDO |
| `AMAZON_AFFILIATE_LINK_V1` | CONCLUÍDO |
| Associate tag fora do código | CONCLUÍDO |
| `Publication` auditável | CONCLUÍDO |
| V14 criada | CONCLUÍDO |
| Migrations anteriores preservadas | CONCLUÍDO |
| Identidade idempotente no PostgreSQL | CONCLUÍDO |
| `PublicationRepository` | CONCLUÍDO |
| `PublicationJdbcRepository` | CONCLUÍDO |
| Reentrada preserva publicação original | CONCLUÍDO |
| `PublicationGenerator` | CONCLUÍDO |
| `Clock` injetável | CONCLUÍDO |
| Composition root dedicado | CONCLUÍDO |
| Teste vertical PostgreSQL | CONCLUÍDO |
| Publicação inicial `CREATED` | CONCLUÍDO |
| Telegram antecipado | NÃO |
| WhatsApp antecipado | NÃO |
| Scheduler antecipado | NÃO |
| Envio automático antecipado | NÃO |
| Flyway | 14 migrations |
| Schema | versão 14 |
| `mvn clean test` | PASSOU |
| Testes | 558 |
| Falhas | 0 |
| Erros | 0 |
| Ignorados | 0 |
| Build | SUCCESS |

---

## 31. Resultado final

**FASE 13 — CONCLUÍDA.**

O Rasping Amazon agora consegue transformar uma avaliação persistida em uma publicação versionada, auditável e idempotente.

Estado resultante:

```text
DealEvaluation
        ↓
PublicationData
        ↓
AMAZON_COMMERCIAL_PRESENTATION_V1
        ↓
AMAZON_AFFILIATE_LINK_V1
        ↓
AMAZON_PUBLICATION_V1
        ↓
PublicationGenerator
        ↓
Publication CREATED
        ↓
PublicationJdbcRepository
        ↓
PostgreSQL
```

Auditabilidade:

```text
DealEvaluation
Template version
Commercial presentation version
Affiliate link version
Generated text
Affiliate URL
Created at
```

Idempotência:

```text
deal_evaluation_id
+
template_version
+
commercial_presentation_version
+
affiliate_link_version
```

A publicação permanece desacoplada de qualquer canal de entrega.

---

## 32. Regra principal da FASE 13

> **Gerar publicação somente a partir de fatos persistidos e auditáveis.**
>
> **Não consultar novamente a Amazon durante a geração.**
>
> **Não transformar regra de apresentação em regra de elegibilidade.**
>
> **Não inventar informação comercial ausente.**
>
> **Versionar política, template e estratégia de link separadamente.**
>
> **Persistir antes de qualquer futura entrega.**
>
> **Preservar idempotência no PostgreSQL.**
>
> **Manter publicação desacoplada dos canais.**

---

## 33. Próxima fase

A próxima etapa oficial é:

```text
FASE 14 — Interface operacional
```

A interface poderá consumir os casos de uso já existentes sem acessar diretamente:

```text
collector
parser
JDBC
Amazon HTML
canais
```

Entre as responsabilidades esperadas da FASE 14 estão operações como:

```text
listar ofertas processadas
consultar avaliações
selecionar uma avaliação
gerar publicação
visualizar publicação
revisar/liberar publicação
```

A interface deverá permanecer uma camada de operação sobre casos de uso existentes.

Não deve reimplementar:

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

## 34. Registro de encerramento

**Data:** 23/09/2026  
**Fase:** 13  
**Status:** CONCLUÍDA  
**Build:** SUCCESS  
**Testes:** 558 — 0 falhas — 0 erros — 0 ignorados  
**PostgreSQL:** OK  
**Flyway:** 14 migrations validadas  
**Schema:** versão 14  
**Migration da fase:** `V14__publication_generation_audit.sql`  
**ADR:** `0004-semantica-geracao-publicacao-e-link-associado.md`  
**Próxima fase:** FASE 14 — Interface operacional
