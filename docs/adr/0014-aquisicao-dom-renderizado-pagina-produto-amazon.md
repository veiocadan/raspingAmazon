# ADR-0014 — DOM renderizado para aquisição da página individual da Amazon

- **Status:** Aceita
- **Data:** 2026-09-29
- **Projeto:** Rasping Amazon
- **Fases relacionadas:** FASE 7 — Enriquecimento da página individual; FASE 8.5 — Consolidação dos dados de decisão; FASE 17 — Agendamento e execução contínua; FASE 18 — Seleção operacional, canais e outbox
- **Complementa:** ADR-0007 e ADR-0013
- **Escopo:** aquisição real da página individual da Amazon em produção e em probes externas

---

## 1. Contexto

O Rasping Amazon separa a aquisição da página individual de produto da interpretação de seu conteúdo.

A fronteira arquitetural existente é:

```text
ProductPageContentProvider
        ↓
AmazonProductPageEnrichmentClient
        ↓
AmazonProductPageParser
AmazonCustomerReviewParser
AmazonPaymentConditionParser
```

Essa separação permite substituir a estratégia concreta de aquisição sem alterar contratos de enrichment, parsers, elegibilidade, filtros, score, ranking, publicação ou seleção operacional.

Historicamente, a composição operacional utilizou `HttpProductPageContentProvider` para adquirir a página individual. Essa implementação realiza uma requisição HTTP convencional e fornece aos parsers o HTML recebido da origem, sem reproduzir necessariamente o estado final apresentado por um navegador depois de execução de JavaScript e renderização dos componentes dinâmicos da buy box.

O projeto também possui uma estratégia baseada em navegador real, inicialmente introduzida para diagnóstico externo, por meio de Playwright/Chromium.

Durante a validação externa da FASE 18 foi demonstrado que essa diferença de aquisição afeta fatos comerciais essenciais da oferta.

---

## 2. Problema observado

Uma probe externa sobre ofertas reais provenientes de:

```text
https://www.amazon.com.br/deals
```

executou o fluxo:

```text
Amazon Deals
    ↓
ParsedDeal
    ↓
página individual
    ↓
AmazonProductPageEnrichmentClient
    ↓
OfferSnapshot
    ↓
elegibilidade
    ↓
filtros
    ↓
score
    ↓
ranking
```

A primeira versão utilizava `HttpProductPageContentProvider` nas páginas individuais.

O resultado apresentou número anormalmente alto de falhas de persistência relacionadas à ausência de:

```text
seller_name
delivery_provider
```

O enrichment produzia, em diversos casos:

```text
seller raw = null
seller type = UNKNOWN

delivery raw = null
delivery type = UNKNOWN
```

Apesar disso, a inspeção humana das mesmas páginas mostrava explicitamente informações como:

```text
Enviado / Vendido
Amazon.com.br
```

Foi estabelecida a divergência:

```text
navegador real
    → seller/delivery visíveis

HTTP bruto
    → seller/delivery frequentemente ausentes
```

---

## 3. Alternativas inicialmente consideradas

Antes do diagnóstico final foram consideradas três alternativas principais.

### 3.1 Relaxar o schema

Foi considerada uma migration para permitir:

```text
seller_name = NULL
delivery_provider = NULL
```

Essa alternativa partiria da premissa de que a ausência observada representava ausência real da evidência na Amazon.

A investigação posterior mostrou que essa premissa não explicava a maior parte dos casos observados.

### 3.2 Obter seller/delivery na página de Deals

Também foi considerada a criação de uma camada anterior:

```text
/deals
    ↓
seller/delivery
    ↓
página individual como fallback
```

Essa alternativa não foi adotada.

Seller e delivery pertencem semanticamente ao enriquecimento da oferta principal da página individual, e a própria página individual demonstrou fornecer esses fatos quando adquirida adequadamente.

### 3.3 Alterar o parser

Também foi investigada a hipótese de erro em `AmazonProductPageParser`.

Essa hipótese foi testada diretamente e rejeitada para os casos investigados.

