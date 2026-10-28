# FASE 21-E-B3 — Compatibilidade de logs Java e diagnosticos do processo (offline)

## Objetivo

Verificar, antes de conectar capturadores ao supervisor, que o formato real emitido por `JsonStructuredOperationalLogAdapter` e as mensagens de erro de `ContinuousProcessingMain` possuem tratamentos separados. **Nao se trata de captura produtiva.**

## Fontes verificadas

- `src/main/java/com/raspingamazon/infrastructure/observability/JsonStructuredOperationalLogAdapter.java`: gera JSON Lines com identificadores e dimensoes de correlacao.
- `src/main/java/com/raspingamazon/infrastructure/bootstrap/ContinuousProcessingBootstrap.java`: `PrintWriter(System.out, true)`.
- `src/main/java/com/raspingamazon/infrastructure/bootstrap/ContinuousProcessingMain.java`: mensagens textuais de erro enviadas a `System.err` com `SafeOperationalError.describe`.
- `infra/postgres/security/FASE_21_C2_E_D_D_D_C_C_INICIAR_SUPERVISIONADO.ps1`: utiliza `ProcessStartInfo`, sem redirecionamento explicito de stdout/stderr.

## Politica experimental de tratamento

1. `stdout` com JSON estruturado compativel e campos identificadores dentro do formato conservador e aceito pelo contrato B3 pode ser gravado pela sessao **sintetica** B1.
2. Linhas de `stdout` fora do contrato geram marcador estruturado de ocorrencia, sem persistir conteudo bruto.
3. Mensagens textuais conhecidas em `stderr` produzem marcadores com codigos operacionais estaveis. Qualquer outra mensagem, inclusive stack traces, produz um marcador redigido, sem texto bruto.
4. Uma linha de `stderr` **nunca** e tratada como log Java autenticado. Nenhum dos canais constitui mecanismo de autenticacao da origem.
5. Marcadores sao evidencia de que houve uma saida nao estruturada, nao prova do conteudo original da saida. Nenhum hash do texto original e armazenado.
6. O escopo e `target/retencao-21eb1-testes`, com ACL e limites da B1. Nao existe rotina destrutiva, nem uso do destino operacional.

## Limites desta prova

- O teste usa exemplos sinteticos dos formatos Java e inspecao estatica do codigo; nao executa Java ou daemon.
- Ainda nao existem limites comprovados de **leitura** para linha excessivamente longa nem politica completa de backpressure na captura de um processo real.
- O padrao de strings permitidas nao e um detector perfeito de segredos. A protecao primaria permanece nos contratos Java de observabilidade e diagnostico. Qualquer futuro modo produtivo requer revisao adicional das fontes produtoras e testes com segredos ficticios.
- A classificacao pode sacrificar detalhes diagnosticos, intencionalmente, em favor de nao persistir saidas nao confiaveis. Se um incidente exigir prova integral, o fluxo de coleta autorizado devera ser desenhado separadamente com protecoes proprias.
- `preparation_only`, credenciais, supervisor, PostgreSQL e backups permanecem inalterados.
- A etapa B4 devera demonstrar leitura concorrente de saidas heterogeneas sem bloquear o processo filho e sem vazamento de texto bruto; o supervisor so pode ser alterado apos novos gates.

## Execucao prevista

Executar em Windows PowerShell 5.1 a partir da raiz do projeto:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'infra/retencao/FASE_21_E_B3_TESTAR_COMPATIBILIDADE.ps1'
```

O teste nao requer PostgreSQL, Maven ou internet. O resultado obtido deve ser registrado separadamente apos execucao real.
