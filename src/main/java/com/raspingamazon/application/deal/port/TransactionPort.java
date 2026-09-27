package com.raspingamazon.application.deal.port;

/**
 * Alias histórico da porta transacional genérica.
 *
 * <p>Novos módulos da aplicação devem depender de
 * {@link com.raspingamazon.application.shared.port.TransactionPort}.
 * Este contrato permanece para preservar compatibilidade com o
 * processamento de deals já existente.</p>
 */
@Deprecated(
    forRemoval = false
)
public interface TransactionPort
    extends com.raspingamazon.application.shared.port.TransactionPort {
}
