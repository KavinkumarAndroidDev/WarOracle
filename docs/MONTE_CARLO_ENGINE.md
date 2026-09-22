# WarOracle Monte Carlo Predictive Modeling Engine

## 1. Mathematical & Statistical Foundations

The WarOracle predictive engine estimates the outcome of a Clan War using **Monte Carlo simulation calibrated with Bayesian Dirichlet-Multinomial priors, Dynamic Hero & Equipment Scaling, and Hybrid Evidence Fusion**.

```
                           Raw Player & War Telemetry
                                       │
                                       ▼
                   ┌───────────────────────────────────────┐
                   │    Hero & Equipment Power Scorer      │
                   │      (R_hero, R_gear -> M_offense)    │
                   └───────────────────┬───────────────────┘
                                       │
                                       ▼
                   ┌───────────────────────────────────────┐
                   │   Hybrid Evidence Fusion Engine       │
                   │   • Live Battle Logs (Weight = 1.0)   │
                   │   • Stored War Attacks (Weight = 2.0) │
                   │   • Exponential Time Decay (30d T1/2) │
                   │   • Town Hall Version-Gated Filter    │
                   └───────────────────┬───────────────────┘
                                       │
                                       ▼
                   ┌───────────────────────────────────────┐
                   │    Dynamic Empirical Global Priors    │
                   │     (Hazelcast-Cached Aggregation)    │
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

## 2. Hero & Equipment Skill Modeling

Hero levels and active Hero Equipment significantly impact a player's ability to convert attacks into 3 stars:

### 2.1 Hero Power Ratio ($R_{\text{hero}}$)
$$R_{\text{hero}} = \frac{1}{|H|} \sum_{h \in H} \frac{\text{level}(h)}{\text{maxLevel}(h)}$$

### 2.2 Equipment Power Ratio ($R_{\text{gear}}$)
$$R_{\text{gear}} = \frac{1}{|E|} \sum_{e \in E} \frac{\text{level}(e)}{\text{maxLevel}(e)}$$

### 2.3 Offensive Strength Multiplier ($M_{\text{offense}}$)
$$M_{\text{offense}} = 0.70 + 0.15 \cdot R_{\text{hero}} + 0.15 \cdot R_{\text{gear}} \in [0.60, 1.00]$$

---

## 3. Hybrid Bayesian Evidence Fusion

For every player attacking at Town Hall differential $\Delta TH = \text{clamp}(TH_{\text{attacker}} - TH_{\text{defender}}, -2, +2)$:

### 3.1 Time-Decay Weighting
For an attack occurring $D$ days ago:
$$w_i = W_{\text{base}} \cdot (0.5)^{\frac{D}{30.0}}$$
* **Live Battle Logs**: $W_{\text{base}} = 1.0$ (multiplayer practice form).
* **Database Clan War Attacks**: $W_{\text{base}} = 2.0$ (authenticated competitive war attacks, filtered by $\text{attacker\_th} == \text{current\_th}$).

### 3.2 Dynamic Empirical Global Priors
Maintained in Hazelcast RAM from aggregated MySQL telemetry:
$$P(k\text{ stars} \mid \Delta TH) = \frac{\sum w_i \cdot \mathbb{I}(\text{stars}=k) + W_{\text{prior}} \cdot P_{\text{empirical}}(k \mid \Delta TH)}{\sum w_i + W_{\text{prior}}}$$

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
