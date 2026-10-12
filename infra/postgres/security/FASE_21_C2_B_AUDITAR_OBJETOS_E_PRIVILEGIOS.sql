-- FASE 21-C2-B - Matriz de privilegios, somente leitura.
-- Nao executa GRANT, REVOKE, ALTER ROLE, SET ROLE, DDL ou DML.
-- Inspeciona apenas metadados de schema, sem ler dados de negocio.
-- PRE-REQUISITO: C2-A concluida; conectar ao banco rasping_amazon como rasping.

\set ON_ERROR_STOP on
\pset pager off
\pset null '[NULL]'

BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;

\echo === C2-B.1 - Contexto e inventario agregado ===
SELECT current_database() AS database_name,
       current_user AS executed_by,
       current_setting('transaction_read_only') AS read_only,
       pg_catalog.current_setting('server_version') AS postgres_version;

SELECT n.nspname AS schema_name,
       CASE c.relkind
           WHEN 'r' THEN 'TABLE'
           WHEN 'p' THEN 'PARTITIONED_TABLE'
           WHEN 'S' THEN 'SEQUENCE'
           WHEN 'v' THEN 'VIEW'
           WHEN 'm' THEN 'MATERIALIZED_VIEW'
           ELSE c.relkind::text
       END AS object_type,
       pg_catalog.pg_get_userbyid(c.relowner) AS owner_role,
       count(*) AS object_count
FROM pg_catalog.pg_class c
JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND c.relkind IN ('r', 'p', 'S', 'v', 'm')
GROUP BY n.nspname, c.relkind, c.relowner
ORDER BY object_type, owner_role;

\echo === C2-B.2 - Roles tecnicas e privilegios estruturais ===
SELECT r.rolname AS role_name,
       r.rolcanlogin AS can_login,
       r.rolinherit AS inherits,
       r.rolsuper AS is_superuser,
       r.rolcreatedb AS can_create_database,
       r.rolcreaterole AS can_create_role,
       r.rolbypassrls AS bypass_row_security,
       pg_catalog.has_database_privilege(r.oid, current_database(), 'CONNECT') AS can_connect,
       pg_catalog.has_database_privilege(r.oid, current_database(), 'CREATE') AS can_create_database_objects,
       pg_catalog.has_schema_privilege(r.oid, 'public', 'USAGE') AS can_use_schema,
       pg_catalog.has_schema_privilege(r.oid, 'public', 'CREATE') AS can_create_in_schema
FROM pg_catalog.pg_roles r
WHERE r.rolname IN ('rasping', 'rasping_migrator', 'rasping_runtime')
ORDER BY r.rolname;

\echo === C2-B.3 - Matriz por tabela e view ===
SELECT c.relname AS object_name,
       CASE c.relkind
           WHEN 'r' THEN 'TABLE'
           WHEN 'p' THEN 'PARTITIONED_TABLE'
           WHEN 'v' THEN 'VIEW'
           WHEN 'm' THEN 'MATERIALIZED_VIEW'
           ELSE c.relkind::text
       END AS object_type,
       pg_catalog.pg_get_userbyid(c.relowner) AS owner_role,
       r.rolname AS role_name,
       pg_catalog.has_table_privilege(r.oid, c.oid, 'SELECT') AS can_select,
       pg_catalog.has_table_privilege(r.oid, c.oid, 'INSERT') AS can_insert,
       pg_catalog.has_table_privilege(r.oid, c.oid, 'UPDATE') AS can_update,
       pg_catalog.has_table_privilege(r.oid, c.oid, 'DELETE') AS can_delete,
       pg_catalog.has_table_privilege(r.oid, c.oid, 'TRUNCATE') AS can_truncate,
       c.relrowsecurity AS row_security_enabled,
       c.relforcerowsecurity AS row_security_forced
FROM pg_catalog.pg_class c
JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
CROSS JOIN pg_catalog.pg_roles r
WHERE n.nspname = 'public'
  AND c.relkind IN ('r', 'p', 'v', 'm')
  AND r.rolname IN ('rasping_migrator', 'rasping_runtime')
ORDER BY c.relname, r.rolname;

\echo === C2-B.4 - Matriz de sequences e dependencias ===
SELECT c.relname AS sequence_name,
       pg_catalog.pg_get_userbyid(c.relowner) AS owner_role,
       r.rolname AS role_name,
       pg_catalog.has_sequence_privilege(r.oid, c.oid, 'USAGE') AS can_use,
       pg_catalog.has_sequence_privilege(r.oid, c.oid, 'SELECT') AS can_select,
       pg_catalog.has_sequence_privilege(r.oid, c.oid, 'UPDATE') AS can_update,
       rel.relname AS owned_by_table,
       att.attname AS owned_by_column
