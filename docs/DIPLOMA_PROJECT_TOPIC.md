# Diploma Project Topic

## Title

AI-Based Credit Decision Web Application Using User, Store, Bank, and Digital Footprint Data

## Short Description

The goal of this diploma project is to design and implement a full web application for credit decision making in an online purchase and consumer financing scenario. The application collects data about the user, partner store activity, bank-related financial information, and the user's consent-based digital footprint. These data sources are analyzed by an AI-assisted decision model to estimate whether a user can receive credit and what maximum credit amount can be offered.

The system is built as a microservice web platform. A frontend application allows users to apply for credit, provide required consent, and view the decision result. A gateway service coordinates the request flow. The main backend modules are user data, web scraping for digital footprint collection, credit analysis, payment processing, and repayment planning. The analysis module combines rule-based scoring, affordability checks, and AI-generated risk signals to produce an explainable credit decision.

## Project Motivation

Traditional credit scoring often depends on limited historical financial records. This can exclude users who have insufficient credit history but still have stable income, responsible spending behavior, and trustworthy digital activity. Modern online commerce and banking data create additional signals that may help evaluate creditworthiness more accurately in digital credit services.

The project investigates how different categories of data can be combined in a transparent and privacy-aware credit decision system. The main challenge is to improve decision quality while keeping the model explainable, auditable, and compliant with data protection principles.

## Problem Statement

The problem addressed by this project is the development of a web-based credit decision platform that can:

- Collect credit application data from the frontend and partner stores.
- Use bank and financial behavior data to estimate affordability.
- Use consent-based digital footprint data collected by a web scraping module as an additional risk factor.
- Analyze the combined data with an AI-assisted scoring model.
- Decide whether credit should be approved, rejected, or sent for manual review.
- Calculate a possible approved credit amount.
- Provide clear reason codes and an audit trail for each decision.

The system must avoid making opaque decisions that cannot be explained to users, developers, or auditors.

## Main Objective

The main objective is to create a working prototype of a full-stack credit decision web application that uses AI-assisted analysis of store, bank, and digital footprint data to support credit approval and credit limit calculation.

## Specific Objectives

- Design a frontend for customers and partner stores.
- Implement a credit request flow where a user can request financing.
- Integrate backend services through an API Gateway.
- Collect and normalize user, store, bank, and digital footprint data.
- Build a credit analysis module that combines multiple scoring factors.
- Add an AI digital footprint analysis component.
- Generate explainable decisions with reason codes and factor contributions.
- Store every decision for audit and later model evaluation.
- Consider privacy, consent, fairness, and regulatory requirements.

## Research Questions

1. How can store, bank, and digital footprint data be combined to improve credit decision making?
2. Which features are useful for estimating creditworthiness and affordability?
3. How can AI-based scoring remain explainable and auditable?
4. How can user consent and data minimization be implemented in a credit decision application?
5. How can the system calculate not only approval or rejection, but also a possible credit amount?

## Scope Of The Project

The project focuses on a prototype system rather than a production banking platform. It includes:

- A web frontend for credit requests and user dashboard.
- Backend microservices for gateway, user data, web scraping, credit analysis, payments, and repayment schedules.
- A proposed AI module for analyzing the user's digital footprint.
- Database persistence for users, credit requests, decisions, reasons, and scoring factors.
- A simple model evaluation approach using test or simulated data.

The project does not aim to become a legally certified credit institution system. Real production usage would require legal, banking, compliance, and security review.

## Proposed System Architecture

```text
Frontend Web Application
        |
        v
API Gateway
        |
        +--> User Data Service
        |
        +--> Bank Data Adapter
        |
        +--> Web Scraping / Digital Footprint Service
        |
        +--> Credit Analysis Service
        |
        +--> Payment Service
        |
        +--> Repayment Service
```

## Main Components

### Frontend Web Application

The frontend is the main user interface. It allows the user to:

- Start a credit request.
- Review repayment options.
- Give or reject consent for digital footprint collection and analysis.
- Submit the application.
- See the result: approved, rejected, or manual review.
- View active repayment schedules in a dashboard.

