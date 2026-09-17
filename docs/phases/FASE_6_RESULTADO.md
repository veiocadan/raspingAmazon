# FASE 6 — Parser, ASIN e normalização

**Status:** CONCLUÍDA  
**Data:** 16/09/2026

---

## 1. Objetivo

A FASE 6 implementa a interpretação do conteúdo bruto produzido pela FASE 5, mantendo a separação entre:

```text
coleta
  ↓
conteúdo bruto
  ↓
parser
  ↓
dados interpretados
  ↓
fases posteriores
```

O parser não executa regras de elegibilidade, filtros, score, persistência, publicação ou validação efetiva de vendedor e entrega.

A responsabilidade desta fase é interpretar a estrutura observada na página de promoções, extrair os dados disponíveis de forma confiável e produzir um contrato intermediário estável para as próximas fases.

---

## 2. Escopo concluído

A FASE 6 implementou:

- contrato `DealsParser`;
- contrato `ParsedDeal`;
- parser específico da Amazon;
- tratamento de erro específico do parser;
- localização da estrutura `productSearchResponse`;
- extração da lista `products`;
- extração e validação do ASIN;
- normalização do ASIN para maiúsculas;
- extração do título;
- extração e normalização da URL do produto;
- extração do preço atual;
- extração do `basisPrice`;
- leitura do percentual de ofertas reivindicadas quando disponível;
- extração da imagem em alta resolução, com fallback para baixa resolução;
- descarte de registros sem identificador confiável;
- descarte de registros sem campos mínimos necessários;
- deduplicação considerando ASIN e contexto da oferta;
- preservação da origem e do instante da coleta;
- fixture local baseada em conteúdo real coletado da Amazon;
- testes automatizados contra dados sintéticos e contra a fixture real.

---

## 3. Componentes implementados

### 3.1 Contrato de parser

Arquivo:

```text
src/main/java/com/raspingamazon/application/parsing/contract/DealsParser.java
```

Contrato:

```text
CollectionResult
      ↓
DealsParser
      ↓
List<ParsedDeal>
```

O contrato pertence à camada de aplicação e não depende de detalhes da implementação específica da Amazon.

---

### 3.2 Resultado intermediário do parser

Arquivo:

```text
src/main/java/com/raspingamazon/application/parsing/contract/ParsedDeal.java
```

O resultado intermediário contém:

```text
asin
productUrl
title
imageUrl
currentPrice
basisPrice
previousPrice
soldPercentage
collectedAt
source
```

O contrato mantém os dados interpretados separados do domínio final.

---

### 3.3 Exceção específica

Arquivo:

```text
src/main/java/com/raspingamazon/infrastructure/amazon/parser/AmazonDealsParsingException.java
```

A exceção permite distinguir falhas de interpretação da resposta de falhas ocorridas durante a coleta.

---

### 3.4 Parser da Amazon

Arquivo:

```text
src/main/java/com/raspingamazon/infrastructure/amazon/parser/AmazonDealsParser.java
```

Implementação:

```text
DealsParser
    ↑
AmazonDealsParser
```

A implementação utiliza Jackson para interpretar a estrutura JSON embutida no conteúdo bruto coletado.

O conteúdo da página não é tratado como um JSON puro. O parser primeiro localiza a estrutura:

```text
"productSearchResponse"
```

e então extrai o objeto correspondente respeitando strings e caracteres de escape antes de entregá-lo ao `ObjectMapper`.

Essa abordagem preserva a característica observada no conteúdo real: a página contém uma estrutura JSON incorporada em um documento maior.

---

## 4. Estrutura observada na fonte

Durante a análise da fixture real, foi identificada a estrutura:

```text
productSearchResponse
    └── products
          ├── product
          ├── product
          ├── product
          └── ...
```

Dentro dos produtos foram observados campos relevantes como:

```text
asin
title
link
image
price
dealBadge
dealDetails
customerReviews
```

A implementação da FASE 6 utiliza somente os dados necessários ao escopo desta fase.

---

## 5. Política de ASIN

O ASIN é tratado como identificador fundamental para as próximas etapas.

A política implementada é:

1. localizar o campo `asin`;
2. remover espaços externos;
3. normalizar para maiúsculas;
4. validar o formato:

```text
[A-Z0-9]{10}
```

5. descartar o registro quando não existir um ASIN confiável.

A implementação não tenta fabricar ou inferir um ASIN ausente.

Isso evita que registros sem identificação confiável avancem para as próximas fases.

---

## 6. URL do produto

A fonte pode fornecer links relativos, por exemplo:

```text
/dp/B087WLJH8Y
```

O parser normaliza a URL utilizando a origem da própria coleta.

O resultado esperado é uma URL absoluta da Amazon Brasil, por exemplo:

```text
https://www.amazon.com.br/dp/B087WLJH8Y
```

O parser não gera links de associado.

A geração efetiva de link de associado permanece em fase posterior, conforme a separação definida no projeto.

---

## 7. Título

O título é extraído do campo correspondente do produto.

Registros sem título confiável não são considerados resultados válidos pelo parser.

