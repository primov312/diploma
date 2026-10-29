-- AI-assisted (Claude), 2026; reviewed by the author.
-- Three kinds of users (customer, seller, administrator), seller-to-partner links, catalogue switches,
-- the human review of REVIEW decisions and an append-only audit log. Existing users become customers.

ALTER TABLE users ADD COLUMN role VARCHAR(10) NOT NULL DEFAULT 'CUSTOMER'
    CHECK (role IN ('CUSTOMER', 'SELLER', 'ADMIN'));
ALTER TABLE users ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- One seller user belongs to exactly one partner; a partner may have several seller users.
CREATE TABLE seller_accounts (
    user_id    BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    partner_id BIGINT NOT NULL REFERENCES partners(id)
);
CREATE INDEX idx_seller_accounts_partner ON seller_accounts(partner_id);

-- A seller_accounts row exists exactly for the users whose role is SELLER. The checks run at commit
-- (deferred), so a service can insert the user and its seller row in one transaction in either order.
CREATE FUNCTION check_seller_link() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    uid BIGINT;
    user_role VARCHAR(10);
    linked BOOLEAN;
BEGIN
    IF TG_TABLE_NAME = 'users' THEN
        uid := NEW.id;
    ELSIF TG_OP = 'DELETE' THEN
        uid := OLD.user_id;
    ELSE
        uid := NEW.user_id;
    END IF;
    SELECT role INTO user_role FROM users WHERE id = uid;
    IF user_role IS NULL THEN RETURN NULL; END IF;  -- the user itself was deleted (cascade)
    SELECT EXISTS (SELECT 1 FROM seller_accounts WHERE user_id = uid) INTO linked;
    IF user_role = 'SELLER' AND NOT linked THEN
        RAISE EXCEPTION 'seller user % has no partner', uid USING ERRCODE = 'check_violation';
    END IF;
    IF user_role <> 'SELLER' AND linked THEN
        RAISE EXCEPTION 'user % is linked to a partner but is not a seller', uid USING ERRCODE = 'check_violation';
    END IF;
    RETURN NULL;
END $$;

CREATE CONSTRAINT TRIGGER users_seller_link AFTER INSERT OR UPDATE OF role ON users
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_seller_link();
CREATE CONSTRAINT TRIGGER seller_accounts_link AFTER INSERT OR UPDATE OR DELETE ON seller_accounts
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_seller_link();

-- Inactive partners and products disappear from the public catalogue and cannot be quoted or bought.
ALTER TABLE partners ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE products ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

-- Human intervention (GDPR Art. 22(3); recital 56 of Directive (EU) 2023/2225): an administrator decides an
-- application that the automated assessment left in REVIEW. The automated decision itself is never changed.
CREATE TABLE application_reviews (
    id              BIGSERIAL PRIMARY KEY,
    application_id  BIGINT NOT NULL UNIQUE REFERENCES credit_applications(id),
    reviewer_id     BIGINT NOT NULL REFERENCES users(id),
    outcome         VARCHAR(10) NOT NULL CHECK (outcome IN ('APPROVED', 'REJECTED')),
    approved_amount NUMERIC(12,2) CHECK (approved_amount > 0),
    note            VARCHAR(500) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT application_reviews_amount CHECK (
        (outcome = 'APPROVED' AND approved_amount IS NOT NULL) OR (outcome = 'REJECTED' AND approved_amount IS NULL))
);

-- Who did what to which object. details never holds passwords, hashes, tokens or free text about customers.
CREATE TABLE audit_events (
    id            BIGSERIAL PRIMARY KEY,
    actor_user_id BIGINT REFERENCES users(id),
    actor_role    VARCHAR(10) NOT NULL,
    action        VARCHAR(60) NOT NULL,
    target_type   VARCHAR(40) NOT NULL,
    target_id     VARCHAR(80),
    details       JSONB NOT NULL DEFAULT '{}',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_events_created ON audit_events(created_at DESC);

-- Audit rows and review decisions are append-only: the database refuses to change or delete them.
CREATE FUNCTION refuse_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME USING ERRCODE = 'insufficient_privilege';
END $$;
CREATE TRIGGER audit_events_append_only BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION refuse_change();
CREATE TRIGGER application_reviews_append_only BEFORE UPDATE OR DELETE ON application_reviews
    FOR EACH ROW EXECUTE FUNCTION refuse_change();
