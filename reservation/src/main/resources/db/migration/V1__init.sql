CREATE TABLE users (
    id          BIGINT PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    phone       VARCHAR(255) NOT NULL,
    created_at  DATETIME(6),
    updated_at  DATETIME(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE merchants (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    name        VARCHAR(255) NOT NULL,
    phone       VARCHAR(255) NOT NULL,
    type        VARCHAR(50)  NOT NULL,
    created_at  DATETIME(6),
    updated_at  DATETIME(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE resources (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id   BIGINT       NOT NULL,
    name          VARCHAR(255) NOT NULL,
    description   TEXT,
    price         BIGINT       NOT NULL,
    max_capacity  INT          NOT NULL,
    created_at    DATETIME(6),
    updated_at    DATETIME(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE available_times (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    resource_id  BIGINT      NOT NULL,
    start_time   DATETIME(6) NOT NULL,
    end_time     DATETIME(6) NOT NULL,
    status       VARCHAR(50) NOT NULL,
    created_at   DATETIME(6),
    updated_at   DATETIME(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE reservations (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    available_time_id BIGINT       NOT NULL,
    user_id           BIGINT       NOT NULL,
    resource_id       BIGINT       NOT NULL,
    resource_name     VARCHAR(255) NOT NULL,
    start_time        DATETIME(6)  NOT NULL,
    end_time          DATETIME(6)  NOT NULL,
    status            VARCHAR(50)  NOT NULL,
    head_count        INT          NOT NULL,
    amount            BIGINT       NOT NULL,
    reminder_sent_at  DATETIME(6),
    created_at        DATETIME(6),
    updated_at        DATETIME(6),
    INDEX idx_reservation_resource_time (resource_id, start_time, end_time),
    INDEX idx_reservation_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE reviews (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id  BIGINT       NOT NULL UNIQUE,
    merchant_id     BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    content         TEXT         NOT NULL,
    created_at      DATETIME(6),
    updated_at      DATETIME(6),
    INDEX idx_review_merchant (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- batch 모듈이 매일 새벽 집계해 쓰는 테이블 (docker/mysql/init 쪽에는 없었던 누락분 — Flyway로 이관하며 보강)
CREATE TABLE daily_merchant_stats (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_date        DATE   NOT NULL,
    merchant_id      BIGINT NOT NULL,
    confirmed_count  INT    NOT NULL,
    cancelled_count  INT    NOT NULL,
    total_revenue    BIGINT NOT NULL,
    UNIQUE KEY uk_daily_merchant_stats_date_merchant (stat_date, merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
