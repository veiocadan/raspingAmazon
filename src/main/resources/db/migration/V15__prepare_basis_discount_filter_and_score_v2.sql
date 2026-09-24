/*
 * Prepara a evolução versionada definida pelas ADR-0005 e ADR-0006.
 *
 * Esta migration NÃO ativa COMMERCIAL_FILTER_V2 nem SCORE_V2.
 *
 * Objetivos:
 *
 * 1. preservar integralmente os perfis V1 históricos;
 * 2. permitir que FilterProfile represente desconto contra basisPrice;
 * 3. permitir que ScoreProfile represente BASIS_DISCOUNT;
 * 4. cadastrar as versões V2 como configuração inativa;
 * 5. manter o comportamento operacional V1 até a ativação explícita
 *    em migration posterior.
 */


/* ================================================================
 * COMMERCIAL FILTER PROFILE
 * ================================================================ */

ALTER TABLE filter_profile
    ALTER COLUMN min_cash_discount_percentage DROP NOT NULL;

ALTER TABLE filter_profile
    ADD COLUMN min_basis_discount_percentage NUMERIC(7, 4);

ALTER TABLE filter_profile
    ADD CONSTRAINT ck_filter_profile_basis_discount_range
        CHECK (
            min_basis_discount_percentage IS NULL
            OR (
                min_basis_discount_percentage >= 0
                AND min_basis_discount_percentage <= 100
            )
        );

ALTER TABLE filter_profile
    ADD CONSTRAINT ck_filter_profile_exactly_one_discount_threshold
        CHECK (
            (
                min_cash_discount_percentage IS NOT NULL
                AND min_basis_discount_percentage IS NULL
            )
            OR
            (
                min_cash_discount_percentage IS NULL
                AND min_basis_discount_percentage IS NOT NULL
            )
        );

INSERT INTO filter_profile (
    version,
    min_cash_discount_percentage,
    min_basis_discount_percentage,
    min_rating,
    min_review_count,
    active
)
VALUES (
    'COMMERCIAL_FILTER_V2',
    NULL,
    20.0000,
    4.30,
    100,
    false
);


/* ================================================================
 * SCORE PROFILE
 * ================================================================ */

ALTER TABLE score_profile
    ALTER COLUMN cash_discount_weight DROP NOT NULL;

ALTER TABLE score_profile
    ADD COLUMN basis_discount_weight NUMERIC(7, 4);

ALTER TABLE score_profile
    ADD CONSTRAINT ck_score_profile_basis_discount_weight_range
        CHECK (
            basis_discount_weight IS NULL
            OR (
                basis_discount_weight >= 0
                AND basis_discount_weight <= 100
            )
        );

ALTER TABLE score_profile
    ADD CONSTRAINT ck_score_profile_exactly_one_discount_weight
        CHECK (
            (
                cash_discount_weight IS NOT NULL
                AND basis_discount_weight IS NULL
            )
            OR
            (
                cash_discount_weight IS NULL
                AND basis_discount_weight IS NOT NULL
            )
        );

ALTER TABLE score_profile
    DROP CONSTRAINT ck_score_profile_total_weight;

ALTER TABLE score_profile
    ADD CONSTRAINT ck_score_profile_total_weight
        CHECK (
            sold_percentage_weight
            + COALESCE(
                cash_discount_weight,
                basis_discount_weight
            )
            + rating_weight
            + review_count_weight
            <= 100
        );

INSERT INTO score_profile (
    version,
    sold_percentage_weight,
    cash_discount_weight,
    basis_discount_weight,
    rating_weight,
    review_count_weight,
    review_count_full_score_threshold,
    active
)
VALUES (
    'SCORE_V2',
    30.0000,
    NULL,
    25.0000,
    20.0000,
    15.0000,
    1000,
    false
);
