# 🧾 `user-data` Microservice

The `user-data` service is a Spring Boot-based microservice responsible for managing user profile information used in the Rocket Credit (CreDIT) checkout process.

---

## 📌 Responsibilities

- Exposes a `GET /users/{id}` API to retrieve user data.
- Stores user records in a PostgreSQL database.
- Provides integration with other microservices via `USER_DATA_URL`.

---

## 🧱 Stack

- **Language:** Java 8
- **Framework:** Spring Boot 1.6.14
- **Database:** PostgreSQL 15 (via Docker)
- **Build Tool:** Maven
- **OpenAPI Generator:** Used to scaffold API models and interfaces.

---

## 🔧 Running Locally

Ensure Docker and Docker Compose are installed.

```bash
cd rocket-credit-deployment
docker compose up -d userdb user-data
```

Access:

- API: http://localhost:8081/users/1
- DB: `psql -h localhost -p 5433 -U postgres -d userdb`

---

## 📂 Project Structure

```
src/
 ├─ main/java/com/rocketcredit/userdata
 │   ├─ api/               # Generated OpenAPI interfaces and controllers
 │   ├─ model/             # DTOs
 │   ├─ repo/              # Spring Data repositories
 │   └─ UserDataApplication.java
```

---

## 🧪 Status

- ✅ Service compiles and builds successfully.
- ✅ Docker container runs and exposes port 8081.
- ✅ Connected to `userdb` PostgreSQL container.
- ❗ Basic endpoints implemented and working.
- 🚧 Additional endpoints and validations are under development.

---

## 📤 Next Steps

- [ ] Add integration test coverage.
- [X] Change structure of the db (credit score, age, transactions and etc)
- [X] Add create/update/delete endpoints.
- [ ] Integrate with `gateway` microservice end-to-end.
- [ ] Deploy to AWS (RDS for DB + ECS or EC2).
