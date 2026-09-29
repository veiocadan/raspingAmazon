# ADR-0013 — Seleção operacional, outbox e entrega de publicação

- **Status:** Aceita
- **Data:** 2026-09-27
- **Projeto:** Rasping Amazon
- **Fase principal:** FASE 18 — Contrato de canais e outbox de publicação
- **Relacionada a:** ADR-0010, ADR-0012
- **Depende de:** FASE 13 — Geração de publicação; FASE 17 — Agendamento e execução contínua
- **Próximas fronteiras:** FASE 19 — Telegram e WhatsApp; FASE 20 — Resiliência e recuperação

---

## Contexto

Ao final da FASE 17, o Rasping Amazon já possui:

```text
coleta recorrente
ProcessingSchedule
ProcessingRun
ProcessingJob
workers
leases PostgreSQL
idempotência
retry do pipeline interno
estado operacional persistido
execução contínua
```

A geração de publicação também já está separada da entrega externa.

O roadmap da versão 1.0 define para a FASE 18:

```text
Publication READY
        ↓
Outbox
        ↓
worker
        ↓
PublicationChannel
```

e determina que a aplicação seja preparada para publicação externa sem dependência concreta de Telegram ou WhatsApp.

Entretanto, a ADR-0010 estabelece uma responsabilidade adicional imediatamente anterior à entrega:

```text
score
    ↓
ranking
    ↓
histórico de publicação
    ↓
PublicationSelectionPolicy
    ↓
trabalho autorizado para publicação
```

Isso é necessário porque:

```text
qualidade comercial
        ≠
prioridade operacional de publicação
```

Uma oferta de score elevado não deve necessariamente ser publicada repetidamente em todos os ciclos.

A FASE 18 precisa, portanto, consolidar três responsabilidades diferentes:

```text
seleção operacional
outbox durável
entrega por canal
```

sem misturá-las.

---

## Drivers arquiteturais

A decisão prioriza:

1. preservação da semântica original do score;
2. consideração de histórico real de publicação;
3. redução de repetição;
4. comportamento determinístico;
5. auditabilidade da decisão de seleção;
6. configuração operacional fora do código;
7. idempotência;
8. concorrência segura;
9. PostgreSQL como estado durável;
10. separação entre seleção e entrega;
11. independência de Telegram e WhatsApp;
12. compatibilidade futura com múltiplos canais e destinos;
13. ausência de infraestrutura distribuída sem necessidade comprovada;
14. preservação das fronteiras das FASES 19 e 20.

---

# Decisão

## 1. Score e prioridade operacional permanecerão conceitos independentes

O score continuará representando qualidade comercial.

Conceitualmente:

```text
score
=
quão atrativa é a oferta?
```

A política de seleção responderá outra pergunta:

```text
prioridade de publicação
=
quão apropriado é publicar esta oferta agora?
```

O histórico de publicação não modificará o score.

Não será criado comportamento equivalente a:

```text
scoreFinal =
    scoreComercial
    - penalidadeDeRecorrencia
```

O score histórico deverá continuar sendo interpretável de acordo com sua própria versão.

---

## 2. A seleção operacional acontecerá depois do ranking e antes da geração/entrega

Fluxo alvo:

```text
ofertas elegíveis
        ↓
filtros
        ↓
score
        ↓
ranking
        ↓
histórico de publicação
        ↓
PublicationSelectionPolicy
        ↓
candidatos selecionados
        ↓
PublicationGenerator
        ↓
Publication
        ↓
revisão
        ↓
aprovação
        ↓
outbox
        ↓
worker
        ↓
PublicationChannel
```

O `PublicationGenerator` não receberá regras de:

```text
cooldown
quota
histórico de publicação
cadência
canal
destino
```

A seleção operacional será uma responsabilidade própria.

---

## 3. Será introduzida uma política versionada de seleção

A primeira política possuirá identidade explícita:

```text
PUBLICATION_SELECTION_V1
```

A versão fará parte da decisão auditável.

Mudanças incompatíveis na semântica de:

```text
prioridade
cooldown
quota
desempate
histórico
escopo
```

deverão produzir uma nova versão.

Não será permitido modificar silenciosamente o significado histórico de:

