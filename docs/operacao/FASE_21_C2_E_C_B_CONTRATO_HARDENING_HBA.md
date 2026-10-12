# FASE 21-C2-E-C-B — Preparar substituicao segura de `trust` por SCRAM

## Estado comprovado de entrada (12/10/2026)

- Branch `feat/fase-21-seguranca-governanca-v1`, commit `5d4af6a`, Git limpo.
- `infra/compose.yml` ja publicado em `127.0.0.1:5432:5432`, contêiner reiniciado e conexao com `rasping_amazon` verificada na C2-E-C-A.
- PostgreSQL 18.6, `public` e suas 29 tabelas/26 sequences sob ownership `rasping_migrator`.
- `rasping_runtime` e `rasping_migrator` permanecem `NOLOGIN NOINHERIT` sem senhas. `rasping` e o superuser que a aplicacao ainda utiliza.
- A auditoria C2-E-B apresentou sete regras HBA, quatro `host trust` somente loopback, duas `local trust` e uma `host all all all scram-sha-256`.
- Backup validado e restaurado em banco independente: `C:\dev\rasping-amazon-backups\rasping_amazon_c2d_20261012_174306.dump` e `rasping_amazon_c2d_restore_test`.

## Objetivo deste gate: evidencias, SEM alterar o HBA

Garantir que a senha atualmente cadastrada para o login `rasping` esteja no formato **SCRAM**, que o segredo `POSTGRES_PASSWORD` do contêiner ainda consiga autenticar via TCP **nao loopback** e que as sete regras HBA ainda sejam exatamente as mesmas auditadas antes de preparar a alteracao. Esse teste evita bloquear o acesso administrativo depois de substituir `trust`.

**Importante:** `password_encryption = scram-sha-256` so configura **novas senhas**; nao prova o formato da senha ja salva. E `pg_hba_file_rules` examina o arquivo atual em disco, que pode diferir da configuracao carregada; validar carregamento e conexoes apos aplicar, no proximo gate.

## Arquivos entregues

1. `infra/postgres/security/FASE_21_C2_E_C_B_PRECHECK_HBA_ADMIN.sql` — precheck READ ONLY de hash SCRAM cadastrado e de regras HBA atuais (escopo e ordem, com falha fechada em caso de divergencia). Nao devolve hash, senha, IP nem caminho.
2. `infra/postgres/security/FASE_21_C2_E_C_B_VERIFICAR_ESTADO_ROLES.sql` — confirma NOLOGIN das roles, ausencia de senha/membership, schema e ownership dos 55 objetos, sem alterar nada.
3. `infra/postgres/security/FASE_21_C2_E_C_B_TESTAR_SCRAM_TCP.sh` — teste real TCP pela rede Docker com senha obtida do ambiente interno, sem imprime-la; aborta em rota loopback.
4. `infra/postgres/security/FASE_21_C2_E_C_B_POLITICA_HBA_PROPOSTA.conf` — **proposta integral**, substituindo as seis regras `trust` por `scram-sha-256`, preservando a regra geral SCRAM. **Nao e ativa, nao copiar para PGDATA neste gate.**
5. Este documento, com contrato, testes e requisitos de reversao.

## Gate 1 — instalacao e auditorias somente leitura

Extrair o ZIP na raiz `C:\dev\rasping-amazon`, preservando pastas. O ZIP **nao** substitui `infra/compose.yml` nem edita o HBA vivo.

No PowerShell:

```powershell
Set-Location "C:\dev\rasping-amazon"
$ErrorActionPreference = "Stop"

$files = @(
    "infra/postgres/security/FASE_21_C2_E_C_B_PRECHECK_HBA_ADMIN.sql",
    "infra/postgres/security/FASE_21_C2_E_C_B_VERIFICAR_ESTADO_ROLES.sql"
)

foreach ($file in $files) {
    Write-Host "=== Executando $file ==="
    Get-Content -Raw -Encoding UTF8 $file |
        docker compose -f infra/compose.yml exec -T postgres psql -X -U rasping -d rasping_amazon -v ON_ERROR_STOP=1 -f -
    if ($LASTEXITCODE -ne 0) { throw "Precheck falhou: $file" }
}
```

