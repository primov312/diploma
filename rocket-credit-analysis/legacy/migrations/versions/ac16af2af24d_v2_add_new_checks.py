"""V2 add new checks

Revision ID: ac16af2af24d
Revises: c9f16d8fe85c
Create Date: 2025-08-14 11:53:28.409567

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'ac16af2af24d'
down_revision: Union[str, Sequence[str], None] = 'c9f16d8fe85c'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    op.execute("""
    -- Assume credit_requests exists; alter for factors
    ALTER TABLE credit_requests
    ADD COLUMN IF NOT EXISTS social_factors JSONB,  -- e.g., {"platforms": ["vkontakte"], "score_boost": 20}
    ADD COLUMN IF NOT EXISTS email_factor JSONB,    -- e.g., {"verified": true, "score_boost": 15}
    ADD COLUMN IF NOT EXISTS history_factor JSONB;  -- e.g., {"on_time_rate": 0.95, "delinquencies": 1, "score_boost": 25}

    -- GIN index for JSONB queries if needed
    CREATE INDEX IF NOT EXISTS idx_credit_social_factors ON credit_requests USING GIN (social_factors);
    """)


def downgrade() -> None:
    """Downgrade schema."""
    pass
