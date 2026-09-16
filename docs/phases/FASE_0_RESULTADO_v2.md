# FASE 0 --- RESULTADO CONSOLIDADO v2

**Projeto:** Rasping Amazon\
**Fase:** 0 --- Levantamento da fonte, regras e semântica comercial\
**Versão:** v2\
**Data:** 14/09/2026\
**Status:** CONCLUÍDA --- resultado consolidado e mapa comercial
definido

------------------------------------------------------------------------

## 1. Objetivo

A FASE 0 tem como objetivo identificar as fontes reais de dados,
documentar de onde cada campo será obtido, registrar as regras de
elegibilidade, limitações técnicas, dependências e riscos, e definir uma
estratégia segura de fallback.

A revisitação da FASE 0 foi necessária porque a investigação original
identificou `current_price`, `previous_price` e `discount_percentage`,
mas não fechou de forma suficiente a semântica comercial do preço nem as
condições de pagamento.

A FASE 0 v2 foi concluída após investigação complementar de uma página
real de produto da Amazon Brasil, incluindo:

-   preço principal;
-   preço de lista;
-   preço visível ao cliente;
-   promoção à vista;
-   Pix;
-   NuPay;
-   parcelamento;
-   juros;
-   distinção entre modalidades;
-   identificação de promoções independentes;
-   relação entre preço, desconto e parcelamento.

Este documento consolida a FASE 0 original, a revisitação de
preços/parcelamento/Pix e o resultado final da investigação.

------------------------------------------------------------------------

# 2. Princípios preservados

A FASE 0 permanece subordinada aos princípios arquiteturais do projeto:

1.  investigar a fonte antes de modelar;
2.  não inferir dados comerciais sem evidência;
3.  separar coleta, parsing, normalização e regra de negócio;
4.  manter o domínio independente de HTML, APIs externas e SQL;
5.  preferir interfaces oficiais quando fornecerem o dado necessário;
6.  utilizar fallback somente quando a semântica permanecer preservada;
7.  falhar fechado quando uma decisão de elegibilidade depender de
    informação ausente;
8.  manter histórico por snapshots;
9.  preservar rastreabilidade das fontes e decisões;
10. não antecipar implementação de fases posteriores.

A arquitetura geral determina que o parser descreva o que foi encontrado
e que o domínio decida o que fazer com os dados.
fileciteturn17file2L624-L641

------------------------------------------------------------------------

# 3. Fonte funcional de promoções

A fonte funcional observada é:

``` text
https://www.amazon.com.br/deals
```

A página é dinâmica.

Foram observados:

-   carregamento dinâmico de ofertas;
-   novos lotes durante a navegação;
-   ação de "Ver mais ofertas";
-   variação do conjunto de ofertas entre carregamentos;
-   virtualização/reutilização de elementos do DOM;
-   impossibilidade de utilizar simplesmente a quantidade de
    `product-card` no DOM como total de ofertas.

A coleta deve permanecer isolada em um adaptador de `amazon.collector`.

Essas características já haviam sido documentadas na FASE 0 original.
fileciteturn15file2L556-L581

------------------------------------------------------------------------

# 4. Fonte técnica observada

Foi identificada durante a investigação uma fonte JSON interna utilizada
pela aplicação web:

``` text
GET https://www.amazon.com.br/d2b/api/v1/products/search
```

Foram observados parâmetros como:

``` text
pageSize
startIndex
rankingContext
filters
promotionTypes
accessTypes
brandIds
unifiedIds
pinnedPromotionGroups
pinnedPromotionsLayoutGroup
```

A fonte foi tecnicamente comprovada, mas:

> **não deve ser considerada automaticamente autorizada para produção.**

A arquitetura determina preferência por interfaces oficiais quando elas
fornecerem o dado necessário. fileciteturn15file2L585-L612

A implementação definitiva deve validar a permissibilidade da fonte
antes de utilizá-la de forma automatizada em produção.

------------------------------------------------------------------------

# 5. Investigação de API oficial

Na investigação complementar também foi consultada a documentação
oficial da Amazon Associates / Creators API.

Foram encontrados recursos de ofertas relacionados a:

``` text
offersV2.listings.price
offersV2.listings.dealDetails
offersV2.listings.merchantInfo
offersV2.listings.type
```

A documentação oficial apresenta, entre outros dados:

``` text
price.money.amount
price.savingBasis.money.amount
savingBasisType
savings.money.amount
savings.percentage
dealDetails.percentClaimed
merchantInfo.name
```

A documentação identifica `offersV2.listings.price` como o preço de
compra da oferta.

### Limitação encontrada

Não foi encontrada na documentação consultada uma estrutura oficial
documentada equivalente a:

``` text
pix_price
pix_discount_percentage
installment_count
installment_amount
installment_total
```

para reproduzir diretamente todos os dados comerciais encontrados na
página brasileira.

### Decisão

A API oficial deve continuar sendo priorizada quando fornecer o dado
necessário, mas a semântica comercial observada na página não deve ser
fabricada a partir de campos que possuem significado diferente.

Em particular:

``` text
savingBasis
```

não deve ser convertido automaticamente em:

``` text
previous_price
```

sem evidência adicional de que represente preço histórico/anterior.

------------------------------------------------------------------------

# 6. Produto utilizado na investigação complementar

Foi investigada uma página real de produto da Amazon Brasil.

``` text
ASIN: B0GM1LSHQF
```

A investigação foi realizada no HTML/DOM inicial da página e no conteúdo
pré-carregado do componente de opções de pagamento.