```text
PUBLICATION_SELECTION_V1
```

---

## 4. A política trabalhará com candidatos explícitos

Será introduzido um conceito equivalente a:

```text
PublicationSelectionCandidate
```

O candidato deverá conter somente dados necessários para a decisão operacional.

Entre eles:

```text
dealEvaluationId
asin
score
ranking
lastSuccessfulPublicationAt
successfulPublicationCount
channel
destination
```

A política não acessará diretamente:

```text
HTML Amazon
HTTP
parser
provider Telegram
provider WhatsApp
repository JDBC
```

A obtenção dos dados será responsabilidade de ports e casos de uso externos à política.

---

## 5. Histórico comercial e histórico de publicação permanecerão diferentes

O histórico utilizado por:

```text
momentum
score
ranking
```

continua sendo histórico comercial da oferta.

O histórico utilizado pela seleção será:

```text
histórico de entrega/publicação bem-sucedida
```

Fluxo:

```text
histórico comercial
        ↓
momentum
        ↓
score
        ↓
ranking
        ↓
histórico de publicação
        ↓
PublicationSelectionPolicy
```

A política não recalculará momentum.

A política não reinterpretará snapshots históricos.

---

## 6. Somente uma entrega efetivamente bem-sucedida iniciará recorrência

Os seguintes estados não contam como publicação efetiva:

```text
Publication CREATED
Publication READY
outbox PENDING
outbox PROCESSING
tentativa falha
```

A evidência autoritativa de publicação bem-sucedida será uma tentativa de entrega persistida com resultado:

```text
SUCCESS
```

Conceitualmente:

```text
PublicationAttempt = SUCCESS
```

será utilizada para reconstruir:

```text
lastSuccessfulPublicationAt
successfulPublicationCount
```

A existência de uma `Publication` não será suficiente para iniciar cooldown.

Falhas de canal não serão tratadas como publicação realizada.

---

## 7. O histórico será considerado por canal e destino

A primeira implementação já preservará o escopo:

```text
ASIN
+
channel
+
destination
```

Exemplo:

```text
ASIN B012345678
Telegram / canal-principal
último sucesso = ontem
```

não implica necessariamente:

```text
ASIN B012345678
WhatsApp / grupo-a
último sucesso = ontem
```

A política central não conterá condicionais específicas como:

```text
if Telegram ...
if WhatsApp ...
```

Canal e destino serão apenas dimensões da decisão operacional.

Essa escolha evita que a introdução de múltiplos canais na FASE 19 exija alterar a semântica central da seleção.

---

## 8. A política inicial utilizará cooldown híbrido

A `PUBLICATION_SELECTION_V1` possuirá:

```text
hardCooldown
preferredCooldown
```

O comportamento será:

```text
nunca publicada
→ elegível com prioridade preferencial

publicada dentro do hardCooldown
→ não selecionável neste ciclo

publicada fora do hardCooldown
mas dentro do preferredCooldown
→ elegível com prioridade reduzida

publicada fora do preferredCooldown
→ elegível com prioridade normal
```

Os valores concretos não serão constantes semânticas dentro do código.

Não serão definidos diretamente como:

```java
private static final int HARD_COOLDOWN_DAYS = 2;
private static final int PREFERRED_COOLDOWN_DAYS = 7;
```

A configuração operacional deverá ser fornecida externamente e possuir estado persistido quando aplicável.

---

## 9. Não existirão valores funcionais obrigatórios escondidos no código

Valores como:

```text
hardCooldown
preferredCooldown
maxPublicationsPerDay
```

não serão definidos silenciosamente por constantes internas.

Configuração técnica e regra funcional permanecerão diferentes.

É aceitável possuir defaults técnicos como:

```text
workerPollInterval
leaseDuration
idleDelay
```

desde que não alterem silenciosamente a política comercial de publicação.

---

## 10. A ordenação da `PUBLICATION_SELECTION_V1` será determinística

Após remoção dos candidatos ainda dentro do `hardCooldown`, a prioridade será calculada conceitualmente por:

```text
1. nunca publicados;
2. publicações bem-sucedidas mais antigas;
3. maior score;
4. desempate por identidade estável.
```

Para candidatos nunca publicados:

```text
lastSuccessfulPublicationAt = null
```

e o score será utilizado para ordená-los entre si.

Para candidatos anteriormente publicados:

```text
lastSuccessfulPublicationAt ASC
```

terá precedência sobre:

```text
score DESC
```

Empates restantes serão resolvidos por identidade estável, por exemplo:

```text
ASIN ASC
```

ou outro identificador persistido equivalente definido durante a implementação.

Não será utilizado:

```sql
ORDER BY RANDOM()
```

A mesma entrada, configuração e instante lógico deverão produzir a mesma ordem.

---

## 11. O preferred cooldown não modificará o score

Candidatos dentro do `preferredCooldown`, mas fora do `hardCooldown`, continuarão elegíveis.

Eles serão colocados em faixa operacional inferior.

Conceitualmente:

```text
bucket 0
nunca publicados

bucket 1
publicados fora do preferredCooldown

bucket 2
publicados dentro do preferredCooldown,
mas fora do hardCooldown

fora da seleção
publicados dentro do hardCooldown
```

Dentro das faixas aplicáveis serão utilizados:

```text
lastSuccessfulPublicationAt
score
desempate determinístico
```

O bucket não será incorporado ao score comercial.

---

## 12. A quota será responsabilidade operacional persistida

Será suportado conceito equivalente a:

```text
maxPublicationsPerDay
```

A quota será consultada antes de liberar novo trabalho externo.

Ela não será mantida apenas por contador em memória.

Exemplo proibido como fonte de verdade:

```java
int publicationsToday = 4;
```

A aplicação deverá conseguir reconstruir do PostgreSQL:

```text
quantas entregas já tiveram sucesso no período
quantas entregas estão reservadas para execução
quantas vagas ainda existem
```

---

## 13. Quota e schedule permanecerão responsabilidades diferentes

O scheduler da FASE 17 responde:

```text
quando iniciar um ciclo?
```

A quota responde:

```text
quantas novas entregas podem ser liberadas?
```

Portanto:

```text
ProcessingSchedule
```

não receberá lógica de:

```text
score
cooldown
ranking
quota de publicação
```

Da mesma forma:

```text
PublicationSelectionPolicy
```

não será responsável por acordar threads ou controlar horários do scheduler.

---

## 14. A seleção não consumirá definitivamente quota

Uma decisão de seleção pode ser seguida por:

```text
revisão manual
aprovação
rejeição humana
```

Portanto, apenas aparecer em:

```text
PublicationSelectionDecision = SELECTED
```

não consumirá definitivamente uma vaga operacional.

A seleção produzirá recomendação/priorização.

A reserva efetiva de capacidade ocorrerá quando trabalho de entrega aprovado for persistido na outbox.

---

## 15. A outbox será o ponto de reserva concorrente da quota

Ao inserir trabalho na outbox, o caso de uso deverá revalidar atomicamente:

```text
publicação aprovada
identidade ainda não enfileirada
quota ainda disponível
```

A operação deverá considerar, no mesmo escopo operacional:

```text
entregas SUCCESS no período
+
entregas PENDING
+
entregas PROCESSING
```

como capacidade já consumida ou reservada.

Estados de falha que não representam mensagem entregue não deverão contar permanentemente como publicação bem-sucedida.

A estratégia concreta de locking será PostgreSQL e deverá impedir que duas instâncias reservem simultaneamente a última vaga disponível.

---

## 16. A política de seleção poderá calcular capacidade, mas a outbox deverá revalidá-la

A `PublicationSelectionPolicy` poderá receber:

```text
availableSlots
```

e limitar quantos candidatos retornará como selecionados.

Isso melhora a decisão e evita gerar trabalho desnecessário.

Entretanto, essa contagem não é garantia concorrente porque poderá existir intervalo entre:

```text
seleção
revisão
aprovação
outbox
```

Por isso:

```text
seleção
→ verifica quota para decidir

outbox enqueue
→ verifica quota novamente para garantir
```

A garantia final pertence à transação persistente de enqueue.

---

## 17. Candidatos não selecionados por quota continuarão candidatos

Se existirem:

```text
37 candidatos
7 vagas
```

o resultado poderá ser:

```text
7 selecionados
30 não selecionados neste ciclo
```

