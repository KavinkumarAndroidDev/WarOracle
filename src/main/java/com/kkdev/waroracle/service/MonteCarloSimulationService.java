package com.kkdev.waroracle.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

import com.kkdev.waroracle.dto.model.ClanPerformanceModel;
import com.kkdev.waroracle.dto.model.MatchupStarProbability;
import com.kkdev.waroracle.dto.model.PlayerPerformanceModel;
import com.kkdev.waroracle.dto.model.WarPerformanceModel;
import com.kkdev.waroracle.dto.simulation.ClanWarPrediction;
import com.kkdev.waroracle.dto.simulation.ConfidenceInterval;
import com.kkdev.waroracle.dto.simulation.ScoreDistribution;
import com.kkdev.waroracle.dto.simulation.SimulationResult;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MonteCarloSimulationService
{

	public SimulationResult simulate(WarPerformanceModel model)
	{
		long startTime = System.currentTimeMillis();
		int iterations = (model.getIterations() > 0) ? model.getIterations() : 50000;
		int teamSize = (model.getTeamSize() > 0) ? model.getTeamSize() : 30;
		int attacksPerMember = (model.getAttacksPerMember() > 0) ? model.getAttacksPerMember() : 2;

		log.info("Starting Monte Carlo simulation for {} vs {} with {} iterations",
				model.getHomeClan() != null ? model.getHomeClan().getName() : "Home",
				model.getOpponentClan() != null ? model.getOpponentClan().getName() : "Opponent",
				iterations);

		ClanPerformanceModel homeClan = model.getHomeClan();
		ClanPerformanceModel opponentClan = model.getOpponentClan();

		List<PlayerPerformanceModel> homePlayers = (homeClan != null && homeClan.getPlayers() != null)
				? homeClan.getPlayers() : Collections.emptyList();
		List<PlayerPerformanceModel> opponentPlayers = (opponentClan != null && opponentClan.getPlayers() != null)
				? opponentClan.getPlayers() : Collections.emptyList();

		int effectiveTeamSize = Math.max(teamSize, Math.max(homePlayers.size(), opponentPlayers.size()));

		int[] homeStars = new int[iterations];
		double[] homeDestruction = new double[iterations];
		int[] opponentStars = new int[iterations];
		double[] opponentDestruction = new double[iterations];
		int[] outcomes = new int[iterations]; // 1 = Home Win, -1 = Opponent Win, 0 = Draw

		double homeParticipationRate = (homeClan != null) ? homeClan.getAttackParticipationRate() : 0.90;
		double oppParticipationRate = (opponentClan != null) ? opponentClan.getAttackParticipationRate() : 0.90;

		IntStream.range(0, iterations).parallel().forEach(i -> {
			SimulatedBase[] homeBases = initBases(homePlayers, effectiveTeamSize);
			SimulatedBase[] opponentBases = initBases(opponentPlayers, effectiveTeamSize);

			simulateClanAttacks(homePlayers, opponentBases, attacksPerMember, homeParticipationRate);
			simulateClanAttacks(opponentPlayers, homeBases, attacksPerMember, oppParticipationRate);

			int hStars = sumStars(opponentBases);
			double hDest = avgDestruction(opponentBases);
			int oStars = sumStars(homeBases);
			double oDest = avgDestruction(homeBases);

			homeStars[i] = hStars;
			homeDestruction[i] = hDest;
			opponentStars[i] = oStars;
			opponentDestruction[i] = oDest;

			if (hStars > oStars)
			{
				outcomes[i] = 1;
			}
			else if (hStars < oStars)
			{
				outcomes[i] = -1;
			}
			else
			{
				if (hDest > oDest)
				{
					outcomes[i] = 1;
				}
				else if (hDest < oDest)
				{
					outcomes[i] = -1;
				}
				else
				{
					outcomes[i] = 0;
				}
			}
		});

		int homeWins = 0;
		int opponentWins = 0;
		int draws = 0;

		for (int outcome : outcomes)
		{
			if (outcome == 1) homeWins++;
			else if (outcome == -1) opponentWins++;
			else draws++;
		}

		double winProb = (double) homeWins / iterations;
		double lossProb = (double) opponentWins / iterations;
		double drawProb = (double) draws / iterations;

		ClanWarPrediction homePrediction = buildClanPrediction(
				homeClan,
				homeStars,
				homeDestruction,
				effectiveTeamSize,
				winProb);

		ClanWarPrediction oppPrediction = buildClanPrediction(
				opponentClan,
				opponentStars,
				opponentDestruction,
				effectiveTeamSize,
				lossProb);

		long executionTime = System.currentTimeMillis() - startTime;
		log.info("Monte Carlo simulation completed in {} ms. Win: {}%, Loss: {}%, Draw: {}%",
				executionTime,
				String.format("%.2f", winProb * 100),
				String.format("%.2f", lossProb * 100),
				String.format("%.2f", drawProb * 100));

		return SimulationResult.builder()
				.warState(model.getWarState())
				.message("Simulation completed successfully.")
				.homeClanPrediction(homePrediction)
				.opponentClanPrediction(oppPrediction)
				.winProbability(winProb)
				.lossProbability(lossProb)
				.drawProbability(drawProb)
				.iterationsRun(iterations)
				.executionTimeMillis(executionTime)
				.performanceModel(model)
				.build();
	}

	private SimulatedBase[] initBases(List<PlayerPerformanceModel> players, int size)
	{
		SimulatedBase[] bases = new SimulatedBase[size];
		for (int i = 0; i < size; i++)
		{
			if (i < players.size())
			{
				PlayerPerformanceModel p = players.get(i);
				bases[i] = new SimulatedBase(
						p.getTownHallLevel(),
						p.getDefenseRating(),
						p.getMapPosition() > 0 ? p.getMapPosition() : (i + 1),
						p.getCurrentBestOpponentStars(),
						p.getCurrentBestOpponentDestruction());
			}
			else
			{
				bases[i] = new SimulatedBase(14, 1.0, i + 1, 0, 0.0);
			}
		}
		return bases;
	}

	private void simulateClanAttacks(
			List<PlayerPerformanceModel> attackers,
			SimulatedBase[] defenderBases,
			int attacksPerMember,
			double clanParticipationRate)
	{
		ThreadLocalRandom random = ThreadLocalRandom.current();

		for (PlayerPerformanceModel attacker : attackers)
		{
			int attacksToUse = attacker.getAttacksRemaining();
			if (attacksToUse <= 0)
			{
				continue;
			}

			for (int attackNum = 1; attackNum <= attacksToUse; attackNum++)
			{
				if (random.nextDouble() > clanParticipationRate)
				{
					continue;
				}

				int targetIndex = selectTargetBase(attacker, defenderBases, attackNum, random);
				if (targetIndex < 0 || targetIndex >= defenderBases.length)
				{
					continue;
				}

				SimulatedBase target = defenderBases[targetIndex];
				AttackResult result = executeAttack(attacker, target, random);

				if (result.stars > target.bestStars)
				{
					target.bestStars = result.stars;
					target.bestDestruction = result.destruction;
				}
				else if (result.stars == target.bestStars && result.destruction > target.bestDestruction)
				{
					target.bestDestruction = result.destruction;
				}
			}
		}
	}

	private int selectTargetBase(
			PlayerPerformanceModel attacker,
			SimulatedBase[] defenderBases,
			int attackNum,
			ThreadLocalRandom random)
	{
		int attackerMapPos = Math.max(1, attacker.getMapPosition());
		int mirrorIndex = Math.min(defenderBases.length - 1, attackerMapPos - 1);

		if (attackNum == 1)
		{
			int[] candidateOffsets = {0, -1, 1, -2, 2};
			for (int offset : candidateOffsets)
			{
				int idx = mirrorIndex + offset;
				if (idx >= 0 && idx < defenderBases.length)
				{
					if (defenderBases[idx].bestStars < 3)
					{
						return idx;
					}
				}
			}
		}

		int bestTargetIdx = -1;
		int lowestStars = 4;
		int bestThDiff = -99;

		for (int i = 0; i < defenderBases.length; i++)
		{
			SimulatedBase base = defenderBases[i];
			if (base.bestStars < 3)
			{
				int thDiff = attacker.getTownHallLevel() - base.townHallLevel;
				if (thDiff >= -1)
				{
					if (base.bestStars < lowestStars || (base.bestStars == lowestStars && thDiff > bestThDiff))
					{
						lowestStars = base.bestStars;
						bestThDiff = thDiff;
						bestTargetIdx = i;
					}
				}
			}
		}

		if (bestTargetIdx != -1)
		{
			return bestTargetIdx;
		}

		for (int i = 0; i < defenderBases.length; i++)
		{
			if (defenderBases[i].bestStars < 3)
			{
				return i;
			}
		}

		return mirrorIndex;
	}

	private AttackResult executeAttack(
			PlayerPerformanceModel attacker,
			SimulatedBase target,
			ThreadLocalRandom random)
	{
		int thDiff = attacker.getTownHallLevel() - target.townHallLevel;
		int clampedDiff = Math.max(-2, Math.min(2, thDiff));

		double prob0 = 0.05;
		double prob1 = 0.15;
		double prob2 = 0.50;
		double prob3 = 0.30;
		double expDest = 85.0;

		Map<Integer, MatchupStarProbability> matchupMap = attacker.getMatchupProbabilities();
		if (matchupMap != null && matchupMap.containsKey(clampedDiff))
		{
			MatchupStarProbability matchup = matchupMap.get(clampedDiff);
			prob0 = matchup.getProb0Star();
			prob1 = matchup.getProb1Star();
			prob2 = matchup.getProb2Star();
			prob3 = matchup.getProb3Star();
			expDest = matchup.getExpectedDestruction();
		}

		double defenseRating = Math.max(0.6, Math.min(1.4, target.defenseRating));
		prob3 = Math.max(0.01, Math.min(0.99, prob3 / defenseRating));
		double remaining = 1.0 - prob3;
		double intermediateSum = prob0 + prob1 + prob2;

		if (intermediateSum > 0.0)
		{
			prob0 = (prob0 / intermediateSum) * remaining;
			prob1 = (prob1 / intermediateSum) * remaining;
			prob2 = (prob2 / intermediateSum) * remaining;
		}
		else
		{
			prob2 = remaining * 0.7;
			prob1 = remaining * 0.2;
			prob0 = remaining * 0.1;
		}

		double roll = random.nextDouble();
		int stars;
		double destruction;

		if (roll < prob0)
		{
			stars = 0;
			double maxDest = Math.max(15.0, Math.min(49.0, expDest));
			destruction = random.nextDouble(10.0, maxDest);
		}
		else if (roll < (prob0 + prob1))
		{
			stars = 1;
			double maxDest = Math.max(51.0, Math.min(85.0, Math.max(expDest, 60.0)));
			destruction = random.nextDouble(50.0, maxDest);
		}
		else if (roll < (prob0 + prob1 + prob2))
		{
			stars = 2;
			double maxDest = Math.max(51.0, Math.min(99.0, Math.max(expDest, 75.0)));
			destruction = random.nextDouble(50.0, maxDest);
		}
		else
		{
			stars = 3;
			destruction = 100.0;
		}

		return new AttackResult(stars, destruction);
	}

	private int sumStars(SimulatedBase[] bases)
	{
		int total = 0;
		for (SimulatedBase b : bases)
		{
			total += b.bestStars;
		}
		return total;
	}

	private double avgDestruction(SimulatedBase[] bases)
	{
		if (bases.length == 0) return 0.0;
		double total = 0.0;
		for (SimulatedBase b : bases)
		{
			total += b.bestDestruction;
		}
		return total / bases.length;
	}

	private ClanWarPrediction buildClanPrediction(
			ClanPerformanceModel clan,
			int[] starsArray,
			double[] destArray,
			int teamSize,
			double projectedWinRate)
	{
		int n = starsArray.length;
		int[] sortedStars = Arrays.copyOf(starsArray, n);
		Arrays.sort(sortedStars);

		long sumStars = 0;
		double sumDest = 0.0;
		int maxPossibleStars = teamSize * 3;
		int perfectWarCount = 0;
		Map<Integer, Long> starFreq = new HashMap<>();

		for (int i = 0; i < n; i++)
		{
			int s = starsArray[i];
			sumStars += s;
			sumDest += destArray[i];
			if (s >= maxPossibleStars)
			{
				perfectWarCount++;
			}
			starFreq.put(s, starFreq.getOrDefault(s, 0L) + 1);
		}

		double meanStars = (double) sumStars / n;
		double meanDest = sumDest / n;
		double medianStars = (n % 2 == 0)
				? (sortedStars[n / 2 - 1] + sortedStars[n / 2]) / 2.0
				: sortedStars[n / 2];

		double varianceSum = 0.0;
		for (int s : starsArray)
		{
			varianceSum += Math.pow(s - meanStars, 2);
		}
		double stdDev = Math.sqrt(varianceSum / n);

		int p025Idx = (int) (n * 0.025);
		int p975Idx = (int) (n * 0.975);
		double lowerBound = sortedStars[Math.max(0, Math.min(n - 1, p025Idx))];
		double upperBound = sortedStars[Math.max(0, Math.min(n - 1, p975Idx))];

		ScoreDistribution scoreDist = ScoreDistribution.builder()
				.meanStars(Math.round(meanStars * 100.0) / 100.0)
				.medianStars(medianStars)
				.minStars(sortedStars[0])
				.maxStars(sortedStars[n - 1])
				.standardDeviation(Math.round(stdDev * 100.0) / 100.0)
				.starFrequency(starFreq)
				.build();

		ConfidenceInterval ci95 = ConfidenceInterval.builder()
				.lowerBound(lowerBound)
				.upperBound(upperBound)
				.confidenceLevel(0.95)
				.build();

		return ClanWarPrediction.builder()
				.clanTag(clan != null ? clan.getClanTag() : null)
				.clanName(clan != null ? clan.getName() : null)
				.expectedStars(Math.round(meanStars * 100.0) / 100.0)
				.expectedDestructionPercentage(Math.round(meanDest * 100.0) / 100.0)
				.starDistribution(scoreDist)
				.starConfidenceInterval95(ci95)
				.perfectWarProbability((double) perfectWarCount / n)
				.projectedWinRate(Math.round(projectedWinRate * 10000.0) / 10000.0)
				.build();
	}

	private static class SimulatedBase
	{
		final int townHallLevel;
		final double defenseRating;
		final int mapPosition;
		int bestStars;
		double bestDestruction;

		SimulatedBase(int townHallLevel, double defenseRating, int mapPosition, int initialStars, double initialDestruction)
		{
			this.townHallLevel = townHallLevel;
			this.defenseRating = defenseRating;
			this.mapPosition = mapPosition;
			this.bestStars = initialStars;
			this.bestDestruction = initialDestruction;
		}
	}

	private static class AttackResult
	{
		final int stars;
		final double destruction;

		AttackResult(int stars, double destruction)
		{
			this.stars = stars;
			this.destruction = destruction;
		}
	}
}
