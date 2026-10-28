# FASE 21-C2-E-D-C-A — Gate antes do provisionamento seguro de credenciais

## Objetivo e estado

**Somente leitura.** A fase C2-E-D-B separou os contratos Java (DB_USER/DB_PASSWORD para runtime, DB_MIGRATION_USER/DB_MIGRATION_PASSWORD para Flyway), com fallback legado **temporario**. Esta C2-E-D-C-A confirma as precondicoes de ativacao das duas roles PostgreSQL. Nao habilita LOGIN, nao define ou gera senhas, nao aplica grants, nao altera HBA e nao modifica o Compose.

## Arquivos entregues

- `infra/postgres/security/FASE_21_C2_E_D_C_A_EXECUTAR_PRECHECK.ps1`
- `infra/postgres/security/FASE_21_C2_E_D_C_A_EXECUTAR_SQL.sh`
- `infra/postgres/security/FASE_21_C2_E_D_C_A_PRECHECK_PROVISIONAMENTO.sql`
- Este documento.

## Portoes de seguranca

1. Backup PostgreSQL existente (e previamente restaurado no banco temporario, conforme C2-D), fora do Git.
2. Backup HBA original e manifesto C2-E-C-C-A integro, fora do Git. O HBA original e antigo: **seu hash nao deve coincidir com o HBA SCRAM ativo**.
3. `infra/compose.yml` valido e 5432 publicada apenas em `127.0.0.1`.
4. Contratos Java separados para JDBC e Flyway e `.env.example` atualizado.
5. Administrador `rasping` autenticado de verdade com SCRAM na rede interna Docker; senha propositalmente incorreta rejeitada.
6. PostgreSQL 18, HBA sem trust (sete entradas SCRAM em disco), sem regras invalidas.
7. Roles `rasping_runtime` e `rasping_migrator` sem LOGIN, sem senha e sem atributos administrativos, sem memberships.
8. Ownership do schema `public`, 29 tabelas e 26 sequences atribuido ao migrator.
9. Matriz runtime: 27 SELECT, 25 INSERT, 12 UPDATE, 23 USAGE de sequences; nenhum DELETE/TRUNCATE/CREATE ou acesso Flyway.
10. Ausencia de conexoes da aplicacao ao banco durante a verificacao.

A ausencia de outras conexoes e uma observacao pontual. Deve ser verificada novamente no momento da ativacao; mantenha worker, scheduler e aplicacao Java parados.

## Como executar

No PowerShell, a partir de `C:\dev\rasping-amazon`:

```powershell
$ErrorActionPreference = 'Stop'
Set-Location 'C:\dev\rasping-amazon'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'infra/postgres/security/FASE_21_C2_E_D_C_A_EXECUTAR_PRECHECK.ps1' -CaminhoManifestoHba 'C:\dev\rasping-amazon-backups\hba\pg_hba_c2ecc_original_20261012_183108_936.json' -CaminhoBackupBanco 'C:\dev\rasping-amazon-backups\rasping_amazon_c2d_20261012_174306.dump'
if ($LASTEXITCODE -ne 0) { throw 'C2-E-D-C-A reprovada' }
git diff --check
git status
```

**Gate esperado:** `C2-E-D-C-A GATE PASS` e quatro arquivos novos nao versionados.

## Seguranca e proxima etapa

- Os dois arquivos copiados para `/tmp` no container sao scripts sem segredos.
- A senha administrativa existente permanece exclusivamente no ambiente do container durante a autenticacao TCP.
- Nao digite nem publique senhas ou tokens. Nenhuma senha deve entrar em Git, parametros de linha de comando, logs, manifestos ou documentos.
- **Nao executar `ALTER ROLE ... LOGIN` nesta etapa** e nao editar HBA.
- A etapa seguinte definira criacao de credenciais aleatorias distintas, armazenamento seguro local, provisionamento idempotente controlado e verificacao real, antes da troca de variaveis usadas pela aplicacao.
- O backup do banco e o ambiente de restauracao permanecem preservados.
