package com.kkdev.waroracle.service.cwl;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

import com.kkdev.waroracle.dto.cwl.DifficultyModifierDetail;
import com.kkdev.waroracle.dto.model.ClanPerformanceModel;
import com.kkdev.waroracle.dto.model.MatchupStarProbability;
import com.kkdev.waroracle.dto.model.PlayerPerformanceModel;
import com.kkdev.waroracle.dto.model.WarPerformanceModel;
import com.kkdev.waroracle.dto.simulation.ClanWarPrediction;
import com.kkdev.waroracle.dto.simulation.ConfidenceInterval;
import com.kkdev.waroracle.dto.simulation.ScoreDistribution;
import com.kkdev.waroracle.dto.simulation.SimulationResult;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CwlSimulationEngine
{

	private static final int[] CANDIDATE_OFFSETS = {0, -1, 1, -2, 2};
	private static final int TH_DIFF_SLOTS = 5;
	private static final int PROB_VALUES_PER_SLOT = 5;

	private static final double DEFAULT_PROB_0 = 0.05;
	private static final double DEFAULT_PROB_1 = 0.15;
	private static final double DEFAULT_PROB_2 = 0.50;
	private static final double DEFAULT_PROB_3 = 0.30;
	private static final double DEFAULT_EXP_DEST = 85.0;

	private final ForkJoinPool cwlPool;

	public CwlSimulationEngine()
	{
		int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
		this.cwlPool = new ForkJoinPool(parallelism);
		log.info("CwlSimulationEngine initialized with ForkJoinPool parallelism={}", parallelism);
	}

	@PreDestroy
	public void shutdown()
	{
		cwlPool.shutdown();
	}

	public SimulationResult simulateRound(WarPerformanceModel model, DifficultyModifierDetail modifier)
	{
		long startTime = System.currentTimeMillis();
		int iterations = (model.getIterations() > 0) ? model.getIterations() : 20_000;
		int teamSize = (model.getTeamSize() > 0) ? model.getTeamSize() : 15;

		ClanPerformanceModel homeClan = model.getHomeClan();
		ClanPerformanceModel opponentClan = model.getOpponentClan();

		List<PlayerPerformanceModel> homePlayers = (homeClan != null && homeClan.getPlayers() != null)
				? homeClan.getPlayers() : Collections.emptyList();
		List<PlayerPerformanceModel> opponentPlayers = (opponentClan != null && opponentClan.getPlayers() != null)
				? opponentClan.getPlayers() : Collections.emptyList();

		int effectiveTeamSize = Math.max(teamSize, Math.max(homePlayers.size(), opponentPlayers.size()));

		double homeParticipation = (homeClan != null) ? homeClan.getAttackParticipationRate() : 0.95;
		double oppParticipation  = (opponentClan != null) ? opponentClan.getAttackParticipationRate() : 0.95;

		double[][] homeProbs = buildProbTable(homePlayers, modifier, true);
		double[][] oppProbs  = buildProbTable(opponentPlayers, modifier, false);

		int[] homeTh = extractTownHallLevels(homePlayers, effectiveTeamSize);
		double[] homeDef = extractDefenseRatings(homePlayers, effectiveTeamSize, modifier);
		int[] homeMapPos = extractMapPositions(homePlayers, effectiveTeamSize);
		int[] homeInitStars = extractInitialStars(homePlayers, effectiveTeamSize);
		double[] homeInitDest = extractInitialDestruction(homePlayers, effectiveTeamSize);
		int[] homeAttacksRem = extractAttacksRemaining(homePlayers, effectiveTeamSize);

		int[] oppTh = extractTownHallLevels(opponentPlayers, effectiveTeamSize);
		double[] oppDef = extractDefenseRatings(opponentPlayers, effectiveTeamSize, modifier);
		int[] oppMapPos = extractMapPositions(opponentPlayers, effectiveTeamSize);
		int[] oppInitStars = extractInitialStars(opponentPlayers, effectiveTeamSize);
		double[] oppInitDest = extractInitialDestruction(opponentPlayers, effectiveTeamSize);
		int[] oppAttacksRem = extractAttacksRemaining(opponentPlayers, effectiveTeamSize);

		int[] homeStars = new int[iterations];
		double[] homeDestruction = new double[iterations];
		int[] opponentStars = new int[iterations];
		double[] opponentDestruction = new double[iterations];
		int[] outcomes = new int[iterations]; // 1=Home Win, -1=Opp Win, 0=Draw

		try
		{
			cwlPool.submit(() ->
				IntStream.range(0, iterations).parallel().forEach(i ->
				{
					ThreadLocalRandom random = ThreadLocalRandom.current();

					int[] hBestStars = new int[effectiveTeamSize];
					double[] hBestDest = new double[effectiveTeamSize];
					int[] oBestStars = new int[effectiveTeamSize];
					double[] oBestDest = new double[effectiveTeamSize];

					System.arraycopy(homeInitStars, 0, hBestStars, 0, effectiveTeamSize);
					System.arraycopy(homeInitDest,  0, hBestDest,  0, effectiveTeamSize);
					System.arraycopy(oppInitStars,  0, oBestStars, 0, effectiveTeamSize);
					System.arraycopy(oppInitDest,   0, oBestDest,  0, effectiveTeamSize);

					// CWL: Exactly 1 single attack wave per player
					simulateCwlSingleAttackWave(
							homePlayers, homeProbs, homeTh, homeMapPos, homeAttacksRem,
							oppTh, oppDef, oBestStars, oBestDest,
							homeParticipation, random);

					simulateCwlSingleAttackWave(
							opponentPlayers, oppProbs, oppTh, oppMapPos, oppAttacksRem,
							homeTh, homeDef, hBestStars, hBestDest,
							oppParticipation, random);

					int hStars = sumArray(oBestStars);
					double hDest = avgArray(oBestDest);
					int oStars = sumArray(hBestStars);
					double oDest = avgArray(hBestDest);

					double hDestRounded = Math.round(hDest * 100.0) / 100.0;
					double oDestRounded = Math.round(oDest * 100.0) / 100.0;

					homeStars[i] = hStars;
					homeDestruction[i] = hDestRounded;
					opponentStars[i] = oStars;
					opponentDestruction[i] = oDestRounded;

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
						if (hDestRounded > oDestRounded) outcomes[i] = 1;
						else if (hDestRounded < oDestRounded) outcomes[i] = -1;
						else outcomes[i] = 0;
					}
				})
			).get();
		}
		catch (Exception ex)
		{
			log.error("CWL Monte Carlo simulation interrupted", ex);
			Thread.currentThread().interrupt();
		}

		int homeWins = 0;
		int oppWins = 0;
		int draws = 0;

		for (int outcome : outcomes)
		{
			if (outcome == 1) homeWins++;
			else if (outcome == -1) oppWins++;
			else draws++;
		}

		double winProb  = (double) homeWins / iterations;
		double lossProb = (double) oppWins / iterations;
		double drawProb = (double) draws / iterations;

		ClanWarPrediction homePrediction = buildClanPrediction(
				homeClan, homeStars, homeDestruction, effectiveTeamSize, winProb);
		ClanWarPrediction oppPrediction = buildClanPrediction(
				opponentClan, opponentStars, opponentDestruction, effectiveTeamSize, lossProb);

		long elapsed = System.currentTimeMillis() - startTime;
		String verdict = (winProb >= 0.55) ? "FAVORABLE" : (lossProb >= 0.55) ? "UNFAVORABLE" : "CONTESTED";

		return SimulationResult.builder()
				.warState(model.getWarState())
				.homeClanPrediction(homePrediction)
				.opponentClanPrediction(oppPrediction)
				.winProbability(Math.round(winProb * 1000.0) / 1000.0)
				.lossProbability(Math.round(lossProb * 1000.0) / 1000.0)
				.drawProbability(Math.round(drawProb * 1000.0) / 1000.0)
				.verdict(verdict)
				.dataConfidence("HIGH")
				.iterationsRun(iterations)
				.executionTimeMillis(elapsed)
				.performanceModel(model)
				.build();
	}

	private void simulateCwlSingleAttackWave(
			List<PlayerPerformanceModel> attackers,
			double[][] probTable,
			int[] attackerTh,
			int[] attackerMapPos,
			int[] attacksRemaining,
			int[] defenderTh,
			double[] defenderDef,
			int[] defenderStars,
			double[] defenderDest,
			double participationRate,
			ThreadLocalRandom random)
	{
		int count = attackers.size();
		int defenderCount = defenderTh.length;

		for (int a = 0; a < count; a++)
		{
			if (attacksRemaining[a] <= 0)
			{
				continue;
			}

			if (random.nextDouble() > participationRate)
			{
				continue;
			}

			int myTh = attackerTh[a];
			int myPos = (attackerMapPos[a] > 0) ? attackerMapPos[a] - 1 : a;

			int bestTarget = -1;
			double bestExpectedGain = -1.0;

			for (int offset : CANDIDATE_OFFSETS)
			{
				int candidate = myPos + offset;
				if (candidate < 0 || candidate >= defenderCount)
				{
					continue;
				}

				int targetTh = defenderTh[candidate];
				int thDiff = Math.max(-2, Math.min(2, myTh - targetTh));
				int slotIndex = (thDiff + 2) * PROB_VALUES_PER_SLOT;

				double p3 = probTable[a][slotIndex + 3];
				double p2 = probTable[a][slotIndex + 2];
				double expStars = (p3 * 3.0) + (p2 * 2.0);

				int currentStars = defenderStars[candidate];
				double starGain = Math.max(0.0, expStars - currentStars);

				if (starGain > bestExpectedGain)
				{
					bestExpectedGain = starGain;
					bestTarget = candidate;
				}
			}

			if (bestTarget < 0)
			{
				bestTarget = Math.min(myPos, defenderCount - 1);
			}

			int targetTh = defenderTh[bestTarget];
			int thDiff = Math.max(-2, Math.min(2, myTh - targetTh));
			int slotIndex = (thDiff + 2) * PROB_VALUES_PER_SLOT;

			double p0 = probTable[a][slotIndex];
			double p1 = probTable[a][slotIndex + 1];
			double p2 = probTable[a][slotIndex + 2];
			double expectedDest = probTable[a][slotIndex + 4];

			double roll = random.nextDouble();
			int starsAwarded;
			if (roll < p0) starsAwarded = 0;
			else if (roll < (p0 + p1)) starsAwarded = 1;
			else if (roll < (p0 + p1 + p2)) starsAwarded = 2;
			else starsAwarded = 3;

			double baseDest = expectedDest + (random.nextDouble() * 10.0 - 5.0);
			double actualDest = Math.max(0.0, Math.min(100.0, baseDest));

			if (starsAwarded > defenderStars[bestTarget])
			{
				defenderStars[bestTarget] = starsAwarded;
			}
			if (actualDest > defenderDest[bestTarget])
			{
				defenderDest[bestTarget] = actualDest;
			}
		}
	}

	private double[][] buildProbTable(List<PlayerPerformanceModel> players, DifficultyModifierDetail modifier, boolean isHome)
	{
		int size = players.size();
		double[][] table = new double[size][TH_DIFF_SLOTS * PROB_VALUES_PER_SLOT];

		double heroPenalty = (modifier != null) ? modifier.getAttackingHeroDpsHpPenalty() : 0.0;

		for (int i = 0; i < size; i++)
		{
			PlayerPerformanceModel p = players.get(i);
			Map<Integer, MatchupStarProbability> matchupMap = (p != null) ? p.getMatchupProbabilities() : null;

			for (int diff = -2; diff <= 2; diff++)
			{
				int slot = (diff + 2) * PROB_VALUES_PER_SLOT;
				MatchupStarProbability matchup = (matchupMap != null) ? matchupMap.get(diff) : null;

				if (matchup != null)
				{
					double p0 = matchup.getProb0Star();
					double p1 = matchup.getProb1Star();
					double p2 = matchup.getProb2Star();
					double p3 = matchup.getProb3Star();
					double dest = matchup.getExpectedDestruction();

					if (heroPenalty < 0)
					{
						double p3Drop = p3 * Math.abs(heroPenalty);
						p3 = Math.max(0.0, p3 - p3Drop);
						p2 = Math.min(1.0, p2 + (p3Drop * 0.7));
						p1 = Math.min(1.0, p1 + (p3Drop * 0.3));
						dest = dest * (1.0 + heroPenalty * 0.5);
					}

					table[i][slot]     = p0;
					table[i][slot + 1] = p1;
					table[i][slot + 2] = p2;
					table[i][slot + 3] = p3;
					table[i][slot + 4] = dest;
				}
				else
				{
					table[i][slot]     = DEFAULT_PROB_0;
					table[i][slot + 1] = DEFAULT_PROB_1;
					table[i][slot + 2] = DEFAULT_PROB_2;
					table[i][slot + 3] = DEFAULT_PROB_3;
					table[i][slot + 4] = DEFAULT_EXP_DEST;
				}
			}
		}
		return table;
	}

	private int[] extractTownHallLevels(List<PlayerPerformanceModel> players, int size)
	{
		int[] th = new int[size];
		for (int i = 0; i < size; i++)
		{
			if (i < players.size() && players.get(i) != null)
			{
				th[i] = players.get(i).getTownHallLevel();
			}
			else
			{
				th[i] = 14;
			}
		}
		return th;
	}

	private double[] extractDefenseRatings(List<PlayerPerformanceModel> players, int size, DifficultyModifierDetail modifier)
	{
		double[] def = new double[size];
		double multiplier = (modifier != null) ? modifier.getDefenseRatingMultiplier() : 1.0;

		for (int i = 0; i < size; i++)
		{
			if (i < players.size() && players.get(i) != null)
			{
				def[i] = players.get(i).getDefenseRating() * multiplier;
			}
			else
			{
				def[i] = 1.0 * multiplier;
			}
		}
		return def;
	}

	private int[] extractMapPositions(List<PlayerPerformanceModel> players, int size)
	{
		int[] pos = new int[size];
		for (int i = 0; i < size; i++)
		{
			if (i < players.size() && players.get(i) != null)
			{
				pos[i] = players.get(i).getMapPosition();
			}
			else
			{
				pos[i] = i + 1;
			}
		}
		return pos;
	}

	private int[] extractInitialStars(List<PlayerPerformanceModel> players, int size)
	{
		int[] stars = new int[size];
		for (int i = 0; i < size; i++)
		{
			if (i < players.size() && players.get(i) != null)
			{
				stars[i] = players.get(i).getCurrentBestOpponentStars();
			}
		}
		return stars;
	}

	private double[] extractInitialDestruction(List<PlayerPerformanceModel> players, int size)
	{
		double[] dest = new double[size];
		for (int i = 0; i < size; i++)
		{
			if (i < players.size() && players.get(i) != null)
			{
				dest[i] = players.get(i).getCurrentBestOpponentDestruction();
			}
		}
		return dest;
	}

	private int[] extractAttacksRemaining(List<PlayerPerformanceModel> players, int size)
	{
		int[] rem = new int[size];
		for (int i = 0; i < size; i++)
		{
			if (i < players.size() && players.get(i) != null)
			{
				rem[i] = Math.min(1, players.get(i).getAttacksRemaining());
			}
			else
			{
				rem[i] = 1;
			}
		}
		return rem;
	}

	private int sumArray(int[] arr)
	{
		int sum = 0;
		for (int val : arr) sum += val;
		return sum;
	}

	private double avgArray(double[] arr)
	{
		if (arr.length == 0) return 0.0;
		double sum = 0.0;
		for (double val : arr) sum += val;
		return sum / arr.length;
	}

	private ClanWarPrediction buildClanPrediction(
			ClanPerformanceModel clanModel,
			int[] starSamples,
			double[] destSamples,
			int teamSize,
			double winProbability)
	{
		int maxPossibleStars = teamSize * 3;
		int[] sortedStars = Arrays.copyOf(starSamples, starSamples.length);
		Arrays.sort(sortedStars);

		double[] sortedDest = Arrays.copyOf(destSamples, destSamples.length);
		Arrays.sort(sortedDest);

		double meanStars = 0.0;
		for (int s : starSamples) meanStars += s;
		meanStars /= starSamples.length;

		double meanDest = 0.0;
		for (double d : destSamples) meanDest += d;
		meanDest /= destSamples.length;

		int p05Idx = (int) (sortedStars.length * 0.05);
		int p95Idx = (int) (sortedStars.length * 0.95);

		ConfidenceInterval ciStars = ConfidenceInterval.builder()
				.lowerBound(sortedStars[p05Idx])
				.upperBound(sortedStars[p95Idx])
				.confidenceLevel(0.90)
				.build();

		Map<Integer, Long> freq = new HashMap<>();
		int perfectCount = 0;
		for (int s : starSamples)
		{
			freq.put(s, freq.getOrDefault(s, 0L) + 1L);
			if (s >= maxPossibleStars)
			{
				perfectCount++;
			}
		}

		ScoreDistribution scoreDist = ScoreDistribution.builder()
				.meanStars(Math.round(meanStars * 100.0) / 100.0)
				.medianStars(sortedStars[sortedStars.length / 2])
				.minStars(sortedStars[0])
				.maxStars(sortedStars[sortedStars.length - 1])
				.starFrequency(freq)
				.build();

		return ClanWarPrediction.builder()
				.clanTag(clanModel != null ? clanModel.getClanTag() : "")
				.clanName(clanModel != null ? clanModel.getName() : "")
				.expectedStars(Math.round(meanStars * 100.0) / 100.0)
				.expectedDestructionPercentage(Math.round(meanDest * 100.0) / 100.0)
				.starConfidenceInterval95(ciStars)
				.starDistribution(scoreDist)
				.perfectWarProbability((double) perfectCount / starSamples.length)
				.projectedWinRate(winProbability)
				.build();
	}
}
