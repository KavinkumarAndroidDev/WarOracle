package com.kkdev.waroracle.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.kkdev.waroracle.dto.player.Hero;
import com.kkdev.waroracle.dto.player.HeroEquipment;
import com.kkdev.waroracle.dto.player.Player;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class HeroEquipmentPowerCalculator
{

	private static final double DEFAULT_OFFENSIVE_MULTIPLIER = 0.85;

	public double calculateOffensiveMultiplier(Player player)
	{
		if (player == null)
		{
			return DEFAULT_OFFENSIVE_MULTIPLIER;
		}

		double heroRatio = calculateHeroRatio(player.getHeroes());
		double gearRatio = calculateGearRatio(player.getHeroes(), player.getHeroEquipment());

		double multiplier = 0.70 + (0.15 * heroRatio) + (0.15 * gearRatio);
		return Math.max(0.60, Math.min(1.00, multiplier));
	}

	public double calculateHeroRatio(List<Hero> heroes)
	{
		if (heroes == null || heroes.isEmpty())
		{
			return 0.80;
		}

		double totalRatio = 0.0;
		int count = 0;

		for (Hero hero : heroes)
		{
			if (hero.getVillage() == null || "home".equalsIgnoreCase(hero.getVillage()))
			{
				if (hero.getMaxLevel() != null && hero.getMaxLevel() > 0 && hero.getLevel() != null)
				{
					totalRatio += (double) hero.getLevel() / hero.getMaxLevel();
					count++;
				}
			}
		}

		return count > 0 ? (totalRatio / count) : 0.80;
	}

	public double calculateGearRatio(List<Hero> heroes, List<HeroEquipment> standaloneEquipment)
	{
		double totalRatio = 0.0;
		int count = 0;

		if (heroes != null)
		{
			for (Hero hero : heroes)
			{
				if (hero.getEquipment() != null)
				{
					for (HeroEquipment eq : hero.getEquipment())
					{
						if (eq.getMaxLevel() != null && eq.getMaxLevel() > 0 && eq.getLevel() != null)
						{
							totalRatio += (double) eq.getLevel() / eq.getMaxLevel();
							count++;
						}
					}
				}
			}
		}

		if (count == 0 && standaloneEquipment != null && !standaloneEquipment.isEmpty())
		{
			for (HeroEquipment eq : standaloneEquipment)
			{
				if (eq.getMaxLevel() != null && eq.getMaxLevel() > 0 && eq.getLevel() != null)
				{
					totalRatio += (double) eq.getLevel() / eq.getMaxLevel();
					count++;
				}
			}
		}

		return count > 0 ? (totalRatio / count) : 0.80;
	}
}
