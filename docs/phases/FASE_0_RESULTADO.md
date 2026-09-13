# FASE 0 — Levantamento da Fonte e das Regras

**Projeto:** Rasping Amazon  
**Fase:** 0 — Levantamento da fonte e das regras  
**Data:** 2026-09-13  
**Status:** CONCLUÍDA — resultado técnico documentado  
**Documento fundamental:** `rasping_Amazon_projeto_revisado.pdf`

---

## 1. Objetivo

A FASE 0 tem como objetivo identificar as fontes reais de dados, documentar de onde cada campo será obtido, registrar as regras de elegibilidade, limitações técnicas, dependências e riscos, e definir uma estratégia segura de fallback.

O encerramento desta fase exige:

1. Mapa Fonte → Campo aprovado;
2. lista de dependências externas e riscos conhecidos;
3. decisão documentada sobre a origem de cada dado crítico.

Esses critérios estão alinhados ao documento fundamental do projeto.

---

## 2. Fonte de promoções

### 2.1 Fonte funcional

A página de promoções observada é:

`https://www.amazon.com.br/deals`

A página apresenta ofertas da Amazon Brasil e possui comportamento dinâmico.

### 2.2 Comportamento observado

Durante a investigação foi observado que:

- as ofertas são carregadas dinamicamente;
- novos lotes aparecem conforme a página é percorrida;
- o botão **“Ver mais ofertas”** pode provocar novas cargas;
- o conjunto de ofertas pode mudar entre carregamentos diferentes;
- uma oferta presente em uma execução pode não aparecer em outra;
- a quantidade de elementos `product-card` presentes no DOM não representa necessariamente a quantidade total de ofertas já carregadas, devido ao comportamento de virtualização/reutilização do DOM.

### 2.3 Consequência arquitetural

A página não será tratada como um HTML estático cuja quantidade de cards determine o fim da coleta.

A coleta deverá ficar isolada em um adaptador (`amazon.collector`), conforme a arquitetura definida no projeto.

---

## 3. Fonte técnica observada

Durante a investigação do comportamento da página foi identificada uma fonte JSON interna utilizada pela própria aplicação web:

`GET https://www.amazon.com.br/d2b/api/v1/products/search`

Parâmetros observados durante as requisições:

- `pageSize`;
- `startIndex`;
- `rankingContext`;
- `filters`;
- `promotionTypes`;
- `accessTypes`;
- `brandIds`;
- `unifiedIds`;
- `pinnedPromotionGroups`;
- `pinnedPromotionsLayoutGroup`.

Foi observado `pageSize=30` e diferentes valores de `startIndex`, incluindo valores como `30`, `360` e `480`.

### 3.1 Classificação da fonte

**OBSERVADA TECNICAMENTE — NÃO APROVADA AUTOMATICAMENTE COMO FONTE DE PRODUÇÃO.**

A existência do endpoint foi comprovada tecnicamente durante a investigação, mas isso não significa, por si só, autorização para uso automatizado em produção.

A implementação definitiva deverá priorizar interfaces oficiais da Amazon quando elas fornecerem os dados necessários e deverá respeitar as regras contratuais e técnicas aplicáveis.

---

## 4. Mapa Fonte → Campo

| Campo do sistema | Fonte observada | Tipo | Obrigatório | Regra |
|---|---|---|---|---|
| `asin` | API / DOM / URL do produto | direto/normalizado | SIM | Deve existir evidência confiável |
| `title` | API / DOM | direto | SIM | Título do produto |
| `image_url` | API / DOM | direto | NÃO | Imagem disponível |
| `product_url` | API / DOM | normalizado | SIM | URL canônica do produto |
| `current_price` | API / oferta | direto | SIM | Preço atual da oferta |
| `previous_price` | API / oferta | direto | NÃO | Preço anterior/base quando disponível |
| `discount_percentage` | preço atual + preço anterior / oferta | derivado | NÃO | Não inventar quando faltarem dados |
| `sold_percentage` | `dealDetails.percentClaimed` | direto | NÃO | Usar quando disponível |
| `rating` | fonte de produto apropriada | direto | NÃO | Enriquecimento posterior |
| `review_count` | fonte de produto apropriada | direto | NÃO | Enriquecimento posterior |
| `seller_name` | página do produto | direto | SIM | Necessário para elegibilidade |
| `delivery_provider` | página do produto | direto | SIM | Necessário para elegibilidade |
| `source` | sistema/coletor | derivado | SIM | Identifica a origem efetiva |
| `collected_at` | sistema | derivado | SIM | Data/hora da coleta |

---

## 5. ASIN

O ASIN foi identificado como o identificador confiável do produto/oferta.

Fontes observadas:

