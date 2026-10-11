DO $$
BEGIN
    IF to_regclass('public.products') IS NOT NULL THEN
        ALTER TABLE products ADD COLUMN IF NOT EXISTS parent_id bigint;
        ALTER TABLE products ADD COLUMN IF NOT EXISTS variant_key varchar(64);
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_product_parent' AND conrelid='products'::regclass) THEN
            ALTER TABLE products ADD CONSTRAINT fk_product_parent FOREIGN KEY (parent_id) REFERENCES products(id) NOT VALID;
            ALTER TABLE products VALIDATE CONSTRAINT fk_product_parent;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='uk_product_variant' AND conrelid='products'::regclass) THEN
            ALTER TABLE products ADD CONSTRAINT uk_product_variant UNIQUE (parent_id,variant_key);
        END IF;
        ALTER TABLE products ADD CONSTRAINT ck_product_variant_v1 CHECK (
            (parent_id IS NULL AND variant_key IS NULL) OR
            (parent_id IS NOT NULL AND variant_key IS NOT NULL AND parent_id <> id)
        ) NOT VALID;
        ALTER TABLE products VALIDATE CONSTRAINT ck_product_variant_v1;
        CREATE INDEX IF NOT EXISTS idx_product_parent ON products(parent_id);
    END IF;
END $$;
