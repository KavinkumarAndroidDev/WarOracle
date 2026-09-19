package com.kkdev.waroracle.service;

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
import com.kkdev.waroracle.dto.simulation.SimulationQuality;
import com.kkdev.waroracle.dto.warlog.ClanWarLogItem;
import com.kkdev.waroracle.dto.warlog.ClanWarLogResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PerformanceModelingService
{

	private static final double DEFAULT_ATTACK_PARTICIPATION_RATE = 0.90;
	private static final double PRIOR_WEIGHT = 3.0;

	public WarPerformanceModel buildWarPerformanceModel(
			CurrentWar currentWar,
			ClanWarLogResponse homeClanWarLog,
			ClanWarLogResponse opponentClanWarLog,
			Map<String, List<PlayerBattleLogItem>> playerBattleLogs,
			SimulationQuality quality)
	{
		log.info("Building war performance model for war state: {}", currentWar.getState());

		SimulationQuality simulationQuality = (quality != null) ? quality : SimulationQuality.HIGH;

		ClanPerformanceModel homeClanModel = buildClanPerformanceModel(
				currentWar.getClan(),
				homeClanWarLog,
				playerBattleLogs,
				currentWar.getAttacksPerMember());

		ClanPerformanceModel opponentClanModel = buildClanPerformanceModel(
				currentWar.getOpponent(),
				opponentClanWarLog,
				playerBattleLogs,
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
			int attacksPerMember)
	{
		if (warClan == null)
		{
			return null;
		}

		double participationRate = calculateAttackParticipationRate(clanWarLog);
		double avgStars = calculateAverageStars(clanWarLog);
		double avgDestruction = calculateAverageDestruction(clanWarLog);

		List<PlayerPerformanceModel> playerModels = new ArrayList<>();
		if (warClan.getMembers() != null)
		{
			for (WarMember member : warClan.getMembers())
			{
				List<PlayerBattleLogItem> battles = playerBattleLogs.getOrDefault(member.getTag(), Collections.emptyList());
				playerModels.add(buildPlayerPerformanceModel(member, battles, attacksPerMember));
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
				.players(playerModels)
				.build();
	}

	public PlayerPerformanceModel buildPlayerPerformanceModel(
			WarMember member,
			List<PlayerBattleLogItem> battleLogs,
			int attacksPerMember)
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

		Map<Integer, MatchupStarProbability> matchupProbabilities = calculateMatchupProbabilities(
				member.getTownhallLevel(),
				homeVillageOffense,
				member.getAttacks());

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
			List<WarAttack> currentWarAttacks)
	{
		Map<Integer, List<AttackSample>> samplesByDiff = new HashMap<>();
		for (int diff = -2; diff <= 2; diff++)
		{
			samplesByDiff.put(diff, new ArrayList<>());
		}

		for (PlayerBattleLogItem battle : offensiveBattles)
		{
			if (battle.getOpponentTownHallLevel() != null && battle.getStars() != null)
			{
				int diff = playerTh - battle.getOpponentTownHallLevel();
				int clampedDiff = Math.max(-2, Math.min(2, diff));
				double dest = (battle.getDestructionPercentage() != null) ? battle.getDestructionPercentage() : 50.0;
				samplesByDiff.get(clampedDiff).add(new AttackSample(battle.getStars(), dest));
			}
		}

		Map<Integer, MatchupStarProbability> result = new HashMap<>();

		for (int diff = -2; diff <= 2; diff++)
		{
			List<AttackSample> samples = samplesByDiff.get(diff);
			double[] prior = getPriorStarProbabilities(diff);
			double priorDest = getPriorDestruction(diff);

			int count0 = 0;
			int count1 = 0;
			int count2 = 0;
			int count3 = 0;
			double totalObservedDest = 0.0;

			for (AttackSample sample : samples)
			{
				if (sample.stars == 0) count0++;
				else if (sample.stars == 1) count1++;
				else if (sample.stars == 2) count2++;
				else if (sample.stars >= 3) count3++;
				totalObservedDest += sample.destruction;
			}

			int sampleSize = samples.size();
			double denom = sampleSize + PRIOR_WEIGHT;

			double prob0 = (count0 + PRIOR_WEIGHT * prior[0]) / denom;
			double prob1 = (count1 + PRIOR_WEIGHT * prior[1]) / denom;
			double prob2 = (count2 + PRIOR_WEIGHT * prior[2]) / denom;
			double prob3 = (count3 + PRIOR_WEIGHT * prior[3]) / denom;

			double expectedDest = (totalObservedDest + PRIOR_WEIGHT * priorDest) / denom;

			result.put(diff, MatchupStarProbability.builder()
					.thDifference(diff)
					.prob0Star(prob0)
					.prob1Star(prob1)
					.prob2Star(prob2)
					.prob3Star(prob3)
					.expectedDestruction(expectedDest)
					.sampleCount(sampleSize)
					.build());
		}

		return result;
	}

	private double[] getPriorStarProbabilities(int thDiff)
	{
		if (thDiff >= 2)
		{
			return new double[]{0.01, 0.02, 0.07, 0.90};
		}
		else if (thDiff == 1)
		{
			return new double[]{0.02, 0.05, 0.18, 0.75};
		}
		else if (thDiff == 0)
		{
			return new double[]{0.05, 0.15, 0.50, 0.30};
		}
		else if (thDiff == -1)
		{
			return new double[]{0.10, 0.40, 0.45, 0.05};
		}
		else
		{
			return new double[]{0.25, 0.55, 0.19, 0.01};
		}
	}

	private double getPriorDestruction(int thDiff)
	{
		if (thDiff >= 2) return 99.0;
		if (thDiff == 1) return 95.0;
		if (thDiff == 0) return 85.0;
		if (thDiff == -1) return 68.0;
		return 52.0;
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

	private static class AttackSample
	{
		final int stars;
		final double destruction;

		AttackSample(int stars, double destruction)
		{
			this.stars = stars;
			this.destruction = destruction;
		}
	}
}
