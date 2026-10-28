# FASE 21-C2-D — Preparação e transferência controlada de ownership

**Estado inicial esperado:** branch `feat/fase-21-seguranca-governanca-v1`, commit `5db5b4b`, PostgreSQL 18.6, 29 tabelas e 26 sequences. Os privilégios C2-C da role `rasping_runtime` estão aplicados; ambas as roles técnicas permanecem `NOLOGIN NOINHERIT`.

## Objetivo

Separar o proprietário dos objetos (`rasping_migrator`) da role que será utilizada pela aplicação (`rasping_runtime`). Não alterar a identidade de login nesta etapa. O login `rasping` continua disponível ao runtime atual e conserva SUPERUSER; o banco `rasping_amazon` continua pertencendo a `rasping`.

## Arquivos

1. `FASE_21_C2_D_PRECHECK_TRANSFERENCIA_OWNER.sql` — valida inventário exato e grants em transação `READ ONLY`. **Executar primeiro e enviar resultado para revisão.**
2. `FASE_21_C2_D_TRANSFERIR_OWNER_MIGRATOR.sql` — transfere em uma única transação ownership das 29 tabelas, 26 sequences e schema `public` para `rasping_migrator`; sem mover dados, alterar estrutura de colunas, sequência numérica ou ACL da role runtime.
3. `FASE_21_C2_D_VERIFICAR_OWNER_MIGRATOR.sql` — valida ownership, inventário, grants e atributo `NOLOGIN` após a aplicação; somente leitura.
4. `FASE_21_C2_D_REVERTER_OWNER_MIGRATOR.sql` — restitui owners anteriores em transação; somente antes de qualquer migração subsequente ou alteração de roles/credenciais. **Não executar sem decisão explícita.**

## Pré-condições

- Realizar backup verificável antes de qualquer execução do script de transferência. **Não executar o script que transfere ownership durante este primeiro gate.**
- Suspender escrita/daemon em janela de manutenção previamente autorizada para transferência futura. `ALTER TABLE OWNER` obtém locks; `lock_timeout=5s` evita espera prolongada e qualquer erro aborta a transação.
- Não editar `.env`, `compose.yml`, migrations V1–V37 ou arquivos Java. Não recriar container/volume.
- Confirmar que scripts estão no mesmo diretório e manter arquivo de rollback acessível.

## Verificações da C2-D

- Precheck aborta se mudar identidade, role, atributo, membership, owner do schema, conjunto de objetos ou grants C2-C.
- Enumeração fixa por nome: 29 tabelas e 26 sequences (incluindo Flyway e `configuration_version`). Não usar `REASSIGN OWNED` por envolver objetos fora da lista.
- A transferência, quando autorizada, preserva as concessões C2-C da role runtime: 27 SELECT; 25 INSERT; 12 UPDATE; 23 USAGE; zero DELETE e TRUNCATE. Exclui a role runtime da tabela Flyway e de `configuration_version`.
- Não concede `LOGIN`, senha ou membership, nem privilege `CREATE` ao runtime.
- **Próximas migrations:** requerem grants explícitos para novos objetos; evitar default grants amplos automáticos ao runtime.

## Executar agora — somente precheck

```powershell
Get-Content -Raw -Encoding UTF8 "infra/postgres/security/FASE_21_C2_D_PRECHECK_TRANSFERENCIA_OWNER.sql" | docker compose -f infra/compose.yml exec -T postgres psql -X -U rasping -d rasping_amazon -v ON_ERROR_STOP=1 -f -

git status
```

## Limite deste gate

Somente a auditoria READ ONLY está autorizada até confirmar o relatório. Manter `rasping_migrator`/`rasping_runtime` como `NOLOGIN`. Não executar transferência nem rollback nesta etapa inicial.

## Correção de auditoria C2-D (12/10/2026)

A primeira pré-verificação detectou que `has_sequence_privilege` recebia OIDs de objetos que não eram sequences (`pg_toast_*`) devido à reordenação de avaliação pelo otimizador do PostgreSQL. O comando interrompeu na transação `READ ONLY`, **sem alterar o banco**.

Os quatro scripts SQL foram corrigidos para obter os OIDs por meio de CTEs `MATERIALIZED` filtradas por `public` e `relkind = 'S'` **antes** de executar `has_sequence_privilege`. Essa barreira evita que o otimizador avalie a função para tabelas ou objetos internos. Nenhum número, concessão, identidade, owner ou comando DDL foi alterado pela correção.

**Gate atual:** substituir os cinco arquivos completos e executar **apenas** `FASE_21_C2_D_PRECHECK_TRANSFERENCIA_OWNER.sql`. Não executar transferência de propriedade, rollback ou commit até a pré-verificação terminar com `C2-D PRECHECK PASS` e ser revisada.
