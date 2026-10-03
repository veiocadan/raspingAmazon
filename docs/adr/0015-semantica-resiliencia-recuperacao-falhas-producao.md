# ADR-0015 — Resiliência, recuperação e falhas de produção

- **Status:** Aceita
- **Data:** 2026-10-03
- **Projeto:** Rasping Amazon
- **Fases relacionadas:** FASE 12 — Orquestração assíncrona e processamento durável; FASE 16 — Observabilidade, auditoria e operação; FASE 17 — Agendamento e execução contínua; FASE 18 — Seleção operacional, canais e outbox; FASE 19 — Telegram e WhatsApp; FASE 20 — Resiliência, recuperação e falhas de produção
- **Complementa:** ADR-0011, ADR-0012, ADR-0013 e ADR-0014
- **Escopo:** classificação operacional de falhas, retry, recuperação após restart, dead-letter lógico, reprocessamento e resultado externo desconhecido

---

## 1. Contexto

O Rasping Amazon executa trabalho durável distribuído entre diferentes fronteiras operacionais:

```text
Amazon
    ↓
coleta
    ↓
enrichment
    ↓
avaliação
    ↓
seleção
    ↓
geração de Publication
    ↓
publication outbox
    ↓
Telegram / WhatsApp
```

O PostgreSQL permanece como fonte principal de estado durável.

A FASE 20 possui como objetivo garantir que:

```text
falhas externas
reinicializações
falhas de banco
falhas de processamento
falhas de canal
```

não deixem o sistema em estado silenciosamente inconsistente.

O sistema deverá terminar cada execução em estado:

```text
conhecido
rastreável
auditável
recuperável quando aplicável
```

A política de resiliência não poderá depender apenas de estado em memória.

---

## 2. Hierarquia documental

O roadmap da FASE 20 constitui a direção histórica da implementação.

As ADRs aceitas e os resultados efetivamente produzidos pelas fases anteriores constituem refinamentos autorizados dessa direção.

Portanto:

```text
ROADMAP
    ↓
direção histórica

ADR aceita
    ↓
decisão arquitetural vigente

resultado de fase
    ↓
estado efetivamente implementado
```

A FASE 20 não deverá desfazer mecanismos corretos introduzidos antecipadamente pela FASE 19.

Também não deverá tratar esses mecanismos antecipados como evidência de que a resiliência global já esteja concluída.

---

## 3. Trabalho antecipado pela FASE 19

A FASE 19 antecipou componentes que pertencem parcialmente ao escopo da FASE 20.

Entre eles:

```text
PUBLICATION_DISPATCH durável
PublicationDispatchJobService
PublicationDispatchReconciliationService
recovery de leases da publication outbox
retry/backoff persistente da publication outbox
rate limiting persistente
```

Esses componentes serão classificados durante a FASE 20 como:

```text
PRESERVAR
ADAPTAR
COMPLEMENTAR
```

e não serão reimplementados apenas para alterar sua autoria de fase.

---

## 4. Problema da classificação atual

A arquitetura atual possui uma classificação de retry baseada principalmente em:

```text
TRANSIENT
PERMANENT
```

Essa classificação responde à pergunta:

```text
esta falha pode ser repetida automaticamente?
```

Ela não responde, sozinha, às perguntas:

```text
onde ocorreu a falha?
qual foi a causa operacional?
o sistema deve pausar?
o sistema deve alertar?
é necessária intervenção humana?
o resultado externo é conhecido?
```

A FASE 20 não deverá transformar todas essas perguntas em um único enum.

As dimensões permanecerão semanticamente separadas.

---

## 5. Dimensões da falha

Uma falha operacional poderá possuir, de forma independente:

```text
origem
categoria
semântica de retry
código estável
mensagem segura
decisão operacional
```

### 5.1 Origem

A origem identifica a fronteira geral da falha.

Conceitualmente:

```text
INTERNAL
EXTERNAL
```

Exemplos:

