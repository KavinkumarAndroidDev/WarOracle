package com.kkdev.waroracle.service.cwl;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.kkdev.waroracle.dto.cwl.CwlLeagueTier;
import com.kkdev.waroracle.dto.cwl.DifficultyModifierDetail;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DifficultyModifierCalculator
{

	private static final Set<String> FIXED_DAMAGE_ABILITIES = Set.of(
			"giant arrow",
			"seeking shield",
			"fireball",
			"spiky ball"
	);

	public DifficultyModifierDetail getModifierForTier(CwlLeagueTier tier)
	{
		if (tier == null)
		{
			return createStandardDetail();
		}

		return switch (tier)
		{
			case TITAN_III, TITAN_II -> DifficultyModifierDetail.builder()
					.modifierCode("Legend III")
					.defenseBuildingDpsBoost(0.10)
					.defendingHeroDpsHpBoost(0.10)
					.defendingGuardianDpsHpBoost(0.05)
					.attackingHeroDpsHpPenalty(-0.05)
					.defenseRatingMultiplier(1.10)
					.build();

			case TITAN_I -> DifficultyModifierDetail.builder()
					.modifierCode("Legend II")
					.defenseBuildingDpsBoost(0.15)
					.defendingHeroDpsHpBoost(0.15)
					.defendingGuardianDpsHpBoost(0.10)
					.attackingHeroDpsHpPenalty(-0.10)
					.defenseRatingMultiplier(1.15)
					.build();

			case LEGEND -> DifficultyModifierDetail.builder()
					.modifierCode("Legend I")
					.defenseBuildingDpsBoost(0.20)
					.defendingHeroDpsHpBoost(0.20)
					.defendingGuardianDpsHpBoost(0.20)
					.attackingHeroDpsHpPenalty(-0.20)
					.defenseRatingMultiplier(1.20)
					.build();

			default -> createStandardDetail();
		};
	}

	public DifficultyModifierDetail getModifierByCode(String code)
	{
		if (code == null || code.trim().isEmpty() || "none".equalsIgnoreCase(code))
		{
			return createStandardDetail();
		}

		String clean = code.trim().toLowerCase();
		if (clean.contains("iii") || clean.contains("3"))
		{
			return getModifierForTier(CwlLeagueTier.TITAN_III);
		}
		if (clean.contains("ii") || clean.contains("2"))
		{
			return getModifierForTier(CwlLeagueTier.TITAN_I);
		}
		if (clean.contains("i") || clean.contains("1") || clean.contains("legend"))
		{
			return getModifierForTier(CwlLeagueTier.LEGEND);
		}

		return createStandardDetail();
	}

	public double computeRageTowerDefenseMultiplier(DifficultyModifierDetail modifier)
	{
		double buildingMultiplier = 1.0 + (modifier != null ? modifier.getDefenseBuildingDpsBoost() : 0.0);
		// Multiplicative stacking with Rage Spell Tower (+50% / 1.50x boost)
		return buildingMultiplier * 1.50;
	}

	public double applyAttackingHeroModifier(double baseMultiplier, DifficultyModifierDetail modifier)
	{
		if (modifier == null || modifier.getAttackingHeroDpsHpPenalty() == 0.0)
		{
			return baseMultiplier;
		}
		double penalty = modifier.getAttackingHeroDpsHpPenalty(); // e.g., -0.10
		return Math.max(0.40, baseMultiplier * (1.0 + penalty));
	}

	public boolean isFixedDamageAbility(String abilityOrEquipmentName)
	{
		if (abilityOrEquipmentName == null)
		{
			return false;
		}
		return FIXED_DAMAGE_ABILITIES.contains(abilityOrEquipmentName.trim().toLowerCase());
	}

	private DifficultyModifierDetail createStandardDetail()
	{
		return DifficultyModifierDetail.builder()
				.modifierCode("none")
				.defenseBuildingDpsBoost(0.0)
				.defendingHeroDpsHpBoost(0.0)
				.defendingGuardianDpsHpBoost(0.0)
				.attackingHeroDpsHpPenalty(0.0)
				.defenseRatingMultiplier(1.0)
				.build();
	}
}
