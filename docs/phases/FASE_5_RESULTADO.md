# FASE 5 — RESULTADO CONSOLIDADO

**Projeto:** Rasping Amazon  
**Fase:** 5 — Coleta da página de promoções  
**Data:** 16/09/2026  
**Status:** CONCLUÍDA

---

## 1. Objetivo

A FASE 5 teve como objetivo implementar a coleta da fonte real de promoções da Amazon Brasil de forma isolada, reproduzível e desacoplada das regras de negócio.

A responsabilidade desta fase termina na obtenção e entrega do conteúdo bruto da fonte. Parsing, identificação/normalização de ASIN, enriquecimento, validação comercial, filtros, score e publicação permanecem nas fases posteriores.

---

## 2. Fonte funcional

A fonte definida durante a investigação da FASE 0 foi:

```text
https://www.amazon.com.br/deals
```

A implementação específica da Amazon concentra essa definição em `AmazonDealsCollector`.

A fonte técnica interna `/d2b/api/v1/products/search`, observada durante a investigação, não foi transformada automaticamente em interface de produção. A preferência por interfaces oficiais permanece preservada.

---

## 3. Arquitetura implementada

A coleta foi organizada em camadas:

```text
AmazonDealsCollector
        ↓
HttpCollectionCollector
        ↓
JavaHttpTransport
        ↓
HTTP
        ↓
CollectionResult
```

### `AmazonDealsCollector`

Responsável exclusivamente pela fronteira específica da Amazon.

Não interpreta:

- HTML;
- JSON;
- ASIN;
- preços;
- percentual vendido;
- vendedor;
- entrega;
- regras de negócio.

### `HttpCollectionCollector`

Adapta o transporte HTTP ao contrato de coleta.

Responsabilidades:

- receber `CollectionRequest`;
- executar a chamada através de `HttpTransport`;
- aceitar somente respostas HTTP `2xx`;
- converter falhas para `CollectionException`;
- produzir `CollectionResult` somente após validação do status.

### `JavaHttpTransport`

Responsável exclusivamente pelo transporte HTTP usando a JDK.

Características:

- requisição `GET`;
- timeout configurável pela composição;
- tratamento de falhas de transporte;
- preservação da interrupção da thread;
- User-Agent estável:

```text
RaspingAmazon/1.0
```

O User-Agent não é aleatório. O valor fixo preserva a reprodutibilidade das execuções.

### Contratos

Foram estabelecidos:

```text
CollectionCollector
CollectionRequest
CollectionResult
CollectionException
HttpTransport
HttpTransportResponse
```

Os contratos não dependem de Amazon, HTML, JSON, PostgreSQL ou biblioteca HTTP específica.

---

## 4. Tratamento de falhas

A implementação trata explicitamente:

| Situação | Comportamento |
|---|---|
| URI nula | rejeitada |
| URI relativa | rejeitada pelo contrato |
| HTTP `2xx` | aceita |
| HTTP fora de `2xx` | convertido em `CollectionException` pelo collector |
| Falha de conexão | convertido em `CollectionException` |
| Timeout | convertido em `CollectionException` |
| Interrupção da thread | flag restaurada e falha convertida |
| Corpo nulo | rejeitado pelo contrato |
| Corpo vazio/branco | rejeitado pelo contrato |
| Redirect | tratado pelo `HttpClient` configurado com `Redirect.NORMAL` |

A resposta HTTP de erro não é encaminhada às etapas seguintes como se fosse uma coleta válida.

---

## 5. User-Agent e coleta real

Durante a investigação operacional da FASE 5, foi observada diferença de comportamento entre requisições sem identificação equivalente e uma requisição identificada como:

```text
RaspingAmazon/1.0
```

A implementação Java passou a enviar esse User-Agent de forma estável.

Após a alteração, a fonte real respondeu com sucesso em execuções da coleta.

Exemplos registrados durante a validação:

```text
HTTP 200
Content length: 514685
```

e posteriormente:

```text
HTTP 200
Content length: 522054
```

Esses tamanhos representam conteúdos brutos recebidos em execuções distintas e não devem ser tratados como tamanho fixo da fonte.

---

## 6. Probe da fonte real

Foi criada:

```text
AmazonDealsCollectorRealSourceProbe
```

A probe:

1. utiliza a mesma composição funcional da coleta;
2. acessa a fonte real;
3. registra fonte, instante e tamanho do conteúdo;
4. salva o conteúdo bruto somente como artefato diagnóstico;
5. não participa como dependência do parser ou do domínio.

Artefato diagnóstico:

```text
target/diagnostics/amazon-deals-real.html
```

O artefato permanece fora do fluxo de negócio e serve apenas como evidência para investigação.

A probe não segue o padrão de descoberta do Surefire, evitando que `mvn test` dependa da disponibilidade da Amazon.

---

## 7. Testes automatizados

A suíte atual foi executada com:

```text
mvn -q test
```

Resultado consolidado:

