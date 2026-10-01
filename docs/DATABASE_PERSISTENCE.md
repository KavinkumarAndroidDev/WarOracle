# WarOracle Database Persistence & Analytics Guide

## 1. Overview & Strategy

WarOracle uses a **Hybrid Relational + JSON Document** model powered by MySQL 8.0 and Spring Data JPA. This architecture ensures high-performance relational querying for war results and attacks, while utilizing JSON columns to store evolving game structures (heroes, equipment, spells, and troops) without requiring schema migrations on every Supercell game update.

---

## 2. Entity-Relationship Diagram (ERD)

```
┌─────────────────────────────────┐
│              clans              │
├─────────────────────────────────┤
│ PK clan_tag (VARCHAR 15)        │
│    name (VARCHAR 100)           │
│    clan_level (INT)             │
│    updated_at (TIMESTAMP)       │
└───────────────┬─────────────────┘
                │ 1
                │
                │ N
┌───────────────▼─────────────────┐       1:N       ┌─────────────────────────────────┐
│           clan_wars             ├────────────────►│           war_attacks           │
├─────────────────────────────────┤                 ├─────────────────────────────────┤
│ PK war_id (VARCHAR 64)          │                 │ PK attack_id (BIGINT AUTO_INC)  │
│ FK clan_tag (VARCHAR 15)        │                 │ FK war_id (VARCHAR 64)          │
│    opponent_tag (VARCHAR 15)    │                 │    attacker_tag (VARCHAR 15)    │
│    state (VARCHAR 20)           │                 │    attacker_th (INT)            │
│    team_size (INT)              │                 │    defender_tag (VARCHAR 15)    │
│    attacks_per_member (INT)     │                 │    defender_th (INT)            │
│    battle_modifier (VARCHAR 50) │                 │    th_diff (INT)                │
│    preparation_start_time (STR) │                 │    stars (INT)                  │
│    start_time (STR)             │                 │    destruction_percentage (DEC) │
│    end_time (STR)               │                 │    attack_order (INT)           │
│    clan_stars (INT)             │                 │    duration_seconds (INT)       │
│    clan_destruction (DEC)       │                 │    is_clan_attack (BOOLEAN)     │
│    opponent_stars (INT)         │                 │    created_at (TIMESTAMP)       │
│    opponent_destruction (DEC)   │                 └─────────────────────────────────┘
│    result (VARCHAR 10)          │
│    created_at (TIMESTAMP)       │
│    updated_at (TIMESTAMP)       │
└───────────────┬─────────────────┘
                │ 1
                │
                │ N
┌───────────────▼─────────────────┐                 ┌─────────────────────────────────┐
│        simulation_runs          │                 │        player_snapshots         │
├─────────────────────────────────┤                 ├─────────────────────────────────┤
│ PK simulation_id (VARCHAR 36)   │                 │ PK snapshot_id (BIGINT AUTO_INC)│
│ FK war_id (VARCHAR 64)          │                 │    player_tag (VARCHAR 15)      │
│    clan_tag (VARCHAR 15)        │                 │    player_name (VARCHAR 100)    │
│    opponent_tag (VARCHAR 15)    │                 │    town_hall_level (INT)        │
│    quality (VARCHAR 10)         │                 │    town_hall_weapon_level (INT) │
│    iterations (INT)             │                 │    war_stars (INT)              │
│    execution_time_millis (BIGINT│                 │    trophies (INT)               │
│    predicted_win_rate (DECIMAL) │                 │    exp_level (INT)              │
│    predicted_loss_rate (DECIMAL)│                 │    heroes_json (JSON)           │
│    predicted_draw_rate (DECIMAL)│                 │    hero_equipment_json (JSON)   │
│    predicted_home_stars_mean(DEC│                 │    troops_json (JSON)           │
│    predicted_opp_stars_mean(DEC)│                 │    spells_json (JSON)           │
│    score_distribution_json(JSON)│                 │    snapshot_time (TIMESTAMP)    │
│    created_at (TIMESTAMP)       │                 └─────────────────────────────────┘
└─────────────────────────────────┘

┌─────────────────────────────────┐
│          war_feedback           │
├─────────────────────────────────┤
│ PK id (BIGINT AUTO_INC)         │
│    category (VARCHAR 50)        │
│    message (TEXT)               │
│    contact (VARCHAR 100)        │
│    client_ip (VARCHAR 45)       │
│    created_at (TIMESTAMP)       │
└─────────────────────────────────┘
```

---

## 3. Key Concepts & Algorithms

### 3.1 Deterministic War ID
Supercell does not issue persistent global war identifiers. WarOracle generates a deterministic, collision-resistant surrogate key:

```text
WarID = SHA256(HomeClanTag + "_" + OpponentClanTag + "_" + PreparationStartTime)[0..32]
```

This guarantees that repeat simulations on the same war record cleanly update the existing `clan_wars` and `war_attacks` rows without creating duplicates.

### 3.2 Player Snapshot Strategy
* Whenever a user triggers a simulation or views stats, their profile (TH level, Hero levels, Hero Equipment levels, Troops, Spells) is captured in `player_snapshots`.
* This tracks the player's progression curve over time, contextualizing their historical attack performance against their base strength at that exact moment.

### 3.3 War Attack Data Ingestion (Ground Truth)
* Every individual attack in `CurrentWar` (Attacker Tag & TH, Defender Tag & TH, $\Delta \text{TH}$, Stars, Destruction %, Duration) is ingested into `war_attacks`.
* This data feeds the **empirical posterior distribution**, enabling WarOracle to refine prior matchup probabilities with real match data.

### 3.4 User Feedback Telemetry
* Users can submit accuracy feedback, bug reports, and UX ratings stored in `war_feedback` via `/warOracle/feedback`.