**Teste de acesso administrativo por senha, sem mostrar o segredo:**

```powershell
Get-Content -Raw -Encoding UTF8 "infra/postgres/security/FASE_21_C2_E_C_B_TESTAR_SCRAM_TCP.sh" |
    docker compose -f infra/compose.yml exec -T postgres sh -s
if ($LASTEXITCODE -ne 0) { throw "Falha no teste TCP com senha SCRAM" }

docker compose -f infra/compose.yml ps
git diff --check
git status
```

- A conexao `-h postgres` resolve o endereco do servico Docker no proprio contêiner e **deve** usar a regra HBA de rede SCRAM, em vez das regras loopback `trust`.
- O script imprime `C2-E-C-B TCP SCRAM PASS` somente se conseguir conectar usando senha pela rota TCP nao loopback, com cliente e servidor usando o endereco interno do contêiner. Se nao imprimir PASS, **parar** e investigar sem aplicar HBA.
- O comando nao imprime `POSTGRES_PASSWORD`, mas o segredo permanece como variavel de ambiente de processo durante o teste. Nao usar `Write-Host $env:DB_PASSWORD`, `docker inspect` exibindo `.Config.Env` ou `docker compose config` (sem `--quiet`) em saidas compartilhadas.
- Se o nome `postgres` nao resolver dentro do contêiner, nao improvisar modificacoes de autenticacao: enviar o erro para ajustar o teste.

## Gates seguintes — NAO executar agora

Antes de qualquer alteracao no HBA ativo:

1. Confirmar que nao existem aplicacao Java, worker, scheduler, scripts ou clientes dependentes de `trust`. O comando antigo `docker compose exec ... psql -U rasping` **deixara de autenticar sem senha** apos a mudanca.
2. Preservar o arquivo HBA **real** do cluster em backup verificavel fora do Git, com permissao controlada, e preparar um procedimento independente para restaura-lo pelo Docker mesmo se `psql` parar de autenticar. O arquivo `.conf` deste pacote e uma politica futura, **nao** um backup real do arquivo atual (que contem comentarios/entradas do initdb).
3. Preparar o arquivo proposto, validar sintaxe e formato com precaucao, aplicando apenas em janela de manutencao e com rollback controlado. `pg_hba_file_rules` reflete o arquivo em disco; um `reload` explicito e testes de conexao serao necessarios para validar as regras efetivas no PostgreSQL do contêiner Linux.
4. Validar login admin por senha tanto via socket quanto TCP, conexao local do aplicativo e rolamento das regras HBA. Em falha, restaurar o HBA original via acesso Docker administrativo e executar novo reload; **nao depender da conexao psql bloqueada** para o rollback.
5. Depois, analisar a habilitacao separada dos novos logins tecnicos e a configuracao de credenciais distintas; nao misturar HBA com `ALTER ROLE ... LOGIN` no mesmo gate.

## Criterios de aprovacao do Gate 1

- `C2-E-C-B HBA PRECHECK PASS` e `C2-E-C-B ROLES PRECHECK PASS`.
- Conexao TCP de `rasping` com `POSTGRES_PASSWORD` bem-sucedida, com `C2-E-C-B TCP SCRAM PASS`.
- Publicacao da porta no Docker continua `127.0.0.1:5432` e Git mostra somente cinco arquivos novos da C2-E-C-B.
- Nao imprimir senhas ou hashes nas saidas enviadas; nao liberar os novos logins nem modificar o HBA ainda.

## Referencias

- PostgreSQL 18, HBA, primeira regra e reload: https://www.postgresql.org/docs/18/auth-pg-hba-conf.html
- PostgreSQL 18, interpretacao da view `pg_hba_file_rules`: https://www.postgresql.org/docs/18/view-pg-hba-file-rules.html
- PostgreSQL 18, metodo `scram-sha-256`: https://www.postgresql.org/docs/18/auth-password.html
