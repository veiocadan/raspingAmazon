# FASE 21-C2-E-D-D-C-C — Politica operacional inicial versionada

## Decisao operacional

Com autorizacao explicita do operador em 12/10/2026, disponibilizamos **valores iniciais recomendados**, sem reivindicar que tenham sido determinados pelas fontes originais ou calibrados com telemetria de producao.

O Java **continua exigindo** cinco variaveis de ambiente; nao se adicionam defaults silenciosos a `EnvironmentOperationalAlertPolicyProvider`. A configuracao fica em `config/operacao/alert-policy.v1.json` e pode ser auditada pelo Git. Nenhuma senha e armazenada nesse JSON.

| Campo | Valor inicial | Interpretação |
|---|---:|---|
| `ALERT_REPEATED_EXTERNAL_FAILURE_THRESHOLD` | `3` | Tres falhas externas na janela |
| `ALERT_REPEATED_EXTERNAL_FAILURE_WINDOW_SECONDS` | `900` | Quinze minutos de observacao |
| `ALERT_SUSPICIOUS_COLLECTION_LOOKBACK_RUNS` | `5` | Media de cinco runs COMPLETED anteriores |
| `ALERT_SUSPICIOUS_COLLECTION_DROP_FRACTION` | `0.50` | Queda de pelo menos 50% |
| `ALERT_SUSPICIOUS_COLLECTION_MINIMUM_BASELINE_CANDIDATES` | `10` | Media historica minima de dez candidatos |

Observacao: a regra SQL de falhas conta eventos dentro da janela; **nao exige consecutividade**. Queda suspeita requer historico suficiente e nao e avaliada quando a ultima coleta tem zero candidatos (existe alerta especifico para zero). Com dados insuficientes, alguns alertas podem nao surgir.

## Arquitetura e escopo

Arquivos versionados e completos:

1. `config/operacao/alert-policy.v1.json`: configuracao publica, editavel e auditavel.
2. `infra/postgres/security/FASE_21_C2_E_D_D_C_C_POLITICA_FUNCOES.ps1`: validacao tipada do contrato JSON, sem efeitos colaterais.
3. `infra/postgres/security/FASE_21_C2_E_D_D_C_C_PRECHECK_POLITICA.ps1`: valida politica, fonte Java e precheck ja aprovado (DPAPI + porta).
4. `infra/postgres/security/FASE_21_C2_E_D_D_C_C_EXECUTAR_CLI_CONFIGURADA.ps1`: injeta politica somente no processo atual e seus filhos, usa o executor CLI existente e restaura o ambiente no `finally`.
5. Este documento.

Dependencias preexistentes (nao substituidas):

- `infra/postgres/security/FASE_21_C2_E_D_D_C_B_PRECHECK_CLI.ps1`
- `infra/postgres/security/FASE_21_C2_E_D_D_C_B_EXECUTAR_CLI_DPAPI.ps1`
- DPAPI `C:\dev\rasping-amazon-backups\credenciais_c2edcb`, fora do repo.

Nenhum script altera `.env`, nenhum deles modifica roles ou senhas, nao executa Flyway `migrate()`, nem inicia scheduler, worker ou daemon. O executor futuro inicia somente a CLI real para 5 comandos de consulta. A politica JSON **nao e automaticamente consumida por outros entrypoints Java**; qualquer worker/daemon futuro precisara de bootstrap proprio, sujeito a gate.

## Execucao — primeira etapa de leitura

Na raiz `C:\dev\rasping-amazon`, com aplicacao, worker e scheduler parados:

```powershell
Set-Location 'C:\dev\rasping-amazon'
$ErrorActionPreference = 'Stop'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'infra/postgres/security/FASE_21_C2_E_D_D_C_C_PRECHECK_POLITICA.ps1'
if ($LASTEXITCODE -ne 0) { throw 'C2-E-D-D-C-C PRECHECK reprovado; nao iniciar CLI.' }
git diff --check
git status
```

Somente depois da revisao desse gate, o operador podera usar `-ConfirmarExecucao` no executor configurado.

## Criterios para ajuste e producao

Esses valores sao ponto de partida, nao padrao universal. Revisar depois de observar frequencia de coletas, taxa de falhas de integracoes e distribuicao real de candidatos por run. Parametros de alerta alteram somente a deteccao e nao devem ser confundidos com limites de retry, quota de integracao ou elegibilidade comercial.

Manter backups PostgreSQL e HBA; nao executar novo provisionamento; nao publicar push antes dos gates finais da FASE 21.