---

## 4. Investigação com páginas reais

Foram selecionados ASINs que haviam apresentado falha na probe baseada em HTTP bruto.

Entre eles:

```text
B0GTRZFNJM
B0F5X4NPK2
B0GVT7QXF7
```

As páginas foram abertas manualmente e, nos três casos, a oferta principal apresentava vendedor e entrega da Amazon.

Os mesmos produtos foram carregados posteriormente por navegador automatizado via Playwright/Chromium.

O DOM renderizado continha estruturas esperadas pelo parser:

```text
merchantInfoFeature
fulfillerInfoFeature
Enviado / Vendido
Amazon.com.br
```

Portanto, a informação necessária existia no estado renderizado da página.

---

## 5. Validação do parser existente

Foi criada a probe:

```text
AmazonRenderedSellerDeliveryExternalProbeIT
```

Fluxo:

```text
PlaywrightRenderedProductPageContentProvider
        ↓
DOM renderizado
        ↓
AmazonProductPageParser
```

Para os três ASINs investigados, o parser existente produziu:

```text
Seller raw: Amazon.com.br
Seller type: AMAZON
Seller source: merchantInfoFeature

Delivery raw: Amazon
Delivery type: AMAZON
Delivery source: merchantInfoFeature
```

Isso demonstrou que `AmazonProductPageParser` já interpreta corretamente a estrutura observada quando recebe conteúdo adequado.

---

## 6. Validação do enrichment completo

Foi criada:

```text
AmazonRenderedProductEnrichmentExternalProbeIT
```

Fluxo:

```text
PlaywrightRenderedProductPageContentProvider
        ↓
AmazonProductPageEnrichmentClient
        ↓
AmazonProductPageParser
AmazonCustomerReviewParser
AmazonPaymentConditionParser
        ↓
ProductEnrichmentResult
```

Para o ASIN `B0GTRZFNJM`, o resultado observado foi:

```text
Seller raw: Amazon.com.br
Seller type: AMAZON
Seller source: merchantInfoFeature

Delivery raw: Amazon
Delivery type: AMAZON
Delivery source: merchantInfoFeature

Rating raw: 4,8 de 5 estrelas
Rating normalized: 4.8

Review count raw: 183 Análises
Review count normalized: 183

Payment conditions: 12
Source: AMAZON_PRODUCT_PAGE
```

A execução terminou com `BUILD SUCCESS`.

A prova confirmou a fronteira completa de enrichment sem alteração dos parsers existentes.

---

## 7. Validação em lote real

A probe `AmazonDealsRankingExternalProbeIT` foi reexecutada alterando somente a aquisição das páginas individuais.

A página de Deals permaneceu em HTTP:

```text
/deals
    ↓
HTTP
    ↓
AmazonDealsParser
```

As páginas individuais passaram a utilizar DOM renderizado:

```text
ParsedDeal
    ↓
Playwright / Chromium
    ↓
DOM renderizado
    ↓
AmazonProductPageEnrichmentClient
```

Foram inspecionadas 30 ofertas.

Resultado consolidado:

```text
Deals inspected: 30
Processing failures: 1
Ineligible after evaluation: 17
Eligible with score: 12
Daily quota simulated: 5
```

A seleção produziu:

```text
5 SELECTED
7 NOT_SELECTED_DUE_TO_QUOTA
```

A probe concluiu com `BUILD SUCCESS`.

A comparação com a execução baseada em HTTP bruto demonstrou melhora substancial na recuperação de seller/delivery e permitiu validar o fluxo real de ranking e quota.

---

## 8. Caso residual

A aquisição renderizada não implica garantia absoluta de presença de todos os fatos.

No lote de 30 ofertas houve uma exceção residual:

```text
ASIN B0GJFS2Y3W
```

Nesse caso seller/delivery permaneceram ausentes e a persistência falhou de forma explícita.

A conclusão desta ADR não é:

```text
Playwright sempre encontra seller/delivery
```

A conclusão é:

