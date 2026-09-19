# ADR-0001 — Semântica dos filtros comerciais e apresentação de meios de pagamento

- **Status:** Aceita
- **Data:** 2026-09-19
- **Projeto:** Rasping Amazon
- **Fases relacionadas:** FASE 8, FASE 8.5, FASE 9 e FASE 10

## Contexto

O Rasping Amazon coleta e persiste informações comerciais provenientes das ofertas da Amazon, incluindo preço atual, condições de pagamento, percentual vendido (`soldPercentage`), avaliação (`rating`), quantidade de avaliações (`reviewCount`) e evidências relacionadas à venda e entrega pela Amazon.

Durante a preparação da FASE 9 — Motor de filtros configuráveis — foi necessário definir com precisão quais dados devem atuar como critérios eliminatórios, quais devem ser utilizados futuramente no score e como as condições de pagamento devem ser interpretadas para filtragem e publicação.

O documento-base do projeto previa inicialmente `minSoldPercentage = 50` como referência de filtro. Entretanto, a observação do comportamento do mural de ofertas indica que `soldPercentage` é um dado dinâmico de consumo/popularidade da oferta e que um limite elevado poderia eliminar ofertas comercialmente relevantes apenas porque ainda possuem baixa porcentagem consumida.

Também foi necessário distinguir:

- elegibilidade estrutural da oferta;
- filtros comerciais;
- score;
- seleção da condição de pagamento usada no filtro;
- seleção das condições de pagamento apresentadas na publicação.

A modelagem existente já estabelece que desconto é contextual a uma `PaymentCondition` e não um atributo universal do `OfferSnapshot`.

## Decisão

### 1. Elegibilidade estrutural permanece separada dos filtros comerciais

A validação de vendedor e entrega pela Amazon continua pertencendo à política de elegibilidade estrutural implementada anteriormente.

A FASE 9 não duplicará essa regra dentro de `FilterProfile`.

Em outras palavras:

- regras como “vendido pela Amazon” e “entregue pela Amazon” pertencem à elegibilidade estrutural;
- regras como desconto mínimo, avaliação mínima e quantidade mínima de avaliações pertencem ao motor de filtros comerciais.

### 2. `soldPercentage` não será um filtro eliminatório

`soldPercentage` continuará sendo:

- coletado;
- normalizado;
- persistido;
- auditável.

Entretanto, não fará parte do `FilterProfile` da FASE 9 e não poderá rejeitar uma oferta por estar abaixo de um limite mínimo.

Esse dado será reservado para a FASE 10 como um dos sinais possíveis do score.

A ausência de `soldPercentage` continuará sendo representada como ausência de dado, e não como zero.

### 3. O filtro de desconto utilizará condições de pagamento à vista

O desconto mínimo da FASE 9 será interpretado como **desconto mínimo à vista**.

O nome preferencial da configuração será:

`minCashDiscountPercentage`

O filtro deverá considerar apenas descontos explicitamente associados a condições de pagamento à vista reconhecidas pelo sistema, incluindo:

- Pix;
- NuPay.

O sistema não deverá:

- inferir desconto inexistente;
- calcular desconto implícito apenas pela diferença entre preços;
- somar descontos de condições diferentes;
- transformar ausência de desconto em zero.

### 4. O filtro utilizará a melhor condição à vista explicitamente disponível

Quando houver mais de uma condição de pagamento à vista reconhecida, o valor observado pelo filtro será o maior desconto explicitamente informado entre elas.

Exemplo:

- Pix: 8%;
- NuPay: 12%.

Resultado para o filtro:

- desconto observado: 12%;
- condição responsável pelo maior desconto: NuPay.

As `PaymentCondition` originais permanecem distintas e devem continuar preservadas no domínio e na persistência.

O sistema não deverá criar um `discountPercentage` universal no `OfferSnapshot`.

### 5. A condição usada pelo filtro não determina sozinha o conteúdo da publicação

A escolha da melhor condição à vista para avaliação do filtro é diferente da política de apresentação das condições de pagamento.

Filtro e publicação possuem responsabilidades distintas.

### 6. Política de apresentação de Pix e NuPay

Para fins de publicação, o sistema deverá seguir as regras abaixo.

#### 6.1 NuPay possui desconto estritamente maior que Pix

Quando NuPay oferecer desconto maior que Pix:

- NuPay deverá ser apresentado como a condição à vista mais vantajosa;
- Pix também deverá ser apresentado como alternativa mais abrangente.

Exemplo:

- NuPay: 12%;
- Pix: 8%.

Publicação:

- destacar NuPay como melhor preço à vista;
- apresentar Pix como alternativa à vista.

#### 6.2 Pix possui desconto maior que NuPay

Quando Pix oferecer desconto maior que NuPay:

- Pix deverá ser apresentado;
- NuPay não precisa ser destacado.

