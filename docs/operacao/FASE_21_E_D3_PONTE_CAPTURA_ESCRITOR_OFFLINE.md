# FASE 21-E-D3 — Ponte B9 → D2 somente offline

## Objetivo

Validar a integração **sem o daemon real** entre:

- **B9**: classificação de `stdout` catalogado e redação integral de `stderr`;
- **D2**: escritor segmentado de JSON Lines, com catálogo estrito, ACL privada, limite por segmento, limite total, `CreateNew` e lacre irreversível da sessão.

A D3 mantém o `continuous-startup-policy.v1.json` em `preparation_only`, a `retention-policy.v1.json` em `inventory_only`, e não modifica o supervisor existente.

## Escopo

Os arquivos D3 são novos e não substituem os anteriores. O único subprocesso permitido neste gate é `powershell.exe` com um arquivo `.ps1` sintético criado exclusivamente sob `target/retencao-21eb1-testes/cenario-<GUID>/`. Não são permitidos classpath, executável Java, PID de serviço ou destino externo configurável.

A sessão D2 é **única** para ambos os canais. Logo os eventos são preservados na ordem em que são processados pelo escritor; cada canal preserva sua própria ordem, mas **não há garantia de ordem absoluta entre canais concorrentes**. A quota por segmento e a quota total são aplicadas a essa mesma sessão.

A leitura usa buffers limitados a 1.024 caracteres e teto de 32.768 caracteres por linha antes de gravar. `stdout` usa UTF-8 estrito; `stderr` é interpretado como caracteres sem dependência da página de código local e descartado pelo classificador, persistindo apenas marcadores seguros.

Em falha de classificação, decodificação, quota, escrita, saída não zero ou timeout, a sessão é encerrada; o subprocesso de teste que ainda estiver vivo é terminado. **Essa terminação não se aplica ao daemon real.** Não há exclusão dos segmentos, dos scripts sintéticos nem dos demais artefatos de teste.

## Testes

`FASE_21_E_D3_TESTAR_PONTE_OFFLINE.ps1` verifica captura mista com 24 registros, preservação dos seis códigos do catálogo, minimização do `errorCode`, ausência de strings sensíveis fictícias, segmentação, contadores, falha de saída não zero, quota, linha excessiva, timeout e bloqueio do escritor produtivo.

A D3 não substitui os gates B9 e D2, nem comprova recuperação contra todos os tipos de indisponibilidade de disco ou concorrência hostil. Os testes D3 devem rodar no Windows PowerShell do operador, com os testes B9 e D2 novamente aprovados.

## Pendências bloqueantes antes de integração operacional

1. Uma implementação própria de escritor para diretório operacional externo seguro, com ACL, isolamento de sessões e ausência de redirecionamentos.
2. Um protocolo de tratamento de falhas de captura que se integre à parada **cooperativa** supervisionada, sem `Kill()` da JVM.
3. Verificação de `PrintWriter.checkError()` ou substituição por um sink Java com falhas observáveis e testes.
4. Ativação de políticas somente em fase separada, com autorização explícita; **nenhuma autorização é concedida pela D3**.
5. Revisão de segurança das fontes e testes integrais antes de commit e release.

Nenhuma alteração produtiva do supervisor, Java, PostgreSQL, backup ou políticas faz parte desta etapa.
