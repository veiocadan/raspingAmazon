#!/bin/sh
# D-D: recebe uma credencial do operador por STDIN e executa SELECT read-only.
set -eu
LC_ALL=C
export LC_ALL
umask 077
IFS= read -r runtime_secret || { echo 'D-D: credencial ausente' >&2; exit 1; }
runtime_secret="$(printf '%s' "$runtime_secret" | tr -d '\r')"
[ "${#runtime_secret}" -eq 64 ] || { echo 'D-D: formato de credencial inesperado' >&2; exit 1; }
case "$runtime_secret" in
    *[!0-9A-F]*) echo 'D-D: formato de credencial inesperado' >&2; exit 1 ;;
esac
sql='/tmp/FASE_21_C2_E_D_D_D_D_D_VERIFICAR_SCHEDULE_READONLY.sql'
[ -r "$sql" ] || { echo 'D-D: SQL ausente' >&2; exit 1; }
if ! PGPASSWORD="$runtime_secret" PGCONNECT_TIMEOUT=5 psql -X -w -q -A -t \
    -h postgres -U rasping_runtime -d rasping_amazon \
    -v ON_ERROR_STOP=1 -f "$sql" 2>/dev/null; then
    echo 'D-D: verificacao readonly reprovada; nao iniciar daemon' >&2
    exit 1
fi
unset runtime_secret
