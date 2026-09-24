# ADR-0008 — Correção versionada de percent-encoding no link de associado

- **Status:** Aceita
- **Data:** 2026-09-24
- **Projeto:** Rasping Amazon
- **Fase relacionada:** FASE 13 — geração de publicação e link de associado
- **Complementa:** ADR-0004
- **Não substitui:** AMAZON_AFFILIATE_LINK_V1

---

## 1. Contexto

A geração de publicação utiliza uma estratégia explicitamente versionada para montar o link de associado da Amazon Brasil.

A versão histórica:

```text
AMAZON_AFFILIATE_LINK_V1
```

recebe uma URL de produto já conhecida pelo sistema, remove query string e fragmento e acrescenta a tag de associado.

Durante probe externo com página real foi observado um defeito quando o path da URL do produto já continha percent-encoding UTF-8.

Entrada observada:

```text
https://www.amazon.com.br/
Celular-Samsung-Recursos-Atualiza%C3%A7%C3%B5es-Seguran%C3%A7a/
dp/B0GVT7QXF7
```

Saída produzida pela V1:

```text
https://www.amazon.com.br/
Celular-Samsung-Recursos-Atualiza%25C3%25A7%25C3%25B5es-Seguran%25C3%25A7a/
dp/B0GVT7QXF7
?tag=...
```

O caractere:

```text
%
```

do percent-encoding já existente foi convertido novamente para:

```text
%25
```

produzindo dupla codificação.

---

## 2. Causa

A V1 obtém:

```text
sourceUri.getRawPath()
```

e entrega esse valor ao construtor de `URI` que recebe os componentes da URL separadamente.

Esse construtor trata o argumento de path como conteúdo que ainda precisa ser escapado.

Consequentemente:

```text
%C3%A7
```

é reinterpretado e serializado como:

```text
%25C3%25A7
```

---

## 3. Decisão de versionamento

`AMAZON_AFFILIATE_LINK_V1` não será alterada.

A publicação persistida registra explicitamente:

```text
affiliate_link_version
```

e a identidade idempotente da geração inclui:

```text
deal_evaluation_id
template_version
commercial_presentation_version
affiliate_link_version
```

Alterar a saída da V1 retroativamente faria a mesma versão representar dois comportamentos diferentes e enfraqueceria a reprodutibilidade histórica.

Será criada:

```text
AMAZON_AFFILIATE_LINK_V2
```

Novas publicações compostas pelo `AmazonPublicationComposition` utilizarão V2.

Publicações históricas que registraram V1 permanecem válidas como fatos históricos e não são reescritas.

---

## 4. Semântica da V2

A V2 continua exigindo:

```text
scheme = https
host = www.amazon.com.br
path não vazio
```

A V2 continua removendo:

```text
query string original
fragmento original
tag de associado anterior
parâmetros transitórios
```

A V2 acrescenta somente a tag configurada para a nova publicação.

A diferença está na montagem do path:

```text
raw path já percent-encoded
→ preservar encoding existente
→ não escapar '%' novamente
```

Assim:

```text
Atualiza%C3%A7%C3%B5es
```

permanece:

```text
Atualiza%C3%A7%C3%B5es
```

e não se transforma em:

```text
Atualiza%25C3%25A7%25C3%25B5es
```

---

## 5. URLs contendo Unicode não escapado

Quando a entrada contém caracteres Unicode diretamente no path, por exemplo:

```text
/Atualizações-Segurança/
```

a serialização ASCII da V2 os transforma uma única vez em UTF-8 percent-encoded:

```text
/Atualiza%C3%A7%C3%B5es-Seguran%C3%A7a/
```

---

## 6. Persistência e migration

Nenhuma nova migration é necessária.

A coluna:

```text
publication.affiliate_link_version
```

já é `TEXT`.

A restrição de unicidade da geração já inclui `affiliate_link_version`.

Portanto V1 e V2 podem coexistir sem alteração de schema.

---

## 7. Testes obrigatórios

A V2 deve comprovar pelo menos:

```text
URL simples
URL com path já percent-encoded
URL com Unicode não escapado
remoção de query string antiga
remoção de fragmento
codificação da associate tag
rejeição de HTTP
rejeição de host estrangeiro
rejeição de host não Amazon
rejeição de URL vazia
rejeição de associate tag vazia
```

O composition root deve comprovar que novas publicações registram:

```text
affiliate_link_version = AMAZON_AFFILIATE_LINK_V2
```

---

## 8. Consequência operacional

Após ativar V2, um novo probe externo deve confirmar para o mesmo ASIN que:

```text
Affiliate link version: AMAZON_AFFILIATE_LINK_V2
```

e que o link produzido contém:

```text
%C3%A7
```

quando aplicável, sem ocorrência de:

```text
%25C3
```

O probe continua utilizando associate tag fictício e rollback externo obrigatório.
