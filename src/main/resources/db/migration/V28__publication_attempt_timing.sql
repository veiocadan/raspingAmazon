ALTER TABLE publication_attempt
    ADD COLUMN started_at TIMESTAMPTZ,
    ADD COLUMN finished_at TIMESTAMPTZ;

UPDATE publication_attempt
SET
    started_at = created_at,
    finished_at = created_at;

ALTER TABLE publication_attempt
    ALTER COLUMN started_at SET NOT NULL,
    ALTER COLUMN finished_at SET NOT NULL;

ALTER TABLE publication_attempt
    ADD CONSTRAINT ck_publication_attempt_timing
        CHECK (finished_at >= started_at);
