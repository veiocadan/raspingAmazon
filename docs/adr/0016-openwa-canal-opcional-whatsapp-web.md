# ADR-0016 — OpenWA como canal opcional de WhatsApp Web

- **Status:** Aceita
- **Data:** 2026-10-06
- **Projeto:** Rasping Amazon
- **Fase proposta:** FASE 20.5 — Integração OpenWA como canal opcional de WhatsApp Web
- **Fases relacionadas:** FASE 18 — Seleção operacional, canais e outbox; FASE 19 — Telegram e WhatsApp; FASE 20 — Resiliência, recuperação e falhas de produção
- **Complementa:** ADR-0009, ADR-0013 e ADR-0015
- **Não substitui:** integração oficial WhatsApp/Meta nem o fluxo `WHATSAPP_MANUAL`
- **Escopo:** adapter OpenWA, sessão/autenticação, destino controlado, fanout paralelo, falha não bloqueante, ausência deliberada de retry automático e implantação futura em computador pessoal ou Raspberry Pi

---

## 1. Contexto

Ao final da FASE 19, o Rasping Amazon possui canais concretos de publicação e uma arquitetura de entrega desacoplada:

```text
Publication
    ↓
outbox
    ↓
worker
    ↓
PublicationChannel
    ↓
adapter concreto
    ↓
provider externo
    ↓
PublicationAttempt
```

Entre os mecanismos já disponíveis estão:

```text
TELEGRAM
WHATSAPP
WHATSAPP_MANUAL
```

O canal `WHATSAPP` representa a integração oficial com a API da Meta.

O canal `WHATSAPP_MANUAL` representa staging privado no Telegram com conteúdo preparado para WhatsApp, seguido de cópia manual pelo operador.

A FASE 20 formaliza regras de resiliência, recuperação, classificação de falhas e tratamento de resultado externo desconhecido.

Durante o uso da integração oficial do WhatsApp foi identificada uma limitação operacional relevante para este projeto:

```text
API oficial Meta
    ↓
regras de provider
    ↓
templates / requisitos de aprovação
    ↓
menor liberdade sobre o conteúdo enviado
```

Para o caso de uso atual, o objetivo não é envio massivo nem aquisição automática de destinatários.

O uso pretendido é controlado:

```text
Rasping Amazon
    ↓
WhatsApp da conta conectada ao OpenWA
    ↓
contato secundário controlado pelo operador
ou
grupo controlado pelo operador
```

O projeto já possui uma alternativa operacional independente:

```text
WHATSAPP_MANUAL
```

Portanto, o novo canal OpenWA não precisa possuir disponibilidade equivalente a um provider crítico.

Ele deverá ser tratado como:

```text
canal adicional
opcional
best effort
não bloqueante
temporário enquanto útil
```

---

## 2. Motivação

A integração OpenWA é considerada para reduzir a dependência operacional da API oficial da Meta enquanto preserva a arquitetura existente.

O objetivo não é substituir o núcleo de publicação.

O objetivo é adicionar outra implementação de:

```text
PublicationChannel
```

sem alterar:

```text
elegibilidade
filtros
score
ranking
momentum
seleção operacional
quota comercial
cadência
PublicationGenerator
```

A arquitetura deverá continuar permitindo:

```text
Meta Cloud API
OpenWA
WHATSAPP_MANUAL
```

como mecanismos independentes.

---

## 3. Princípio central

A decisão principal desta ADR é:

```text
OpenWA é conveniência de entrega
e não dependência do funcionamento do Rasping Amazon
```

Consequentemente:

```text
OpenWA disponível
    → tenta entregar

OpenWA indisponível
    → registra falha
    → restante do sistema continua
```

A indisponibilidade do OpenWA nunca deverá interromper a running global.

---

# Decisão

## 4. Será criado um canal operacional próprio para OpenWA

O OpenWA possuirá identidade própria no sistema.

Nome conceitual:

```text
WHATSAPP_OPENWA
```

Essa identidade não deverá ser confundida com `WHATSAPP`, que continuará representando a integração oficial da Meta.

