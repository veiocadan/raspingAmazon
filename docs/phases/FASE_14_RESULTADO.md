# FASE 14 — RESULTADO CONSOLIDADO

**Projeto:** Rasping Amazon  
**Fase:** 14 — Interface operacional não bloqueante  
**Data:** 25/09/2026  
**Status local:** CONCLUÍDA — implementação, CLI, composição, execução real e gate local aprovados  
**Status remoto:** PENDENTE — branch local ainda não publicada/validada remotamente após os commits finais

---

## 1. Objetivo

A FASE 14 introduziu uma interface operacional Java para observação e diagnóstico do pipeline do Rasping Amazon sem transformar o operador em parte obrigatória da automação.

A decisão arquitetural central da fase é:

```text
A interface observa e administra o pipeline.
A interface não autoriza o pipeline a funcionar.
```

A interface não implementa aprovação humana obrigatória.

O pipeline deve continuar evoluindo para operação automática:

```text
coleta
  ↓
parsing
  ↓
enriquecimento
  ↓
elegibilidade
  ↓
filtros
  ↓
score / ranking
  ↓
seleção automática futura
  ↓
geração de Publication
  ↓
outbox / entrega futura
  ↓
canal
```

A FASE 14 observa o estado persistido desse fluxo por contratos próprios da camada `application`.

---

## 2. Decisão arquitetural vigente

O roadmap revisado original atribuía à interface responsabilidades de revisão e seleção manual.

Durante a implementação da FASE 14, essa interpretação foi refinada formalmente pela:

```text
ADR-0009 — Interface operacional não bloqueante
```

Status:

```text
ACEITA
```

A ADR-0009 estabelece que:

- a interface não pertence ao caminho crítico da publicação;
- aprovação humana não é requisito para publicação;
- estados de `Publication` representam ciclo automatizado, não aprovação manual;
- a primeira interface é CLI;
- a apresentação não acessa JDBC;
- consultas operacionais possuem contratos próprios na `application`;
- decisões históricas não são recalculadas pela interface;
- pause/resume não será simulado antes do scheduler real;
- retry só poderá existir quando houver semântica já definida;
- uma futura interface web poderá reutilizar os mesmos casos de uso.

Portanto, a divergência em relação ao texto original do roadmap é deliberada e documentada, não uma falta de implementação.

---

## 3. ADR prospectiva de recorrência e cadência

Durante o encerramento da FASE 14 foi incorporada como referência prospectiva:

```text
ADR-0010 — Política de seleção, recorrência e cadência de publicações
```

Status:

```text
PROPOSTA
```

Fases principais relacionadas:

```text
FASE 17 — Agendamento e execução contínua
FASE 18 — Contrato de canais e outbox de publicação
```

A ADR-0010 preserva para a FASE 14 a seguinte fronteira:

```text
interface
    ↓
use case
    ↓
PublicationSelectionPolicy
```

e proíbe a alternativa:

```text
interface
    ↓
if publicado há menos de X dias...
```

A política futura de recorrência, cooldown, quota e cadência não pertence à CLI.

---

## 4. Read side operacional de avaliações

Foi criado um conjunto explícito de contratos de leitura em:

```text
application/operation/evaluation
```

Componentes principais:

```text
DealEvaluationCursor
DealEvaluationPage
DealEvaluationSearchCriteria
DealEvaluationSummary
DealEvaluationDetail
OperationalEvaluationRuleResult
OperationalScoreFactorResult
OperationalMomentumAudit
ListDealEvaluationsUseCase
GetDealEvaluationDetailUseCase
DealEvaluationOperationalQueryPort
DealEvaluationOperationalDetailQueryPort
```

A interface consegue consultar avaliações persistidas sem reconstruir ou recalcular o agregado de decisão.

---

## 5. Listagem operacional de avaliações

A consulta suporta filtros por:

```text
eligible
ASIN
minScore
maxScore
evaluatedFrom
evaluatedUntil
cursor
limit
```

A paginação é keyset:

```text
evaluatedAt DESC
evaluationId DESC
```

Não utiliza `OFFSET`.

Limites:

```text
default = 50
max = 200
```

A consulta retorna fatos persistidos, sem recalcular:

```text
elegibilidade
filtros
score
momentum
```

---

## 6. Detalhe auditável de avaliação

O detalhe operacional permite inspecionar:

```text
identidade da avaliação
produto / ASIN
preços
rating
review count
seller
delivery
source
eligibility policy version
filter profile version
score version
momentum version
resultados persistidos das regras
fatores persistidos do score
auditoria persistida de momentum
```

As regras e fatores são apresentados em ordem persistida.