Os 30 restantes não serão:

```text
apagados
rejeitados comercialmente
invalidados
marcados como FAILED
```

Continuarão disponíveis para ciclos futuros caso permaneçam comercialmente válidos.

---

## 18. Será possível auditar a decisão de seleção

A implementação possuirá conceitos equivalentes a:

```text
PublicationSelectionRun
PublicationSelectionDecision
```

Um `PublicationSelectionRun` representará uma execução da política para determinado:

```text
policyVersion
channel
destination
currentTime
quota
availableSlots
```

Cada decisão deverá preservar dados suficientes para reconstruir o motivo operacional.

Entre eles:

```text
dealEvaluationId
asin
score
lastSuccessfulPublicationAt
successfulPublicationCount
decision
reason
position
```

Razões iniciais poderão incluir conceitos equivalentes a:

```text
SELECTED
DEFERRED_DUE_TO_HARD_COOLDOWN
NOT_SELECTED_DUE_TO_QUOTA
```

Os nomes concretos serão definidos no domínio durante a implementação.

---

## 19. A auditoria operacional não modificará `DealEvaluation`

Uma oferta não selecionada por quota ou recorrência não será comercialmente rejeitada.

Exemplo:

```text
DealEvaluation = aprovada
score = 91
```

pode produzir:

```text
PublicationSelectionDecision
=
NOT_SELECTED_DUE_TO_QUOTA
```

sem mudar o resultado original da avaliação comercial.

A camada de decisão comercial e a camada operacional permanecerão independentes.

---

## 20. Uma publicação precisará estar aprovada antes de entrar na outbox

Fluxo manual:

```text
Publication CREATED
        ↓
revisão
        ↓
aprovação
        ↓
Publication READY
        ↓
outbox
```

A existência da publicação não implicará autorização para envio.

A geração e o envio externo permanecerão desacoplados.

---

## 21. Aprovação continuará válida após o primeiro sucesso

`PUBLISHED` representará que pelo menos uma entrega externa daquela publicação foi concluída com sucesso.

Esse estado não deverá ser interpretado como revogação da aprovação do conteúdo.

Assim, a arquitetura deverá permitir futuramente que uma publicação aprovada possa possuir múltiplas entregas distintas:

```text
publication
+
channel
+
destination
```

sem que um primeiro sucesso torne impossível uma entrega autorizada para outro destino.

A regra concreta de transição será implementada sem transformar o status global da publicação em estado de cada destino.

---

## 22. Falha de um destino não tornará automaticamente toda a publicação `FAILED`

Falhas externas pertencem à entrega específica.

Exemplo:

```text
Publication 42
Telegram / canal-a = SUCCESS
WhatsApp / grupo-b = FAILED_TRANSIENT
```

não deve resultar automaticamente em:

```text
Publication 42 = FAILED
```

O resultado por destino será persistido na outbox e/ou em `PublicationAttempt`.

O status global da `Publication` não substituirá o estado de entrega por canal.

---

## 23. Será utilizado um contrato de canal independente de provider

Será criado conceito equivalente a:

```text
PublicationChannel
```

Responsabilidades:

```text
identificar o tipo de canal
validar destino
publicar comando pronto
classificar resultado
```

Adapters concretos futuros:

```text
PublicationChannel
        ↑
        ├── TelegramChannel
        └── WhatsAppChannel
```

A FASE 18 não implementará integração real com esses providers.

---

## 24. O canal receberá um comando pronto

Será introduzido conceito equivalente a:

```text
PublicationCommand
```

O comando conterá os dados necessários para entrega.

O adapter de canal não poderá consultar diretamente:

```text
OfferSnapshotRepository
DealEvaluationRepository
PublicationRepository
Amazon
```

para construir a mensagem.

Fluxo correto:

```text
application
    ↓
PublicationCommand pronto
    ↓
PublicationChannel
```

Fluxo incorreto:

```text
PublicationChannel
    ↓
consulta OfferSnapshot
    ↓
consulta DealEvaluation
    ↓
monta conteúdo
```

---

## 25. O resultado do canal será estruturado

Será definido resultado equivalente a:

```text
SUCCESS
FAILED_TRANSIENT
FAILED_PERMANENT
```