O parser realiza somente a normalização necessária para produzir o valor textual utilizável. Não aplica regras de publicação ou classificação comercial ao título.

---

## 8. Preço atual

O preço atual é obtido de:

```text
price
  └── priceToPay
        └── price
```

O valor é convertido para `BigDecimal`.

A implementação aceita representação decimal com ponto ou vírgula quando aplicável ao valor recebido.

Preço inválido ou ausente impede que o registro seja considerado um `ParsedDeal` válido.

---

## 9. `basisPrice` e `previousPrice`

Uma decisão importante da FASE 6 foi preservar a diferença semântica entre:

```text
basisPrice
previousPrice
```

O campo:

```text
price.basisPrice.price
```

é mapeado para:

```text
basisPrice
```

e não é convertido automaticamente em:

```text
previousPrice
```

Na FASE 6:

```text
basisPrice → preenchido quando fornecido pela fonte
previousPrice → null
```

Isso preserva a regra comercial já estabelecida no projeto:

> `basisPrice` não deve ser transformado automaticamente em `previousPrice`.

Também não é criado nenhum `pixPrice` por inferência.

---

## 10. Percentual de ofertas reivindicadas

A fonte real apresentou:

```text
dealDetails.percentClaimed
```

e também mensagens textuais como:

```text
89% comprados
```

Quando o valor numérico `percentClaimed` está disponível, ele é mapeado para:

```text
soldPercentage
```

O valor é validado para permanecer no intervalo:

```text
0 ≤ soldPercentage ≤ 100
```

Quando o valor não está disponível ou não é válido, o resultado permanece sem esse campo, em vez de inventar um percentual.

A implementação não confunde esse percentual com:

- desconto;
- percentual de avaliação;
- percentual de distribuição das avaliações;
- qualquer outro percentual encontrado no documento.

Essa separação é importante porque a estrutura real também contém percentuais relacionados às avaliações dos clientes.

---

## 11. Desconto

A estrutura observada contém também:

```text
dealBadge
```

com valores como:

```text
70% off
66% off
```

Esse dado não foi convertido automaticamente em uma regra de negócio ou em um atributo universal de desconto do `ParsedDeal`.

A FASE 6 mantém a interpretação estrutural separada da decisão comercial.

Não é permitido transformar qualquer percentual encontrado na página em desconto principal sem regra explícita.

---

## 12. Imagem

A extração da imagem utiliza preferencialmente:

```text
image.hiRes.baseUrl
image.hiRes.extension
```

Quando a imagem de alta resolução não está disponível, existe fallback para a estrutura de baixa resolução.

O parser produz uma URL de imagem quando essa informação está disponível.

A ausência de imagem não cria uma imagem artificial e não altera a identificação do produto.

---

## 13. Registros inválidos

O parser utiliza política conservadora.

São descartados registros sem dados mínimos confiáveis, incluindo casos como:

```text
ASIN ausente
ASIN inválido
título ausente
URL ausente
preço atual ausente
preço atual inválido
```

O objetivo é evitar que registros estruturalmente incompletos contaminem as fases posteriores.

O parser não tenta corrigir silenciosamente dados que não possam ser determinados de forma confiável.

---

## 14. Deduplicação

A fixture e os testes incluem cenários de duplicação.

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

Essa combinação preserva a possibilidade de o mesmo ASIN aparecer associado a contextos de oferta diferentes.

Portanto:

```text
mesmo ASIN
+
mesmo contexto de oferta
→ duplicata
```

enquanto:

```text
mesmo ASIN
+
contexto de oferta diferente
→ registros distintos
```

A regra evita tanto duplicação literal quanto uma deduplicação agressiva que eliminaria ofertas diferentes do mesmo produto.

---

## 15. Fixture real

Foi utilizada uma fixture local:

```text
src/test/resources/amazon/deals-sample.html
```

A fixture representa conteúdo real coletado da página de promoções da Amazon Brasil.

Isso permite executar o parser sem depender de uma nova chamada de rede durante os testes.

O objetivo é preservar:

```text
fonte real
    ↓
conteúdo capturado
    ↓
fixture versionada
    ↓
parser
    ↓
testes reproduzíveis
```

A fixture também permitiu confirmar que o parser consegue encontrar a estrutura de produtos dentro do documento real.

---

## 16. Exemplos observados na fixture

Entre os registros analisados foram observados produtos com ASINs como:

```text
B087WLJH8Y
B08R93TVRG
```

Também foram observados dados como:

```text
currentPrice
basisPrice
dealDetails.percentClaimed
customerReviews
image
```

A estrutura real confirmou que:

```text
basisPrice
```

não deve ser tratado automaticamente como:

```text
previousPrice
```

e que:

```text
dealDetails.percentClaimed
```

é a fonte estrutural adequada para o percentual de ofertas reivindicadas disponível nessa resposta.

---

## 17. Testes

A suíte automatizada foi executada com:

```text
mvn clean test
```

Resultado:

