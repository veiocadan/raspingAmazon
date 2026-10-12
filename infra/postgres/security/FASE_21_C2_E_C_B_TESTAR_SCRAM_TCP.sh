#!/bin/sh
# FASE 21-C2-E-C-B - Prova real da credencial administrativa sobre TCP interno.
# Rodar pelo stdin via docker compose exec -T postgres sh -s.
# Nao exibe senha, hash, endereco de rede ou parametros do ambiente.
set -eu

if [ -z "${POSTGRES_PASSWORD:-}" ]; then
    echo 'C2-E-C-B TCP: POSTGRES_PASSWORD indisponivel; nao aplicar HBA' >&2
    exit 1
fi

answer=$(
    PGPASSWORD="$POSTGRES_PASSWORD" PGCONNECT_TIMEOUT=5 psql \
        -X -w -h postgres -U rasping -d rasping_amazon \
        -v ON_ERROR_STOP=1 -A -t \
        -c "SELECT CASE
               WHEN current_user = 'rasping'
                AND current_database() = 'rasping_amazon'
                AND inet_client_addr() IS NOT NULL
                AND inet_client_addr() = inet_server_addr()
                AND NOT (inet_client_addr() <<= '127.0.0.0/8'::cidr)
                AND inet_client_addr() <> '::1'::inet
               THEN 'PASS' ELSE 'FAIL' END;"
)

if [ "$answer" != 'PASS' ]; then
    echo 'C2-E-C-B TCP: falhou assert de rede nao loopback; nao aplicar HBA' >&2
    exit 1
fi

echo '=== C2-E-C-B TCP SCRAM PASS - senha administrativa valida na rota interna nao loopback ==='
