/*
 * Ativa as versões comerciais definidas pelas ADR-0005 e ADR-0006.
 *
 * Pré-requisito:
 *
 * V15__prepare_basis_discount_filter_and_score_v2.sql
 *
 * Estado esperado antes desta migration:
 *
 * COMMERCIAL_FILTER_V1 = ativo
 * COMMERCIAL_FILTER_V2 = inativo
 *
 * SCORE_V1 = ativo
 * SCORE_V2 = inativo
 *
 * Esta migration é deliberadamente defensiva.
 *
 * Ela NÃO desativa genericamente "o perfil ativo".
 * Ela exige explicitamente o estado que esperamos encontrar.
 *
 * Se o banco estiver em estado diferente, a migration falha em vez
 * de alterar silenciosamente outra configuração.
 */


/* ================================================================
 * COMMERCIAL FILTER
 * ================================================================ */

DO $$
DECLARE
    active_version TEXT;
    valid_v2_count INTEGER;
    updated_rows INTEGER;
BEGIN

    SELECT version
      INTO active_version
      FROM filter_profile
     WHERE active = TRUE;

    IF active_version IS DISTINCT FROM 'COMMERCIAL_FILTER_V1' THEN

        RAISE EXCEPTION
            'Expected COMMERCIAL_FILTER_V1 to be the active filter profile before V16, but found %',
            COALESCE(
                active_version,
                '<none>'
            );

    END IF;

    SELECT COUNT(*)
      INTO valid_v2_count
      FROM filter_profile
     WHERE version = 'COMMERCIAL_FILTER_V2'
       AND active = FALSE
       AND min_cash_discount_percentage IS NULL
       AND min_basis_discount_percentage = 20.0000
       AND min_rating = 4.30
       AND min_review_count = 100;

    IF valid_v2_count <> 1 THEN

        RAISE EXCEPTION
            'Expected exactly one valid inactive COMMERCIAL_FILTER_V2 before activation, found %',
            valid_v2_count;

    END IF;

    UPDATE filter_profile
       SET active = FALSE
     WHERE version = 'COMMERCIAL_FILTER_V1'
       AND active = TRUE;

    GET DIAGNOSTICS updated_rows = ROW_COUNT;

    IF updated_rows <> 1 THEN

        RAISE EXCEPTION
            'Expected to deactivate exactly one COMMERCIAL_FILTER_V1 row, updated %',
            updated_rows;

    END IF;

    UPDATE filter_profile
       SET active = TRUE
     WHERE version = 'COMMERCIAL_FILTER_V2'
       AND active = FALSE;

    GET DIAGNOSTICS updated_rows = ROW_COUNT;

    IF updated_rows <> 1 THEN

        RAISE EXCEPTION
            'Expected to activate exactly one COMMERCIAL_FILTER_V2 row, updated %',
            updated_rows;

    END IF;

END
$$;


/* ================================================================
 * SCORE
 * ================================================================ */

DO $$
DECLARE
    active_version TEXT;
    valid_v2_count INTEGER;
    updated_rows INTEGER;
BEGIN

    SELECT version
      INTO active_version
      FROM score_profile
     WHERE active = TRUE;

    IF active_version IS DISTINCT FROM 'SCORE_V1' THEN

        RAISE EXCEPTION
            'Expected SCORE_V1 to be the active score profile before V16, but found %',
            COALESCE(
                active_version,
                '<none>'
            );

    END IF;

    SELECT COUNT(*)
      INTO valid_v2_count
      FROM score_profile
     WHERE version = 'SCORE_V2'
       AND active = FALSE
       AND sold_percentage_weight = 30.0000
       AND cash_discount_weight IS NULL
       AND basis_discount_weight = 25.0000
       AND rating_weight = 20.0000
       AND review_count_weight = 15.0000
       AND review_count_full_score_threshold = 1000;

    IF valid_v2_count <> 1 THEN

        RAISE EXCEPTION
            'Expected exactly one valid inactive SCORE_V2 before activation, found %',
            valid_v2_count;

    END IF;

    UPDATE score_profile
       SET active = FALSE
     WHERE version = 'SCORE_V1'
       AND active = TRUE;

    GET DIAGNOSTICS updated_rows = ROW_COUNT;

    IF updated_rows <> 1 THEN

        RAISE EXCEPTION
            'Expected to deactivate exactly one SCORE_V1 row, updated %',
            updated_rows;

    END IF;

    UPDATE score_profile
       SET active = TRUE
     WHERE version = 'SCORE_V2'
       AND active = FALSE;

    GET DIAGNOSTICS updated_rows = ROW_COUNT;

    IF updated_rows <> 1 THEN

        RAISE EXCEPTION
            'Expected to activate exactly one SCORE_V2 row, updated %',
            updated_rows;

    END IF;

END
$$;