```text
DOM renderizado representa a fonte real de forma
substancialmente mais confiável que o HTML HTTP bruto
para os fatos comerciais da página individual.
```

Ausências residuais continuam devendo permanecer visíveis e investigáveis.

---

## 9. Decisão principal

A aquisição da página individual da Amazon em produção deverá utilizar DOM renderizado enquanto não existir fonte estruturada menos custosa e comprovadamente confiável.

A estratégia operacional deverá implementar a porta:

```text
ProductPageContentProvider
```

por meio de Playwright/Chromium ou implementação equivalente capaz de entregar o DOM renderizado necessário ao enrichment.

Fluxo produtivo alvo:

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
    ↓
seller
delivery
rating
reviewCount
payment conditions
```

A razão da decisão é funcional, não tecnológica:

```text
HTTP bruto
    → perda recorrente de fatos comerciais existentes

DOM renderizado
    → recuperação correta dos fatos na ampla maioria dos casos observados
```

---

## 10. A página de Deals permanece em HTTP

A decisão não implica renderizar toda a Amazon em navegador.

A página `/deals` continuou sendo adquirida com sucesso por HTTP durante a prova real.

Portanto:

```text
Deals
    → HTTP enquanto suficiente

página individual
    → DOM renderizado
```

A estratégia será escolhida por fronteira e pela confiabilidade observada da fonte.

---

## 11. O schema não será relaxado para esconder aquisição incompleta

A migration cogitada para permitir `seller_name` e `delivery_provider` nulos não será adotada como correção do problema diagnosticado.

Nos casos investigados, a cadeia problemática foi:

```text
dado existe
    ↓
HTTP bruto não o apresenta adequadamente
    ↓
parser não recebe evidência
    ↓
UNKNOWN / null
```

Relaxar a persistência nesse ponto transformaria falha de aquisição em estado aparentemente válido e poderia mascarar perda de evidência.

Isso não elimina a validade semântica de:

```text
SellerType.UNKNOWN
DeliveryType.UNKNOWN
```

quando a ausência de evidência for real.

---

## 12. UNKNOWN continua válido

`UNKNOWN` representa ausência de evidência suficiente.

A decisão desta ADR rejeita somente a equivalência automática:

```text
HTTP bruto não encontrou
=
Amazon não informou
```

quando existe uma estratégia capaz de observar o estado renderizado real da página.

Nenhum provider ou parser deverá inventar:

```text
seller = Amazon
delivery = Amazon
```

sem evidência observada.

---

## 13. Cadência operacional prevista

A carga maior do navegador renderizado foi considerada na decisão.

A operação prevista é executar a raspagem completa aproximadamente duas vezes por dia, com intervalo superior a dez horas entre os ciclos.

Nesse cenário, a prioridade é:

```text
corretude da evidência
>
latência mínima por produto
```

Se um lote de aproximadamente 450 produtos levar dezenas de minutos ou mais de uma hora, isso não inviabiliza a arquitetura atual.

O horário de início dos ciclos pode ser ajustado para acomodar a duração total do processamento.

A frequência não será hardcoded no provider. A responsabilidade continua pertencendo ao scheduler da FASE 17.

---

## 14. Execução sequencial e concorrência

Não existe requisito atual para máxima concorrência de browsers.

A implementação inicial deverá privilegiar:

```text
previsibilidade
isolamento
estabilidade
controle de recursos
baixo risco de challenge
```

A prova externa de 30 produtos foi executada sequencialmente em aproximadamente 2 minutos e 44 segundos.

Esse valor é apenas evidência operacional, não benchmark rígido.

O nível de concorrência futuro deverá ser guiado por medição.

---

## 15. Reutilização do browser

A implementação produtiva deverá evitar abrir um novo processo Chromium por produto quando o provider permitir reutilização segura.

Fluxo conceitual desejado:

```text
início do ciclo
    ↓
abre browser
    ↓
produto 1
produto 2
produto 3
...
produto N
    ↓
