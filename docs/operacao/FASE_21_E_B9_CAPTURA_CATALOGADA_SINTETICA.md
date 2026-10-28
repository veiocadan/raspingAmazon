# FASE 21-E-B9 — Captura catalogada sintetica (v1)

## Escopo

A B9 integra o contrato estrito B8 ao escritor B1, ao leitor limitado da B4/B5 e ao lacre de erro B6, sem modificar arquivos B1-B8. O unico subprocesso permitido pelo teste e `powershell.exe`, criado com codigo sintetico controlado, nunca o `ContinuousProcessingMain`, Java ou o supervisor Windows.

**Estado obrigatorio:** `continuous-startup-policy.v1.json` em `preparation_only`, quatro autorizacoes desligadas; `retention-policy.v1.json` em `inventory_only` com captura continua e exclusoes desligadas; `operational-log-catalog.v1.json` em `offline_validation_only`. Nao criar o diretorio operacional `C:\dev\rasping-amazon-operacao\logs`. Nao modificar PostgreSQL, backups, credenciais ou migracoes.

## Arquivos novos

- `infra/retencao/FASE_21_E_B9_FUNCOES_CAPTURA_CATALOGADA.ps1`: conversor B8 -> formato JSON Lines permitido pela B1, gravacao lacrada e capturador estritamente sintetico.
- `infra/retencao/FASE_21_E_B9_TESTAR_CAPTURA_CATALOGADA.ps1`: teste local com subprocessos artificiais e falhas injetadas. O teste preserva novos cenarios em `target/retencao-21eb1-testes/` e nao faz limpeza automatica.
- Este documento.

## Contrato da saida

1. `stdout` catalogado: preserva somente `timestamp`, `level`, `event`, `component`, `operation`, `outcome`, correlacoes numericas, `jobType` e, se presentes, `failureOrigin` e `failureType` validados por B8. Se existir a tripla de falha, grava **`errorCode = "REDACTED"`**, um valor fixo necessario ao contrato conjunto da B1/B3. Nao copia `errorCode` de origem.
2. `stdout` fora do catalogo: grava marcador `runtime.stdout.redacted`, sem conteudo original.
3. Qualquer `stderr`: grava marcador `runtime.stderr.redacted`, sem conteudo original.
4. Campos `asin`, `integration`, `kind`, `channel` e `errorCodeRedacted` de origem/projecao **nao** sao persistidos.
5. Linhas brutas sao lidas em blocos de 1.024 caracteres; cada linha tem teto de 32.768 caracteres. Cada segmento e criado por `CreateNew`, com ACL isolada sob `target/` e limite em bytes, sem sobrescrita ou exclusao.
6. Se a sessao esgotar o limite combinado, encontrar uma falha de gravacao ou um evento acima do limite, ambos os canais sao lacrados e as evidencias anteriores permanecem. Ao exceder o tamanho bruto de linha, o teste interrompe e fecha ambos os canais. A ausencia de um evento novo apos a falha **nao** equivale a sucesso operacional.

## Testes de aceitacao

O teste B9 verifica 24 registros por canal: 12 `stdout` catalogados (duas repeticoes de cada um dos seis codigos B8), 12 marcadores `stdout`, 24 marcadores `stderr`; valida ordem por canal, correlacoes numericas, integridade de segmentos, nao persistencia de um segredo ficticio e de stack traces. Inclui falha de filho com codigo 17, limite total combinado, linha de 40.000 caracteres, falha de escrita simulada com preservacao de hash e timeout de filho sintetico (5 s).

## Limites de aprovacao

- Os testes exigem Windows PowerShell 5.1 e permissao para criar o sandbox ACL privado ja aprovado na B1. O projeto deve estar na branch `feat/fase-21-seguranca-governanca-v1`.
- O teste pode levar alguns segundos devido ao caso de timeout. Nao remover nem editar evidencias.
- Nao foi realizada validacao automatizada em Windows no ambiente de preparo do pacote. Somente a execucao local do usuario pode aprovar o gate.
- **Proibido reutilizar `Invoke-21EB9-CapturaCatalogadaSintetica` no supervisor real ou qualquer processo Java.** Integracao produtiva, rotacao/retencao automatica, parada supervisionada e politica de backup dependem de etapas separadas.
- A B9 adiciona um capturador especifico enquanto conserva a B7 para regressao. Antes de producao deve haver revisao de duplicacao e consolidacao arquitetural, sem alterar retroativamente evidencias.
