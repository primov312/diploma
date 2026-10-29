<!-- AI-assisted (Claude), 2026; reviewed by the author. -->
# Roles: use cases and data model

See [ROLES.md](ROLES.md) for the rules behind these diagrams.

## Use cases

Mermaid has no UML use-case diagram, so actors are drawn as rounded nodes and use cases as ellipses (`([...])`).

```mermaid
flowchart LR
    customer((Customer))
    seller((Seller))
    admin((Administrator))
    ai((AI provider<br/>Jev / TypeSafe))
    facebook((Facebook))

    subgraph RC[Rocket Credit]
        uc1([Browse marketplace])
        uc2([Apply for credit])
        uc3([Accept offer / repay])
        uc4([Manage wallet])
        uc5([Give or withdraw consent])
        uc6([Connect social account])
        uc7([See financed orders])
        uc8([Manage own catalogue])
        uc9([Manage accounts])
        uc10([Decide REVIEW application<br/>human intervention])
        uc11([Manage partners])
        uc12([Read audit log])
        uc13([Check documents and posts])
    end

    customer --- uc1 & uc2 & uc3 & uc4 & uc5 & uc6
    seller --- uc1 & uc7 & uc8
    admin --- uc1 & uc9 & uc10 & uc11 & uc12
    uc2 -.->|AI-assisted analysis, with consent| uc13
    uc13 --- ai
    uc6 --- facebook
```

## New tables (V17)

```mermaid
erDiagram
    users ||--o| seller_accounts : "is (role SELLER)"
    partners ||--o{ seller_accounts : "employs"
    partners ||--o{ products : "offers"
    credit_applications ||--o| application_reviews : "decided by a person"
    users ||--o{ application_reviews : "reviewer"
    users ||--o{ audit_events : "actor"

    users {
        bigint id PK
        varchar email
        varchar role "CUSTOMER | SELLER | ADMIN (new)"
        boolean enabled "new"
    }
    seller_accounts {
        bigint user_id PK,FK
        bigint partner_id FK
    }
    partners {
        bigint id PK
        varchar slug
        numeric amount_cap
        boolean active "new"
    }
    products {
        bigint id PK
        bigint partner_id FK
        numeric price
        boolean active "new"
    }
    credit_applications {
        bigint id PK
        varchar decision_status "automated, never changed"
    }
    application_reviews {
        bigint id PK
        bigint application_id FK "UNIQUE"
        bigint reviewer_id FK
        varchar outcome "APPROVED | REJECTED"
        numeric approved_amount "only for APPROVED"
        varchar note "10-500 chars"
        timestamptz created_at
    }
    audit_events {
        bigint id PK
        bigint actor_user_id FK
        varchar actor_role
        varchar action
        varchar target_type
        varchar target_id
        jsonb details "no secrets, no customer free text"
        timestamptz created_at
    }
```

`application_reviews` and `audit_events` are append-only (triggers refuse `UPDATE` and `DELETE`).