FROM pg_catalog.pg_class c
JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
CROSS JOIN pg_catalog.pg_roles r
LEFT JOIN pg_catalog.pg_depend d
    ON d.classid = 'pg_catalog.pg_class'::pg_catalog.regclass
   AND d.objid = c.oid
   AND d.refclassid = 'pg_catalog.pg_class'::pg_catalog.regclass
   AND d.deptype IN ('a', 'i')
LEFT JOIN pg_catalog.pg_class rel ON rel.oid = d.refobjid
LEFT JOIN pg_catalog.pg_attribute att
    ON att.attrelid = d.refobjid AND att.attnum = d.refobjsubid
WHERE n.nspname = 'public'
  AND c.relkind = 'S'
  AND r.rolname IN ('rasping_migrator', 'rasping_runtime')
ORDER BY c.relname, r.rolname;

\echo === C2-B.5 - Historico Flyway: proprietario e acessos ===
SELECT c.relname AS flyway_table,
       pg_catalog.pg_get_userbyid(c.relowner) AS owner_role,
       r.rolname AS role_name,
       pg_catalog.has_table_privilege(r.oid, c.oid, 'SELECT') AS can_select,
       pg_catalog.has_table_privilege(r.oid, c.oid, 'INSERT') AS can_insert,
       pg_catalog.has_table_privilege(r.oid, c.oid, 'UPDATE') AS can_update,
       pg_catalog.has_table_privilege(r.oid, c.oid, 'DELETE') AS can_delete
FROM pg_catalog.pg_class c
JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
CROSS JOIN pg_catalog.pg_roles r
WHERE n.nspname = 'public'
  AND c.relname = 'flyway_schema_history'
  AND c.relkind IN ('r', 'p')
  AND r.rolname IN ('rasping', 'rasping_migrator', 'rasping_runtime')
ORDER BY r.rolname;

\echo === C2-B.6 - Default ACLs do schema ===
SELECT pg_catalog.pg_get_userbyid(a.defaclrole) AS configured_by,
       n.nspname AS schema_name,
       CASE a.defaclobjtype
           WHEN 'r' THEN 'TABLES'
           WHEN 'S' THEN 'SEQUENCES'
           WHEN 'f' THEN 'FUNCTIONS'
           WHEN 'T' THEN 'TYPES'
           WHEN 'n' THEN 'SCHEMAS'
           ELSE a.defaclobjtype::text
       END AS object_type,
       a.defaclacl::text AS default_acl
FROM pg_catalog.pg_default_acl a
LEFT JOIN pg_catalog.pg_namespace n ON n.oid = a.defaclnamespace
WHERE n.nspname = 'public' OR a.defaclnamespace = 0
ORDER BY configured_by, object_type;

\echo === C2-B.7 - Funcoes e triggers nao internos ===
SELECT p.proname AS function_name,
       pg_catalog.pg_get_userbyid(p.proowner) AS owner_role,
       p.prosecdef AS security_definer
FROM pg_catalog.pg_proc p
JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace
WHERE n.nspname = 'public'
ORDER BY p.proname;

SELECT t.tgname AS trigger_name,
       c.relname AS table_name,
       pg_catalog.pg_get_userbyid(c.relowner) AS table_owner,
       t.tgenabled AS enabled_mode
FROM pg_catalog.pg_trigger t
JOIN pg_catalog.pg_class c ON c.oid = t.tgrelid
JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND NOT t.tgisinternal
ORDER BY c.relname, t.tgname;

\echo === C2-B.8 - Memberships de roles tecnicas ===
SELECT member.rolname AS member_role,
       granted.rolname AS granted_role,
       m.admin_option,
       m.inherit_option,
       m.set_option
FROM pg_catalog.pg_auth_members m
JOIN pg_catalog.pg_roles member ON member.oid = m.member
JOIN pg_catalog.pg_roles granted ON granted.oid = m.roleid
WHERE member.rolname IN ('rasping_migrator', 'rasping_runtime')
   OR granted.rolname IN ('rasping_migrator', 'rasping_runtime')
ORDER BY member_role, granted_role;

ROLLBACK;
\echo === C2-B concluida - leitura apenas; nenhuma permissao alterada ===
