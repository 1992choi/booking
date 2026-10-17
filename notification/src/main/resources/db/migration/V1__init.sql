CREATE TABLE users (
    id          BIGINT PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    phone       VARCHAR(255) NOT NULL,
    created_at  DATETIME(6),
    updated_at  DATETIME(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notifications (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT      NOT NULL,
    reservation_id  BIGINT,
    message         TEXT,
    type            VARCHAR(50) NOT NULL,
    channel         VARCHAR(50) NOT NULL,
    status          VARCHAR(50) NOT NULL,
    sent_at         DATETIME(6),
    read_at         DATETIME(6),
    created_at      DATETIME(6),
    updated_at      DATETIME(6),
    UNIQUE KEY uk_notification_reservation_type (reservation_id, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
