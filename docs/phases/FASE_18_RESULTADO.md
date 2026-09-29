# FASE 18 — RESULTADO CONSOLIDADO

**Projeto:** Rasping Amazon  
**Fase:** 18 — Contrato de canais e outbox de publicação  
**Data de consolidação:** 29/09/2026  
**Status local:** CONCLUÍDA — implementação, worker genérico, completion atômico, prova end-to-end, gate completo e validação externa real aprovados  
**Status remoto:** PENDENTE — commit, push da branch, Pull Request e CI remoto ainda precisam ser concluídos  
**Baseline remoto:** `main` após merge da FASE 17 (`1a0beb9776e59852ef8707aa29255997470382e2`)  
**Schema Flyway local final:** V27  
**Gate local final:** 1229 testes, 0 falhas, 0 erros, 0 ignorados — `BUILD SUCCESS`  
**Último gate remoto confirmado:** FASE 17, CI verde após merge  

---

## 1. Status de fechamento local

Este arquivo consolida o resultado técnico final da FASE 18 no estado local da branch:

```text
feat/fase-18-canais-outbox-publicacao
```

O gate completo foi executado após a implementação dos blocos de seleção, aprovação, canal, outbox, completion, worker genérico e prova end-to-end.

Resultado:

```text
Tests run: 1229
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

Também foi executado:

```text
git diff --check
```

sem apontar problemas.

A suíte final confirmou, entre outros, os seguintes blocos específicos da FASE 18:

```text
PublicationOutboxWorkerTest
JdbcPublicationOutboxCompletionAdapterTest
PublicationOutboxEndToEndTest
FakePublicationChannelTest
JdbcPublicationOutboxEnqueueConcurrencyTest
JdbcPublicationOutboxQueueConcurrencyTest
```

Portanto, o estado local correto é:

```text
FASE 18 = CONCLUÍDA
```

O fechamento remoto permanece pendente somente de:

```text
commit
push
Pull Request
CI remoto verde
```

A ADR-0014 também registra a decisão operacional de utilizar DOM renderizado com Playwright/Chromium na página individual em produção. A consolidação desse provider na infraestrutura principal deve ocorrer antes da operação real contínua, mas não altera o critério funcional de conclusão da FASE 18, cujo escopo é seleção, aprovação, outbox e contrato de canais.

---

## 2. Fonte de verdade da fase

A FASE 18 foi conduzida a partir das seguintes fontes arquiteturais:

```text
docs/phases/ROADMAP_RASPING_AMAZON_V1.md
docs/adr/0010-politica-selecao-recorrencia-cadencia-publicacoes.md
docs/adr/0011-observabilidade-correlacao-e-metricas-operacionais.md
docs/adr/0012-agendamento-e-execucao-continua.md
docs/adr/0013-selecao-operacional-outbox-e-entrega-publicacao.md
docs/adr/0014-aquisicao-dom-renderizado-pagina-produto-amazon.md
docs/phases/FASE_17_RESULTADO.md
README.md
estado real da branch local da FASE 18
```

O roadmap define para a FASE 18 o objetivo de preparar publicação externa sem acoplar o sistema a Telegram ou WhatsApp.

Fluxo esperado:

```text
Publication READY
        ↓
outbox persistida
        ↓
worker
        ↓
PublicationChannel
        ↓
PublicationResult
```

A fase deve preservar a separação entre:

```text
geração
aprovação
seleção operacional
reserva de quota
persistência de outbox
tentativa de entrega
provider externo
```

---

## 3. Baseline da FASE 18

A FASE 18 iniciou após o fechamento remoto da FASE 17.

Referência remota utilizada como baseline:

```text
commit: 1a0beb9776e59852ef8707aa29255997470382e2
mensagem: Merge pull request #8 from veiocadan/feat/fase-17-agendamento-execucao-continua
```

O CI remoto da FASE 17 foi confirmado verde.

A branch local planejada para a fase é:

```text
feat/fase-18-canais-outbox-publicacao
```

No início da fase o catálogo Flyway terminava em:

```text
V24
```

A implementação local da FASE 18 evoluiu o schema até:

```text
V27
```

---

## 4. Objetivo arquitetural

A FASE 18 não implementa Telegram nem WhatsApp reais.

O objetivo é construir a infraestrutura independente de provider:

```text
DealEvaluation elegível e pontuada
        ↓
PublicationSelectionPolicy
        ↓
Publication selecionada
        ↓
aprovação manual
        ↓
