# FASE 21-C2-B — Matriz de objetos e privilegios PostgreSQL

**Status:** auditoria de pré-concessão, sem alterar as roles, senhas, ownership, dados ou aplicações.

## Baseline confirmado

- Branch: `feat/fase-21-seguranca-governanca-v1`.
- C2-A: commit `34bdae1`, working tree clean.
- `rasping_migrator` e `rasping_runtime` existem com `NOLOGIN`, `NOINHERIT` e sem privilégios elevados.
- PostgreSQL 18.6; `rasping` é superuser e continua sendo proprietário das 29 tabelas e 26 sequences inventariadas na C1.
- As credenciais Flyway e Java ainda não foram separadas. Não modificar `infra/compose.yml` nem o `.env` nesta etapa.

## Objetivo

Produzir inventário real de cada relação (tabela, view), sequence, propriedade, ACL, funções e triggers do banco antes de desenhar concessões de DML e uso de sequences. O runtime **não deve receber** `CREATE`, `TRUNCATE`, `SUPERUSER`, propriedade dos objetos ou acesso de manutenção Flyway.

A matriz do PostgreSQL indica o estado de permissões existentes; ela **não determina sozinha as permissões necessárias**. Antes de conceder permissões, o mapeamento precisa ser cruzado com os comandos SQL reais de todos os adapters JDBC e com as migrations existentes, preservando privilégios mínimos para a fila, scheduler, publicação e consultas operacionais.

## Arquivo

- `infra/postgres/security/FASE_21_C2_B_AUDITAR_OBJETOS_E_PRIVILEGIOS.sql`: diagnóstico completo de catálogo em transação somente leitura.

## Execução em PowerShell

Na raiz `C:\dev\rasping-amazon`, com PostgreSQL existente em execução:

```powershell
Set-Location "C:\dev\rasping-amazon"

Get-Content -Raw -Encoding UTF8 "infra/postgres/security/FASE_21_C2_B_AUDITAR_OBJETOS_E_PRIVILEGIOS.sql" | docker compose -f infra/compose.yml exec -T postgres psql -X -U rasping -d rasping_amazon -v ON_ERROR_STOP=1 -f -

git status
```

## Critérios de aceite

- Todas as seções C2-B.1 a C2-B.8 aparecem sem erro SQL.
- `read_only = on` na C2-B.1.
- A quantidade de objetos coincide com a auditoria C1 ou qualquer divergência é registrada antes de prosseguir.
- As duas roles técnicas ainda estão com `can_login = f`, sem `CREATE`, `SUPERUSER`, nem membership.
- A saída de Flyway, ACLs, funções e triggers é verificada para dependências adicionais.
- A execução termina com `ROLLBACK` e a mensagem de leitura apenas.

## Limites e controles

- **Não executar GRANT nem REVOKE nesta etapa.**
- Não criar credenciais com `LOGIN` ainda.
- Não executar Flyway como `rasping_migrator` antes de transferir ownership e verificar o acesso ao histórico de migrations.
- Não reescrever scripts Flyway já aplicados; o baseline de schema é V37.
- Não expor `.env`, senhas ou conteúdo das tabelas no relatório.
- Após aprovação, registrar somente este documento e o script de auditoria em commit próprio.