O objetivo desta investigação foi validar a semântica dos dados, e não
implementar o parser.

------------------------------------------------------------------------

# 7. Mapa comercial definitivo observado

A oferta investigada apresentou:

``` text
Preço de lista:
R$ 299,00

Preço visível ao cliente:
R$ 161,40

Promoção:
15% off à vista
no Pix ou NuPay Limite Adicional

Cartão de Crédito:
6x de R$ 31,65
sem juros
Total: R$ 189,90
```

Também foram encontradas outras modalidades de pagamento e uma segunda
promoção de 15%, mas elas possuem semânticas próprias e não devem ser
confundidas com a promoção principal.

------------------------------------------------------------------------

# 8. Preço de lista / `basisPrice`

Foi encontrada a estrutura:

``` text
corePriceDisplay_desktop_feature_div
└── corePrice_feature_div
    └── apex-basisprice-feature
```

Com:

``` html
data-basisprice-label="{label} {price}"
```

e:

``` html
data-a-strike="true"
```

A página apresenta:

``` text
De: R$ 299,00
```

Também foi encontrada a representação:

``` text
Preço de lista
R$ 299,00
```

## Conclusão

O valor deve ser normalizado como:

``` text
basis_price = 299.00
```

ou conceitualmente:

``` text
list_price = 299.00
```

## Regra

Não converter automaticamente:

``` text
basis_price
```

em:

``` text
previous_price
```

`previous_price` deve continuar reservado para informação cuja fonte
permita afirmar que se trata de preço anterior/histórico.

------------------------------------------------------------------------

# 9. Preço visível ao cliente

Foi encontrada uma fonte estruturada no formulário de compra:

``` html
name="items[0].base][customerVisiblePrice][amount]"
value="161.40"
```

Também:

``` html
name="items[0].base][customerVisiblePrice][currencyCode]"
value="BRL"
```

e:

``` html
name="items[0].base][customerVisiblePrice][displayString]"
value="R$ 161,40"
```

A mesma informação aparece no bloco principal:

``` text
corePriceDisplay_desktop_feature_div
```

e em:

``` text
corePrice_feature_div
```

## Conclusão

Existe evidência estruturada suficiente para registrar:

``` text
customer_visible_price = 161.40
currency = BRL
display_string = "R$ 161,40"
```

Na página investigada, esse é o preço principal apresentado ao cliente.

A condição comercial associada é identificada separadamente pela
mensagem:

``` text
15% off à vista
no Pix ou NuPay Limite Adicional
```

------------------------------------------------------------------------

# 10. Semântica do `current_price`

A investigação complementar resolveu a principal dúvida da revisitação.

Na página investigada, o valor que anteriormente poderia ser tratado
genericamente como `current_price` corresponde ao:

``` text
customerVisiblePrice
```

ou seja, ao preço efetivamente apresentado ao cliente naquele estado da
oferta.

Para a oferta investigada:

``` text
current_price / customer_visible_price = R$ 161,40
```

### Porém

Esse campo não deve ser interpretado genericamente como:

``` text
preço Pix
```

sem preservar a condição comercial.

Também não deve ser interpretado como:

``` text
preço de lista
```

nem como:

``` text
previous_price
```

A condição deve ser modelada separadamente.

------------------------------------------------------------------------

# 11. Promoção à vista de 15%

Foi encontrada no buy box:

``` text
15% off à vista
no Pix ou NuPay Limite Adicional
```

O componente observado é:

``` text
promotionMessageInsideBuyBox_feature_div
```

Também existe:

``` text
oneTimePaymentPrice_feature_div
```

contendo a mesma informação comercial.

## Conclusão

A promoção deve ser registrada conceitualmente como:

``` text
discount_percentage = 15%
discount_condition = "à vista"
payment_methods = ["Pix", "NuPay Limite Adicional"]
```

A informação é condicional.

Não deve ser tratada como:

``` text
discount_percentage = 15%
```

sem contexto.

------------------------------------------------------------------------

# 12. O componente `oneTimePaymentPrice`

O componente:

``` text
oneTimePaymentPrice_feature_div
```

não contém o valor monetário de R\$ 161,40.

Seu conteúdo observado é:

``` text
15% off à vista
no Pix ou NuPay Limite Adicional
```

Portanto:

``` text
oneTimePaymentPrice
```

é evidência da **condição comercial/promocional**, e não a fonte
numérica principal do preço.

A fonte numérica estruturada é:

``` text
customerVisiblePrice.amount = 161.40
```

------------------------------------------------------------------------

# 13. Pix

Foi aberta a aba Pix do componente de opções de pagamento.

A aba apresenta texto explicativo sobre pagamento à vista, mas:

> **não foi encontrado um valor numérico específico de Pix dentro da
> aba.**

Não foi encontrada uma estrutura equivalente a:

``` text
pix_price = 161.40
```

## Decisão

Não criar automaticamente:

``` text
pix_price
```

a partir da simples existência da aba Pix.

Também não assumir:

``` text
customer_visible_price = pix_price
```

como regra universal.

### Resultado

O sistema pode registrar com segurança:

``` text
customer_visible_price = 161.40
```

e:

``` text
cash_payment_discount = 15%
cash_payment_condition = Pix ou NuPay Limite Adicional
```

Mas:

``` text
pix_price
```

permanece **não confirmado como campo numérico específico da fonte**.

------------------------------------------------------------------------

# 14. Parcelamento

O componente de parcelamento apresenta:

``` text
ou em até
6x de R$ 31,65
```

e:

``` text
sem juros
```

Foi localizada a tabela:

