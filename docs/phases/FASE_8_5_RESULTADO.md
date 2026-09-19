# FASE 8.5 — RESULTADO CONSOLIDADO

**Projeto:** Rasping Amazon  
**Fase:** 8.5 — Consolidação do núcleo e preparação dos dados de decisão  
**Data:** 19/09/2026  
**Status:** CONCLUÍDA LOCALMENTE — validação do CI remoto pendente antes da abertura formal da FASE 9

---

## 1. Objetivo

A FASE 8.5 foi criada a partir da auditoria técnica realizada após a FASE 8.

O objetivo foi consolidar o núcleo existente antes do início do motor de filtros da FASE 9, sem reescrever a arquitetura e sem antecipar responsabilidades futuras.

A consolidação concentrou-se em:

- contratos de enriquecimento;
- provenance e auditabilidade;
- versionamento da avaliação;
- dados necessários para decisão;
- fluxo vertical transacional;
- idempotência;
- concorrência;
- isolamento de testes externos;
- CI;
- redução de fixtures;
- Maven Wrapper;
- higiene do repositório;
- padronização de encoding e EOL.

---

## 2. Princípios preservados

A FASE 8.5 preservou as decisões arquiteturais centrais do projeto:

- Java como núcleo;
- PostgreSQL como estado operacional principal;
- Flyway para migrations versionadas;
- JDBC explícito;
- domínio independente de HTML, HTTP, JDBC e PostgreSQL;
- separação entre `domain`, `application` e `infrastructure`;
- migrations aplicadas permanecem imutáveis;
- histórico por snapshots;
- seller e delivery tratados com política fail closed;
- interfaces externas encapsuladas em adapters;
- idempotência como requisito do processamento;
- rastreabilidade das decisões;
- evolução incremental por fases.

A arquitetura permaneceu em um único módulo Maven, conforme recomendação da auditoria para o estágio atual.

---

## 3. Contratos de enriquecimento

O contrato de enriquecimento foi consolidado para evitar ambiguidades causadas por múltiplos valores textuais posicionais.

Seller e delivery permaneceram conceitos independentes.

O enriquecimento continua representado por:

```text
ParsedDeal
    ↓
ProductEnrichmentClient
    ↓
AmazonProductPageEnrichmentClient
    ↓
AmazonProductPageParser
    ↓
ProductEnrichmentResult
```

A evidência utilizada na decisão deixou de depender apenas dos valores finais normalizados e passou a possuir rastreabilidade explícita.

---

## 4. Provenance e auditabilidade

Foi introduzida persistência dedicada de evidências da oferta.

A estrutura passou a registrar, conforme aplicável:

```text
evidence_type
raw_value
normalized_value
source_adapter
source_component
observed_at
```

Seller e delivery passam a possuir evidência auditável associada ao `OfferSnapshot`.

Isso permite reconstruir por que uma oferta foi classificada de determinada forma sem depender novamente da página externa.

---

## 5. Versionamento da avaliação

A FASE 8 utilizava `filter_version` para uma política que, semanticamente, ainda não correspondia ao futuro motor de filtros da FASE 9.

A FASE 8.5 separou os conceitos necessários para evitar colisão semântica entre:

```text
policy version
filter version
score version
```

A persistência da avaliação também foi evoluída para permitir rastrear resultados individuais de regras.

A estrutura de avaliação passou a suportar resultados filhos versionados, preservando a decisão final e a explicação das regras aplicadas.

---

## 6. Evolução do schema

O schema PostgreSQL evoluiu exclusivamente por novas migrations.

Estado validado ao final da fase:

```text
PostgreSQL: 18.6
Flyway: OK
Migrations validadas: 6
Schema: versão 6
Migration pendente: não
```

As migrations anteriores não foram editadas retroativamente.

---

## 7. Dados da oferta para decisão

O fluxo foi consolidado para transportar e persistir os dados necessários às próximas fases.

Entre os dados atualmente protegidos por contratos e testes estão:

```text
currentPrice
basisPrice
previousPrice
soldPercentage
rating
reviewCount
seller
delivery
paymentConditions
```

A separação semântica entre `basisPrice` e `previousPrice` permanece preservada.

Valores ausentes não são inventados.

---

## 8. Fluxo vertical de processamento

Foi implementado um caso de uso vertical síncrono que coordena:

```text
CollectionRequest
        ↓
coleta
        ↓
parsing
        ↓
enriquecimento
        ↓
Product
        ↓
OfferSnapshot
        ↓
PaymentConditions
        ↓
OfferEvidence
        ↓
DealEvaluation
        ↓
PostgreSQL
```

