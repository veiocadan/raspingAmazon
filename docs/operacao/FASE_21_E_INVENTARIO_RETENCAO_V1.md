# FASE 21-E — Inventário e classificação de retenção V1

## 1. Identificação

Projeto: Rasping Amazon.

Fase: 21-E — Retenção e classificação dos dados.

Data do levantamento inicial: 2026-10-15.

Branch: feat/fase-21-seguranca-governanca-v1.

Referências: ADR-0018, seção 10, e ADR-0011.

Estado: inventário inicial. Política operacional de logs e
diagnósticos ainda em definição e validação.

## 2. Objetivo

Classificar os dados do sistema, identificar sua finalidade,
estabelecer as garantias mínimas de conservação e impedir
exclusões destrutivas não autorizadas.

Retenção de dados, descarte de logs, arquivamento e backup são
responsabilidades diferentes e não deverão ser confundidas.

## 3. Princípios obrigatórios

1. PostgreSQL permanece como fonte de verdade durável.
2. Registros históricos de negócio não expiram automaticamente
   apenas em função de sua idade.
3. Eventos de auditoria não são descartados por uma política
   genérica de limpeza.
4. Estado operacional ativo não pode ser removido enquanto
   necessário à execução, recuperação ou idempotência.
5. Não haverá DELETE, TRUNCATE ou DROP genérico de produção.
6. Novas regras de expiração exigem justificativa, dependências
   analisadas, testes e decisão arquitetural explícita.
7. Configurações versionadas preservam versões históricas.
8. Logs estruturados não substituem registros persistidos.
9. Backups possuem política própria, tratada na FASE 21-F.
10. Não será feita alteração retroativa das migrations existentes.

## 4. Inventário das tabelas

A relação abaixo contempla as 29 tabelas declaradas pelas
migrations versionadas até a V39.

| Tabela | Classificação | Política inicial |
|---|---|---|
| `product` | Identidade comercial | Preservar |
| `offer_snapshot` | Histórico comercial | Preservar |
| `offer_payment_condition` | Evidência comercial | Preservar |
| `offer_payment_condition_method` | Evidência comercial | Preservar |
| `offer_evidence` | Evidência de origem | Preservar |
| `deal_evaluation` | Decisão comercial | Preservar |
| `deal_evaluation_rule_result` | Explicabilidade | Preservar |
| `deal_evaluation_score_factor` | Explicabilidade | Preservar |
| `deal_evaluation_momentum_audit` | Auditoria comercial | Preservar |
| `configuration_version` | Configuração versionada | Preservar |
| `filter_profile` | Configuração versionada | Preservar |
| `score_profile` | Configuração versionada | Preservar |
| `publication_selection_profile` | Configuração versionada | Preservar |
| `publication_quota_profile` | Configuração versionada | Preservar |
| `publication_cadence_profile` | Configuração versionada | Preservar |
| `processing_run` | Execução durável | Preservar |
| `deal_candidate` | Rastreabilidade de execução | Preservar |
| `processing_job` | Fila e recuperação duráveis | Preservar |
| `processing_schedule` | Estado operacional ativo | Preservar |
| `processing_job_reprocess_event` | Auditoria operacional | Preservar |
| `processing_schedule_change_audit` | Auditoria administrativa | Preservar |
| `publication` | Publicação histórica | Preservar |
| `publication_attempt` | Histórico de entrega | Preservar |
| `publication_outbox` | Fila e entrega duráveis | Preservar |
| `publication_selection_run` | Decisão operacional | Preservar |
| `publication_selection_decision` | Explicabilidade da seleção | Preservar |
| `publication_delivery_resolution_event` | Auditoria de resolução | Preservar |
| `publication_rate_limit_state` | Estado operacional ativo | Preservar |
| `integration_observation` | Observabilidade persistida | Preservar |

## 5. Regras por família

### 5.1. Dados comerciais

Produtos, snapshots, condições comerciais, evidências,
avaliações, resultados de regras, score e momentum compõem
o histórico necessário à explicabilidade.

Política inicial: conservação sem expiração automática.

### 5.2. Configurações versionadas

Perfis de filtro, score, seleção, quota, cadência e versões
de configuração preservam o histórico semântico.

Política inicial: conservar versões antigas e atuais.

A proteção de histórico introduzida na FASE 21-D permanece
vigente e não deverá ser contornada por mecanismos de retenção.

