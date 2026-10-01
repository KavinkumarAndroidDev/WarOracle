# Clash of Clans: Clan War Leagues (CWL) Comprehensive Specification & Engine Architecture

> **Document Version:** 1.0  
> **Status:** Reference Specification & Architectural Blueprint  
> **Author:** WarOracle Engineering Team  
> **Purpose:** Permanent reference for Supercell API schemas, tournament math, promotion/demotion rules, difficulty modifiers, and simulation engine adaptations for CWL support.

---

## 1. Overview & Core Differences (CWL vs. Regular Wars)

| Feature | Regular Clan War | Clan War League (CWL) |
|---|---|---|
| **Cadence** | On-demand (continuous) | Once per month (8-day event with 2-day sign-up) |
| **Tournament Format** | 1v1 standalone war | Round-robin group (8 clans = 7 wars, 6 clans = 5 wars) |
| **Attacks Per Member** | **2 attacks** | **Strictly 1 attack per war day** |
| **War Sizes** | 5v5, 10v10, 15v15, 20v20, ..., 50v50 | **15v15** (all tiers) or **30v30** (Master I and below) |
| **Roster Size** | Exact team size | Up to 50 players (substitutions allowed each day) |
| **Matchmaking Basis** | Weight-based (Town Hall, offense, defense) | **Pure League Tier** (no Town Hall or weight balancing) |
| **Win Bonus** | Standard war loot | **+10 bonus stars per war win** towards group standings |
| **Tie-Breaker** | War destruction % | Cumulative league destruction % across all rounds |
| **Difficulty Modifiers** | None | Applied in **Titan III through Legend League** |

---

## 2. Supercell REST API Endpoints & Schemas

### 2.1 Endpoint 1: Retrieve Current Clan War League Group
* **URL:** `GET /clans/{clanTag}/currentwar/leaguegroup`
* **Query Parameters:** None
* **Cache Consideration:** Cache for 5–15 minutes during active league week; invalidate on round transition.

#### Response JSON Schema (`ClanWarLeagueGroup`):
```json
{
  "tag": "#8U08RJUV",
  "state": "inWar", 
  "season": "2026-09",
  "clans": [
    {
      "tag": "#2PP",
      "name": "Elite Warriors",
      "clanLevel": 22,
      "badgeUrls": {
        "small": "https://...",
        "large": "https://...",
        "medium": "https://..."
      },
      "members": [
        {
          "tag": "#P809Y8V",
          "name": "Chief Player",
          "townHallLevel": 16
        }
      ]
    }
  ],
  "rounds": [
    {
      "warTags": [
        "#2Q08VJY8R",
        "#2Q08VJY8S",
        "#2Q08VJY8T",
        "#2Q08VJY8U"
      ]
    },
    {
      "warTags": [
        "#0",
        "#0",
        "#0",
        "#0"
      ]
    }
  ]
}
```

#### Enum State Values:
* `GROUP_NOT_FOUND`: Clan is not registered in CWL or event is not active.
* `NOT_IN_WAR`: Group created but preparation has not started.
* `PREPARATION`: Round 1 preparation day is active.
* `WAR` / `IN_WAR`: Battle days are actively progressing.
* `ENDED`: All rounds concluded.

> [!IMPORTANT]
> In `rounds[i].warTags`, upcoming wars that have not yet had their preparation day created are represented as `"#0"`. The client must check `!warTag.equals("#0")` before calling the individual war endpoint.

---

### 2.2 Endpoint 2: Retrieve Individual CWL War Round
* **URL:** `GET /clanwarleagues/wars/{warTag}`
* **Query Parameters:** None
* **Response:** Standard `CurrentWar` / `ClanWar` structure with `attacksPerMember: 1`.

#### Key Fields in Round War:
```json
{
  "state": "inWar",
  "teamSize": 15,
  "attacksPerMember": 1,
  "battleModifier": "none",
  "preparationStartTime": "20260924T180000.000Z",
  "startTime": "20260925T180000.000Z",
  "endTime": "20260926T180000.000Z",
  "clan": {
    "tag": "#2PP",
    "name": "Elite Warriors",
    "attacks": 14,
    "stars": 42,
    "destructionPercentage": 98.45,
    "members": [ ... ]
  },
  "opponent": {
    "tag": "#9QQ",
    "name": "Nemesis",
    "attacks": 15,
    "stars": 40,
    "destructionPercentage": 94.20,
    "members": [ ... ]
  }
}
```

---

## 3. Tournament Structure & Mechanics

