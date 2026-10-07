# ADR-0017 — Abandono do OpenWA após prova operacional

- **Status:** Aceita
- **Data:** 2026-10-07
- **Projeto:** Rasping Amazon
- **Substitui:** ADR-0016 — OpenWA como canal opcional de WhatsApp Web
- **Fase relacionada:** investigação proposta como FASE 20.5
- **Preserva:** ADR-0009, ADR-0013 e ADR-0015
- **Decisão:** não implementar `WHATSAPP_OPENWA`

---

## 1. Contexto

A ADR-0016 autorizou a adoção de um canal opcional baseado em OpenWA, condicionado à validação real do provider antes de sua incorporação ao runtime do Rasping Amazon.

A arquitetura proposta previa:

```text
Publication
    ↓
publication outbox
    ↓
fanout
    ├── TELEGRAM
    ├── WHATSAPP_MANUAL
    └── WHATSAPP_OPENWA
```

O OpenWA seria executado como processo separado, acessado pelo Java através da Easy API HTTP.

Antes de qualquer alteração no código de produção, foi realizada uma prova operacional isolada.

Nenhuma implementação `WHATSAPP_OPENWA` foi incorporada ao projeto durante essa investigação.

---

## 2. Critério de validação

Para ser adotado, não bastaria o provider conseguir enviar uma mensagem.

Era necessário demonstrar condições compatíveis com execução contínua:

```text
autenticação previsível
readiness confiável
sessão suficientemente estável
operação desacompanhada
recuperação coerente
falha fail closed
resultado externo auditável
```

Essa validação antecipada foi deliberada para impedir que uma dependência externa não comprovada contaminasse domínio, aplicação, outbox, persistência e composition root.

---

## 3. OpenWA 4.76.0

A versão 4.76.0 foi testada em ambiente real.

O browser foi iniciado e o WhatsApp Web foi alcançado, porém o bootstrap não conseguiu produzir uma sessão operacional confiável.

Resultado:

```text
REPROVADA
```

---

## 4. OpenWA 5.4.0

A versão 5.4.0 iniciou a Easy API e o browser, mas apresentou falha no mecanismo de sessão:

```text
PORTABLE_SESSION_FLUSH_FAILED
```

A sessão não atingiu estabilidade suficiente.

Resultado:

```text
REPROVADA
```

---

## 5. OpenWA 5.1.0

A versão 5.1.0 foi instalada com a família principal de pacotes OpenWA alinhada na mesma versão.

O pareamento por QR foi realizado.

Entretanto, foram observadas falhas na bridge de runtime, incluindo binding obrigatório relacionado a:

```text
OpenWA_RuntimeStateChanged
onStateChanged
```

A sessão permaneceu com sinais equivalentes a:

```text
runtimeOperational = false
runtimeBridgeReady = false
```

Resultado:

```text
REPROVADA
```

---

## 6. OpenWA 5.2.0

A versão 5.2.0 apresentou resultado diferente das anteriores.

O bootstrap conseguiu atingir:

```text
sessionLoaded = true
bridgeReady = true
state = READY
status = ready
```

Uma chamada real para:

```text
POST /api/messages/sendText
```

retornou:

```text
HTTP 200
```

e a mensagem foi efetivamente entregue ao destinatário.

Portanto:

```text
capacidade funcional de envio
=
COMPROVADA
```

---

## 7. Falha de estabilidade

Apesar do envio real bem-sucedido, a sessão não permaneceu operacional de forma estável.

Foram observados ciclos recorrentes de navegação e recuperação do WhatsApp Web, incluindo:

```text
https://web.whatsapp.com/?post_logout=1&logout_reason=0
```

Também foram observados sinais relacionados à persistência de storage do browser e degradação do runtime.

Após a degradação, o estado passava a apresentar combinações equivalentes a:

```text
connected = false
ready = false
runtimeOperational = false
runtimeBridgeReady = false
```

Em diferentes momentos foi necessária nova autenticação por QR para restaurar temporariamente a sessão.

Isso não atende ao objetivo de execução contínua e desacompanhada do Rasping Amazon.

---

## 8. Fail closed

A investigação também confirmou um comportamento positivo.

Quando a sessão não estava realmente pronta, uma tentativa de:

