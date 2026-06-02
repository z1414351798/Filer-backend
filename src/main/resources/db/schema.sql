CREATE DATABASE IF NOT EXISTS filer CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE filer;

CREATE TABLE IF NOT EXISTS user (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    email         VARCHAR(256) NOT NULL UNIQUE,
    password_hash VARCHAR(256) NOT NULL,
    role          VARCHAR(32)  NOT NULL DEFAULT 'USER',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS file_record (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    file_id       VARCHAR(64)   NOT NULL UNIQUE,
    original_name VARCHAR(512)  NOT NULL,
    stored_name   VARCHAR(512)  NOT NULL,
    file_path     VARCHAR(1024) NOT NULL,
    file_size     BIGINT        NOT NULL DEFAULT 0,
    mime_type     VARCHAR(128),
    extension     VARCHAR(32),
    user_id       BIGINT,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_file_id (file_id),
    INDEX idx_user_id (user_id)
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
    user_id         BIGINT,
    notify_email    VARCHAR(256),
    webhook_url     VARCHAR(1024),
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    completed_at    DATETIME,
    INDEX idx_job_id (job_id),
    INDEX idx_source_file (source_file_id),
    INDEX idx_status (status),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS share_link (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    token      VARCHAR(64)   NOT NULL UNIQUE,
    file_id    VARCHAR(64)   NOT NULL,
    user_id    BIGINT,
    expires_at DATETIME      NOT NULL,
    created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_token (token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS conversion_preset (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    preset_id       VARCHAR(64)  NOT NULL UNIQUE,
    user_id         BIGINT,
    name            VARCHAR(128) NOT NULL,
    conversion_type VARCHAR(64)  NOT NULL,
    options_json    JSON,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
