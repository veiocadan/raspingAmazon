# FASE 21-D — Auditoria administrativa de agendamentos

## Contrato

- A migration **V38** introduz `processing_schedule_change_audit`, imutavel (append-only).
- O trigger `trg_processing_schedule_admin_change` grava cada alteracao efetiva de `enabled` ou `interval_ms` na **mesma transacao** do `UPDATE` original.
- Registra chave, operacao, estado anterior e posterior, datas, `session_user`, ator declarado e motivo/referencia.
- Nao duplica historico de filtros, score, selecao, quota ou cadencia. A passagem rotineira de `next_run_at` pelo scheduler nao e auditada.
- Chamadas JDBC antigas sao capturadas pelo trigger, com ator `db-role:<session_user>` e motivo identificado como operacao direta sem referencia declarada.
- Chamadas operacionais pela CLI podem informar `--reason`; as assinaturas historicas continuam suportadas, com uma referencia automatica ao comando.
- O ator local da CLI deriva de `user.name`, **nao e uma identidade autenticada**. O papel PostgreSQL (`db_session_role`) e capturado separadamente.
- O adapter JDBC define o contexto somente na transacao da mutacao e restaura o contexto anterior. `JdbcTransactionAdapter` garante rollback conjunto em erro.
- A auditoria nao preserva evidencias retroativas anteriores a V38; nao fazer backfill de eventos nao observados.
- A migration nao exige nem concede DDL ao usuario de runtime, nem concede INSERT direto na tabela de auditoria.
- A aplicacao nao precisa de interface web ou aprovacao humana no fluxo normal.

## Exemplos de CLI (somente apos autorizacao de escrita)

```text
rasping-amazon schedules pause amazon-deals --reason manutencao-planejada
rasping-amazon schedules interval amazon-deals PT1H --reason ajuste-operacional-2026-10
rasping-amazon schedules resume amazon-deals --reason retomada-aprovada
```

`resume` torna a proxima janela elegivel **imediatamente**; nao executar antes de autorizar efeitos externos e o daemon.

## Instalacao e validacao

1. Verifique que o daemon esta parado, o agendamento continua pausado e existe backup recuperavel.
2. Execute Flyway **somente com rasping_migrator**, numa janela de alteracao autorizada, para instalar V38. Nao execute `migrate` como runtime.
3. Execute a suite Maven e o teste JDBC de integracao em ambiente de teste **com V38 instalada**. Nao disparar testes de integracao contra banco com dados produtivos sem isolamento.
4. Consulte os registros por SQL apenas com credencial autorizada; nunca alterar historico.
5. Registre o commit somente apos gates completos.

## Verificacao SQL somente leitura

```sql
SELECT id, schedule_key, change_type, db_session_role, declared_actor,
       change_reason, previous_enabled, new_enabled,
       previous_interval_ms, new_interval_ms, changed_at
FROM public.processing_schedule_change_audit
ORDER BY id DESC
LIMIT 25;
```

Nao imprimir credenciais, nem desabilitar o bloqueio `preparation_only`.