A interface não executa novamente:

```text
AmazonEligibilityValidator
CommercialFilter
ScoreEngine
MomentumEngine
```

---

## 7. Consultas operacionais de ProcessingRun

Foi criado read side explícito para:

```text
ProcessingRun
```

Componentes:

```text
ProcessingRunCursor
ProcessingRunSearchCriteria
ProcessingRunSummary
ProcessingRunPage
ProcessingRunOperationalQueryPort
ListProcessingRunsUseCase
JdbcProcessingRunOperationalQueryAdapter
```

A ordenação operacional é:

```text
requestedAt DESC
runId DESC
```

A CLI expõe:

```text
runs list
```

---

## 8. Consultas operacionais de ProcessingJob

Foi criado read side explícito para:

```text
ProcessingJob
```

Filtros suportados incluem:

```text
type
status
lastFailureType
processingRunId
dealCandidateId
offerSnapshotId
createdFrom
createdUntil
cursor
limit
```

A ordenação é:

```text
createdAt DESC
jobId DESC
```

A CLI expõe:

```text
jobs list
```

O filtro `processingRunId` representa apenas o vínculo direto persistido no job.

Ele não é apresentado como rastreamento completo de linhagem de todos os jobs descendentes de um run.

Os tipos atuais continuam:

```text
COLLECT_DEALS
ENRICH_DEAL
EVALUATE_DEAL
```

A FASE 14 não inventa um `PUBLISH` dentro da fila da FASE 12.

---

## 9. Consultas operacionais de Publication

Foi criado read side de publicação em:

```text
application/operation/publication
```

A listagem suporta:

```text
status
ASIN
dealEvaluationId
createdFrom
createdUntil
cursor
limit
```

A paginação utiliza:

```text
createdAt DESC
publicationId DESC
```

O resumo não carrega desnecessariamente o texto completo da publicação.

O detalhe carrega:

```text
metadados
generatedText
affiliateUrl
```

A CLI expõe:

```text
publications list
publications show <publication-id>
```

---

## 10. Migrations e índices operacionais

A FASE 14 introduziu migrations somente para suporte eficiente ao read side operacional:

```text
V17__operational_deal_evaluation_read.sql
V18__operational_processing_read_indexes.sql
V19__operational_publication_read_indexes.sql
```

Essas migrations adicionam índices de consulta.

Elas não introduzem:

```text
aprovação humana
estado de seleção manual obrigatório
estado artificial de pause
fila de canal
scheduler
```

As migrations:

```text
V15
V16
```

pertencem à evolução anterior de filtro/score e não são atribuídas semanticamente à interface operacional.

O catálogo atual do projeto alcança:

```text
V19
```

Nenhuma migration já aplicada foi editada retroativamente.

---

## 11. Adapters JDBC operacionais

Foram implementados adapters de leitura dedicados:

```text
JdbcDealEvaluationOperationalQueryAdapter
JdbcDealEvaluationOperationalDetailQueryAdapter
JdbcProcessingRunOperationalQueryAdapter
JdbcProcessingJobOperationalQueryAdapter
JdbcPublicationOperationalQueryAdapter
JdbcPublicationOperationalDetailQueryAdapter
```

Os adapters:

- usam SQL parametrizado;
- aplicam paginação keyset;
- retornam read models operacionais;
- encapsulam JDBC na infraestrutura;
- não movem regras comerciais para SQL ou apresentação.

---

## 12. Composition root operacional

Foi criado:

```text
OperationalInterfaceComposition
```

A composição abre uma `Connection` compartilhada para os adapters operacionais e expõe somente casos de uso:

```text
listDealEvaluations()
getDealEvaluationDetail()
listProcessingRuns()
listProcessingJobs()
listPublications()
getPublicationDetail()
```

A composição é dona da conexão e implementa `AutoCloseable`.

Ela não contém:

```text
regra comercial
template
score
momentum
retry
scheduler
transição de Publication
```

---

## 13. CLI operacional

A primeira interface da FASE 14 foi implementada em:

```text
com.raspingamazon.presentation.cli
```

Núcleo:

```text
CliExitCode
CliUsageException
CliCommandHandler
CliText
CliValueParser
OperationalCliUsage
OperationalCli
OperationalCliFactory
```

Recursos disponíveis:

```text
evaluations list
evaluations show <evaluation-id>

runs list

jobs list

publications list
publications show <publication-id>
```

Códigos de saída definidos:

```text
0 SUCCESS
1 OPERATIONAL_ERROR
2 USAGE_ERROR
3 NOT_FOUND
```

O motor `OperationalCli` retorna o código e não chama `System.exit()`.

---

## 14. Separação da apresentação