Também não deverá ser confundida com `WHATSAPP_MANUAL`, que continuará representando o staging privado existente.

A distinção permitirá auditoria equivalente a:

```text
Publication #123

TELEGRAM
SUCCESS

WHATSAPP_MANUAL
SUCCESS

WHATSAPP_OPENWA
FAILED
```

sem ocultar qual mecanismo foi utilizado.

---

## 5. O adapter continuará implementando `PublicationChannel`

A integração não criará um caminho paralelo ao mecanismo de publicação existente.

Fluxo:

```text
PublicationCommand
    ↓
PublicationChannel
    ↑
OpenWaPublicationChannel
```

A implementação deverá:

```text
validar configuração
validar destino
formatar conteúdo adequado
chamar o serviço OpenWA
interpretar a resposta
produzir PublicationResult
permitir persistência de PublicationAttempt
```

Detalhes próprios do OpenWA não deverão escapar como contrato funcional normal para a aplicação.

---

## 6. OpenWA será executado como serviço separado

O runtime OpenWA não será incorporado diretamente ao processo Java.

Arquitetura alvo:

```text
Rasping Amazon
    ↓ HTTP
OpenWA Easy API
    ↓
runtime WhatsApp Web
    ↓
WhatsApp
```

A aplicação Java continuará sendo responsável por:

```text
regra de negócio
seleção
outbox
auditoria
estado durável
```

O OpenWA será responsável pela fronteira específica de WhatsApp Web.

Conceitualmente:

```text
Java
    ≠
runtime Node.js do OpenWA
```

Essa separação permite executar o OpenWA no mesmo computador, em processo/container separado e, futuramente, no Raspberry Pi, sem incorporar Node.js ao núcleo Java.

---

## 7. Comunicação será preferencialmente HTTP local

A primeira implementação deverá priorizar a Easy API HTTP do OpenWA.

Exemplo conceitual:

```text
Rasping Amazon
    ↓
http://openwa:8080
    ↓
OpenWA
```

A comunicação deverá possuir:

```text
base URI configurável
timeout configurável
API key quando habilitada
sessionId controlado
```

Credenciais não serão versionadas.

---

## 8. A versão do OpenWA deverá ser controlada

A implantação não deverá depender silenciosamente de atualização automática para qualquer versão mais recente.

A versão utilizada em ambiente operacional deverá ser fixada de forma reproduzível.

Conceitualmente:

```text
OPENWA_VERSION=<versão validada>
```

Uma nova versão deverá ser adotada somente após teste de compatibilidade.

A dependência é sensível a mudanças de WhatsApp Web, OpenWA, browser e contrato da Easy API.

---

## 9. Destino poderá ser contato ou grupo

A primeira implementação deverá suportar dois tipos de destino:

```text
CONTACT
GROUP
```

### Contato

Destino controlado pelo operador, destinado principalmente ao contato secundário utilizado no fluxo privado.

### Grupo

Grupo previamente conhecido e controlado pelo operador.

O sistema não deverá:

```text
varrer contatos
procurar destinatários arbitrariamente
criar listas de envio em massa
descobrir grupos por heurística
```

O destino operacional será explicitamente configurado.

---

## 10. O identificador do destino será explícito

A primeira implementação não deverá localizar grupo por nome a cada publicação.

Será utilizado identificador estável aceito pelo OpenWA.

Conceitualmente:

```text
OPENWA_DESTINATION_TYPE=CONTACT
OPENWA_DESTINATION=<chat-id>
```

ou:

```text
OPENWA_DESTINATION_TYPE=GROUP
OPENWA_DESTINATION=<group-chat-id>
```

A validação do formato deverá ser centralizada no adapter/configuração correspondente.

---

## 11. `WHATSAPP_MANUAL` permanecerá sempre disponível em paralelo

A introdução do OpenWA não substituirá o fluxo manual existente.

O comportamento desejado é:

```text
Publication
    ↓
fanout
    ├── TELEGRAM
    ├── WHATSAPP_MANUAL
    └── WHATSAPP_OPENWA
```

Quando configurados e habilitados, `WHATSAPP_MANUAL` e `WHATSAPP_OPENWA` poderão receber a mesma publicação.

