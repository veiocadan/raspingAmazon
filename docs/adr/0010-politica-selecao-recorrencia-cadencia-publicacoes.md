# ADR-0010 — Política de seleção, recorrência e cadência de publicações

- **Status:** Proposta
- **Data:** 2026-09-25
- **Projeto:** Rasping Amazon
- **Fases relacionadas:** FASE 17 — Agendamento e execução contínua; FASE 18 — Contrato de canais e outbox de publicação
- **Complementa:** ADR-0002, ADR-0003 e ADR-0004

## Contexto

O Rasping Amazon já separa conceitos que não devem ser confundidos:

- elegibilidade estrutural determina se a oferta pode continuar no pipeline;
- filtros comerciais determinam se a oferta atende aos critérios mínimos ativos;
- score mede a qualidade relativa da oferta corrente;
- ranking ordena ofertas elegíveis segundo o score;
- histórico e momentum descrevem a evolução temporal da oferta;
- geração de publicação transforma uma `DealEvaluation` selecionada em uma `Publication`;
- entrega externa pertence às fases posteriores de scheduler, outbox e canais.

A Amazon não necessariamente renova integralmente a página de ofertas a cada execução.

É esperado que parte das ofertas permaneça disponível por vários ciclos consecutivos, enquanto outras entram e saem de forma dinâmica.

Consequentemente, uma política que simplesmente publique novamente os itens de maior score a cada ciclo tenderia a produzir repetição excessiva.

Exemplo:

```text
Dia 1
ranking:
A = 95
B = 91
C = 88
D = 84

publicados:
A, B, C, D

Dia 2
ranking:
A = 95
B = 91
C = 88
D = 84
E = 82
F = 80

sem política de recorrência:
A, B, C, D voltariam ao topo
```

Esse comportamento não é desejável.

Uma oferta possuir score alto não significa automaticamente que deva ser publicada novamente imediatamente.

Portanto, surge uma nova necessidade semântica:

```text
qualidade da oferta
        ≠
prioridade operacional de publicação
```

A política de publicação deverá considerar o histórico de publicações anteriores sem alterar retroativamente o significado do score.

Também é necessário limitar o volume de mensagens por período e distribuí-las ao longo do tempo, evitando publicar todo o lote de uma vez.

Esta ADR registra as alternativas e estabelece as fronteiras arquiteturais para a implementação futura.

## Decisão

### 1. Score e prioridade de publicação permanecerão conceitos independentes

O score continuará respondendo:

```text
Quão atrativa é esta oferta?
```

A política de seleção para publicação responderá:

```text
Quão apropriado é publicar esta oferta agora?
```

O histórico de publicação não deverá reduzir ou modificar o score da oferta.

Não será adotado comportamento como:

```text
scoreFinal =
    scoreDaOferta
    - penalidadePorPublicacaoRecente
```

O score histórico deve permanecer interpretável segundo sua própria versão.

A priorização operacional ocorrerá depois do ranking e antes da entrega externa.

Fluxo conceitual:

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
candidatos priorizados
        ↓
geração / aprovação / outbox
        ↓
scheduler / worker
        ↓
