# FASE 21-F — Backup e restauração PostgreSQL

## Objetivo

Criar um backup **real** do banco `rasping_amazon` via PostgreSQL 18.6 e comprovar restauração integral em um **contêiner diferente**. Não usar o backup de `pg_hba.conf` como substituto do backup de dados.

## Segurança operacional

- A política do processamento contínuo permanece `preparation_only` (quatro flags falsas); retenção permanece `inventory_only` (sem exclusões).
- A verificação inicial **não abre conexão com o banco**: confere branch, políticas, versão da imagem e exposição Docker em `127.0.0.1`.
- A execução real exige `-Modo BackupERestauracao -ConfirmarBackupRestauracao` e deve ocorrer **somente após autorização humana específica para backup e restauração**. As flags de startup não são alteradas.
- Usa `pg_dump` da mesma imagem `postgres:18.6`, em contêiner temporário que compartilha a rede do Postgres original. A role `rasping_migrator` autentica com segredo DPAPI transmitido **apenas por stdin**; o segredo não aparece na linha de comando.
- Grava o arquivo custom `PGDMP` e seu manifesto SHA-256 sob `C:\dev\rasping-amazon-backups\postgres` (fora do Git, ACL privada). Não sobrescreve arquivos.
- A restauração usa **novo contêiner de teste** (`--network none`, nenhuma porta publicada) e a imagem `postgres:18.6`. Apenas nele é criado `rasping_amazon`; nenhum SQL de escrita é dirigido ao banco original.
- `pg_restore --exit-on-error` reconstrói objetos e dados. O teste compara contagens das tabelas `processing_job`, `processing_run`, `publication_outbox`, `processing_schedule`, `flyway_schema_history` e total de tabelas públicas entre origem e restauração. Se a origem for alterada durante a operação, a comparação poderá reprovar conservadoramente.
- Ao final, o contêiner de restauração é **parado e preservado** (não removido). O backup e evidências também são preservados; não existe exclusão ou política automática de expiração neste script.

## Arquivo

`infra/postgres/backup/FASE_21_F_BACKUP_RESTAURACAO.ps1`

## Gate 1 — Verificação sem acessar o banco

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'infra/postgres/backup/FASE_21_F_BACKUP_RESTAURACAO.ps1'
if ($LASTEXITCODE -ne 0) { throw 'Preflight 21-F reprovado' }
```

## Gate 2 — Backup e restauração reais (após autorização específica)

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'infra/postgres/backup/FASE_21_F_BACKUP_RESTAURACAO.ps1' -Modo BackupERestauracao -ConfirmarBackupRestauracao
if ($LASTEXITCODE -ne 0) { throw 'Backup ou restauracao reprovados: preservar evidencias' }
```

**Atenção:** Gate 2 inicia contêiner temporário, lê o banco original, grava arquivos fora do Git e cria um banco de **teste** no contêiner isolado. Nunca execute Gate 2 para um ambiente não autorizado. Os scripts não executam Flyway, não iniciam `ContinuousProcessingMain` e não abrem portas para o banco restaurado.

## Critérios de aceite

1. Backup custom gerado, SHA-256 e manifesto gravados sem sobrescrita.
2. `pg_restore` sem erro em contêiner isolado.
3. Contagens, esquema e histórico Flyway preservados.
4. Prova de restauração gravada; backup original e banco de origem preservados.

Se falhar, manter arquivos e contêiner de teste para diagnóstico. **Não executar comandos de limpeza, DROP DATABASE ou restauração no contêiner original**.
