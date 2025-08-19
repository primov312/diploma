"""V5 credit policy

Revision ID: d6ab4918d175
Revises: 51e7e2b658cb
Create Date: 2025-08-18 12:54:52.696846

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'd6ab4918d175'
down_revision: Union[str, Sequence[str], None] = '51e7e2b658cb'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.execute("""
    CREATE TABLE IF NOT EXISTS credit_policy_versions (
      id BIGSERIAL PRIMARY KEY,
      policy_name TEXT NOT NULL DEFAULT 'default',
      version INT NOT NULL,
      weights JSONB NOT NULL,
      thresholds JSONB NOT NULL,
      flags JSONB NOT NULL DEFAULT '{}'::jsonb,
      valid_from TIMESTAMPTZ NOT NULL DEFAULT now(),
      enabled BOOLEAN NOT NULL DEFAULT TRUE,
      created_by TEXT,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      notes TEXT
    );

    CREATE UNIQUE INDEX IF NOT EXISTS uq_credit_policy_name_version
      ON credit_policy_versions(policy_name, version);

    CREATE INDEX IF NOT EXISTS ix_credit_policy_enabled_from
      ON credit_policy_versions(policy_name, enabled, valid_from DESC);

    INSERT INTO credit_policy_versions (policy_name, version, weights, thresholds, flags, notes)
    VALUES (
      'default',
      1,
      '{"rocket": 0.5,"partner": 0.3,"amount": 0.2}',
      '{"approve": 0.60,"review": 0.50}',
      '{"enable_social": false}',
      'Seeded from code defaults'
    )
    ON CONFLICT (policy_name, version) DO NOTHING;
    """)


def downgrade() -> None:
    op.execute("""
    CREATE TABLE IF NOT EXISTS credit_policy_versions (
      id BIGSERIAL PRIMARY KEY,
      policy_name TEXT NOT NULL DEFAULT 'default',
      version INT NOT NULL,
      weights JSONB NOT NULL,
      thresholds JSONB NOT NULL,
      flags JSONB NOT NULL DEFAULT '{}'::jsonb,
      valid_from TIMESTAMPTZ NOT NULL DEFAULT now(),
      enabled BOOLEAN NOT NULL DEFAULT TRUE,
      created_by TEXT,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      notes TEXT
    );

    CREATE UNIQUE INDEX IF NOT EXISTS uq_credit_policy_name_version
      ON credit_policy_versions(policy_name, version);

    CREATE INDEX IF NOT EXISTS ix_credit_policy_enabled_from
      ON credit_policy_versions(policy_name, enabled, valid_from DESC);

    INSERT INTO credit_policy_versions (policy_name, version, weights, thresholds, flags, notes)
    VALUES (
    'default',
    1,
    jsonb_build_object('rocket', 0.5, 'partner', 0.3, 'amount', 0.2),
    jsonb_build_object('approve', 0.60, 'review', 0.50),
    jsonb_build_object('enable_social', false),
    'Seeded from code defaults'
    )
    ON CONFLICT (policy_name, version) DO NOTHING;
    """)
