CREATE DATABASE IF NOT EXISTS filer CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE filer;

CREATE TABLE IF NOT EXISTS file_record (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    file_id      VARCHAR(64)  NOT NULL UNIQUE COMMENT 'UUID for the file',
    original_name VARCHAR(512) NOT NULL,
    stored_name  VARCHAR(512) NOT NULL,
    file_path    VARCHAR(1024) NOT NULL,
    file_size    BIGINT       NOT NULL DEFAULT 0,
    mime_type    VARCHAR(128),
    extension    VARCHAR(32),
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_file_id (file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS conversion_job (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_id          VARCHAR(64)  NOT NULL UNIQUE,
    source_file_id  VARCHAR(64)  NOT NULL,
    output_file_id  VARCHAR(64),
    conversion_type VARCHAR(64)  NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    options         JSON,
    error_message   TEXT,
    progress        INT          NOT NULL DEFAULT 0,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    completed_at    DATETIME,
    INDEX idx_job_id (job_id),
    INDEX idx_source_file (source_file_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