``` text
InstallmentCalculatorTableCredit
```

com a linha:

``` text
Em 6x de R$ 31,65
sem juros
R$ 189,90
```

## Dados normalizados

``` text
payment_method = CREDIT
installment_count = 6
installment_amount = 31.65
installment_total = 189.90
interest = 0
```

Esses dados são explícitos na fonte.

------------------------------------------------------------------------

# 15. Regra de parcelamento para publicação

Foi definida a regra comercial:

> **Sempre considerar o maior número de parcelas possível sem juros na
> modalidade Cartão de Crédito.**

Portanto, a seleção não deve procurar simplesmente a maior quantidade de
parcelas encontrada na página.

O algoritmo conceitual é:

``` text
1. selecionar modalidade = Cartão de Crédito;
2. filtrar somente parcelas sem juros;
3. selecionar a maior quantidade de parcelas;
4. utilizar essa condição na publicação.
```

Para a oferta investigada:

``` text
6x de R$ 31,65 sem juros
Total: R$ 189,90
```

### Importante

Essa é uma **regra de negócio**.

Não é uma regra do parser.

O parser deve coletar/normalizar as condições disponíveis. A seleção da
condição a publicar deve ocorrer posteriormente, no domínio/aplicação.

------------------------------------------------------------------------

# 16. Outras modalidades de parcelamento

O popover também apresenta:

``` text
NuPay Crédito
Parcele sem cartão
Pix
```

O NuPay Crédito possui tabela própria.

A modalidade sem cartão também possui condições próprias.

## Decisão

Essas modalidades podem ser capturadas como evidência da fonte quando
necessário, mas não alteram a regra de publicação definida para o
projeto.

A condição selecionada para publicação é:

``` text
Cartão de Crédito
+
maior número de parcelas
+
sem juros
```

Não é permitido selecionar a maior quantidade de parcelas globalmente
entre todas as modalidades.

------------------------------------------------------------------------

# 17. Segunda promoção de 15% --- DESCARTADA

Foi encontrada uma segunda ocorrência de `15%` em:

``` text
promoPriceBlockMessage_feature_div
```

A promoção apresenta conteúdo equivalente a:

``` text
Exclusivo Prime: 15%
off na 1ª compra com cartão Amazon.
Insira o código CARTAO... no final da compra.
Desconto da Amazon.
```

## Condições

Essa promoção depende de:

-   Prime;
-   primeira compra;
-   cartão Amazon;
-   utilização de cupom.

## Decisão

Essa promoção:

``` text
NÃO
```

faz parte do desconto principal da oferta.

Ela deve ser descartada da captura comercial principal.

### Regra definitiva

Não utilizar:

``` text
primeira ocorrência de "15%"
```

como `discount_percentage`.

O percentual somente pode ser associado à promoção correta depois de
identificar:

-   o componente;
-   a condição;
-   a modalidade;
-   o escopo da promoção.

------------------------------------------------------------------------

# 18. Relação entre os preços

Os principais valores observados foram:

``` text
basis_price = 299.00
customer_visible_price = 161.40
credit_installment_total = 189.90
```

O parcelamento confirma:

``` text
6 × 31.65 = 189.90
```

Também foi observada a relação aproximada:

``` text
189.90 × 0.85 ≈ 161.40
```

Isso é consistente com os 15% da promoção à vista.

## Limitação

Essa relação matemática é uma validação/inferência da investigação.

Ela não deve ser utilizada como fonte primária para reconstruir preços.

O sistema deve utilizar os valores explicitamente encontrados:

``` text
161.40
189.90
15%
```

e não recalcular um deles para substituir a evidência original.

------------------------------------------------------------------------

# 19. Prova de que R\$ 299,00 não é a base do desconto de 15%

Se os 15% fossem aplicados diretamente sobre R\$ 299,00:

``` text
299.00 × 0.85 = 254.15
```

e não:

``` text
161.40
```

A diferença entre R\$ 299,00 e R\$ 161,40 também não corresponde a 15%.

``` text
(299.00 - 161.40) / 299.00
≈ 45.85%
```

## Conclusão

Não existe base para registrar:

``` text
299.00
→ 161.40
→ 15%
```

como uma simples relação:

``` text
previous_price
current_price
discount_percentage
```

------------------------------------------------------------------------

# 20. Mapa Fonte → Campo → Semântica

  ------------------------------------------------------------------------------------------------------------------------------------------------------
  Fonte                                  Campo encontrado           Campo comercial               Tipo                 Obrigatório   Uso
  -------------------------------------- -------------------------- ----------------------------- -------------------- ------------- -------------------
  `customerVisiblePrice.amount`          `161.40`                   `customer_visible_price`      direto/normalizado   SIM           preço principal da
                                                                                                                                     oferta

  `customerVisiblePrice.currencyCode`    `BRL`                      `currency`                    direto               SIM           interpretação
                                                                                                                                     monetária

  `customerVisiblePrice.displayString`   `R$ 161,40`                representação do preço        direto               NÃO           evidência/display

  `apex-basisprice-feature`              `299.00`                   `basis_price` / `list_price`  direto/normalizado   NÃO           comparação
                                                                                                                                     comercial

  `data-a-strike="true"`                 preço riscado              evidência de preço-base       contextual           NÃO           suporte à semântica

  `promotionMessageInsideBuyBox`         `15%`                      `discount_percentage`         direto/contextual    NÃO           promoção à vista
                                                                    condicionado                                                     

  `promotionMessageInsideBuyBox`         `à vista`                  `discount_condition`          direto               NÃO           condição

  `promotionMessageInsideBuyBox`         `Pix`                      `payment_condition`           direto               NÃO           condição

  `promotionMessageInsideBuyBox`         `NuPay Limite Adicional`   `payment_condition`           direto               NÃO           condição

  `InstallmentCalculatorTableCredit`     `6`                        `credit_installment_count`    direto               NÃO           publicação

  `InstallmentCalculatorTableCredit`     `31.65`                    `credit_installment_amount`   direto               NÃO           publicação

  `InstallmentCalculatorTableCredit`     `189.90`                   `credit_installment_total`    direto               NÃO           publicação

  `InstallmentCalculatorTableCredit`     `sem juros`                `interest = 0`                direto               NÃO           regra de seleção

  Aba Pix                                sem valor                  ---                           ausência de          ---           não criar
                                                                                                  evidência                          `pix_price`

  `promoPriceBlockMessage`               `15%`                      promoção Prime/cartão Amazon  direto, mas fora do  NÃO           descartar
                                                                                                  escopo                             
  ------------------------------------------------------------------------------------------------------------------------------------------------------

