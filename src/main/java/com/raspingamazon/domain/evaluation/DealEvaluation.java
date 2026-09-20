package com.raspingamazon.domain.evaluation;

import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.scoring.ScoreFactorResult;
import com.raspingamazon.domain.scoring.ScoreResult;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Representa o resultado agregado da avaliação de uma oferta.
 *
 * <p>DealEvaluation registra:</p>
 *
 * <ul>
 *     <li>a decisão agregada;</li>
 *     <li>o motivo principal de rejeição;</li>
 *     <li>as versões das etapas aplicadas;</li>
 *     <li>os resultados individuais das regras;</li>
 *     <li>o score e seus fatores explicativos, quando existirem;</li>
 *     <li>momentum, quando existir.</li>
 * </ul>
 *
 * <p>Os resultados individuais permitem reconstruir exatamente
 * quais regras passaram ou falharam em uma avaliação histórica.</p>
 *
 * <p>Quando há score, os fatores preservam a explicação matemática
 * necessária para reproduzir a pontuação histórica.</p>
 */
public final class DealEvaluation {

    private final Long id;

    private final OfferSnapshot offerSnapshot;

    private final boolean eligible;

    /**
     * Motivo principal da rejeição.
     *
     * <p>É mantido como resumo agregado. A explicação completa
     * encontra-se em ruleResults.</p>
     */
    private final RejectionReason rejectionReason;

    private final String eligibilityPolicyVersion;

    private final String filterProfileVersion;

    /**
     * Resultados individuais das regras aplicadas.
     *
     * <p>A ordem é deliberadamente preservada.</p>
     */
    private final List<EvaluationRuleResult> ruleResults;

    private final BigDecimal score;

    private final String scoreVersion;

    /**
     * Decomposição auditável do score.
     *
     * <p>Quando não existe score, esta lista obrigatoriamente
     * permanece vazia.</p>
     */
    private final List<ScoreFactorResult> scoreFactors;

    private final BigDecimal momentum;

    private final String momentumVersion;

    private final OffsetDateTime evaluatedAt;