Publication READY
        ↓
reserva de quota + outbox
        ↓
claim/lease
        ↓
PublicationCommand
        ↓
PublicationChannel
        ↓
PublicationResult
```

O canal recebe um comando autocontido.

O canal não deve consultar:

```text
OfferSnapshot
DealEvaluation
Product
repositórios internos
Amazon
```

para reconstruir a mensagem no momento da entrega.

---

## 5. ADR-0010 como regra de separação

A FASE 18 ratificou a distinção:

```text
qualidade da oferta
        ≠
prioridade operacional de publicação
```

O score continua respondendo:

```text
quão atrativa é esta oferta?
```

A política de seleção passa a responder:

```text
qual oferta deve receber prioridade operacional agora?
```

Histórico de publicação não modifica score retroativamente.

A seleção ocorre depois de elegibilidade, filtros, score e ranking.

---

## 6. ADR-0013 — seleção operacional, outbox e entrega

Foi preparado o ADR:

```text
docs/adr/0013-selecao-operacional-outbox-e-entrega-publicacao.md
```

A decisão consolida:

- score separado de prioridade operacional;
- `PUBLICATION_SELECTION_V1`;
- histórico somente de entrega externa bem-sucedida;
- escopo por ASIN + channel + destination;
- cooldown configurável;
- quota configurável;
- seleção provisória até a reserva efetiva na outbox;
- `Publication READY` antes do enqueue;
- outbox separada de tentativa de entrega;
- provider independente do domínio;
- claim por lease em PostgreSQL;
- idempotência de entrega por `publication + channel + destination`.

Uma decisão importante foi explicitada:

```text
NOT_SELECTED_DUE_TO_QUOTA
    → decisão de auditoria
    → nenhuma linha de outbox
    → nenhuma prioridade carregada para o futuro
    → candidato volta a ser reavaliado em ciclo posterior
```

A quota não representa rejeição comercial.

---

## 7. Histórico de publicação bem-sucedida

Foi criado o modelo de histórico necessário à seleção operacional.

A semântica consolidada é:

```text
somente tentativa externa SUCCESS
        ↓
conta como publicação bem-sucedida
```

Não contam como recorrência bem-sucedida:

```text
geração da Publication
aprovação READY
criação da outbox
claim do worker
falha transitória
falha permanente
```

O histórico é consultado no escopo exato:

```text
ASIN
+
channel
+
destination
```

Isso evita que publicação em um destino impeça indevidamente outra publicação válida em destino diferente.

---

## 8. `PublicationSelectionPolicy`

Foi criada a política de seleção operacional versionada.

Versão:

```text
PUBLICATION_SELECTION_V1
```

A política trabalha com candidatos já qualificados e pontuados.

Ela não executa:

```text
coleta
enrichment
elegibilidade
filtros
cálculo de score
consulta externa
```

A ordem determinística inicial considera:

```text
1. nunca publicado com sucesso
2. publicação bem-sucedida mais antiga
3. maior score
4. identidade estável para desempate
```

---

## 9. Cooldown configurável

Foi introduzido perfil próprio de seleção.

A configuração de cooldown não foi transformada em constante semântica escondida no código.

A política distingue candidatos em estados operacionais próprios.

Entre os estados consolidados:

```text
SELECTED
DEFERRED_DUE_TO_HARD_COOLDOWN
NOT_SELECTED_DUE_TO_QUOTA
```

Candidatos barrados por hard cooldown não ocupam posição de publicação.

---

## 10. Quota configurável

A quota foi separada do scheduler.

Conceitualmente:

```text
scheduler
    → quando executar

quota
    → quantas publicações podem ser reservadas no escopo/período
```

Foi criado perfil próprio de quota e snapshot observacional para a etapa de seleção.

A seleção pode identificar candidatos acima da capacidade disponível, mas a reserva definitiva ocorre somente no enqueue da outbox.

---

## 11. Concorrência da quota

A reserva efetiva de quota foi implementada no mesmo caminho transacional do enqueue.

Princípio:

```text
seleção SELECTED
    → provisória

linha de outbox criada
    → slot de quota reservado
