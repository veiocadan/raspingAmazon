#!/bin/sh
# C2-E-D-D-A: executado no contêiner; duas credenciais recebidas por STDIN,
# sem argumentos com senhas ou arquivos de segredo. So executa SQL READ ONLY.
set -eu
LC_ALL=C
export LC_ALL

IFS= read -r runtime_secret || { echo 'C2-E-D-D-A: credencial runtime ausente' >&2; exit 1; }
IFS= read -r migrator_secret || { echo 'C2-E-D-D-A: credencial migrator ausente' >&2; exit 1; }
runtime_secret="$(printf '%s' "$runtime_secret" | tr -d '\r')"
migrator_secret="$(printf '%s' "$migrator_secret" | tr -d '\r')"
[ "${#runtime_secret}" -eq 64 ] && [ "${#migrator_secret}" -eq 64 ] || {
  echo 'C2-E-D-D-A: formato de credenciais invalido' >&2; exit 1;
}
case "$runtime_secret$migrator_secret" in
  *[!0-9A-F]* ) echo 'C2-E-D-D-A: formato de credenciais invalido' >&2; exit 1;;
esac
[ "$runtime_secret" != "$migrator_secret" ] || {
  echo 'C2-E-D-D-A: credenciais nao sao distintas' >&2; exit 1;
}

sql_runtime='/tmp/FASE_21_C2_E_D_D_A_RUNTIME_READONLY.sql'
sql_migrator='/tmp/FASE_21_C2_E_D_D_A_MIGRATOR_READONLY.sql'
[ -f "$sql_runtime" ] && [ -f "$sql_migrator" ] || {
  echo 'C2-E-D-D-A: SQL nao instalado no conteiner' >&2; exit 1;
}

if ! PGPASSWORD="$runtime_secret" PGCONNECT_TIMEOUT=5 psql -X -w -q -h postgres \
  -U rasping_runtime -d rasping_amazon -v ON_ERROR_STOP=1 -f "$sql_runtime" >/dev/null 2>&1; then
  echo 'C2-E-D-D-A: acessos reais do runtime reprovados' >&2
  exit 1
fi
echo '=== C2-E-D-D-A RUNTIME REAL PASS - JDBC ACLs e leituras confirmadas, sem escrita ==='

if ! PGPASSWORD="$migrator_secret" PGCONNECT_TIMEOUT=5 psql -X -w -q -h postgres \
  -U rasping_migrator -d rasping_amazon -v ON_ERROR_STOP=1 -f "$sql_migrator" >/dev/null 2>&1; then
  echo 'C2-E-D-D-A: acessos reais do migrator reprovados' >&2
  exit 1
fi
echo '=== C2-E-D-D-A MIGRATOR REAL PASS - owner e Flyway acessiveis, sem DDL ==='

# Falha obrigatoria da autenticacao com senha errada, sem emitir stderr da conexao.
for usuario in rasping_runtime rasping_migrator; do
  if PGPASSWORD='__C2_E_D_D_A_SENHA_INCORRETA__' PGCONNECT_TIMEOUT=5 \
     psql -X -w -q -h postgres -U "$usuario" -d rasping_amazon -v ON_ERROR_STOP=1 \
     -c 'SELECT 1' >/dev/null 2>&1; then
    echo 'C2-E-D-D-A: autenticao sem credencial correta detectada' >&2
    exit 1
  fi
done
unset runtime_secret migrator_secret
echo '=== C2-E-D-D-A SCRAM PASS - credenciais incorretas rejeitadas ==='
