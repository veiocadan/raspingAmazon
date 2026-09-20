package com.raspingamazon.domain.scoring;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Resultado agregado de uma execução do motor de score.
 *
 * <p>ScoreResult representa o resultado final reproduzível de uma
 * determinada versão de score. Além da pontuação final, preserva
 * todos os fatores utilizados para produzi-la.</p>
 *
 * <p>As principais invariantes são:</p>
 *
 * <ul>
 *     <li>a versão do score deve estar presente;</li>
 *     <li>deve existir ao menos um fator;</li>
 *     <li>não pode haver fatores duplicados;</li>
 *     <li>o score deve permanecer entre 0 e 100;</li>
 *     <li>o score deve corresponder à soma das contribuições,
 *         arredondada para quatro casas decimais com HALF_UP.</li>
 * </ul>
 *
 * <p>A lista de fatores é defensivamente copiada para impedir que
 * o resultado seja modificado depois de criado.</p>
 */
public record ScoreResult(
    String version,
    BigDecimal score,
    List<ScoreFactorResult> factors
) {

    private static final BigDecimal MIN_SCORE = BigDecimal.ZERO;
    private static final BigDecimal MAX_SCORE = new BigDecimal("100");

    public static final int SCORE_SCALE = 4;
    public static final RoundingMode SCORE_ROUNDING_MODE = RoundingMode.HALF_UP;

    public ScoreResult {

        version = requireText(
            version,
            "ScoreResult version must not be blank"
        );

        score = Objects.requireNonNull(
            score,
            "ScoreResult score must not be null"
        );

        factors = Objects.requireNonNull(
            factors,
            "ScoreResult factors must not be null"
        );

        if (factors.isEmpty()) {
            throw new IllegalArgumentException(
                "ScoreResult factors must not be empty"
            );
        }

        factors = List.copyOf(factors);

        validateFactors(factors);

        BigDecimal normalizedScore = score.setScale(
            SCORE_SCALE,
            SCORE_ROUNDING_MODE
        );

        if (normalizedScore.compareTo(MIN_SCORE) < 0
            || normalizedScore.compareTo(MAX_SCORE) > 0) {

            throw new IllegalArgumentException(
                "ScoreResult score must be between 0 and 100"
            );
        }

        BigDecimal expectedScore = calculateScore(factors);

        if (normalizedScore.compareTo(expectedScore) != 0) {
            throw new IllegalArgumentException(
                "ScoreResult score must match the sum of factor contributions"
            );
        }

        score = normalizedScore;
    }

    /**
     * Cria um ScoreResult calculando automaticamente a pontuação
     * final a partir das contribuições dos fatores.
     */
    public static ScoreResult fromFactors(
        String version,
        List<ScoreFactorResult> factors
    ) {
        Objects.requireNonNull(
            factors,
            "ScoreResult factors must not be null"
        );

        return new ScoreResult(
            version,
            calculateScore(factors),
            factors
        );
    }

    /**
     * Retorna o fator correspondente ao código informado.
     *
     * <p>A ausência é representada por null porque a composição
     * efetiva de fatores pertence ao ScoreProfile versionado.</p>
     */
    public ScoreFactorResult factor(
        ScoreFactorCode code
    ) {
        Objects.requireNonNull(
            code,
            "ScoreFactorCode must not be null"
        );

        return factors.stream()
            .filter(factor -> factor.code() == code)
            .findFirst()
            .orElse(null);
    }

    private static BigDecimal calculateScore(
        List<ScoreFactorResult> factors
    ) {
        Objects.requireNonNull(
            factors,
            "ScoreResult factors must not be null"
        );

        BigDecimal total = factors.stream()
            .map(ScoreFactorResult::contribution)
            .reduce(
                BigDecimal.ZERO,
                BigDecimal::add
            );

        return total.setScale(
            SCORE_SCALE,
            SCORE_ROUNDING_MODE
        );
    }

    private static void validateFactors(
        List<ScoreFactorResult> factors
    ) {
        Set<ScoreFactorCode> observedCodes = new HashSet<>();

        for (ScoreFactorResult factor : factors) {

            Objects.requireNonNull(
                factor,
                "ScoreResult factor must not be null"
            );

            if (!observedCodes.add(factor.code())) {
                throw new IllegalArgumentException(
                    "ScoreResult must not contain duplicate factor: "
                        + factor.code()
                );
            }
        }
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
            throw new IllegalArgumentException(message);
        }

        return value;
    }
}
