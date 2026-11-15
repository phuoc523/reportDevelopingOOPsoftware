CREATE DATABASE IF NOT EXISTS scentstation_identity
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE scentstation_identity;

CREATE TABLE IF NOT EXISTS tenants (
    id varchar(36) NOT NULL,
    active bit(1) NOT NULL,
    code varchar(80) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY UKaye4nalvpsbxpjq0vt2ulaiqi (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS users (
    id bigint NOT NULL AUTO_INCREMENT,
    active bit(1) NOT NULL,
    email varchar(160) NOT NULL,
    full_name varchar(120) NOT NULL,
    password varchar(255) NOT NULL,
    role enum('ADMIN','CUSTOMER','STAFF') NOT NULL,
    username varchar(80) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY UK6dotkott2kjsp8vw4d0m25fb7 (email),
    UNIQUE KEY UKr43af9ap4edm43mmtq01oddj6 (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS staff_users (
    id varchar(36) NOT NULL,
    enabled bit(1) NOT NULL,
    failed_attempts int NOT NULL,
    last_failed_login_at datetime(6) DEFAULT NULL,
    last_login_at datetime(6) DEFAULT NULL,
    locked_until datetime(6) DEFAULT NULL,
    manually_locked bit(1) NOT NULL,
    password_hash varchar(100) NOT NULL,
    username varchar(120) NOT NULL,
    tenant_id varchar(36) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY UK84ntv1iab9fa9byg67plxw9fb (username),
    KEY idx_staff_tenant (tenant_id),
    CONSTRAINT FK6if9tva3fwloew6srwbh1yfot
        FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS staff_user_roles (
    user_id varchar(36) NOT NULL,
    role_name varchar(64) NOT NULL,
    PRIMARY KEY (user_id, role_name),
    CONSTRAINT FK36maqyxh47rmg3vmmhqt33cho
        FOREIGN KEY (user_id) REFERENCES staff_users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