```text
timeout Amazon
    → EXTERNAL

erro de configuração local
    → INTERNAL

falha de persistência PostgreSQL
    → INTERNAL

rejeição Telegram
    → EXTERNAL
```

A origem não determina automaticamente se a falha é transitória ou permanente.

---

## 6. Semântica de retry

A classificação já existente:

```text
TRANSIENT
PERMANENT
```

será preservada como semântica de retry.

### TRANSIENT

Significa:

```text
uma nova tentativa poderá produzir resultado válido
sem alteração manual obrigatória da entrada
```

Isso não significa retry infinito.

O número máximo de tentativas continuará limitado pela política operacional aplicável.

### PERMANENT

Significa:

```text
repetir automaticamente o mesmo trabalho
não possui expectativa razoável de corrigir a causa
```

Falha permanente permanece visível e auditável.

Permanente não significa apagada.

---

## 7. Categorias operacionais

A FASE 20 introduzirá categorias operacionais independentes da semântica de retry.

O conjunto inicial será:

```text
NETWORK
SOURCE_RESTRICTION
SOURCE_CHANGED
AUTHENTICATION
RATE_LIMIT
DATA_UNAVAILABLE
DATABASE
CHANNEL
CONFIGURATION
PROCESSING
UNKNOWN
```

### NETWORK

Falhas de comunicação cuja causa principal pertence ao transporte.

Exemplos:

```text
connect timeout
connection refused
socket timeout
```

### SOURCE_RESTRICTION

A fonte respondeu ou se comportou de forma compatível com restrição de acesso.

Exemplos:

```text
CAPTCHA
challenge
bloqueio explícito
página de proteção
```

Essa categoria não autoriza contorno da proteção.

### SOURCE_CHANGED

A estrutura observada da fonte tornou-se incompatível com o contrato conhecido.

Exemplos:

```text
layout incompatível
seletor estrutural crítico desaparecido sistematicamente
resposta que não corresponde mais ao contrato esperado
```

A política deverá continuar fail closed.

Nenhum dado será inventado para manter artificialmente o pipeline funcionando.

### AUTHENTICATION

Credenciais, tokens ou autorização foram recusados.

Exemplos:

```text
token Telegram inválido
token WhatsApp inválido
credencial expirada
HTTP 401
HTTP 403 quando semanticamente associado a autenticação
```

A repetição automática sem mudança da credencial normalmente não resolve essa categoria.

### RATE_LIMIT

A integração recusou ou postergou a operação por limite de frequência ou capacidade.

Exemplo:

```text
HTTP 429
```

Rate limit é diferente de falha de rede.

A política deverá preferir reagendamento durável a espera bloqueante.

### DATA_UNAVAILABLE

A fonte foi adquirida, mas evidência necessária para continuar não está disponível de forma confiável.

Exemplos:

```text
produto removido
oferta indisponível
campo crítico legitimamente ausente
```

Essa categoria não será utilizada para esconder `SOURCE_CHANGED`.

Ausência pontual de um dado e mudança estrutural da fonte permanecem conceitos diferentes.

### DATABASE

Falha cuja causa operacional pertence ao PostgreSQL ou à persistência.

Exemplos:

```text
conexão perdida
deadlock
serialization failure
falha transitória
constraint violada
schema incompatível
```

A classificação de retry deverá considerar SQLState e contexto, não apenas a classe Java concreta da exceção.

### CHANNEL

Falha funcional ou operacional pertencente à entrega por canal.

Exemplos:

```text
destino inexistente
mensagem rejeitada
resposta inválida
provider indisponível
```

### CONFIGURATION

Configuração obrigatória ausente, inválida ou incoerente.

Exemplos:

```text
variável obrigatória ausente
intervalo inválido
canal habilitado sem credenciais
```

### PROCESSING

Falha interna do processamento que não pertence a uma fronteira externa específica.

Exemplos:

```text
invariante interna quebrada
estado persistido incompatível
entrada inválida
```

### UNKNOWN

Fallback conservador para falha ainda não classificada.

