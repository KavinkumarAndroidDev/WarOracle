# WarOracle Backend Architecture & System Reference

## 1. Executive Summary & Purpose
**WarOracle** is an advanced Clash of Clans clan war predictive modeling and tactical intelligence platform. It fuses live Supercell API data (current player offensive capabilities, hero/equipment levels, clan war logs, battle logs, and active war rosters) with historical database telemetry to simulate thousands of war outcomes and calculate optimal player target assignments.

---

## 2. High-Level System Architecture

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                 PRESENTATION LAYER                                     │
│                             (React 18 + Vite + Tailwind)                               │
└───────────────────────────────────────────┬────────────────────────────────────────────┘
                                            │ HTTP / JSON REST APIs
                                            ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                APPLICATION LAYER                                       │
│                                                                                        │
│   ┌────────────────────────────────────────────────────────────────────────────────┐   │
│   │ Controller Layer:                                                              │   │
│   │  • PlayerController (Player/Clan Stats, Modeling, Simulation)                  │   │
│   │  • WarStrategyController (Kuhn-Munkres Optimal War Plan Generation)             │   │
│   │  • FeedbackController (User Feedback & Accuracy Telemetry)                     │   │
│   └───────────────────────────────────────┬────────────────────────────────────────┘   │
│                                           │                                            │
│   ┌───────────────────────────────────────▼────────────────────────────────────────┐   │
│   │ Orchestration & Domain Services:                                               │   │
│   │  • WarOracleService (Workflow Orchestrator)                                    │   │
│   │  • WarStrategyServiceImpl (Strategic War Planner & Contingency Generator)      │   │
│   │  • KuhnBipartiteOptimizer (Hungarian Maximum-Weight Bipartite Solver)          │   │
│   │  • PerformanceModelingService (Bayesian & Hero Equipment Feature Extractor)    │   │
│   │  • MonteCarloSimulationService (Isolated ForkJoinPool Trial Engine)            │   │
│   │  • HeroEquipmentPowerCalculator (Offensive Scaling Factor)                     │   │
│   │  • EmpiricalPriorCalibrationService (Dynamic Bayesian Priors)                  │   │
│   │  • ClashApiCacheService (Hazelcast Cache Gateway)                              │   │
│   │  • WarPersistenceService (Data Ingestion & Store)                              │   │
│   │  • FeedbackServiceImpl (Feedback Store)                                        │   │
│   └───────────────┬───────────────────────┬────────────────────────┬───────────────┘   │
└───────────────────┼───────────────────────┼────────────────────────┼───────────────────┘
                    │                       │                        │
                    ▼                       ▼                        ▼
┌───────────────────────────────┐ ┌───────────────────┐ ┌────────────────────────────────┐
│   LIVE EXTERNAL INGESTION     │ │  HAZELCAST GRID   │ │   MYSQL 8 DATA WAREHOUSE       │
│                               │ │ (In-Memory Cache) │ │                                │
│ • Official Supercell API      │ │                   │ │ • player_snapshots (Progression)│
│ • Live Player Profile & Gear  │ │ • API Response TTL│ │ • clan_wars (Historical War Log│
│ • Live Battle Logs (Last ~25) │ │ • Global Priors   │ │ • war_attacks (Real War Telemetry│
│ • Live Current War Roster     │ │ • Active Sessions │ │ • simulation_runs (Audit Trail)│
│                               │ │                   │ │ • war_feedback (User Feedback) │
└───────────────────────────────┘ └───────────────────┘ └────────────────────────────────┘
```

---

## 3. Layered Design & Separation of Concerns

### 3.1 Controller Layer (`com.kkdev.waroracle.controller`)
* **`PlayerController`**: Exposes REST endpoints for player preview statistics (`GET /warOracle/{playerTag}`), clan preview statistics (`GET /warOracle/clan/{clanTag}`), empirical performance modeling (`POST /warOracle/performance-model`), quality options (`GET /warOracle/qualities`), and war simulation execution (`POST /warOracle/simulate`).
* **`WarStrategyController`**: Exposes strategy generation endpoints (`POST /warOracle/strategy/plan`) implementing Kuhn-Munkres matching under `SAFE`, `BALANCED`, and `AGGRESSIVE` doctrines.
* **`FeedbackController`**: Handles user feedback, comments, and bug reporting (`POST /warOracle/feedback`).
* Validates inputs with Jakarta Bean Validation and returns standard `ApiResponse<T>` envelopes with distributed trace IDs via `LogTagInterceptor`.

### 3.2 Service Layer (`com.kkdev.waroracle.service`)
* **`WarOracleService`**: Main orchestrator. Coordinates player profile retrieval, clan war state resolution, parallel battle log mining via `CompletableFuture`, simulation execution, and persistence.
* **`WarStrategyServiceImpl` & `KuhnBipartiteOptimizer`**: Solves optimal 1-to-1 attacker assignments in $O(N^3)$ and builds second-wave contingency cleanup plans.
* **`PerformanceModelingService`**: Converts live player stats, hero/equipment levels, live battle logs, and historical war attacks into a unified statistical matchup matrix.
* **`HeroEquipmentPowerCalculator`**: Computes hero and equipment power ratios to calculate offensive power multipliers $\Omega_{\text{player}} \in [0.60, 1.00]$.
* **`EmpiricalPriorCalibrationService`**: Periodically aggregates global war attack outcomes from MySQL and caches empirical probability distributions in Hazelcast.
* **`ClashApiCacheService`**: Manages Hazelcast distributed caching for Supercell API responses (3-minute sliding TTL for player/clan/logs, 1-minute TTL for active wars).
* **`MonteCarloSimulationService`**: High-performance multi-threaded trial simulator running 5,000 to 100,000 randomized iterations per war on an isolated `ForkJoinPool`.
* **`WarPersistenceService`**: Transactional data management. Handles player snapshots, clan war logs, individual war attack ingestion, and simulation run audits.

### 3.3 Client Layer (`com.kkdev.waroracle.service`)
* **`ClashApiCacheService`**: Built on Spring Boot's modern `RestClient` with Hazelcast cache-aside integration and multi-token rotation fallback.

### 3.4 Repository Layer (`com.kkdev.waroracle.repository`)
* Standard Spring Data JPA interfaces (`ClanRepository`, `ClanWarRepository`, `WarAttackRepository`, `PlayerSnapshotRepository`, `SimulationRunRepository`, `FeedbackRepository`).

---

## 4. Configuration & External Environment Variables

WarOracle supports twelve-factor external configuration via environment variables:

| Environment Variable | Default Value / Fallback | Description |
|---|---|---|
| `CLASH_API_TOKENS` | `${CLASH_API_TOKEN}` | Comma-separated list of Supercell Developer API JWT tokens for load balancing & failover |
| `CLASH_API_TOKEN` | (Embedded Dev Token) | Single Supercell Developer API JWT token |
| `DB_URL` | `jdbc:mysql://localhost:3306/waroracle?...` | JDBC connection URL for MySQL 8 |
| `DB_USERNAME` | `root` | Database username |
| `DB_PASSWORD` | `root` | Database password |
| `HAZELCAST_CLUSTER_NAME` | `dev` | Standalone Hazelcast cluster identifier |
| `HAZELCAST_ADDRESSES` | `127.0.0.1:5701` | Standalone Hazelcast cluster member IP/port |
| `CLASH_API_CACHE_TTL_MINUTES` | `3` | Slide TTL for player/clan profile and war log caches |
| `CLASH_API_CACHE_WAR_TTL_MINUTES` | `1` | Anti-stale TTL for active war status |
