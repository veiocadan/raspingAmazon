# FASE 21-C2-E-D-D-D-C-B — Base de parada cooperativa do daemon

## Contexto

O commit `32d11d3` validou controles passivos de processo e bloqueios da politica `config/operacao/continuous-startup-policy.v1.json`. A politica continua em `preparation_only` com inicio, Flyway migrate, efeitos externos e restart automatico proibidos. A fase C inteira tera **um unico commit ao final**, e nao um commit para cada verificacao.

## Entrega

- `src/main/java/com/raspingamazon/infrastructure/bootstrap/SupervisedStopSignal.java`: observa exclusivamente um arquivo de pedido de parada, sem ler seu conteudo, logar senhas ou executar SQL. O monitor e daemon thread para nao manter a JVM artificialmente aberta. Rejeita arquivos preexistentes e caminhos relativos.
- `src/main/java/com/raspingamazon/infrastructure/bootstrap/ContinuousProcessingMain.java`: arquivo completo com integracao opt-in via `RASPING_SUPERVISED_STOP_REQUEST_FILE`. Mantem shutdown hook e lifecycle existentes; ao receber um pedido o monitor chama `ContinuousProcessingApplication.close()`, que encerra runtime, worker, scheduler e recursos gerenciados pela estrategia normal do projeto.
- `src/test/java/com/raspingamazon/infrastructure/bootstrap/SupervisedStopSignalTest.java`: quatro testes unitarios isolados sem PostgreSQL, Amazon, Playwright ou rede.
- `infra/postgres/security/FASE_21_C2_E_D_D_D_C_B_TESTAR_PARADA_COOPERATIVA.ps1`: gate de compilacao e teste, sem iniciar daemon. O gate executa quatro testes novos mais `ContinuousProcessingMainTest` (um teste historico de sanitizacao), total de cinco.

## Limites

**Este pacote nao autoriza partida do processamento** e nao contem launcher Windows. O Java so observa o arquivo de pedido se o caminho for explicitamente definido pelo futuro supervisor. Criar esse arquivo manualmente nao e um procedimento autorizado. Um supervisor futuro devera proteger diretorio e pedido via ACL, registrar identidade real do processo, validar PID e instante de criacao, solicitar encerramento e aguardar prazo de 30 segundos sem `taskkill /F` / `Stop-Process` como parada normal.

A JVM deve executar com JDK 25 conforme `pom.xml`. O monitor de parada somente prepara a cooperacao; **ainda nao comprovamos o shutdown real de processos produtivos**. Nao liberar inicializacao sem revisar recovery de startup, integracoes externas, publicacao e verificacoes de seguranca.

## Execucao

Extraia o ZIP na raiz do repositorio, preservando pastas e sobrescrevendo apenas `ContinuousProcessingMain.java` com o arquivo completo. Da raiz:

```powershell
Set-Location 'C:\dev\rasping-amazon'
$ErrorActionPreference = 'Stop'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_B_TESTAR_PARADA_COOPERATIVA.ps1'
if ($LASTEXITCODE -ne 0) { throw 'C2-E-D-D-D-C-B reprovada; nao continuar' }
git diff --check
git status
```

Esperado: `C2-E-D-D-D-C-B GATE PASS`, com `ContinuousProcessingMain.java` modificado e outros quatro arquivos novos. **Nao fazer commit ainda**. A validacao do mecanismo de launcher/parada Windows sera incorporada na mesma etapa C antes do unico commit final.

## Correcao de compatibilidade e sigilo — revisao 2

A primeira entrega do pacote substituia indevidamente o metodo historico
`failureMessage(RuntimeException)` por uma versao `private` que retornava
`exception.getMessage()`. Isso causava falha de compilacao em
`ContinuousProcessingMainTest` e poderia expor credenciais em `stderr`.

Esta revisao corrige ambos os defeitos usando arquivo Java integral:

- restaura a visibilidade **package-private** de `failureMessage` usada pelo teste legado;
- restaura `SafeOperationalError.describe(exception)`, que nao le mensagens arbitrarias de excecoes;
- preserva a integracao de `SupervisedStopSignal` e o fechamento normal da aplicacao;
- valida explicitamente **5 testes**: quatro da parada cooperativa e um da protecao de erros.

Nenhum script inicia o processamento, acessa PostgreSQL ou modifica `.env`.
Nao fazer commit antes de concluir a FASE 21-C2-E-D-D-D-C inteira.