------------------------------------------------------------------------

# 21. Classificação dos dados

## 21.1 Dados diretos

São dados explicitamente presentes na fonte:

``` text
ASIN
title
image
product_url
customer_visible_price
currency
basis_price
discount_percentage
discount_condition
payment_condition
installment_count
installment_amount
installment_total
interest
seller
delivery
sold_percentage
```

quando efetivamente disponíveis no layout/fonte correspondente.

## 21.2 Dados normalizados

Exemplos:

``` text
seller_type
delivery_type
payment_method
price fields
URLs
ASIN
```

A normalização não deve alterar a semântica original.

## 21.3 Dados derivados

Exemplos:

``` text
eligible
rejection_reason
score
momentum
```

Também podem existir indicadores derivados para validação, mas não devem
substituir dados comerciais diretamente observados.

## 21.4 Dados condicionais

Exemplos:

``` text
discount_percentage
discount_condition
credit_installment_count
credit_installment_amount
credit_installment_total
interest
```

A ausência desses dados não significa zero.

------------------------------------------------------------------------

# 22. Modelo comercial recomendado

A investigação indica que o conceito de preço não deve permanecer
limitado a:

``` text
current_price
previous_price
discount_percentage
```

O modelo conceitual deve permitir separar:

``` text
Offer
│
├── listPrice / basisPrice
│
├── customerVisiblePrice
│
├── cashPaymentCondition
│   ├── price
│   ├── discountPercentage
│   └── paymentMethods
│
└── creditInstallmentCondition
    ├── paymentMethod
    ├── installmentCount
    ├── installmentAmount
    ├── installmentTotal
    └── interest
```

### `previousPrice`

Deve permanecer semanticamente separado de:

``` text
basisPrice
```

até que exista evidência específica de preço histórico/anterior.

------------------------------------------------------------------------

# 23. Relação com `OfferSnapshot`

A FASE 3 atualmente possui:

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

Esse modelo foi criado antes da conclusão da investigação complementar.
fileciteturn16file2L903-L930

A investigação demonstra que o conceito de preço precisa ser
enriquecido.

## Decisão

Não modificar `OfferSnapshot` dentro da FASE 0.

A evolução deve ser feita na fase responsável pela alteração do domínio,
acompanhada de:

-   revisão do modelo;
-   revisão dos invariantes;
-   migration PostgreSQL;
-   persistência;
-   parser/normalização;
-   testes;
-   impacto na publicação.

Isso preserva a regra de desenvolvimento de trabalhar uma fase por vez.
fileciteturn17file2L624-L641

------------------------------------------------------------------------

# 24. Relação com PostgreSQL

A FASE 2 atualmente possui `offer_snapshot` com:

``` text
id
product_id
collected_at
current_price
previous_price
discount_percentage
sold_percentage
rating
review_count
seller_name
delivery_provider
source
```

O schema atual deve ser preservado até que a evolução seja formalmente
especificada.

A FASE 2 já estabelece migrations versionadas por Flyway e preservação
de snapshots históricos. fileciteturn16file1L507-L530

## Regra

Qualquer evolução futura deverá:

``` text
não editar V1 retroativamente
```

e utilizar:

``` text
nova migration Flyway
```

quando houver necessidade de alteração estrutural.

------------------------------------------------------------------------

# 25. Política de persistência comercial

A persistência deve preservar informação suficiente para:

-   reconstruir a decisão comercial;
-   explicar o preço utilizado;
-   distinguir preço de lista de preço atual;
-   identificar condições de pagamento;
-   identificar parcelamento selecionado;
-   auditar a seleção;
-   permitir histórico;
-   evitar dependência de interpretação posterior do HTML.

## Princípio

Dados comerciais estruturados devem ser persistidos de forma que a
decisão posterior de publicação não precise voltar à página original
para descobrir o significado do preço.

Artefatos brutos de diagnóstico podem ser armazenados somente quando
necessários e conforme política de retenção definida. A FASE 0 original
já registrou essa necessidade. fileciteturn16file0L265-L299

------------------------------------------------------------------------

# 26. Critério de elegibilidade de vendedor e entrega

A investigação anterior permanece válida.

Somente a combinação:

``` text
seller_type == AMAZON
AND
delivery_type == AMAZON
```

é elegível.

Combinações com:

``` text
THIRD_PARTY
UNKNOWN
```

devem ser rejeitadas.

A ausência de evidência suficiente resulta em falha fechada.
fileciteturn16file0L61-L79

