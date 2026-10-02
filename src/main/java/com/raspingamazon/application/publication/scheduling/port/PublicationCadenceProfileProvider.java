package com.raspingamazon.application.publication.scheduling.port;

import com.raspingamazon.domain.publication.scheduling.PublicationCadenceProfile;

/**
 * Porta que fornece o perfil de cadência atualmente ativo
 * para um determinado escopo de publicação.
 *
 * <p>O contrato não determina onde a configuração é armazenada.</p>
 */
@FunctionalInterface
public interface PublicationCadenceProfileProvider {

    PublicationCadenceProfile activeProfile(
        String channel,
        String destination
    );
}
