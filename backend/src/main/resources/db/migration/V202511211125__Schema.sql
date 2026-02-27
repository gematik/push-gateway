CREATE TABLE IF NOT EXISTS rejected_push_keys
(
    push_key  VARCHAR(255) PRIMARY KEY,
    app_id    VARCHAR(255) NOT NULL,
    reason    VARCHAR(255),
    timestamp TIMESTAMP    NOT NULL
);
