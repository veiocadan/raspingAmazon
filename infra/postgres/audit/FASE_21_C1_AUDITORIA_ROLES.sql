-- FASE 21-C1 — auditoria somente leitura das identidades PostgreSQL.
-- Nao altera roles, senhas, tabelas, schema ou dados.
-- Execute conectado ao banco rasping_amazon com psql.
-- Relatorio nao consulta pg_authid nem qualquer segredo.

\set ON_ERROR_STOP on
\pset pager off
\pset null '[NULL]'

BEGIN TRANSACTION READ ONLY;

\echo === C1.1 — Contexto de conexao ===
SELECT current_database() AS database_name,
       current_user AS connected_as,
       session_user AS session_role,
       current_setting('server_version') AS server_version;

\echo === C1.2 — Roles nao internas e atributos ===
SELECT rolname AS role_name,
       rolcanlogin AS can_login,
       rolsuper AS is_superuser,
       rolcreatedb AS can_create_database,
       rolcreaterole AS can_create_role,
       rolreplication AS replication,
       rolbypassrls AS bypass_row_security
FROM pg_catalog.pg_roles
WHERE rolname !~ '^pg_'
ORDER BY rolname;

\echo === C1.3 — Proprietario e ACL do banco ===
SELECT d.datname AS database_name,
       pg_catalog.pg_get_userbyid(d.datdba) AS owner_role,
       d.datacl::text AS explicit_database_acl
FROM pg_catalog.pg_database d
WHERE d.datname = current_database();

\echo === C1.4 — Proprietario e ACL do schema public ===
SELECT n.nspname AS schema_name,
       pg_catalog.pg_get_userbyid(n.nspowner) AS owner_role,
       n.nspacl::text AS explicit_schema_acl
FROM pg_catalog.pg_namespace n
WHERE n.nspname = 'public';

\echo === C1.5 — Inventario de objetos e proprietarios ===
SELECT n.nspname AS schema_name,
       pg_catalog.pg_get_userbyid(c.relowner) AS owner_role,
       CASE c.relkind
           WHEN 'r' THEN 'TABLE'
           WHEN 'p' THEN 'PARTITIONED_TABLE'
           WHEN 'S' THEN 'SEQUENCE'
           WHEN 'v' THEN 'VIEW'
           WHEN 'm' THEN 'MATERIALIZED_VIEW'
           ELSE c.relkind::text
       END AS object_type,
       count(*) AS object_count
FROM pg_catalog.pg_class c
JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND c.relkind IN ('r', 'p', 'S', 'v', 'm')
GROUP BY n.nspname, pg_catalog.pg_get_userbyid(c.relowner), c.relkind
ORDER BY object_type, owner_role;

\echo === C1.6 — Privilegios efetivos sobre tabelas (por role) ===
SELECT r.rolname AS role_name,
       count(*) AS total_tables,
       count(*) FILTER (WHERE pg_catalog.has_table_privilege(r.oid, c.oid, 'SELECT')) AS can_select,
       count(*) FILTER (WHERE pg_catalog.has_table_privilege(r.oid, c.oid, 'INSERT')) AS can_insert,
       count(*) FILTER (WHERE pg_catalog.has_table_privilege(r.oid, c.oid, 'UPDATE')) AS can_update,
       count(*) FILTER (WHERE pg_catalog.has_table_privilege(r.oid, c.oid, 'DELETE')) AS can_delete,
       count(*) FILTER (WHERE pg_catalog.has_table_privilege(r.oid, c.oid, 'TRUNCATE')) AS can_truncate
FROM pg_catalog.pg_roles r
CROSS JOIN pg_catalog.pg_class c
JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND c.relkind IN ('r', 'p')
  AND r.rolname !~ '^pg_'
GROUP BY r.rolname
ORDER BY r.rolname;

\echo === C1.7 — Privilegios efetivos sobre sequences (por role) ===
SELECT r.rolname AS role_name,
       count(*) AS total_sequences,
       count(*) FILTER (WHERE pg_catalog.has_sequence_privilege(r.oid, c.oid, 'USAGE')) AS can_use,
       count(*) FILTER (WHERE pg_catalog.has_sequence_privilege(r.oid, c.oid, 'SELECT')) AS can_select,
       count(*) FILTER (WHERE pg_catalog.has_sequence_privilege(r.oid, c.oid, 'UPDATE')) AS can_update
FROM pg_catalog.pg_roles r
CROSS JOIN pg_catalog.pg_class c
JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND c.relkind = 'S'
  AND r.rolname !~ '^pg_'
GROUP BY r.rolname
ORDER BY r.rolname;

\echo === C1.8 — Privilegios efetivos no banco e schema public ===
SELECT r.rolname AS role_name,
       pg_catalog.has_database_privilege(r.oid, current_database(), 'CONNECT') AS database_connect,
       pg_catalog.has_database_privilege(r.oid, current_database(), 'CREATE') AS database_create,
       pg_catalog.has_schema_privilege(r.oid, 'public', 'USAGE') AS public_schema_usage,
       pg_catalog.has_schema_privilege(r.oid, 'public', 'CREATE') AS public_schema_create
FROM pg_catalog.pg_roles r
WHERE r.rolname !~ '^pg_'
ORDER BY r.rolname;

\echo === C1.9 — Privilegios padrao definidos pelos proprietarios ===
SELECT pg_catalog.pg_get_userbyid(d.defaclrole) AS owner_role,
       coalesce(n.nspname, '(todos os schemas)') AS schema_name,
       d.defaclobjtype AS object_type,
       d.defaclacl::text AS default_acl
FROM pg_catalog.pg_default_acl d
LEFT JOIN pg_catalog.pg_namespace n ON n.oid = d.defaclnamespace
ORDER BY owner_role, schema_name, object_type;

ROLLBACK;
\echo === Auditoria C1 concluida: nenhuma alteracao foi executada ===
