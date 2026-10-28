-- FASE 21-C2-E-C-B - Invariantes de roles tecnicas e ownership.
-- READ ONLY, antes de endurecer o HBA; nenhuma credencial exibida.
\set ON_ERROR_STOP on
\pset pager off
\pset null '[NULL]'
BEGIN TRANSACTION READ ONLY;

DO $guard$
BEGIN
    IF current_database() <> 'rasping_amazon'
       OR current_user <> 'rasping'
       OR session_user <> 'rasping' THEN
        RAISE EXCEPTION 'C2-E-C-B: banco ou identidade administrativa incorreta';
    END IF;

    IF (SELECT count(*) FROM pg_catalog.pg_roles AS r
        WHERE r.rolname IN ('rasping_runtime', 'rasping_migrator')
          AND NOT r.rolcanlogin AND NOT r.rolinherit
          AND NOT r.rolsuper AND NOT r.rolcreatedb
          AND NOT r.rolcreaterole AND NOT r.rolreplication
          AND NOT r.rolbypassrls) <> 2 THEN
        RAISE EXCEPTION 'C2-E-C-B: roles tecnicas perderam o estado NOLOGIN';
    END IF;

    IF EXISTS (SELECT 1
                 FROM pg_catalog.pg_authid AS a
                WHERE a.rolname IN ('rasping_runtime', 'rasping_migrator')
                  AND a.rolpassword IS NOT NULL) THEN
        RAISE EXCEPTION 'C2-E-C-B: senha tecnica presente antes do gate de LOGIN';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM pg_catalog.pg_auth_members AS m
          JOIN pg_catalog.pg_roles AS grantor_role ON grantor_role.oid = m.roleid
          JOIN pg_catalog.pg_roles AS member_role ON member_role.oid = m.member
         WHERE grantor_role.rolname IN ('rasping_runtime', 'rasping_migrator')
            OR member_role.rolname IN ('rasping_runtime', 'rasping_migrator')
    ) THEN
        RAISE EXCEPTION 'C2-E-C-B: membership inesperada entre identidades';
    END IF;

    IF (SELECT pg_catalog.pg_get_userbyid(nspowner)
          FROM pg_catalog.pg_namespace
         WHERE nspname = 'public') <> 'rasping_migrator' THEN
        RAISE EXCEPTION 'C2-E-C-B: migrator nao e owner de public';
    END IF;

    IF (SELECT count(*) FROM pg_catalog.pg_class AS c
        JOIN pg_catalog.pg_namespace AS n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
          AND pg_catalog.pg_get_userbyid(c.relowner) = 'rasping_migrator') <> 29 THEN
        RAISE EXCEPTION 'C2-E-C-B: ownership das 29 tabelas divergiu';
    END IF;

    IF (SELECT count(*) FROM pg_catalog.pg_class AS c
        JOIN pg_catalog.pg_namespace AS n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind = 'S'
          AND pg_catalog.pg_get_userbyid(c.relowner) = 'rasping_migrator') <> 26 THEN
        RAISE EXCEPTION 'C2-E-C-B: ownership das 26 sequences divergiu';
    END IF;
END;
$guard$;

\echo === C2-E-C-B.3 - Roles tecnicas preservadas ===
SELECT rolname AS identidade,
       rolcanlogin AS login_habilitado,
       rolsuper AS administrador,
       rolinherit AS herda_privilegios
  FROM pg_catalog.pg_roles
 WHERE rolname IN ('rasping_runtime', 'rasping_migrator')
 ORDER BY rolname;

\echo === C2-E-C-B.4 - Ownership agregado ===
SELECT CASE WHEN c.relkind = 'S' THEN 'SEQUENCE' ELSE 'TABLE' END AS objeto,
       pg_catalog.pg_get_userbyid(c.relowner) AS proprietario,
       count(*) AS quantidade
  FROM pg_catalog.pg_class AS c
  JOIN pg_catalog.pg_namespace AS n ON n.oid = c.relnamespace
 WHERE n.nspname = 'public'
   AND c.relkind IN ('r', 'p', 'S')
 GROUP BY 1, 2
 ORDER BY 1, 2;

ROLLBACK;
\echo === C2-E-C-B ROLES PRECHECK PASS - nenhuma role alterada ===
