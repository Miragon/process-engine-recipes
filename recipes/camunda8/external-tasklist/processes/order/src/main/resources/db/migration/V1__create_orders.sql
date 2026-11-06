CREATE TABLE items
(
    id          TEXT PRIMARY KEY,
    name        TEXT           NOT NULL,
    description TEXT           NOT NULL,
    price       NUMERIC(10, 2) NOT NULL CHECK (price > 0)
);

CREATE SEQUENCE order_number START WITH 1001;

CREATE TABLE orders
(
    id                   TEXT PRIMARY KEY        DEFAULT 'ORD-' || nextval('order_number'),
    status               TEXT           NOT NULL CHECK (status IN ('CHECKING', 'CANCELED', 'DECLINED', 'AWAITING_PAYMENT',
                                                                   'PREPARING', 'SHIPPED', 'NOT_PAID')),
    customer_name        TEXT           NOT NULL,
    customer_email       TEXT           NOT NULL,
    customer_address     TEXT           NOT NULL,
    total                NUMERIC(10, 2) NOT NULL,
    check_comment        TEXT,
    cancel_reason        TEXT,
    payment_id           TEXT,
    payment_status       TEXT CHECK (payment_status IN ('PAID', 'REJECTED')),
    payment_reason       TEXT,
    shipping_date        DATE,
    delivery_note        TEXT,
    process_instance_key BIGINT,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- The price is copied from the catalog, so an order keeps the prices it was placed with.
CREATE TABLE order_items
(
    order_id TEXT           NOT NULL REFERENCES orders (id),
    item_id  TEXT           NOT NULL REFERENCES items (id),
    quantity INTEGER        NOT NULL CHECK (quantity BETWEEN 1 AND 10),
    price    NUMERIC(10, 2) NOT NULL,
    ready    BOOLEAN        NOT NULL DEFAULT FALSE,
    PRIMARY KEY (order_id, item_id)
);

INSERT INTO items (id, name, description, price)
VALUES ('city-helmet', 'City helmet', 'Lightweight helmet with an integrated rear light, size M', 79.90),
       ('folding-lock', 'Folding lock', 'Hardened steel folding lock with frame mount', 50.00),
       ('light-set', 'LED light set', 'Front and rear light, rechargeable over USB-C', 34.90),
       ('pannier-bag', 'Pannier bag', 'Waterproof 20 litre bag for the rear rack', 59.00),
       ('floor-pump', 'Floor pump', 'Floor pump with pressure gauge for all valve types', 29.90),
       ('inner-tube', 'Inner tube 28"', 'Inner tube 28 x 1.5-2.1 inch with Presta valve', 7.50);
