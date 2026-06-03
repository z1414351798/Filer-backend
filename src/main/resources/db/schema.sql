CREATE DATABASE IF NOT EXISTS filer DEFAULT CHARSET utf8mb4;
USE filer;

CREATE TABLE IF NOT EXISTS `user` (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS file_record (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_id       VARCHAR(36)  NOT NULL UNIQUE,
    original_name VARCHAR(512) NOT NULL,
    stored_name   VARCHAR(512) NOT NULL,
    file_path     VARCHAR(1024) NOT NULL,
    file_size     BIGINT       NOT NULL DEFAULT 0,
    mime_type     VARCHAR(255),
    extension     VARCHAR(20),
    user_id       BIGINT,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_file_id (file_id),
    INDEX idx_user_id (user_id)
);

CREATE TABLE IF NOT EXISTS conversion_job (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id          VARCHAR(36)   NOT NULL UNIQUE,
    source_file_id  VARCHAR(36)   NOT NULL,
    output_file_id  VARCHAR(36),
    conversion_type VARCHAR(50)   NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    progress        INT           NOT NULL DEFAULT 0,
    options         TEXT,
    error_message   TEXT,
    notify_email    VARCHAR(255),
    webhook_url     VARCHAR(1024),
    user_id         BIGINT,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    DATETIME,
    updated_at      DATETIME,
    INDEX idx_job_id (job_id),
    INDEX idx_status (status),
    INDEX idx_user_id (user_id)
);

CREATE TABLE IF NOT EXISTS share_link (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    token               VARCHAR(36)  NOT NULL UNIQUE,
    file_id             BIGINT       NOT NULL,
    created_by_user_id  BIGINT,
    expires_at          DATETIME     NOT NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_token (token)
);

CREATE TABLE IF NOT EXISTS conversion_preset (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    name            VARCHAR(100) NOT NULL,
    conversion_type VARCHAR(50)  NOT NULL,
    params_json     TEXT,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);
