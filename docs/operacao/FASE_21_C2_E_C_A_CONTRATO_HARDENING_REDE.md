# FASE 21-C2-E-C-A — Restricao de porta do PostgreSQL ao host local

## Estado aprovado para este gate

- Commit de entrada: `1b621fc` na branch `feat/fase-21-seguranca-governanca-v1`, Git limpo.
- O PostgreSQL 18.6 preserva o banco `rasping_amazon`, com 29 tabelas e 26 sequences sob `rasping_migrator`.
- `rasping_runtime` e `rasping_migrator` permanecem `NOLOGIN`; `rasping` segue como identidade de conexao atual.
- A C2-E-B identificou quatro regras `host trust` restritas a loopback, duas regras `local trust`, uma regra `host scram-sha-256` geral, `listen_addresses='*'` e a porta Docker exposta a todas as interfaces.
- Backup recuperavel fora do Git: `C:\dev\rasping-amazon-backups\rasping_amazon_c2d_20261012_174306.dump`; banco de restauração `rasping_amazon_c2d_restore_test` deve ser preservado.

## Objetivo desta subetapa

Reduzir a porta Docker publicada no host de `5432:5432` para `127.0.0.1:5432:5432`, mantendo inalterados a imagem, os volumes, as variaveis do PostgreSQL, o banco, o schema, as roles e os grants. **Nao modifica pg_hba.conf, senhas, LOGIN ou SSL.**

Essa restricao protege o acesso atraves da porta publicada no host Windows; **nao** isola outros containers na mesma rede Docker e **nao** corrige as regras `trust` para clientes que alcançam os enderecos correspondentes. O PostgreSQL permanece escutando em todas as interfaces **dentro** do container; essa propriedade sera tratada no desenho de rede e autenticacao subsequentes.

## Arquivos completos

1. `infra/compose.yml`: mesmo arquivo Compose, alterada somente a linha de `ports` para `127.0.0.1:5432:5432`.
2. `infra/postgres/security/FASE_21_C2_E_C_A_COMPOSE_ANTERIOR.yml`: copia integral do Compose anterior para procedimento de reversao. **Nao** executar este arquivo como composicao simultanea.
3. `infra/postgres/security/FASE_21_C2_E_C_A_PRECHECK_REDE.sql`: SQL transacional somente leitura que confirma roles, ownership, HBA valido e configuracao geral; nao imprime segredos, enderecos de HBA, nem modifica objetos.
4. Este documento operacional.

## Primeiro gate — instalar e verificar SEM recriar o container

Descompactar o ZIP na raiz `C:\dev\rasping-amazon`, preservando pastas e substituindo **somente** `infra/compose.yml` (mais os tres arquivos novos).

No PowerShell:

```powershell
Set-Location "C:\dev\rasping-amazon"
$ErrorActionPreference = "Stop"

# Comparar a unica alteracao esperada no Compose.
git diff -- infra/compose.yml

docker compose -f infra/compose.yml config --quiet
if ($LASTEXITCODE -ne 0) { throw "Compose invalido - nao recriar" }

Get-Content -Raw -Encoding UTF8 "infra/postgres/security/FASE_21_C2_E_C_A_PRECHECK_REDE.sql" |
  docker compose -f infra/compose.yml exec -T postgres psql -X -U rasping -d rasping_amazon -v ON_ERROR_STOP=1 -f -
if ($LASTEXITCODE -ne 0) { throw "Precheck falhou - nao recriar" }

docker compose -f infra/compose.yml ps
git diff --check
git status
```

**Esperado:** o `git diff` mostra somente o endereco de bind na linha de `ports`; `config --quiet` sem erro, `C2-E-C-A PRECHECK PASS` e o container ainda mostra o bind **antigo** em `0.0.0.0:5432` (porque **nao** foi recriado). Registrar saida antes de prosseguir.

## Segundo gate — somente apos revisao do primeiro gate

1. Confirmar janela de manutencao; parar processamento Java, scheduler e worker; verificar ausencia de conexoes ao banco e preservar backup validado.
2. Confirmar que clientes externos nao dependem do acesso a `5432` no host por rede. Se dependerem, esse bind quebra a conexao e a mudanca deve ser revista.
3. Recriar somente o servico `postgres` com Compose, **sem** remover o volume e sem `down -v`.
4. Verificar `docker compose ps`, limites de porta no Docker (`HostIp=127.0.0.1`), conexao e inventario do banco. O comando de recriacao sera autorizado **depois** do primeiro gate.
5. Rollback controlado: restaurar o arquivo completo `FASE_21_C2_E_C_A_COMPOSE_ANTERIOR.yml` para `infra/compose.yml` e recriar o servico; nao alterar volumes ou credenciais. Somente executar se a verificacao pos-mudanca indicar falha e apos diagnostico.

## Regras de seguranca e rollback

- **Nao** executar `docker compose up`, `down`, `down -v`, `pg_reload_conf`, `ALTER ROLE`, edicao do HBA, migracoes Flyway ou rollback neste primeiro gate.
- Nao versionar senhas, arquivos `.env`, dumps, caminhos com segredo nem historico de comandos sensiveis.
- Nao fazer commit/push antes da verificacao apos o segundo gate.
- **Importante:** a configuracao Docker Desktop e firewalls de host podem influenciar a exposicao efetiva. A configuracao desejada do Compose por si nao garante a inacessibilidade de toda rota de rede; posteriormente sera verificada a porta efetiva.
- O `pg_hba.conf` mantem `trust` para sockets locais e TCP loopback; tratar autenticacao em uma subetapa posterior com credenciais protegidas e rota administrativa antes de alteracao.

## Referencias

- Docker Compose — services/ports: https://docs.docker.com/reference/compose-file/services/
- PostgreSQL 18 — pg_hba.conf: https://www.postgresql.org/docs/18/auth-pg-hba-conf.html
- PostgreSQL 18 — trust: https://www.postgresql.org/docs/18/auth-trust.html