Essa regra não foi alterada pela investigação comercial.

------------------------------------------------------------------------

# 27. `% vendidos`

A regra original permanece:

``` text
dealDetails.percentClaimed
```

como fonte preferencial.

Fallback:

``` text
dealDetails.percentClaimed
→ estrutura equivalente
→ texto visual
→ null
```

O sistema não deve estimar `% vendidos`.
fileciteturn15file2L663-L704

------------------------------------------------------------------------

# 28. ASIN

O ASIN permanece como identificador externo principal do produto.

Fontes possíveis:

``` text
API estruturada
→ data-asin
→ URL /dp/{ASIN}
→ rejeitar se inconclusivo
```

O sistema não deve adivinhar ASIN por título, imagem ou outras
informações incompletas. fileciteturn15file2L637-L659

------------------------------------------------------------------------

# 29. Fallbacks comerciais

## Preço principal

``` text
fonte estruturada de preço
→ informação estruturada equivalente
→ indisponível
```

## Preço de lista

``` text
basisPrice estruturado
→ informação equivalente de preço-base
→ indisponível
```

## Desconto

``` text
promoção estruturada/contextualizada
→ informação equivalente com condição identificável
→ indisponível
```

Não:

``` text
qualquer "15%"
→ desconto
```

## Parcelamento

``` text
tabela estruturada de parcelamento
→ estrutura equivalente
→ indisponível
```

Não calcular parcelamento a partir de preço total quando a fonte não
fornecer evidência suficiente.

## Pix

``` text
preço Pix explicitamente identificado
→ equivalente estruturado
→ não informado
```

Não:

``` text
customer_visible_price
→ pix_price
```

por hipótese.

------------------------------------------------------------------------

# 30. Regras de rejeição por insuficiência de evidência

A regra geral é:

> **Quando a informação for necessária para uma decisão e não houver
> evidência suficiente, rejeitar ou marcar como indisponível conforme a
> natureza do dado.**

Exemplos:

``` text
seller desconhecido
→ rejeitar oferta

delivery desconhecido
→ rejeitar oferta

ASIN inconclusivo
→ descartar oferta

preço principal ausente
→ oferta não processável

desconto ausente
→ não inventar desconto

Pix sem valor explícito
→ não inventar preço Pix

parcelamento sem evidência
→ não inventar parcelamento
```

Essa política é consistente com a regra fail-closed já estabelecida no
projeto. fileciteturn16file0L83-L126

------------------------------------------------------------------------

# 31. Regra de publicação

A publicação poderá utilizar somente dados cuja semântica tenha sido
estabelecida.

Para a oferta investigada, o conteúdo comercial seguro é:

``` text
De: R$ 299,00
Preço à vista: R$ 161,40
15% off à vista no Pix ou NuPay Limite Adicional
6x de R$ 31,65 sem juros
Total no cartão: R$ 189,90
```

### Observação

A forma final da mensagem pertence à fase de geração de publicação.

A FASE 0 apenas define quais dados podem alimentar essa publicação.

------------------------------------------------------------------------

# 32. Regra de preço principal para publicação

O preço principal deve ser o preço que a fonte apresenta como preço
efetivamente aplicável ao estado comercial da oferta.

Na investigação realizada:

``` text
customerVisiblePrice = R$ 161,40
```

é o preço principal apresentado.

A condição de pagamento deve ser preservada:

``` text
à vista
Pix ou NuPay Limite Adicional
```

Não apresentar o valor como se fosse:

``` text
preço universal independente da forma de pagamento
```

se a fonte associá-lo explicitamente a uma condição.

------------------------------------------------------------------------

# 33. O que não pode ser publicado por inferência

Não publicar como fato:

``` text
Pix: R$ 161,40
```

se não houver evidência suficiente de que o valor é especificamente um
preço Pix.

Também não publicar:

``` text
Desconto: 46%
```

calculado entre R\$ 299,00 e R\$ 161,40.

Também não publicar:

``` text
Preço anterior: R$ 299,00
```

somente porque a página apresenta `basisPrice`.

Também não publicar a promoção:

``` text
Prime / primeira compra / cartão Amazon / cupom
```

como se fosse o desconto geral da oferta.

------------------------------------------------------------------------

# 34. Auditoria e rastreabilidade

Cada dado comercial relevante deverá manter, conceitualmente, a relação:

``` text
fonte
→ localização/identificador
→ valor bruto
→ valor normalizado
→ semântica
→ condição
→ regra aplicada
→ resultado
```

Exemplo:

``` text
Fonte:
customerVisiblePrice.amount

Valor:
161.40

Normalizado:
Money(161.40, BRL)

Semântica:
customer_visible_price

Condição contextual:
à vista / Pix ou NuPay Limite Adicional

Uso:
preço principal da oferta
```

Outro exemplo:

``` text
Fonte:
InstallmentCalculatorTableCredit

Valor:
6x de 31.65

Normalizado:
count=6
amount=31.65
total=189.90
interest=0

Regra:
maior quantidade de parcelas sem juros no Cartão de Crédito

Uso:
publicação
```

Isso é compatível com o requisito arquitetural de auditabilidade das
decisões e publicações. fileciteturn17file2L624-L641

------------------------------------------------------------------------

# 35. Limitações conhecidas

Permanecem as limitações da FASE 0 original:

1.  página de Deals dinâmica;
2.  conjunto de ofertas variável;
3.  DOM virtualizado;
4.  mudanças de HTML;
5.  mudanças de estrutura interna;
6.  seller/fulfillment em layouts diferentes;
7.  campos ausentes;
8.  fonte interna potencialmente não autorizada;
9.  disponibilidade temporária das ofertas;
10. paginação variável;
11. necessidade de validação da fonte antes da produção.

