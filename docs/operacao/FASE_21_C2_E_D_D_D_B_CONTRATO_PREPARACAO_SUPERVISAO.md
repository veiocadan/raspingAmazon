# FASE 21-C2-E-D-D-D-B — Preparacao da inicializacao supervisionada

## Estado de entrada

O gate A foi aprovado e registrado pelo commit `44b920f`: backups, identidade JDBC/Flyway, DPAPI e efeitos de startup foram revisados, sem dar partida no daemon. A CLI operacional ja foi validada com duas identidades, mas nao reproduz o comportamento do scheduler, worker e recovery.

## Finalidade

Preparar um **contrato de partida supervisionada** de modo seguro e fail-closed. Este pacote nao implementa nem autoriza um launcher ativo: primeiro registra o plano, verifica precondicoes e mantem todos os interruptores desligados. Scripts e politicas nao devem guardar credenciais, tokens de canal ou dados de publicacao. **Importante:** os indicadores false deste JSON sao guardas do planejamento, NAO sao lidos pelo Java; iniciar o daemon manualmente ignoraria esses indicadores e poderia produzir efeitos reais.

## Arquivos do pacote

1. `config/operacao/continuous-startup-policy.v1.json`: plano versionavel, sem segredos, com `execution_mode=preparation_only`, desligando partida, migrate, acoes externas e reinicio automatico.
2. `infra/postgres/security/FASE_21_C2_E_D_D_D_B_FUNCOES_PLANO.ps1`: leitor de JSON rigoroso e verificacoes estruturais.
3. `infra/postgres/security/FASE_21_C2_E_D_D_D_B_PRECHECK_SUPERVISAO.ps1`: gate composto somente leitura que reexecuta o gate A e checa politica e Git.
4. `infra/postgres/security/FASE_21_C2_E_D_D_D_B_EXIBIR_PLANO_SUPERVISAO.ps1`: mostra o plano ao operador, sem abrir processos.
5. Este documento.

## Parametros iniciais recomendados para o projeto, nao defaults do Java

- `initial_schedule_interval: PT1H`: proposta inicial de **uma coleta por hora**. Nao instala variavel de ambiente nem modifica um agendamento ja existente; a primeira ativacao devera conferir o agendamento persistido e os efeitos em Amazon/canais externos.
- `shutdown_grace_seconds: 30`: referencia inicial para desenhar parada supervisionada. Nenhum mecanismo de parada foi ativado nesta fase.
- `daemon_start_authorized: false`: bloqueio explicito da inicializacao.
- `flyway_migrate_authorized: false`: nenhum Flyway migrate.
- `external_actions_authorized: false`: nenhuma coleta, enriquecimento, disparo ou publicacao via este pacote.
- `automatic_restart_authorized: false`: sem reinicio automatico.

A politica de alertas `config/operacao/alert-policy.v1.json` da fase C-C continua independente. O `PROCESSING_SCHEDULE_INTERVAL` e obrigatorio no provider Java; neste pacote `PT1H` e uma proposta **nao injetada**. O script apenas informa `PRESENTE`/`AUSENTE` no ambiente e `CHAVE_PRESENTE`/`CHAVE_AUSENTE` no `.env`, sem revelar valores.

## Procedimento

Extrair o ZIP na raiz `C:\dev\rasping-amazon` preservando diretorios, com Java, worker e scheduler parados. No PowerShell:

```powershell
Set-Location 'C:\dev\rasping-amazon'
$ErrorActionPreference = 'Stop'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'infra/postgres/security/FASE_21_C2_E_D_D_D_B_PRECHECK_SUPERVISAO.ps1'
if ($LASTEXITCODE -ne 0) { throw 'C2-E-D-D-D-B reprovada; nao iniciar processamento.' }
git diff --check
git status
```

Esperar: `C2-E-D-D-D_B PRECHECK PASS` (a grafia real do marcador usa `C2-E-D-D-D-B`). Nenhum arquivo `.env` sera modificado. Os cinco arquivos novos serao `untracked` ate a aprovacao e commit, de acordo com o fluxo de revisao.

O script opcional `FASE_21_C2_E_D_D_D_B_EXIBIR_PLANO_SUPERVISAO.ps1` apenas exibe politica publica. Nao existe rotina de start nesta entrega. A proxima etapa devera implementar e provar supervisionamento de processos (PID, exclusao mutua, logs redigidos/ACL, shutdown, observabilidade), bem como validar configuracoes e integracoes externas antes de liberar a partida controlada. O gate B nao equivale a autorizacao para iniciar `ContinuousProcessingMain`.

**Proibido nesta etapa:** executar manualmente o daemon, worker, scheduler ou `Flyway.migrate()`, alterar o `.env`, reprovisionar segredos DPAPI ou fazer push.
