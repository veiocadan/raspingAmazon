#!/bin/sh
# FASE 21-F — cliente PostgreSQL 18.6, executado em container efemero.
# A senha DPAPI entra exclusivamente por STDIN e nao e escrita em disco.
set -eu
LC_ALL=C
export LC_ALL
umask 077

mode="${1:-}"
case "$mode" in
  backup|counts) ;;
  *) echo 'FASE 21-F: operacao invalida' >&2; exit 11 ;;
esac

IFS= read -r secret || { echo 'FASE 21-F: credencial ausente' >&2; exit 12; }
# O PowerShell 5.1 pode enviar CRLF pelo pipe.
secret="$(printf '%s' "$secret" | tr -d '\r')"
if [ "${#secret}" -ne 64 ]; then
  echo 'FASE 21-F: formato de credencial invalido' >&2
  exit 13
fi
case "$secret" in
  *[!0-9A-F]*) echo 'FASE 21-F: formato de credencial invalido' >&2; exit 13 ;;
esac

if [ "$mode" = backup ]; then
  name="${2:-}"
  case "$name" in
    rasping_21f_*.dump) ;;
    *) echo 'FASE 21-F: nome de backup invalido' >&2; exit 14 ;;
  esac
  case "$name" in
    *[!a-zA-Z0-9_.-]*) echo 'FASE 21-F: nome de backup invalido' >&2; exit 14 ;;
  esac
  [ -d /backup ] && [ -r /scripts/FASE_21_F_CLIENTE_POSTGRES.sh ] || exit 15
  PGPASSWORD="$secret" PGCONNECT_TIMEOUT=10 pg_dump -w \
    -h 127.0.0.1 -p 5432 -U rasping_migrator -d rasping_amazon \
    -Fc --no-owner --no-acl -f "/backup/$name" 2>/dev/null || {
      echo 'FASE 21-F: pg_dump falhou' >&2
      exit 16
    }
else
  [ -r /scripts/FASE_21_F_CONTAGENS_READONLY.sql ] || exit 17
  PGPASSWORD="$secret" PGCONNECT_TIMEOUT=10 psql -X -w -q -A -t \
    -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5432 \
    -U rasping_migrator -d rasping_amazon \
    -f /scripts/FASE_21_F_CONTAGENS_READONLY.sql 2>/dev/null || {
      echo 'FASE 21-F: consulta readonly falhou' >&2
      exit 18
    }
fi
unset secret