```

O adapter de enqueue serializa a decisão por perfil de quota ativo e conta as linhas já reservadas no escopo/dia.

Foi criada prova concorrente com duas conexões e quota máxima igual a um.

Resultado esperado e validado no bloco de testes da outbox:

```text
1 ENQUEUED
1 QUOTA_EXHAUSTED
1 linha de outbox
```

O candidato perdedor não recebe uma linha em espera.

---

## 12. Auditoria da seleção — V25

Foi criada:

```text
V25__publication_selection_audit.sql
```

Estruturas introduzidas:

```text
publication_selection_profile
publication_quota_profile
publication_selection_run
publication_selection_decision
```

A auditoria registra o resultado da política sem confundir decisão provisória com reserva efetiva de quota.

Foram implementados providers JDBC para os perfis e repositório de auditoria da seleção.

---

## 13. Serviço de seleção

Foi criado serviço de aplicação para coordenar:

```text
candidatos já qualificados
        ↓
perfil de seleção
        ↓
perfil de quota
        ↓
histórico de sucesso
        ↓
PublicationSelectionPolicy
        ↓
auditoria persistida
```

A política continua isolada de JDBC.

A persistência continua isolada da regra de ordenação.

---

## 14. Aprovação manual

A FASE 18 implementou a transição explícita:

```text
Publication CREATED
        ↓
aprovação
        ↓
Publication READY
```

Foi criado serviço específico de aprovação.

A alteração de status utiliza atualização condicional com status esperado, preservando concorrência e impedindo transições silenciosas indevidas.

A geração continua não equivalendo a aprovação.

---

## 15. Contrato de canais

Foi criado o pacote de contratos de canal de publicação.

Conceitos principais:

```text
PublicationCommand
PublicationChannel
PublicationResult
PublicationResultStatus
```

O comando transporta, de forma autocontida:

```text
publicationId
channel
destination
content
```

O canal recebe conteúdo pronto.

Ele não reconstrói a publicação a partir do banco.

---

## 16. Resultado estruturado de publicação

Os resultados previstos são:

```text
SUCCESS
FAILED_TRANSIENT
FAILED_PERMANENT
```

`PublicationResult` preserva:

```text
status
providerReference
errorCode
```

Provider reference pode ser nula quando a integração não fornece uma referência.

A classificação de erro pertence ao resultado do canal e não deve ser inferida pelo worker a partir de texto livre.

---

## 17. Fake channel

Foi implementado:

```text
FakePublicationChannel
```

O fake:

- recebe `PublicationCommand` real;
- registra comandos publicados;
- retorna `PublicationResult` configurável;
- permite testar sucesso, falha transitória e falha permanente;
- não depende de Telegram ou WhatsApp.

Esse adapter existe para validar o pipeline da FASE 18 sem antecipar providers reais da FASE 19.

---

## 18. Outbox persistida — V26

Foi criada:

```text
V26__publication_outbox.sql
```

A tabela `publication_outbox` preserva, entre outros fatos:

```text
publication_id
selection_run_id
selection_position
channel
destination
content
quota_profile_version
quota_date
status
available_at
locked_at
locked_by
created_at
updated_at
finished_at
```

Estados:

```text
PENDING
PROCESSING
SUCCEEDED
FAILED_TRANSIENT
FAILED_PERMANENT
```

A linha de outbox contém o conteúdo congelado que será enviado.

O worker não recalcula mensagem, score ou seleção.

---

## 19. Identidade idempotente da entrega

A outbox protege a identidade mínima:

```text
publication_id
+
channel
+
destination
```

Isso impede criação concorrente de múltiplos trabalhos lógicos para a mesma entrega autorizada.

Também existe proteção da posição dentro do run de seleção.

---

## 20. Semântica da quota na outbox

A existência da linha de outbox representa slot reservado.

Todos os estados da mesma linha continuam ocupando o mesmo slot diário.

Consequentemente:

```text
FAILED_PERMANENT
```

não libera automaticamente o slot para criação de uma nova publicação no mesmo dia.

Retry da mesma entrega deve reutilizar a identidade já reservada, e não criar outra publicação para consumir quota novamente.

---

## 21. Enqueue transacional

Foi criada porta de enqueue e implementação JDBC.

Resultados explícitos:

```text
ENQUEUED
ALREADY_ENQUEUED
QUOTA_EXHAUSTED
STALE_SELECTION
```

O adapter valida:

- decisão `SELECTED`;
- `Publication READY`;
- perfil de quota ainda compatível;
- data local do perfil;
- quota ainda disponível;
- identidade idempotente ainda inexistente.

A reserva ocorre no banco, não apenas em memória.

---

## 22. Claim e lease da outbox

Foi criada a porta de fila da outbox e adapter JDBC.

O claim utiliza PostgreSQL com:

```sql
FOR UPDATE SKIP LOCKED
```

Ordem de claim:

```text
available_at
selection_run_id
selection_position
id
```

Transição:

```text
PENDING
    ↓
