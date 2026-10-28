# FASE 21-E-D4 — Sinalização cooperativa após falha do escritor (offline)

**Estado inicial:** etapa isolada; nenhum processo Java e nenhum supervisor produtivo foram modificados ou autorizados. Depende da aprovação local de D2/D3 e B9.

## Objetivo e limites

A D4 valida o elo **falha real da validação de gravação D2 → sessão lacrada → arquivo privado de solicitação → reconhecimento pelo filho PowerShell sintético → encerramento voluntário**. O contrato inspira-se no mecanismo `SupervisedStopSignal.java`, mas utiliza nomes exclusivos de testes e caminhos somente sob `target/retencao-21eb1-testes/cenario-<GUID>`.

Isso **não** prova integração com o Java, não garante parada cooperativa do daemon, não substitui a verificação de PID e tempo de criação do supervisor, nem remove a obrigação de investigar falhas de I/O no sistema produtivo. O capturador D3 ainda usa encerramento forçado somente para filhos de teste nos cenários adversariais; não deve ser transplantado para produção.

## Comportamentos verificados

1. Exigir `preparation_only` e `inventory_only`, catálogo B8 e fontes da supervisão existentes.
2. Criar sessão D2 no sandbox privado, persistir evento catalogado e provocar rejeição por campo desconhecido, **sem persistir o campo**.
3. Após o lacre, criar arquivo `parada-sintetica-d4.request` em modo `CreateNew`, no mesmo cenário, sem sobrescrita nem exclusão.
4. O processo filho **PowerShell**, com vida útil limitada a 10 segundos, monitora a existência do sinal e encerra espontaneamente com código 0. O teste **não usa `Kill()` nem `taskkill`**.
5. Rejeitar duplicação do pedido e caminho fora do sandbox, preservar evento catalogado anterior, impedir escrita após lacre.
6. Testar filho com vida útil autônoma de 2 segundos e **sem sinal**: deve terminar com código 75 e não criar pedido espúrio.
7. Permanecer proibida a construção do escritor produtivo, assim como acesso a diretórios externos, banco, credenciais e backups.

## Evidências e restrições operacionais

- Todos os arquivos criados ficam exclusivamente no sandbox privado sob `target/`, ignorado pelo Git e **não são eliminados pelo teste**.
- A verificação de ACL e de reparse point utiliza os validadores da B1/D2.
- Se um filho não finalizar no prazo, o teste reprova e exige inspeção manual; o script não força encerramento.
- O ensaio utiliza a falha **do escritor** como gatilho de parada. A detecção de erro real no `stdout`/`stderr` Java, a política para `PrintWriter.checkError()` e a integração do supervisor são gates distintos.
- O modo de produção continua explicitamente desautorizado: `continuous-startup-policy.v1.json` em `preparation_only`, `retention-policy.v1.json` em `inventory_only`, captura/exclusão desautorizadas.

## Execução

No PowerShell em `C:\Dev\rasping-amazon`:

```
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "infra/retencao/FASE_21_E_D4_TESTAR_PARADA_SINTETICA.ps1"
```

Somente considerar D4 aprovada após a execução local completa e inspeção das mensagens `PASS`. Não realizar commit, push, daemon, limpeza nem modificar o supervisor nesta etapa.