Essas limitações já estavam registradas e permanecem válidas.
fileciteturn16file0L229-L244

A investigação complementar acrescenta:

12. preço e condição de pagamento podem aparecer em componentes
    diferentes;
13. a mesma informação pode aparecer repetida em representações
    distintas do DOM;
14. uma ocorrência de percentual pode pertencer a outra promoção;
15. o popover de pagamento pode estar pré-carregado no HTML sem gerar
    nova requisição ao ser aberto;
16. a aba Pix pode explicar a modalidade sem fornecer valor numérico;
17. diferentes modalidades de parcelamento possuem tabelas
    independentes.

------------------------------------------------------------------------

# 36. Riscos comerciais específicos

## R008 --- Confusão entre preço de lista e preço anterior

**Risco:** transformar `basisPrice` em `previous_price`.

**Mitigação:** manter os conceitos separados.

------------------------------------------------------------------------

## R009 --- Confusão entre preço à vista e Pix

**Risco:** assumir que todo preço à vista é necessariamente um preço Pix
específico.

**Mitigação:** preservar a condição e exigir evidência específica para
`pix_price`.

------------------------------------------------------------------------

## R010 --- Percentual de promoção incorreto

**Risco:** capturar qualquer ocorrência de `15%` da página.

**Mitigação:** associar percentual ao componente e às condições da
promoção.

------------------------------------------------------------------------

## R011 --- Promoções concorrentes

**Risco:** utilizar promoção Prime/cupom/cartão Amazon como desconto
geral.

**Mitigação:** classificar o escopo da promoção antes da normalização
comercial.

------------------------------------------------------------------------

## R012 --- Escolha incorreta do parcelamento

**Risco:** selecionar a maior quantidade de parcelas globalmente,
incluindo modalidades diferentes.

**Mitigação:** selecionar primeiro `Cartão de Crédito`, depois filtrar
`sem juros`, depois escolher a maior quantidade.

------------------------------------------------------------------------

## R013 --- Reconstituição matemática do preço

**Risco:** recalcular um preço a partir de desconto ou parcelamento e
substituir o valor original.

**Mitigação:** usar a fonte explícita como autoridade; cálculos somente
para validação.

------------------------------------------------------------------------

# 37. Decisões arquiteturais da FASE 0 v2

### D013 --- `customerVisiblePrice` como evidência principal do preço atual

O preço principal observado na página deve ser capturado a partir da
estrutura `customerVisiblePrice` quando disponível.

------------------------------------------------------------------------

### D014 --- `basisPrice` separado de `previousPrice`

`basisPrice` representa preço de lista/base da página investigada.

Não deve ser automaticamente tratado como preço histórico anterior.

------------------------------------------------------------------------

### D015 --- Condição de pagamento separada do valor

Preço e condição de pagamento são conceitos distintos.

------------------------------------------------------------------------

### D016 --- Pix não deve ser inferido

A existência de uma condição "à vista no Pix" não autoriza criar um
`pix_price` numérico sem evidência.

------------------------------------------------------------------------

### D017 --- Promoção deve possuir contexto

Um percentual só pode ser utilizado depois de identificar a promoção e
suas condições.

------------------------------------------------------------------------

### D018 --- Promoção Prime/cupom separada

Promoções específicas de Prime, primeira compra, cartão Amazon e cupom
não participam da promoção comercial geral da oferta investigada.

------------------------------------------------------------------------

### D019 --- Parcelamento selecionado por regra de negócio

O parser pode capturar as condições disponíveis.

O domínio/aplicação seleciona:

``` text
Cartão de Crédito
+
sem juros
+
maior número de parcelas
```

------------------------------------------------------------------------

### D020 --- Cálculos não substituem evidências

Valores explícitos da fonte têm prioridade sobre valores matematicamente
reconstruídos.

------------------------------------------------------------------------

### D021 --- FASE 2 e FASE 3 não são alteradas nesta fase

A investigação define requisitos e semântica.

A alteração do código deve ocorrer somente na fase correspondente.

------------------------------------------------------------------------

# 38. Mapa final: fonte → campo → evidência → interpretação → destino

  -------------------------------------------------------------------------------------------------------------------------------------------------------------------
  Fonte                                 Campo         Evidência            Interpretação   Classificação       Domínio           Persistência            Publicação
  ------------------------------------- ------------- -------------------- --------------- ------------------- ----------------- ----------------------- ------------
  `customerVisiblePrice.amount`         `161.40`      valor estruturado    preço atual     direto              preço comercial   snapshot                SIM
                                                                           visível                             da oferta                                 

  `customerVisiblePrice.currencyCode`   `BRL`         valor estruturado    moeda           direto              Money/monetário   snapshot/normalização   indireto

  `basisPrice`                          `299.00`      preço-base/riscado   preço de lista  direto              list/basis price  snapshot                SIM, quando
                                                                                                                                                         desejado

  `promotionMessageInsideBuyBox`        `15%`         texto contextual     desconto à      direto/contextual   promoção          snapshot comercial      SIM
                                                                           vista                                                                         

  `promotionMessageInsideBuyBox`        Pix/NuPay     texto contextual     condição        direto              payment condition snapshot comercial      SIM

  `InstallmentCalculatorTableCredit`    `6`           tabela               parcelas sem    direto              credit            snapshot comercial      SIM
                                                                           juros                               installment                               

  `InstallmentCalculatorTableCredit`    `31.65`       tabela               valor da        direto              credit            snapshot comercial      SIM
                                                                           parcela                             installment                               

  `InstallmentCalculatorTableCredit`    `189.90`      tabela               total           direto              credit            snapshot comercial      SIM
                                                                                                               installment                               

  `InstallmentCalculatorTableCredit`    `sem juros`   tabela               juros zero      direto              credit            snapshot comercial      SIM
                                                                                                               installment                               

  Aba Pix                               nenhum valor  ausência de valor    preço Pix não   ausência            ---               não preencher           NÃO
                                                                           confirmado                                                                    

  Promoção Prime/cupom                  `15%`         promoção             promoção        descartado          ---               não usar como desconto  NÃO
                                                      independente         específica                                            principal               
  -------------------------------------------------------------------------------------------------------------------------------------------------------------------

