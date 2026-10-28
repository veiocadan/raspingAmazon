# FASE 21-E-D7 — Captura catalogada e parada cooperativa na mesma JVM (offline)

## Objetivo

Validar a fronteira entre Java e PowerShell no **mesmo processo JVM de teste**, sem executar `ContinuousProcessingMain`, iniciar o daemon ou alterar o launcher supervisionado.

O teste reutiliza os contratos já aprovados:

- B1: sandbox privado restrito a `target/retencao-21eb1-testes/cenario-<guid>`;
- B8/B9: classificação catalogada de `stdout` e redação completa de `stderr`;
- D2: escritor segmentado offline, quota e lacre após falha;
- D3: leitura concorrente e validação de eventos;
- D4/D6: contrato Java de parada cooperativa via `SupervisedStopSignal`.

## Arquivos novos

- `src/test/java/com/raspingamazon/infrastructure/bootstrap/Phase21EJavaCapturedStopProbeMain.java` — fixture Java com prazo próprio, saída artificial e sinal real de parada;
- `infra/retencao/FASE_21_E_D7_TESTAR_CAPTURA_PARADA_JVM.ps1` — gate PowerShell; imprime somente resultados seguros;
- `docs/operacao/FASE_21_E_D7_CAPTURA_PARADA_JAVA_OFFLINE.md` — este contrato.

## Prova executada pelo gate

1. Recusa branch ou políticas fora de `preparation_only` e `inventory_only`.
2. Verifica a integridade da fixture Java e a ausência de mudanças em `SupervisedStopSignal.java`.
3. Cria um sandbox privado B1 e uma sessão escritora D2, ambos sob `target`.
4. Inicia **exclusivamente** a classe Java `Phase21EJavaCapturedStopProbeMain` do `target/test-classes`.
5. Processa simultaneamente `stdout` e `stderr`: 1 evento `processing.job.claimed` com correlação fictícia `runId=7`, `jobId=11`; 1 marcador `runtime.stderr.redacted` sem o texto original.
6. Rejeita intencionalmente um registro artificial que contém um campo proibido. A D2 deve lacrar a sessão sem gravar esse registro.
7. Cria um pedido de parada `java-d7-stop.request` exclusivamente no sandbox da fixture.
8. Exige que **a mesma JVM** reconheça o pedido via `SupervisedStopSignal` e encerre naturalmente com código `0`, sem `Kill()`/`taskkill`.
9. Verifica EOF sem dados extras, registros persistidos, integridade de bytes e ausência de texto fictício sensível.
10. Rejeita qualquer nova gravação após o lacre.

## Procedimento

A partir de `C:\Dev\rasping-amazon`, em PowerShell:

```powershell
.\mvnw.cmd -o -q -DskipTests test-compile
if ($LASTEXITCODE -ne 0) { throw 'Compilacao reprovada' }
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'infra\retencao\FASE_21_E_D7_TESTAR_CAPTURA_PARADA_JVM.ps1'
if ($LASTEXITCODE -ne 0) { throw 'Gate D7 reprovado' }
git diff --check
git status --short
git branch --show-current
```

## Restricoes e limites

- O cenário é **offline**. Não usa `ContinuousProcessingMain`, `ContinuousProcessingBootstrap`, CLI produtiva, JDBC, serviços externos, credenciais ou backups.
- Nenhum diretório de logs operacional externo é criado ou inspecionado.
- As evidências artificiais são preservadas sob `target`. Nenhuma exclusão ou rotação é feita.
- O código da fixture Java se encerra por prazo próprio (no máximo 12 segundos) caso não receba parada; o controlador aguarda seu fim, **sem encerramento forçado**.
- Este gate **não** demonstra que falhas de captura do supervisor produtivo estão tratadas. A adaptação produtiva, ACL externa, limites de sessão, recuperação de processos e governança de ativação ainda exigem implementação e validação próprias.
- `config/operacao/continuous-startup-policy.v1.json` permanece `preparation_only` com quatro autorizações `false`.
- `config/operacao/retention-policy.v1.json` permanece `inventory_only` com ações destrutivas e captura contínua desautorizadas.
- Nenhum commit ou push deve ocorrer como parte deste gate.