`UNKNOWN` não autoriza retry automático por padrão.

Uma falha desconhecida deverá permanecer visível até possuir classificação explícita.

---

## 8. Decisão operacional

A classificação deverá permitir que a política operacional determine uma ou mais ações.

Ações previstas pelo roadmap:

```text
retry
rejeitar
pausar
alertar
reprocessar
intervenção humana
```

Essas ações não serão forçadas a uma única dimensão.

Por exemplo:

```text
AUTHENTICATION
    → não retry
    → pausar integração
    → alertar
    → intervenção humana
```

Outro exemplo:

```text
RATE_LIMIT
    → retry
    → reagendar
    → não bloquear thread
```

Outro exemplo:

```text
SOURCE_CHANGED
    → não inventar dado
    → encerrar trabalho afetado
    → alertar
    → intervenção humana
```

---

## 9. Fail closed

O comportamento fail closed permanece obrigatório.

Quando não houver evidência suficiente para continuar com segurança:

```text
não inferir
não inventar
não publicar parcialmente
não transformar ausência em dado confirmado
```

Falhas de aquisição ou parsing não deverão produzir fatos comerciais artificiais.

A decisão é compatível com as ADRs anteriores de aquisição e evidência.

---

## 10. Retry possui limite

Nenhum retry será infinito.

Toda política automática deverá possuir:

```text
limite de tentativas
backoff quando aplicável
estado terminal quando o limite for excedido
```

Ao exceder sua política:

```text
o trabalho permanece persistido
a causa permanece auditável
o histórico permanece disponível
```

O trabalho poderá posteriormente ser elegível para reprocessamento explícito.

---

## 11. Dead-letter lógico

A versão 1.0 não exige fila externa dedicada para dead-letter.

O projeto utilizará dead-letter lógico por meio dos estados duráveis já existentes ou por estados adicionais introduzidos explicitamente quando necessários.

Exemplos atuais:

```text
ProcessingJob.DEAD
publication_outbox.FAILED_PERMANENT
publication_outbox.FAILED_TRANSIENT terminal
```

A mera existência de um estado terminal não autoriza reprocessamento cego.

O reprocessamento será um caso de uso explícito.

---

## 12. Reprocessamento

Reprocessamento é diferente de retry automático.

### Retry automático

Ocorre como continuação da mesma política de execução:

```text
mesma unidade durável
mesma identidade lógica
nova tentativa automática
```

### Reprocessamento

Ocorre por decisão operacional posterior.

Deverá:

```text
validar estado atual
preservar histórico anterior
registrar a nova decisão
preservar idempotência
não apagar evidência das tentativas anteriores
```

No caso de publicação, reprocessamento não deverá automaticamente:

```text
criar nova quota
gerar nova Publication
refazer seleção
reenviar mensagem com resultado externo desconhecido
```

sem política explícita para isso.

---

## 13. Reinicialização

O sistema deverá conseguir reiniciar sem depender do conteúdo da memória do processo anterior.

Após restart, deverá ser possível localizar trabalho persistido relacionado a:

```text
processing schedule
ProcessingRun
ProcessingJob
PUBLICATION_DISPATCH
publication_outbox
```

PostgreSQL permanece a autoridade sobre o trabalho pendente.

---

## 14. Recuperação de ProcessingJob

Jobs duráveis que possuam lease expirado poderão ser recuperados de acordo com sua política existente, desde que a unidade de trabalho não atravesse uma fronteira externa cuja conclusão tenha se tornado indeterminada.

Para operações puramente internas e idempotentes, a retomada pode retornar o trabalho à execução normal.

A política deverá continuar respeitando:

```text
attemptCount
maxAttempts
backoff
estado DEAD
idempotencyKey
```

---

## 15. Recuperação de PUBLICATION_DISPATCH

O trabalho antecipado da FASE 19 será preservado.

A unidade:

```text
PUBLICATION_DISPATCH
```

continua pertencendo à `ProcessingRun`.

