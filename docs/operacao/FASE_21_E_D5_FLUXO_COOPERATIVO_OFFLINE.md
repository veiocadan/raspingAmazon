# FASE 21-E-D5 — captura catalogada com parada cooperativa no mesmo filho

## Objetivo

Unir em um ensaio end-to-end offline as capacidades D3 (captura concorrente e escritor D2) e D4 (pedido de parada cooperativo), sem alterar ou iniciar o supervisor.

## Procedimento

O teste cria um sandbox privado de `target/retencao-21eb1-testes` pela B1 e inicia **somente um filho PowerShell sintético** via `-File`. O filho emite um evento catalogado em `stdout` e um segredo fictício em `stderr`, confirmando sua prontidão por um marcador privado. O capturador D3 processa ambos os canais e o escritor D2 persiste duas linhas JSONL.

O cenário injeta um objeto JSON deliberadamente inválido no escritor D2. O escritor deve lacrar a sessão sem persistir o objeto. O mecanismo de D4 cria exclusivamente um sinal privado dentro do sandbox; o mesmo filho reconhece esse sinal e termina voluntariamente, com código 0. A validação comprova a manutenção dos registros anteriores e a ausência de conteúdo bruto de `stderr`.

## Limitações explícitas

- Não há processo Java, credencial, acesso ao PostgreSQL, backup ou log operacional externo.
- Não há nenhuma integração com o launcher supervisionado real.
- O filho possui duração máxima autônoma de 12 segundos. Não há `Kill()` nem `taskkill`; falhas no encerramento exigem inspeção manual.
- A falha injetada é de **validação do escritor**, não de hardware ou disco cheio; esses cenários requerem validação própria.
- A leitura concorrente e sua segurança herdam as pré-condições testadas em D3 e B5; a D5 adiciona a ligação temporal entre captura, lacre e parada.
- A captura produtiva e a retenção destrutiva permanecem proibidas em `preparation_only`/`inventory_only`.

## Arquivos

- `infra/retencao/FASE_21_E_D5_FUNCOES_FLUXO_COOPERATIVO.ps1`
- `infra/retencao/FASE_21_E_D5_TESTAR_FLUXO_COOPERATIVO.ps1`

## Gates esperados

1. Sintaxe dos dois scripts aprovada pelo `Parser.ParseFile`.
2. Gate de teste D5 com mensagens PASS e código de saída zero.
3. `git diff --check` sem erros e branch `feat/fase-21-seguranca-governanca-v1`.

Não executar o launcher de supervisão. Não fazer commits ou push antes do fechamento consolidado da FASE 21-E.
