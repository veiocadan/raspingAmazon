# FASE 21-E-D9 — Adaptador de sessão supervisionada, somente isolado

**Estado:** implementação offline em `target/`, sem integração ao launcher e sem autorização de captura produtiva.

## Motivação

As provas D2/D3 demonstraram gravação catalogada e captura em sandbox; D6/D7 demonstraram parada cooperativa de JVM de teste; D8 definiu o modelo de identidade e ACL para futuro diretório externo. Esta D9 liga identidade de sessão (PID, ticks de criação e nonce **sintéticos**) ao escritor D2, sem criar diretório operacional e sem alterar componentes encerrados.

## Contrato implementado

- `Assert-21ED9-Contexto`: valida branch, planos `preparation_only` e `inventory_only`, catálogo B8, proposta D8 e hashes críticos D1. Somente lê arquivos versionados do repositório.
- `Assert-21ED9-IdentidadePlanejada`: valida PID/ticks positivos, nonce hexadecimal minúsculo e nome proposto `sessao-{pid}-{ticks}-{nonce}` em memória. Não acessa o destino externo.
- `New-21ED9-AdaptadorEnsaio`: cria **apenas** um novo `target/retencao-21eb1-testes/cenario-{guid}` através do B1, abre uma sessão D2 exclusivamente nesse sandbox e associa a identidade sintética a um objeto registrado em memória.
- `Write-21ED9-RegistroEnsaio`: encaminha JSON catalogado ao escritor D2, com limites e revalidação a cada registro. Falhas lacram o adaptador e preservam os segmentos anteriores.
- `Close-21ED9-AdaptadorEnsaio`: fecha a sessão e mantém a identidade marcada como consumida na execução PowerShell atual.
- `New-21ED9-AdaptadorProdutivo`: **sempre recusa**. Não consulta nem cria `C:\dev\rasping-amazon-operacao\logs`; não aceita ativação automática por alteração de flag.

## Evidência esperada

O script `FASE_21_E_D9_TESTAR_ADAPTADOR_ISOLADO.ps1` exercita: contratos atuais, identidade inválida, recusa produtiva, rejeição de objeto de sessão falso, reutilização de identidade, dois eventos catalogados/redigidos, segmentação, bytes, injeção de dados, lacre, preservação de SHA-256, rejeição de reabertura e isolamento com novo nonce. Todos os artefatos gerados ficam sob `target/` e são preservados.

## Limites importantes

1. PID e ticks são **valores artificiais**; o teste não confirma identidade de processo Java real.
2. A lista de identidades consumidas existe somente na memória do PowerShell que executa o teste. **Não constitui exclusividade interprocessos** e não substitui reserva atômica/ACL em produção.
3. O escritor é o D2, restrito a `target/`; a D9 **não implementa escritor operacional externo** nem integra os dois pipes do supervisor.
4. A falta de autorização na política de startup e de retenção continua obrigatória. Nenhum dos gates D1–D9 autoriza editar a política, iniciar o daemon, executar exclusão ou criar diretório operacional real.
5. Antes de qualquer adaptação produtiva, falta implementar e auditar exclusividade interprocessos, ACL NTFS real e segura, confirmação de PID/creation ticks, proteção contra links e corridas, captura de dois pipes no supervisor, parada cooperativa por falhas, quarentena de estado incerto e testes de falhas com o launcher sob condições controladas.

**Conclusão desta etapa só após passar o teste no Windows.** Sem commit/push nem alterações aos arquivos anteriores.