O resultado poderá carregar:

```text
providerReference
errorCode
```

quando disponíveis.

Exceções específicas de provider não deverão escapar como semântica da camada de aplicação.

O adapter deverá classificá-las no contrato comum.

---

## 26. A outbox será uma fila durável diferente do histórico de tentativas

Os conceitos permanecerão distintos.

```text
PublicationOutbox
=
trabalho que precisa ser entregue
```

```text
PublicationAttempt
=
evidência histórica de uma tentativa de entrega
```

Portanto:

```text
outbox != attempt
```

Uma entrada de outbox poderá produzir uma ou mais tentativas ao longo de sua vida futura.

A FASE 18 implementará inicialmente a tentativa necessária para comprovar o fluxo completo.

Políticas avançadas de retry de canal permanecem reservadas às fases posteriores.

---

## 27. A identidade idempotente mínima da outbox será composta

A identidade lógica mínima será:

```text
publicationId
+
channel
+
destination
```

O PostgreSQL possuirá proteção por unique constraint correspondente.

Essa proteção complementará verificações da aplicação.

Não será suficiente fazer apenas:

```text
SELECT
if not exists
INSERT
```

sem garantia no banco.

---

## 28. A outbox preservará a decisão, não recalculará a seleção

A entrada da outbox armazenará ou referenciará informações suficientes para preservar:

```text
selectionRun
selectionDecision
selectionPolicyVersion
selectionPosition
```

quando o trabalho tiver origem na política automática.

O worker não consultará score para decidir qual oferta deve ser enviada.

O worker não recalculará cooldown.

O worker não refará ranking comercial.

---

## 29. A prioridade da outbox será baseada no trabalho autorizado

Conceitualmente:

```text
trabalhos aprovados e disponíveis
        ↓
ordem persistida
        ↓
claim
```

A ordem poderá utilizar:

```text
availableAt
selectionPosition
id
```

ou identidade persistente equivalente.

A implementação concreta deverá possuir desempate determinístico.

A ausência de aprovação de uma publicação não bloqueará indefinidamente outras publicações já aprovadas.

---

## 30. A outbox possuirá estados próprios

Será utilizado modelo equivalente a:

```text
PENDING
PROCESSING
SUCCEEDED
FAILED_TRANSIENT
FAILED_PERMANENT
```

Esses estados pertencem à entrega.

Eles não substituem:

```text
PublicationStatus
```

nem:

```text
DealEvaluation
```

---

## 31. O claim do worker utilizará lease PostgreSQL

A experiência da FASE 17 será reutilizada conceitualmente.

Uma entrada de outbox terá estado equivalente a:

```text
leaseOwner
leaseExpiresAt
```

Somente uma instância poderá possuir o trabalho no intervalo válido do lease.

A aquisição deverá ser atômica.

Duas instâncias concorrentes não poderão normalmente publicar a mesma entrada ao mesmo tempo.

---

## 32. Lease expirado poderá ser recuperado

Se um worker desaparecer enquanto possui trabalho:

```text
leaseExpiresAt < now
```

outra instância poderá retomar a entrada.

O lease não será lock permanente.

A recuperação continuará baseada em estado PostgreSQL.

---

## 33. Idempotência de banco não equivale a exactly-once externo absoluto

Existe uma fronteira inevitável:

```text
provider aceita mensagem
        ↓
processo cai
        ↓
resultado SUCCESS ainda não foi persistido
```

Nesse cenário, apenas o banco não consegue provar se o provider recebeu a mensagem.

Portanto, a FASE 18 garantirá que:

```text
reinício simples após sucesso persistido
```

não provoque reenvio.

Também protegerá:

```text
enqueue duplicado
claim concorrente normal
reprocessamento de estado já concluído
```

Mas não será feita promessa de exactly-once externo absoluto quando o provider não fornecer mecanismo correspondente.

Na FASE 19, adapters deverão utilizar chave de idempotência do provider quando esse recurso existir.

---

## 34. Um resultado `SUCCESS` será evidência de publicação histórica

Depois de entrega bem-sucedida:

```text
PublicationAttempt SUCCESS
```

será persistida.

Essa tentativa será a fonte primária para consultas futuras de:

```text
lastSuccessfulPublicationAt
successfulPublicationCount
```

no respectivo:

```text
ASIN
channel
destination
```

Isso fecha a decisão que a ADR-0010 deixou aberta entre estado global da publicação e tentativa de entrega.

---

## 35. Resultado falho não iniciará cooldown

Resultados:

```text
FAILED_TRANSIENT
FAILED_PERMANENT
```

não atualizarão:

```text
lastSuccessfulPublicationAt
```

nem:

```text
successfulPublicationCount
```

Uma oferta não será penalizada como publicada quando a mensagem não foi entregue.

---

## 36. `Publication = PUBLISHED` terá semântica agregada

Quando existir pelo menos uma tentativa externa `SUCCESS`, a publicação poderá transicionar para:

```text
PUBLISHED
```

quando a transição for compatível com seu estado atual.

Esse status responderá:

```text
esta publicação já teve algum sucesso externo?
```

Ele não responderá:

```text
todos os destinos foram entregues?
```

A fonte de verdade para recorrência por destino continuará sendo:

```text
PublicationAttempt SUCCESS
```

---

## 37. Retry automático de provider não será implementado integralmente na FASE 18

A FASE 18 classificará:

```text
FAILED_TRANSIENT
FAILED_PERMANENT
```

e persistirá o resultado.

A política completa de:

```text
número máximo de tentativas
backoff de canal
janela de retry
rate limit
circuit breaker
```

não será adicionada antecipadamente.

Essas responsabilidades serão incorporadas nas fases posteriores conforme roadmap.

---

## 38. Telegram e WhatsApp não entrarão na FASE 18

A FASE 18 possuirá adapter fake/testável.

Exemplo:

```text
FakePublicationChannel
```

Seu objetivo será provar:

```text
Publication READY
        ↓
outbox
        ↓
claim
        ↓
PublicationCommand
        ↓
FakePublicationChannel
        ↓
PublicationResult
        ↓
resultado persistido
```

Não serão necessárias:

```text
credenciais Telegram
credenciais WhatsApp
HTTP real de provider
webhook
telefone real
grupo real
canal real
```

---

## 39. O PostgreSQL continuará sendo a fila operacional

Não serão introduzidos durante a FASE 18:

```text
Kafka
RabbitMQ
SQS
Redis Streams
```

somente para implementar publicação externa.

O PostgreSQL já oferece:

```text
transação
unique constraint
locking
lease
índices
auditoria
```

e continuará sendo suficiente enquanto o volume real não demonstrar necessidade diferente.

---

## 40. A outbox será independente do scheduler de coleta

Não será criado acoplamento equivalente a:

```text
ProcessingScheduler
    ↓
PublicationOutboxWorker
```

O scheduler da FASE 17 permanece responsável por ciclos do pipeline de coleta.

A publicação externa possuirá seu próprio caso de uso/worker.

Ambos poderão existir no mesmo runtime futuramente, mas permanecerão conceitualmente independentes.

---

## 41. Estado durável continuará sendo a fonte de verdade

Não serão usados exclusivamente em memória:

```text
fila de publicação
quota consumida
última publicação
estado de tentativa
lease
```

Restart da JVM não deverá zerar a visão operacional do sistema.

O PostgreSQL deverá permitir reconstruir:

```text
trabalhos pendentes
trabalhos em processamento
trabalhos concluídos
trabalhos falhos
sucessos anteriores
quota consumida/reservada
histórico por ASIN/canal/destino
```

---

# Fluxo final da FASE 18

O fluxo operacional será:

```text
DealEvaluation elegível
        ↓
score/ranking existentes
        ↓
consulta de histórico de publicação
        ↓
PublicationSelectionPolicy V1
        ↓
PublicationSelectionDecision
        ↓
PublicationGenerator
        ↓
Publication CREATED
        ↓
revisão
        ↓
aprovação
        ↓
Publication READY
        ↓
enqueue transacional
        ↓
revalidação de quota
        ↓
PublicationOutbox PENDING
        ↓
worker claim/lease
        ↓
PublicationCommand
        ↓
PublicationChannel
        ↓
PublicationResult
        ↓
PublicationAttempt
        ↓
outbox terminal
        ↓
histórico para próximo ciclo
```