### 5.3. Orquestração

ProcessingRun, DealCandidate e ProcessingJob representam
execução, rastreabilidade, retry e recuperação.

ProcessingSchedule contém estado administrativo ativo.

Eventos de reprocessamento e mudanças administrativas são
registros necessários à auditoria.

Política inicial: conservação sem exclusão por idade.

### 5.4. Publicação

Publication, PublicationAttempt, outbox, decisões de seleção
e resoluções de entrega preservam o histórico da automação.

O tratamento de DELIVERY_UNKNOWN não será alterado.

O estado de rate limit continua necessário para o controle
operacional e não pode ser descartado indiscriminadamente.

Política inicial: conservação sem exclusão por idade.

### 5.5. Observações de integração

integration_observation contém fatos operacionais persistidos,
incluindo duração, resultado, falhas e correlações conhecidas.

Não é um arquivo temporário de log.

Política inicial: conservação sem exclusão automática.

## 6. Logs estruturados

O JsonStructuredOperationalLogAdapter emite eventos JSON Lines
por meio de um PrintWriter fornecido pela composição.

Os logs são complementares ao PostgreSQL e não constituem
a fonte de verdade dos fatos comerciais ou de auditoria.

Antes de fixar um prazo de conservação, será necessário:

- identificar os destinos efetivos das saídas;
- verificar rotação e limitação de armazenamento;
- definir prazo de conservação operacional;
- preservar diagnósticos necessários a incidentes abertos;
- validar ausência de segredos e dados sensíveis;
- comprovar o procedimento em ambiente isolado.

Prazo efetivo: pendente de decisão operacional e teste.

Não será declarada uma política de rotação como implementada
sem prova de que o destino utilizado realmente a aplica.

## 7. Diagnósticos temporários

Relatórios de investigação, saídas locais de testes e outros
artefatos temporários deverão possuir destino, responsável,
prazo, critérios de descarte e proteção de acesso documentados.

Nenhum script de exclusão automática será introduzido antes
da identificação de caminhos seguros e arquivos protegidos.

Prazo efetivo: pendente de decisão operacional e teste.

O descarte nunca deverá atingir código-fonte, evidências
duráveis, backups ou arquivos necessários à recuperação.

## 8. Backups

Os backups não pertencem à política de descarte dos logs.

Frequência, retenção, integridade, proteção, armazenamento
externo e restauração pertencem à FASE 21-F.

Nenhum backup será eliminado durante a FASE 21-E.

## 9. Metadados do Flyway

A tabela flyway_schema_history é administrada pelo Flyway.

Ela não está incluída nas 29 tabelas de negócio e operação
declaradas diretamente pelas migrations analisadas.

Seu histórico não será usado como alvo de limpeza.

## 10. Evidência do levantamento

Foi realizada uma busca estática nas migrations por
declarações CREATE TABLE.

Resultado inicial: 29 tabelas identificadas.

Foi realizada uma busca estática em src/main por:

- DELETE FROM;
- TRUNCATE;
- DROP TABLE.

Resultado: nenhuma ocorrência encontrada pelo padrão usado.

Esse resultado não prova ausência absoluta de operações
destrutivas em SQL dinâmico, scripts externos ou ferramentas
de administração.

O Git estava sem alterações locais pendentes no início
deste levantamento.

## 11. Pendências da FASE 21-E

- Confirmar a cobertura integral do inventário de tabelas.
- Verificar os destinos operacionais dos logs.
- Classificar os diagnósticos e artefatos temporários.
- Definir prazos explícitos e critérios de exceção.
- Documentar limites de armazenamento e rotação.
- Adicionar validações automatizadas ao contrato.
- Executar gates sem acesso destrutivo ao banco operacional.
- Consolidar a documentação final e registrar o commit.

## 12. Proibições neste estágio

Não executar:

- limpeza por idade nas tabelas históricas;
- TRUNCATE de tabelas operacionais;
- DROP de tabelas;
- alteração de migrations históricas;
- exclusão de backups;
- desativação de triggers de auditoria;
- inicialização do daemon para simular retenção.

A política atual é conservadora e não destrutiva.

## 13. Critério de fechamento

A FASE 21-E somente será considerada concluída após
o inventário, a política operacional de retenção de logs
e diagnósticos, os testes e as evidências serem aprovados.

O commit deverá consolidar a etapa na branch existente.
