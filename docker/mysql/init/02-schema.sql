-- db_api
USE db_api;

CREATE TABLE IF NOT EXISTS users (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL UNIQUE,
    phone       VARCHAR(255) NOT NULL,
    password    VARCHAR(255) NOT NULL,
    role        VARCHAR(50)  NOT NULL,
    created_at  DATETIME(6),
    updated_at  DATETIME(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS merchants (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    name        VARCHAR(255) NOT NULL,
    phone       VARCHAR(255) NOT NULL,
    type        VARCHAR(50)  NOT NULL,
    created_at  DATETIME(6),
    updated_at  DATETIME(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS resources (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id   BIGINT       NOT NULL,
    name          VARCHAR(255) NOT NULL,
    description   TEXT,
    price         BIGINT       NOT NULL,
    max_capacity  INT          NOT NULL,
    created_at    DATETIME(6),
    updated_at    DATETIME(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS available_times (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    resource_id  BIGINT      NOT NULL,
    start_time   DATETIME(6) NOT NULL,
    end_time     DATETIME(6) NOT NULL,
    status       VARCHAR(50) NOT NULL,
    created_at   DATETIME(6),
    updated_at   DATETIME(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- db_reservation
USE db_reservation;

CREATE TABLE IF NOT EXISTS reservations (
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
    created_at        DATETIME(6),
    updated_at        DATETIME(6),
    INDEX idx_reservation_resource_time (resource_id, start_time, end_time),
    INDEX idx_reservation_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- db_payment
USE db_payment;

CREATE TABLE IF NOT EXISTS payments (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id  BIGINT      NOT NULL UNIQUE,
    user_id         BIGINT      NOT NULL,
    amount          BIGINT      NOT NULL,
    status          VARCHAR(50) NOT NULL,
    paid_at         DATETIME(6),
    failed_reason   VARCHAR(255),
    created_at      DATETIME(6),
    updated_at      DATETIME(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- db_notification
USE db_notification;

CREATE TABLE IF NOT EXISTS notifications (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT      NOT NULL,
    reservation_id  BIGINT      NOT NULL,
    type            VARCHAR(50) NOT NULL,
    channel         VARCHAR(50) NOT NULL,
    status          VARCHAR(50) NOT NULL,
    sent_at         DATETIME(6),
    created_at      DATETIME(6),
    updated_at      DATETIME(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;