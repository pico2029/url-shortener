CREATE TABLE links (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    original_url VARCHAR(8192) NOT NULL,
    short_code VARCHAR(8) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_links_short_code UNIQUE (short_code)
);
