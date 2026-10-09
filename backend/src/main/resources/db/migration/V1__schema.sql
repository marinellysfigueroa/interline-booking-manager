-- =============================================================================
-- V1: esquema inicial. Flyway aplica las migraciones al arrancar
-- (quarkus.flyway.migrate-at-start), igual que spring.flyway.* en Spring Boot.
-- =============================================================================

-- ---------------------------------------------------------------- catálogo
CREATE TABLE airport (
    code          VARCHAR(3)      PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    city          VARCHAR(80)  NOT NULL,
    country_code  VARCHAR(2)      NOT NULL,
    time_zone     VARCHAR(40)  NOT NULL,
    -- texto normalizado (minúsculas, sin tildes) para el autocompletado
    search_key    VARCHAR(300) NOT NULL
);
CREATE INDEX ix_airport_search_key ON airport (search_key);

CREATE TABLE airline (
    code             VARCHAR(2)  PRIMARY KEY,
    name             VARCHAR(80) NOT NULL,
    accounting_code  VARCHAR(3)     NOT NULL,
    loyalty_program  VARCHAR(80)
);

-- Acuerdos interline desde la perspectiva de la validadora (airline_code).
CREATE TABLE airline_interline_partner (
    airline_code  VARCHAR(2) NOT NULL REFERENCES airline (code),
    partner_code  VARCHAR(2) NOT NULL REFERENCES airline (code),
    PRIMARY KEY (airline_code, partner_code),
    CONSTRAINT ck_partner_not_self CHECK (airline_code <> partner_code)
);

-- ---------------------------------------------------------------- ofertas
-- Ofertas devueltas por el proveedor; se reserva por offer_id sin confiar en el cliente.
CREATE TABLE flight_offer (
    offer_id    VARCHAR(64) PRIMARY KEY,
    payload     JSONB       NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_flight_offer_expires_at ON flight_offer (expires_at);

-- ---------------------------------------------------------------- reservas
CREATE TABLE booking (
    id                  UUID         PRIMARY KEY,
    locator             VARCHAR(6)      NOT NULL,
    validating_carrier  VARCHAR(2)   NOT NULL REFERENCES airline (code),
    offer_id            VARCHAR(64)  NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    contact_email       VARCHAR(120) NOT NULL,
    contact_phone       VARCHAR(20),
    fare_amount         NUMERIC(14, 4),
    fare_currency       VARCHAR(3),
    fare_miles          BIGINT,
    -- desnormalizados para el listado (evitan cargar los segmentos)
    origin              VARCHAR(3)      NOT NULL,
    destination         VARCHAR(3)      NOT NULL,
    first_departure     TIMESTAMP    NOT NULL,
    passenger_count     INT          NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    -- bloqueo optimista (@Version)
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_booking_locator UNIQUE (locator),
    CONSTRAINT ck_booking_status CHECK (status IN
        ('DRAFT', 'PRICED', 'HELD', 'PAYMENT_AUTHORIZED', 'TICKETED', 'FAILED', 'CANCELLED'))
);
CREATE INDEX ix_booking_listing ON booking (validating_carrier, status, created_at DESC);
CREATE INDEX ix_booking_created_at ON booking (created_at DESC);

CREATE TABLE booking_passenger (
    id                   UUID        PRIMARY KEY,
    booking_id           UUID        NOT NULL REFERENCES booking (id) ON DELETE CASCADE,
    position             INT         NOT NULL,
    type                 VARCHAR(3)  NOT NULL CHECK (type IN ('ADT', 'CHD', 'INF')),
    first_name           VARCHAR(50) NOT NULL,
    last_name            VARCHAR(50) NOT NULL,
    date_of_birth        DATE        NOT NULL,
    associated_adult_id  UUID        REFERENCES booking_passenger (id)
);
CREATE INDEX ix_booking_passenger_booking ON booking_passenger (booking_id);

CREATE TABLE booking_segment (
    id                 UUID        PRIMARY KEY,
    booking_id         UUID        NOT NULL REFERENCES booking (id) ON DELETE CASCADE,
    position           INT         NOT NULL,
    operating_carrier  VARCHAR(2)  NOT NULL,
    flight_number      VARCHAR(5)  NOT NULL,
    origin             VARCHAR(3)     NOT NULL,
    destination        VARCHAR(3)     NOT NULL,
    -- horas LOCALES del aeropuerto: TIMESTAMP sin zona a propósito
    departure_local    TIMESTAMP   NOT NULL,
    arrival_local      TIMESTAMP   NOT NULL,
    status             VARCHAR(2)  NOT NULL CHECK (status IN ('HK', 'UC', 'XX'))
);
CREATE INDEX ix_booking_segment_booking ON booking_segment (booking_id);

CREATE TABLE booking_payment (
    id                      UUID           PRIMARY KEY,
    booking_id              UUID           NOT NULL REFERENCES booking (id) ON DELETE CASCADE,
    method                  VARCHAR(5)     NOT NULL CHECK (method IN ('CASH', 'MILES', 'MIXED')),
    cash_amount             NUMERIC(14, 4),
    cash_currency           VARCHAR(3),
    miles                   BIGINT         NOT NULL DEFAULT 0,
    member_number           VARCHAR(16),
    cash_authorization_ref  VARCHAR(64),
    miles_hold_ref          VARCHAR(64),
    status                  VARCHAR(10)    NOT NULL CHECK (status IN ('AUTHORIZED', 'CAPTURED', 'RELEASED')),
    created_at              TIMESTAMPTZ    NOT NULL
);
CREATE INDEX ix_booking_payment_booking ON booking_payment (booking_id);

CREATE TABLE booking_ticket (
    number        VARCHAR(13)    PRIMARY KEY,
    booking_id    UUID        NOT NULL REFERENCES booking (id) ON DELETE CASCADE,
    passenger_id  UUID        NOT NULL REFERENCES booking_passenger (id),
    issued_at     TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_booking_ticket_booking ON booking_ticket (booking_id);

CREATE TABLE booking_status_change (
    id          BIGSERIAL    PRIMARY KEY,
    booking_id  UUID         NOT NULL REFERENCES booking (id) ON DELETE CASCADE,
    position    INT          NOT NULL,
    from_status VARCHAR(20),
    to_status   VARCHAR(20)  NOT NULL,
    changed_at  TIMESTAMPTZ  NOT NULL,
    reason      VARCHAR(300)
);
CREATE INDEX ix_booking_status_change_booking ON booking_status_change (booking_id);

-- Serie de los números de ticket (10 dígitos tras el prefijo contable).
CREATE SEQUENCE ticket_serial_seq START WITH 2400000000 MAXVALUE 9999999999;

-- ---------------------------------------------------------------- idempotencia
-- Registro de peticiones con Idempotency-Key (draft-ietf-httpapi-idempotency-key-header).
CREATE TABLE idempotency_record (
    scope            VARCHAR(80)  NOT NULL,
    idempotency_key  VARCHAR(64)  NOT NULL,
    request_hash     VARCHAR(64)     NOT NULL,
    status           VARCHAR(12)  NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    response_status  INT,
    response_body    TEXT,
    content_type     VARCHAR(60),
    location         VARCHAR(200),
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (scope, idempotency_key)
);
CREATE INDEX ix_idempotency_record_created_at ON idempotency_record (created_at);