encerra browser
```

O lifecycle deve permanecer encapsulado na infraestrutura e não vazar para domínio ou regras de negócio.

---

## 16. Ambiente operacional futuro

Existe intenção de executar o sistema futuramente em hardware dedicado, incluindo Raspberry Pi dedicado ao Rasping Amazon.

Maior consumo de CPU, memória e tempo pelo browser não é, por si só, impeditivo no desenho atual porque:

- o equipamento será dedicado;
- a frequência de raspagem é baixa;
- existe grande intervalo entre ciclos;
- a prioridade é obter evidência comercial correta.

Essa é uma premissa operacional atual, não uma regra de domínio.

Caso medições reais demonstrem limitação do hardware escolhido, a infraestrutura poderá ser otimizada sem alterar a semântica desta ADR.

---

## 17. API estruturada é a evolução preferencial

Playwright resolve o problema funcional atual, mas possui custo maior que uma fonte estruturada.

A arquitetura deve permanecer aberta para substituir o provider renderizado por:

```text
API oficial
endpoint estruturado
fonte de dados estável
serviço de aquisição comprovadamente confiável
```

A substituição deverá preservar a porta:

```text
ProductPageContentProvider
```

E somente deverá ocorrer quando a alternativa demonstrar fornecer, com confiabilidade suficiente:

```text
seller
delivery
rating
reviewCount
payment conditions
demais evidências exigidas pelo contrato ativo
```

Critério de decisão:

```text
corretude
+
confiabilidade
+
auditabilidade
```

antes de:

```text
custo
latência
throughput
```

---

## 18. Tratamento de falhas de renderização

Uso de browser não elimina falhas externas.

A infraestrutura deverá continuar distinguindo:

```text
timeout
falha de navegação
challenge
conteúdo incompleto
browser encerrado
erro transitório da Amazon
produto indisponível
DOM sem evidência suficiente
```

Esses eventos não devem resultar em invenção de dados.

A política de retry permanece responsabilidade das camadas operacionais apropriadas.

---

## 19. Probes externas utilizarão a mesma estratégia real

Probes externas que dependam da página individual deverão reproduzir a estratégia de aquisição usada em produção.

Elas permanecerão separadas da suíte hermética padrão.

A suíte padrão continuará baseada em fixtures e providers controlados.

As probes reais continuarão sob:

```text
src/external-probe/java/
```

com execução deliberada via:

```text
-Pexternal-probe
```

---

## 20. Playwright deixa de ser exclusivamente dependência de probe

Como consequência desta decisão, a estratégia renderizada deixa de ser apenas mecanismo de diagnóstico.

A implementação produtiva deverá disponibilizar no runtime:

```text
biblioteca Playwright
Chromium compatível
dependências necessárias ao ambiente
```

O provider produtivo deverá residir em `src/main` e ser reutilizado pelas probes externas.

Não deverão existir duas implementações independentes de browser, uma para produção e outra para diagnóstico.

Fluxo desejado:

```text
src/main
    ↓
PlaywrightProductPageContentProvider
    ↓
produção
    ↓
probes externas reutilizam o mesmo provider
```

---

## 21. Relação com a FASE 18

A investigação ocorreu durante a FASE 18 porque a prova do fluxo de seleção precisava de um conjunto real suficientemente grande de ofertas processadas corretamente.

O problema encontrado pertence à aquisição/enrichment de uma fase anterior, mas não deve ser ocultado para concluir uma validação de seleção.

A cadeia correta permanece:

```text
aquisição correta
    ↓
enrichment confiável
    ↓
DealEvaluation confiável
    ↓
ranking
    ↓
PublicationSelectionPolicy
    ↓
quota
    ↓