Partner stores can use the same frontend or integrate through an SDK/API.

### API Gateway

The Gateway is the public backend entry point. It receives credit requests from the frontend and coordinates calls to internal services. It hides internal service complexity from the frontend and provides a stable API contract.

### User Data Service

The User Data service stores customer profile data such as identity, KYC status, contact information, and historical platform activity. It provides the Credit Analysis service with normalized user features.

### Bank Data Adapter

The Bank Data Adapter represents the integration with bank or open banking data sources. In the prototype, this can use mock or generated data. In a real system, it would retrieve consented financial signals such as:

- Income regularity.
- Account balance trends.
- Expense behavior.
- Existing debt or repayment obligations.
- Cash flow stability.

The adapter should return derived features, not raw bank statements.

### Web Scraping And Digital Footprint Module

The web scraping and digital footprint module collects consent-based public or user-authorized information that can help evaluate the consistency and reliability of the applicant's digital profile. It should collect only permitted data sources and convert raw observations into structured features.

Example digital footprint signals include:

- Email and phone verification consistency.
- Account age and account consistency.
- Public business or professional profile consistency, if relevant and allowed.
- Device/session trust indicators.
- Partner store interaction behavior.
- Order velocity and abnormal behavior patterns.

The AI part of this module analyzes the collected digital footprint features and converts them into a risk score.

The service returns:

- Digital footprint score.
- Risk level.
- Reason codes.
- Model version.
- Feature version.

Sensitive or legally risky data should not be used. The system should avoid raw social media scraping, special category personal data, precise location tracking, and any data source without explicit consent.

### Credit Analysis Service

The Credit Analysis service combines all available scoring factors:

- Store behavior score.
- Platform history score.
- Bank affordability score.
- Web scraping and digital footprint AI score.
- Requested amount risk.

The service produces:

- `APPROVED`, `REJECTED`, or `REVIEW`.
- Credit score.
- Maximum possible credit amount.
- Main reason for the decision.
- Detailed factor contributions for audit.

### Payment And Repayment Services

If credit is approved, the Payment service creates the payment intent and the Repayment service creates the installment schedule. These services complete the financing flow after the credit decision.

## Data Flow

1. The user starts a credit request in the frontend or through a partner store.
2. The frontend displays repayment terms and consent options.
3. The user submits the credit request.
4. The Gateway receives the request.
5. User, store, bank, and digital footprint features are collected.
6. The web scraping and AI module generates an additional digital footprint risk signal.
7. The Credit Analysis service calculates the credit score and possible amount.
8. The decision and explanation are saved in the database.
9. The result is returned to the frontend.
10. If approved, payment and repayment schedule creation continues.

## Decision Model Concept

The first version can use a hybrid scoring approach:

```text
Final Score =
  Store Behavior Score * 0.25 +
  Platform History Score * 0.25 +
  Bank Affordability Score * 0.30 +
  Web Scraping / Digital Footprint AI Score * 0.15 +
  Requested Amount Score * 0.05
```

The weights should be configurable. The system should store the exact weights used for every decision so past decisions remain explainable.

## Possible Credit Amount Calculation

The system should not only approve or reject the user. It should also estimate a safe credit limit.

Example formula:

```text
baseLimit = monthlyDisposableIncome * affordabilityMultiplier
riskAdjustedLimit = baseLimit * finalScore
possibleCreditAmount = min(riskAdjustedLimit, partnerMaximumLimit)
```

The requested amount is approved only if:

```text
requestedAmount <= possibleCreditAmount
```

If the requested amount is too high, the system can reject the request or offer a lower approved amount.

## Explainability

Every decision should include stable reason codes. Examples:

- `LOW_AFFORDABILITY`
- `INSUFFICIENT_INCOME_STABILITY`
- `HIGH_RECENT_ORDER_VELOCITY`
- `GOOD_REPAYMENT_HISTORY`
- `DIGITAL_FOOTPRINT_CONSENT_MISSING`
- `EMAIL_AND_PHONE_VERIFIED`
- `REQUESTED_AMOUNT_TOO_HIGH`

