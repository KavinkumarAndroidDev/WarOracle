package com.kkdev.waroracle.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.kkdev.waroracle.dto.battlelog.PlayerBattleLogItem;
import com.kkdev.waroracle.dto.clan.CurrentWar;
import com.kkdev.waroracle.dto.clan.WarAttack;
import com.kkdev.waroracle.dto.clan.WarClan;
import com.kkdev.waroracle.dto.clan.WarMember;
import com.kkdev.waroracle.dto.model.ClanPerformanceModel;
import com.kkdev.waroracle.dto.model.MatchupStarProbability;
import com.kkdev.waroracle.dto.model.PlayerPerformanceModel;
import com.kkdev.waroracle.dto.model.WarPerformanceModel;
import com.kkdev.waroracle.dto.player.Player;
import com.kkdev.waroracle.dto.simulation.SimulationQuality;
import com.kkdev.waroracle.dto.warlog.ClanWarLogItem;
import com.kkdev.waroracle.dto.warlog.ClanWarLogResponse;
import com.kkdev.waroracle.entity.WarAttackEntity;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PerformanceModelingService
{

	private static final double DEFAULT_ATTACK_PARTICIPATION_RATE = 0.90;
	private static final double PRIOR_WEIGHT = 3.0;
	private static final double HALF_LIFE_DAYS = 30.0;

	private final EmpiricalPriorCalibrationService empiricalPriorCalibrationService;
	private final HeroEquipmentPowerCalculator heroEquipmentPowerCalculator;

	public PerformanceModelingService(
			EmpiricalPriorCalibrationService empiricalPriorCalibrationService,
			HeroEquipmentPowerCalculator heroEquipmentPowerCalculator)
	{
		this.empiricalPriorCalibrationService = empiricalPriorCalibrationService;
		this.heroEquipmentPowerCalculator = heroEquipmentPowerCalculator;
	}

	public WarPerformanceModel buildWarPerformanceModel(
			CurrentWar currentWar,
			ClanWarLogResponse homeClanWarLog,
			ClanWarLogResponse opponentClanWarLog,
			Map<String, List<PlayerBattleLogItem>> playerBattleLogs,
			Map<String, List<WarAttackEntity>> playerWarAttacks,
			Map<String, Player> livePlayerProfiles,
			SimulationQuality quality)
	{
		log.info("Building hybrid war performance model for war state: {}", currentWar.getState());

		SimulationQuality simulationQuality = (quality != null) ? quality : SimulationQuality.HIGH;

		ClanPerformanceModel homeClanModel = buildClanPerformanceModel(
				currentWar.getClan(),
				homeClanWarLog,
				playerBattleLogs,
				playerWarAttacks,
				livePlayerProfiles,
				currentWar.getAttacksPerMember());

		ClanPerformanceModel opponentClanModel = buildClanPerformanceModel(
				currentWar.getOpponent(),
				opponentClanWarLog,
				playerBattleLogs,
				playerWarAttacks,
				livePlayerProfiles,
				currentWar.getAttacksPerMember());

		return WarPerformanceModel.builder()
				.warState(currentWar.getState())
				.teamSize(currentWar.getTeamSize())
				.attacksPerMember(currentWar.getAttacksPerMember())
				.homeClan(homeClanModel)
				.opponentClan(opponentClanModel)
				.simulationQuality(simulationQuality)
				.iterations(simulationQuality.getIterations())
				.build();
	}

	public ClanPerformanceModel buildClanPerformanceModel(
			WarClan warClan,
			ClanWarLogResponse clanWarLog,
			Map<String, List<PlayerBattleLogItem>> playerBattleLogs,
			Map<String, List<WarAttackEntity>> playerWarAttacks,
			Map<String, Player> livePlayerProfiles,
			int attacksPerMember)
	{
		if (warClan == null)
		{
			return null;
		}

		double participationRate = calculateAttackParticipationRate(clanWarLog);
		double avgStars = calculateAverageStars(clanWarLog);
		double avgDestruction = calculateAverageDestruction(clanWarLog);
		int consecutiveWins = calculateConsecutiveWins(clanWarLog);

		double clanTierMultiplier = 1.0;
		if (avgDestruction >= 98.0)
		{
			clanTierMultiplier = 1.40;
		}
		else if (avgDestruction >= 95.0)
		{
			clanTierMultiplier = 1.25;
		}
		else if (avgDestruction >= 90.0)
		{
			clanTierMultiplier = 1.10;
		}

		double winStreakBoost = (Math.min(consecutiveWins, 20) / 20.0) * 0.15;
		double finalClanTierMultiplier = Math.min(1.65, clanTierMultiplier + winStreakBoost);

		List<PlayerPerformanceModel> playerModels = new ArrayList<>();
		if (warClan.getMembers() != null)
		{
			for (WarMember member : warClan.getMembers())
			{
				List<PlayerBattleLogItem> battles = playerBattleLogs != null
						? playerBattleLogs.getOrDefault(member.getTag(), Collections.emptyList())
						: Collections.emptyList();
				List<WarAttackEntity> dbAttacks = playerWarAttacks != null
						? playerWarAttacks.getOrDefault(member.getTag(), Collections.emptyList())
						: Collections.emptyList();
				Player liveProfile = livePlayerProfiles != null ? livePlayerProfiles.get(member.getTag()) : null;

				playerModels.add(buildPlayerPerformanceModel(member, battles, dbAttacks, liveProfile, attacksPerMember, finalClanTierMultiplier));
			}
		}

		return ClanPerformanceModel.builder()
				.clanTag(warClan.getTag())
				.name(warClan.getName())
				.clanLevel(warClan.getClanLevel())
				.totalMembersInWar(warClan.getMembers() != null ? warClan.getMembers().size() : 0)
				.attackParticipationRate(participationRate)
				.avgStarsPerWar(avgStars)
				.avgDestructionPerWar(avgDestruction)
				.clanTierMultiplier(finalClanTierMultiplier)
				.estimatedConsecutiveWins(consecutiveWins)
				.players(playerModels)
				.build();
	}

	public PlayerPerformanceModel buildPlayerPerformanceModel(
			WarMember member,
			List<PlayerBattleLogItem> battleLogs,
			List<WarAttackEntity> dbWarAttacks,
			Player liveProfile,
			int attacksPerMember)
	{
		return buildPlayerPerformanceModel(member, battleLogs, dbWarAttacks, liveProfile, attacksPerMember, 1.0);
	}

	public PlayerPerformanceModel buildPlayerPerformanceModel(
			WarMember member,
			List<PlayerBattleLogItem> battleLogs,
			List<WarAttackEntity> dbWarAttacks,
			Player liveProfile,
			int attacksPerMember,
			double clanTierMultiplier)
	{
		int attacksMade = (member.getAttacks() != null) ? member.getAttacks().size() : 0;
		int attacksRemaining = Math.max(0, attacksPerMember - attacksMade);

		List<PlayerBattleLogItem> homeVillageOffense = new ArrayList<>();
		List<PlayerBattleLogItem> homeVillageDefense = new ArrayList<>();

		if (battleLogs != null)
		{
			for (PlayerBattleLogItem item : battleLogs)
			{
				if (item.getBattleType() != null && "homeVillage".equalsIgnoreCase(item.getBattleType().trim()))
				{
					if (Boolean.TRUE.equals(item.getAttack()))
					{
						homeVillageOffense.add(item);
					}
					else
					{
						homeVillageDefense.add(item);
					}
				}
			}
		}

		double offensiveMultiplier = heroEquipmentPowerCalculator.calculateOffensiveMultiplier(liveProfile);
		offensiveMultiplier = Math.min(1.65, offensiveMultiplier * clanTierMultiplier);

		Map<Integer, MatchupStarProbability> matchupProbabilities = calculateMatchupProbabilities(
				member.getTownhallLevel(),
				homeVillageOffense,
				dbWarAttacks,
				member.getAttacks(),
				offensiveMultiplier);

		double avgStarsConceded = 0.0;
		double avgDestConceded = 0.0;
		int defenseCount = homeVillageDefense.size();

		if (defenseCount > 0)
		{
			double totalStars = 0.0;
			double totalDest = 0.0;
			for (PlayerBattleLogItem defense : homeVillageDefense)
			{
				totalStars += (defense.getStars() != null) ? defense.getStars() : 0;
				totalDest += (defense.getDestructionPercentage() != null) ? defense.getDestructionPercentage() : 0.0;
			}
			avgStarsConceded = totalStars / defenseCount;
			avgDestConceded = totalDest / defenseCount;
		}
		else if (member.getBestOpponentAttack() != null)
		{
			avgStarsConceded = member.getBestOpponentAttack().getStars();
			avgDestConceded = member.getBestOpponentAttack().getDestructionPercentage();
		}
		else
		{
			avgStarsConceded = 2.0;
			avgDestConceded = 75.0;
		}

		int currentBestStars = 0;
		double currentBestDest = 0.0;
		if (member.getBestOpponentAttack() != null)
		{
			currentBestStars = member.getBestOpponentAttack().getStars();
			currentBestDest = member.getBestOpponentAttack().getDestructionPercentage();
		}

		double defenseRating = calculateDefenseRating(avgStarsConceded, avgDestConceded);

		return PlayerPerformanceModel.builder()
				.playerTag(member.getTag())
				.name(member.getName())
				.townHallLevel(member.getTownhallLevel())
				.mapPosition(member.getMapPosition())
				.attacksMade(attacksMade)
				.attacksRemaining(attacksRemaining)
				.currentBestOpponentStars(currentBestStars)
				.currentBestOpponentDestruction(currentBestDest)
				.matchupProbabilities(matchupProbabilities)
				.defenseRating(defenseRating)
				.avgStarsConceded(avgStarsConceded)
				.avgDestructionConceded(avgDestConceded)
				.build();
	}

	private Map<Integer, MatchupStarProbability> calculateMatchupProbabilities(
			int playerTh,
			List<PlayerBattleLogItem> offensiveBattles,
			List<WarAttackEntity> dbWarAttacks,
			List<WarAttack> currentWarAttacks,
			double offensiveMultiplier)
	{
		Map<Integer, List<WeightedAttackSample>> samplesByDiff = new HashMap<>();
		for (int diff = -2; diff <= 2; diff++)
		{
			samplesByDiff.put(diff, new ArrayList<>());
		}

		LocalDateTime now = LocalDateTime.now();

		if (offensiveBattles != null)
		{
			for (PlayerBattleLogItem battle : offensiveBattles)
			{
				if (battle.getOpponentTownHallLevel() != null && battle.getStars() != null)
				{
					int diff = playerTh - battle.getOpponentTownHallLevel();
					int clampedDiff = Math.max(-2, Math.min(2, diff));
					double dest = (battle.getDestructionPercentage() != null) ? battle.getDestructionPercentage() : 50.0;
					double sampleWeight = 1.0;
					samplesByDiff.get(clampedDiff).add(new WeightedAttackSample(battle.getStars(), dest, sampleWeight));
				}
			}
		}

		if (dbWarAttacks != null)
		{
			for (WarAttackEntity attack : dbWarAttacks)
			{
				if (attack.getAttackerTh() != null && attack.getAttackerTh() == playerTh && attack.getDefenderTh() != null)
				{
					int diff = playerTh - attack.getDefenderTh();
					int clampedDiff = Math.max(-2, Math.min(2, diff));
					double dest = attack.getDestructionPercentage() != null ? attack.getDestructionPercentage().doubleValue() : 50.0;

					double daysElapsed = 0.0;
					if (attack.getCreatedAt() != null)
					{
						daysElapsed = (double) Duration.between(attack.getCreatedAt(), now).toHours() / 24.0;
					}
					double decayWeight = Math.pow(0.5, Math.max(0.0, daysElapsed) / HALF_LIFE_DAYS);
					double warAttackWeight = 2.0 * decayWeight;

					samplesByDiff.get(clampedDiff).add(new WeightedAttackSample(attack.getStars(), dest, warAttackWeight));
				}
			}
		}

		Map<Integer, MatchupStarProbability> result = new HashMap<>();

		for (int diff = -2; diff <= 2; diff++)
		{
			List<WeightedAttackSample> samples = samplesByDiff.get(diff);
			double[] prior = empiricalPriorCalibrationService.getPriorStarProbabilities(diff);
			double priorDest = empiricalPriorCalibrationService.getPriorDestruction(diff);

			double weightedCount0 = 0.0;
			double weightedCount1 = 0.0;
			double weightedCount2 = 0.0;
			double weightedCount3 = 0.0;
			double totalObservedDest = 0.0;
			double totalWeight = 0.0;

			for (WeightedAttackSample sample : samples)
			{
				if (sample.stars == 0) weightedCount0 += sample.weight;
				else if (sample.stars == 1) weightedCount1 += sample.weight;
				else if (sample.stars == 2) weightedCount2 += sample.weight;
				else if (sample.stars >= 3) weightedCount3 += sample.weight;

				totalObservedDest += (sample.destruction * sample.weight);
				totalWeight += sample.weight;
			}

			double denom = totalWeight + PRIOR_WEIGHT;

			double prob0 = (weightedCount0 + PRIOR_WEIGHT * prior[0]) / denom;
			double prob1 = (weightedCount1 + PRIOR_WEIGHT * prior[1]) / denom;
			double prob2 = (weightedCount2 + PRIOR_WEIGHT * prior[2]) / denom;
			double prob3 = (weightedCount3 + PRIOR_WEIGHT * prior[3]) / denom;

			double expectedDest = (totalObservedDest + PRIOR_WEIGHT * priorDest) / denom;

			double adjustedProb3 = prob3 * offensiveMultiplier;
			double diffProb3 = prob3 - adjustedProb3;
			double adjustedProb2 = prob2 + (diffProb3 * 0.70);
			double adjustedProb1 = prob1 + (diffProb3 * 0.30);
			double adjustedDest = expectedDest * (0.85 + (0.15 * offensiveMultiplier));

			result.put(diff, MatchupStarProbability.builder()
					.thDifference(diff)
					.prob0Star(prob0)
					.prob1Star(adjustedProb1)
					.prob2Star(adjustedProb2)
					.prob3Star(adjustedProb3)
					.expectedDestruction(Math.min(100.0, adjustedDest))
					.sampleCount((int) Math.round(totalWeight))
					.build());
		}

		return result;
	}

	private double calculateDefenseRating(double avgStarsConceded, double avgDestConceded)
	{
		double baseStars = 2.0;
		double starFactor = (baseStars - avgStarsConceded) * 0.3;
		double destFactor = (80.0 - avgDestConceded) * 0.005;
		double rating = 1.0 + starFactor + destFactor;
		return Math.max(0.5, Math.min(1.5, rating));
	}

	private double calculateAttackParticipationRate(ClanWarLogResponse warLog)
	{
		if (warLog == null || warLog.getItems() == null || warLog.getItems().isEmpty())
		{
			return DEFAULT_ATTACK_PARTICIPATION_RATE;
		}

		int totalPossibleAttacks = 0;
		int totalActualAttacks = 0;

		for (ClanWarLogItem item : warLog.getItems())
		{
			if (item.getAttacksPerMember() == 2 && item.getTeamSize() > 0 && item.getClan() != null)
			{
				int maxAttacks = item.getTeamSize() * item.getAttacksPerMember();
				int actual = (item.getClan().getAttacks() != null) ? item.getClan().getAttacks() : 0;
				totalPossibleAttacks += maxAttacks;
				totalActualAttacks += Math.min(actual, maxAttacks);
			}
		}

		if (totalPossibleAttacks == 0)
		{
			return DEFAULT_ATTACK_PARTICIPATION_RATE;
		}

		return (double) totalActualAttacks / totalPossibleAttacks;
	}

	private double calculateAverageStars(ClanWarLogResponse warLog)
	{
		if (warLog == null || warLog.getItems() == null || warLog.getItems().isEmpty())
		{
			return 0.0;
		}

		double totalStars = 0.0;
		int warCount = 0;

		for (ClanWarLogItem item : warLog.getItems())
		{
			if (item.getAttacksPerMember() == 2 && item.getClan() != null && item.getClan().getStars() != null)
			{
				totalStars += item.getClan().getStars();
				warCount++;
			}
		}

		return (warCount > 0) ? (totalStars / warCount) : 0.0;
	}

	private double calculateAverageDestruction(ClanWarLogResponse warLog)
	{
		if (warLog == null || warLog.getItems() == null || warLog.getItems().isEmpty())
		{
			return 0.0;
		}

		double totalDest = 0.0;
		int warCount = 0;

		for (ClanWarLogItem item : warLog.getItems())
		{
			if (item.getAttacksPerMember() == 2 && item.getClan() != null && item.getClan().getDestructionPercentage() != null)
			{
				totalDest += item.getClan().getDestructionPercentage();
				warCount++;
			}
		}

		return (warCount > 0) ? (totalDest / warCount) : 0.0;
	}

	private int calculateConsecutiveWins(ClanWarLogResponse warLog)
	{
		if (warLog == null || warLog.getItems() == null || warLog.getItems().isEmpty())
		{
			return 0;
		}

		int streak = 0;
		for (ClanWarLogItem item : warLog.getItems())
		{
			if ("win".equalsIgnoreCase(item.getResult()))
			{
				streak++;
			}
			else
			{
				break;
			}
		}
		return streak;
	}

	private static class WeightedAttackSample
	{
		final int stars;
		final double destruction;
		final double weight;

		WeightedAttackSample(int stars, double destruction, double weight)
		{
			this.stars = stars;
			this.destruction = destruction;
			this.weight = weight;
		}
	}
}
