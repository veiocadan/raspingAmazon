# FASE 21-C2-E-C-C-A — Preparacao para aplicacao do HBA seguro

## Estado de entrada

- Commit local `c05801a`: proposta de sete regras HBA SCRAM e teste real de autenticacao TCP com senha aprovados.
- PostgreSQL 18.6 (Docker), porta publicada no host apenas em `127.0.0.1:5432`.
- O HBA efetivo anterior permanece com duas regras `local trust`, quatro `host trust` loopback e uma regra geral SCRAM.
- `rasping_migrator` e `rasping_runtime` continuam `NOLOGIN`. Ownership migrado ao `rasping_migrator`.
- Backup logico PostgreSQL anterior validado em banco de teste; manter ambos.

## Objetivo deste gate

Estabelecer backup **real e binariamente verificado** do `pg_hba.conf` ativo no servidor, salvo **fora do Git** juntamente com um manifesto para recuperacao. Verificar que a proposta versionada continua contendo as sete regras esperadas. **Nao aplicar nem recarregar** autenticacao nesta etapa.

## Arquivos completos deste pacote

1. `infra/postgres/security/FASE_21_C2_E_C_C_A_PRECHECK_HBA_ORIGINAL.sql`: auditoria READ ONLY do HBA atual, da versao e da credencial administrativa SCRAM; falha em divergencias.
2. `infra/postgres/security/FASE_21_C2_E_C_C_A_VALIDAR_POLITICA_PROPOSTA.ps1`: compara estaticamente as sete regras do arquivo proposto, sem instalacao ou reload. **Nao substitui o parser nativo PostgreSQL.**
3. `infra/postgres/security/FASE_21_C2_E_C_C_A_BACKUP_HBA.ps1`: identifica o HBA pelo `SHOW hba_file`, copia via `docker cp`, confere SHA-256 antes/depois e grava manifesto com metadados originais fora do repositorio. Nao modifica o servidor.
4. `infra/postgres/security/FASE_21_C2_E_C_C_A_RESTAURAR_HBA_ORIGINAL.ps1`: **APENAS para recuperacao autorizada no futuro**, exige `-ConfirmarRestauracao`; verifica o manifesto e hash, restaura HBA com Docker e recarrega com `pg_ctl`, sem depender de `psql`.
5. Este documento.

## Gate de execucao: somente preparacao (PowerShell do Windows)

Descompactar o ZIP na raiz `C:\dev\rasping-amazon`, preservando pastas. Nenhum arquivo anterior precisa ser substituido. O backup ficara fora do Git em `C:\dev\rasping-amazon-backups\hba`.

```powershell
Set-Location "C:\dev\rasping-amazon"
$ErrorActionPreference = "Stop"

Get-Content -Raw -Encoding UTF8 "infra/postgres/security/FASE_21_C2_E_C_C_A_PRECHECK_HBA_ORIGINAL.sql" |
    docker compose -f infra/compose.yml exec -T postgres psql -X -U rasping -d rasping_amazon -v ON_ERROR_STOP=1 -f -
if ($LASTEXITCODE -ne 0) { throw "Falha na verificacao HBA original" }

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "infra/postgres/security/FASE_21_C2_E_C_C_A_VALIDAR_POLITICA_PROPOSTA.ps1"
if ($LASTEXITCODE -ne 0) { throw "Falha na politica HBA proposta" }

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "infra/postgres/security/FASE_21_C2_E_C_C_A_BACKUP_HBA.ps1"
if ($LASTEXITCODE -ne 0) { throw "Falha no backup do HBA" }

Get-ChildItem "C:\dev\rasping-amazon-backups\hba" -File | Select-Object Name, Length

docker compose -f infra/compose.yml ps
git diff --check
git status
```

Se a politica offline falhar, nao aplicar nenhuma alteracao. Se a copia ou comparacao de SHA-256 falhar, nao prosseguir. Enviar as linhas `PASS`, hashes e o status Git, **nao enviar o conteudo integral do HBA nem credenciais**.

## Reversao preparada, proibida neste gate

O arquivo `..._RESTAURAR_HBA_ORIGINAL.ps1` nao deve ser executado agora. Em caso de falha num gate futuro, a restauracao devera apontar para o manifesto `.json` de backup correspondente; o comando exato sera orientado somente apos uma falha confirmada. O procedimento usa `docker cp`, `chown`, `chmod` e `pg_ctl reload` em vez de depender de SQL quando uma troca de HBA bloqueou as conexoes.

`docker cp` pode criar arquivo com ownership diferente no destino; por isso o manifesto registra UID, GID e modo para restauracao (Docker Docs). O reload reinterpreta o HBA, mas sucesso no `pg_ctl reload` **nao garante** que a politica passou a valer; testes de login reais continuam obrigatorios.

## Fora do escopo

- Nao modificar `pg_hba.conf` ativo, nem executar reload/restart.
- Nao habilitar logins tecnicos, alterar senhas, aplicar grants ou ownership.
- Nao fazer `git commit` ou push ate validar os resultados.

## Referencias

- PostgreSQL 18 — pg_hba.conf: https://www.postgresql.org/docs/18/auth-pg-hba-conf.html
- PostgreSQL 18 — pg_hba_file_rules: https://www.postgresql.org/docs/18/view-pg-hba-file-rules.html
- PostgreSQL 18 — pg_ctl reload: https://www.postgresql.org/docs/18/app-pg-ctl.html
- Docker — docker cp: https://docs.docker.com/reference/cli/docker/container/cp/