outbox
```

A qualidade da entrada é pré-condição da seleção operacional.

---

## 22. Relação com quota

A probe final produziu 12 candidatos elegíveis com score e quota simulada de cinco.

Resultado:

```text
5 SELECTED
7 NOT_SELECTED_DUE_TO_QUOTA
```

Isso confirma que `NOT_SELECTED_DUE_TO_QUOTA` permanece decisão operacional e não rejeição comercial.

Os candidatos continuam válidos para nova seleção futura conforme ADR-0010 e ADR-0013.

---

## 23. Probes que fundamentaram a decisão

As seguintes probes integram a evidência desta ADR e serão mantidas versionadas:

```text
AmazonRenderedProductPageExternalProbeIT
AmazonRenderedSellerDeliveryExternalProbeIT
AmazonRenderedProductEnrichmentExternalProbeIT
AmazonDealsRankingExternalProbeIT
```

Elas não serão tratadas como código temporário descartável.

A sequência registrada é:

```text
página manual
    ↓
DOM renderizado
    ↓
parser
    ↓
enrichment completo
    ↓
pipeline real de 30 ofertas
    ↓
ranking
    ↓
quota
```

---

## 24. Consequências positivas

A decisão:

- preserva seller e delivery observados na oferta real;
- mantém a página individual como fonte desses fatos;
- evita duplicar seller/delivery em `/deals` sem necessidade demonstrada;
- reutiliza os parsers existentes;
- preserva a abstração `ProductPageContentProvider`;
- mantém domínio e regras de negócio desacoplados de Playwright;
- evita relaxar o schema para esconder aquisição incompleta;
- aceita maior latência em troca de dados corretos;
- é compatível com a baixa frequência operacional prevista;
- mantém aberta futura substituição por API estruturada;
- torna probes reais reproduzíveis e versionadas.

---

## 25. Custos e consequências negativas

A estratégia renderizada aumenta:

```text
uso de CPU
uso de memória
tempo por página
complexidade do runtime
dependência de browser
tamanho do ambiente
tempo de inicialização
```

Também introduz preocupações com lifecycle do Chromium, timeouts, challenges e compatibilidade de plataforma.

Esses custos são aceitos no cenário operacional atual porque corretude da informação tem prioridade sobre velocidade máxima da coleta.

---

## 26. O que esta ADR não fixa

Esta ADR não fixa:

```text
número exato de workers de browser
nível final de paralelismo
timeout definitivo
hardware definitivo
horários exatos dos ciclos
quantidade definitiva de produtos
política final de retry do browser
API futura específica
```

Esses parâmetros deverão ser ajustados por medição.

A decisão estável é:

```text
a página individual precisa ser adquirida por estratégia
capaz de observar o DOM renderizado enquanto não existir
fonte estruturada igualmente confiável.
```

---

## 27. Critérios de revisão

Esta ADR deverá ser revista se:

1. uma API oficial ou fonte estruturada fornecer de forma confiável todas as evidências necessárias;
2. o HTTP bruto passar a fornecer os fatos exigidos com confiabilidade equivalente;
3. a estratégia renderizada deixar de funcionar de forma aceitável;
4. medições demonstrarem inviabilidade operacional no hardware escolhido;
5. surgir provider alternativo mais leve e igualmente confiável;
6. a estrutura comercial da página individual mudar de forma incompatível com o parser atual.

---

## 28. Resumo da decisão

```text
PÁGINA DE DEALS

HTTP
    ↓
permanece enquanto confiável


PÁGINA INDIVIDUAL

HTTP bruto
    ↓
perda observada de seller/delivery
    ↓
não será a estratégia principal


PRODUÇÃO

Playwright / Chromium
    ↓
DOM renderizado
    ↓
ProductPageContentProvider
    ↓
AmazonProductPageEnrichmentClient
    ↓
seller
delivery
rating
reviewCount
payment conditions


OPERAÇÃO PREVISTA

~2 ciclos por dia
>10 horas entre ciclos
centenas de produtos
latência maior aceitável
hardware dedicado previsto


FUTURO

API/fonte estruturada confiável
    ↓
novo ProductPageContentProvider
    ↓
substitui Playwright
    ↓
sem alterar domínio nem regras de negócio
```

A decisão final é:

```text
corretude da evidência comercial
tem precedência sobre o menor custo
de aquisição da página.
```
