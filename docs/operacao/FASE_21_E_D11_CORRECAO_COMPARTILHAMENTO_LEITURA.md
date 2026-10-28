# Correção localizada da FASE 21-E-D11 — leitura de arquivos sob escrita

## Motivo

O gate D11 tentou chamar `Get-FileHash` sobre `operacional-000002.jsonl`
quando o processo escritor D2 ainda mantinha o `FileStream` aberto. No Windows,
o compartilhamento usado pelo `Get-FileHash` é incompatível com esse handle.
Além disso, a D11 tentava ler o marcador D10 enquanto ele continuava aberto
com `FileShare.None` pelo processo proprietário.

## Alteração

Apenas `infra/retencao/FASE_21_E_D11_TESTAR_DONO_SESSAO.ps1` foi substituído
por uma versão **completa**:

1. Durante a vida do escritor, o teste calcula SHA-256 com `FileStream`
   estritamente de leitura e `FileShare.ReadWrite`, compatível com o
   `FileShare.Read` adotado pelo D2. Os metadados do arquivo são comparados
   imediatamente antes/depois da leitura; nenhuma escrita é realizada.
2. O teste continua comparando hashes **antes e depois da disputa real** por
   reserva fixa, sem encerrar prematuramente o processo proprietário.
3. O marcador D10 é verificado apenas quanto à existência durante a sessão.
   O conteúdo PID, creation ticks e nonce é lido **depois** de o processo
   encerrar cooperativamente, quando `FileShare.None` foi liberado.
4. Após a saída do proprietário, o teste confere novamente os SHA-256 com
   `Get-FileHash` e valida a identidade D10 e o marcador D11.

O teste permanece não destrutivo e offline. A leitura compatível com escrita
não estabelece um snapshot atômico contra adversários que modifiquem um arquivo
no instante exato da leitura; trata-se de um gate de integração isolado.

## Restrições preservadas

- Nenhuma alteração nas funções B1, B8, D1, D2, D8, D9, D10 ou D11.
- Nenhuma mudança em política ou no supervisor.
- Apenas processos PowerShell e cenários privados em `target/`.
- Sem Java daemon, banco, rede, backups, exclusões, commit ou push.
- Interromper em qualquer falha ou filho não encerrado; não executar limpeza.

## Validação

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File infra/retencao/FASE_21_E_D11_TESTAR_DONO_SESSAO.ps1
```

O resultado funcional deverá ser confirmado no Windows. Esta correção foi
verificada estruturalmente, mas não é declarada aprovada antecipadamente.