canal
```

### 2. Será introduzido o conceito de política de seleção de publicação

A solução deverá possuir um conceito equivalente a:

```text
PublicationSelectionPolicy
```

Seu papel será decidir a prioridade operacional entre ofertas já aptas a serem publicadas.

A política deverá utilizar somente dados persistidos e auditáveis.

Entre os dados relevantes poderão estar:

```text
ASIN
score
ranking
lastSuccessfulPublicationAt
successfulPublicationCount
channel
destination
currentTime
```

A política não deverá acessar diretamente Amazon HTML, APIs externas ou provedores de canal.

### 3. A primeira política deverá ser versionada

A primeira versão operacional deverá possuir identificador explícito, por exemplo:

```text
PUBLICATION_SELECTION_V1
```

O nome concreto poderá ser refinado na implementação.

Mudanças futuras que alterem a semântica de:

- prioridade;
- cooldown;
- desempate;
- limite;
- tratamento de ausência de histórico;
- escopo por canal;
- escopo por destino;

não deverão modificar silenciosamente a interpretação da versão anterior.

Quando a semântica mudar de forma incompatível, deverá existir nova versão.

### 4. Publicação anterior significa publicação efetivamente concluída

A geração de uma `Publication` não deverá, por si só, iniciar o cooldown.

Estados como:

```text
CREATED
READY
FAILED
```

não representam necessariamente conteúdo entregue ao público.

A referência temporal para recorrência deverá considerar uma publicação efetivamente concluída com sucesso.

Conceitualmente:

```text
lastSuccessfulPublicationAt
```

deverá derivar de evidência persistida equivalente a:

```text
Publication = PUBLISHED
```

ou:

```text
PublicationAttempt = SUCCESS
```

conforme o modelo definitivo das FASES 18 e 19.

Uma tentativa falha não deverá penalizar artificialmente a oferta como se ela tivesse sido publicada.

### 5. Ofertas nunca publicadas terão tratamento explícito

A política deverá distinguir:

```text
nunca publicada
```

de:

```text
publicada anteriormente
```

Ausência de publicação anterior não equivale a uma data artificial.

Na versão inicial, ofertas nunca publicadas deverão possuir prioridade operacional superior às ofertas recentemente publicadas, desde que continuem elegíveis e válidas.

Exemplo conceitual:

```text
Grupo 1:
nunca publicadas

Grupo 2:
publicadas há mais tempo

Grupo 3:
publicadas recentemente
```

O score continuará sendo utilizado dentro desses grupos ou em desempates, conforme a política versionada.

### 6. Ordenação inicial sugerida

Uma estratégia inicial recomendada é:

```text
1. ofertas nunca publicadas;
2. ofertas com publicação bem-sucedida mais antiga;
3. maior score;
4. desempate determinístico.
```

Exemplo:

```text
Produto A
score = 97
última publicação = ontem

Produto B
score = 84
nunca publicado

Produto C
score = 92
última publicação = 10 dias atrás

Produto D
score = 75
nunca publicado
```

A prioridade operacional poderá resultar em:

```text
B
D
C
A
```

Isso não significa que `B` ou `D` possuam score superior a `A`.

Significa apenas que a política operacional está promovendo variedade e reduzindo repetição.

### 7. Cooldown poderá ser rígido, suave ou híbrido

Foram consideradas três possibilidades.

#### Alternativa A — cooldown rígido

Regra:

```text
se publicado nos últimos X dias
    → não pode ser selecionado
```

Vantagens:

- comportamento simples;
- fácil explicação;
- evita repetição dentro da janela.

Custos:

- pode reduzir demais a quantidade de candidatos;
- uma oferta excepcional pode ficar indisponível mesmo quando ainda seria útil republicá-la;
- exige política adicional quando o número de candidatos não atingir a quota.

#### Alternativa B — cooldown suave

Regra:

```text
se publicado recentemente
    → continua elegível
    → recebe prioridade menor
```

Vantagens:

- mantém flexibilidade;
- permite completar a quota quando houver poucos itens novos;
- reduz repetição sem bloquear completamente.

Custos:

- exige ordenação um pouco mais sofisticada;
- a semântica precisa ser bem explicada e testada.

#### Alternativa C — modelo híbrido

Exemplo:

```text
hardCooldownDays = 2
preferredCooldownDays = 7
```

Comportamento:

```text
publicado há menos de 2 dias
    → temporariamente indisponível

publicado entre 2 e 7 dias
    → elegível com baixa prioridade

publicado há mais de 7 dias
    → prioridade normal

nunca publicado
    → prioridade preferencial
