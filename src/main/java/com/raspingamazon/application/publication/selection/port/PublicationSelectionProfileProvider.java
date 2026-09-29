package com.raspingamazon.application.publication.selection.port;

import com.raspingamazon.domain.publication.selection.PublicationSelectionProfile;

/**
 * Porta de aplicação responsável por fornecer a configuração
 * ativa da seleção operacional de publicações.
 *
 * <p>A aplicação conhece somente este contrato. PostgreSQL,
 * JDBC e qualquer outra origem concreta da configuração
 * pertencem à infraestrutura.</p>
 *
 * <p>O contrato recebe canal e destino desde a primeira versão
 * para preservar a evolução futura em que diferentes destinos
 * possam possuir políticas operacionais distintas.</p>
 */
public interface PublicationSelectionProfileProvider {

    /**
     * Retorna o perfil ativo aplicável ao canal e destino.
     *
     * @param channel identidade lógica do canal
     * @param destination destino dentro do canal
     * @return perfil ativo e versionado
     * @throws IllegalStateException quando nenhuma configuração
     *                               aplicável existir, quando houver
     *                               ambiguidade ou quando a fonte
     *                               estiver inconsistente
     */
    PublicationSelectionProfile activeProfile(
        String channel,
        String destination
    );
}