PROCESSING
```

com:

```text
locked_at
locked_by
```

Foi implementada recuperação de leases expirados para devolver itens abandonados ao estado pendente.

---

## 23. Concorrência do claim

Foi criada prova concorrente específica para a fila de outbox.

O bloco de testes conhecido da outbox fechou com:

```text
Tests run: 21
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

Esse bloco inclui migrations, enqueue, concorrência da quota, item de outbox, claim/lease e concorrência de claim.

---

## 24. Relação entre outbox e tentativa de entrega — V27

A FASE 18 preservou a diferença entre:

```text
outbox
    → trabalho lógico autorizado

tentativa
    → execução concreta desse trabalho
```

Foi criada:

```text
V27__publication_attempt_outbox_link.sql
```

A migration adiciona `publication_outbox_id` a `publication_attempt` e cria proteção de unicidade por:

```text
publication_outbox_id
+
attempt_number
```

A coluna é nullable para preservar compatibilidade histórica com tentativas anteriores à outbox.

---

## 25. Persistência do resultado da entrega

Foi desenhada a fronteira de completion para realizar de forma atômica:

```text
PublicationResult
        ↓
publication_attempt
+
status terminal da outbox
```

Mapeamento previsto:

```text
SUCCESS
    → attempt SUCCESS
    → outbox SUCCEEDED

FAILED_TRANSIENT
    → attempt FAILED_TRANSIENT
    → outbox FAILED_TRANSIENT

FAILED_PERMANENT
    → attempt FAILED_PERMANENT
    → outbox FAILED_PERMANENT
```

A conclusão deve exigir ownership válido do lease atual.

A FASE 18 não deve usar o status global de `Publication` como verdade de recorrência de entregas futuras.

A implementação final foi comprovada por `JdbcPublicationOutboxCompletionAdapterTest`, com 6 testes aprovados, e pelo E2E de publicação, sem falhas.

---

## 26. Worker genérico de publicação

O worker genérico foi implementado com o seguinte fluxo:

```text
claimNext
    ↓
PublicationOutboxItem.command()
    ↓
resolver de PublicationChannel
    ↓
publish(command)
    ↓
PublicationResult
    ↓
completion
```

O worker não deve conhecer Telegram ou WhatsApp diretamente.

Também não deve recalcular:

```text
seleção
quota
conteúdo
score
ranking
```

A implementação foi comprovada por:

```text
PublicationOutboxWorkerTest
Tests run: 7
Failures: 0
Errors: 0
Skipped: 0
```

---

## 27. Prova end-to-end exigida pelo roadmap

O roadmap exige fake/test adapter provando o caminho end-to-end sem provider real.

A prova end-to-end foi implementada e demonstra:

```text
seleção
    ↓
aprovação READY
    ↓
enqueue
    ↓
claim
    ↓
FakePublicationChannel
    ↓
completion
    ↓
publication_attempt SUCCESS
    ↓
outbox SUCCEEDED
    ↓
histórico de publicação bem-sucedida
```

A prova foi executada por:

```text
PublicationOutboxEndToEndTest
Tests run: 1
Failures: 0
Errors: 0
Skipped: 0
```

O teste percorre o fluxo de publicação até o `FakePublicationChannel`, persiste `publication_attempt`, conclui a outbox e valida o efeito necessário para o histórico de publicação bem-sucedida.

---

## 28. Validação com ofertas reais

Durante a FASE 18 foi criada uma probe real para verificar seleção e quota sobre ofertas atuais da Amazon.

A primeira execução utilizou HTML bruto nas páginas individuais e produziu alto número de falhas por ausência de seller/delivery.

A investigação demonstrou que a informação estava visível no navegador e presente no DOM renderizado.

Foram criadas as probes:

```text
AmazonRenderedProductPageExternalProbeIT
AmazonRenderedSellerDeliveryExternalProbeIT
AmazonRenderedProductEnrichmentExternalProbeIT
AmazonDealsRankingExternalProbeIT
```

As probes serão mantidas versionadas como evidência arquitetural e operacional.

---

## 29. ADR-0014 — DOM renderizado

A investigação resultou em:

```text
docs/adr/0014-aquisicao-dom-renderizado-pagina-produto-amazon.md
```

Decisão:

```text
/deals
    → HTTP enquanto confiável

página individual
    → DOM renderizado em produção
      enquanto não existir API/fonte estruturada igualmente confiável
```

