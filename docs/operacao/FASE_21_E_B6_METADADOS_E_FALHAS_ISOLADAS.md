# FASE 21-E-B6 — Metadados estruturados e falha fechada de gravação (offline)

## Escopo e motivação

A FASE 21-E-B3 valida nomes de campos, tipos e formato de strings. Essa validação **não demonstra** que strings como `event`, `component`, `operation`, `outcome`, `integration`, `jobType`, `failureType` e `errorCode` sejam livres de segredos: uma cadeia arbitrária alfanumérica pode cumprir o padrão e ainda ser sensível.

Este gate produz uma **projeção minimizada em memória**, preservando apenas `timestamp`, `level` e campos numéricos de correlação (`runId`, `jobId`, `candidateId`, `snapshotId`, `evaluationId`, `publicationId`, `durationMs`). Os valores textuais livres são descartados e substituídos por campos fixos de classificação. Entradas `stdout` não estruturadas e todas as linhas `stderr` permanecem marcadores redigidos pela B3.

**Limitação consciente:** o rótulo do evento original deixa de estar disponível na projeção. Esta é uma prova de defesa, não uma integração produtiva; será necessário um catálogo confiável e versionado de semântica operacional antes de habilitar captura real que preserve a identidade dos eventos.

## Escrita e recuperação

`Write-21EB6-EventoLacrado` utiliza o escritor B1. Se qualquer gravação falhar, marca a sessão como encerrada, descarta seu objeto de fluxo e recusa novas escritas. Não remove, sobrescreve nem trunca os segmentos existentes. Não inclui texto da exceção em sua mensagem pública.

O cenário injetado fecha deliberadamente o objeto `FileStream` da sessão sintética após uma gravação válida. A tentativa seguinte deve falhar; uma terceira tentativa deve ser recusada; a evidência anterior deve permanecer igual byte a byte.

**Limitações conscientes:** o teste não simula falta física de espaço, perda abrupta de energia, acesso concorrente de múltiplos processos ou falha parcial de escrita em um mesmo segmento. Uma falha real no meio do `FileStream.Write` poderia deixar uma linha incompleta, que precisará ser tratada por protocolo adicional de recuperação, sem exclusão automática. A B1/B4 continuam com seus contratos atuais; a B6 não as modifica.

## Dependências e segurança

Requer aprovação prévia de E-A, B1, B3, B4 e B5; mantém `retention-policy.v1.json` em `inventory_only`, `destructive_actions_authorized=false` e `continuous_log_capture_authorized=false`; mantém `continuous-startup-policy.v1.json` em `preparation_only` e todas as flags de autorização `false`.

Importar o arquivo de funções não executa subprocessos ou ações externas. O teste grava somente em novo `target/retencao-21eb1-testes/cenario-<guid>` com ACL privada. Não altera supervisor, credenciais, migracões, PostgreSQL, backups, política de startup nem repositório Git. Não há deleção nem rotação de logs reais.

## Gate

1. Verificar sintaxe dos dois novos arquivos PowerShell com `Parser.ParseFile(Resolve-Path ...)`.
2. Reexecutar B3, B4 e B5 para garantir ausência de regressões.
3. Executar `powershell.exe -NoProfile -ExecutionPolicy Bypass -File infra/retencao/FASE_21_E_B6_TESTAR_PROTECAO.ps1`.
4. Conferir `git diff --check`, `git status --short` e a branch de FASE 21.

Mesmo com aprovação, o próximo trabalho é integrar a minimização e o lacre ao capturador sintético, provar as regressões e definir protocolo de recuperação de segmentos antes de considerar qualquer modificação no supervisor real.
