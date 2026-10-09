ALTER TABLE links
    ADD COLUMN owner_id UUID REFERENCES users(id);

CREATE INDEX idx_links_owner_created
    ON links (owner_id, created_at DESC, id DESC);
