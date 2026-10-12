#!/bin/sh
# Executado no container. Recebe 2 senhas apenas em STDIN, nao por argv/env do Docker.
# Nao cria arquivos com segredos nem revela consultas de autenticacao falhas.
set -eu
IFS= read -r runtime_secret || { echo 'C2-E-D-C-B: segredo runtime ausente' >&2; exit 1; }
IFS= read -r migrator_secret || { echo 'C2-E-D-C-B: segredo migrator ausente' >&2; exit 1; }
# Windows PowerShell pode encerrar linhas transmitidas com CRLF.
runtime_secret="$(printf '%s' "$runtime_secret" | tr -d '\r')"
migrator_secret="$(printf '%s' "$migrator_secret" | tr -d '\r')"
[ "${#runtime_secret}" -eq 64 ] && [ "${#migrator_secret}" -eq 64 ] || exit 1
case "$runtime_secret$migrator_secret" in *[!0-9A-F]* ) echo 'C2-E-D-C-B: formato invalido' >&2; exit 1;; esac
[ "$runtime_secret" != "$migrator_secret" ] || exit 1

for role in rasping_runtime rasping_migrator; do
  if [ "$role" = 'rasping_runtime' ]; then
    secret="$runtime_secret"
    consulta="SELECT CASE WHEN session_user='rasping_runtime' AND current_user='rasping_runtime' AND inet_client_addr() IS NOT NULL AND has_table_privilege(current_user,'public.processing_job','SELECT') AND NOT has_table_privilege(current_user,'public.flyway_schema_history','SELECT') AND NOT has_schema_privilege(current_user,'public','CREATE') THEN 'PASS' ELSE 'FAIL' END"
  else
    secret="$migrator_secret"
    consulta="SELECT CASE WHEN session_user='rasping_migrator' AND current_user='rasping_migrator' AND inet_client_addr() IS NOT NULL AND has_schema_privilege(current_user,'public','CREATE') AND has_table_privilege(current_user,'public.flyway_schema_history','SELECT') THEN 'PASS' ELSE 'FAIL' END"
  fi
  # Todos os erros do cliente sao suprimidos para nao expor detalhes acidentais.
  resultado="$(PGPASSWORD="$secret" PGCONNECT_TIMEOUT=5 psql -X -w -h postgres -U "$role" -d rasping_amazon -v ON_ERROR_STOP=1 -A -t -c "$consulta" 2>/dev/null)" || {
    echo "C2-E-D-C-B: falha de login $role" >&2; exit 1;
  }
  [ "$resultado" = PASS ] || { echo "C2-E-D-C-B: acesso $role divergente" >&2; exit 1; }
  if PGPASSWORD='__senha_incorreta_c2edcb__' PGCONNECT_TIMEOUT=5 psql -X -w -h postgres -U "$role" -d rasping_amazon -v ON_ERROR_STOP=1 -A -t -c 'SELECT 1' >/dev/null 2>&1; then
    echo "C2-E-D-C-B: senha errada aceita para $role" >&2; exit 1
  fi
  echo "=== C2-E-D-C-B LOGIN $role PASS - senha correta aceita e incorreta rejeitada ==="
done
unset runtime_secret migrator_secret secret