O reconciliador continuará responsável por garantir idempotentemente a existência do trabalho durável.

O handler continuará revalidando readiness antes da execução.

Não será criada uma segunda fila para a mesma responsabilidade.

---

## 16. Recuperação da publication outbox exige regra especial

A publicação externa possui uma característica diferente das demais etapas.

Existe uma janela operacional:

```text
outbox = PROCESSING
        ↓
provider é chamado
        ↓
provider aceita mensagem
        ↓
processo cai
        ↓
resultado ainda não foi persistido
```

Nesse caso:

```text
estado local
    → não sabe se a entrega ocorreu

estado externo
    → mensagem pode já ter sido entregue
```

Retornar automaticamente essa outbox para:

```text
PENDING
```

e repetir o envio pode produzir duplicação externa.

Portanto, a recuperação de lease de publicação não poderá tratar toda entrada `PROCESSING` expirada como retry automaticamente seguro.

O mecanismo antecipado pela FASE 19 deverá ser adaptado na FASE 20.

---

## 17. Resultado externo desconhecido

A FASE 20 introduz explicitamente o conceito:

```text
DELIVERY_UNKNOWN
```

`DELIVERY_UNKNOWN` significa:

```text
a fronteira externa pode ter sido atravessada
mas o sistema não possui confirmação confiável
de sucesso nem de rejeição
```

Isso é diferente de:

```text
FAILED_TRANSIENT
```

e diferente de:

```text
FAILED_PERMANENT
```

Uma falha é transitória quando uma nova tentativa é considerada operacionalmente segura e potencialmente útil.

Um resultado é desconhecido quando repetir a operação pode duplicar um efeito externo.

---

## 18. DELIVERY_UNKNOWN não recebe retry automático por padrão

A política padrão será:

```text
DELIVERY_UNKNOWN
    ↓
não reenviar automaticamente
    ↓
persistir estado
    ↓
alertar / disponibilizar para reconciliação
```

Retry somente poderá ocorrer automaticamente se existir garantia suficiente de segurança, por exemplo:

```text
chave de idempotência aceita pelo provider
consulta de reconciliação autoritativa
prova de que a requisição não atravessou a fronteira externa
```

Na ausência dessa garantia:

```text
duplicação externa
>
conveniência do retry
```

O sistema deverá preferir intervenção ou reconciliação explícita.

---

## 19. Início da tentativa externa deverá tornar-se persistente

O modelo atual registra `PublicationAttempt` durante a conclusão da chamada externa.

A FASE 20 deverá evoluir essa fronteira para permitir distinguir:

```text
nenhuma chamada externa iniciada
chamada externa iniciada
chamada concluída com resultado conhecido
chamada cujo resultado ficou desconhecido
```

A persistência do início deverá ocorrer antes da chamada ao provider.

Fluxo alvo:

```text
claim outbox
    ↓
rate-limit admission
    ↓
persistir início da tentativa
    ↓
commit
    ↓
chamar provider
    ↓
persistir resultado
```

A transação de banco não deverá permanecer aberta durante a chamada HTTP.

Se houver queda depois do registro do início e antes da conclusão:

```text
tentativa incompleta persistida
    ↓
resultado desconhecido
```

Isso é deliberadamente conservador.

É preferível exigir reconciliação de uma tentativa que talvez nem tenha sido enviada do que reenviar automaticamente uma mensagem que pode já ter sido publicada.

---

## 20. Estados de publicação deverão representar incerteza

O schema atual da `publication_outbox` não possui estado capaz de representar resultado externo desconhecido.

A FASE 20 deverá introduzir, por nova migration, um estado operacional equivalente a:

```text
DELIVERY_UNKNOWN
```

O nome persistido definitivo deverá permanecer coerente entre:

```text
application
JDBC
schema
observabilidade
testes
```

Esse estado será:

```text
terminal para retry automático
não terminal para operação humana
visível
auditável
reconciliável
```

Migrations já aplicadas não serão alteradas retroativamente.

---

## 21. Tentativa de publicação

