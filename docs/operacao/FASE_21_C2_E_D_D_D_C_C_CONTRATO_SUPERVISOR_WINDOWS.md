# FASE 21-C2-E-D-D-D-C-C — Supervisor Windows (sem partida produtiva)

## Escopo

Integra o mecanismo Java `SupervisedStopSignal` validado na C-B ao Windows. O supervisor utiliza `System.Diagnostics.Process`, ambiente de processo filho (sem senhas em linha de comando), registro JSON privado e pedido de parada por arquivo. O próprio Java executa `ContinuousProcessingApplication.close()` para terminar scheduler e worker. Não existe `taskkill`, reinício automático ou migração Flyway neste supervisor.

## Identidade e arquivos privados

- Registro fora do Git: `C:\dev\rasping-amazon-backups\supervisao\rasping-amazon-daemon.v1.json`.
- Pedido de parada: `C:\dev\rasping-amazon-backups\supervisao\rasping-amazon-daemon.stop.request`.
- Para iniciar: o registro e o pedido devem estar ausentes; busca Win32_Process identifica Java pela classe principal, PID e CreationDate.
- Na partida autorizada, o diretório do supervisor é criado com ACL restrita ao usuário Windows atual, SYSTEM e Administradores; se o diretório já existir, a ACL não pode ser herdada nem permitir escrita de terceiros.
- Registro tem apenas 4 propriedades: versão, PID, horário de criação e classe principal; não há senhas nem tokens.
- Qualquer registro antigo/incoerente provoca falha fechada; não o apague automaticamente.

## Credenciais

Lê `rasping_runtime.dpapi` e `rasping_migrator.dpapi` com o usuário Windows que as provisionou. O launcher injeta ambas somente no **ambiente do processo Java filho** via `ProcessStartInfo.EnvironmentVariables`. Não modifica `.env`, argumentos Java, arquivos SQL ou logs de provisionamento. A JVM mantém suas credenciais em memória durante a execução, portanto o host e o usuário de serviço devem ser protegidos.

## Bloqueios nesta entrega

A política versionada `config/operacao/continuous-startup-policy.v1.json` permanece `execution_mode=preparation_only` com 4 flags `false`. O script `INICIAR_SUPERVISIONADO.ps1` rejeita a partida **antes** de buscar DPAPI, criar diretórios, compilar ou iniciar Java. O teste `VALIDAR_SUPERVISOR.ps1` confirma essa rejeição e que `PARAR_SUPERVISIONADO.ps1` não cria sinal sem registro válido.

A política poderá ser ativada apenas em etapa futura, depois de revisão de efeitos externos e de atualização auditada do JSON. A função de validação do supervisor admite modo `supervised` somente quando as autorizações explícitas e o Git limpo coincidirem; Flyway e reinício automático continuam proibidos. **Não altere manualmente a política para liberar a partida.**

## Comportamento após futura autorização

- Launcher compila Maven sem executar testes, monta classpath de runtime, carrega política de alertas versionada, configura intervalo `PT1H`, inicia processo Java e grava identidade autenticada pelo CIM. A sessão do launcher espera o fim do processo e não reinicia automaticamente.
- Parada envia sinal cooperativo pelo arquivo local exclusivo, aguarda `shutdown_grace_seconds` (atualmente 30) e não mata a JVM se o prazo esgotar. Se ocorrer timeout, preservar os arquivos e investigar.
- O bootstrap do daemon **pode modificar PostgreSQL e executar ações externas**, inclusive recovery. A primeira partida requer etapa de autorização operacional específica; os testes desta fase NÃO demonstram que essas ações já estejam aprovadas ou sejam seguras.
- Não compartilhar logs brutos que possam conter dados operacionais; os scripts de gate exibem apenas marcadores sem segredos.

## Validação nesta fase

Executar apenas `FASE_21_C2_E_D_D_D_C_C_VALIDAR_SUPERVISOR.ps1`, que não inicia Java nem escreve arquivos privados. Enviar `git status` para análise. Não fazer commit até a conclusão de toda a etapa C.
