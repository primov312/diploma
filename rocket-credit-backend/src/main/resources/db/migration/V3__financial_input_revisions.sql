CREATE TABLE financial_input_revisions (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    revision INTEGER NOT NULL CHECK (revision > 0),
    monthly_net_income NUMERIC(12,2) CHECK (monthly_net_income >= 0),
    housing_situation VARCHAR(12) NOT NULL CHECK (housing_situation IN ('RENTING', 'OWNER', 'FAMILY', 'OTHER')),
    expense_mode VARCHAR(12) NOT NULL CHECK (expense_mode IN ('AGGREGATE', 'ITEMIZED')),
    housing_cost NUMERIC(12,2) CHECK (housing_cost >= 0),
    groceries_cost NUMERIC(12,2) CHECK (groceries_cost >= 0),
    utilities_cost NUMERIC(12,2) CHECK (utilities_cost >= 0),
    transport_cost NUMERIC(12,2) CHECK (transport_cost >= 0),
    other_living_costs NUMERIC(12,2) CHECK (other_living_costs >= 0),
    legacy_living_expenses NUMERIC(12,2) CHECK (legacy_living_expenses >= 0),
    monthly_obligations NUMERIC(12,2) NOT NULL CHECK (monthly_obligations >= 0),
    source VARCHAR(20) NOT NULL CHECK (source IN ('STARTER', 'FIXTURE', 'USER_DECLARED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, revision),
    CHECK ((expense_mode = 'AGGREGATE' AND legacy_living_expenses IS NOT NULL
            AND housing_cost IS NULL AND groceries_cost IS NULL AND utilities_cost IS NULL
            AND transport_cost IS NULL AND other_living_costs IS NULL)
        OR (expense_mode = 'ITEMIZED' AND legacy_living_expenses IS NULL
            AND housing_cost IS NOT NULL AND groceries_cost IS NOT NULL AND utilities_cost IS NOT NULL
            AND transport_cost IS NOT NULL AND other_living_costs IS NOT NULL))
);

CREATE TABLE financial_input_state (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    current_revision INTEGER NOT NULL,
    FOREIGN KEY (user_id, current_revision)
        REFERENCES financial_input_revisions(user_id, revision)
        DEFERRABLE INITIALLY DEFERRED
);

INSERT INTO financial_input_revisions (
    user_id, revision, monthly_net_income, housing_situation, expense_mode,
    legacy_living_expenses, monthly_obligations, source
)
SELECT user_id, 1, monthly_income, 'OTHER', 'AGGREGATE', monthly_expenses,
       monthly_obligations, synthetic_source
FROM demo_financial_profiles;

INSERT INTO financial_input_state (user_id, current_revision)
SELECT user_id, 1 FROM demo_financial_profiles;