```text
Tests run: 161
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

Os testes abrangem:

- parsing de estrutura válida;
- ausência da estrutura principal;
- ASIN inválido;
- ausência de ASIN;
- título ausente;
- URL ausente;
- preço atual ausente;
- preço atual inválido;
- percentual de ofertas ausente;
- percentual de ofertas inválido;
- separação entre `basisPrice` e `previousPrice`;
- deduplicação;
- mesmo ASIN com contexto de oferta diferente;
- normalização de ASIN em minúsculas;
- fallback de imagem;
- processamento da fixture real;
- extração de ASINs reais;
- extração de preços reais;
- identidade dos produtos;
- percentual de ofertas quando disponível;
- preservação de `previousPrice`;
- preservação de metadados da coleta.

---

## 18. Independência da Amazon durante os testes

Os testes do parser utilizam conteúdo local.

Portanto:

```text
teste do parser
      ↓
fixture local
      ↓
sem chamada HTTP
```

A disponibilidade da Amazon não é requisito para executar a suíte de testes do parser.

A coleta real continua sendo responsabilidade da FASE 5.

Essa separação mantém a fronteira:

```text
FASE 5
coleta

FASE 6
interpretação
```

---

## 19. O que não foi implementado nesta fase

Para preservar a ordem arquitetural, a FASE 6 não implementa:

- enriquecimento por fonte oficial;
- consulta individual de produto na Amazon;
- validação efetiva de vendedor;
- validação efetiva de entrega;
- filtros de negócio;
- score;
- ranking;
- momentum;
- persistência dos resultados normalizados;
- orquestração;
- geração de publicação;
- link de associado;
- scheduler;
- filas;
- WhatsApp;
- Telegram;
- observabilidade avançada;
- resiliência operacional;
- segurança operacional;
- Excel/CSV como fluxo principal.

Essas responsabilidades permanecem nas fases correspondentes do roadmap.

---

## 20. Limites da normalização desta fase

A FASE 6 normaliza os campos necessários ao contrato atualmente implementado:

```text
ASIN
URL
texto do título
preço
percentual de ofertas
imagem
metadados de coleta
```

Não existe, neste momento, um campo de data no `ParsedDeal` que exija uma normalização de datas.

Também não foi criada uma regra para inferir dados que a fonte não forneça explicitamente.

A ausência de informação permanece ausência de informação.

---

## 21. Decisões arquiteturais preservadas

A FASE 6 mantém as decisões anteriores:

- Java + SQL como núcleo;
- domínio independente da infraestrutura;
- coleta separada do parsing;
- contratos internos antes das integrações;
- conteúdo bruto separado da interpretação;
- interfaces oficiais priorizadas;
- idempotência como princípio;
- rastreabilidade;
- diagnóstico isolado;
- credenciais fora do código;
- publicação desacoplada;
- ordem incremental das fases;
- ausência de inferências comerciais não autorizadas;
- `basisPrice` separado de `previousPrice`;
- ausência de `pixPrice` inventado;
- percentuais sem significado comercial automático.

---

## 22. Critérios de conclusão

| Critério | Resultado |
|---|---|
| Contrato `DealsParser` | CONCLUÍDO |
| Contrato `ParsedDeal` | CONCLUÍDO |
| Parser específico da Amazon | CONCLUÍDO |
| Identificação de `productSearchResponse` | CONCLUÍDO |
| Extração da lista de produtos | CONCLUÍDO |
| Extração e validação de ASIN | CONCLUÍDO |
| Normalização de ASIN | CONCLUÍDO |
| Extração de título | CONCLUÍDO |
| Normalização de URL | CONCLUÍDO |
| Extração de preço atual | CONCLUÍDO |
| Extração de `basisPrice` | CONCLUÍDO |
| Separação de `previousPrice` | CONCLUÍDO |
| Extração de `soldPercentage` | CONCLUÍDO |
| Extração de imagem | CONCLUÍDO |
| Política para registros sem ASIN confiável | CONCLUÍDO |
| Deduplicação | CONCLUÍDO |
| Fixture real local | CONCLUÍDO |
| Testes unitários | CONCLUÍDO |
| Testes contra fixture real | CONCLUÍDO |
| `mvn clean test` | PASSOU |
| Testes executados | 161 |
| Falhas | 0 |
| Erros | 0 |
| Ignorados | 0 |
| Build | SUCCESS |
| Dependência de rede nos testes do parser | NÃO |
| Regras de negócio antecipadas | NÃO |

---

## 23. Resultado final

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

---

## 24. Próxima fase

A próxima etapa oficial é:

# FASE 7 — Enriquecimento oficial

A FASE 7 deverá tratar o enriquecimento dos produtos por fonte oficial, mantendo a separação entre:

```text
conteúdo coletado
    ↓
parser / normalização
    ↓
produto identificado
    ↓
fonte oficial
    ↓
dados enriquecidos
```

A próxima fase não deve antecipar:

- validação final de vendedor;
- validação final de entrega;
- filtros;
- score;
- ranking;
- momentum;
- publicação.

Essas responsabilidades permanecem nas fases posteriores definidas pelo projeto.