- atributo `data-asin` nos cards;
- campo `asin` no objeto JSON da fonte observada;
- ASIN incorporado ao link do produto (`/dp/{ASIN}`).

### Regra

O sistema somente aceitará um ASIN quando houver evidência suficiente.

Não será permitido inferir ou adivinhar ASIN a partir de título, imagem ou outros dados incompletos.

### Identificação

Exemplo observado:

`B0FN4BK3V7`

O formato e a presença do ASIN devem ser tratados pelo parser/normalizador, enquanto a decisão de negócio permanece no domínio.

---

## 6. Percentual de vendidos

O indicador de popularidade da promoção foi localizado tanto visualmente quanto em dados estruturados.

### 6.1 Fonte preferencial

Campo estruturado:

`dealDetails.percentClaimed`

Exemplo observado:

```json
{
  "dealDetails": {
    "percentClaimed": 48.0,
    "percentClaimedMessage": "48% comprados"
  }
}
```

Também foram observados valores como `83`, `25`, `66`, `2` e `21`.

### 6.2 Fonte visual

No DOM foi encontrado o componente associado a `ClaimedBar`, com textos como:

- `48% comprados`;
- `83% comprados`.

Foram observadas duplicações desses elementos em diferentes representações do DOM.

### 6.3 Decisão

A prioridade será:

1. `dealDetails.percentClaimed`;
2. informação estruturada equivalente, caso a primeira deixe de existir;
3. texto visual somente como fallback controlado;
4. `null` quando não houver evidência suficiente.

O sistema **não estimará** percentual de vendidos.

---

## 7. Vendedor e entrega

A validação de vendedor e entrega será independente da identificação da promoção.

Foram observados diferentes layouts na página de produto.

### 7.1 Amazon / Amazon

Caso observado:

- `Enviado / Vendido`;
- `merchant-trust-info-card = Amazon.com.br`.

Interpretação:

- vendedor = AMAZON;
- entrega = AMAZON.

Resultado:

**ELEGÍVEL**, desde que os demais critérios também sejam satisfeitos.

### 7.2 Terceiro / Amazon

Caso observado:

- `Vendido por Automaficial`;
- `Enviado pela Amazon`.

Interpretação:

- vendedor = THIRD_PARTY;
- entrega = AMAZON.

Resultado:

**REJEITADO**.

Esse caso é importante porque comprova que “Enviado pela Amazon” não significa que o produto seja vendido pela Amazon.

### 7.3 Terceiro / Terceiro

Caso observado:

- `Enviado / Vendido Acer Brasil`.

Interpretação:

- vendedor = THIRD_PARTY;
- entrega = THIRD_PARTY.

Resultado:

**REJEITADO**.

### 7.4 Informação desconhecida

Se seller ou delivery não puderem ser determinados com segurança:

- `seller_type = UNKNOWN`;
- `delivery_type = UNKNOWN`;
- oferta = **REJEITADA**.

### 7.5 Regra definitiva

Somente a combinação abaixo satisfaz o requisito:

```text
seller_type == AMAZON
AND
delivery_type == AMAZON
```

Qualquer combinação diferente deve ser rejeitada.

---

## 8. Campos obrigatórios

Para o processamento inicial, os campos considerados críticos são:

- `asin`;
- `product_url`;
- `current_price`;
- `seller_name`;
- `delivery_provider`;
- `collected_at`;
- `source`.

A ausência de informação necessária para uma decisão de elegibilidade deve resultar em falha fechada, e não em aprovação por suposição.

---

## 9. Campos opcionais

São considerados opcionais na primeira etapa:

- `image_url`;
- `previous_price`;
- `sold_percentage`;
- `rating`;
- `review_count`.

A ausência de um campo opcional não deve, por si só, provocar fabricação de dados.

---

## 10. Campos derivados

Serão derivados/normalizados pelo sistema:

- `discount_percentage`;
- `seller_type`;
- `delivery_type`;
- `eligible`;
- `rejection_reason`;
- versões de regras/filtros;
- score;
- momentum.

O domínio será responsável pelas decisões de negócio e não deverá conhecer detalhes de HTML, SQL específico ou APIs externas.

---

## 11. Paginação e comportamento de carregamento

### 11.1 O que foi observado

A fonte técnica apresentou requisições com:

```text
pageSize=30
startIndex=30
startIndex=360
startIndex=480
```

Em algumas respostas foi observado `nextIndex`.

Em uma requisição posterior, não foi observado `nextIndex`, enquanto a interface chegou ao final visual da sequência disponível naquele carregamento.

### 11.2 Decisão

Não considerar `promotionsSearchStartIndex` na URL pública da página `/deals` como mecanismo confiável de paginação.

Também não assumir um limite global fixo de ofertas.

A implementação futura deverá determinar o avanço usando a resposta real da fonte e suas condições de continuidade, com tratamento para:

