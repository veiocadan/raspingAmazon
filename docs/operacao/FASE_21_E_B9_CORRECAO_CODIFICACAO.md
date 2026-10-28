# FASE 21-E-B9 — Correcao isolada de codificacao do stderr

## Ocorrencia

O gate B9 falhou no Windows PowerShell 5.1, em
`Receive-21EB9-BlocoCatalogado`, na chamada a `GetResult()`, devido ao
byte isolado `A2` que nao e UTF-8 valido. A verificacao de sintaxe do
pacote anterior passou, mas isso nao aprova o gate B9.

A mensagem aponta para incompatibilidade entre bytes recebidos do
`stderr` do processo filho sintetico e o decoder UTF-8 estrito. A origem
exata do byte nao foi comprovada, portanto a correcao nao pressupoe que
ele venha de um evento especifico.

## Decisao de seguranca

- `stdout` **permanece** UTF-8 estrito, pois recebe eventos JSON que
  precisam cumprir o catalogo B8. Uma sequencia invalida bloqueia a
  captura e nao e convertida em evento estruturado.
- `stderr` do processo sintetico passa a usar ISO-8859-1 (`28591`) para
  decodificar qualquer byte, preservando os limites de leitura por
  blocos da B5. Isso **nao valida nem confia** no texto recebido: a B8
  exige que todo `stderr` seja transformado em marcador constante
  `runtime.stderr.redacted`.
- Os bytes originais, mensagens de excecao, dados de fornecedores,
  segredos e stack traces **nao** sao persistidos.
- Quotas combinadas, segmentos, ACL privada, falha fechada, encerramento
  do filho sintetico e politica `preparation_only` ficam inalterados.
- Este pacote nao substitui B1-B8, nao altera `B9_TESTAR_CAPTURA_CATALOGADA.ps1`
  nem conecta a captura ao supervisor real.

## Arquivos

1. `infra/retencao/FASE_21_E_B9_FUNCOES_CAPTURA_CATALOGADA.ps1` — arquivo
   B9 completo; unica alteracao funcional: decoder de `stderr`.
2. `infra/retencao/FASE_21_E_B9_TESTAR_CODIFICACAO.ps1` — novo teste
   isolado com byte `A2` bruto em stderr e sequencia UTF-8 invalida em
   stdout, ambos com processos PowerShell sinteticos.

## Criterio de aprovacao

Executar a verificacao de sintaxe dos dois arquivos, o teste novo de
codificacao e a regressao completa da B9, nesta ordem. Ambos os testes
devem sair com codigo 0. Um teste que falhar suspende o gate; nao alterar
os outros scripts nem converter `stdout` para codificacao permissiva.

Os cenarios artificiais ficam em `target/retencao-21eb1-testes` e sao
preservados. Nao remover evidencias, nao fazer commit/push e nao iniciar
daemon durante esta etapa.