Essa é uma decisão deliberada.

O canal manual não será ativado em reação à falha do OpenWA. Ele já estará funcionando independentemente.

---

## 12. Não haverá fallback automático de OpenWA para `WHATSAPP_MANUAL`

Fluxo rejeitado:

```text
OpenWA falhou
    ↓
criar automaticamente trabalho WHATSAPP_MANUAL
```

Motivo:

```text
WHATSAPP_MANUAL já existe em paralelo
```

Criar fallback adicional aumentaria risco de duplicação de outbox, duplicação operacional e confusão de auditoria.

Portanto, `WHATSAPP_OPENWA` e `WHATSAPP_MANUAL` serão ramos independentes.

---

## 13. Não existe aprovação humana no pipeline

A introdução do OpenWA não modifica a decisão vigente da interface operacional não bloqueante.

Não será criado:

```text
approval
confirm
authorize send
wait for operator
```

antes da tentativa OpenWA.

Fluxo:

```text
Publication selecionada
    ↓
READY
    ↓
outbox
    ↓
WHATSAPP_OPENWA
```

O operador somente poderá precisar atuar quando houver necessidade de restabelecer a sessão do OpenWA.

Essa atuação não é aprovação da publicação.

---

## 14. OpenWA utilizará política de tentativa única

Para `WHATSAPP_OPENWA`, a política inicial será:

```text
maxAttempts = 1
```

ou mecanismo funcional equivalente.

Não haverá retry automático específico do OpenWA.

Isso vale mesmo quando a falha técnica poderia, em abstrato, ser transitória.

A razão é decisão operacional:

```text
o projeto já possui WHATSAPP_MANUAL
+
duplicata eventual não precisa ser evitada por complexidade adicional
+
não existe necessidade de tornar OpenWA um canal crítico
```

A classificação técnica da falha continuará podendo ser preservada para auditoria.

Entretanto:

```text
retryable tecnicamente
```

não implicará:

```text
retry automático no WHATSAPP_OPENWA
```

---

## 15. A ADR-0015 continua válida

A ADR-0015 estabelece semântica geral de:

```text
TRANSIENT
PERMANENT
DELIVERY_UNKNOWN
```

Esta ADR não remove essa semântica.

Ela especializa a política operacional do canal OpenWA:

```text
WHATSAPP_OPENWA
    ↓
uma tentativa automática
```

Se o resultado for conhecido como falha:

```text
persistir falha
não reenviar automaticamente
```

Se o resultado externo ficar desconhecido:

```text
DELIVERY_UNKNOWN
não reenviar automaticamente
```

Portanto, o comportamento permanece compatível com o princípio conservador da FASE 20.

---

## 16. Publicações perdidas durante desconexão não serão automaticamente reenviadas

Se uma publicação OpenWA falhar porque:

```text
sessão desconectada
autenticação requerida
serviço OpenWA indisponível
browser indisponível
timeout
```

ela não deverá retornar automaticamente à fila para nova tentativa OpenWA.

Exemplo:

```text
10:00
Publication A
OpenWA desconectado
    ↓
falha registrada

10:20
operador reconecta OpenWA

12:00
Publication B
    ↓
nova tentativa normal
```

A reconexão permite que publicações futuras voltem a funcionar.

Ela não implica replay automático das anteriores.

O staging `WHATSAPP_MANUAL` continuará disponível para tratamento humano da publicação perdida, caso o operador deseje.

---

## 17. Falha do OpenWA nunca deverá bloquear a running global

Esta é uma invariante central.

Falhas do OpenWA não deverão parar:

```text
scheduler
coleta
parsing
enrichment
persistência
avaliação
score
ranking
momentum
seleção
geração de Publication
Telegram
WHATSAPP_MANUAL
outros canais independentes
```

Fluxo esperado:

```text
WHATSAPP_OPENWA
    ↓
falhou
    ↓
PublicationAttempt registrada
    ↓
trabalho OpenWA termina
    ↓
running continua
```

Uma exception específica do provider não deverá escapar até o ponto de encerrar o runtime principal.

---

