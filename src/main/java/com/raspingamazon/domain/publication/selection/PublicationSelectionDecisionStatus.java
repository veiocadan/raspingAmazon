package com.raspingamazon.domain.publication.selection;

/**
 * Resultado operacional atribuído a um candidato durante uma
 * execução da política de seleção.
 */
public enum PublicationSelectionDecisionStatus {

    /**
     * O candidato entrou no conjunto escolhido pela seleção.
     *
     * <p>Isso ainda não significa que a quota foi reservada
     * definitivamente no PostgreSQL. A outbox deverá revalidar
     * a capacidade de maneira transacional.</p>
     */
    SELECTED,

    /**
     * O candidato não pode participar da seleção neste instante
     * porque ainda está dentro do hard cooldown.
     */
    DEFERRED_DUE_TO_HARD_COOLDOWN,

    /**
     * O candidato era elegível, porém ficou fora do conjunto
     * escolhido porque o snapshot de quota não possuía vagas
     * suficientes para todos os candidatos elegíveis.
     */
    NOT_SELECTED_DUE_TO_QUOTA
}