A composição de produção compartilha a mesma `Connection` entre os adapters JDBC participantes da unidade de trabalho.

O fluxo vertical é exercitado por teste ponta a ponta hermético, usando servidor HTTP local e fixtures mínimas, sem acesso à Amazon real.

---

## 9. Transação

A fronteira transacional foi consolidada em `JdbcTransactionAdapter`.

Foram definidos dois modos de ownership:

### Connection com `autoCommit=true`

O adapter é proprietário da transação:

```text
setAutoCommit(false)
        ↓
operação
        ↓
commit em sucesso
ou
rollback em falha
        ↓
restauração de autoCommit
```

### Connection com `autoCommit=false`

A transação pertence ao chamador.

Nesse caso o adapter:

```text
cria Savepoint
        ↓
executa trabalho interno
        ↓
não executa commit da transação externa
        ↓
rollback até o Savepoint em falha
```

Assim, uma unidade de trabalho interna não confirma nem desfaz indevidamente trabalho pertencente ao chamador.

Falhas secundárias de rollback/restauração são preservadas como exceções suprimidas quando existe uma falha principal.

---

## 10. Idempotência

A persistência de `Product` passou a utilizar operação atômica com PostgreSQL:

```sql
INSERT ...
ON CONFLICT (asin)
DO UPDATE ...
RETURNING id
```

A identidade de uma observação de oferta foi fechada por:

```text
product_id
+
collected_at
+
source
```

Uma restrição única protege essa identidade no banco.

O fluxo diferencia:

```text
snapshot criado
```

de:

```text
snapshot já existente
```

Somente quando a observação é nova são persistidos novamente os estados dependentes correspondentes, evitando duplicação imprópria de evidências, condições de pagamento e avaliação.

Também foram adicionados testes de idempotência e concorrência reais contra PostgreSQL.

---

## 11. Teste ponta a ponta

Foi criado:

```text
AmazonDealProcessingEndToEndTest
```

O teste usa:

```text
fixture Deals
        ↓
servidor HTTP local
        ↓
coletor real da aplicação
        ↓
parser real
        ↓
fixture de produto
        ↓
enrichment real
        ↓
persistência JDBC real
        ↓
avaliação real
        ↓
PostgreSQL real
```

O mesmo evento é processado duas vezes com instante fixo.

O teste confirma que a oferta é processada, o produto é persistido, existe somente um snapshot para a mesma observação, seller e delivery são persistidos, as evidências são persistidas, a avaliação é persistida, a decisão permanece estável no reprocessamento e o estado dependente não é duplicado indevidamente.

Commit de referência:

```text
644989e FASE 8.5: valida fluxo ponta a ponta e idempotencia
```

---

## 12. Ownership transacional

O comportamento do adapter JDBC foi endurecido com testes que comprovam:

- commit quando o adapter é proprietário da transação;
- rollback total quando a transação própria falha;
- ausência de commit indevido quando o chamador possui a transação;
- rollback apenas do trabalho interno por Savepoint;
- preservação do trabalho externo anterior ao Savepoint.

Commit de referência:

```text
770a2d2 FASE 8.5: corrige ownership de transacoes JDBC
```

---

## 13. Testes externos

O acesso real à Amazon foi removido da suíte JUnit hermética padrão.

A probe externa passou a existir separadamente:

```text
AmazonDealsExternalProbeIT
```

Execução:

```text
./mvnw --batch-mode -Pexternal-probe test
```

Artefatos diagnósticos permanecem em:

```text
target/diagnostics/
```

A indisponibilidade externa não interfere em:

```text
./mvnw clean test
```

---

## 14. CI

Foram criados workflows separados:

```text
.github/workflows/ci.yml
.github/workflows/amazon-source-probe.yml
```

### CI hermético

Configurado com:

```text
JDK 25
PostgreSQL 18.6
Maven Wrapper
./mvnw --batch-mode clean test
```

### Probe externa

Configurada separadamente para execução manual/agendada com:

```text
./mvnw --batch-mode -Pexternal-probe test
```

O CI remoto ainda precisa ser validado após o primeiro `push` deste fechamento.

---

## 15. Maven Wrapper

O Maven Wrapper foi adicionado ao repositório.

Versões configuradas:

```text
Maven Wrapper: 3.3.4
Maven: 3.9.16
```

O build local e os workflows passaram a utilizar o wrapper versionado.

---

## 16. Fixtures

As capturas HTML completas foram substituídas por fixtures mínimas.

