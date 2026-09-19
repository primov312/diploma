"""V3 add user_external_id

Revision ID: a97f2cec16c7
Revises: ac16af2af24d
Create Date: 2025-08-14 11:56:18.876713

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'a97f2cec16c7'
down_revision: Union[str, Sequence[str], None] = 'ac16af2af24d'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""

    op.execute("""
    ALTER TABLE credit_requests
      ADD COLUMN IF NOT EXISTS user_external_id BIGINT;
    CREATE INDEX IF NOT EXISTS idx_credit_requests_user_external_id
      ON credit_requests(user_external_id);
    """)


def downgrade() -> None:
    """Downgrade schema."""
    
    op.execute("""
    DROP INDEX IF EXISTS idx_credit_requests_user_external_id;
    ALTER TABLE credit_requests
      DROP COLUMN IF EXISTS user_external_id;
    """)