------------------------------------------------------------------------

# 39. Estado dos campos originais

  ------------------------------------------------------------------------------
  Campo original          Estado após FASE 0 v2   Decisão
  ----------------------- ----------------------- ------------------------------
  `current_price`         Semântica esclarecida   representar o preço
                                                  principal/customer-visible da
                                                  oferta, preservando condição

  `previous_price`        Não equivale            manter separado e preencher
                          automaticamente a       somente com evidência adequada
                          `basisPrice`            

  `discount_percentage`   Condicional             usar somente quando associado
                                                  à promoção correta

  `sold_percentage`       Confirmado              `dealDetails.percentClaimed`
                                                  preferencial

  `seller_name`           Confirmado              fonte da página

  `delivery_provider`     Confirmado              fonte da página

  `seller_type`           Normalizado             `AMAZON`, `THIRD_PARTY`,
                                                  `UNKNOWN`

  `delivery_type`         Normalizado             `AMAZON`, `THIRD_PARTY`,
                                                  `UNKNOWN`

  `source`                Confirmado              origem efetiva

  `collected_at`          Confirmado              timestamp da coleta
  ------------------------------------------------------------------------------

------------------------------------------------------------------------

# 40. Novos conceitos comerciais identificados

A investigação mostrou necessidade de representar, conceitualmente,
informações como:

``` text
basis_price
customer_visible_price
payment_condition
cash_payment_price
discount_percentage
discount_condition
payment_methods
credit_installment_count
credit_installment_amount
credit_installment_total
interest
```

### Observação

A lista acima é o **resultado semântico da investigação**, não uma
determinação de que todos esses nomes devam ser implementados
literalmente como colunas ou atributos.

A decisão final de estrutura pertence à fase de evolução do
domínio/persistência.

------------------------------------------------------------------------

# 41. Critérios de conclusão da FASE 0 v2

  -----------------------------------------------------------------------
  Critério                            Resultado
  ----------------------------------- -----------------------------------
  Fonte funcional documentada         CONCLUÍDO

  Fonte técnica observada             CONCLUÍDO

  Status da fonte interna documentado CONCLUÍDO

  Preferência por fonte oficial       CONCLUÍDO
  documentada                         

  ASIN documentado                    CONCLUÍDO

  `% vendidos` documentado            CONCLUÍDO

  Seller documentado                  CONCLUÍDO

  Delivery documentado                CONCLUÍDO

  `current_price` semanticamente      CONCLUÍDO
  esclarecido                         

  `basisPrice` identificado           CONCLUÍDO

  `basisPrice` separado de            CONCLUÍDO
  `previous_price`                    

  Preço à vista identificado          CONCLUÍDO

  Condição do preço à vista           CONCLUÍDO
  identificada                        

  Preço Pix específico investigado    CONCLUÍDO --- não confirmado
                                      numericamente

  Desconto Pix não inferido           CONCLUÍDO

  Desconto de 15% contextualizado     CONCLUÍDO

  Segunda promoção de 15% descartada  CONCLUÍDO

  Parcelamento identificado           CONCLUÍDO

  Número de parcelas identificado     CONCLUÍDO

  Valor da parcela identificado       CONCLUÍDO

  Total parcelado identificado        CONCLUÍDO

  Juros identificados                 CONCLUÍDO

  Regra de seleção de parcelamento    CONCLUÍDO
  definida                            

  Fallbacks definidos                 CONCLUÍDO

  Regras de não inferência definidas  CONCLUÍDO

  Mapa Fonte → Campo → Semântica      CONCLUÍDO
  definido                            

  Impacto em domínio identificado     CONCLUÍDO

  Impacto em persistência             CONCLUÍDO
  identificado                        

  Impacto em publicação identificado  CONCLUÍDO

  Código alterado                     NÃO --- corretamente preservado
                                      para fases posteriores
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 42. Pendências para fases posteriores

A FASE 0 v2 está concluída, mas suas decisões deverão ser implementadas
nas fases correspondentes.

Permanecem para fases posteriores:

1.  evolução do modelo de domínio;
2.  evolução do schema PostgreSQL;
3.  nova migration, caso necessária;
4.  implementação do parser;
5.  implementação da normalização;
6.  persistência dos novos conceitos comerciais;
7.  validação automatizada das condições;
8.  implementação da regra de seleção do parcelamento;
9.  geração de publicação;
10. testes unitários e integrados;
11. validação da fonte efetivamente escolhida para produção;
12. configuração de credenciais e integrações oficiais;
13. observabilidade e auditoria operacional.

A ordem das fases deve ser preservada. O projeto estabelece uma
sequência incremental e verificável, sem antecipar responsabilidades.
fileciteturn17file2L658-L693

