CREATE TABLE IF NOT EXISTS keyword_group_result (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    group_id      BIGINT NOT NULL,
    keyword_text  VARCHAR(255) NOT NULL,
    sort_order    INT NOT NULL DEFAULT 0,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_group_keyword (group_id, keyword_text),
    KEY idx_group_sort (group_id, sort_order, id),
    CONSTRAINT fk_keyword_group_result_group FOREIGN KEY (group_id) REFERENCES keyword_group(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='saved keyword-group preview results';