Explainability is important because credit decisions can significantly affect users. The system should make it possible to understand which factors influenced the outcome.

## Privacy And Compliance Considerations

Because the system uses personal data and AI-assisted credit assessment, privacy and compliance are important parts of the project.

Key principles:

- Use explicit consent for bank and digital footprint data.
- Apply data minimization: collect only features needed for the decision.
- Store derived features instead of raw sensitive data where possible.
- Provide audit logs for all credit decisions.
- Keep human review possible for uncertain or disputed decisions.
- Avoid prohibited or sensitive data categories.
- Monitor the model for bias and unfair outcomes.

In the European context, AI systems used to evaluate creditworthiness of natural persons are treated as high-risk under the EU AI Act. GDPR also gives users protections around automated decision-making and profiling. Therefore, the project should include transparency, human oversight, record keeping, and the possibility to contest decisions.

## Methodology

The project can be developed using the following methodology:

1. Requirement analysis.
2. Architecture design.
3. Database and API design.
4. Frontend implementation.
5. Backend microservice implementation.
6. AI scoring prototype.
7. Integration of the scoring model with the credit request flow.
8. Testing with simulated user and transaction data.
9. Evaluation of decision quality, explainability, and system performance.
10. Documentation of limitations and future improvements.

## Technology Stack

Possible stack based on the existing repository:

- Frontend: React, TypeScript, Vite, Tailwind CSS.
- Gateway: Java, Spring Boot.
- User Data, Payment, Repayment: Java, Spring Boot.
- Credit Analysis: Python, FastAPI.
- AI Digital Footprint Service: Python, FastAPI, scikit-learn or another ML library.
- Databases: PostgreSQL.
- Cache and rate limiting: Redis.
- Deployment: Docker and Docker Compose.
- API documentation: OpenAPI.

## Evaluation Plan

The prototype can be evaluated using:

- Functional tests for credit request and credit decision flow.
- Unit tests for scoring functions.
- API integration tests between services.
- Simulated datasets with different user risk profiles.
- Comparison of decisions with and without digital footprint features.
- Explainability checks to ensure every decision has reason codes.
- Basic fairness checks across synthetic user groups.
- Performance tests for response time of the credit request flow.

## Expected Result

The expected result is a working web application prototype where a user can apply for credit, the system analyzes multiple data sources, and the application returns an explainable credit decision with a possible approved amount. The project should demonstrate how AI can support credit decision making while preserving auditability, privacy, and user consent.

## Limitations

- The prototype may use simulated bank and digital footprint data.
- The AI model may be simple and not trained on real production credit data.
- Legal compliance is discussed at a design level but not certified.
- Real bank integrations require regulated open banking providers and strong security controls.
- Real credit deployment requires professional model validation, legal approval, and continuous monitoring.

## Future Improvements

- Integrate real open banking APIs.
- Add human review dashboard.
- Add partner portal for merchants.
- Improve model training with larger datasets.
- Add bias monitoring and model drift detection.
- Add user appeal and decision contest workflow.
- Add production-grade identity verification.

## Suggested Diploma Structure

1. Introduction
2. Background and related work
3. Problem statement
4. Requirements analysis
5. System architecture
6. Data model and API design
7. AI credit decision model
8. Implementation
9. Testing and evaluation
10. Privacy, security, and ethical considerations
11. Conclusion and future work

## References

- Regulation (EU) 2024/1689, Artificial Intelligence Act, especially high-risk AI system requirements and creditworthiness classification: https://eur-lex.europa.eu/eli/reg/2024/1689/oj
- GDPR Article 22, automated individual decision-making and profiling: https://gdpr.eu/article-22-automated-individual-decision-making/
- European Banking Authority Guidelines on loan origination and monitoring: https://www.eba.europa.eu/activities/single-rulebook/regulatory-activities/credit-risk/guidelines-loan-origination-and-monitoring