- resposta sem produtos;
- ausência de continuidade;
- repetição de resultados;
- mudanças de estrutura;
- timeout;
- resposta incompleta.

A estratégia definitiva será implementada e testada na FASE 5, não nesta fase.

---

## 12. Normalização e deduplicação

A unidade de identificação do produto será o ASIN.

O sistema deverá:

1. normalizar o ASIN;
2. normalizar URLs;
3. deduplicar produtos por ASIN;
4. preservar ocorrências/snapshots distintos quando representarem coletas diferentes.

A arquitetura do projeto exige histórico e múltiplas ocorrências da mesma oferta.

---

## 13. Fallbacks

### ASIN

```text
API estruturada
→ atributo data-asin
→ ASIN presente no URL /dp/{ASIN}
→ rejeitar/descartar se inconclusivo
```

### `% vendidos`

```text
dealDetails.percentClaimed
→ dado estruturado equivalente
→ texto visual
→ null
```

### Seller

```text
merchantInfoFeature
→ informação equivalente da página
→ UNKNOWN
```

### Delivery

```text
fulfillerInfoFeature
→ combinação "Enviado / Vendido"
→ UNKNOWN
```

### Preço

```text
fonte estruturada
→ informação estruturada da oferta
→ indisponível
```

Fallbacks não poderão criar ou estimar informações que não estejam disponíveis.

---

## 14. Limitações técnicas

Foram identificadas as seguintes limitações:

1. A página de Deals é dinâmica.
2. A lista de ofertas pode variar entre carregamentos.
3. O DOM pode ser virtualizado.
4. `product-card` no DOM não representa necessariamente o total de ofertas processadas.
5. A estrutura interna da API pode mudar.
6. A estrutura HTML pode mudar.
7. Seller e fulfillment podem aparecer em layouts diferentes.
8. Alguns campos podem estar ausentes.
9. `nextIndex` não foi observado em todas as respostas.
10. A paginação pública por parâmetros da URL não é confiável.
11. A fonte interna observada não deve ser considerada automaticamente autorizada para produção.
12. A disponibilidade e o conteúdo das ofertas são temporários.

---

## 15. Regras do Programa de Associados e integrações

A arquitetura deve considerar as regras aplicáveis ao Programa de Associados da Amazon.

Decisão arquitetural:

- preferir API/interface oficial quando ela fornecer os dados necessários;
- manter a separação entre descoberta de ofertas e enriquecimento oficial;
- não assumir que a observação técnica de um endpoint interno equivale a autorização de uso;
- validar a permissibilidade da fonte antes da utilização automatizada em produção;
- manter credenciais e segredos fora do código;
- preservar rastreabilidade das decisões e das fontes utilizadas.

A geração do link de associado pertence ao fluxo posterior de publicação e não faz parte da coleta inicial.

---

## 16. Dados armazenáveis e retenção

O projeto exige que a política de armazenamento e retenção seja definida.

### Dados previstos para persistência

- ASIN;
- produto;
- preço;
- preço anterior;
- desconto;
- percentual de vendidos;
- rating;
- quantidade de avaliações;
- vendedor;
- provedor de entrega;
- origem;
- data/hora da coleta;
- avaliação;
- motivo de rejeição;
- score;
- momentum;
- dados de publicação;
- tentativas de publicação;
- informações necessárias para auditoria.

### Artefatos brutos

HTML bruto, respostas completas ou outros artefatos de diagnóstico deverão ser armazenados apenas quando necessários para investigação e conforme política de retenção definida para o ambiente.

### Retenção

**PENDÊNCIA CONTROLADA:** o prazo exato de retenção deverá ser definido na fase de persistência/configuração, considerando necessidade operacional, auditoria e regras aplicáveis.

Até essa definição, não deve ser implementada uma retenção arbitrária como regra de negócio.

---

## 17. Dependências externas

Dependências identificadas:

- Amazon Brasil;
- fonte de promoções;
- eventual API oficial de produto/afiliados;
- credenciais e configuração do Programa de Associados, quando aplicável;
- PostgreSQL;
- mecanismo de execução agendada/fila em fases futuras;
- canais de publicação futuros;
- serviços de observabilidade.

---

## 18. Riscos conhecidos

### R001 — Mudança da fonte

A Amazon pode alterar HTML, JSON, endpoint, parâmetros ou comportamento da página.

**Mitigação:** collector e parser isolados, testes e observabilidade.

### R002 — Mudança do conjunto de ofertas

A lista pode variar entre execuções.

**Mitigação:** tratar cada execução como snapshot e persistir histórico.

### R003 — Virtualização do DOM

O DOM pode não conter simultaneamente todos os produtos já apresentados.