A decisão foi baseada em evidência real, não em preferência de implementação.

O parser existente funcionou corretamente quando recebeu o DOM renderizado.

---

## 30. Resultado da probe real de ranking

A execução final utilizou:

```text
Deals URL: https://www.amazon.com.br/deals
Product-page acquisition: PLAYWRIGHT_RENDERED_DOM
Deals inspected: 30
Daily quota simulated: 5
Filter profile: COMMERCIAL_FILTER_V2
Score profile: SCORE_V2
Successful history rows: 0
```

Resultado:

```text
Processing failures: 1
Ineligible after evaluation: 17
Eligible with score: 12
```

Top 5 selecionado:

```text
#1 B00NHQFA1I  score 72.0146
#2 6555321806  score 67.1734
#3 B08R91NTHY  score 65.3868
#4 B01IT28KG6  score 47.5386
#5 B0DD1KD5JP  score 45.4735
```

Restantes elegíveis:

```text
#06 B0GVT7QXF7  NOT_SELECTED_DUE_TO_QUOTA
#07 B0FG9GWZJS  NOT_SELECTED_DUE_TO_QUOTA
#08 B0DPY3469H  NOT_SELECTED_DUE_TO_QUOTA
#09 B0GKQTJD9P  NOT_SELECTED_DUE_TO_QUOTA
#10 B0GMY41LM8  NOT_SELECTED_DUE_TO_QUOTA
#11 B0F8KVQZQX  NOT_SELECTED_DUE_TO_QUOTA
#12 B0GTRZFNJM  NOT_SELECTED_DUE_TO_QUOTA
```

A probe terminou com:

```text
Tests run: 1
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

Tempo aproximado:

```text
02:44 min
```

---

## 31. Caso residual da probe real

Um dos 30 produtos permaneceu sem seller/delivery mesmo com DOM renderizado:

```text
ASIN B0GJFS2Y3W
```

O caso continuou visível como falha de processamento.

Não foi transformado artificialmente em `Amazon` e não foi ocultado com alteração do schema.

Isso preserva a regra:

```text
ausência de evidência
    ≠
inferência de Amazon
```

---

## 32. Decisão operacional para produção

A aquisição da página individual deverá migrar para provider renderizado em produção.

Motivação:

```text
corretude da evidência
>
custo mínimo por página
```

A operação prevista é aproximadamente:

```text
2 ciclos por dia
>10 horas entre ciclos
~450 produtos como ordem de grandeza atual
```

Nesse cenário, uma execução longa continua compatível com a operação.

Existe ainda intenção de utilizar hardware dedicado, incluindo Raspberry Pi dedicado ao sistema.

A solução permanecerá aberta para substituição futura por API ou fonte estruturada menos custosa quando houver confiabilidade equivalente.

---

## 33. Playwright e fronteira de infraestrutura

A decisão de produção implica que o provider renderizado não deve permanecer duplicado apenas dentro de `src/external-probe`.

O estado alvo é:

```text
src/main
    ↓
provider Playwright de produção
    ↓
ProductPageContentProvider
    ↓
AmazonProductPageEnrichmentClient

src/external-probe
    ↓
reutiliza o mesmo provider
```

Essa integração é uma dívida técnica explicitamente registrada pela ADR-0014 e deve ser concluída antes de considerar o sistema pronto para operação real contínua.

---

## 34. Migrations da FASE 18

Migrations adicionadas localmente:

```text
V25__publication_selection_audit.sql
V26__publication_outbox.sql
V27__publication_attempt_outbox_link.sql
```

Não foi adotada a migration inicialmente cogitada para tornar seller/delivery nullable como correção da aquisição incompleta.

Portanto, o catálogo conhecido ao final desta consolidação permanece em:

```text
V27
```

---

## 35. Evidência de testes conhecida

Blocos confirmados durante a implementação incluem:

```text
contratos de canal + fake
Tests run: 60
Failures: 0
Errors: 0
Skipped: 0
```

```text
outbox / quota / claim / concorrência
Tests run: 21
Failures: 0
Errors: 0
Skipped: 0
```

```text
migrations de seleção/outbox/attempt
Tests run: 3
Failures: 0
Errors: 0
Skipped: 0
```

```text
probe real de ranking com DOM renderizado
Tests run: 1
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

O comando:

```text
git diff --check
```

foi executado no estado final local sem apontar problemas.

Gate final completo:

