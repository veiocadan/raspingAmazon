package com.raspingamazon.domain.evaluation;

import com.raspingamazon.domain.deal.OfferSnapshot;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Representa o resultado agregado da avaliação de uma oferta.
 *
 * <p>DealEvaluation não executa as regras de negócio por conta própria.
 * Ela registra o resultado produzido pelos diferentes estágios de
 * decisão do sistema.</p>
 *
 * <p>Os estágios são versionados separadamente para permitir
 * reprodutibilidade histórica:</p>
 *
 * <ul>
 *     <li>política de elegibilidade estrutural;</li>
 *     <li>perfil de filtros;</li>
 *     <li>algoritmo de score;</li>
 *     <li>algoritmo de momentum.</li>
 * </ul>
 *
 * <p>Nem todos esses estágios já existem. Por isso somente
 * eligibilityPolicyVersion é obrigatória nesta fase.</p>
 */
public final class DealEvaluation {

    /**
     * Identificador persistente.
     *
     * <p>Pode ser nulo antes da persistência.</p>
     */
    private final Long id;

    /**
     * Snapshot avaliado.
     */
    private final OfferSnapshot offerSnapshot;

    /**
     * Resultado agregado de elegibilidade.
     */
    private final boolean eligible;

    /**
     * Motivo principal de rejeição utilizado pelo modelo atual.
     *
     * <p>Na evolução para filtros múltiplos, explicações detalhadas
     * serão representadas por resultados de regra separados.</p>
     */
    private final RejectionReason rejectionReason;

    /**
     * Versão da política estrutural de elegibilidade.
     *
     * <p>Exemplo atual:</p>
     *
     * <pre>
     * AMAZON_SELLER_DELIVERY_V1
     * </pre>
     */
    private final String eligibilityPolicyVersion;

    /**
     * Versão do perfil de filtros da FASE 9.
     *
     * <p>Permanece nula enquanto filtros configuráveis ainda
     * não forem aplicados.</p>
     */
    private final String filterProfileVersion;

    /**
     * Pontuação produzida pelo algoritmo de scoring.
     *
     * <p>Ainda não implementado.</p>
     */
    private final BigDecimal score;

    /**
     * Versão do algoritmo de scoring.
     *
     * <p>Deve ser nula enquanto score não tiver sido calculado.</p>
     */
    private final String scoreVersion;

    /**
     * Indicador de momentum.
     *
     * <p>Ainda não implementado.</p>
     */
    private final BigDecimal momentum;

    /**
     * Versão do algoritmo de momentum.
     */
    private final String momentumVersion;

    /**
     * Instante da avaliação.
     */
    private final OffsetDateTime evaluatedAt;

    public DealEvaluation(
            Long id,
            OfferSnapshot offerSnapshot,
            boolean eligible,
            RejectionReason rejectionReason,
            String eligibilityPolicyVersion,
            String filterProfileVersion,
            BigDecimal score,
            String scoreVersion,
            BigDecimal momentum,
            String momentumVersion,
            OffsetDateTime evaluatedAt
    ) {
        this.id = id;

        this.offerSnapshot =
                Objects.requireNonNull(
                        offerSnapshot,
                        "DealEvaluation offerSnapshot must not be null"
                );

        this.eligible = eligible;

        /*
         * Mantemos temporariamente a invariante atual:
         *
         * - elegível -> sem rejectionReason;
         * - inelegível -> com rejectionReason.
         *
         * A explicabilidade multi-regra será introduzida
         * separadamente para não misturar responsabilidades.
         */
        if (eligible && rejectionReason != null) {
            throw new IllegalArgumentException(
                    "Eligible evaluation must not have rejectionReason"
            );
        }

        if (!eligible && rejectionReason == null) {
            throw new IllegalArgumentException(
                    "Ineligible evaluation must have rejectionReason"
            );
        }

        this.rejectionReason =
                rejectionReason;

        /*
         * Toda avaliação realizada atualmente obrigatoriamente passou
         * pela política estrutural Amazon.
         */
        this.eligibilityPolicyVersion =
                requireText(
                        eligibilityPolicyVersion,
                        "DealEvaluation eligibilityPolicyVersion must not be blank"
                );

        /*
         * Os filtros configuráveis ainda não existem.
         *
         * Quando existirem, a versão será registrada aqui.
         */
        this.filterProfileVersion =
                optionalText(
                        filterProfileVersion,
                        "DealEvaluation filterProfileVersion must not be blank"
                );

        this.score =
                score;

        this.scoreVersion =
                optionalText(
                        scoreVersion,
                        "DealEvaluation scoreVersion must not be blank"
                );

        this.momentum =
                momentum;

        this.momentumVersion =
                optionalText(
                        momentumVersion,
                        "DealEvaluation momentumVersion must not be blank"
                );

        /*
         * Se existe um score, ele precisa dizer qual algoritmo o produziu.
         *
         * Da mesma forma, não permitimos uma versão de score sem score.
         */
        if ((score == null) != (this.scoreVersion == null)) {
            throw new IllegalArgumentException(
                    "DealEvaluation score and scoreVersion must either both be present or both be null"
            );
        }

        /*
         * Mesma regra para momentum.
         */
        if ((momentum == null)
                != (this.momentumVersion == null)) {

            throw new IllegalArgumentException(
                    "DealEvaluation momentum and momentumVersion must either both be present or both be null"
            );
        }

        this.evaluatedAt =
                Objects.requireNonNull(
                        evaluatedAt,
                        "DealEvaluation evaluatedAt must not be null"
                );
    }

    /**
     * Valida texto obrigatório.
     */
    private static String requireText(
            String value,
            String message
    ) {
        Objects.requireNonNull(
                value,
                message
        );

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    message
            );
        }

        return value;
    }

    /**
     * Valida texto opcional.
     *
     * <p>null significa "a etapa ainda não foi aplicada".
     * String vazia, por outro lado, representa dado inválido.</p>
     */
    private static String optionalText(
            String value,
            String message
    ) {
        if (value == null) {
            return null;
        }

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    message
            );
        }

        return value;
    }

    public Long id() {
        return id;
    }

    public OfferSnapshot offerSnapshot() {
        return offerSnapshot;
    }

    public boolean eligible() {
        return eligible;
    }

    public RejectionReason rejectionReason() {
        return rejectionReason;
    }

    public String eligibilityPolicyVersion() {
        return eligibilityPolicyVersion;
    }

    public String filterProfileVersion() {
        return filterProfileVersion;
    }

    public BigDecimal score() {
        return score;
    }

    public String scoreVersion() {
        return scoreVersion;
    }

    public BigDecimal momentum() {
        return momentum;
    }

    public String momentumVersion() {
        return momentumVersion;
    }

    public OffsetDateTime evaluatedAt() {
        return evaluatedAt;
    }
}