# FASE 21-C2-E-D-C-B — Provisionamento controlado de credenciais PostgreSQL

## Estado de partida

A fase C2-E-D-C-A foi registrada no commit `8c1a4c7` e validou backups, SCRAM, porta loopback, Java separado e roles tecnicas `NOLOGIN` com propriedade de 29 tabelas e 26 sequences em `rasping_migrator`.

A fase C2-E-D-B (`fd89d3e`) deixou o Flyway ler `DB_MIGRATION_USER`/`DB_MIGRATION_PASSWORD` e o JDBC usar `DB_USER`/`DB_PASSWORD`, mantendo fallback temporario. Nesta C2-E-D-C-B **nenhuma variavel de runtime, Compose ou Flyway e mudada**.

## Arquivos

- `FASE_21_C2_E_D_C_B_PRECHECK_PROVISIONAMENTO.ps1`: verifica prerequisitos, sem efeitos.
- `FASE_21_C2_E_D_C_B_PROVISIONAR_ROLES.ps1`: gera duas senhas CSPRNG e ativa logins, quando autorizado.
- `FASE_21_C2_E_D_C_B_ALTERAR_ROLES.sql`: template transacional; **nao executar diretamente**.
- `FASE_21_C2_E_D_C_B_VERIFICAR_LOGINS.ps1`: autentica duas roles com credenciais protegidas.
- `FASE_21_C2_E_D_C_B_TESTAR_LOGINS.sh`: executa testes de autenticacao dentro do container, sem persistir segredos.
- `FASE_21_C2_E_D_C_B_DESATIVAR_LOGINS.ps1`: desativa logins e elimina hashes de senha; so executar com autorizacao explicita.
- Este documento.

## Modelo de segredos

As senhas sao **duas sequencias aleatorias independentes de 32 bytes** (64 caracteres hexadecimais) produzidas com `RandomNumberGenerator` do Windows. Sao armazenadas separadamente em `rasping_runtime.dpapi` e `rasping_migrator.dpapi` usando Windows DPAPI ligado ao usuario atual. O diretorio dedicado `C:\dev\rasping-amazon-backups\credenciais_c2edcb` recebe ACL restrita ao usuario Windows atual e `SYSTEM`, sem heranca; o diretorio deve estar fora do repositorio. Os arquivos DPAPI nao sao senhas em claro, mas devem ser protegidos contra copia e perda.

O SQL preenchido existe apenas em memoria e e enviado via STDIN ao `psql`. `stdout` e `stderr` da aplicacao SQL sao ocultos para impedir que uma eventual falha imprima as senhas. O `psql` e o teste de login nao recebem senhas em argumentos da linha de comando. Os testes transmitem os segredos em STDIN ao processo Linux, sem arquivos de senha no container. As senhas sao protegidas em repouso via DPAPI, nao por um cofre de segredos; o futuro runtime necessitara de um mecanismo de injecao de ambiente sob o mesmo usuario Windows. **Nao compartilhar a saida de erros completos da transmissao de segredos.**

O acesso as credenciais DPAPI exige o mesmo usuario Windows que executou o provisionamento, no mesmo perfil de usuario/computador. Fazer backup do material DPAPI e da conta conforme politica operacional antes de migrar de host. Nao copiar segredos para `.env.example` nem para Git.

## Pre-verificacao (SEM alteracoes)

Com worker, scheduler e app Java parados:

```powershell
Set-Location C:\dev\rasping-amazon
$ErrorActionPreference = 'Stop'
$manifesto = 'C:\dev\rasping-amazon-backups\hba\pg_hba_c2ecc_original_20261012_183108_936.json'
$backup = 'C:\dev\rasping-amazon-backups\rasping_amazon_c2d_20261012_174306.dump'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'infra/postgres/security/FASE_21_C2_E_D_C_B_PRECHECK_PROVISIONAMENTO.ps1' -CaminhoManifestoHba $manifesto -CaminhoBackupBanco $backup
if ($LASTEXITCODE -ne 0) { throw 'C2-E-D-C-B: precheck falhou' }
git status
```

Esperado: `C2-E-D-C-B PRECHECK PASS`. **Nao executar os demais arquivos antes da autorizacao.**

## Provisionamento (SOMENTE DEPOIS de precheck e autorizacao)

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'infra/postgres/security/FASE_21_C2_E_D_C_B_PROVISIONAR_ROLES.ps1' -CaminhoManifestoHba $manifesto -CaminhoBackupBanco $backup -ConfirmarProvisionamento
if ($LASTEXITCODE -ne 0) { throw 'Provisionamento nao confirmado; preservar artefatos e diagnosticar' }
```

Ao concluir: duas roles `LOGIN`, sem superuser/admin, com SCRAM, credenciais DPAPI exclusivas; app JDBC e Flyway continuam com credenciais anteriores. Validar em seguida que as contas conseguem se autenticar e que senha errada e rejeitada.

## Reversao

Se for realmente necessario e a aplicacao estiver parada:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'infra/postgres/security/FASE_21_C2_E_D_C_B_DESATIVAR_LOGINS.ps1' -ConfirmarReversao
```

Isso restaura `NOLOGIN`/`PASSWORD NULL` nas roles tecnicas, sem mudar propriedade, grants, HBA ou o administrador. Nao remove automaticamente os arquivos DPAPI locais (esses valores sao invalidados; remover/arquivar apenas apos analise). O script **nao substitui** os backups do banco/HBA.

## Riscos residuais e etapas posteriores

- As contas sao habilitadas no PostgreSQL, mas **o runtime e o Flyway nao sao migrados nesta subfase**.
- O provisionamento e conservador: falha se o diretorio DPAPI ja existir, se as roles nao estiverem NOLOGIN/sem senha ou se houver outra conexao de aplicacao. Nao e ferramenta de rotacao.
- PostgreSQL 18, pg_hba.conf com sete regras SCRAM e porta Docker em loopback sao prerequisitos.
- A futura etapa de migracao da aplicacao deve validar Flyway em papel migrator, as permissoes das migracoes novas (default privileges), a leitura DPAPI no host e a ausencia de regressao em 1.829+ testes.
- Segredos nao podem ser enviados ao chat, Git, logs, scripts versionados ou output PowerShell.
