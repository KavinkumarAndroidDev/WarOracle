# WarOracle Backend Architecture & System Reference

## 1. Executive Summary & Purpose
**WarOracle** is an advanced Clash of Clans clan war predictive modeling platform. It fuses live Supercell API data (current player offensive capabilities, hero/equipment levels, clan war logs, battle logs, and active war rosters) with historical database telemetry to simulate thousands of war outcomes, computing win/loss/draw probabilities, expected star totals, and 95% confidence intervals.

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
│   │ Controller Layer: PlayerController                                             │   │
│   └───────────────────────────────────────┬────────────────────────────────────────┘   │
│                                           │                                            │
│   ┌───────────────────────────────────────▼────────────────────────────────────────┐   │
│   │ Orchestration & Domain Services:                                               │   │
│   │  • WarOracleService (Workflow Orchestrator)                                    │   │
│   │  • PerformanceModelingService (Bayesian & Hero Equipment Feature Extractor)    │   │
│   │  • MonteCarloSimulationService (Multi-Threaded Trial Engine)                   │   │
│   │  • HeroEquipmentPowerCalculator (Offensive Scaling Factor)                     │   │
│   │  • EmpiricalPriorCalibrationService (Dynamic Bayesian Priors)                  │   │
│   │  • ClashApiCacheService (Hazelcast Cache Gateway)                              │   │
│   │  • WarPersistenceService (Data Ingestion & Store)                              │   │
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
└───────────────────────────────┘ └───────────────────┘ └────────────────────────────────┘
```

---

## 3. Layered Design & Separation of Concerns

### 3.1 Controller Layer (`com.kkdev.waroracle.controller`)
* **`PlayerController`**: Exposes REST endpoints for player preview statistics (`GET /warOracle/{playerTag}`), clan preview statistics (`GET /warOracle/clan/{clanTag}`), and war simulation execution (`POST /warOracle/simulate`).
* Validates inputs with Jakarta Bean Validation and returns standard `ApiResponse<T>` envelopes.

### 3.2 Service Layer (`com.kkdev.waroracle.service`)
* **`WarOracleService`**: Main orchestrator. Coordinates player profile retrieval, clan war state resolution, parallel battle log mining, simulation execution, and persistence.
* **`PerformanceModelingService`**: Converts live player stats, hero/equipment levels, live battle logs, and historical war attacks into a unified statistical matchup matrix.
* **`HeroEquipmentPowerCalculator`**: Computes $R_{\text{hero}}$ and $R_{\text{gear}}$ to calculate offensive power multipliers $M_{\text{offense}} \in [0.60, 1.00]$.
* **`EmpiricalPriorCalibrationService`**: Periodically aggregates global war attack outcomes from MySQL and caches empirical probability distributions in Hazelcast.
* **`ClashApiCacheService`**: Manages Hazelcast distributed caching for Supercell API responses (3-minute sliding TTL for player/clan/logs, 1-minute TTL for active wars).
* **`MonteCarloSimulationService`**: High-performance multi-threaded trial simulator running 10,000 to 250,000 randomized iterations per war.
* **`WarPersistenceService`**: Transactional data management. Handles player snapshots, clan war logs, individual war attack ingestion, and simulation run audits.

### 3.3 Client Layer (`com.kkdev.waroracle.client`)
* **`ClashApiTokenProvider`**: High-availability token manager. Supports multiple API keys via `CLASH_API_TOKENS` (comma/semicolon/newline separated), balances requests across healthy tokens in round-robin fashion, and applies automatic 30-second cooldown isolation when any token encounters HTTP 429 Rate Limits.
* **`ClashApiClient`**: Built on Spring Boot's modern `RestClient` with Hazelcast cache-aside integration. Dynamically injects rotated Bearer JWT credentials, triggers 429 failover telemetry, and maps Supercell response codes into structured domain exceptions.

### 3.4 Repository Layer (`com.kkdev.waroracle.repository`)
* Standard Spring Data JPA interfaces (`ClanRepository`, `ClanWarRepository`, `WarAttackRepository`, `PlayerSnapshotRepository`, `SimulationRunRepository`).

---

## 4. Configuration & External Environment Variables

WarOracle supports full twelve-factor external configuration via environment variables:

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
