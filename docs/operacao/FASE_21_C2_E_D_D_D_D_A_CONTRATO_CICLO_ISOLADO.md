# FASE 21-C2-E-D-D-D-D-A — Ciclo de vida supervisionado isolado

## Propósito

Validar o canal de parada cooperativa em um processo Windows real, **sem** iniciar `ContinuousProcessingMain`. Esta etapa não autoriza `external_actions_authorized`, não modifica `continuous-startup-policy.v1.json` e não acessa banco, Amazon, Flyway ou credenciais DPAPI.

## Segurança e isolamento

- Exige `execution_mode=preparation_only` e as quatro autorizações em `false`.
- Exige ausência de daemon registrado e ausência de qualquer `ContinuousProcessingMain` identificável antes/depois da prova.
- Compila apenas fontes Java e testes (`mvn -B -q -DskipTests test-compile`); não executa plugins de migração.
- Executa somente `SupervisedLifecycleSmokeMain`, classe de teste sob `src/test/java`. Ela usa o `SupervisedStopSignal` produtivo, mas nunca inicializa a aplicação contínua.
- Usa diretório temporário único, externo ao repositório, com ACL privada do usuário Windows. O ambiente de processo filho é definido por lista permitida, sem `DB_*` nem outros segredos.
- O PowerShell autentica o processo de teste por PID, instante de criação e classe principal obtida por CIM. Emite um pedido exclusivo `STOP` e espera a saída normal. Não usa `taskkill`, `Stop-Process` nem reinício automático.
- A classe simulada tem limite de espera de 45 segundos; o executor também efetua encerramento cooperativo em caso de erro. Se o processo ainda estiver ativo, o diretório é preservado para investigação.
- Compara existência e SHA256 de `.env` antes/depois e exige novamente ausência de daemon real.

## Limite da prova

O resultado `GATE PASS` prova o fluxo Windows → arquivo protegido → `SupervisedStopSignal` → JVM de teste → saída, mas **não** prova a segurança de iniciar `ContinuousProcessingMain`, o trabalho de recovery, a publicação externa, a política de coleta nem as permissões de produção. A partida real continua bloqueada.

## Execução

Na raiz `C:\dev\rasping-amazon`, com daemon parado:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "infra/postgres/security/FASE_21_C2_E_D_D_D_D_A_VALIDAR_CICLO_ISOLADO.ps1"
if ($LASTEXITCODE -ne 0) { throw "Gate isolado reprovado. Nao iniciar daemon." }
git diff --check
git status
```

Nenhum commit intermediário. A etapa D deverá reunir os controles e as provas posteriores antes de um único commit.
