#!/bin/sh
# FASE 21-C2-E-C-C-B - Testa autenticacao efetiva POS-RELOAD.
# Nao imprime senha, hash, enderecos, HBA ou resultado de login invalido.
set -eu

if [ -z "${POSTGRES_PASSWORD:-}" ]; then
    echo 'C2-E-C-C-B: senha administrativa nao esta no ambiente do container' >&2
    exit 1
fi

sql_file='/tmp/FASE_21_C2_E_C_C_B_VERIFICAR_HBA.sql'
if [ ! -r "$sql_file" ]; then
    echo 'C2-E-C-C-B: SQL de validacao ausente no container' >&2
    exit 1
fi

# Rotas: socket Unix, TCP loopback e TCP na rede interna Docker.
# Consulta em cada rota valida o tipo de endereco do cliente.
for rota in socket loopback docker; do
    case "$rota" in
        socket)
            set --
            esperado='SOCKET'
            ;;
        loopback)
            set -- -h 127.0.0.1
            esperado='LOOPBACK'
            ;;
        docker)
            set -- -h postgres
            esperado='REDE'
            ;;
    esac

    valido=$(
        PGPASSWORD="$POSTGRES_PASSWORD" PGCONNECT_TIMEOUT=5 psql \
            -X -w "$@" -U rasping -d rasping_amazon \
            -v ON_ERROR_STOP=1 -A -t \
            -c "SELECT CASE WHEN current_user <> 'rasping' THEN 'FAIL' WHEN '$esperado' = 'SOCKET' AND inet_client_addr() IS NULL THEN 'PASS' WHEN '$esperado' = 'LOOPBACK' AND inet_client_addr() = '127.0.0.1'::inet THEN 'PASS' WHEN '$esperado' = 'REDE' AND inet_client_addr() IS NOT NULL AND inet_client_addr() <> '127.0.0.1'::inet AND inet_client_addr() <> '::1'::inet THEN 'PASS' ELSE 'FAIL' END"
    )
    if [ "$valido" != 'PASS' ]; then
        echo "C2-E-C-C-B: autenticacao valida falhou na rota $rota" >&2
        exit 1
    fi

    # Senha ERRADA deve ser rejeitada. Isso distingue SCRAM de 'trust' ativo,
    # mesmo que o arquivo no disco aparente conter SCRAM.
    if PGPASSWORD='__c2eccb_senha_invalida_fixa_para_teste__' PGCONNECT_TIMEOUT=5 psql \
        -X -w "$@" -U rasping -d rasping_amazon \
        -v ON_ERROR_STOP=1 -A -t -c 'SELECT 1' >/dev/null 2>&1; then
        echo "C2-E-C-C-B: senha invalida ACEITA na rota $rota; politica insegura" >&2
        exit 1
    fi
    echo "=== C2-E-C-C-B ROTA $rota PASS - senha correta aceita e incorreta rejeitada ==="
done

# pg_hba_file_rules reporta conteudo do arquivo, nao a politica carregada.
PGPASSWORD="$POSTGRES_PASSWORD" PGCONNECT_TIMEOUT=5 psql \
    -X -w -h postgres -U rasping -d rasping_amazon \
    -v ON_ERROR_STOP=1 -f "$sql_file"

echo '=== C2-E-C-C-B AUTENTICACAO PASS - HBA analisado e SCRAM exigido nas tres rotas ==='
