# FASE 21-E-B9 — Correção do protocolo de execução do processo sintético

## Evidência anterior

No Windows PowerShell 5.1, o teste principal da B9 produziu 24 linhas de `stdout` e 26 de `stderr`. As 24 linhas intencionais de `stderr` foram preservadas, mas duas linhas adicionais eram identificáveis como `CLIXML`, antes e depois das linhas intencionais. A codificação do `stderr` já havia sido corrigida anteriormente, com teste específico aprovado.

## Decisão

Não aceitar uma contagem de 26, não filtrar mensagens `CLIXML` e não alterar o esquema dos logs. Em vez disso, executar o código **sintético** com `powershell.exe -File` a partir de um arquivo `filho-sintetico-<uuid>.ps1` criado com `FileMode.CreateNew` no sandbox privado aprovado na B1, com UTF-8 BOM para compatibilidade com Windows PowerShell 5.1.

A política de execução é substituída **somente no processo filho sintético** (`-ExecutionPolicy Bypass`), sem alterar a configuração do Windows. Os arquivos de código de teste ficam em `target/retencao-21eb1-testes/cenario-*/` como evidências artificiais, sem apagamento automático. Não inserir código com segredos reais no parâmetro `CodigoFilho`.

O capturador preserva:
- `stdout` estritamente UTF-8 e somente eventos do catálogo B8;
- `stderr` minimizado, sem incorporar seu conteúdo;
- leitura concorrente limitada por blocos da B5;
- limite combinado, proibição de sobrescrita e lacre de ambos os canais após falha;
- `preparation_only`, `inventory_only` e zero integração com supervisor/daemon.

## Gate

1. Instalar somente o arquivo de funções B9 completo corrigido e o novo teste de protocolo.
2. Executar `FASE_21_E_B9_TESTAR_PROTOCOLO_FILHO.ps1` (exige `stdout=2`, `stderr=1`, zero linhas CLIXML extras).
3. Reexecutar `FASE_21_E_B9_TESTAR_CODIFICACAO.ps1`.
4. Reexecutar `FASE_21_E_B9_TESTAR_CAPTURA_CATALOGADA.ps1` (exige `stdout=24`, `stderr=24` e todos os testes adversariais).
5. Conferir `git diff --check`, `git status --short` e branch. Sem commit ou push antes da conclusão da fase.

Se alguma verificação reprovar, parar; **não** alterar as contagens, descartar linhas ou ativar a captura real.

## Limitação

Estes scripts são um teste de integração **sintético**, não uma solução de captura de processo Java autorizada. Sua execução completa só foi projetada para Windows PowerShell e requer validação no ambiente do projeto.