A camada:

```text
presentation
```

não conhece:

```text
Connection
DriverManager
SQL
adapters JDBC
HTML Amazon
collector
parser
```

Também não recalcula:

```text
eligibilidade
filtros
score
ranking
momentum
template
link de associado
```

A factory da CLI recebe somente casos de uso da camada `application`.

---

## 15. Bootstrap e entrypoint

Foi criado o bootstrap externo:

```text
com.raspingamazon.infrastructure.bootstrap
```

Componentes:

```text
OperationalCliBootstrap
OperationalCliMain
```

Fluxo real:

```text
OperationalCliMain
        ↓
OperationalCliBootstrap
        ↓
OperationalInterfaceComposition
        ↓
application use cases
        ↓
OperationalCliFactory
        ↓
CLI
```

Ajuda global e comandos top-level inválidos não precisam abrir PostgreSQL.

Comandos operacionais reais abrem a composição e fecham a conexão ao término da execução.

`System.exit()` fica restrito ao entrypoint de processo.

---

## 16. Execução via Maven

Foi adicionado:

```text
exec-maven-plugin
```

Uso local:

```powershell
.\mvnw.cmd compile exec:java "-Dexec.args=help"
```

Exemplo com banco:

```powershell
.\mvnw.cmd compile exec:java "-Dexec.args=evaluations list --limit 5"
```

O plugin não foi associado automaticamente ao lifecycle padrão.

Assim:

```powershell
.\mvnw.cmd clean test
```

continua sendo o gate normal do projeto.

A FASE 14 não introduziu fat-JAR ou pacote final de distribuição.

Isso permanece como preocupação de hardening/distribuição posterior.

---

## 17. Smoke tests reais

Foram validados localmente:

### Ajuda sem banco

```text
mvn compile exec:java -Dexec.args=help
→ ajuda exibida
→ BUILD SUCCESS
```

### Consulta real ao PostgreSQL

```text
mvn compile exec:java -Dexec.args=evaluations list --limit 5
→ composição operacional aberta
→ PostgreSQL consultado
→ cabeçalho operacional exibido
→ BUILD SUCCESS
```

O banco utilizado não possuía linhas correspondentes no momento do smoke test, portanto apenas o cabeçalho foi retornado.

Esse resultado ainda valida o caminho real:

```text
CLI
↓
bootstrap
↓
composition
↓
JDBC
↓
PostgreSQL
↓
application
↓
presentation
```

---

## 18. Gate de testes

Gate final local:

```text
Tests run: 820
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

Evolução observada durante a implementação da FASE 14:

```text
baseline inicial da leitura operacional = 619

