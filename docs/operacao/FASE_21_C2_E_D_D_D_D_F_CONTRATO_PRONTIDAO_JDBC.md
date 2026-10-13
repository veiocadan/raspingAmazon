# FASE 21-C2-E-D-D-D-D-F — Prontidao JDBC Java sem partida produtiva

## Objetivo

A D-E confirmou a barreira interna de `ContinuousProcessingMain` e
os 1.850 testes completos passaram antes do commit `fd4d6d6`.
O agendamento `amazon-deals` esta pausado e nao ha backlog.
Esta fase nao altera o plano de autorizacao nem inicia o daemon.

## Arquitetura

A entrada `ContinuousProcessingReadinessMain` e fisicamente separada
`ContinuousProcessingMain` e nunca chama `ContinuousProcessingBootstrap`,
`ContinuousProcessingApplication`, scheduler, worker, Flyway ou recovery.
Ambas compartilham a mesma leitura rigorosa da politica versionada em
`DaemonStartupPolicyGate`. O metodo de readiness so aceita
`preparation_only` com as quatro autorizacoes `false`; o metodo produtivo
continua exigindo `supervised` com autorizacao expressa para efeitos externos.

O script Windows D-F carrega somente a senha `rasping_runtime` de DPAPI,
fora do repositorio, e injeta apenas no ambiente da JVM filha. Nao usa
`rasping_migrator`. A JVM conecta usando `DatabaseConnection` e
`EnvironmentConfigProvider` historicos, sem novos defaults de credenciais.

A verificacao solicita `Connection.setReadOnly(true)` e transacao
`REPEATABLE_READ`, confirmando `current_setting('transaction_read_only')`
no servidor PostgreSQL. Consulta somente `current_user`, `current_database`,
identidade do agendamento pausado e contagens de `processing_job`,
`processing_run` e `publication_outbox`. Termina a transacao com `ROLLBACK`.
Nenhuma linha de negocio ou valor de credencial e exibido.

Os testes Java usam conexao falsa em memoria. A propria verificacao
JDBC real aplica as invariantes da D-D na mesma transacao somente leitura.
O script D-D nao e invocado durante o desenvolvimento porque exige Git
sem alteracoes rastreadas. A versao completa atualizada do D-D acompanha
este pacote para reconhecer tambem a referencia de metodo `Bootstrap::open`
introduzida na D-E, sem enfraquecer as demais verificacoes.

## Limites e medidas de seguranca

- Nao autoriza coleta Amazon, publicacao, recuperacao, migracao ou restart.
- Nao libera o supervisor Windows nem muda a configuracao de startup.
- Nao testa a partida real do `ContinuousProcessingBootstrap.open()`;
  testa o caminho JDBC real separado, em modo somente leitura.
- Erros Java exibem apenas o tipo da excecao; o script nao imprime
  stdout/stderr nao previstos, evitando exposicao de segredos.
- Um timeout nao mata processos automaticamente: requer inspecao.
- Nao repetir o script de criacao do agendamento da D-C.

Gate esperado: `C2-E-D-D-D-D-F GATE PASS`, seguido de `BLOQUEIO ATIVO`.
Sem commit ou push ate a revisao e execucao final da nova subetapa.
