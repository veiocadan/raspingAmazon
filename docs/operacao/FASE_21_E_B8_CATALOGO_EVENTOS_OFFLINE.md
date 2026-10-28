# FASE 21-E-B8 — Catalogo fechado de eventos (offline)

## Objetivo

Conservar identificadores semanticos de observabilidade sem copiar texto livre de origem externa. Etapa isolada para validar um **contrato de dados**, nao uma autorizacao de captura, rotacao, daemon ou exclusao.

## Fonte de verdade local

- `ProcessingWorker`: `processing.job.claimed`, `processing.job.succeeded`, `processing.job.retry-scheduled`, `processing.job.dead`, `processing.job.failure-handled`.
- `BestEffortIntegrationObservationRecorder`: `integration.observation.persistence-failed`.
- `OperationalLogEvent` e `JsonStructuredOperationalLogAdapter`: estrutura geral de JSON emitido.
- `ProcessingJobType`, `ProcessingJobStatus`, `OperationalLogLevel`, `OperationalFailureOrigin`, `ProcessingFailureType`: dominios enumerados.

## Regras

1. Catalogo JSON versionado, falha fechada, com **exatamente seis entradas** conhecidas. A ordem e metadados sao verificados pelas funcoes (mudancas exigem revisao deliberada).
2. Apenas objetos JSON de `stdout` com evento + metadados + resultado identicos ao catalogo podem manter o codigo semantico. Saidas desconhecidas viram marcadores fixos.
3. `stderr` e sempre representado por marcador sem texto bruto.
4. Projecao retém timestamp parseado, severidade, evento, componente, operacao, resultado, correlacoes numericas positivas, `jobType` enumerado e `durationMs` inteiro nao negativo.
5. `asin`, `integration` e valor textual de `errorCode` nao sao persistidos nesta etapa. Metadados de falha conhecidos conservam `failureOrigin`/`failureType` e um marcador `errorCodeRedacted`.
6. Qualquer propriedade inesperada, falha de analise, incongruencia ou linha excessiva produz um marcador sem o valor bruto.
7. Os testes usam somente objetos artificiais em memoria e leem codigo-fonte local. Nao criam arquivos em `target/` nem mexem no supervisor ou nas politicas existentes.

## Limites reconhecidos

- Este e um **contrato offline experimental**. Nao foi integrado ao capturador B7 nem a JVM; nenhum efeito produtivo autorizado.
- `ConvertFrom-Json` do Windows PowerShell nao e um parser endurecido para dados hostis; nao garante deteccao estrita de chaves JSON duplicadas/casing. O pipeline produtivo precisara de parser com verificacao de duplicatas, limites de profundidade/bytes, controle de codificacao, fluxo de erro persistente e testes adversariais especificos.
- A existencia de `PrintWriter` nos produtores Java sem `checkError()` exige estudo separado: problemas de escrita nao podem ser considerados detectados apenas por um capturador externo.
- Os nomes de eventos e metadados devem continuar versionados e sujeitos a testes de regressao se o codigo Java evoluir.
- Logs locais nao substituem observacoes historicas duraveis PostgreSQL, nem autorizam apagar historicos.

## Criterios de aceite

- Inicio bloqueado (`preparation_only`) e retencao `inventory_only`.
- Correspondencia exata dos seis codigos entre catalogo e fontes Java.
- Seis casos positivos; casos negativos; textos livres descartados; mutacoes de catalogo rejeitadas.
- `git status` sem alteracoes inesperadas.

## Proxima etapa

Somente apos B8: testar a aplicacao do catalogo ao processo sintetico B7 e revisar semantica de `PrintWriter`/falhas de transporte. Nao editar supervisor real nem ativar captura com base apenas neste teste.