14.2  → 639
14.3  → 647
14.4  → 660
14.5A → 678
14.5B → 699
14.6A → 719
14.6B → 732
14.7  → 737
14.8A → 748
14.8B → 760
14.8C → 772
14.8D → 778
14.8E → 790
14.8F → 802
14.8G → 809
14.8H → 820
```

A contagem representa a suíte completa em cada gate observado.

---

## 19. Critérios de aceite da ADR-0009

| Critério | Resultado |
|---|---|
| Pipeline pode funcionar sem a interface | CONCLUÍDO |
| Publicação normal não depende de aprovação manual | CONCLUÍDO |
| Interface usa a camada `application` | CONCLUÍDO |
| JDBC ausente da apresentação | CONCLUÍDO |
| Regra comercial ausente da apresentação | CONCLUÍDO |
| Consultas possuem contratos operacionais próprios | CONCLUÍDO |
| Operação inicial disponível integralmente por CLI | CONCLUÍDO |
| Arquitetura permite futura interface web sobre os mesmos contratos | CONCLUÍDO |
| Comandos administrativos não inventam semântica de retry/pause | CONCLUÍDO |
| Estados de `Publication` não são tratados como aprovação humana obrigatória | CONCLUÍDO |

Resultado:

```text
10 / 10 critérios arquiteturais atendidos localmente
```

---

## 20. Relação com o roadmap original

O roadmap original da interface previa itens como:

```text
revisão
seleção
configuração
geração
liberação
```

A implementação final não coloca esses itens como gate humano.

Essa mudança foi intencional.

A ADR-0009 substitui a interpretação de:

```text
score
↓
operador
↓
publicação
```

por:

```text
score / ranking
↓
seleção automática futura
↓
geração
↓
entrega futura
```

com a interface posicionada ao lado do pipeline.

Portanto, não implementar aprovação manual obrigatória é requisito arquitetural, não pendência.

---

## 21. O que NÃO foi implementado

Permanecem fora da FASE 14:

```text
aprovação humana obrigatória
scheduler definitivo
pause/resume real
retry arbitrário pela CLI
PublicationSelectionPolicy
cooldown de publicação
quota diária
cadência
outbox
worker de canal
Telegram
WhatsApp
interface web
autenticação/autorização web
dashboard avançado
métricas avançadas
alertas
fat-JAR/distribuição final
```

Também não foi implementada lógica de recorrência da ADR-0010.

A ADR-0010 permanece proposta para fases posteriores.

---

## 22. Débitos conhecidos para a FASE 15

### 22.1 Pré-condição de banco nos testes JDBC

Durante validação em um segundo computador, alguns testes JDBC inicialmente falharam porque a tabela:

```text
processing_run
```

ainda não existia naquele schema.

Após aplicar/normalizar as migrations, o gate completo passou:

```text
820 / 0 / 0 / 0
```

Isso não demonstrou regressão da FASE 14.

Entretanto, a FASE 15 deverá revisar se os testes integrados podem tornar a preparação de schema mais autocontida e menos dependente da ordem/pré-condição do ambiente externo.

### 22.2 Validação remota

Os commits finais da FASE 14 permanecem locais até o fechamento documental.

Antes de declarar encerramento remoto definitivo:

```text
push da branch
+
CI remoto verde
```

devem ser confirmados.

### 22.3 Distribuição da CLI

A execução Maven é suficiente para operação e validação local da FASE 14.

Empacotamento de distribuição final não foi tratado como requisito desta fase.

---

## 23. Documentação de fechamento

Documentos arquiteturais relevantes:

```text
docs/adr/0001-semantica-filtros-comerciais-e-apresentacao-pagamentos.md
docs/adr/0002-semantica-score-ranking-explicabilidade.md
docs/adr/0003-semantica-historico-e-momentum.md
docs/adr/0004-semantica-geracao-publicacao-e-link-associado.md
docs/adr/0005-semantica-desconto-preco-base-e-preco-efetivo.md
docs/adr/0006-semantica-score-v2-desconto-preco-base.md
docs/adr/0007-fallback-rating-review-pagina-produto.md
docs/adr/0008-correcao-versionada-link-associado-percent-encoding.md
docs/adr/0009-interface-operacional-nao-bloqueante.md
docs/adr/0010-politica-selecao-recorrencia-cadencia-publicacoes.md
```

A ADR-0009 está aceita e é normativa para a FASE 14.

A ADR-0010 permanece proposta e funciona como fronteira arquitetural para seleção operacional futura.

---

## 24. Commits finais observados

Checkpoint local da FASE 14:

```text
70e70e5 feat: add operational read model foundation
9d0f864 feat: add operational publication queries and composition
ab06fc1 Fase 14 parcial - indo pra MS
bae5635 feat: add operational publication detail command
4e2b7f3 feat: add operational run and job commands
dbe1c00 feat: add operational evaluation detail command
555925d feat: wire operational cli bootstrap
0d8284d build: add operational cli execution
```

O commit documental de encerramento ainda deverá ser criado.

---

## 25. Estado final local

```text
FASE 14 — Interface operacional não bloqueante

STATUS LOCAL:
CONCLUÍDA

Interface:
CLI JAVA

Evaluations list:
OK

Evaluations detail:
OK

Processing runs:
OK

Processing jobs:
OK

Publications list:
OK

Publications detail:
OK

JDBC na presentation:
NÃO

Regras comerciais na presentation:
NÃO

Aprovação humana obrigatória:
NÃO

Bootstrap real:
OK

PostgreSQL real:
SMOKE TEST OK

Migrations da fase:
V17
V18
V19

Catálogo de migrations:
ATÉ V19

Testes:
820

Falhas:
0

Erros:
0

Ignorados:
0

Build:
SUCCESS

ADR principal:
ADR-0009 — ACEITA

ADR prospectiva:
ADR-0010 — PROPOSTA

Próximo gate:
DOCUMENTAÇÃO FINAL
+
PUSH
+
CI REMOTO VERDE
```

---

## 26. Próxima fase

Depois do fechamento documental, push da branch e CI remoto verde:

```text
FASE 15 — Qualidade integrada
```

A FASE 15 deverá fortalecer a validação transversal do sistema já construído sem reabrir responsabilidades de domínio encerradas nas fases anteriores.

Prioridades já identificadas:

```text
testes integrados mais autocontidos
preparação/migração de schema nos testes
gates de regressão ponta a ponta
qualidade transversal da CLI operacional
validação conjunta processamento + publicação + leitura operacional
```

Nenhuma responsabilidade da FASE 15 deve ser antecipada dentro do encerramento da FASE 14.
