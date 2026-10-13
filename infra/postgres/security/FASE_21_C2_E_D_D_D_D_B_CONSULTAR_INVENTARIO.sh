#!/bin/sh
# FASE 21-C2-E-D-D-D-D-B — executado no container PostgreSQL com senha via STDIN.
set -eu
LC_ALL=C
export LC_ALL
umask 077
IFS= read -r runtime_secret || { echo 'D-B: credencial de runtime nao recebida' >&2; exit 1; }
runtime_secret="$(printf '%s' "$runtime_secret" | tr -d '\r')"
[ "${#runtime_secret}" -eq 64 ] || { echo 'D-B: credencial de formato inesperado' >&2; exit 1; }
case "$runtime_secret" in
  *[!0-9A-F]*) echo 'D-B: credencial de formato inesperado' >&2; exit 1;;
esac
sql='/tmp/FASE_21_C2_E_D_D_D_D_B_INVENTARIO_READONLY.sql'
[ -r "$sql" ] || { echo 'D-B: script SQL indisponivel' >&2; exit 1; }
# Nao salvar PGPASSWORD em arquivos; suprimir stderr bruto do psql.
if ! PGPASSWORD="$runtime_secret" PGCONNECT_TIMEOUT=5 psql -X -w -q -A -t \
    -h postgres -U rasping_runtime -d rasping_amazon \
    -v ON_ERROR_STOP=1 -f "$sql" 2>/dev/null; then
    echo 'D-B: consulta readonly falhou; verificar SQL e ACL antes de prosseguir' >&2
    exit 1
fi
unset runtime_secret
