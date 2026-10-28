# FASE 21-C2-E-A — Validação das identidades antes de ativar LOGIN

**Entrada confirmada:** commit `018efa0`, branch `feat/fase-21-seguranca-governanca-v1`, C2-D `VERIFICACAO PASS`, proprietário `rasping_migrator` de `public`, 29 tabelas e 26 sequences. Grants de `rasping_runtime` preservados na transferência C2-D (27 SELECT / 25 INSERT / 12 UPDATE / 23 USAGE). As duas roles ainda estão `NOLOGIN NOINHERIT` e `rasping` segue como login da aplicação.

## Objetivo e escopo deste gate

Provar **permissões efetivas** após a transferência de ownership e levantar as condições de autenticação para a futura troca de identidades. Não habilitar LOGIN, não definir senhas, não editar `.env` ou Compose, não executar migrations e não usar a conta restrita na aplicação ainda.

## Arquivos completos

1. `infra/postgres/security/FASE_21_C2_E_A_AUDITAR_AUTENTICACAO.sql` — auditoria administrativa `READ ONLY`: roles, ausência de senha, método padrão SCRAM e métodos `pg_hba.conf`, sem imprimir senha, hash, endereço de conexão ou segredo.
2. `infra/postgres/security/FASE_21_C2_E_A_VERIFICAR_ACESSO_RUNTIME.sql` — `SET LOCAL ROLE rasping_runtime` dentro de transação `READ ONLY`, conferência de privilégio por objeto e tentativa de `SELECT ... LIMIT 0` das 27 tabelas operacionais; nenhuma linha empresarial é lida.
3. `infra/postgres/security/FASE_21_C2_E_A_VERIFICAR_ACESSO_MIGRATOR.sql` — `SET LOCAL ROLE rasping_migrator` em transação `READ ONLY`, conferência dos proprietários e direito de leitura das tabelas de migração, **sem executar DDL**.

## Execução: PowerShell, na raiz do repositório

```powershell
$ErrorActionPreference = "Stop"
Set-Location "C:\dev\rasping-amazon"

$files = @(
    "infra/postgres/security/FASE_21_C2_E_A_AUDITAR_AUTENTICACAO.sql",
    "infra/postgres/security/FASE_21_C2_E_A_VERIFICAR_ACESSO_RUNTIME.sql",
    "infra/postgres/security/FASE_21_C2_E_A_VERIFICAR_ACESSO_MIGRATOR.sql"
)

foreach ($file in $files) {
    Write-Host "=== Executando $file ==="
    Get-Content -Raw -Encoding UTF8 $file | docker compose -f infra/compose.yml exec -T postgres psql -X -U rasping -d rasping_amazon -v ON_ERROR_STOP=1 -f -
    if ($LASTEXITCODE -ne 0) { throw "Falha no gate C2-E-A: $file" }
}

git diff --check
git status
```

## Critérios de aprovação

- Auditoria `C2-E-A AUTENTICACAO PASS`: duas roles `NOLOGIN`, sem senhas e sem membership; `password_encryption = scram-sha-256`; métodos de autenticação mostrados sem informações sensíveis.
- Simulação `C2-E-A RUNTIME PASS`: nenhuma permissão administrativa; 29 tabelas, 27 SELECT, 25 INSERT, 12 UPDATE, 0 DELETE e 0 TRUNCATE; 26 sequences, 23 USAGE e 0 UPDATE; histórico Flyway e `configuration_version` isolados.
- Simulação `C2-E-A MIGRATOR PASS`: owner do schema `public`, 29 tabelas e 26 sequences, sem superuser e sem LOGIN, acesso aos metadados Flyway.
- Os testes **não verificam autenticação real por senha**, nem demonstram todas as rotas de escrita em execução; isso fica para os próximos gates C2-E, com segredo protegido e controle de rollback.

## Atenção aos métodos pg_hba.conf

A auditoria agrega `connection_type` e `auth_method` sem expor IPs ou usuários. Se exibir `REVISAR`, não liberar novas identidades antes de avaliar a política de acesso. A visão `pg_hba_file_rules` examina o conteúdo corrente do arquivo e não garante, por si, que o servidor já tenha recarregado essa configuração.

## Limites

Não registrar senhas em Git, PowerShell History ou logs. Não habilitar `LOGIN`, não rotacionar `DB_PASSWORD` e não editar nenhum Java/Compose nesta subetapa. Manter backup externo `C:\dev\rasping-amazon-backups\rasping_amazon_c2d_20261012_174306.dump` e o banco temporário restaurado. Evitar o antigo verificador `C2-C_VERIFICAR_PRIVILEGIOS_RUNTIME.sql` após C2-D porque ele ainda espera que o migrator não seja proprietário de objetos.

**Gate atual:** instalar os 4 arquivos, executar as três auditorias, enviar saídas e `git status`; **não fazer commit ou push antes da análise**.
