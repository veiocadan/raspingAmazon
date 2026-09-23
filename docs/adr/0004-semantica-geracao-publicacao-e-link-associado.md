# ADR-0004 — Semântica da geração de publicação e link de associado

- **Status:** Aceita
- **Data:** 2026-09-23
- **Projeto:** Rasping Amazon
- **Fase relacionada:** FASE 13
- **Complementa:** ADR-0001

## Contexto

Ao final da FASE 12, o Rasping Amazon possui um pipeline durável capaz de coletar, interpretar, enriquecer, persistir e avaliar ofertas de forma desacoplada e idempotente.

O próximo passo é transformar uma oferta já avaliada e selecionada pelo pipeline em uma `Publication` reproduzível, persistida e pronta para revisão.

A publicação não deve introduzir novamente decisões pertencentes às etapas anteriores.

Portanto:

- seller e delivery continuam pertencendo à elegibilidade estrutural;
- filtros comerciais continuam pertencendo ao motor de filtros;
- score continua pertencendo ao mecanismo de priorização;
- momentum continua pertencendo à análise histórica;
- apresentação comercial pertence à política de publicação;
- entrega em Telegram, WhatsApp ou outros canais permanece fora desta fase.

A ADR-0001 já estabelece que a condição utilizada para filtragem não determina automaticamente a condição apresentada na publicação.

Esta ADR complementa essa decisão e formaliza a geração de conteúdo da FASE 13.

## Decisão

### 1. A geração de publicação será um caso de uso independente

A aplicação deverá possuir um caso de uso próprio para geração de publicação.

Fluxo conceitual:

```text
DealEvaluation selecionada
        ↓
dados persistidos da oferta
        ↓
política de apresentação comercial
        ↓
geração do link de associado
        ↓
template versionado
        ↓
Publication
        ↓
persistência
```

O caso de uso não poderá depender de Telegram, WhatsApp ou outro canal.

### 2. Somente dados persistidos e auditáveis podem alimentar a publicação

O gerador deverá utilizar dados pertencentes ao estado persistido do pipeline.

Entre eles, quando aplicáveis:

```text
Product
OfferSnapshot
DealEvaluation
PaymentCondition
score
momentum
```

O gerador não deverá acessar novamente a Amazon para montar uma publicação.

Uma publicação deve continuar sendo compreensível mesmo quando a fonte externa não estiver mais disponível.

### 3. Dados ausentes não serão inventados

A publicação somente poderá apresentar informações existentes no estado da oferta.

Portanto, o sistema não deverá:

- inventar preço Pix;
- calcular desconto Pix por hipótese;
- fabricar preço anterior a partir de `basisPrice`;
- inventar parcelamento;
- transformar ausência de informação em zero;
- inferir condição comercial não observada.

`basisPrice` permanece semanticamente diferente de `previousPrice`.

### 4. Preço principal

O preço principal da publicação será o `currentPrice` do `OfferSnapshot`.

Ele representa o preço comercial principal observado pelo pipeline.

Quando `basisPrice` estiver disponível, ele poderá ser apresentado como preço-base ou preço de lista.

`basisPrice` não deverá ser rotulado como preço anterior ou histórico.

`previousPrice` somente poderá ser apresentado com semântica de preço anterior quando estiver explicitamente disponível como tal.

### 5. Política de apresentação à vista

A política de apresentação seguirá a ADR-0001.

Quando somente Pix estiver disponível:

- apresentar Pix.

Quando somente NuPay estiver disponível:

- apresentar NuPay.

Quando Pix e NuPay estiverem disponíveis e Pix possuir vantagem igual ou maior:

- apresentar Pix;
- NuPay não precisa ser destacado.

Quando NuPay possuir vantagem estritamente maior:

- destacar NuPay como a condição à vista mais vantajosa;
- também apresentar Pix como alternativa mais abrangente, quando disponível.

Ausência de uma condição não significa desconto zero.

### 6. Política de parcelamento

Parcelamento não participa do filtro de desconto à vista.

Para apresentação, serão consideradas as condições de cartão explicitamente observadas.

A versão inicial da política deverá priorizar:

1. parcelamentos sem juros;
2. entre eles, a maior quantidade de parcelas.

O valor da parcela, quantidade de parcelas e total parcelado deverão ser preservados conforme os dados observados.

Nenhum parcelamento será calculado a partir do preço principal.

Critérios adicionais de desempate deverão ser introduzidos por decisão versionada caso dados reais demonstrem necessidade.

### 7. Política de apresentação possuirá versão explícita

A seleção das condições comerciais deverá possuir versão própria.

Versão inicial:

```text
AMAZON_COMMERCIAL_PRESENTATION_V1
```

Essa versão representa as regras utilizadas para escolher quais informações comerciais aparecem na publicação.

Mudanças futuras de semântica devem criar nova versão.

Uma versão histórica não deverá mudar silenciosamente de comportamento.

### 8. Template possuirá versão explícita

A composição textual será separada da seleção dos dados.

Versão inicial:

