# FASE 21-E-D11 — Reserva fixa por diretório e escrita concorrente offline

## Objetivo e decisão

A D10 validou atomicidade de `reserva-<nonce>.v1.json` com `FileMode.CreateNew`. Um nonce **diferente** gera outro arquivo e, portanto, por si só não reserva o diretório compartilhado. A D11 adiciona `d11-dono-diretorio.v1.json` como recurso de exclusividade invariável por diretório. Essa nova reserva também usa `FileMode.CreateNew` e `FileShare.None`; o arquivo nunca é eliminado ou reutilizado.

## Composição efetivamente ensaiada

- B1 define o único local permitido: `target/retencao-21eb1-testes/cenario-<32 hex>` com ACL privada.
- D9/D8/D1 conferem configuração versionada e bloqueios `preparation_only`/`inventory_only`.
- D2 prepara a sessão apenas com diretório vazio; essa preparação não realiza escrita. **A primeira gravação ocorre somente após conquistar a reserva fixa e a reserva D10.**
- D10 persiste PID real, instante de criação do processo e nonce.
- D11 controla a reserva fixa por diretório, encaminha a gravação ao validador D2 e mantém as duas reservas fechadas/persistentes após o encerramento.

## Prova adversarial

Um filho PowerShell proprietário abre sessão, grava um evento catalogado e um marcador redigido em segmentos separados e fica vivo. Outro processo tenta abrir o **mesmo** diretório com **nonce diferente**; deve falhar sem tocar nas evidências. Após o primeiro filho encerrar naturalmente, nova tentativa de reuso deve falhar. Outra sessão em novo diretório deve funcionar.

Contagens, tipos de eventos, SHA-256 dos segmentos, metadados da reserva D10 e ausência de nova gravação são verificados sem expor dados brutos. Falhas de escrita são fechadas por D2. A D11 não cria, lê nem remove arquivos fora do sandbox; não inicia Java.

## Limites

- Esta implementação é exclusivamente de ensaio. Não substitui as verificações de ACL de um diretório externo real e não opera com `ContinuousProcessingMain` ou supervisor.
- Não é autorização de captura produtiva, retenção destrutiva, rotacionamento externo ou inicialização automática.
- Nenhum processo filho é terminado à força. Se um filho não encerrar no prazo próprio, interromper a sequência e investigar.
- Marcadores, arquivos e evidências são intencionalmente preservados. Nenhuma limpeza automática.
- Como o diretório pode ser observado antes de um marcador ser criado, a garantia formal de exclusividade só começa após `CreateNew` bem-sucedido; **o escritor D2 não pode gravar antes dessa reserva**.

## Gate

`powershell.exe -NoProfile -ExecutionPolicy Bypass -File infra/retencao/FASE_21_E_D11_TESTAR_DONO_SESSAO.ps1`

Executar somente no Windows, a partir da raiz `C:\Dev\rasping-amazon`, na branch `feat/fase-21-seguranca-governanca-v1`, após passar o parser do PowerShell e verificar a integridade do pacote. Não executar daemon, backup, Flyway ou operações de exclusão.
