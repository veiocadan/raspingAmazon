# FASE 21-C2-E-D-D-D-D-B — Inventário pré-ativação (somente leitura)

## Objetivo

Identificar, **sem iniciar o daemon**, quanto trabalho já está persistido e pode causar efeitos no primeiro boot do `ContinuousProcessingMain`: jobs pendentes, leases, outbox de publicação e schedules vencidos. A execução da FASE 21-C2-E-D-D-D-D-A já comprovou a parada cooperativa de uma JVM **de teste**; não comprovou a segurança de iniciar o daemon produtivo.

## Contrato

1. O arquivo `config/operacao/continuous-startup-policy.v1.json` deve permanecer em `preparation_only`, com **todas** as quatro autorizações desativadas.
2. O processo Java produtivo e seu registro supervisionado devem estar ausentes; qualquer divergência interrompe o diagnóstico.
3. O único usuário SQL será `rasping_runtime`, autenticado pela senha DPAPI **já existente** do usuário Windows atual. Não usar o administrador nem `rasping_migrator`.
4. A conexão SQL é `READ ONLY` + `REPEATABLE READ`, timeout e identificação obrigatória da role e database.
5. A consulta retorna **apenas totais agregados** de jobs, schedules, runs e publication outbox; não retorna mensagens, URLs, IDs, tokens nem registros históricos.
6. Os scripts SQL e shell não contêm segredos; podem ser copiados ao container. A senha é enviada pelo `STDIN` e usada apenas no processo `psql`, nunca em linha de comando, arquivo ou commit.
7. Este inventário **não prova** que o boot seja seguro e **não** autoriza coleta, recuperação, publicação, Flyway `migrate` ou alteração da política. A ausência de backlog também não constitui autorização.
8. Não se alteram `.env`, roles, grants, arquivos DPAPI, configuração de startup ou dados PostgreSQL.
9. Não commitar esta subetapa separadamente; registrar as alterações apenas quando a etapa D inteira estiver concluída, conforme decisão do projeto.

## Interpretação

- `schedule_enabled_due`: schedules ativos cuja próxima execução já venceu; podem disparar coletas assim que o scheduler iniciar.
- `jobs_*`: trabalho presente em fila ou já executando; o recovery também pode alterar alguns estados.
- `outbox_*`: entregas em processamento/pendentes/ambíguas. Nunca fazer replay cego de `DELIVERY_UNKNOWN`.
- `runs_running`: processos históricos ainda marcados como em andamento.

A consulta é um retrato pontual, não uma garantia de ausência de concorrência futura. Antes de uma partida real será necessária uma autorização operacional explícita e barreiras de efeitos externos efetivas, com testes adicionais.

## Execução

A partir da raiz do repositório, com daemon/worker/scheduler parados:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "infra/postgres/security/FASE_21_C2_E_D_D_D_D_B_EXECUTAR_INVENTARIO.ps1"
if ($LASTEXITCODE -ne 0) { throw "Inventario reprovado; nao iniciar daemon." }
git diff --check
git status
```

Espera-se `C2-E-D-D-D-D-B INVENTARIO PASS`, seguido de `BLOQUEIO ATIVO`.
