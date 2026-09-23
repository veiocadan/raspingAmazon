ALTER TABLE publication
    ADD COLUMN commercial_presentation_version TEXT,
    ADD COLUMN affiliate_link_version TEXT;

UPDATE publication
SET commercial_presentation_version = 'LEGACY'
WHERE commercial_presentation_version IS NULL;

UPDATE publication
SET affiliate_link_version = 'LEGACY'
WHERE affiliate_link_version IS NULL;

ALTER TABLE publication
    ALTER COLUMN commercial_presentation_version SET NOT NULL,
    ALTER COLUMN affiliate_link_version SET NOT NULL;

ALTER TABLE publication
    ADD CONSTRAINT uq_publication_generation_identity
        UNIQUE (
            deal_evaluation_id,
            template_version,
            commercial_presentation_version,
            affiliate_link_version
        );

CREATE INDEX idx_publication_deal_evaluation
    ON publication (deal_evaluation_id);