## 18. Uma falha OpenWA não deverá impedir o fanout dos outros canais

O fanout deverá preservar independência entre destinos.

Exemplo:

```text
Publication #321

TELEGRAM
    → SUCCESS

WHATSAPP_MANUAL
    → SUCCESS

WHATSAPP_OPENWA
    → AUTHENTICATION_REQUIRED
```

O resultado global não deverá ser interpretado como se toda a publicação tivesse falhado.

Cada entrega deverá possuir sua própria evidência.

---

## 19. O fanout OpenWA não consumirá nova quota comercial

Uma oferta selecionada representa uma única decisão comercial.

Se `Publication X` for distribuída por:

```text
TELEGRAM
WHATSAPP_MANUAL
WHATSAPP_OPENWA
```

isso não representa três vagas da quota.

A implementação deverá reutilizar a semântica de fanout derivado já existente.

Conceitualmente:

```text
uma seleção
uma posição
uma reserva comercial
múltiplos adapters de entrega
```

---

## 20. Sessão do OpenWA deverá ser persistente

A autenticação não deverá ser descartada a cada reinicialização.

O runtime OpenWA deverá utilizar mecanismo de sessão persistente compatível com a versão adotada.

Fluxo esperado:

```text
primeira execução
    ↓
autenticação
    ↓
QR / mecanismo suportado
    ↓
sessão persistida

reinicialização futura
    ↓
restaura sessão
    ↓
READY
```

Quando a plataforma exigir nova autenticação:

```text
AUTHENTICATION_REQUIRED
```

deverá ser observado.

---

## 21. O estado da sessão será observável, mas não será gate do sistema

Estados conceituais iniciais:

```text
DISABLED
READY
AUTHENTICATION_REQUIRED
UNAVAILABLE
```

Esses estados servem para diagnóstico, interface operacional, logs estruturados, métricas e ação do operador.

Eles não deverão ser utilizados para bloquear o restante da aplicação.

Exemplo:

```text
AUTHENTICATION_REQUIRED
```

significa:

```text
OpenWA precisa de nova vinculação
```

não:

```text
Rasping Amazon precisa parar
```

---

## 22. Nova autenticação será uma ação operacional eventual

Quando a sessão deixar de ser válida:

```text
OpenWA
    ↓
AUTHENTICATION_REQUIRED
```

o sistema deverá tornar a condição visível ao operador.

O operador poderá realizar nova vinculação quando conveniente.

Após restabelecimento:

```text
OpenWA
    ↓
READY
```

as próximas entregas poderão funcionar normalmente.

Não haverá dependência de reinicialização do Rasping Amazon para que a integração volte a operar, desde que o serviço OpenWA volte a responder normalmente.

---

## 23. QR e dados de autenticação são sensíveis

Conteúdo utilizado para pareamento não deverá ser gravado em logs comuns.

Não deverá existir comportamento equivalente a:

```text
logger.info(qrCode)
```

Caso o projeto exponha QR na interface operacional, ele deverá aparecer somente em contexto local/controlado.

A Easy API não deverá ser publicada diretamente na internet sem necessidade explícita e proteção adequada.

---

## 24. Health check não fará parte do caminho crítico global

A integração poderá consultar o estado do OpenWA para diagnóstico, observabilidade e identificação de `AUTHENTICATION_REQUIRED`.

Entretanto, não será criado health check agressivo como pré-condição para a running global.

O comportamento mínimo poderá ser:

```text
na hora da publicação
    ↓
tenta entregar
    ↓
interpreta resultado
```

Uma consulta de saúde poderá existir para melhorar diagnóstico, mas não deverá criar dependência operacional central.

---

## 25. Implantação futura preferirá isolamento por processo/container

Arquitetura futura possível:

```text
máquina / Raspberry Pi
│
├── rasping-amazon
├── postgres
└── openwa
      ↓
   browser/runtime
```

Se o OpenWA estiver em container:

```text
rasping-amazon
    ↓ rede interna
openwa
```

A porta da Easy API deverá preferencialmente permanecer restrita à rede necessária.

A queda do container OpenWA não deverá encerrar os demais componentes.

---

