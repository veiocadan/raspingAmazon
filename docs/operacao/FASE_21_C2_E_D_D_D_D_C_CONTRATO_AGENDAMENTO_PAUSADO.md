# FASE 21-C2-E-D-D-D-D-C — Agendamento inicial pausado

## Risco confirmado

A primeira inicializacao do `ContinuousProcessingBootstrap.open()` chama `EnsureProcessingScheduleUseCase.execute()`. Esse caso de uso cria `ProcessingSchedule` com `enabled=true` e `nextRunAt=now` quando nao existe registro persistido. O scheduler pode, portanto, iniciar uma coleta imediatamente. O inventario D-B demonstrou 0 schedules, 0 jobs, 0 runs e 0 outbox.

## Estrategia segura

Sem iniciar o daemon, provisionar uma unica linha `processing_schedule` com `schedule_key=amazon-deals`, `source_uri=https://www.amazon.com.br/deals`, `interval_ms=3600000` (PT1H), `enabled=false`, sem leases e sem historico. Isso faz o `saveIfAbsent` do bootstrap preservar o agendamento pausado. O banco e a aplicacao devem continuar bloqueados ate verificacoes posteriores.

O plano versionado `config/operacao/continuous-startup-policy.v1.json` deve continuar `execution_mode=preparation_only`, com as quatro autorizacoes `false`.

## Arquivos

- `FASE_21_C2_E_D_D_D_D_C_PRECHECK_AGENDAMENTO.ps1`: somente leitura: valida codigo, plano, Docker e repete D-B; so aprova se todas as contagens forem zero. **Executar primeiro.**
- `FASE_21_C2_E_D_D_D_D_C_CRIAR_AGENDAMENTO_PAUSADO.ps1`: **nao executar sem nova autorizacao**. Executa transacao PostgreSQL como `rasping_runtime`, atraves da senha DPAPI; verifica leitura posterior, preserva `.env` e nao inicia Java.
- `FASE_21_C2_E_D_D_D_D_C_INSERIR_AGENDAMENTO_PAUSADO.sql`: `INSERT` transacional de unica linha, com assercoes anteriores e posteriores e `COMMIT` apenas no fim.
- `FASE_21_C2_E_D_D_D_D_C_EXECUTAR_INSERCAO.sh`: executa SQL via senha recebida de stdin; nunca imprime segredos.

## Seguranca e limites

As verificacoes de identidade, ausencia de backlog e exclusividade da chave protegem a transacao; nao substituem isolamento operacional contra outros operadores ou comandos manuais concorrentes. O `ROLLBACK` ocorre automaticamente em caso de falha de SQL anterior ao `COMMIT`.

O procedimento de criacao e de uso do agendamento faz uma escrita deliberada, e nao e reversivel automaticamente: remocao exigiria exame de estado e decisao operacional explicita. Nao usar `DELETE` sem auditoria. Backups anteriores permanecem preservados.

Nenhum arquivo `.dpapi`, `.env` ou senha sera incluido no Git. Nao iniciar o daemon nem executar Flyway `migrate`. Commit unico apenas apos a etapa D completa.
