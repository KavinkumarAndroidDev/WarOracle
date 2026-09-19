-- ==========================================================
-- Migration V2: Create War Feedback Table for Mailbox
-- Date: 2026-09-17
-- ==========================================================

CREATE TABLE IF NOT EXISTS war_feedback (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    contact VARCHAR(100),
    client_ip VARCHAR(45),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_feedback_created (created_at),
    KEY idx_feedback_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