```text
Testes executados: 136
Falhas: 0
Erros: 0
Ignorados: 0
BUILD SUCCESS
```

O relatório do Surefire contém 33 classes de teste e totaliza 136 testes executados.

### Cobertura relevante da FASE 5

Foram protegidos por testes:

- contrato de `CollectionCollector`;
- `CollectionException`;
- `CollectionRequest`;
- `CollectionResult`;
- transporte HTTP;
- User-Agent `RaspingAmazon/1.0`;
- falha de transporte;
- timeout;
- redirecionamento;
- status HTTP de erro;
- resposta vazia;
- integração local entre adaptadores;
- adaptador `AmazonDealsCollector`;
- execução de coleta contra a fonte real.

O teste da fonte real permanece diagnóstico e não transforma indisponibilidade externa em dependência obrigatória de todos os testes da suíte.

---

## 8. Banco e infraestrutura existente

A execução dos testes confirmou que a infraestrutura PostgreSQL/Flyway permaneceu funcional.

Resultado observado:

```text
PostgreSQL: 18.6
Flyway: 11.14.1
Migrations validadas: 2
Schema: versão 2
Schema up to date: sim
```

A FASE 5 não criou nem alterou migrations.

---

## 9. O que NÃO foi implementado nesta fase

Para preservar a ordem do projeto, esta fase não implementou:

- parser HTML/JSON;
- extração de ASIN;
- normalização de produtos;
- enriquecimento por fonte oficial;
- validação de vendedor;
- validação de entrega;
- filtros;
- score;
- ranking;
- momentum;
- nova persistência da coleta;
- orquestração;
- scheduler;
- interface operacional;
- publicação;
- WhatsApp;
- Telegram;
- observabilidade avançada;
- resiliência;
- segurança operacional.

Esses itens pertencem às fases posteriores.

---

## 10. Critérios de conclusão

| Critério | Resultado |
|---|---|
| `AmazonDealsCollector` isolado | CONCLUÍDO |
| Fonte real definida | CONCLUÍDO |
| Coleta real executada | CONCLUÍDO |
| Conteúdo bruto capturado | CONCLUÍDO |
| User-Agent estável | CONCLUÍDO |
| Timeout tratado | CONCLUÍDO |
| Indisponibilidade/falha HTTP tratada | CONCLUÍDO |
| Respostas vazias rejeitadas | CONCLUÍDO |
| Redirect tratado | CONCLUÍDO |
| Contrato de entrada estável | CONCLUÍDO |
| Diagnóstico isolado | CONCLUÍDO |
| Testes automatizados | CONCLUÍDO |
| Testes executados | 136 |
| Falhas | 0 |
| Erros | 0 |
| Ignorados | 0 |
| Build | SUCCESS |
| PostgreSQL/Flyway preservados | CONCLUÍDO |
| Parsing/normalização antecipados | NÃO |

---

## 11. Decisões preservadas

A FASE 5 mantém as decisões arquiteturais anteriores:

- Java + SQL como núcleo;
- domínio independente da infraestrutura;
- coleta separada do domínio;
- contratos internos antes das integrações;
- conteúdo bruto separado do parsing;
- interfaces oficiais priorizadas;
- idempotência como princípio;
- rastreabilidade e diagnóstico;
- credenciais fora do código;
- publicação desacoplada;
- ordem incremental das fases.

---

## 12. Estado final

```text
FASE 5 — Coleta da página de promoções
STATUS: CONCLUÍDA

Fonte:
https://www.amazon.com.br/deals

AmazonDealsCollector:
OK

HttpCollectionCollector:
OK

JavaHttpTransport:
OK

User-Agent:
RaspingAmazon/1.0

Coleta real:
OK

Conteúdo bruto:
CAPTURADO

Tratamento de falhas:
OK

Timeout:
OK

Redirect:
OK

Diagnóstico:
OK

Testes:
136

Falhas:
0

Erros:
0

Ignorados:
0

Build:
SUCCESS

PostgreSQL:
OK

Flyway:
OK

Schema:
versão 2

Próxima etapa:
FASE 6 — Parser, ASIN e normalização
```

---

## 13. Próxima fase

A próxima etapa oficial é:

### FASE 6 — Parser, ASIN e normalização

A FASE 6 deverá receber o conteúdo bruto produzido pela coleta e tratar sua interpretação de forma separada.

Nenhuma responsabilidade da FASE 6 foi implementada antecipadamente nesta fase.

---

## 14. Registro de encerramento

```text
FASE 5 — Coleta da página de promoções
DATA: 16/09/2026
STATUS: CONCLUÍDA

Fonte real:
OK

Coleta:
OK

User-Agent:
RaspingAmazon/1.0

Testes:
136

Falhas:
0

Erros:
0

Ignorados:
0

Build:
SUCCESS

PostgreSQL/Flyway:
OK

Próxima fase:
FASE 6 — Parser, ASIN e normalização
```
