package com.raspingamazon.domain.evaluation;

import com.raspingamazon.domain.deal.OfferSnapshot;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Representa o resultado de uma avaliação de uma oferta.
 *
 * DealEvaluation não realiza a avaliação por conta própria.
 * Ela representa o resultado produzido por uma regra de avaliação
 * que será implementada posteriormente.
 *
 * A separação é intencional:
 *
 * OfferSnapshot
 *      |
 *      v
 * processo de avaliação
 *      |
 *      v
 * DealEvaluation
 *
 * Dessa forma, o snapshot permanece como registro dos dados observados,
 * enquanto a avaliação registra a interpretação dessas informações pelas
 * regras de negócio.
 *
 * A entidade não conhece PostgreSQL, JDBC, Amazon, HTML, Excel ou canais
 * de publicação.
 */
public final class DealEvaluation {

    /**
     * Identificador interno da avaliação.
     *
     * Pode ser nulo antes da persistência, pois a estratégia de geração
     * do identificador pertence à infraestrutura.
     */
    private final Long id;

    /**
     * Snapshot que foi avaliado.
     *
     * Uma avaliação sempre precisa estar associada a uma fotografia
     * específica da oferta.
     */
    private final OfferSnapshot offerSnapshot;

    /**
     * Indica se a oferta foi considerada elegível pelas regras aplicadas.
     */
    private final boolean eligible;

    /**
     * Motivo controlado para uma eventual rejeição.
     *
     * Quando a oferta é elegível, o motivo pode ser nulo porque não
     * existe uma rejeição a registrar.
     */
    private final RejectionReason rejectionReason;

    /**
     * Identifica a versão das regras/filtros utilizadas na avaliação.
     *
     * A versão é importante para que uma avaliação histórica possa ser
     * interpretada sabendo quais regras estavam vigentes naquele momento.
     */
    private final String filterVersion;

    /**
     * Pontuação produzida pelas regras de scoring.
     *
     * O cálculo do score será implementado posteriormente.
     */
    private final BigDecimal score;

    /**
     * Indicador de momentum associado à avaliação.
     *
     * O significado e cálculo desse indicador serão definidos em etapa
     * posterior, portanto DealEvaluation apenas armazena o resultado.
     */
    private final BigDecimal momentum;

    /**
     * Momento em que a avaliação foi realizada.
     */
    private final OffsetDateTime evaluatedAt;

    /**
     * Construtor principal da avaliação.
     *
     * Este construtor não calcula elegibilidade, score ou momentum.
     * Ele somente garante as invariantes estruturais do resultado.
     */
    public DealEvaluation(
            Long id,
            OfferSnapshot offerSnapshot,
            boolean eligible,
            RejectionReason rejectionReason,
            String filterVersion,
            BigDecimal score,
            BigDecimal momentum,
            OffsetDateTime evaluatedAt
    ) {
        /*
         * O identificador interno pode ser nulo antes da persistência.
         */
        this.id = id;

        /*
         * Uma avaliação sem snapshot não possui o contexto necessário
         * para saber qual oferta foi avaliada.
         */
        this.offerSnapshot = Objects.requireNonNull(
                offerSnapshot,
                "DealEvaluation offerSnapshot must not be null"
        );

        this.eligible = eligible;

        /*
         * Uma avaliação elegível não pode possuir motivo de rejeição.
         */
        if (eligible && rejectionReason != null) {
            throw new IllegalArgumentException(
                    "eligible evaluation must not have rejectionReason"
            );
        }

        /*
         * Uma avaliação não elegível precisa registrar o motivo controlado
         * da rejeição.
         */
        if (!eligible && rejectionReason == null) {
            throw new IllegalArgumentException(
                    "ineligible evaluation must have rejectionReason"
            );
        }

        this.rejectionReason = rejectionReason;

        /*
         * A versão das regras é obrigatória para manter rastreabilidade
         * das avaliações realizadas.
         */
        this.filterVersion = requireText(
                filterVersion,
                "DealEvaluation filterVersion must not be blank"
        );

        /*
         * Score e momentum podem ainda não estar disponíveis quando
         * a avaliação estrutural for criada. A regra de scoring será
         * definida posteriormente.
         */
        this.score = score;
        this.momentum = momentum;

        /*
         * Toda avaliação precisa registrar quando foi realizada.
         */
        this.evaluatedAt = Objects.requireNonNull(
                evaluatedAt,
                "DealEvaluation evaluatedAt must not be null"
        );
    }

    /**
     * Valida campos textuais obrigatórios.
     */
    private static String requireText(String value, String message) {
        Objects.requireNonNull(value, message);

        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
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

    public String filterVersion() {
        return filterVersion;
    }

    public BigDecimal score() {
        return score;
    }

    public BigDecimal momentum() {
        return momentum;
    }

    public OffsetDateTime evaluatedAt() {
        return evaluatedAt;
    }
}