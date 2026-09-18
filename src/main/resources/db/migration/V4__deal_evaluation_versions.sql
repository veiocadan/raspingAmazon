/*
 * FASE 8.5-C
 *
 * Separa explicitamente as versões dos diferentes estágios
 * de decisão da oferta.
 *
 * Antes desta migration, filter_version estava sendo usado
 * para armazenar a política de elegibilidade Amazon:
 *
 * AMAZON_SELLER_DELIVERY_V1
 *
 * Isso criava uma colisão semântica, pois a FASE 9 também
 * precisa de uma versão própria para o perfil de filtros.
 */

ALTER TABLE deal_evaluation
    ADD COLUMN eligibility_policy_version TEXT,
    ADD COLUMN filter_profile_version TEXT,
    ADD COLUMN score_version TEXT,
    ADD COLUMN momentum_version TEXT;

/*
 * Os registros históricos existentes foram produzidos somente
 * pela política Amazon da FASE 8.
 *
 * Portanto, o conteúdo antigo de filter_version representa,
 * na prática, eligibility_policy_version.
 */
UPDATE deal_evaluation
SET eligibility_policy_version = filter_version
WHERE eligibility_policy_version IS NULL;

/*
 * Toda avaliação atual obrigatoriamente possui uma política
 * de elegibilidade.
 */
ALTER TABLE deal_evaluation
    ALTER COLUMN eligibility_policy_version SET NOT NULL;

/*
 * filter_profile_version, score_version e momentum_version
 * permanecem opcionais.
 *
 * Eles só serão preenchidos quando suas respectivas fases
 * forem implementadas.
 */

/*
 * A coluna antiga não é mais semanticamente correta.
 *
 * Depois de migrar o conteúdo histórico para
 * eligibility_policy_version, ela pode ser removida.
 */
ALTER TABLE deal_evaluation
    DROP COLUMN filter_version;