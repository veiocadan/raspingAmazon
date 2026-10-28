# FASE 21-E-D10 — Reserva atômica entre processos reais, offline

**Estado:** implementação e testes estritamente no sandbox `target/retencao-21eb1-testes/cenario-*`.

## Objetivo

D9 validou uma identidade de sessão sintética registrada apenas na memória do processo PowerShell. D10 demonstra uma propriedade complementar: **a criação de um mesmo arquivo de reserva, via `FileMode.CreateNew`, é exclusiva entre dois processos PowerShell independentes** no Windows. O arquivo de reserva permanece como evidência depois do fechamento, impedindo sua reutilização.

## Escopo e contrato

- Utiliza o sandbox privado e a verificação de redirecionamentos da B1, os contratos bloqueados D1/D8 e a validação de identidade da D9.
- O próprio processo que cria a reserva fornece seu PID real e `StartTime.ToUniversalTime().Ticks`; o nonce é fornecido pelo cenário de teste.
- A reserva fica em `reserva-<nonce>.v1.json` dentro do cenário privado; a gravação exige `CreateNew` e `FileShare.None`.
- Dois processos usam o mesmo nonce para provocar disputa artificial. O segundo deve receber código de saída `23` **exclusivamente** quando a reserva já existir. Outra falha recebe código diferente.
- O dono sinaliza prontidão, espera um marcador de liberação no mesmo cenário, fecha o handle voluntariamente e termina. A reserva não é removida.
- Um terceiro processo tenta reusar o nonce original após o término e deve ser rejeitado; outro nonce deve poder criar uma reserva nova.
- São rejeitados nonce inválido, cenário fora de B1 e abertura produtiva. A fonte do processo filho é fixa e não recebe comandos arbitrários.

## Limites explícitos

Esta prova NÃO demonstra a confiabilidade de uma reserva produtiva de supervisão, porque a reserva usa apenas um nonce como chave artificial de disputa. Também não valida recuperação após queda de energia, ACL do diretório externo, reparse points concorrentes nem identidade da JVM real. O arquivo persistente é evidência de exclusividade e não substitui a reserva supervisionada de partida já existente.

Nenhum arquivo é excluído; os arquivos de prova permanecem em `target/`. Não há integração no launcher Windows, no Java de produção nem mudança de política.

As políticas continuam `preparation_only` e `inventory_only`. A captura produtiva, a criação de diretórios externos e as exclusões continuam desautorizadas. Não realizar commit ou push antes da consolidação combinada.