### 3.1 Standard 8-Clan Group Schedule (7 Rounds)
* **Day 1:** Preparation Day for War 1
* **Day 2:** Battle Day for War 1 + Preparation Day for War 2
* **Day 3:** Battle Day for War 2 + Preparation Day for War 3
* **Day 4:** Battle Day for War 3 + Preparation Day for War 4
* **Day 5:** Battle Day for War 4 + Preparation Day for War 5
* **Day 6:** Battle Day for War 5 + Preparation Day for War 6
* **Day 7:** Battle Day for War 6 + Preparation Day for War 7
* **Day 8:** Battle Day for War 7 (Final Round)

### 3.2 The 6-Clan Edge Case & February Exception
* **Matchmaking Fallback:** If remaining registered clans in a specific tier cannot form a group of 8, Supercell compresses remaining clans into **groups of 6**.
* **February Shortening:** During February (28 days), Supercell forces 6-clan groups for all participants to fit the season schedule.
* **Rules Shift in 6-Clan Group:**
  1. Duration drops to 6 days total with **5 war days** (5 opponents).
  2. **No Demotion Rule:** Positions 7 and 8 do not exist. Therefore, **demotion is completely disabled** for 6-clan groups. Top 2 promote; bottom 4 remain safe.
  3. Medal thresholds are proportionally scaled.

---

## 4. Promotion, Demotion & Medal Matrix

### 4.1 League Tier Table (8-Clan Standard)

| League Tier | Format Options | Promoted Clans (↑) | Demoted Clans (↓) | Unchanged (—) | 1st Place Medals | Bonus Medal Size |
|---|---|---|---|---|---|---|
| **Bronze III** | 15v15, 30v30 | Top 3 (1st–3rd) | None (0) | 4th–8th | 46 | 42 |
| **Bronze II** | 15v15, 30v30 | Top 3 (1st–3rd) | Bottom 1 (8th) | 4th–7th | 58 | 45 |
| **Bronze I** | 15v15, 30v30 | Top 3 (1st–3rd) | Bottom 1 (8th) | 4th–7th | 70 | 48 |
| **Silver III** | 15v15, 30v30 | Top 2 (1st–2nd) | Bottom 1 (8th) | 3rd–7th | 88 | 51 |
| **Silver II** | 15v15, 30v30 | Top 2 (1st–2nd) | Bottom 2 (7th–8th) | 3rd–6th | 106 | 54 |
| **Silver I** | 15v15, 30v30 | Top 2 (1st–2nd) | Bottom 2 (7th–8th) | 3rd–6th | 124 | 57 |
| **Gold III** | 15v15, 30v30 | Top 2 (1st–2nd) | Bottom 2 (7th–8th) | 3rd–6th | 148 | 60 |
| **Gold II** | 15v15, 30v30 | Top 2 (1st–2nd) | Bottom 2 (7th–8th) | 3rd–6th | 172 | 63 |
| **Gold I** | 15v15, 30v30 | Top 2 (1st–2nd) | Bottom 2 (7th–8th) | 3rd–6th | 196 | 66 |
| **Crystal III** | 15v15, 30v30 | Top 2 (1st–2nd) | Bottom 2 (7th–8th) | 3rd–6th | 220 | 69 |
| **Crystal II** | 15v15, 30v30 | Top 2 (1st–2nd) | Bottom 2 (7th–8th) | 3rd–6th | 244 | 72 |
| **Crystal I** | 15v15, 30v30 | Top 2 (1st–2nd) | Bottom 2 (7th–8th) | 3rd–6th | 274 | 75 |
| **Master III** | 15v15, 30v30 | Top 2 (1st–2nd) | Bottom 2 (7th–8th) | 3rd–6th | 304 | 78 |
| **Master II** | 15v15, 30v30 | Top 2 (1st–2nd) | Bottom 2 (7th–8th) | 3rd–6th | 334 | 81 |
| **Master I** | 15v15, 30v30 | Top 1 (1st) | Bottom 2 (7th–8th) | 2nd–6th | 364 | 84 |
| **Champion III** | **15v15 only** | Top 1 (1st) | Bottom 2 (7th–8th) | 2nd–6th | 388 | 87 |
| **Champion II** | **15v15 only** | Top 1 (1st) | Bottom 2 (7th–8th) | 2nd–6th | 412 | 90 |
| **Champion I** | **15v15 only** | Top 1 (1st) | Bottom 2 (7th–8th) | 2nd–6th | 436 | 93 |
| **Titan III** | **15v15 only** | Top 1 (1st) | Bottom 2 (7th–8th) | 2nd–6th | 454 | 96 |
| **Titan II** | **15v15 only** | Top 1 (1st) | Bottom 2 (7th–8th) | 2nd–6th | 472 | 99 |
| **Titan I** | **15v15 only** | Top 1 (1st) | Bottom 2 (7th–8th) | 2nd–6th | 490 | 102 |
| **Legend** | **15v15 only** | None (Top tier) | Bottom 2 (7th–8th) | 1st–6th | 508 | 105 |

