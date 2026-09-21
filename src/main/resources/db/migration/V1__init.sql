CREATE TABLE payments
(
    id                  BIGINT AUTO_INCREMENT NOT NULL,
    order_id            VARCHAR(255) NULL,
    provider_order_id   VARCHAR(255) NULL,
    provider_payment_id VARCHAR(255) NULL,
    amount              BIGINT NULL,
    currency            VARCHAR(255) NULL,
    status              VARCHAR(255) NULL,
    user_id             BIGINT NULL,
    created_at          datetime NULL,
    updated_at          datetime NULL,
    CONSTRAINT pk_payments PRIMARY KEY (id)
);

CREATE TABLE revchanges
(
    rev        BIGINT NOT NULL,
    entityname VARCHAR(255) NULL
);

CREATE TABLE revinfo
(
    rev      BIGINT NOT NULL,
    revtstmp BIGINT NULL,
    CONSTRAINT pk_revinfo PRIMARY KEY (rev)
);

ALTER TABLE revchanges
    ADD CONSTRAINT fk_revchanges_on_default_tracking_modified_entities_changelog FOREIGN KEY (rev) REFERENCES revinfo (rev);