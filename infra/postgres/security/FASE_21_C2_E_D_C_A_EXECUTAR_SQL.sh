#!/bin/sh
# C2-E-D-C-A: usa exclusivamente senha administrativa JA EXISTENTE no container.
# Nao imprime segredo. Nao altera PostgreSQL. Nao habilita LOGIN.
set -eu

if [ "${1:-}" != '/tmp/FASE_21_C2_E_D_C_A_PRECHECK_PROVISIONAMENTO.sql' ]; then
    echo 'C2-E-D-C-A: caminho SQL nao autorizado' >&2
    exit 1
fi
if [ -z "${POSTGRES_PASSWORD:-}" ]; then
    echo 'C2-E-D-C-A: senha administrativa nao encontrada no container' >&2
    exit 1
fi

# Realiza conexao TCP que exige SCRAM e nao exibe o resultado de falha.
resultado="$(PGPASSWORD="$POSTGRES_PASSWORD" PGCONNECT_TIMEOUT=5 psql \
  -X -w -h postgres -U rasping -d rasping_amazon -v ON_ERROR_STOP=1 -A -t \
  -c "SELECT CASE WHEN session_user='rasping' AND inet_client_addr() IS NOT NULL THEN 'PASS' ELSE 'FAIL' END")"
if [ "$resultado" != 'PASS' ]; then
    echo 'C2-E-D-C-A: login administrativo TCP falhou' >&2
    exit 1
fi
if PGPASSWORD='__c2edca_senha_incorreta_de_teste__' PGCONNECT_TIMEOUT=5 psql \
  -X -w -h postgres -U rasping -d rasping_amazon -v ON_ERROR_STOP=1 -A -t \
  -c 'SELECT 1' >/dev/null 2>&1; then
    echo 'C2-E-D-C-A: senha errada aceita. Interromper.' >&2
    exit 1
fi

echo '=== C2-E-D-C-A SCRAM PASS - senha correta aceita, senha incorreta rejeitada ==='
PGPASSWORD="$POSTGRES_PASSWORD" PGCONNECT_TIMEOUT=5 exec psql \
  -X -w -h postgres -U rasping -d rasping_amazon \
  -v ON_ERROR_STOP=1 -f "$1"
