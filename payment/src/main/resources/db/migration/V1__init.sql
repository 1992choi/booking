CREATE TABLE users (
    id          BIGINT PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    phone       VARCHAR(255) NOT NULL,
    created_at  DATETIME(6),
    updated_at  DATETIME(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payments (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id      BIGINT      NOT NULL UNIQUE,
    user_id             BIGINT      NOT NULL,
    amount              BIGINT      NOT NULL,
    status              VARCHAR(50) NOT NULL,
    paid_at             DATETIME(6),
    failed_reason       VARCHAR(255),
    pg_transaction_id   VARCHAR(255),
    created_at          DATETIME(6),
    updated_at          DATETIME(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
