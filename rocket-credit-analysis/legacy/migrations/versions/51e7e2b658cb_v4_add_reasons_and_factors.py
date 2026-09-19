"""V4 add reasons and factors

Revision ID: 51e7e2b658cb
Revises: a97f2cec16c7
Create Date: 2025-08-18 12:53:19.355343

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '51e7e2b658cb'
down_revision: Union[str, Sequence[str], None] = 'a97f2cec16c7'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    op.execute("""
    -- columns to store explainability
    ALTER TABLE credit_requests
      ADD COLUMN IF NOT EXISTS reasons JSONB NOT NULL DEFAULT '[]'::jsonb,
      ADD COLUMN IF NOT EXISTS factors JSONB;

    -- GIN index to query by reason codes quickly
    CREATE INDEX IF NOT EXISTS ix_credit_requests_reasons_gin
      ON credit_requests
      USING GIN (reasons);

    -- helpful analytics indices
    DO $$ BEGIN
      IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname='public' AND indexname='ix_credit_requests_decision'
      ) THEN
        CREATE INDEX ix_credit_requests_decision ON credit_requests(decision);
      END IF;
    END $$;
    """)


def downgrade() -> None:
    """Downgrade schema."""
    op.execute("""
    DROP INDEX IF EXISTS ix_credit_requests_reasons_gin;
    DROP INDEX IF EXISTS ix_credit_requests_decision;
    ALTER TABLE credit_requests
      DROP COLUMN IF EXISTS factors,
      DROP COLUMN IF EXISTS reasons;
    """)