Estrutura atual relevante:

```text
src/test/resources/amazon/fixtures/
├── deals/
│   ├── basic-deal.html
│   ├── duplicate-deal.html
│   ├── end-to-end-deal.html
│   └── invalid-deal.html
└── product/
    ├── amazon-amazon.html
    ├── amazon-global.html
    ├── thirdparty-amazon.html
    └── thirdparty-thirdparty.html
```

Foram adicionados testes específicos para oferta básica, deduplicação de ofertas equivalentes e descarte de oferta inválida.

Capturas completas não fazem parte da dependência normal da suíte.

---

## 17. Higiene do repositório

Foram realizados os ajustes recomendados pela auditoria:

- Maven Wrapper versionado;
- `.editorconfig`;
- `.gitattributes`;
- normalização de EOL;
- `.idea/workspace.xml` removido do versionamento;
- PostgreSQL fixado em `18.6` no CI e Compose;
- workflows migrados para Maven Wrapper;
- tokens internos `filecite` removidos dos documentos versionados;
- mojibake de `FASE_4_RESULTADO.md` corrigido para UTF-8;
- fixtures HTML reduzidas.

Commit de referência:

```text
eda74fa FASE 8.5-G: fecha qualidade e higiene do repositorio
```

---

## 18. Validação final local

A suíte completa mais recente executou:

```text
Tests run: 233
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

Também foi validado:

```text
PostgreSQL: 18.6
Flyway: OK
Migrations: 6
Schema: versão 6
```

O teste ponta a ponta e os testes de ownership transacional também foram executados isoladamente com sucesso.

---

## 19. Critério de saída da FASE 8.5

O critério de saída definido pela auditoria foi atendido localmente:

> Dada uma fixture de Deals e fixtures de produto, o sistema processa uma oferta ponta a ponta, persiste estado auditável e reproduz a mesma decisão sem duplicação imprópria de estado.

| Critério | Resultado |
|---|---|
| Contratos de enriquecimento corrigidos | CONCLUÍDO |
| Seller/delivery com provenance | CONCLUÍDO |
| Versionamento de avaliação separado | CONCLUÍDO |
| Dados para decisão consolidados | CONCLUÍDO |
| Fluxo vertical | CONCLUÍDO |
| Transação ponta a ponta | CONCLUÍDO |
| Ownership de transação externa | CONCLUÍDO |
| Product upsert atômico | CONCLUÍDO |
| Chave de idempotência de snapshot | CONCLUÍDO |
| Teste de concorrência | CONCLUÍDO |
| Teste E2E | CONCLUÍDO |
| Testes externos separados | CONCLUÍDO |
| CI configurado | CONCLUÍDO |
| Maven Wrapper | CONCLUÍDO |
| Fixtures mínimas | CONCLUÍDO |
| Higiene do repositório | CONCLUÍDO |
| Suíte local completa | 233 / PASSOU |
| CI remoto após push | PENDENTE DE VALIDAÇÃO |

---

## 20. Limite deste resultado

A FASE 8.5 não implementa o motor de filtros da FASE 9.

Também permanecem para fases posteriores:

- score;
- ranking;
- momentum;
- execução recorrente;
- interface operacional;
- geração de publicação;
- canais;
- observabilidade completa;
- resiliência de produção;
- segurança e governança de produção;
- mecanismos de escala guiados por métricas.

A estratégia de uso automatizado das fontes Amazon continua sendo um gate de produção e deve priorizar interfaces oficiais quando aplicáveis.

---

## 21. Próxima fase

Após o `push` e a confirmação de que o workflow `CI` está verde:

```text
FASE 9 — Motor de filtros configuráveis
```

A FASE 9 deverá utilizar os contratos, dados, versionamento e auditabilidade consolidados nesta fase, sem misturar filtros configuráveis com a política estrutural Amazon de seller/delivery.

---

## 22. Registro de encerramento local

```text
FASE 8.5
STATUS LOCAL: CONCLUÍDA
STATUS REMOTO: AGUARDANDO PRIMEIRA VALIDAÇÃO DO CI

Testes: 233
Falhas: 0
Erros: 0
Ignorados: 0
Build: SUCCESS

PostgreSQL: 18.6
Flyway: OK
Migrations: 6
Schema: versão 6

Commits finais:
644989e  fluxo ponta a ponta e idempotência
770a2d2  ownership de transações JDBC
eda74fa  qualidade e higiene do repositório

Próximo gate:
push + CI verde

Próxima fase após o gate:
FASE 9 — Motor de filtros configuráveis
```