    /**
     * Construtor completo da avaliação.
     *
     * <p>Este é o contrato que deve ser utilizado por avaliações
     * pontuadas a partir da FASE 10.</p>
     */
    public DealEvaluation(
        Long id,
        OfferSnapshot offerSnapshot,
        boolean eligible,
        RejectionReason rejectionReason,
        String eligibilityPolicyVersion,
        String filterProfileVersion,
        List<EvaluationRuleResult> ruleResults,
        BigDecimal score,
        String scoreVersion,
        List<ScoreFactorResult> scoreFactors,
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

        this.eligibilityPolicyVersion =
            requireText(
                eligibilityPolicyVersion,
                "DealEvaluation eligibilityPolicyVersion must not be blank"
            );

        this.filterProfileVersion =
            optionalText(
                filterProfileVersion,
                "DealEvaluation filterProfileVersion must not be blank"
            );

        Objects.requireNonNull(
            ruleResults,
            "DealEvaluation ruleResults must not be null"
        );

        if (ruleResults.isEmpty()) {
            throw new IllegalArgumentException(
                "DealEvaluation ruleResults must not be empty"
            );
        }

        this.ruleResults =
            List.copyOf(
                ruleResults
            );

        boolean allRulesPassed =
            this.ruleResults.stream()
                .allMatch(
                    EvaluationRuleResult::passed
                );

        if (eligible != allRulesPassed) {
            throw new IllegalArgumentException(
                "DealEvaluation eligibility must match rule results"
            );
        }

        RejectionReason firstFailureReason =
            this.ruleResults.stream()
                .filter(
                    result -> !result.passed()
                )
                .map(
                    EvaluationRuleResult::reasonCode
                )
                .findFirst()
                .orElse(
                    null
                );

        if (eligible) {

            if (rejectionReason != null) {
                throw new IllegalArgumentException(
                    "Eligible evaluation must not have rejectionReason"
                );
            }

        } else {

            if (rejectionReason == null) {
                throw new IllegalArgumentException(
                    "Ineligible evaluation must have rejectionReason"
                );
            }

            if (rejectionReason != firstFailureReason) {
                throw new IllegalArgumentException(
                    "DealEvaluation rejectionReason must match the first failed rule"
                );
            }
        }

        this.rejectionReason =
            rejectionReason;

        this.scoreVersion =
            optionalText(
                scoreVersion,
                "DealEvaluation scoreVersion must not be blank"
            );

        Objects.requireNonNull(
            scoreFactors,
            "DealEvaluation scoreFactors must not be null"
        );

        this.scoreFactors =
            List.copyOf(
                scoreFactors
            );

        /*
         * Score e versão sempre aparecem juntos.
         */
        if ((score == null)
            != (this.scoreVersion == null)) {

            throw new IllegalArgumentException(
                "DealEvaluation score and scoreVersion must either both be present or both be null"
            );
        }

        /*
         * Uma avaliação sem score não pode carregar fatores.
         */
        if (score == null
            && !this.scoreFactors.isEmpty()) {

            throw new IllegalArgumentException(
                "DealEvaluation without score must not have scoreFactors"
            );
        }

        /*
         * Uma avaliação pontuada precisa ser elegível.
         *
         * O score atua somente depois de elegibilidade estrutural
         * e filtros comerciais.
         */
        if (score != null
            && !eligible) {

            throw new IllegalArgumentException(
                "Ineligible DealEvaluation must not have score"
            );
        }

        /*
         * Toda avaliação pontuada precisa carregar sua explicação.
         */
        if (score != null
            && this.scoreFactors.isEmpty()) {

            throw new IllegalArgumentException(
                "Scored DealEvaluation must have scoreFactors"
            );
        }

        /*
         * ScoreResult reaplica as invariantes matemáticas:
         *
         * - fatores não duplicados;
         * - score entre 0 e 100;
         * - score igual à soma das contribuições;
         * - arredondamento determinístico.
         *
         * Assim DealEvaluation não duplica a lógica matemática
         * de scoring.
         */
        if (score != null) {

            ScoreResult validatedScore =
                new ScoreResult(
                    this.scoreVersion,
                    score,
                    this.scoreFactors
                );

            this.score =
                validatedScore.score();

        } else {

            this.score =
                null;
        }

        this.momentum =
            momentum;

        this.momentumVersion =
            optionalText(
                momentumVersion,
                "DealEvaluation momentumVersion must not be blank"
            );

        /*
         * Momentum e versão sempre aparecem juntos.
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
     * Construtor de compatibilidade para avaliações ainda sem score.
     *
     * <p>Ele preserva os pontos existentes do código durante a
     * integração incremental da FASE 10.</p>
     *
     * <p>Uma avaliação pontuada não pode mais utilizar esta assinatura,
     * pois scoreFactors ficaria vazio e a invariável de explicabilidade
     * rejeitaria o objeto.</p>
     */
    public DealEvaluation(
        Long id,
        OfferSnapshot offerSnapshot,
        boolean eligible,
        RejectionReason rejectionReason,
        String eligibilityPolicyVersion,
        String filterProfileVersion,
        List<EvaluationRuleResult> ruleResults,
        BigDecimal score,
        String scoreVersion,
        BigDecimal momentum,
        String momentumVersion,
        OffsetDateTime evaluatedAt
    ) {
        this(
            id,
            offerSnapshot,
            eligible,
            rejectionReason,
            eligibilityPolicyVersion,
            filterProfileVersion,
            ruleResults,
            score,
            scoreVersion,
            List.of(),
            momentum,
            momentumVersion,
            evaluatedAt
        );
    }

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

    public List<EvaluationRuleResult> ruleResults() {
        return ruleResults;
    }

    public BigDecimal score() {
        return score;
    }

    public String scoreVersion() {
        return scoreVersion;
    }

    public List<ScoreFactorResult> scoreFactors() {
        return scoreFactors;
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