## 26. Persistência de sessão deverá sobreviver à recriação do serviço

Quando houver containerização, dados de sessão não deverão depender apenas do filesystem efêmero do container.

Deverá existir armazenamento persistente apropriado.

Fluxo desejado:

```text
container OpenWA removido
    ↓
dados de sessão permanecem
    ↓
novo container
    ↓
restauração possível
```

A implementação concreta dependerá da versão do OpenWA adotada e deverá seguir sua documentação vigente.

---

## 27. Raspberry Pi é alvo possível, não requisito da primeira implementação

A implementação deverá evitar acoplamento ao Windows.

Ao mesmo tempo, a primeira prova poderá ser realizada em computador pessoal.

Arquitetura:

```text
mesmo contrato Java
    ↓
OpenWA HTTP
```

deverá funcionar independentemente do host compatível.

A viabilidade de recursos no Raspberry deverá ser medida na implantação real.

O custo relevante será principalmente do runtime externo:

```text
Node.js
browser
sessão WhatsApp Web
```

e não do adapter Java em si.

---

## 28. Não será criado segundo mecanismo de browser dentro do Java

O OpenWA será tratado como serviço externo.

Não será introduzido no núcleo Java:

```text
Selenium para WhatsApp
Playwright para WhatsApp
controle direto do DOM do WhatsApp
```

A responsabilidade de interação com WhatsApp Web permanecerá no OpenWA.

---

## 29. A API oficial da Meta continuará preservada

A implementação atual do canal oficial não será removida.

Arquitetura:

```text
PublicationChannel
    │
    ├── TelegramChannel
    ├── WhatsAppChannel
    ├── WhatsAppManualStagingChannel
    └── OpenWaPublicationChannel
```

O OpenWA é alternativa temporária/opcional.

Se no futuro a integração oficial tornar-se operacionalmente adequada, o OpenWA poderá ser desabilitado sem reescrever o núcleo de publicação.

---

## 30. `WHATSAPP_MANUAL` continuará sendo mecanismo simples e independente

O staging manual não será depreciado pela introdução do OpenWA.

Ele continuará sendo útil porque:

```text
não depende da sessão OpenWA
não depende da API oficial Meta
permite intervenção humana final
é simples
já está integrado ao fanout
```

A coexistência é deliberada:

```text
automático conveniente
+
manual sempre disponível
```

---

## 31. Configuração inicial conceitual

Os nomes definitivos deverão ser alinhados ao padrão existente durante a implementação.

Configuração conceitual:

```text
OPENWA_ENABLED=true
OPENWA_BASE_URI=http://openwa:8080
OPENWA_API_KEY=<secret>
OPENWA_SESSION_ID=rasping-amazon
OPENWA_DESTINATION_TYPE=CONTACT
OPENWA_DESTINATION=<chat-id>
OPENWA_REQUEST_TIMEOUT=<duration>
OPENWA_VERSION=<versão validada>
```

Para grupo:

```text
OPENWA_DESTINATION_TYPE=GROUP
OPENWA_DESTINATION=<group-chat-id>
```

A chave da Easy API, quando utilizada, é segredo.

---

## 32. Auditoria

Uma tentativa OpenWA deverá possuir evidência semelhante às demais entregas.

Quando aplicável:

```text
publication
publicationOutbox
channel = WHATSAPP_OPENWA
destination
attemptNumber
status
providerReference
errorCode
startedAt
finishedAt
```

A ausência de retry não elimina a necessidade de auditoria.

---

## 33. Códigos de falha esperados

A implementação poderá utilizar códigos estáveis equivalentes a:

```text
OPENWA_UNAVAILABLE
OPENWA_AUTHENTICATION_REQUIRED
OPENWA_DESTINATION_INVALID
OPENWA_REQUEST_REJECTED
OPENWA_TIMEOUT
OPENWA_INVALID_RESPONSE
OPENWA_DELIVERY_UNKNOWN
```

Os nomes definitivos deverão ser definidos de acordo com os contratos consolidados pela FASE 20.

---

## 34. Semântica de falha

A classificação operacional deverá reutilizar as categorias da FASE 20 quando possível.

