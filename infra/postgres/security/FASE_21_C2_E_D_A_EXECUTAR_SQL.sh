#!/bin/sh
# FASE 21-C2-E-D-A: psql autenticado com credencial JA EXISTENTE no container.
# Nenhum segredo e impresso, gravado ou transferido ao Windows.
set -eu

case "${1:-}" in
    /tmp/FASE_21_C2_E_D_A_PRECHECK_IDENTIDADES.sql|/tmp/FASE_21_C2_E_D_A_VERIFICAR_RUNTIME_SIMULADO.sql)
        ;;
    *)
        echo 'C2-E-D-A: arquivo SQL nao permitido' >&2
        exit 1
        ;;
esac

if [ -z "${POSTGRES_PASSWORD:-}" ]; then
    echo 'C2-E-D-A: senha administrativa ausente no container' >&2
    exit 1
fi

PGPASSWORD="$POSTGRES_PASSWORD" exec psql \
    -X -w -U rasping -d rasping_amazon \
    -v ON_ERROR_STOP=1 -f "$1"
