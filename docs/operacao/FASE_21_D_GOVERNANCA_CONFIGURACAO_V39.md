# FASE 21-D — Governança de configuração e auditoria V39

## 1. Estado

Implementação V39 validada localmente em PostgreSQL 18.6 isolado.

Data da validação: 2026-10-15.

Branch: feat/fase-21-seguranca-governanca-v1.

Estado: gates locais específicos aprovados. Revisão final de governança,
documentação e consolidação do commit ainda pendentes.

Referência arquitetural: ADR-0018, seção 8.

## 2. Escopo implementado

### 2.1. Auditoria administrativa de agendamentos

A V39 corrige a função introduzida pela V38.

Uma alteração exclusivamente em next_run_at não produz evento de
mudança administrativa, mesmo com motivo declarado na transação.

Alterações efetivas de enabled e interval_ms continuam auditadas
na mesma transação da alteração original.

A V38 permanece inalterada.

### 2.2. Imutabilidade dos perfis históricos

As tabelas protegidas são:

- publication_selection_profile;
- publication_quota_profile;
- publication_cadence_profile.

O PostgreSQL rejeita modificações do conteúdo semântico, alteração
de identidade, reativação de versões históricas e exclusão.

A transição legítima de active=true para active=false permanece
autorizada e recebe metadados de desativação.

O histórico não pode ser sobrescrito para reutilizar uma versão antiga.

### 2.3. Rastreabilidade

Cada nova ativação pode registrar:

- activation_previous_version;
- activation_actor;
- activation_reference;
- activated_at.

Cada desativação pode registrar:

- deactivation_actor;
- deactivation_reference;
- deactivated_at.

Os sete campos foram confirmados nas três tabelas.

Quando não existe contexto administrativo declarado, a função usa
a identidade de sessão PostgreSQL e uma referência técnica à versão.

A referência técnica não equivale a uma justificativa humana.

Um ator declarado por variável de sessão não equivale a identidade
autenticada de uma pessoa.

Registros anteriores à V39 não recebem informações retrospectivas
inventadas. Seus novos campos podem permanecer nulos.

## 3. Compatibilidade com os testes anteriores

Seis classes de teste foram atualizadas para respeitar a nova
imutabilidade do histórico.

A limpeza dos cenários continua removendo os dados operacionais
transitórios pertinentes, mas não exclui versões históricas dos
perfis de seleção, quota e cadência.

Os testes que exigem confirmação transacional continuam utilizando
as fronteiras transacionais reais. Não foi introduzido rollback
artificial que enfraqueça a prova de persistência.

Os testes são executados em PostgreSQL isolado e descartável.
Perfis históricos criados por cenários confirmados permanecem
apenas até o descarte desse banco.

## 4. Evidências executadas

### 4.1. Flyway

Ambiente:

- PostgreSQL 18.6;
- banco rasping_21d_test;
- porta local 55439;
- container rasping-21d-pg;
- ambiente descartável separado do banco operacional.

Consulta executada:

SELECT version, success
FROM public.flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 1;

Resultado observado: 39|t.

### 4.2. Suíte completa Maven

Comando:

.\mvnw.cmd clean test

Resultado observado em 2026-10-15:

- Tests run: 1879;
- Failures: 0;
- Errors: 0;
- Skipped: 0;
- BUILD SUCCESS.

Esta execução utilizou o banco PostgreSQL isolado.

### 4.3. Testes com usuário restrito

Comando:

.\mvnw.cmd "-Dtest=JdbcConfigurationGovernanceV39Test,JdbcProcessingScheduleAdministrativeAuditTest" test

Identidade operacional: rasping_runtime.

Identidade de preparação/migração no ambiente descartável: rasping.

Resultado observado:

- Tests run: 6;
- Failures: 0;
- Errors: 0;
- Skipped: 0;
- BUILD SUCCESS.

A sessão PostgreSQL foi confirmada como:

rasping_runtime|rasping_runtime

### 4.4. Verificação de privilégios

Resultados observados para rasping_runtime:

- CREATE no schema public: negado;
- INSERT direto na auditoria de agendamentos: negado;
- DELETE nos perfis de seleção: negado;
- INSERT de novas versões de seleção: permitido.

Esses resultados validam os privilégios específicos do gate V39.
Não substituem uma verificação integral da matriz de privilégios
da FASE 21-C.

### 4.5. Metadados

Os sete campos de rastreabilidade foram encontrados em:

- publication_selection_profile: 7/7;
- publication_quota_profile: 7/7;
- publication_cadence_profile: 7/7.

Nenhum campo esperado estava ausente.

### 4.6. Triggers

Foram encontrados oito triggers relevantes, todos com
tgenabled = 'O':

- auditoria administrativa de processing_schedule;
- proteção append-only de processing_schedule_change_audit;
- proveniência e imutabilidade de seleção;
- proveniência e imutabilidade de quota;
- proveniência e imutabilidade de cadência.

## 5. Restrições preservadas

- ADR-0018 preservada;
- migrations anteriores não alteradas;
- arquitetura Java existente preservada;
- PostgreSQL mantido como autoridade durável;
- daemon não iniciado;
- banco operacional não modificado;
- preparation_only não alterado;
- nenhuma interface web introduzida;
- nenhum novo motor de negócio;
- nenhuma exclusão retroativa de histórico;
- nenhuma autenticação humana fictícia;
- nenhum commit ou push realizado nesta implementação até a revisão final.

## 6. Limitações e verificações pendentes

- Revalidar o conjunto de alterações antes do commit consolidado.
- Conferir o histórico de Git e a ausência de arquivos auxiliares.
- Confirmar que os testes antigos modificados preservam as
  verificações de comportamento originais.
- Revisar o contrato de auditoria administrativa e os fallbacks
  de ator e referência.
- Diferenciar o gate restrito V39 da matriz integral da FASE 21-C.
- Registrar o SHA do commit consolidado quando ele existir.
- Executar validações remotas de CI após publicação da branch.

A suíte completa foi executada com a identidade administrativa do
banco de teste. Os seis testes específicos foram repetidos com
identidade operacional restrita. Não foi comprovado que os 1879
testes completos funcionem sob a matriz restrita integral.

## 7. Critério para consolidação

A FASE 21-D será consolidada após:

1. revisão do diff completo;
2. ausência de falhas e erros nos gates exigidos;
3. validação do contrato de governança perante a ADR-0018;
4. registro de eventuais limitações;
5. commit único do bloco consolidado na branch vigente.

A FASE 21-E não será iniciada antes desse fechamento.
