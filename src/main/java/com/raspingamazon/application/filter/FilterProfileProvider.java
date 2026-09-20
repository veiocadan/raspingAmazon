package com.raspingamazon.application.filter;

import com.raspingamazon.domain.filter.FilterProfile;

/**
 * Porta de aplicação responsável por fornecer o perfil comercial
 * de filtros atualmente ativo.
 *
 * A aplicação conhece somente este contrato.
 *
 * Ela não precisa saber se o perfil vem de PostgreSQL, arquivo,
 * serviço remoto ou qualquer outra infraestrutura.
 */
public interface FilterProfileProvider {

    /**
     * Retorna o único perfil comercial ativo.
     *
     * @return perfil ativo e versionado
     * @throws IllegalStateException quando nenhum perfil ativo existir
     *                               ou quando a fonte estiver inconsistente
     */
    FilterProfile activeProfile();
}