```

O modelo híbrido preserva uma proteção mínima contra repetição imediata e mantém flexibilidade operacional.

### 8. A ADR não fixa ainda o número definitivo de dias

A discussão inicial utilizou como exemplos:

```text
2 dias
7 dias
```

Esses valores não constituem regra imutável.

A implementação deverá permitir configuração controlada.

Exemplo conceitual:

```text
hardCooldownDays
preferredCooldownDays
```

A escolha dos valores iniciais deverá ser validada com uso real.

Não deverá existir dependência de alteração de código para mudar esses limites.

### 9. O limite de publicações será uma política operacional configurável

Foi levantado como valor inicial:

```text
7 publicações por dia
```

Esse número deverá ser tratado como configuração inicial candidata, não como constante semântica do domínio.

Conceito esperado:

```text
maxPublicationsPerDay
```

Poderão existir posteriormente:

```text
maxPublicationsPerHour
minimumIntervalMinutes
```

caso o comportamento real demonstre necessidade.

Não deverá ser necessário recompilar a aplicação para alterar a quota operacional.

### 10. Limite diário e cadência são responsabilidades diferentes

A política deverá distinguir:

```text
quota
```

de:

```text
schedule
```

Exemplo:

```text
maxPublicationsPerDay = 7
```

não significa:

```text
publicar 7 mensagens consecutivamente às 08:00
```

A cadência poderá distribuir as publicações ao longo do dia:

```text
08:00
10:00
12:00
14:00
16:00
18:00
20:00
```

Os horários acima são apenas exemplo.

A frequência real deverá ser configurável e ficará sob responsabilidade do mecanismo de agendamento da FASE 17.

### 11. A fila não será destruída quando a quota for atingida

Ofertas que permaneçam elegíveis, mas não sejam selecionadas por falta de quota, deverão permanecer disponíveis para ciclos posteriores.

Exemplo:

```text
37 ofertas candidatas
7 vagas no dia
        ↓
7 selecionadas
30 permanecem candidatas
```

No ciclo seguinte, o histórico das 7 publicadas muda.

As ofertas restantes poderão subir naturalmente na prioridade.

A política não deverá apagar, invalidar ou marcar como rejeitada uma oferta apenas porque a quota do período foi atingida.

### 12. Quota operacional não equivale a rejeição comercial

Uma oferta pode estar:

```text
elegível = true
filtros = PASS
score = 91
```

e ainda assim não ser publicada naquele dia porque:

```text
quota diária esgotada
```

Isso não deve gerar `DealEvaluation` rejeitada.

A razão pertence à camada operacional de seleção/publicação.

Deverá ser possível distinguir, para auditoria:

```text
REJECTED_BY_BUSINESS_RULE
```

de situações equivalentes a:

```text
NOT_SELECTED_DUE_TO_QUOTA
DEFERRED_DUE_TO_RECENCY
```

Os códigos concretos serão definidos quando o modelo operacional for implementado.

### 13. A política poderá evoluir para escopo por canal e destino

A versão inicial poderá aplicar uma política global caso isso seja suficiente para a v1.0.

Entretanto, a arquitetura não deverá impedir evolução para:

```text
ASIN
+
channel
+
destination
```

Exemplo:

```text
WhatsApp / Grupo A
última publicação = ontem

Telegram / Canal Principal
última publicação = nunca
```

A publicação no WhatsApp não precisa necessariamente impedir a publicação no Telegram.

Futuramente poderão existir configurações como:

```text
WhatsApp Grupo A:
preferredCooldownDays = 7

Telegram Canal Principal:
preferredCooldownDays = 5
```

Essa possibilidade deve ser preservada sem espalhar condicionais específicas de canal pela política central.

### 14. A FASE 13 não deverá absorver esta política dentro do PublicationGenerator

A ADR-0004 estabelece que a FASE 13 transforma uma `DealEvaluation` já selecionada em uma `Publication`.

Portanto:

```text
PublicationGenerator
```

não deverá decidir:

- quantas ofertas publicar no dia;
- qual oferta deve furar a fila;
- cooldown;
- frequência;
- canal;
- horário de envio.

O gerador continuará responsável por transformar uma seleção já realizada em conteúdo reproduzível.

A seleção operacional deverá permanecer separada.

### 15. Relação com a FASE 14

A interface operacional poderá apresentar dados úteis para decisão humana, como:

```text
score
última publicação
quantidade de publicações
estado atual
```

Também poderá futuramente exibir a prioridade calculada pela política.

Entretanto, a interface não deverá implementar a regra diretamente.

Fluxo correto:

```text
interface
    ↓
use case
    ↓
PublicationSelectionPolicy
```

Fluxo incorreto:

```text
interface
    ↓
