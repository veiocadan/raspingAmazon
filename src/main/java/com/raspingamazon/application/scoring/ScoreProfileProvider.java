package com.raspingamazon.application.scoring;

import com.raspingamazon.domain.scoring.ScoreProfile;

/**
 * Porta de aplicação responsável por fornecer o perfil de score
 * atualmente ativo.
 *
 * <p>A aplicação conhece apenas este contrato. A origem concreta
 * da configuração pertence à infraestrutura.</p>
 *
 * <p>O perfil pode ser carregado de PostgreSQL, arquivo, serviço
 * externo ou outra fonte sem alterar o domínio ou o serviço de
 * aplicação consumidor.</p>
 */
public interface ScoreProfileProvider {

    /**
     * Retorna o único perfil de score atualmente ativo.
     *
     * @return perfil de score ativo e versionado
     * @throws IllegalStateException quando nenhum perfil ativo existir,
     *                               quando houver mais de um perfil ativo
     *                               ou quando a fonte estiver inconsistente
     */
    ScoreProfile activeProfile();
}
