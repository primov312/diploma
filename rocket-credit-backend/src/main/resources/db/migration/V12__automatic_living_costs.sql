-- Preserve older revisions; new calculations use automatic costs and fixture income/debt.
DO $$
DECLARE constraint_row record;
BEGIN
    FOR constraint_row IN SELECT conname FROM pg_constraint
        WHERE conrelid='financial_input_revisions'::regclass AND contype='c'
          AND (pg_get_constraintdef(oid) LIKE '%expense_mode%' OR pg_get_constraintdef(oid) LIKE '%source%')
    LOOP EXECUTE format('ALTER TABLE financial_input_revisions DROP CONSTRAINT %I', constraint_row.conname); END LOOP;
END $$;
ALTER TABLE financial_input_revisions ADD CONSTRAINT financial_expense_mode
    CHECK (expense_mode IN ('AGGREGATE','ITEMIZED','AUTOMATIC'));
ALTER TABLE financial_input_revisions ADD CONSTRAINT financial_source
    CHECK (source IN ('STARTER','FIXTURE','USER_DECLARED','AUTOMATIC'));
ALTER TABLE financial_input_revisions ADD CONSTRAINT financial_expense_layout CHECK (
    (expense_mode='AGGREGATE' AND legacy_living_expenses IS NOT NULL AND housing_cost IS NULL
        AND groceries_cost IS NULL AND utilities_cost IS NULL AND transport_cost IS NULL AND other_living_costs IS NULL)
    OR (expense_mode='ITEMIZED' AND legacy_living_expenses IS NULL AND housing_cost IS NOT NULL
        AND groceries_cost IS NOT NULL AND utilities_cost IS NOT NULL AND transport_cost IS NOT NULL AND other_living_costs IS NOT NULL)
    OR (expense_mode='AUTOMATIC' AND legacy_living_expenses IS NOT NULL AND housing_cost IS NULL
        AND groceries_cost IS NULL AND utilities_cost IS NULL AND transport_cost IS NULL AND other_living_costs IS NULL));

INSERT INTO financial_input_revisions(user_id,revision,monthly_net_income,housing_situation,expense_mode,legacy_living_expenses,monthly_obligations,source)
SELECT s.user_id,s.current_revision+1,p.monthly_income,r.housing_situation,'AUTOMATIC',p.monthly_expenses,p.monthly_obligations,'AUTOMATIC'
FROM financial_input_state s JOIN financial_input_revisions r ON r.user_id=s.user_id AND r.revision=s.current_revision
JOIN demo_financial_profiles p ON p.user_id=s.user_id;
UPDATE financial_input_state SET current_revision=current_revision+1,generation=generation+1;
-- Existing profiles keep their dataset pin; grocery quantities and other amounts are reset; area/sharing are preserved.
INSERT INTO user_living_cost_profiles(user_id,revision,dataset_version,apartment_size,rent_sharers,grocery_quantities,other_spending,source)
SELECT p.user_id,p.revision+1,p.dataset_version,p.apartment_size,p.rent_sharers,d.snapshot->'defaults'->'groceryQuantities',0,p.source
FROM (SELECT DISTINCT ON(user_id) * FROM user_living_cost_profiles ORDER BY user_id,revision DESC) p
JOIN monthly_living_cost_datasets d USING(dataset_version);