if publicado há menos de 7 dias...
```

### 16. Relação com a FASE 17

A FASE 17 será responsável por:

- frequência dos ciclos;
- horários;
- execução contínua;
- prevenção de execuções concorrentes;
- pausa operacional.

A quota temporal deverá ser consultada pelo fluxo agendado antes de liberar novas publicações.

O scheduler não deverá recalcular score nem reproduzir regras do motor de decisão.

### 17. Relação com a FASE 18

A FASE 18 introduzirá outbox e worker de publicação.

A outbox deverá preservar a ordem e a identidade do trabalho selecionado.

O mecanismo deverá impedir que reinicializações ou concorrência façam a quota ser ultrapassada ou a mesma publicação ser enviada indevidamente duas vezes.

A aplicação da quota precisa ser consistente com:

```text
idempotência
concorrência
transação
estado persistido
```

A estratégia concreta deverá ser definida durante a implementação da FASE 18.

### 18. Relação com a FASE 19

Telegram e WhatsApp serão adapters de entrega.

Eles não deverão possuir regras como:

```text
if publicado nos últimos 7 dias...
```

O canal recebe uma publicação já autorizada para entrega.

Adaptação de formatação continua sendo responsabilidade do adapter.

Seleção, cooldown e quota pertencem à política operacional anterior ao canal.

### 19. A política deverá ser auditável

Para uma decisão operacional deverá ser possível responder:

```text
por que esta oferta foi selecionada?
por que esta oferta ficou para depois?
qual era a quota?
quantas vagas estavam disponíveis?
quando o ASIN foi publicado pela última vez?
qual score ele possuía?
qual versão da política foi usada?
qual canal/destino foi considerado?
```

Dados suficientes deverão permanecer persistidos ou reconstruíveis.

Não será aceitável depender somente do estado em memória do scheduler.

### 20. Estado persistido continuará sendo a fonte de verdade

Decisões de recorrência e quota não deverão depender apenas de contadores em memória.

Exemplo inadequado:

```text
int publicationsToday = 4;
```

como única fonte do limite diário.

Após reinicialização:

```text
publicationsToday = 0
```

poderia provocar excesso de publicações.

O PostgreSQL deverá fornecer o estado durável necessário para reconstruir:

```text
quantas publicações bem-sucedidas ocorreram no período
qual foi a última publicação de cada ASIN
quais trabalhos permanecem pendentes
```

A implementação concreta será definida junto às migrations e repositories correspondentes.

## Alternativas consideradas

### 1. Publicar sempre os maiores scores

Fluxo:

```text
ranking
    ↓
top N
    ↓
publicação
```

**Não adotada como política suficiente.**

Motivo:

- tende a repetir as mesmas ofertas enquanto elas permanecerem na página;
- confunde qualidade da oferta com momento apropriado de publicação;
- reduz variedade do canal.

### 2. Alterar o score com penalidade de recorrência

Exemplo:

```text
scoreOperacional =
    score
    - recurrencePenalty
```

**Não adotada.**

Motivo:

- altera a semântica do score;
- dificulta explicação histórica;
- mistura qualidade comercial com estratégia de comunicação;
- tornaria duas avaliações iguais diferentes apenas por contexto de publicação.

### 3. Excluir definitivamente uma oferta depois da primeira publicação

**Não adotada.**

Motivo:

- uma oferta pode permanecer relevante por vários dias;
- preço, desconto, momentum ou condições podem melhorar;
- republicação futura pode ser legítima;
- impediria campanhas recorrentes.

### 4. Cooldown rígido

**Mantida como possibilidade configurável**, especialmente para uma janela mínima contra repetição imediata.

Não será obrigatório que toda a política seja exclusivamente rígida.

### 5. Cooldown suave

**Mantida como possibilidade preferencial para priorização**, pois reduz repetição sem eliminar candidatos quando o volume de novidades for baixo.

### 6. Modelo híbrido

**Mantido como candidato forte para a versão inicial**, combinando:

```text
janela mínima rígida
+
janela preferencial suave
+
quota configurável
```

Os valores concretos deverão ser definidos por configuração e ajustados com métricas reais.

### 7. Limite apenas no adapter de WhatsApp/Telegram

**Não adotada.**

Motivo:

- duplicaria regra entre canais;
- faria cada adapter conhecer política de negócio operacional;
- dificultaria consistência entre canais;
- contrariaria a separação prevista na arquitetura.

### 8. Contador somente em memória

**Não adotada.**

Motivo:

- reinicialização perderia o estado;
- concorrência poderia ultrapassar a quota;
- não seria auditável;
- não suportaria execução contínua confiável.

## Configuração inicial candidata

Sem transformar estes valores em decisão imutável, a primeira configuração operacional poderá experimentar:

```text
policyVersion = PUBLICATION_SELECTION_V1

