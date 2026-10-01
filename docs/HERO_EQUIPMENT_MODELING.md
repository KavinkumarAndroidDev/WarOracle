# Mathematical Specification: Empirical Priors & Hero Equipment Modeling

> Mathematical specification of Bayesian empirical priors, exponential time decay, and Hero Equipment potency calculations implemented in `com.kkdev.waroracle.service.EmpiricalPriorCalibrationService`, `com.kkdev.waroracle.service.HeroEquipmentPowerCalculator`, and `com.kkdev.waroracle.service.PerformanceModelingService`.

---

## 1. Hero & Equipment Power Scaling

Modern *Clash of Clans* attack power is heavily influenced by active Hero levels and equipment gear levels (Common vs. Epic equipment).

### 1.1 Hero Progression Ratio ($\rho_H$)
For active home village heroes $\{h_1, h_2, \dots, h_m\}$:

$$\rho_H = \frac{1}{m} \sum_{i=1}^{m} \frac{\text{Level}(h_i)}{\text{MaxLevel}(h_i)}$$

*(Defaults to $0.80$ if no hero data is available).*

### 1.2 Equipment Progression Ratio ($\rho_E$)
For all equipped gear items $\{e_1, e_2, \dots, e_k\}$ across active heroes:

$$\rho_E = \frac{1}{k} \sum_{j=1}^{k} \frac{\text{Level}(e_j)}{\text{MaxLevel}(e_j)}$$

*(Defaults to $0.80$ if no equipment data is available).*

### 1.3 Offensive Multiplier ($\Omega_{\text{player}}$)
The overall offensive multiplier combines hero and equipment ratios bounded within $[0.60, 1.00]$:

$$\Omega_{\text{player}} = \text{clamp}\left( 0.70 + 0.15 \cdot \rho_H + 0.15 \cdot \rho_E, \, 0.60, \, 1.00 \right)$$

---

## 2. Global Empirical Priors Calibration

Global baseline matchup probabilities for Town Hall differences $\Delta \text{TH} \in \{-2, -1, 0, +1, +2\}$ are continuously calibrated against historical war attacks stored in MySQL and cached in Hazelcast.

### 2.1 Heuristic Baseline Priors ($\mathbf{p}_0$)

$$\mathbf{p}_0(\Delta \text{TH}) = \begin{cases}
[0.01, 0.02, 0.07, 0.90] & \Delta \text{TH} \ge +2 \\
[0.02, 0.05, 0.18, 0.75] & \Delta \text{TH} = +1 \\
[0.05, 0.15, 0.50, 0.30] & \Delta \text{TH} = 0 \\
[0.10, 0.40, 0.45, 0.05] & \Delta \text{TH} = -1 \\
[0.25, 0.55, 0.19, 0.01] & \Delta \text{TH} \le -2
\end{cases}$$

### 2.2 Empirical Sample Weighting ($\beta$)
With total recorded attacks $N_{\text{samples}}$ for a given delta $\Delta \text{TH}$ and confidence threshold $T_{\text{conf}} = 100$:

$$\beta = \min\left( 1.0, \, \frac{N_{\text{samples}}}{T_{\text{conf}}} \right)$$

The recalibrated prior distribution $\mathbf{p}_{\text{prior}}$ blends heuristic priors with empirical frequencies:

$$\mathbf{p}_{\text{prior}}(\Delta \text{TH}) = (1 - \beta) \cdot \mathbf{p}_0(\Delta \text{TH}) + \beta \cdot \mathbf{p}_{\text{empirical}}(\Delta \text{TH})$$

---

## 3. Bayesian Matchup Probability Blending

For an individual player, personal attack history is blended with the global prior using Bayesian weighting with pseudo-count $W_{\text{prior}} = 3.0$:

### 3.1 Exponential Time Decay for Historical War Attacks
Historical attacks lose relevance over time as game balance changes. The decay weight $w(t)$ for an attack recorded $t$ days ago with half-life $t_{1/2} = 30$ days is:

$$w(t) = 2.0 \cdot \left( \frac{1}{2} \right)^{\frac{t}{t_{1/2}}}$$

*(Regular friendly / multiplayer home village battle log attacks receive flat weight $w = 1.0$).*

### 3.2 Posterior Star Probability Vector
Let $W_{\text{total}} = \sum_{k} w_k$ and $W_s$ be the sum of weights for observed attacks resulting in $s \in \{0, 1, 2, 3\}$ stars:

$$P(s \mid \Delta \text{TH}) = \frac{W_s + W_{\text{prior}} \cdot p_{\text{prior}}(s)}{W_{\text{total}} + W_{\text{prior}}}$$

### 3.3 Equipment Multiplier Scaling
The 3-star probability is scaled by the player's offensive multiplier $\Omega_{\text{player}}$ and clan tier boost:

$$P'(3) = P(3) \cdot \Omega_{\text{player}}$$

The difference $\Delta P_3 = P(3) - P'(3)$ is redistributed into 2-star and 1-star outcomes:

$$P'(2) = P(2) + 0.70 \cdot \Delta P_3$$

$$P'(1) = P(1) + 0.30 \cdot \Delta P_3$$

---

## 4. Defender Base Defense Rating ($R_{\text{def}}$)

A defender base's resistance $R_{\text{def}}$ is computed from stars and destruction conceded across defensive battle logs:

$$R_{\text{def}} = \text{clamp}\left( 1.0 + 0.3 \cdot (2.0 - \bar{S}_{\text{conceded}}) + 0.005 \cdot (80.0 - \bar{D}_{\text{conceded}}), \, 0.5, \, 1.5 \right)$$

Higher $R_{\text{def}}$ values reduce the attacker's 3-star probability during the Monte Carlo attack simulation.
