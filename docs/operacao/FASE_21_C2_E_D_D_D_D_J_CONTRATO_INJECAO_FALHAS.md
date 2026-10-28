# FASE 21-C2-E-D-D-D-D-J - Injeção isolada de falhas no supervisor

## Objetivo

Validar o caminho de recuperação introduzido pela D-I em condições reais de
JVM já criada, porém **sem registro produtivo**. O teste não altera as
políticas, não lê DPAPI, não conecta ao PostgreSQL e não chama o bootstrap.

## Premissas

- Política `preparation_only` com as quatro autorizações bloqueadas.
- Nenhum processo/registro do daemon produtivo em execução.
- Fonte de teste `SupervisedLifecycleSmokeMain` já validada na D-A.
- JVM de teste iniciada em pasta TEMP privada, com ambiente mínimo e
  sem credenciais/variáveis de banco.
- Somente encerramento cooperativo, sem `taskkill` ou `Stop-Process`.

## Cenários de validação

1. Falha antes do `Start()` -> remove apenas a reserva própria.
2. Falha ao recuperar o handle do `Start()` -> conserva a reserva, bloqueia
   novas partidas e requer inspeção manual.
3. JVM de teste criada com PID, classe e creation time identificados.
4. Identidade diferente da classe produtiva -> **não** sinaliza parada e
   **não** remove a reserva.
5. Injeção de falha de registro -> processo smoke identificado recebe
   `STOP`, encerra cooperativamente, e reserva e sinal são eliminados.
6. Processo de teste já encerrado -> retirada segura da reserva própria.

O mecanismo de recuperação admite apenas a classe produtiva padrão e a classe
smoke de testes. O launcher produtivo não recebe parâmetro de classe: utiliza
sempre o padrão `ContinuousProcessingMain`. A identidade de processo é
verificada pela API CIM do Windows antes de qualquer sinal.

## Execução

Na raiz `C:\dev\rasping-amazon`:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "infra/postgres/security/FASE_21_C2_E_D_D_D_D_J_VALIDAR_INJECAO_FALHAS.ps1"
```

A compilação utiliza `mvn -B -q -DskipTests test-compile`. Não inicia daemon,
não chama Flyway migrate, e não escreve dados de negócio. Uma eventual JVM de
teste que não encerre é preservada para inspeção manual e reprova o gate.

## Limites

Não é um teste da partida produtiva, de recovery de jobs/outbox ou da
capacidade do Windows de recuperar uma JVM depois de perda completa da janela
PowerShell; essas exigências continuam pendentes de autorização e testes
específicos. Não autorizar efeitos externos como consequência deste gate.