#### 6.3 Pix e NuPay possuem o mesmo desconto

Quando Pix e NuPay oferecerem o mesmo desconto:

- Pix deverá ser apresentado;
- NuPay não precisa ser destacado.

A justificativa é que, na ausência de vantagem econômica adicional, deve-se priorizar a condição de pagamento mais abrangente.

#### 6.4 Apenas uma condição está disponível

Quando apenas Pix ou apenas NuPay estiver disponível:

- a condição disponível poderá ser apresentada normalmente.

A ausência da outra condição não deverá ser interpretada como desconto zero.

### 7. Parcelamento por cartão não participa do filtro de desconto à vista

As condições de cartão continuarão sendo:

- coletadas;
- normalizadas;
- persistidas;
- disponibilizadas à camada de publicação.

Entretanto, elas não participarão de `minCashDiscountPercentage`.

O fato de cartão não participar do filtro de desconto não autoriza a remoção desses dados do pipeline.

### 8. A publicação deverá apresentar a melhor condição de parcelamento disponível

A política de publicação deverá selecionar a melhor condição de parcelamento entre as condições de cartão disponíveis.

O critério principal será:

1. maior quantidade de parcelas sem juros;
2. preservação do valor de cada parcela e demais dados necessários para publicação.

Exemplo:

- 6x sem juros;
- 10x sem juros;
- 12x com juros.

A condição preferida para apresentação será, em princípio:

- 10x sem juros.

Critérios adicionais de desempate poderão ser definidos posteriormente por nova decisão arquitetural, caso dados reais demonstrem necessidade.

## Consequências

### Consequências positivas

- Evita eliminar ofertas relevantes apenas por baixa porcentagem vendida.
- Mantém `soldPercentage` disponível para score e análises futuras.
- Preserva a semântica contextual de desconto em `PaymentCondition`.
- Evita misturar elegibilidade estrutural com filtros comerciais.
- Permite usar a melhor vantagem econômica para filtragem sem esconder meios de pagamento mais acessíveis na publicação.
- Mantém dados de cartão no pipeline mesmo quando eles não influenciam o filtro.
- Torna as regras de publicação determinísticas e auditáveis.
- Reduz o risco de futuras interpretações ambíguas sobre o significado de “desconto”.

### Consequências técnicas

O `FilterProfile` da FASE 9 não deverá conter `minSoldPercentage`.

A configuração de desconto deverá preferencialmente utilizar o nome:

`minCashDiscountPercentage`

O motor de filtros deverá conseguir identificar:

- desconto à vista observado;
- condição de pagamento que forneceu esse desconto;
- limiar configurado;
- resultado da regra.

A futura camada de publicação deverá possuir uma política própria para selecionar quais `PaymentCondition` serão apresentadas, sem reutilizar diretamente o resultado do filtro como decisão de apresentação.

## Alternativas consideradas

### Manter `minSoldPercentage = 50`

Rejeitada.

Esse valor poderia eliminar grande parte das ofertas apenas por baixa utilização momentânea da promoção, reduzindo excessivamente o universo de produtos elegíveis.

### Reduzir `minSoldPercentage` para um valor menor

Rejeitada.

O problema não é apenas o valor do limite. A natureza do dado é mais adequada a score/popularidade do que a uma regra eliminatória.

### Usar apenas Pix no filtro

Rejeitada.

NuPay pode oferecer uma condição à vista explicitamente mais vantajosa e deve ser considerada economicamente pelo filtro.

### Publicar somente a condição à vista com maior desconto

Rejeitada.

Quando NuPay for mais vantajoso, omitir Pix esconderia uma alternativa de pagamento mais abrangente para o público.

### Usar cartão no cálculo do desconto mínimo

Rejeitada.

Parcelamento e desconto à vista representam benefícios comerciais diferentes e devem permanecer semanticamente separados.

## Relação com fases futuras

### FASE 9

Implementará os filtros comerciais configuráveis, incluindo:

- desconto mínimo à vista;
- avaliação mínima;
- quantidade mínima de avaliações.

`soldPercentage` não será critério eliminatório.

### FASE 10

Poderá utilizar `soldPercentage` como sinal de popularidade ou tração dentro do score, com peso e versão próprios.

A forma exata de cálculo do score deverá ser definida em decisão posterior.

### Publicação

A camada de publicação deverá aplicar política própria para:

- apresentação de Pix;
- apresentação de NuPay;
- seleção do melhor parcelamento por cartão.

## Regra de evolução desta ADR

Esta ADR registra a decisão vigente.

Caso dados reais ou novas necessidades comerciais exijam alteração futura, esta ADR não deverá ser apagada ou reescrita para ocultar a decisão original.

Uma nova ADR deverá ser criada e deverá indicar explicitamente se:

- substitui esta ADR integralmente;
- substitui apenas parte desta ADR;
- complementa esta ADR.
