# FASE 21-C2-E-D-D-A — Gate de acesso real antes da transicao operacional

## Contexto e objetivo

A fase C2-E-D-C-B (`00c9c16`) ativou as identidades `rasping_runtime` e `rasping_migrator` com duas senhas independentes, protegidas em `C:\dev\rasping-amazon-backups\credenciais_c2edcb` via Windows DPAPI. A separacao no Java foi versionada em C2-E-D-B (`fd89d3e`); a aplicacao, entretanto, ainda nao foi mudada para usar essas identidades. A restricao de porta e o `pg_hba.conf` SCRAM-SHA-256 seguem obrigatorios.

Esta entrega **nao modifica `.env`, Java, HBA, roles, senhas ou estado do banco**. Valida duas sessoes autenticas, nao `SET ROLE` em sessao de administrador, usando transacoes READ ONLY e ROLLBACK.

## Artefatos completos

- `FASE_21_C2_E_D_D_A_EXECUTAR_GATE.ps1`: confere porta e contrato Java, le os dois arquivos DPAPI e executa os testes via Docker.
- `FASE_21_C2_E_D_D_A_TESTAR_ACESSO_REAL.sh`: recebe as duas senhas por STDIN, executa psql com cada identidade e rejeita senha incorreta.
- `FASE_21_C2_E_D_D_A_RUNTIME_READONLY.sql`: verifica role real, escopo estrutural, matriz de 29 tabelas e 26 sequences, isolamento do Flyway e consultas de leitura efetivas.
- `FASE_21_C2_E_D_D_A_MIGRATOR_READONLY.sql`: verifica role real, propriedade do schema, 29 tabelas e 26 sequences e acesso efetivo a `flyway_schema_history`.
- Este contrato.

## Execucao

Com Java, worker e scheduler parados, no Windows PowerShell, sob o **mesmo usuario Windows que criou os arquivos DPAPI**:

```powershell
Set-Location 'C:\dev\rasping-amazon'
$ErrorActionPreference = 'Stop'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'infra/postgres/security/FASE_21_C2_E_D_D_A_EXECUTAR_GATE.ps1'
if ($LASTEXITCODE -ne 0) { throw 'C2-E-D-D-A reprovada. Nao mudar as credenciais da aplicacao.' }
git diff --check
git status
```

Esperado:

- `C2-E-D-D-A PORTA PASS`
- `C2-E-D-D-A JAVA PASS`
- `C2-E-D-D-A RUNTIME REAL PASS`
- `C2-E-D-D-A MIGRATOR REAL PASS`
- `C2-E-D-D-A SCRAM PASS`
- `C2-E-D-D-A GATE PASS`

## Controles de seguranca

As senhas nao aparecem em parametros CLI, scripts SQL, terminal ou Git. Sao lidas do DPAPI com a identidade Windows original e transmitidas em STDIN ao contêiner. O cliente `psql` usa `PGPASSWORD` somente no ambiente de seu processo temporario dentro do contêiner; nao ha arquivo de senhas nem secrets montados. Os arquivos `sh` e `sql` nao contem credenciais. Erros da etapa de autenticacao sao suprimidos para impedir vazamento acidental.

O teste de privilégio eh comparado com os totais validados na C2-C: 27 SELECT, 25 INSERT, 12 UPDATE e 23 USAGE; zero DELETE/TRUNCATE, zero UPDATE de sequence e isolamento de Flyway. Permissoes de propriedade do migrator sao verificadas, mas **nao se executa Flyway/migracao real**; a proxima etapa devera validar a integracao Java e migrações sem tentar uma escrita de producao inesperada.

## Limites e reversao

- Nao habilita novas roles, nao gira senhas e nao altera ACLs ou ownership.
- Nao mede desempenho do PostgreSQL nem testa uma migracao Flyway completa. Nao comprova operacoes INSERT/UPDATE reais; verifica concessoes e SELECTs efetivos.
- Em caso de falha, **nao repita provisionamento**. Preserve os backups, investigue a causa e mantenha o Java no contrato de conexao anterior.
- O rollback previsto em C2-E-D-C-B (`FASE_21_C2_E_D_C_B_DESATIVAR_LOGINS.ps1`) e destrutivo para esses logins; nao executar sem autorizacao explicita.
- Nao versionar `.env`, arquivos `.dpapi`, dados de `pg_authid` nem extratos de credenciais.
