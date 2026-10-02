package com.kkdev.waroracle.dto.cwl;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DifficultyModifierDetail
{
	private String modifierCode; // "Legend III", "Legend II", "Legend I"
	private double defenseBuildingDpsBoost; // e.g., 0.10 for +10%
	private double defendingHeroDpsHpBoost; // e.g., 0.10 for +10%
	private double defendingGuardianDpsHpBoost; // e.g., 0.05 for +5%
	private double attackingHeroDpsHpPenalty; // e.g., -0.05 for -5%
	private double defenseRatingMultiplier; // e.g., 1.10
}
