package com.raspingamazon.infrastructure.config;

/**
 * Configuração operacional de ativação dos canais externos.
 *
 * <p>Ativação é deliberadamente separada das credenciais e
 * das configurações específicas dos providers.</p>
 *
 * <p>As identidades operacionais conhecidas nesta fase são:</p>
 *
 * <pre>
 * TELEGRAM
 * WHATSAPP_MANUAL
 * WHATSAPP
 * </pre>
 *
 * <p>WHATSAPP_MANUAL representa conteúdo preparado para WhatsApp,
 * entregue tecnicamente em um destino privado do Telegram para
 * posterior cópia e publicação humana.</p>
 */
public record PublicationChannelActivationConfig(
    boolean telegramEnabled,
    boolean whatsAppManualEnabled,
    boolean whatsAppEnabled
) {

    /**
     * Mantém compatibilidade com os pontos já existentes da FASE 19
     * que conheciam somente Telegram e WhatsApp automático.
     *
     * <p>Nessa forma, WhatsApp manual permanece desabilitado.</p>
     */
    public PublicationChannelActivationConfig(
        boolean telegramEnabled,
        boolean whatsAppEnabled
    ) {

        this(
            telegramEnabled,
            false,
            whatsAppEnabled
        );
    }
}
