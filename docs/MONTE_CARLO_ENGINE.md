# WarOracle Monte Carlo Predictive Modeling Engine

## 1. Mathematical & Statistical Foundations

The WarOracle predictive engine estimates the outcome of a Clan War using **Monte Carlo simulation calibrated with Bayesian Dirichlet-Multinomial priors**.

```
                           Raw Player & War Telemetry
                                       │
                                       ▼
                   ┌───────────────────────────────────────┐
                   │  Bayesian Matchup Probability Matrix  │
                   │    (TH Differential + Prior Weight)   │
                   └───────────────────┬───────────────────┘
                                       │
                                       ▼
                   ┌───────────────────────────────────────┐
                   │    Player Defense Rating Modifiers    │
                   │    (Avg Stars Conceded & Dest %)      │
                   └───────────────────┬───────────────────┘
                                       │
                                       ▼
       ┌───────────────────────────────────────────────────────────────┐
       │             Monte Carlo Iteration Loop (N Trials)             │
       │                                                               │
       │  1. In-progress war attack retention (real-world completed)   │
       │  2. Simulated attacks for remaining members                   │
       │  3. Greedy cleanup attack allocation on un-cleared bases      │
       │  4. Star sum & tie-breaker destruction calculation            │
       └───────────────────────────────┬───────────────────────────────┘
                                       │
                                       ▼
                        Win / Loss / Draw Probability
                         95% Confidence Interval (CI)
```

---

## 2. Bayesian Prior Weighting & Matchup Matrix

For any matchup between an attacker with Town Hall $TH_{\text{attacker}}$ and a defender with Town Hall $TH_{\text{defender}}$, the Town Hall difference is defined as:

$$\Delta TH = \text{clamp}(TH_{\text{attacker}} - TH_{\text{defender}}, -2, +2)$$

### 2.1 Default Prior Distributions

When a player has zero or few observed attacks at a specific $\Delta TH$, the engine applies empirical baseline priors:

| $\Delta TH$ | Heuristic Prior $(P_0, P_1, P_2, P_3)$ | Expected Destruction % |
| :---: | :---: | :---: |
| **$+2$** (Attacking 2 THs down) | `[0.01, 0.02, 0.07, 0.90]` | 99.0% |
| **$+1$** (Attacking 1 TH down) | `[0.02, 0.05, 0.18, 0.75]` | 95.0% |
| **$0$** (Mirror Town Hall) | `[0.05, 0.15, 0.50, 0.30]` | 85.0% |
| **$-1$** (Attacking 1 TH up) | `[0.10, 0.40, 0.45, 0.05]` | 68.0% |
| **$-2$** (Attacking 2 THs up) | `[0.25, 0.55, 0.19, 0.01]` | 52.0% |

### 2.2 Bayesian Posterior Updating

Given $N_{\text{obs}}$ observed attacks with star counts $c_0, c_1, c_2, c_3$, and prior weight $W_{\text{prior}} = 3.0$:

$$P(k\text{ stars} \mid \Delta TH) = \frac{c_k + W_{\text{prior}} \cdot P_k^{\text{prior}}}{N_{\text{obs}} + W_{\text{prior}}} \quad \text{for } k \in \{0, 1, 2, 3\}$$

Expected destruction percentage is updated analogously:

$$E[\text{Destruction}] = \frac{\sum \text{observed destruction} + W_{\text{prior}} \cdot \text{Dest}^{\text{prior}}}{N_{\text{obs}} + W_{\text{prior}}}$$

---

## 3. Defense Rating Calculation

Each defender base has an inherent defensive strength rating $R_{\text{def}} \in [0.5, 1.5]$ computed from their historical defense logs and current best conceded attack:

$$R_{\text{def}} = 1.0 + (2.0 - \text{AvgStarsConceded}) \times 0.3 + (80.0 - \text{AvgDestConceded}) \times 0.005$$

* $R_{\text{def}} > 1.0$: Strong anti-3-star base layout. Reduces attacker's 3-star probability.
* $R_{\text{def}} < 1.0$: Weak or rushed base. Increases attacker's 3-star probability.

---

## 4. In-Progress War Simulation Mechanics

When simulating an active war (`inWar`):
1. **Preserve Completed Attacks**:
   * Bases already attacked in reality retain their real best stars ($S_{\text{real}}$) and best destruction ($D_{\text{real}}$).
2. **Remaining Attacks Only**:
   * If a player has used $k$ attacks out of $A_{\text{max}}$ (typically 2), only $A_{\text{max}} - k$ attacks are simulated.
3. **Smart Cleanup Targeting**:
   * Remaining attackers target opponent bases in descending map position, prioritizing un-starred bases or 2-starred bases with room for improvement ($3 - S_{\text{current}} > 0$).
4. **Non-Regressive Score Rule**:
   * A new attack on base $B$ only updates the team score if $S_{\text{sim}} > S_{\text{best}}$ or ($S_{\text{sim}} = S_{\text{best}}$ and $D_{\text{sim}} > D_{\text{best}}$).

---

## 5. Statistical Aggregations & Confidence Intervals

Over $N$ Monte Carlo iterations (e.g., 50,000 runs):
* **Win Probability**: $P_{\text{win}} = \frac{N_{\text{home\_wins}}}{N}$
* **Loss Probability**: $P_{\text{loss}} = \frac{N_{\text{opp\_wins}}}{N}$
* **Draw Probability**: $P_{\text{draw}} = \frac{N_{\text{ties}}}{N}$
* **95% Confidence Interval**:
  * Sorted simulated star distribution $[S_{(1)}, S_{(2)}, \dots, S_{(N)}]$.
  * $\text{Lower Bound} = S_{(\lfloor 0.025 \cdot N \rfloor)}$
  * $\text{Upper Bound} = S_{(\lceil 0.975 \cdot N \rceil)}$
