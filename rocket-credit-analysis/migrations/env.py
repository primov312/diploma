# migrations/env.py
from __future__ import annotations
import os
from logging.config import fileConfig

from sqlalchemy import engine_from_config, pool
from alembic import context

# Interpret the config file for Python logging.
config = context.config
if config.config_file_name is not None and os.path.exists(config.config_file_name):
    try:
        # Don't kill the run if logging sections are missing
        fileConfig(config.config_file_name, disable_existing_loggers=False)
    except Exception as e:
        # Optional: print or ignore – migration should continue
        print(f"[alembic] skipping logging config: {e}")

# Get URL from env or fall back (local dev example)
DB_URL = os.getenv(
    "SQLALCHEMY_URL",
    "postgresql+psycopg://postgres:postgres@localhost:5432/creditdb",
)
config.set_main_option("sqlalchemy.url", DB_URL)

target_metadata = None  # we're executing raw SQL, not autogenerate

def run_migrations_offline():
    url = config.get_main_option("sqlalchemy.url")
    context.configure(
        url=url,
        literal_binds=True,
        compare_type=True,
        dialect_opts={"paramstyle": "named"},
        transaction_per_migration=True,
    )
    with context.begin_transaction():
        context.run_migrations()

def run_migrations_online():
    connectable = engine_from_config(
        config.get_section(config.config_ini_section),
        prefix="sqlalchemy.",
        poolclass=pool.NullPool,
        future=True,
    )

    with connectable.connect() as connection:
        context.configure(
            connection=connection,
            target_metadata=target_metadata,
            compare_type=True,
            transaction_per_migration=True,
        )

        with context.begin_transaction():
            context.run_migrations()

if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()