maxPublicationsPerDay = 7

hardCooldownDays = 2

preferredCooldownDays = 7
```

Ordenação conceitual:

```text
1. excluir temporariamente itens dentro do hard cooldown;
2. priorizar itens nunca publicados;
3. priorizar itens fora do preferred cooldown;
4. entre itens comparáveis, preferir publicação mais antiga;
5. utilizar score DESC;
6. aplicar desempate determinístico.
```

Esta configuração é uma hipótese operacional inicial.

Ela deverá ser validada com dados reais antes de ser tratada como política definitiva.

## Consequências

### Positivas

- score continua semanticamente puro;
- redução de repetição nos canais;
- maior variedade de produtos publicados;
- uso do histórico já existente;
- quota operacional explícita;
- cadência configurável;
- possibilidade de evolução por canal e destino;
- decisões reproduzíveis;
- maior auditabilidade;
- menor risco de spam por execução automática;
- scheduler e adapters permanecem desacoplados da regra de prioridade.

### Custos

Será necessário definir ou implementar:

- `PublicationSelectionPolicy`;
- configuração versionada;
- consulta eficiente à última publicação bem-sucedida;
- consulta de quantidade publicada por janela temporal;
- integração com scheduler;
- coordenação transacional com outbox;
- tratamento de concorrência;
- novos testes de recorrência;
- novos testes de quota;
- novos testes de reinicialização;
- novos testes de concorrência;
- métricas operacionais.

Poderão ser necessárias migrations adicionais.

As migrations já aplicadas não deverão ser alteradas retroativamente.

## Requisitos de teste

A implementação deverá possuir, no mínimo, cenários equivalentes a:

```text
nunca publicado
→ prioridade alta

publicado ontem
→ prioridade reduzida ou bloqueio temporário

publicado fora da janela preferencial
→ volta à prioridade normal

score alto + publicação recente
→ não domina automaticamente a fila

score menor + nunca publicado
→ pode ficar à frente

quota diária atingida
→ nenhuma nova publicação é liberada

reinício da aplicação
→ quota consumida continua conhecida

tentativa FAILED
→ não conta como publicação bem-sucedida

tentativa SUCCESS
→ atualiza histórico de recorrência

duas execuções concorrentes
→ não ultrapassam a quota

candidato não selecionado hoje
→ permanece disponível para ciclo futuro
```

Quando existir escopo por canal/destino, também deverão existir testes equivalentes a:

```text
publicado no WhatsApp
→ política do Telegram permanece independente quando configurada assim
```

## Observabilidade futura

A FASE 16 deverá fornecer base para métricas que permitam validar a política.

Indicadores úteis:

```text
candidatos elegíveis por ciclo
publicações liberadas
publicações adiadas por quota
publicações adiadas por recência
idade média desde última publicação
percentual de itens nunca publicados
quantidade de republicações
distribuição de mensagens por horário
falhas de envio
```

Esses dados poderão orientar mudanças futuras de:

```text
maxPublicationsPerDay
hardCooldownDays
preferredCooldownDays
cadência
```

A política não deverá ser sofisticada apenas por hipótese.

Primeiro medir, depois evoluir.

## Fora do escopo desta ADR

Não são definidos aqui:

- horários definitivos de publicação;
- número definitivo de publicações diárias;
- política de campanhas especiais;
- tratamento de ofertas relâmpago que possam furar fila;
- modelo final por canal/destino;
- algoritmo adaptativo baseado em CTR;
- otimização por conversão;
- aprendizado de máquina;
- mudanças no score;
- regras específicas de WhatsApp;
- regras específicas de Telegram.

Esses pontos exigirão evidência operacional ou decisão arquitetural própria.

## Critério de adoção

Esta ADR poderá mudar de `Proposta` para `Aceita` quando as fases de execução contínua e outbox formalizarem a implementação concreta e confirmarem:

```text
score independente
+
histórico de publicação
+
política versionada de seleção
+
quota configurável
+
cadência configurável
+
estado durável
+
idempotência
+
auditoria
```

como base do controle de recorrência das publicações.