### 4.2 Individual Medal Yield by Stars Scored

$$\text{Medal Percentage} = \min\left(100, \, 20 + (\text{Stars Scored} \times 10)\right)\%$$

* **0 stars** $\rightarrow 20\%$ of clan medal yield
* **1 star** $\rightarrow 30\%$ of clan medal yield
* **2 stars** $\rightarrow 40\%$ of clan medal yield
* **3 stars** $\rightarrow 50\%$ of clan medal yield
* **4 stars** $\rightarrow 60\%$ of clan medal yield
* **5 stars** $\rightarrow 70\%$ of clan medal yield
* **6 stars** $\rightarrow 80\%$ of clan medal yield
* **7 stars** $\rightarrow 90\%$ of clan medal yield
* **$\ge 8$ stars** $\rightarrow 100\%$ of clan medal yield (Full yield)

---

## 5. Difficulty Modifiers (Titan III to Legend League)

In high tiers, Supercell applies server-side combat scaling modifiers that directly alter base stats:

| League | Modifier Code | Defense Building DPS | Defending Hero DPS/HP | Defending Guardian DPS/HP | Attacking Hero DPS/HP |
|---|---|---|---|---|---|
| **Titan III** | `Legend III` | $+10\%$ | $+10\%$ | $+5\%$ | $-5\%$ |
| **Titan II** | `Legend III` | $+10\%$ | $+10\%$ | $+5\%$ | $-5\%$ |
| **Titan I** | `Legend II` | $+15\%$ | $+15\%$ | $+10\%$ | $-10\%$ |
| **Legend** | `Legend I` | $+20\%$ | $+20\%$ | $+20\%$ | $-20\%$ |

### Engine Calculation Rules for Difficulty Modifiers:
1. **Multiplicative Stacking:** Defending boosts stack multiplicatively with Rage Spell Towers ($1.0 \times 1.20 \times 1.50 = 1.80\times$).
2. **Hero Equipment Passive Stats:** Attacking hero penalties apply directly to passive hero equipment stats (DPS & HP), lowering offensive multipliers in `HeroEquipmentPowerCalculator`.
3. **Fixed Damage Exemption:** Abilities that deal fixed lump-sum damage (e.g. *Giant Arrow*, *Seeking Shield*) do NOT receive penalties.
4. **Defense Rating Adjustment:** In Titan/Legend tiers, the defense baseline must be multiplied by $1.10\text{--}1.20\times$ in `PerformanceModelingService.calculateDefenseRating()`.

---

## 6. WarOracle Engine Architecture & Implementation Plan for CWL

### 6.1 Simulation Differences (Single-Attack Dynamic)
In regular wars, the engine uses **2 waves** (Primary attack + Coordinated clean-up).  
In CWL:
1. `attacksPerMember = 1`
2. **No clean-up wave occurs.** If player #1 scores a 1-star on enemy #1, that base remains at 1 star unless another attacker hits it instead of their own mirror.
3. Target selection strategy: High TH players prioritize securing a guaranteed 2★/3★ on high bases, while lower THs hit for 2★ safe stars.
4. **Win Bonus (+10 Stars):** The simulator must compute:
   $$\text{Clan Total Stars} = \sum (\text{Attack Stars}) + (10 \times \text{Simulated War Wins})$$

### 6.2 Full 7-Day Season Simulation Model
When simulating CWL, WarOracle should support two simulation levels:
1. **Single Day War Simulation:** Simulate today's ongoing round against today's opponent (`/clanwarleagues/wars/{warTag}`).
2. **Full Season Tournament Projection:** Simulate all 7 rounds across all 8 clans using a Monte Carlo Tournament matrix:
   * Simulates all $8 \times 7 / 2 = 28$ individual matches in the group.
   * Ranks all 8 clans by $(\text{Stars} + 10\times\text{Wins})$, then cumulative destruction.
   * Outputs: **Probability of 1st Place**, **Probability of Promotion (Top 2)**, **Probability of Demotion (Bottom 2)**, and **Expected League Medals**.

---

## 7. Future DTO & Client Additions Roadmap

```
com.kkdev.waroracle
├── client
│   └── ClashApiClient.java (add getLeagueGroup, getLeagueWar)
├── dto
│   └── cwl
│       ├── ClanWarLeagueGroupResponse.java
│       ├── ClanWarLeagueClanDto.java
│       ├── ClanWarLeagueMemberDto.java
│       ├── ClanWarLeagueRoundDto.java
│       ├── CwlSeasonSimulationResult.java
│       └── CwlClanStandingsPrediction.java
└── service
    ├── CwlService.java (orchestration & round-robin matrix)
    └── CwlTournamentSimulationService.java (7-day Monte Carlo simulator)
```

---
*End of Specification. Saved for future CWL feature rollout.*
