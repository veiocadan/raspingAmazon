# FASE 21-C2-E-D-D-D-D-G — Minimo privilegio no ambiente do daemon Windows

## Objetivo

O launcher supervisonado passa somente a senha DPAPI do papel `rasping_runtime`
para o processo Java produtivo. O daemon nao depende das credenciais de Flyway
ou do papel `rasping_migrator`. A ausencia de necessidade e verificada no
caminho Java de execucao, que usa `DB_USER` e `DB_PASSWORD` na conexao runtime.

## Problema identificado

O launcher C-C original decriptava a senha de `rasping_migrator` e a entregava
no ambiente do processo filho, embora esse processo nao execute `Flyway migrate`.
Mesmo que essa atribuicao seja removida, `ProcessStartInfo.EnvironmentVariables`
herda variaveis da sessao PowerShell, exigindo limpeza explicita.

## Modificacoes

- `FASE_21_C2_E_D_D_D_C_C_INICIAR_SUPERVISIONADO.ps1`: carrega **somente**
  `rasping_runtime.dpapi`; nao abre o segredo do migrator nem transmite
  `DB_MIGRATION_*` ao Java.
- `FASE_21_C2_E_D_D_D_C_C_FUNCOES_SUPERVISOR.ps1`: a funcao
  `Remover-VariaveisPrivilegiadas-C-C` limpa todas as variaveis `DB_MIGRATION_*`,
  `FLYWAY_*` e as variaveis especificas de PostgreSQL `PGHOST`, `PGPORT`,
  `PGDATABASE`, `PGUSER`, `PGPASSWORD`, `PGPASSFILE`, `PGSERVICE`,
  `PGSERVICEFILE` do dicionario do processo **filho**.
- O segredo `DB_PASSWORD` da identidade `rasping_runtime` continua apenas no
  ambiente do processo filho, nunca nos argumentos Java.
- A politica de partida, a guarda Java, a identificacao PID/creation time,
  a parada cooperativa e o bloqueio de Flyway permanecem intactos.

## Teste G (nenhum efeito externo)

`infra/postgres/security/FASE_21_C2_E_D_D_D_D_G_VALIDAR_MINIMO_PRIVILEGIO.ps1`
verifica a politica de preparacao, a sequencia das instrucoes do launcher,
a remocao efetiva de variaveis de um `ProcessStartInfo` construido **em memoria**
e a rejeicao de partida real antes de descriptografar qualquer segredo.
Nao executa `ProcessStartInfo.Start()`, exceto indiretamente no script do
launcher que, por politica, falha antes de criar a JVM.

## Limites

Este gate **nao** autoriza a partida produtiva. A simples pausa de um schedule
nao bloqueia necessariamente recuperacao ou dispatch de publicacoes presentes
no banco. A autorizacao futura precisa considerar essas rotas explicitamente.

**Operacao:** nao alterar `.env`, nao executar Flyway, nao modificar o plano
`continuous-startup-policy.v1.json`, nao iniciar `ContinuousProcessingMain`.
