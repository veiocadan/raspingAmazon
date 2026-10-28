# FASE 21-C2-E-C-C-B — Contrato de aplicacao controlada do `pg_hba.conf`

## Contexto confirmado

- Commit local `64a2c7b`: ferramentas de backup e restauracao do HBA versionadas.
- PostgreSQL 18.6 no Docker, porta host `127.0.0.1:5432`.
- Politica proposta ja versionada: sete regras `scram-sha-256`, incluindo sockets locais e TCP.
- Senha `rasping` validada por TCP nao loopback, sem revela-la.
- Roles `rasping_migrator` e `rasping_runtime` ainda `NOLOGIN`.
- Backup HBA fora do Git, manifesto fornecido pelo operador:
  `C:\dev\rasping-amazon-backups\hba\pg_hba_c2ecc_original_20261012_183108_936.json`.
- Banco de teste de restauracao e backup logico anteriores permanecem preservados.

## Objetivo e limites

Aplicar uma troca *controlada* do HBA apenas apos um gate independente de pre-verificacao. A troca deve preservar metadados originais do arquivo, usar rename no mesmo volume, recarregar HBA e comprovar que **conexoes novas** exigem senha SCRAM nas rotas socket, loopback e rede Docker. A verificacao da view `pg_hba_file_rules` sozinha nao comprova carregamento efetivo.

Nao alterar credenciais, roles, ownership, dados, migrations ou Compose. Nao reiniciar o PostgreSQL para aplicar HBA: usar somente reload.

## Arquivos completos

- `infra/postgres/security/FASE_21_C2_E_C_C_B_PRECHECK_APLICACAO.ps1` — somente auditoria; confere manifesto SHA-256, metadados e hash do arquivo ativo, Compose/porta, HBA original, proposta, autenticacao TCP e inatividade. **Nao modifica o HBA nem faz reload**.
- `infra/postgres/security/FASE_21_C2_E_C_C_B_APLICAR_HBA.ps1` — aplicacao com guarda explicita `-ConfirmarAplicacao` e recuperacao automatica via script da etapa C2-E-C-C-A em caso de falha apos iniciar a troca. **Nao executar antes de autorizacao da etapa seguinte**.
- `infra/postgres/security/FASE_21_C2_E_C_C_B_TESTAR_AUTENTICACAO.sh` — teste de tres rotas com senha correta e deliberadamente incorreta; autentica sem ecoar segredos.
- `infra/postgres/security/FASE_21_C2_E_C_C_B_VERIFICAR_HBA.sql` — validacao estrutural de sete regras sem exibir enderecos ou credenciais. E complementar ao teste de conexoes reais.
- Este documento.

## Primeiro gate: somente precheck

Antes do teste, parar Java, scheduler, worker e outras aplicacoes que mantenham conexoes ao PostgreSQL. Na raiz do repo, executar:

```powershell
Set-Location 'C:\dev\rasping-amazon'
$ErrorActionPreference = 'Stop'
$manifesto = 'C:\dev\rasping-amazon-backups\hba\pg_hba_c2ecc_original_20261012_183108_936.json'

powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'infra/postgres/security/FASE_21_C2_E_C_C_B_PRECHECK_APLICACAO.ps1' -CaminhoManifesto $manifesto
if ($LASTEXITCODE -ne 0) { throw 'Gate C2-E-C-C-B reprovado: nao executar aplicacao.' }

docker compose -f infra/compose.yml ps
git diff --check
git status
```

Esperado: `C2-E-C-C-B PRECHECK PASS`, sem alteracao ao HBA e sem reload. Se falhar, nao corrigir automaticamente o banco: analisar saida. Nao imprimir variaveis de ambiente, hashes de senha, `docker compose config` sem `--quiet`, arquivo HBA integral ou senhas. Nao enviar segredos ao chat.

## Segundo gate: aguardando autorizacao

O script `FASE_21_C2_E_C_C_B_APLICAR_HBA.ps1` so deve ser executado apos aprovacao explicita dos resultados do primeiro gate. A opcao `-ConfirmarAplicacao` impede uso acidental. Faz precheck integral novamente antes da troca.

Ao realizar a troca, copia a proposta para o container, prepara outro arquivo no diretorio do HBA com UID/GID/permissoes originais, compara SHA-256, recusa drift e faz rename. Depois envia reload por `pg_ctl`, verifica sintaxe por `pg_hba_file_rules`, e exige senha valida/invalida em tres rotas.

Se uma etapa **apos iniciar a substituicao** falhar, o script chama o restaurador da C2-E-C-C-A, independente de login SQL. Uma restauracao solicitada nao substitui verificacao humana do acesso. Se houver falha, nao reiniciar, nao prosseguir para habilitar roles, nao fazer commit: conservar os logs nao sensiveis e solicitar revisao.

## Reversao / seguranca

Backup validado da C2-E-C-C-A: `.conf` e `.json` fora do Git. Restaurador existente no commit `64a2c7b` usa `docker cp`, `chown`, `chmod` e `pg_ctl reload`. **Nao executar a restauracao por precaucao enquanto os testes passam.** Manter o container original rodando e a pasta de backup; nao usar `docker compose down -v`.

A porta no host permanece restrita a loopback. Isso nao equivale a TLS na rede interna Docker; o cluster permanece com `ssl=off`. A etapa trata autenticacao SCRAM, nao criptografia TLS.

## Referencias oficiais

- PostgreSQL 18, HBA: https://www.postgresql.org/docs/18/auth-pg-hba-conf.html
- PostgreSQL 18, `pg_hba_file_rules`: https://www.postgresql.org/docs/18/view-pg-hba-file-rules.html
- PostgreSQL 18, `pg_ctl reload`: https://www.postgresql.org/docs/18/app-pg-ctl.html
- Docker, `docker cp`: https://docs.docker.com/reference/cli/docker/container/cp/