`PublicationAttempt` representa evidência histórica e não será sobrescrita.

A evolução da FASE 20 deverá permitir estados suficientes para representar o lifecycle real da tentativa.

Conceitualmente:

```text
STARTED
SUCCESS
FAILED_TRANSIENT
FAILED_PERMANENT
DELIVERY_UNKNOWN
```

Uma tentativa `STARTED` abandonada por perda do processo deverá poder ser reconciliada para `DELIVERY_UNKNOWN`.

Nenhuma tentativa anterior será apagada quando houver reprocessamento.

---

## 22. Falhas seguras para retry em canais

Exemplos de falhas que poderão continuar sendo tratadas como retryable quando o adapter puder determinar que o efeito externo não se tornou ambíguo:

```text
rate limit explícito antes de aceitação
provider temporariamente indisponível com resposta conhecida
falha de conexão anterior ao envio
```

A decisão final depende da informação disponível na fronteira de transporte.

---

## 23. Timeouts de canal exigem cuidado

Timeout não será classificado automaticamente como simples retry seguro.

É necessário distinguir, quando possível:

```text
falha antes da conexão/envio
```

de:

```text
timeout depois que a requisição pode ter sido enviada
```

No segundo caso:

```text
DELIVERY_UNKNOWN
```

deverá ser considerado.

A infraestrutura HTTP poderá evoluir para preservar essa informação sem expor detalhes específicos de biblioteca às camadas superiores.

---

## 24. Resposta externa inválida ou incompleta

Quando o provider responde com sucesso HTTP, mas a resposta não permite confirmar a identidade da entrega:

```text
HTTP 2xx
+
resposta inválida ou incompleta
```

o sistema não deverá automaticamente assumir que a mensagem não foi enviada.

Se o provider puder ter aplicado o efeito:

```text
DELIVERY_UNKNOWN
```

é a classificação conservadora.

---

## 25. Rate limiting

O rate limiter persistente antecipado pela FASE 19 será preservado.

A identidade da limitação continuará representando a integração física.

Exemplo:

```text
TELEGRAM
WHATSAPP_MANUAL
    ↓
TELEGRAM_BOT_API
```

A decisão de admissão continuará persistente e concorrente.

Não será utilizado:

```text
Thread.sleep
```

como mecanismo de rate limiting dentro da execução da publicação.

Quando o slot não estiver disponível:

```text
outbox
    ↓
PENDING
    ↓
availableAt futuro
```

sem chamada ao provider.

---

## 26. Rate limit observado no provider

A FASE 20 também deverá tratar rate limiting devolvido pela integração externa.

Quando houver:

```text
HTTP 429
Retry-After
ou sinal equivalente do provider
```

a política poderá atualizar o próximo instante seguro da integração e reagendar a unidade durável.

Headers ou metadados necessários à decisão deverão ser preservados pela infraestrutura HTTP quando aplicável.

---

## 27. Amazon

A política da fonte Amazon deverá distinguir explicitamente:

```text
timeout
erro de conexão
erro HTTP
rate limit
source restriction
CAPTCHA / challenge
layout alterado
campo crítico ausente
produto removido
dado indisponível
```

Não haverá mecanismo para burlar challenge ou proteção da fonte.

---

## 28. Mudança de layout

Mudança de layout não poderá gerar dados falsos silenciosamente.

Quando evidência estrutural esperada desaparecer de forma incompatível com o contrato conhecido:

```text
SOURCE_CHANGED
```

deverá ser considerada.

A política deverá permitir interrupção controlada, observabilidade e investigação.

---

## 29. Ausência de evidência

Ausência legítima de um dado não é necessariamente mudança de layout.

Exemplo:

```text
um único produto sem seller observável
```

pode representar:

```text
DATA_UNAVAILABLE
```

Enquanto:

```text
seller desaparece sistematicamente
em um conjunto amplo de páginas
após alteração estrutural
```

pode representar:

```text
SOURCE_CHANGED
```

