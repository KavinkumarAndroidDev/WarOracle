package com.kkdev.waroracle.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kkdev.waroracle.dto.cwl.CwlLeagueTier;
import com.kkdev.waroracle.dto.cwl.DifficultyModifierDetail;
import com.kkdev.waroracle.service.cwl.DifficultyModifierCalculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DifficultyModifierCalculatorTest
{

	private DifficultyModifierCalculator calculator;

	@BeforeEach
	void setUp()
	{
		calculator = new DifficultyModifierCalculator();
	}

	@Test
	@DisplayName("Should return correct modifier boosts for Titan and Legend tiers")
	void shouldReturnCorrectModifiersForTiers()
	{
		// Titan III -> Legend III modifier (+10% building, +10% hero, +5% guardian, -5% att hero)
		DifficultyModifierDetail titan3 = calculator.getModifierForTier(CwlLeagueTier.TITAN_III);
		assertEquals("Legend III", titan3.getModifierCode());
		assertEquals(0.10, titan3.getDefenseBuildingDpsBoost());
		assertEquals(-0.05, titan3.getAttackingHeroDpsHpPenalty());
		assertEquals(1.10, titan3.getDefenseRatingMultiplier());

		// Titan I -> Legend II modifier (+15% building, +15% hero, +10% guardian, -10% att hero)
		DifficultyModifierDetail titan1 = calculator.getModifierForTier(CwlLeagueTier.TITAN_I);
		assertEquals("Legend II", titan1.getModifierCode());
		assertEquals(0.15, titan1.getDefenseBuildingDpsBoost());
		assertEquals(-0.10, titan1.getAttackingHeroDpsHpPenalty());
		assertEquals(1.15, titan1.getDefenseRatingMultiplier());

		// Legend -> Legend I modifier (+20% building, +20% hero, +20% guardian, -20% att hero)
		DifficultyModifierDetail legend = calculator.getModifierForTier(CwlLeagueTier.LEGEND);
		assertEquals("Legend I", legend.getModifierCode());
		assertEquals(0.20, legend.getDefenseBuildingDpsBoost());
		assertEquals(-0.20, legend.getAttackingHeroDpsHpPenalty());
		assertEquals(1.20, legend.getDefenseRatingMultiplier());

		// Master I -> standard modifier (0)
		DifficultyModifierDetail master = calculator.getModifierForTier(CwlLeagueTier.MASTER_I);
		assertEquals("none", master.getModifierCode());
		assertEquals(0.0, master.getDefenseBuildingDpsBoost());
	}

	@Test
	@DisplayName("Should stack multiplicatively with Rage Spell Tower")
	void shouldStackMultiplicativelyWithRageTower()
	{
		DifficultyModifierDetail legend = calculator.getModifierForTier(CwlLeagueTier.LEGEND);
		// (1 + 0.20) * 1.50 = 1.80
		double totalRageBoost = calculator.computeRageTowerDefenseMultiplier(legend);
		assertEquals(1.80, totalRageBoost, 0.001);
	}

	@Test
	@DisplayName("Should correctly detect fixed damage abilities")
	void shouldDetectFixedDamageAbilities()
	{
		assertTrue(calculator.isFixedDamageAbility("Giant Arrow"));
		assertTrue(calculator.isFixedDamageAbility("seeking shield"));
		assertTrue(calculator.isFixedDamageAbility("Fireball"));
		assertFalse(calculator.isFixedDamageAbility("King Gauntlet"));
		assertFalse(calculator.isFixedDamageAbility("Frozen Arrow"));
	}
}
