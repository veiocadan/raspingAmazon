-- FASE 21-C2-A - Verificacao somente leitura apos provisionamento.
-- Nao consulta senhas, pg_authid, dados de negocio nem conteudo da tabela.

\set ON_ERROR_STOP on
\pset pager off
\pset null '[NULL]'

BEGIN TRANSACTION READ ONLY;

\echo === C2-A.1 - Contexto ===
SELECT current_database() AS database_name, current_user AS executed_by;

\echo === C2-A.2 - Atributos das identidades ===
SELECT rolname AS role_name,
       rolcanlogin AS can_login,
       rolinherit AS inherits,
       rolsuper AS is_superuser,
       rolcreatedb AS can_create_database,
       rolcreaterole AS can_create_role,
       rolreplication AS replication,
       rolbypassrls AS bypass_row_security
FROM pg_catalog.pg_roles
WHERE rolname IN ('rasping', 'rasping_migrator', 'rasping_runtime')
ORDER BY rolname;

\echo === C2-A.3 - Memberships das identidades ===
SELECT member.rolname AS member_role,
       granted.rolname AS granted_role,
       m.admin_option, m.inherit_option, m.set_option
FROM pg_catalog.pg_auth_members m
JOIN pg_catalog.pg_roles member ON member.oid = m.member
JOIN pg_catalog.pg_roles granted ON granted.oid = m.roleid
WHERE member.rolname IN ('rasping_migrator', 'rasping_runtime')
   OR granted.rolname IN ('rasping_migrator', 'rasping_runtime')
ORDER BY member_role, granted_role;

\echo === C2-A.4 - Ownership dos objetos public ===
SELECT pg_catalog.pg_get_userbyid(c.relowner) AS owner_role,
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
GROUP BY pg_catalog.pg_get_userbyid(c.relowner), c.relkind
ORDER BY owner_role, object_type;

\echo === C2-A.5 - Privilegios sobre schema ===
SELECT r.rolname AS role_name,
       pg_catalog.has_schema_privilege(r.oid, 'public', 'USAGE') AS can_use_public,
       pg_catalog.has_schema_privilege(r.oid, 'public', 'CREATE') AS can_create_public
FROM pg_catalog.pg_roles r
WHERE r.rolname IN ('rasping_migrator', 'rasping_runtime')
ORDER BY r.rolname;

\echo === C2-A.6 - Privilegios efetivos sobre tabelas public ===
SELECT r.rolname AS role_name,
       count(*) AS table_count,
       count(*) FILTER (WHERE pg_catalog.has_table_privilege(r.oid, c.oid, 'SELECT')) AS can_select,
       count(*) FILTER (WHERE pg_catalog.has_table_privilege(r.oid, c.oid, 'INSERT')) AS can_insert,
       count(*) FILTER (WHERE pg_catalog.has_table_privilege(r.oid, c.oid, 'UPDATE')) AS can_update,
       count(*) FILTER (WHERE pg_catalog.has_table_privilege(r.oid, c.oid, 'DELETE')) AS can_delete
FROM pg_catalog.pg_roles r
CROSS JOIN pg_catalog.pg_class c
JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND c.relkind IN ('r', 'p')
  AND r.rolname IN ('rasping_migrator', 'rasping_runtime')
GROUP BY r.rolname
ORDER BY r.rolname;

ROLLBACK;
\echo === C2-A: auditoria concluida, sem alteracoes ===