A distinção será feita com evidência observada e não por inferência arbitrária.

---

## 30. PostgreSQL

Falhas de banco serão classificadas considerando:

```text
tipo da exceção
SQLState
operação em execução
possibilidade de commit parcial
```

Não serão consideradas transitórias apenas porque pertencem genericamente a `SQLException`.

Também não serão consideradas permanentes apenas porque a implementação concreta não utiliza uma subclasse `SQLTransientException`.

---

## 31. Transações e efeitos externos

Nenhuma chamada HTTP externa será realizada enquanto uma transação JDBC permanecer deliberadamente aberta aguardando a resposta externa.

Padrão:

```text
persistir intenção
    ↓
commit
    ↓
efeito externo
    ↓
persistir resultado
```

Isso reduz tempo de lock e permite representar explicitamente falhas entre intenção e conclusão.

---

## 32. Observabilidade

Toda falha operacional relevante deverá produzir informação suficiente para correlação.

Sempre que aplicável:

```text
processingRunId
processingJobId
dealCandidateId
offerSnapshotId
selectionRunId
publicationId
publicationOutboxId
publicationAttemptId
channel
destination lógico seguro
failure origin
failure category
retry classification
error code
```

Segredos não serão registrados.

Exemplos proibidos:

```text
bot token
access token
Authorization header
credenciais PostgreSQL
cookies sensíveis
```

---

## 33. Alertas

A FASE 20 definirá condições de alerta, mas não introduzirá infraestrutura externa complexa sem necessidade.

São candidatas naturais a alerta:

```text
AUTHENTICATION
SOURCE_CHANGED
CONFIGURATION
DELIVERY_UNKNOWN
falha DATABASE persistente
retries esgotados
```

A geração de alerta não altera a classificação da falha.

---

## 34. Circuit breaker

Circuit breaker não será implementado apenas por padrão arquitetural.

Primeiro serão utilizadas:

```text
retry limitado
backoff
rate limiting
persistência
recovery
classificação de falhas
observabilidade
```

Circuit breaker somente será adicionado se testes ou operação real demonstrarem necessidade concreta que não seja atendida pelos mecanismos anteriores.

A decisão de não implementar circuit breaker também deverá ser registrada no resultado da FASE 20 caso nenhuma necessidade seja demonstrada.

---

## 35. Testes de falha controlada

A FASE 20 deverá incluir testes capazes de provocar de forma determinística:

```text
timeout Amazon
falha de conexão Amazon
HTTP 429 Amazon
HTTP 5xx Amazon
source restriction
layout incompatível
falha PostgreSQL transitória
falha PostgreSQL permanente
crash com ProcessingJob em andamento
crash com publication_outbox em PROCESSING
crash após início de tentativa externa
rate limit de canal
token inválido
destino inválido
resposta externa inválida
resultado DELIVERY_UNKNOWN
retries esgotados
```

Cada teste deverá verificar, conforme aplicável:

```text
estado persistido
classificação
código de erro
tentativas
reagendamento
terminalidade
reprocessabilidade
ausência de duplicação automática
```

---

## 36. Matriz inicial de comportamento

A política inicial esperada é:

```text
falha de conexão antes do envio
    → NETWORK
    → TRANSIENT
    → retry limitado

rate limit conhecido
    → RATE_LIMIT
    → TRANSIENT
    → reagendar

CAPTCHA / challenge
    → SOURCE_RESTRICTION
    → PERMANENT para a tentativa automática atual
    → alertar / intervenção

layout incompatível
    → SOURCE_CHANGED
    → PERMANENT para o trabalho atual
    → alertar

token inválido
    → AUTHENTICATION
    → PERMANENT
    → pausar / alertar

configuração inválida
    → CONFIGURATION
    → PERMANENT
    → intervenção

SQL serialization/deadlock recuperável
    → DATABASE
    → TRANSIENT
    → retry limitado

constraint/invariante persistente
    → DATABASE ou PROCESSING
    → PERMANENT
    → alertar

timeout depois de possível envio ao canal
    → CHANNEL
    → DELIVERY_UNKNOWN
    → sem retry automático

queda depois do envio e antes do ACK persistido
    → DELIVERY_UNKNOWN
    → sem retry automático
```

