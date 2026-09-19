# Frontend Integration Guide: War Prediction & Simulation API

This document provides frontend engineers with everything needed to integrate the WarOracle War Simulation and Prediction Engine (`/warOracle/simulate`).

---

## 1. Overview & User Journey

The WarOracle application provides a two-step experience:

```
[ Step 1: Preview ]                     [ Step 2: Prediction / Simulation ]
User enters #PlayerTag                   User clicks "Simulate War" button
      │                                                │
      ▼                                                ▼
GET /warOracle/{playerTag}               POST /warOracle/simulate
• Player stats                           • Monte Carlo simulation (50k trials)
• Clan metadata                          • Win / Loss / Draw probabilities
• Current war overview                   • Score distributions & 95% CI
                                         • Historical performance model
```

---

## 2. API Specifications

### Endpoint
* **Path**: `/warOracle/simulate`
* **Method**: `POST`
* **Content-Type**: `application/json`

### Request Payload
```json
{
  "clanTag": "#2J2UQYRJ8",
  "quality": "MEDIUM"
}
```

| Field | Type | Required | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `clanTag` | String | **Yes** | — | Clan tag (e.g. `"#2J2UQYRJ8"`). |
| `quality` | String (Enum) | No | `"MEDIUM"` | Monte Carlo sample size. Options: `LOW` (10k runs), `MEDIUM` (50k runs), `HIGH` (100k runs), `MAX` (250k runs). |

---

## 3. Response Scenarios & JSON Payloads

All responses follow the unified `ApiResponse` envelope:

```typescript
interface ApiResponse<T> {
  status: "SUCCESS" | "FAILURE";
  errorcode: number | null;
  internalErrorMessage: string | null;
  errorField: Record<string, string> | null;
  responseObject: T;
  traceId: string;
}
```

---

### Scenario A: Active War (`inWar` or `preparation`)
* **When**: The clan is currently preparing for or fighting an active war.
* **In-Progress War Mechanics (`inWar`)**:
  * **Preserves Real Completed Attacks**: Bases that have already been attacked in reality retain their actual real-world best stars and destruction percentage.
  * **Remaining Attacks Only**: The simulator only simulates attacks for members who have remaining attacks (`attacksRemaining > 0`). Players who already used both attacks in reality do not attack again.
  * **Cleanup Focus**: Simulated remaining attacks will prioritize improving bases that are not yet 3-starred.
* **Backend Action**: Mines participant logs, computes Bayesian matchup probabilities, and executes 50,000+ Monte Carlo iterations over remaining attacks.

#### JSON Response:
```json
{
  "status": "SUCCESS",
  "errorcode": null,
  "internalErrorMessage": null,
  "errorField": null,
  "responseObject": {
    "warState": "inWar",
    "message": "Simulation completed successfully.",
    "winProbability": 0.785,
    "lossProbability": 0.198,
    "drawProbability": 0.017,
    "iterationsRun": 50000,
    "executionTimeMillis": 68,
    "homeClanPrediction": {
      "clanTag": "#2J2UQYRJ8",
      "clanName": "PATEL Empire",
      "expectedStars": 83.42,
      "expectedDestructionPercentage": 96.75,
      "starDistribution": {
        "meanStars": 83.42,
        "medianStars": 84.0,
        "minStars": 76,
        "maxStars": 90,
        "standardDeviation": 2.14,
        "starFrequency": {
          "80": 3420,
          "81": 6890,
          "82": 11450,
          "83": 14200,
          "84": 9800,
          "85": 3500,
          "86": 740
        }
      },
      "starConfidenceInterval95": {
        "lowerBound": 79.0,
        "upperBound": 87.0,
        "confidenceLevel": 0.95
      },
      "perfectWarProbability": 0.024,
      "projectedWinRate": 0.785
    },
    "opponentClanPrediction": {
      "clanTag": "#2Q8880YYG",
      "clanName": "ROYAL KOHALPUR",
      "expectedStars": 76.15,
      "expectedDestructionPercentage": 90.12,
      "starDistribution": {
        "meanStars": 76.15,
        "medianStars": 76.0,
        "minStars": 68,
        "maxStars": 84,
        "standardDeviation": 2.85,
        "starFrequency": { ... }
      },
      "starConfidenceInterval95": {
        "lowerBound": 71.0,
        "upperBound": 81.0,
        "confidenceLevel": 0.95
      },
      "perfectWarProbability": 0.0,
      "projectedWinRate": 0.198
    },
    "currentWar": {
      "state": "inWar",
      "teamSize": 30,
      "attacksPerMember": 2,
      "clan": { ... },
      "opponent": { ... }
    },
    "performanceModel": { ... }
  },
  "traceId": "SIMULATE_WAR_1789398012345"
}
```

