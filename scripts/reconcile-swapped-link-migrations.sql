-- Run manually with the backend stopped and after taking a database backup.
-- Only accepts the legacy V3 expansion / V4 ownership history. No application
-- tables or migration SQL are changed; applied timestamps and checksums remain.
\set ON_ERROR_STOP on
BEGIN;
LOCK TABLE public.flyway_schema_history IN EXCLUSIVE MODE;

DO $$
BEGIN
    IF (SELECT count(*) FROM public.flyway_schema_history
        WHERE version IN ('3', '4')) <> 2
        OR NOT EXISTS (SELECT 1 FROM public.flyway_schema_history
            WHERE version = '3' AND script = 'V3__expand_link_short_code.sql'
                AND description = 'expand link short code' AND type = 'SQL'
                AND checksum = 772876360 AND success)
        OR NOT EXISTS (SELECT 1 FROM public.flyway_schema_history
            WHERE version = '4' AND script = 'V4__add_link_owner.sql'
                AND description = 'add link owner' AND type = 'SQL'
                AND checksum = -1975026989 AND success) THEN
        RAISE EXCEPTION 'Unexpected Flyway history; reconciliation aborted';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'links'
                AND column_name = 'short_code' AND data_type = 'character varying'
                AND character_maximum_length = 32)
        OR NOT EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'links'
                AND column_name = 'owner_id' AND data_type = 'uuid'
                AND is_nullable = 'YES')
        OR NOT EXISTS (SELECT 1 FROM pg_constraint
            WHERE conrelid = 'public.links'::regclass
                AND pg_get_constraintdef(oid) = 'FOREIGN KEY (owner_id) REFERENCES users(id)')
        OR NOT EXISTS (SELECT 1 FROM pg_indexes
            WHERE schemaname = 'public' AND tablename = 'links'
                AND indexname = 'idx_links_owner_created'
                AND indexdef = 'CREATE INDEX idx_links_owner_created ON public.links USING btree (owner_id, created_at DESC, id DESC)') THEN
        RAISE EXCEPTION 'Expected link schema is missing; reconciliation aborted';
    END IF;
END $$;

UPDATE public.flyway_schema_history
SET version = CASE version WHEN '3' THEN '4' ELSE '3' END,
    script = CASE version
        WHEN '3' THEN 'V4__expand_link_short_code.sql'
        ELSE 'V3__add_link_owner.sql'
    END
WHERE version IN ('3', '4');

COMMIT;