Exemplos conceituais:

```text
OpenWA não responde
    → EXTERNAL / NETWORK ou CHANNEL

sessão exige nova autenticação
    → EXTERNAL / AUTHENTICATION

destino inválido
    → EXTERNAL / CHANNEL

resposta ambígua após possível envio
    → DELIVERY_UNKNOWN
```

Independentemente da categoria:

```text
WHATSAPP_OPENWA
    → sem retry automático
```

---

## 35. Observabilidade

A interface operacional deverá, quando houver suporte suficiente, permitir distinguir pelo menos:

```text
OpenWA habilitado?
estado da sessão
última tentativa
último sucesso
última falha
código da última falha
```

Não é requisito da primeira implementação criar dashboard sofisticado.

A observabilidade deverá reutilizar os mecanismos existentes.

---

## 36. Segurança

O OpenWA deverá ser tratado como integração externa sensível.

Regras mínimas:

```text
não versionar API key
não logar QR
não logar dados de sessão
não expor Easy API publicamente sem necessidade
restringir rede quando possível
fixar versão validada
proteger volume de sessão
```

O canal OpenWA não deve aumentar a superfície pública do PostgreSQL.

---

# Alternativas consideradas

## 37. Manter apenas a API oficial da Meta

### Vantagens

```text
integração oficialmente suportada
contrato estruturado
menor dependência de WhatsApp Web
```

### Motivo para não ser a única opção atual

O fluxo operacional possui restrições de provider, incluindo dependência de templates em cenários aplicáveis, que reduzem o controle desejado pelo projeto.

A integração oficial será preservada, mas não será a única estratégia disponível.

---

## 38. Usar Selenium diretamente no Rasping Amazon

### Não adotada

Isso introduziria WebDriver, seletores DOM, perfil de browser e manutenção específica do WhatsApp dentro da infraestrutura Java.

O OpenWA encapsula essa responsabilidade e pode expor uma fronteira HTTP.

---

## 39. Usar Playwright diretamente no Rasping Amazon para WhatsApp

### Não adotada como primeira opção

Não existe benefício suficiente em tornar o Java responsável também pela automação de WhatsApp Web quando o OpenWA pode encapsular essa função.

Essa decisão não impede reavaliação futura caso OpenWA deixe de ser adequado.

---

## 40. Substituir `WHATSAPP_MANUAL` pelo OpenWA

### Não adotada

O canal manual possui valor operacional independente.

Preservá-lo reduz risco operacional com baixo custo.

---

## 41. Acionar `WHATSAPP_MANUAL` somente quando OpenWA falhar

### Não adotada

Isso criaria acoplamento desnecessário entre dois canais que podem operar independentemente.

O staging manual continuará em paralelo.

---

## 42. Retry automático do OpenWA

### Não adotada

A disponibilidade do `WHATSAPP_MANUAL` torna desnecessário investir complexidade em recuperação automática específica do OpenWA.

Além disso:

```text
resultado externo pode ser desconhecido
sessão pode exigir intervenção
reenvio pode duplicar mensagem
```

A política inicial deliberadamente aceita:

```text
uma tentativa
+
falha visível
+
manual paralelo
```

---

## 43. Replay automático após reconexão

### Não adotado

Depois de reautenticar o OpenWA, somente novas publicações precisam voltar a ser entregues normalmente.

Publicações perdidas continuarão disponíveis no fluxo manual já existente.

Isso reduz duplicações, fila acumulada, reenvio de oferta possivelmente expirada e complexidade de reconciliação.

---

## 44. OpenWA como dependência obrigatória da aplicação

### Não adotada

O Rasping Amazon deverá iniciar e operar mesmo se o OpenWA estiver:

```text
desabilitado
fora do ar
desautenticado
não instalado
```

quando a configuração geral permitir o uso dos outros canais.

---

# Consequências

## 45. Consequências positivas

