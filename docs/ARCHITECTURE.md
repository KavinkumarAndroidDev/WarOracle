# WarOracle Backend Architecture & System Reference

## 1. Executive Summary & Purpose
**WarOracle** is a Clash of Clans clan war analysis and Monte Carlo predictive modeling platform. It aggregates live Supercell API data (player offensive capabilities, hero/equipment levels, clan war logs, battle logs, and active war rosters) to simulate thousands of war outcomes, computing win/loss/draw probabilities, expected star totals, and 95% confidence intervals.

---

## 2. High-Level System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                 Frontend (React + Vite)                     │
└──────────────────────────────┬──────────────────────────────┘
                               │ REST / JSON (HTTP)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 Spring Boot 4 Application                   │
│                                                             │
│   ┌─────────────────────────────────────────────────────┐   │
│   │ Controller Layer (PlayerController)                 │   │
│   └──────────────────────────┬──────────────────────────┘   │
│                              ▼                              │
│   ┌─────────────────────────────────────────────────────┐   │
│   │ Service Layer                                       │   │
│   │  • WarOracleService (Orchestrator)                  │   │
│   │  • PerformanceModelingService (Bayesian Engine)     │   │
│   │  • MonteCarloSimulationService (50k+ Trial Sim)     │   │
│   │  • WarPersistenceService (Data Ingestion & Store)   │   │
│   │  • PlayerService & ClanService                      │   │
│   └──────────────┬──────────────────────┬───────────────┘   │
│                  │                      │                   │
│                  ▼                      ▼                   │
│   ┌────────────────────────┐  ┌─────────────────────────┐  │
│   │ ClashApiClient         │  │ Repository Layer        │  │
│   │ (Spring RestClient)    │  │ (Spring Data JPA)       │  │
│   └──────────────┬─────────┘  └─────────────┬───────────┘  │
└──────────────────┼──────────────────────────┼───────────────┘
                   │                          │
                   ▼                          ▼
   ┌───────────────────────────┐  ┌───────────────────────────┐
   │ Official Supercell API    │  │ MySQL 8.0 Database        │
   │ (Bearer JWT / Rate-limit) │  │ (Relational + JSON store) │
   └───────────────────────────┘  └───────────────────────────┘
```

---

## 3. Layered Design & Separation of Concerns

### 3.1 Controller Layer (`com.kkdev.waroracle.controller`)
* **`PlayerController`**: Exposes REST endpoints for player preview statistics (`GET /warOracle/{playerTag}`) and war simulation execution (`POST /warOracle/simulate`).
* Validates inputs with Jakarta Bean Validation and returns standard `ApiResponse<T>` envelopes.

### 3.2 Service Layer (`com.kkdev.waroracle.service`)
* **`WarOracleService`**: Main orchestrator. Coordinates player profile retrieval, clan war state resolution, parallel battle log mining, simulation execution, and persistence.
* **`PerformanceModelingService`**: Converts raw game data into statistical matchup matrices using Bayesian prior weighting across Town Hall level differentials (`thDiff`).
* **`MonteCarloSimulationService`**: High-performance multi-threaded trial simulator running 10,000 to 250,000 randomized iterations per war.
* **`WarPersistenceService`**: Transactional data management. Handles player snapshots, clan war logs, individual war attack ingestion, and simulation run audits.
* **`PlayerService` & `ClanService`**: Domain services wrapping external Clash API calls with caching and error translation.

### 3.3 Client Layer (`com.kkdev.waroracle.client`)
* **`ClashApiClient`**: Built on Spring Boot's modern `RestClient`. Manages Bearer JWT injection, base URL routing, and converts HTTP error codes (`404 Not Found`, `403 Private War Log`, `429 Rate Limited`) into domain-specific `ClashApiException` errors.

### 3.4 Repository Layer (`com.kkdev.waroracle.repository`)
* Standard Spring Data JPA interfaces (`ClanRepository`, `ClanWarRepository`, `WarAttackRepository`, `PlayerSnapshotRepository`, `SimulationRunRepository`).

---

## 4. Security & Compliance
1. **Supercell Fan Content Policy**:
   * All API requests comply with the Supercell Fan Content Policy.
   * Prominent disclaimer notices are embedded in both UI and API contracts.
2. **Spring Security**:
   * Configured via `SecurityConfig` and `WebMvcConfig` with CORS enabled for frontend clients and state-agnostic REST endpoints.

---

## 5. Configuration & Environment Reference
* **`clash.api.token`**: Official developer JWT obtained from [developer.clashofclans.com](https://developer.clashofclans.com/).
* **`spring.datasource.url`**: Local MySQL 8 database (`jdbc:mysql://localhost:3306/waroracle`).
* **`spring.jpa.hibernate.ddl-auto=none`**: DDL auto-generation is disabled; all schemas are version-controlled via SQL files in `src/main/resources/sql/`.