```text
.\mvnw.cmd clean test

Tests run: 1229
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

O Flyway validou:

```text
27 migrations
schema public = V27
```

Esse gate inclui os testes unitários, JDBC, concorrência, migrations e o E2E de outbox da FASE 18.

---

## 36. O que não pertence à FASE 18

A fase deliberadamente não deve implementar providers reais de:

```text
Telegram
WhatsApp
```

Também não antecipa o hardening completo da FASE 19/20, como:

```text
rate limiting específico de provider
retry sofisticado por provider
backoff específico de canal
circuit breaker
dead-letter externo
broker adicional
```

A FASE 18 constrói o contrato e a infraestrutura genérica necessária para que esses adapters possam ser acrescentados depois.

---

## 37. Arquitetura consolidada da publicação

Fluxo conceitual consolidado:

```text
Amazon Deals
    ↓
ParsedDeal
    ↓
enrichment confiável
    ↓
OfferSnapshot
    ↓
DealEvaluation
    ↓
score / ranking
    ↓
PublicationSelectionPolicy
    ↓
selection audit
    ↓
Publication gerada
    ↓
aprovação CREATED → READY
    ↓
outbox + reserva atômica de quota
    ↓
claim / lease
    ↓
PublicationCommand
    ↓
PublicationChannel
    ↓
PublicationResult
    ↓
PublicationAttempt
    ↓
histórico de sucesso para seleção futura
```

A cadeia preserva separação entre decisão comercial, prioridade operacional e entrega externa.

---

## 38. Arquivos que devem permanecer versionados

Além do código principal e dos testes herméticos, devem permanecer no repositório as probes que fundamentaram decisões arquiteturais:

```text
src/external-probe/java/com/raspingamazon/infrastructure/diagnostic/AmazonRenderedProductPageExternalProbeIT.java
src/external-probe/java/com/raspingamazon/infrastructure/diagnostic/AmazonRenderedSellerDeliveryExternalProbeIT.java
src/external-probe/java/com/raspingamazon/infrastructure/diagnostic/AmazonRenderedProductEnrichmentExternalProbeIT.java
src/external-probe/java/com/raspingamazon/infrastructure/diagnostic/AmazonDealsRankingExternalProbeIT.java
```

Essas classes não devem entrar na suíte hermética padrão.

---

## 39. Fechamento local e pendências remotas

Os critérios funcionais e técnicos locais da FASE 18 foram confirmados:

1. completion atômico `publication_attempt + outbox`;
2. worker genérico `claim → channel → completion`;
3. prova end-to-end com `FakePublicationChannel`;
4. `mvnw clean test` completo;
5. `git diff --check`;
6. migrations Flyway validadas até V27;
7. probes externas reais preservadas para versionamento;
8. ADR-0013 e ADR-0014 consolidadas.

A decisão de produção do ADR-0014 é:

```text
página individual
    → DOM renderizado com Playwright/Chromium
```

A implementação do provider Playwright em `src/main` permanece como pré-requisito para a operação real contínua e deve reutilizar `ProductPageContentProvider`, sem duplicar a lógica das probes.

Pendências exclusivamente remotas para encerrar a branch no GitHub:

```text
commit
push
Pull Request
CI remoto verde
```

Essas pendências não alteram o status local:

```text
FASE 18 = CONCLUÍDA
```

---

## 40. Próxima fase

Após o fechamento formal da FASE 18, o roadmap segue para:

```text
FASE 19 — Telegram e WhatsApp
```

A FASE 19 deverá consumir os contratos já definidos pela FASE 18, sem mover lógica de seleção, quota ou reconstrução de publicação para os adapters externos.

---

## 41. Resumo do estado atual

A FASE 18 já estabeleceu e validou a maior parte de sua arquitetura central:

```text
histórico de sucesso
    ↓
seleção operacional versionada
    ↓
cooldown
    ↓
quota
    ↓
auditoria
    ↓
aprovação
    ↓
PublicationCommand / PublicationChannel / PublicationResult
    ↓
fake channel
    ↓
outbox persistida
    ↓
reserva concorrente de quota
    ↓
claim / lease
    ↓
vínculo outbox ↔ attempts
```

Também produziu uma validação real relevante da fonte Amazon e uma nova decisão operacional:

```text
página individual em produção
    → DOM renderizado
```

O estado final local é:

```text
FASE 18
CONCLUÍDA
```

O fechamento remoto depende apenas de commit, push, Pull Request e CI remoto verde.