---

# Ordem de implementação

A implementação seguirá a sequência:

```text
18.0
ADR e contrato arquitetural

18.1
modelo de histórico de publicação bem-sucedida

18.2
PublicationSelectionPolicy V1

18.3
cooldown configurável

18.4
quota persistida e concorrente

18.5
seleção determinística:
histórico → score → desempate

18.6
auditoria da decisão de seleção

18.7
aprovação CREATED → READY

18.8
PublicationChannel
PublicationCommand
PublicationResult

18.9
outbox persistida e idempotente

18.10
claim e lease concorrente

18.11
worker genérico de publicação

18.12
FakePublicationChannel

18.13
teste vertical:
READY → outbox → worker → channel → resultado persistido

18.14
gate final e documentação
```

---

# Fora do escopo da FASE 18

Não serão implementados nesta fase:

```text
Telegram real
WhatsApp real
credenciais reais de provider
webhooks reais
retry avançado de provider
rate limiting específico de provider
circuit breaker de provider
dashboard analítico de publicação
infraestrutura de mensageria externa
Kafka
RabbitMQ
```

Também não será alterada a semântica do motor comercial de:

```text
elegibilidade
filtros
score
ranking
momentum
```

---

# Consequências

## Positivas

A arquitetura passa a distinguir claramente:

```text
qualidade comercial
prioridade operacional
aprovação
fila
tentativa
entrega
```

A seleção deixa de repetir automaticamente os maiores scores.

O histórico passa a participar da decisão sem contaminar o score.

A recorrência pode ser reconstruída por canal e destino.

A quota permanece segura contra restart e concorrência.

A outbox permanece simples como mecanismo de entrega de trabalho já autorizado.

Adapters de Telegram e WhatsApp poderão ser adicionados sem transportar regras comerciais.

---

## Custos

A FASE 18 passa a exigir mais persistência operacional.

Serão necessários modelos, ports, migrations e repositories para:

```text
configuração da política
execução da seleção
decisões auditáveis
outbox
tentativas
```

A garantia concorrente de quota exige transação e locking explícitos.

A distinção entre status global de publicação e status por destino aumenta o número de estados operacionais.

Esses custos são aceitos porque evitam transportar regras de negócio para:

```text
scheduler
worker
adapter de canal
interface
```

---

# Critério de conclusão

A FASE 18 estará concluída quando testes herméticos provarem pelo menos:

```text
candidatos com score são carregados
        ↓
histórico de sucesso é considerado
        ↓
hard cooldown é respeitado
        ↓
nunca publicados recebem prioridade adequada
        ↓
score desempata conforme PUBLICATION_SELECTION_V1
        ↓
quota limita novas autorizações
        ↓
decisão fica auditável
        ↓
Publication é gerada/revisada/aprovada
        ↓
outbox é criada de forma idempotente
        ↓
dois workers não processam normalmente a mesma entrada
        ↓
FakePublicationChannel recebe PublicationCommand pronto
        ↓
resultado estruturado é persistido
        ↓
SUCCESS passa a compor histórico futuro
        ↓
restart não reenfileira entrega já concluída
```

Tudo isso deverá funcionar sem integração real com Telegram ou WhatsApp.

---

# Relação resumida entre os componentes

```text
score
  │
  │ não é alterado
  ▼
PublicationSelectionPolicy
  │
  │ decide o que publicar
  ▼
PublicationSelectionDecision
  │
  ▼
PublicationGenerator
  │
  ▼
Publication
  │
  │ revisão/aprovação
  ▼
Outbox
  │
  │ preserva trabalho autorizado
  ▼
PublicationWorker
  │
  ▼
PublicationChannel
  │
  ▼
PublicationResult
  │
  ▼
PublicationAttempt
  │
  └──────────────► histórico da próxima seleção
```

Regra de responsabilidade:

```text
A seleção decide O QUE publicar.

A quota decide SE existe capacidade para publicar.

O scheduler decide QUANDO iniciar ciclos.

A outbox preserva O TRABALHO autorizado.

O worker executa O TRABALHO.

O canal sabe COMO entregar.

O histórico registra O QUE REALMENTE FOI ENTREGUE.
```
