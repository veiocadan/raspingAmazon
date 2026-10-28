# FASE 21-E — Integracao de logs estruturados no supervisor Windows (codigo pronto, inativo)

## O que foi entregue

O launcher supervisionado existente agora redireciona `stdout` e `stderr` **somente depois** de exigir os bloqueios do startup, Git e captura versionada. Reutiliza o catalogo B8/B9, o validador do escritor D2 e as garantias de ACL do B1; nao usa o capturador sintetico com `Kill()`.

O capturador operacional implementado em `FASE_21_E_FUNCOES_CAPTURA_OPERACIONAL.ps1` classifica as linhas e armazena exclusivamente JSONL minimizado, em uma sessao privada `sessao-PID-ticks-nonce`, com `owner.v1.json` reservado atomicamente, segmentos de 16 MiB, quota de 512 MiB por sessao e sem reabertura de historico. Mensagens arbitrarias em `stderr` viram marcadores sem texto. Limites ou falhas de captura disparam pedido de parada cooperativa da JVM ja identificada. Nenhuma exclusao automatica foi acrescentada.

## Status de autorizacao (nao alterar nesta etapa)

- `config/operacao/continuous-startup-policy.v1.json`: `preparation_only`, quatro flags `false`.
- `config/operacao/retention-policy.v1.json`: `inventory_only`, captura e destruicao `false`.
- `config/operacao/supervisor-log-integration-review.v1.json`: `static_review_only`, flags `false`, `production_adapter_status=implemented_policy_gated`.
- `config/operacao/operational-log-catalog.v1.json`: catalogo `offline_validation_only`, sem autorizacao autonoma de captura.

**Nessas condicoes, nenhum daemon, logs externos, migrations ou exclusoes podem ser iniciados.** A ligacao no launcher esta implementada, mas nao foi homologada com o banco e a JVM produtiva. Uma autorizacao operacional futura exigira revisao e gate de homologacao proprio. A politica de 30 dias continua proposta, sem rotina destrutiva automatica; snapshots comerciais e auditoria PostgreSQL permanecem preservados.

## Prova minima

Execute `infra/retencao/FASE_21_E_TESTAR_INTEGRACAO_FINAL.ps1` no PowerShell 5.1, na raiz do repositorio. O teste confirma a integridade do supervisor e do contrato D1, bloqueio de captura e duas gravacoes sanitizadas em sandbox privado sob `target/`. As evidencias de teste nao sao apagadas.

O teste offline nao substitui a homologacao operacional posterior. Para impedir efeitos externos, nao execute o launcher nem altere flags durante esta validacao.