---

### Scenario B: War Already Concluded (`warEnded`)
* **When**: The war has already ended.
* **Backend Action**: **Short-circuits immediately** without running simulations or log mining. Returns the concluded war state and final scoreboard.

#### JSON Response:
```json
{
  "status": "SUCCESS",
  "errorcode": null,
  "internalErrorMessage": null,
  "errorField": null,
  "responseObject": {
    "warState": "warEnded",
    "message": "War has already ended. Simulation is only applicable for active or upcoming wars. Start next war to run simulation.",
    "currentWar": {
      "state": "warEnded",
      "teamSize": 30,
      "attacksPerMember": 2,
      "startTime": "20260913T085523.000Z",
      "endTime": "20260914T085523.000Z",
      "clan": {
        "tag": "#2J2UQYRJ8",
        "name": "PATEL Empire",
        "stars": 90,
        "destructionPercentage": 100.0,
        "attacks": 46
      },
      "opponent": {
        "tag": "#2Q8880YYG",
        "name": "ROYAL KOHALPUR",
        "stars": 74,
        "destructionPercentage": 89.8,
        "attacks": 36
      }
    },
    "homeClanPrediction": null,
    "opponentClanPrediction": null,
    "winProbability": 0.0,
    "lossProbability": 0.0,
    "drawProbability": 0.0,
    "iterationsRun": 0,
    "executionTimeMillis": 0,
    "performanceModel": null
  },
  "traceId": "SIMULATE_WAR_1789398012345"
}
```

---

### Scenario C: Not In War (`notInWar`)
* **When**: Clan is not participating in any war.

```json
{
  "status": "SUCCESS",
  "responseObject": {
    "warState": "notInWar",
    "message": "Clan is not currently in an active or upcoming war.",
    "currentWar": null
  },
  "traceId": "SIMULATE_WAR_1789398012345"
}
```

---

## 4. Recommended Frontend UI Components

### 1. Header / Status Guard
* If `responseObject.warState === "warEnded"`:
  * Render a notice banner: **"War Concluded — Final Score: 90 vs 74"**.
  * Render a call-to-action button: **"Start Next War in Clash of Clans to Predict Again"**.
  * Show the final war summary card instead of simulation gauges.
* If `responseObject.warState === "notInWar"`:
  * Render an empty state: **"Clan is not currently in war"**.

### 2. Win Probability Banner (Active Wars)
* Render a prominent tri-color comparison bar / donut chart:
  * **Win %**: `(responseObject.winProbability * 100).toFixed(1)%` (Green)
  * **Draw %**: `(responseObject.drawProbability * 100).toFixed(1)%` (Grey/Yellow)
  * **Loss %**: `(responseObject.lossProbability * 100).toFixed(1)%` (Red)

### 3. Head-to-Head Comparison Card
* Two-column cards comparing **Home Clan** vs **Opponent Clan**:
  * **Expected Stars**: `homeClanPrediction.expectedStars` vs `opponentClanPrediction.expectedStars`
  * **95% Confidence Interval**: e.g., `79.0 — 87.0 Stars (95% CI)`
  * **Expected Destruction**: `homeClanPrediction.expectedDestructionPercentage.toFixed(1)%`
  * **Perfect War Chance**: `(homeClanPrediction.perfectWarProbability * 100).toFixed(2)%`

### 4. Star Distribution Histogram Chart
* Render a Bar / Area chart using `starDistribution.starFrequency`:
  * **X-Axis**: Star count (e.g. 76, 77, 78 ... 90)
  * **Y-Axis**: Frequency / Probability density
  * Shade the region between `lowerBound` and `upperBound` as the 95% confidence interval.

### 5. Meta Details / Execution Footer
* Display engine metadata:
  * *"Simulated over 50,000 trials in 68ms"*
  * Trace ID for debugging: `traceId`