```text
POST /api/messages/sendText
```

foi recusada pela Easy API com:

```text
HTTP 503
API not available until the session is truly ready
```

A chamada foi bloqueada antes do envio.

Esse comportamento é compatível com a política conservadora estabelecida pela ADR-0015.

Entretanto, fail closed correto não compensa a falta de estabilidade operacional do provider.

---

## 9. Interpretação da prova

A conclusão não é:

```text
OpenWA não consegue enviar mensagens
```

A conclusão comprovada é:

```text
OpenWA 5.2.0 consegue autenticar
OpenWA 5.2.0 consegue atingir READY
OpenWA 5.2.0 consegue executar sendText real
```

porém:

```text
a sessão não apresentou estabilidade suficiente
para o modelo de execução contínua do Rasping Amazon
```

A capacidade funcional foi comprovada.

A adequação operacional foi reprovada.

---

## 10. Decisão

O Rasping Amazon não implementará:

```text
WHATSAPP_OPENWA
```

com base na prova realizada.

Não serão adicionados por consequência:

```text
adapter OpenWA
configuração OpenWA
destination OpenWA
fanout OpenWA
retry policy OpenWA
health OpenWA
container OpenWA
migration OpenWA
segredos OpenWA
```

Nenhum código incompleto deverá ser mantido apenas para preservar a possibilidade dessa integração.

---

## 11. Canais preservados

Esta decisão não altera os canais existentes:

```text
TELEGRAM
WHATSAPP_MANUAL
WHATSAPP
```

`WHATSAPP` continua representando a integração oficial com a Meta.

`WHATSAPP_MANUAL` continua sendo o mecanismo independente de staging manual.

Nenhum fallback automático OpenWA → manual será criado porque o canal OpenWA não será incorporado.

---

## 12. Pipeline preservado

A decisão não altera:

```text
coleta
parsing
enrichment
elegibilidade
filtros
score
ranking
histórico
momentum
seleção
quota
cadência
Publication
publication outbox
recovery
```

O domínio permanece ignorante da tecnologia utilizada pelos providers de publicação.

---

## 13. Persistência

Nenhuma migration é necessária.

O schema permanece:

```text
V37
```

Migrations aplicadas não serão reescritas.

---

## 14. Segurança e evidências locais

A prova foi realizada fora do repositório Git.

Não foram incorporados ao projeto:

```text
API keys de teste
QR codes
perfil de browser
dados de sessão WhatsApp
credenciais OpenWA
```

Após o encerramento da investigação, o diretório externo utilizado para a prova foi removido.

---

## 15. Relação com a ADR-0016

Esta ADR substitui a decisão arquitetural da ADR-0016.

A ADR-0016 deve permanecer no histórico porque documenta corretamente a hipótese e a arquitetura que motivaram a investigação.

A decisão vigente passa a ser:

```text
ADR-0016
    ↓
SUBSTITUÍDA

ADR-0017
    ↓
VIGENTE
```

O histórico não deve ser apagado ou reescrito para ocultar a mudança de decisão.

---

## 16. Possível reconsideração futura

Uma solução baseada em WhatsApp Web poderá ser reconsiderada somente mediante nova ADR e nova prova operacional.

Uma futura candidata deverá demonstrar, no mínimo:

```text
sessão persistente
restart previsível
readiness confiável
recuperação estável
operação desacompanhada
tratamento conservador de resultado desconhecido
```

A simples capacidade de executar `sendText` não será suficiente.

---

## 17. Resultado final

```text
OPENWA

AUTENTICAÇÃO REAL:
COMPROVADA

READY:
COMPROVADO NA 5.2.0

SENDTEXT REAL:
COMPROVADO

ENTREGA REAL:
COMPROVADA

FAIL CLOSED:
COMPROVADO

ESTABILIDADE DA SESSÃO:
REPROVADA

OPERAÇÃO DESACOMPANHADA:
REPROVADA

INTEGRAÇÃO NO RASPING AMAZON:
NÃO IMPLEMENTAR

IMPACTO NO CÓDIGO:
NENHUM

IMPACTO NO SCHEMA:
NENHUM

ADR-0016:
SUBSTITUÍDA POR ESTA ADR
```
