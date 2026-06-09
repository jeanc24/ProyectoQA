CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE products (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(150) NOT NULL,
    sku           VARCHAR(50)  NOT NULL UNIQUE,
    description   TEXT,
    category_id   BIGINT REFERENCES categories(id),
    price         NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    quantity      INTEGER NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    min_stock     INTEGER NOT NULL DEFAULT 0 CHECK (min_stock >= 0),
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_products_name ON products(name);
CREATE INDEX idx_products_sku ON products(sku);
CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_active ON products(active);

CREATE TABLE stock_movements (
    id              BIGSERIAL PRIMARY KEY,
    product_id      BIGINT NOT NULL REFERENCES products(id),
    movement_type   VARCHAR(20) NOT NULL CHECK (movement_type IN ('IN', 'OUT', 'ADJUSTMENT')),
    quantity_before INTEGER NOT NULL,
    quantity_after  INTEGER NOT NULL,
    quantity_delta  INTEGER NOT NULL,
    notes           TEXT,
    performed_by    VARCHAR(150) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_stock_movements_product ON stock_movements(product_id);
CREATE INDEX idx_stock_movements_created ON stock_movements(created_at DESC);

-- Tablas de auditoría Hibernate Envers (Semana 4)
CREATE TABLE revinfo (
    rev      INTEGER NOT NULL PRIMARY KEY,
    revtstmp BIGINT
);

CREATE TABLE products_audit (
    id          BIGINT NOT NULL,
    rev         INTEGER NOT NULL REFERENCES revinfo(rev),
    revtype     SMALLINT,
    name        VARCHAR(150),
    sku         VARCHAR(50),
    description TEXT,
    category_id BIGINT,
    price       NUMERIC(12, 2),
    quantity    INTEGER,
    min_stock   INTEGER,
    active      BOOLEAN,
    created_at  TIMESTAMPTZ,
    updated_at  TIMESTAMPTZ,
    PRIMARY KEY (id, rev)
);
