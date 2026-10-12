# FASE 21-C2-E-D-D-D-C-A - Controles passivos de supervisao

## Situacao inicial

A FASE 21-C2-E-D-D-D-B foi aprovada e registrada pelo commit `609150e`. A politica `config/operacao/continuous-startup-policy.v1.json` esta em `preparation_only`, com todas as autorizacoes negadas. A cadencia `PT1H` e o prazo de encerramento de 30 s sao propostas, **nao aplicadas a processos**. A CLI de leitura e o acesso JDBC/Flyway foram validados nas etapas anteriores; nada disso equivale a autorizar o processamento continuo.

## Objetivo deste gate

Verificar o controle de identidade de um futuro daemon Windows, definir um registro externo ao Git e provar que alteracoes acidentais nas cinco condicoes de bloqueio nao sao aceitas. **Este pacote nao implementa inicio nem encerramento produtivo.** Nao executa Maven, Java, Flyway, Docker, SQL, coletores ou publicacoes. O status consulta metadados de processos do Windows, sem exibir linha de comando, informacoes operacionais ou senhas.

## Registro planejado (ainda nao criado)

`C:\dev\rasping-amazon-backups\supervisao\rasping-amazon-daemon.v1.json`

Esquema reservado para o futuro supervisor, sem nenhum segredo:

```json
{
  "schema_version": 1,
  "pid": 12345,
  "creation_utc_ticks": 638959600000000000,
  "java_main_class": "com.raspingamazon.infrastructure.bootstrap.ContinuousProcessingMain"
}
```

Os numeros sao **apenas ilustrativos**. Nunca grave esse arquivo manualmente. O supervisor futuro devera gerarlo e validarlo de maneira atomica e proteger permissoes de acesso. PID isolado nao basta para identificar processo: o status exige correspondencia do PID, classe principal Java e instante de criacao, para evitar colisao/reutilizacao. Registro obsoleto ou daemon sem registro sao incidentes: nenhuma remocao automatica e feita.

## Arquivos

- `infra/postgres/security/FASE_21_C2_E_D_D_D_C_A_FUNCOES_CONTROLES.ps1`: leitura de processos, do registro externo e das guardas.
- `infra/postgres/security/FASE_21_C2_E_D_D_D_C_A_STATUS_SUPERVISAO.ps1`: status read-only.
- `infra/postgres/security/FASE_21_C2_E_D_D_D_C_A_TESTAR_GUARDAS.ps1`: testes negativos com cinco mutacoes somente em memoria.
- `infra/postgres/security/FASE_21_C2_E_D_D_D_C_A_PRECHECK_CONTROLES.ps1`: gate principal, reexecuta precheck B.
- Este documento.

## Executar somente o gate

Na raiz `C:\dev\rasping-amazon`, como o mesmo usuario Windows que possui as credenciais DPAPI, mantendo daemon, worker e scheduler parados:

```powershell
Set-Location 'C:\dev\rasping-amazon'
$ErrorActionPreference = 'Stop'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_A_PRECHECK_CONTROLES.ps1'
if ($LASTEXITCODE -ne 0) { throw 'C2-E-D-D-D-C-A reprovada: nao iniciar daemon' }
git diff --check
git status
```

Esperar `C2-E-D-D-D-C-A PRECHECK PASS` e `BLOQUEIO ATIVO`. Cinco arquivos novos devem aparecer como nao rastreados. Nao executar commit antes da revisao do resultado.

## Limites e proximo desenho

O proximo passo e desenvolver o launcher supervisionado com injeção DPAPI no processo filho, estado protegido fora do Git, exclusao de concorrencia, parada **realmente graciosa** (shutdown hook Java), tolerancia de 30 s, controle de logs e supervisao, sem reinicio automatico. `Stop-Process`/`taskkill /F` nao constituem parada graciosa e nao devem ser usados como mecanismo normal. O plano JSON atual nao e uma barreira de seguranca do sistema operacional: invocar o Java diretamente o contornaria. Antes de partir, revisar efeitos de `EnsureProcessingScheduleUseCase`, `startupRecoveryService().recover()`, integracoes Amazon e publicacao externa, alem da politica de autorizacao formal.

**Proibido nesta fase:** iniciar daemon/scheduler/worker, executar Flyway migrate, alterar `.env`, reprovisionar DPAPI, registrar PIDs inventados e fazer push.