Essa matriz será refinada por testes concretos durante a implementação.

---

## 37. Compatibilidade com trabalho já implementado

A FASE 20 deverá preservar, salvo defeito demonstrado:

```text
ProcessingJob durable queue
processing leases
processing retry/backoff
PUBLICATION_DISPATCH
dispatch reconciliation
publication outbox
publication retry/backoff
publication rate limiting
publication selection
quota persistente
PublicationAttempt
```

Quando uma adaptação for necessária, deverá ser feita sobre esses contratos existentes.

Não será criada infraestrutura paralela com a mesma responsabilidade.

---

## 38. Migrations

O schema atual encontra-se em V34.

Migrations existentes não serão editadas.

Qualquer mudança de schema da FASE 20 começará em:

```text
V35
```

e deverá ser aditiva/evolutiva.

Mudanças previstas poderão incluir suporte persistente para:

```text
tentativa iniciada
resultado externo desconhecido
estado de reconciliação
metadados operacionais necessários
```

somente quando a implementação correspondente for iniciada.

---

## 39. Ordem de implementação

A implementação da FASE 20 seguirá esta ordem:

```text
20-A
contrato formal e auditoria

20-B
taxonomia e classificação operacional

20-C
falhas da fonte Amazon

20-D
falhas de canais e DELIVERY_UNKNOWN

20-E
recuperação após restart

20-F
dead-letter lógico e reprocessamento

20-G
fechamento de rate limit

20-H
testes destrutivos controlados e gate final
```

Cada etapa deverá terminar com testes verdes antes da próxima.

---

## 40. Critério de conclusão

A FASE 20 será considerada concluída quando for possível provocar falhas controladas em:

```text
fonte Amazon
PostgreSQL
processamento
canal
```

e demonstrar que cada uma termina em estado:

```text
conhecido
persistido quando necessário
rastreável
auditável
recuperável ou explicitamente terminal
```

Também deverá ser demonstrado que:

```text
restart não perde trabalho durável
retry não é infinito
falhas permanentes não entram em loop
resultado externo desconhecido não produz retry cego
trabalho terminal continua visível
reprocessamento preserva histórico
```

---

## 41. Consequências positivas

A decisão fornece:

```text
taxonomia operacional explícita
separação entre causa e retry
recovery após restart
redução do risco de publicação duplicada
dead-letter lógico sem infraestrutura adicional
reprocessamento auditável
base para alertas
```

---

## 42. Custos

A solução aumenta:

```text
número de estados operacionais
complexidade da publication outbox
quantidade de testes de falha
necessidade de migrations adicionais
```

Esse custo é aceito porque representa estados reais que já podem ocorrer em produção.

Ocultar esses estados atrás de um simples retry produziria uma arquitetura menos segura.

---

## 43. Decisões explicitamente rejeitadas

Não serão adotados nesta fase:

```text
retry infinito
sleep bloqueante para rate limit
edição retroativa de migrations aplicadas
fila externa obrigatória apenas para dead-letter
circuit breaker sem necessidade demonstrada
invenção de dados quando a Amazon não fornece evidência
retry automático de resultado externo desconhecido
duplicação de mecanismos já implementados na FASE 19
```

---

## 44. Decisão final

A resiliência do Rasping Amazon será baseada em:

```text
estado durável no PostgreSQL
+
classificação explícita de falhas
+
retry limitado
+
backoff
+
rate limiting persistente
+
recovery controlado
+
resultado externo desconhecido como estado de primeira classe
+
reprocessamento explícito
+
observabilidade
```

O sistema deverá preferir:

```text
estado conhecido e conservador
```

a:

```text
continuação automática potencialmente incorreta
```

principalmente quando uma operação externa puder ter produzido efeito irreversível ou duplicável.