**Mitigação:** utilizar fonte estruturada quando aprovada e não depender de contagem de cards.

### R004 — Seller/fulfillment ambíguos

A Amazon pode apresentar diferentes layouts.

**Mitigação:** normalização semântica e regra fail closed.

### R005 — Campos ausentes

Nem todas as ofertas possuem todos os campos.

**Mitigação:** distinguir campos obrigatórios, opcionais e derivados.

### R006 — Fonte interna não autorizada

A fonte técnica observada pode não ser apropriada para uso automatizado em produção.

**Mitigação:** aprovação/validação antes da implementação definitiva e preferência por interface oficial.

### R007 — Repetição de ofertas

Uma mesma oferta/produto pode aparecer em múltiplos lotes ou execuções.

**Mitigação:** deduplicação por ASIN e histórico por snapshot.

---

## 19. Decisões arquiteturais

### D001 — Coleta isolada

Acesso à Amazon será encapsulado em adaptadores.

### D002 — Parser separado

Parser interpreta e normaliza dados; não decide elegibilidade.

### D003 — Domínio independente

O domínio não conhece HTML, detalhes específicos de API, SQL ou mensageria.

### D004 — ASIN como identificador

O ASIN será o identificador principal do produto.

### D005 — `% vendidos`

Usar `dealDetails.percentClaimed` como fonte preferencial quando disponível.

### D006 — Seller

A oferta deve comprovar vendedor Amazon.

### D007 — Delivery

A oferta deve comprovar entrega Amazon.

### D008 — Fail closed

Ausência de evidência suficiente resulta em rejeição.

### D009 — Histórico

Coletas relevantes serão tratadas como snapshots.

### D010 — SQL

PostgreSQL/SQL será a persistência principal; Excel não será estado do sistema.

### D011 — Fonte técnica interna

O endpoint `/d2b/api/v1/products/search` está documentado como fonte tecnicamente observada, mas sua utilização definitiva em produção permanece condicionada à validação de uso autorizado.

### D012 — API oficial

Quando a API oficial fornecer o dado necessário, ela deverá ser priorizada.

---

## 20. Critérios de conclusão da FASE 0

| Critério | Status |
|---|---|
| Página de promoções identificada | CONCLUÍDO |
| Fonte técnica observada | CONCLUÍDO |
| Filtro Amazon identificado | CONCLUÍDO |
| ASIN documentado | CONCLUÍDO |
| `% vendidos` documentado | CONCLUÍDO |
| Seller documentado | CONCLUÍDO |
| Delivery documentado | CONCLUÍDO |
| Campos disponíveis documentados | CONCLUÍDO |
| Campos obrigatórios definidos | CONCLUÍDO |
| Campos opcionais definidos | CONCLUÍDO |
| Campos derivados definidos | CONCLUÍDO |
| Paginação documentada | CONCLUÍDO |
| Fallbacks definidos | CONCLUÍDO |
| Limitações documentadas | CONCLUÍDO |
| Riscos documentados | CONCLUÍDO |
| Dependências documentadas | CONCLUÍDO |
| Regras de integração documentadas | CONCLUÍDO |
| Política de retenção identificada | CONCLUÍDO — prazo exato pendente |
| Mapa Fonte → Campo | CONCLUÍDO |
| Origem dos dados críticos | CONCLUÍDO |
| Decisões arquiteturais iniciais | CONCLUÍDO |

---

## 21. Pendências para fases futuras

As seguintes questões não bloqueiam o encerramento técnico da FASE 0, mas deverão ser tratadas nas fases correspondentes:

1. Implementação Java do collector.
2. Implementação do parser.
3. Definição final dos contratos internos.
4. Definição do schema PostgreSQL.
5. Configuração externa e gerenciamento de segredos.
6. Implementação da coleta real.
7. Enriquecimento de produto.
8. Validação automatizada de seller/delivery.
9. Filtros configuráveis.
10. Score.
11. Histórico e momentum.
12. Orquestração.
13. Publicação.
14. Política operacional definitiva de retenção.
15. Validação final da fonte de coleta quanto às regras aplicáveis antes da produção.

---

## 22. Resultado final

A investigação técnica da FASE 0 foi concluída.

As fontes observadas, campos, identificadores, `% vendidos`, vendedor, entrega, paginação, fallback, limitações, riscos e decisões arquiteturais estão documentados.

O ponto de atenção principal é a distinção entre:

- **fonte tecnicamente observada**, e
- **fonte autorizada/aprovada para uso de produção**.

O endpoint interno da Amazon foi comprovado como fonte técnica durante a investigação, mas não deve ser tratado como autorizado automaticamente.

Com isso, a FASE 0 está pronta para encerramento formal e a próxima fase, quando aprovada, será a:

**FASE 1 — Fundação do projeto Java.**