```text
AMAZON_PUBLICATION_V1
```

O template recebe dados já selecionados e não deverá procurar informações diretamente em repositories, HTML ou APIs externas.

Alterações futuras de estrutura, texto ou disposição com impacto no conteúdo deverão criar nova versão.

### 9. A geração deve ser determinística

Mantidos os mesmos:

```text
dados de origem
+
política de apresentação
+
versão do template
+
versão do gerador de link
+
configuração de associado
```

o conteúdo gerado deverá ser equivalente.

Horário de geração não deverá ser incorporado ao texto apenas para tornar a mensagem diferente.

O instante de geração será armazenado como metadado da `Publication`.

### 10. Link de associado será responsabilidade de componente dedicado

O link de associado será produzido através de contrato próprio:

```text
ProductUrl
        ↓
AffiliateLinkGenerator
        ↓
AffiliateLink
```

O restante da aplicação não deverá concatenar manualmente parâmetros ou identificadores de associado.

A configuração necessária deverá permanecer fora do código-fonte.

Nenhum template deverá conhecer a regra técnica de montagem do link.

### 11. A geração do link possuirá versão

A estratégia de geração do link deverá possuir versão explícita.

Versão inicial prevista:

```text
AMAZON_AFFILIATE_LINK_V1
```

Mudanças futuras na semântica ou formato deverão resultar em nova versão quando afetarem reprodutibilidade ou auditoria.

### 12. A publicação será persistida antes da entrega externa

A FASE 13 termina com uma `Publication` persistida e pronta para revisão.

Fluxo:

```text
gerar
  ↓
persistir
  ↓
revisar futuramente
```

Não faz parte desta fase:

```text
aprovar automaticamente
enviar
Telegram
WhatsApp
outbox de canal
scheduler
```

### 13. Estado inicial

Uma publicação recém-gerada será criada no estado:

```text
CREATED
```

`CREATED` significa que o conteúdo existe e está persistido, mas ainda não foi liberado para entrega externa.

A transição:

```text
CREATED → READY
```

permanece reservada para o fluxo de revisão/aprovação.

A FASE 13 não utilizará `READY` para significar apenas “texto gerado”.

### 14. Idempotência de geração

Reprocessar a mesma solicitação lógica não deverá criar publicações duplicadas indefinidamente.

A identidade de geração deverá considerar, no mínimo:

```text
DealEvaluation
templateVersion
commercialPresentationVersion
affiliateLinkVersion
```

A persistência deverá possuir proteção correspondente.

A estratégia concreta de banco será introduzida por nova migration, sem alteração retroativa das migrations já aplicadas.

### 15. Auditabilidade

Para cada publicação deverá ser possível determinar:

```text
qual DealEvaluation a originou
qual OfferSnapshot foi utilizado
qual Product foi utilizado
qual score existia
qual momentum existia
qual política comercial foi aplicada
qual template foi utilizado
qual versão do template foi utilizada
qual estratégia de link foi utilizada
qual link final foi persistido
quando a publicação foi criada
qual era seu estado
```

Quando condições comerciais forem apresentadas, sua escolha deverá ser reconstruível de forma determinística a partir dos dados persistidos e da versão da política.

### 16. Separação de responsabilidades

A arquitetura esperada é:

```text
application
    ↓
publication use case
    ↓
domain policies / contracts
    ↓
ports
    ↓
infrastructure
```

Não serão permitidos fluxos como:

```text
PublicationTemplate
    ↓
SQL
```

ou:

```text
PublicationTemplate
    ↓
Amazon HTML
```

ou:

```text
interface
    ↓
montagem manual de mensagem
```

### 17. Relação com a interface operacional

A interface operacional pertence à FASE 14.

Ela deverá apenas invocar o caso de uso construído nesta fase.

A FASE 14 não deverá reimplementar:

- escolha de condições comerciais;
- template;
- geração de link;
- persistência da publicação.

## Consequências

A FASE 13 deverá introduzir componentes equivalentes a:

```text
PublicationGenerationUseCase
PublicationDataQueryPort
CommercialPresentationPolicy
CommercialPresentation
PublicationTemplate
AffiliateLinkGenerator
PublicationRepositoryPort
```

Os nomes concretos poderão ser refinados durante a implementação desde que as responsabilidades permaneçam separadas.

Também poderá ser necessária nova migration para registrar as versões adicionais e proteger a idempotência da geração.

A migration existente que criou `publication` não será modificada.

## Fora do escopo

Permanecem fora da FASE 13:

```text
Telegram
WhatsApp
envio externo
scheduler
execução contínua
outbox de canais
retry de canais
dashboard
interface operacional
```

## Critério de conclusão

A FASE 13 estará concluída quando uma `DealEvaluation` selecionada puder produzir, sem acesso externo à Amazon durante a geração:

```text
Publication
```

que seja:

- construída a partir de dados auditáveis;
- comercialmente correta;
- versionada;
- determinística;
- dotada de link de associado;
- persistida;
- protegida contra duplicação lógica;
- independente de qualquer canal de entrega.
