package com.raspingamazon.application.publication.selection.port;

import com.raspingamazon.domain.publication.selection.PublicationQuotaProfile;

/**
 * Porta de aplicação responsável por fornecer a configuração
 * ativa da quota de publicação.
 *
 * <p>A camada de aplicação conhece somente este contrato.
 * PostgreSQL, JDBC ou outra origem concreta permanecem na
 * infraestrutura.</p>
 *
 * <p>Canal e destino fazem parte do escopo desde a primeira
 * versão para preservar a futura operação multi-canal.</p>
 */
public interface PublicationQuotaProfileProvider {

    /**
     * Retorna a configuração ativa de quota aplicável ao canal
     * e destino.
     *
     * @param channel identidade lógica do canal
     * @param destination destino dentro do canal
     * @return configuração ativa e versionada
     * @throws IllegalStateException quando nenhuma configuração
     *                               aplicável existir, houver mais
     *                               de uma configuração aplicável
     *                               ou a fonte estiver inconsistente
     */
    PublicationQuotaProfile activeProfile(
        String channel,
        String destination
    );
}
