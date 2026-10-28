# FASE 21-C2-E-D-D-D-D-D — Gate de bootstrap com agendamento pausado

## Estado anterior confirmado

Na D-C, um agendamento `amazon-deals` foi criado no PostgreSQL com `enabled=false`, `interval_ms=3600000` e fonte `https://www.amazon.com.br/deals`. O inventario D-B posterior demonstrou `schedules_total=1`, `schedules_enabled=0` e nenhum job, run ou outbox.

## Objetivo deste gate

Antes de qualquer partida real, comprovar:

1. A politica continua `preparation_only`, sem autorizar daemon, migracao, efeitos externos ou reinicio automatico.
2. Nao existe daemon produtivo nem registro ativo de supervisao.
3. Os defaults de identidade Java (`amazon-deals` e a fonte Amazon) nao foram sobrepostos pelo processo ou `.env`.
4. `EnsureProcessingScheduleUseCase` preserva o estado retornado pela porta de persistencia; `JdbcProcessingScheduleAdapter.saveIfAbsent()` usa `ON CONFLICT (schedule_key) DO NOTHING` e respeita a identidade da fonte.
5. `ContinuousProcessingBootstrap.open()` chama a garantia de schedule e a barreira de startup recovery **antes** de transferir os recursos para o processo continuo. Recovery e parte da inicializacao; agendamento pausado sozinho nao e autorizacao para iniciar o daemon.
6. A consulta SQL com a identidade `rasping_runtime` confirma **exatamente** uma linha de agendamento, pausada, sem lease, sem historico e sem outros jobs, runs ou outbox.
7. O `.env` e o registro produtivo continuam intactos.

## Arquivos

- `FASE_21_C2_E_D_D_D_D_D_VALIDAR_BOOTSTRAP_PAUSADO.ps1`: gate unico Windows, sem partida de daemon; invoca o inventario D-B, o teste Java especifico e consulta SQL de leitura.
- `FASE_21_C2_E_D_D_D_D_D_CONSULTAR_SCHEDULE.sh`: executa PostgreSQL como `rasping_runtime`, recebendo credencial DPAPI via stdin; nao registra nem imprime senha.
- `FASE_21_C2_E_D_D_D_D_D_VERIFICAR_SCHEDULE_READONLY.sql`: transacao `READ ONLY` com assercoes sobre identidade e agendamento persistido.
- `EnsureProcessingSchedulePausedStartupTest.java`: teste de unidade do contrato `EnsureProcessingScheduleUseCase` com uma porta simulada, sem conexoes e sem chamadas externas.

## Limites

Este gate **nao executa** `ContinuousProcessingBootstrap.open()`, `ContinuousProcessingApplication.start()`, workers ou scheduler. Ele nao executa migracao Flyway, nem recuperacao de publicacoes em banco real. O teste Java usa uma porta em memoria; junto da consulta SQL de somente leitura e da inspecao do adapter, isso comprova os contratos basicos, nao uma partida real de ponta a ponta.

O script copia **somente** os dois arquivos publicos SQL/shell para `/tmp` no container PostgreSQL, podendo recompilar testes em `target/`. O estado de negocio no PostgreSQL nao e modificado pelo gate. Nao alterar a politica de startup nem habilitar o agendamento. Uma ativacao real exigira etapa posterior e autorizacao explicita, com revisao das integracoes de publicacao/recuperacao.

Nao executar `FASE_21_C2_E_D_D_D_D_C_CRIAR_AGENDAMENTO_PAUSADO.ps1` novamente. Nao commitar ate concluir a etapa D.