- mantém a API oficial preservada;
- mantém `WHATSAPP_MANUAL` sempre disponível;
- adiciona uma alternativa automática sem alterar o núcleo;
- aproveita `PublicationChannel`;
- aproveita outbox e auditoria existentes;
- mantém fanout sem duplicar quota comercial;
- falha do OpenWA não bloqueia a running;
- ausência de retry reduz complexidade;
- reconexão por QR pode ocorrer quando conveniente;
- permite contato e grupo controlados;
- evita automação específica de WhatsApp dentro do Java;
- permite execução futura em Raspberry Pi;
- permite remover OpenWA no futuro sem reescrever a aplicação.

---

## 46. Custos e riscos

A solução adiciona:

```text
runtime OpenWA
Node.js
browser/runtime Web
sessão persistente
configuração adicional
serviço/processo/container adicional
```

Também existe dependência de uma integração não oficial com WhatsApp Web.

Consequentemente, mudanças do WhatsApp Web, do OpenWA, expiração de sessão, necessidade de nova autenticação e restrições da plataforma podem afetar o canal.

Esse risco é aceito porque:

```text
WHATSAPP_OPENWA não é crítico
WHATSAPP_MANUAL permanece disponível
demais canais continuam independentes
```

---

## 47. Relação com a FASE 20

A implementação deverá ocorrer somente a partir do estado final efetivamente consolidado da FASE 20.

Esta ADR não presume que o estado remoto do GitHub represente todo o trabalho local ainda não publicado durante sua elaboração.

No início da fase de implementação deverão ser revisados:

```text
resultado final da FASE 20
ADR-0015
contratos de failure/recovery
estado final da publication outbox
estado final de PublicationAttempt
composition root
mecanismo de fanout
configuração de canais
```

A implementação deverá adaptar-se ao modelo final existente, e não criar uma segunda abstração concorrente.

---

## 48. Fase proposta de implementação

A evolução poderá ser registrada como:

```text
FASE 20.5 — OpenWA como canal opcional de WhatsApp Web
```

Essa numeração preserva:

```text
FASE 20
    → resiliência geral

FASE 20.5
    → integração adicional surgida após a FASE 19

FASE 21
    → segurança, governança e fechamento da v1.0
```

A numeração poderá ser ajustada pelo roadmap se houver outra decisão documental antes da implementação.

---

## 49. Etapas sugeridas da FASE 20.5

### 20.5-A — Prova técnica isolada

Validar:

```text
subida do OpenWA
autenticação
sessão persistente
Easy API
envio para contato controlado
envio para grupo controlado
detecção de desconexão
nova autenticação
```

Nenhum código de produção Java deverá ser alterado antes de confirmar o contrato real que será utilizado.

### 20.5-B — Contrato e configuração Java

Introduzir somente os conceitos necessários, possivelmente equivalentes a:

```text
OpenWaChannelConfig
OpenWaChannelConfigProvider
OpenWaDestination
OpenWaClient
OpenWaSessionStatus
```

Os nomes definitivos dependerão do estado final da FASE 20.

### 20.5-C — Adapter

Implementar conceito equivalente a:

```text
OpenWaPublicationChannel
```

mantendo o contrato:

```text
PublicationChannel
```

### 20.5-D — Fanout e composição

Registrar:

```text
WHATSAPP_OPENWA
```

sem remover:

```text
WHATSAPP
WHATSAPP_MANUAL
```

Garantir que o fanout não consuma quota comercial adicional.

### 20.5-E — Falhas e observabilidade

Validar:

```text
OpenWA desligado
AUTHENTICATION_REQUIRED
destino inválido
timeout
resposta inválida
DELIVERY_UNKNOWN
```

com:

```text
sem retry automático
sem bloqueio da running
sem bloqueio de Telegram
sem bloqueio de WHATSAPP_MANUAL
```

### 20.5-F — Probe real

Executar teste controlado fora da suíte hermética:

```text
enviar para contato secundário
enviar para grupo controlado
desconectar sessão
confirmar falha isolada
confirmar continuidade do sistema
reautenticar
confirmar próximas publicações
```

---

## 50. Critérios de aceitação

A implementação será considerada compatível com esta ADR quando for possível demonstrar que:

```text
1. OpenWA é adapter de PublicationChannel;
2. contato configurado recebe publicação;
3. grupo configurado recebe publicação;
4. WHATSAPP_MANUAL permanece funcionando em paralelo;
5. mesma Publication pode gerar fanout para manual e OpenWA;
6. OpenWA não consome nova quota comercial;
7. cada entrega OpenWA possui no máximo uma tentativa automática;
8. falha OpenWA é persistida e auditável;
9. OpenWA fora do ar não interrompe a running;
10. OpenWA desautenticado não interrompe a running;
11. Telegram continua funcionando durante falha OpenWA;
12. WHATSAPP_MANUAL continua funcionando durante falha OpenWA;
13. nova autenticação pode restabelecer entregas futuras;
14. publicações anteriores não são automaticamente reenviadas após reconexão;
15. resultado externo desconhecido não recebe retry automático;
16. a API oficial Meta permanece preservada;
17. nenhuma regra de domínio passa a conhecer OpenWA;
18. a suíte padrão não depende da disponibilidade real do WhatsApp/OpenWA;
19. a validação real fica isolada em probe explícita.
```

---

## 51. Fora de escopo

Não fazem parte desta decisão inicial:

```text
envio em massa
descoberta automática de contatos
gerenciamento de membros de grupo
criação automática de grupos
múltiplas contas OpenWA
múltiplas sessões simultâneas
recebimento/processamento de mensagens do WhatsApp
chatbot bidirecional
respostas automáticas
sincronização de histórico de conversas
replay automático de falhas
retry automático
fallback automático para WHATSAPP_MANUAL
substituição definitiva da API oficial Meta
remoção do staging manual
exposição pública da Easy API
```

Necessidades futuras deverão ser avaliadas separadamente.

---

## 52. Referências internas

```text
docs/adr/0009-interface-operacional-nao-bloqueante.md
docs/adr/0013-selecao-operacional-outbox-e-entrega-publicacao.md
docs/adr/0015-semantica-resiliencia-recuperacao-falhas-producao.md
docs/phases/FASE_19_RESULTADO.md
resultado consolidado da FASE 20, quando fechado
```

---

## 53. Referências externas verificadas na elaboração

Documentação oficial do projeto OpenWA consultada para confirmar a viabilidade técnica da fronteira proposta:

- OpenWA `wa-automate-nodejs` — README / Easy API:  
  `https://github.com/open-wa/wa-automate-nodejs`
- Easy API quick start:  
  `https://github.com/open-wa/wa-automate-nodejs/blob/master/apps/docs/content/docs/getting-started/quickstart.mdx`
- Session events, QR e estado de autenticação:  
  `https://github.com/open-wa/wa-automate-nodejs/blob/master/apps/docs/content/docs/guides/session-events.mdx`
- Docker / ARM64:  
  `https://github.com/open-wa/wa-automate-nodejs/blob/master/apps/docker/README.md`

As referências externas descrevem capacidades do OpenWA.

As decisões específicas de:

```text
uma tentativa
WHATSAPP_MANUAL sempre paralelo
sem fallback automático
sem replay
falha não bloqueante
```

são decisões arquiteturais próprias do Rasping Amazon registradas nesta ADR.

---

## 54. Decisão resumida

O Rasping Amazon adotará, após a consolidação da FASE 20, a possibilidade de adicionar OpenWA como canal opcional e temporário de WhatsApp Web.

Arquitetura:

```text
                         Publication
                              │
                         fanout derivado
                              │
              ┌───────────────┼─────────────────┐
              ▼               ▼                 ▼
          TELEGRAM      WHATSAPP_MANUAL   WHATSAPP_OPENWA
                              │                 │
                              ▼                 ▼
                      Telegram privado       OpenWA
                                                │
                                                ▼
                                           WhatsApp Web
```

Regra operacional:

```text
OpenWA funciona
    → entrega automática

OpenWA falha
    → registra a tentativa
    → não retry
    → não bloqueia
    → WHATSAPP_MANUAL já está disponível

OpenWA é reconectado
    → próximas publicações voltam a tentar normalmente
```

O canal oficial da Meta permanece preservado.

O OpenWA não será tratado como dependência crítica do funcionamento do sistema.