------------------------------------------------------------------------

# 43. Relação com as fases já concluídas

## FASE 0 original

Concluída originalmente, mas com lacunas na semântica de preços e
condições de pagamento.

## FASE 0 v2

Concluída com o fechamento da investigação comercial.

## FASE 1

Permanece concluída e não precisa ser alterada. Sua responsabilidade foi
estabelecer a fundação Java e a estrutura arquitetural.
fileciteturn15file3L723-L743

## FASE 2

Permanece concluída.

O schema atual continua sendo o estado persistido existente. Qualquer
evolução deverá ocorrer por migration versionada.
fileciteturn16file1L615-L665

## FASE 3

Permanece concluída.

O `OfferSnapshot` atual será revisado na fase apropriada para incorporar
as necessidades comerciais identificadas. Não se deve alterar o domínio
diretamente dentro desta FASE 0.

------------------------------------------------------------------------

# 44. Ordem recomendada após a FASE 0 v2

O fluxo continua:

``` text
FASE 0
  │
  ▼
FASE 1 — Fundação Java
  │
  ▼
FASE 2 — PostgreSQL + migrations
  │
  ▼
FASE 3 — Domínio + contratos
  │
  ▼
FASE 4 — Configuração e segredos
  │
  ▼
FASE 5 — Coleta
  │
  ▼
FASE 6 — Parser, ASIN e normalização
  │
  ▼
FASE 7 — Enriquecimento oficial
  │
  ▼
FASE 8 — Validação Amazon
  │
  ▼
FASE 9 — Filtros
  │
  ▼
FASE 10 — Score
  │
  ▼
FASE 11 — Histórico e momentum
  │
  ▼
FASE 12 — Orquestração
  │
  ▼
FASE 13 — Interface
  │
  ▼
FASE 14 — Publicação
  │
  ▼
FASE 15+ — testes, observabilidade,
           agendamento, canais,
           resiliência, segurança
```

A ordem oficial do projeto preserva a evolução incremental e evita
misturar coleta, domínio e publicação. fileciteturn17file2L658-L683

------------------------------------------------------------------------

# 45. Resultado final

A FASE 0 v2 fecha a investigação comercial necessária para continuar o
projeto sem depender de hipóteses sobre preços.

Para a oferta investigada, foi estabelecido:

``` text
BASIS / LIST PRICE
R$ 299,00

CUSTOMER VISIBLE PRICE
R$ 161,40

CASH PAYMENT PROMOTION
15% off à vista
Pix ou NuPay Limite Adicional

CREDIT
6x de R$ 31,65
sem juros

CREDIT TOTAL
R$ 189,90
```

Também foi estabelecido que:

``` text
R$ 299,00
≠
previous_price necessariamente
```

``` text
R$ 161,40
≠
pix_price necessariamente
```

``` text
15%
≠
desconto sobre R$ 299,00
```

``` text
qualquer "15%"
≠
desconto principal da oferta
```

e:

``` text
maior parcela global
≠
maior parcela sem juros no cartão
```

A regra comercial de publicação fica definida como:

``` text
Cartão de Crédito
→ somente sem juros
→ maior número de parcelas
→ publicar essa condição
```

O projeto pode agora avançar para as fases de implementação sem que a
semântica desses dados seja tratada por suposição.

------------------------------------------------------------------------

# 46. Regra principal da FASE 0 v2

> **Investigar primeiro. Modelar depois. Implementar por último.**

> **Não transformar `basisPrice` automaticamente em `previous_price`.**

> **Não transformar preço à vista automaticamente em `pix_price`.**

> **Não interpretar qualquer ocorrência de percentual como desconto da
> oferta.**

> **Não calcular preço para substituir uma evidência explícita da
> fonte.**

> **Não escolher parcelamento de modalidade diferente da regra definida
> para publicação.**

> **Quando faltar evidência, preservar a ausência em vez de inventar o
> dado.**

------------------------------------------------------------------------

# 47. Registro de encerramento

``` text
FASE 0 — Levantamento da fonte e regras
VERSÃO: v2
STATUS: CONCLUÍDA

Investigação comercial:
OK

Semântica de preços:
OK

Preço de lista:
OK

Preço atual/customer-visible:
OK

Condição à vista:
OK

Pix:
investigado — preço numérico específico não confirmado

Desconto:
OK — contextualizado

Parcelamento:
OK

Regra de seleção do parcelamento:
OK

Promoção concorrente:
identificada e descartada

Mapa Fonte → Campo → Semântica:
OK

Impacto em domínio:
documentado

Impacto em persistência:
documentado

Impacto em publicação:
documentado

Alterações de código:
NÃO REALIZADAS

Próxima ação:
seguir para a fase correspondente à implementação da evolução definida,
sem alterar fases anteriores fora do fluxo estabelecido.
```

------------------------------------------------------------------------

## 48. Referências internas do projeto

-   `FASE_0_RESULTADO.md` --- levantamento original da fonte e regras.
-   `FASE_0_v2_PRECOS_PARCELAMENTO_PIX.md` --- documento de revisitação.
-   `FASE_1_RESULTADO.md` --- fundação Java.
-   `FASE_2_RESULTADO.md` --- PostgreSQL, Flyway e persistência.
-   `FASE_3_RESULTADO.md` --- domínio e contratos internos.
-   `rasping_Amazon_projeto_revisado.pdf` --- documento arquitetural
    principal.

O resultado desta FASE 0 v2 deve ser tratado como referência de
requisitos para as próximas alterações de domínio, persistência, parser
e publicação.
