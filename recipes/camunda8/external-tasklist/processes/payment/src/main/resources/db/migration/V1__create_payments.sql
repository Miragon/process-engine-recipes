CREATE SEQUENCE payment_number START WITH 1001;

CREATE TABLE payments
(
    id                   text PRIMARY KEY        DEFAULT 'PAY-' || nextval('payment_number'),
    order_id             text           NOT NULL UNIQUE,
    amount               numeric(10, 2) NOT NULL CHECK (amount > 0),
    currency             text           NOT NULL,
    customer_name        text           NOT NULL,
    customer_email       text           NOT NULL,
    callback_url         text           NOT NULL,
    status               text           NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'PAID', 'REJECTED')),
    rejection_reason     text,
    amount_received      numeric(10, 2),
    reminders_sent       int            NOT NULL DEFAULT 0,
    process_instance_key bigint,
    created_at           timestamptz    NOT NULL DEFAULT now(),
    decided_at           timestamptz,
    reported_at          timestamptz
);

ALTER SEQUENCE payment_number OWNED BY payments.id;
