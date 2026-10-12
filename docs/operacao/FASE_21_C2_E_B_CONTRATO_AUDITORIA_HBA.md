# FASE 21-C2-E-B — Auditoria da abrangencia do pg_hba.conf

## Ponto de partida verificado

- Branch `feat/fase-21-seguranca-governanca-v1`, commit `bd1ea67`, Git limpo.
- PostgreSQL 18.6; schema `public`, 29 tabelas e 26 sequences pertencem a `rasping_migrator`.
- `rasping_runtime` e `rasping_migrator` ainda sao `NOLOGIN NOINHERIT`, sem senhas, sem superuser.
- C2-E-A identificou quatro regras `host trust` e duas regras `local trust`; o modo de encriptacao de novas senhas e SCRAM-SHA-256. `PASS` em C2-E-A refere-se a execucao da auditoria, **nao** a uma configuracao segura.
- A saida anterior de `docker compose ps` mostrou publicacao TCP do PostgreSQL em `0.0.0.0:5432` e `[::]:5432` no host; a exposicao efetiva depende da rede e do firewall, a serem validados separadamente.

## Objetivo

Obter evidencias sobre a **ordem e abrangencia** das regras `pg_hba.conf` antes de alterar autenticacao ou habilitar logins. Esta etapa **nao corrige** a configuracao; apenas oferece uma auditoria segura e reprodutivel para fundamentar a proxima mudanca.

## Arquivos entregues

1. `infra/postgres/security/FASE_21_C2_E_B_AUDITAR_REGRAS_HBA.sql`: regras validas ordenadas, numero de linha, tipo, metodo de autenticacao e **categorias** de abrangencia de banco, role e origem; nao imprime enderecos completos, caminhos ou segredos. Audita em destaque as regras `host trust`.
2. `infra/postgres/security/FASE_21_C2_E_B_AUDITAR_SUPERFICIE_CONEXAO.sql`: escuta TCP categorizada, TLS, tipo de hash de senha, momento do ultimo carregamento e alerta para `trust` / `md5` / `password`.
3. `infra/postgres/security/FASE_21_C2_E_B_VERIFICAR_ROLES_SEM_LOGIN.sql`: guarda `READ ONLY` do estado C2-D/C2-E-A; verifica owner e `NOLOGIN` sem consultar hashes.
4. Este documento operacional.

## Execucao no PowerShell (na raiz do projeto)

```powershell
Set-Location "C:\dev\rasping-amazon"
$ErrorActionPreference = "Stop"

$files = @(
    "infra/postgres/security/FASE_21_C2_E_B_AUDITAR_REGRAS_HBA.sql",
    "infra/postgres/security/FASE_21_C2_E_B_AUDITAR_SUPERFICIE_CONEXAO.sql",
    "infra/postgres/security/FASE_21_C2_E_B_VERIFICAR_ROLES_SEM_LOGIN.sql"
)

foreach ($file in $files) {
    Write-Host "=== Executando $file ==="
    Get-Content -Raw -Encoding UTF8 $file |
        docker compose -f infra/compose.yml exec -T postgres psql -X -U rasping -d rasping_amazon -v ON_ERROR_STOP=1 -f -
    if ($LASTEXITCODE -ne 0) { throw "Falha na auditoria C2-E-B: $file" }
}

docker compose -f infra/compose.yml ps
git diff --check
git status
```

## Analise e criterio de seguranca

- A auditoria **nao** concede aprovacao para ativar `LOGIN`. O resultado `PENDENTE: REVISAR HOST TRUST` nao e falha de execucao; indica que as regras exigem analise. Se alguma regra estiver marcada como `QUALQUER_ORIGEM`, a exposicao e critica e deve ser tratada antes de ativar identidades de conexao.
- `LOOPBACK_UNICO` significa endereco literal loopback individual, com mascara compativel; `CIDR_IP_OU_HOSTNAME_REVISAR`, `REDE_DINAMICA_REVISAR`, `MATCH_DINAMICO_REVISAR` e `OUTRO_OU_INDIRETO_REVISAR` **nao** podem ser classificados como seguros automaticamente. A categoria pode omitir regras indiretas/expandidas. Para concluir as alteracoes do HBA, e preciso examinar a configuracao completa de forma controlada.
- A ordem e essencial: o PostgreSQL usa a **primeira regra que corresponda**, sem continuar para as seguintes se a autenticacao falhar.
- `pg_hba_file_rules` le o conteudo atual **no disco**, que pode divergir das regras atualmente carregadas. A auditoria nao faz `reload`; verificar isso antes de executar mudancas.
- O acesso sem senha via `trust` e especialmente perigoso se combinado com regra `host` de abrangencia externa e com o login superuser `rasping` ainda ativo.
- `listen_addresses` e publicacao Docker sao camadas diferentes. O comando `docker compose ... ps` mostra a publicacao de porta, mas nao comprova acesso efetivo por redes externas.
- Nao transmitir senhas, hashes, URL de conexao com credenciais ou o `pg_hba.conf` integral sem revisao.

## Proibicoes e preservacao de estado

Nao executar `ALTER ROLE`, `ALTER SYSTEM`, `pg_reload_conf()`, `pg_ctl reload`, migrations Flyway, mudanca no Compose, Java, `.env`, ou `pg_hba.conf`. Nao recriar containers ou volumes. Nao fazer commit nem push antes da revisao dos resultados.

Manter o backup externo `C:\dev\rasping-amazon-backups\rasping_amazon_c2d_20261012_174306.dump` e o banco `rasping_amazon_c2d_restore_test` intactos.

## Fontes tecnicas

- PostgreSQL 18, `pg_hba_file_rules`: https://www.postgresql.org/docs/18/view-pg-hba-file-rules.html
- PostgreSQL 18, configuracao HBA e precedencia: https://www.postgresql.org/docs/18/auth-pg-hba-conf.html
- PostgreSQL 18, autenticacao `trust`: https://www.postgresql.org/docs/18/auth-trust.html
