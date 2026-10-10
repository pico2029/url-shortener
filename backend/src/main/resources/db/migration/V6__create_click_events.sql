CREATE TABLE click_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    link_id BIGINT NOT NULL REFERENCES links(id) ON DELETE CASCADE,
    occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_click_events_link_time ON click_events (link_id, occurred_at);
