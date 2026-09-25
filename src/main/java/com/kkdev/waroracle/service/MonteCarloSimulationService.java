package com.kkdev.waroracle.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
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

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MonteCarloSimulationService
{

	/**
	 * Target indices when selecting a primary attack (wave 1).
	 * Mirror position first, then ±1, ±2 neighbours.
	 * Static constant — allocated once at class load, never inside the hot loop.
	 */
	private static final int[] CANDIDATE_OFFSETS = {0, -1, 1, -2, 2};

	/**
	 * Number of TH-diff slots in the pre-flattened probability table.
	 * Slots cover clampedDiff values: -2, -1, 0, +1, +2  → indices 0..4
	 */
	private static final int TH_DIFF_SLOTS = 5;

	/**
	 * Number of probability values stored per TH-diff slot in the flat table.
	 * Layout: [prob0, prob1, prob2, prob3, expectedDestruction]
	 */
	private static final int PROB_VALUES_PER_SLOT = 5;

	/** Default probability values used when no player-specific matchup data exists. */
	private static final double DEFAULT_PROB_0    = 0.05;
	private static final double DEFAULT_PROB_1    = 0.15;
	private static final double DEFAULT_PROB_2    = 0.50;
	private static final double DEFAULT_PROB_3    = 0.30;
	private static final double DEFAULT_EXP_DEST  = 85.0;

	/**
	 * Isolated ForkJoinPool for simulation work.
	 *
	 * Using the JVM's commonPool (the default for parallel streams) is dangerous
	 * under concurrent HTTP load because the commonPool is shared across the entire
	 * JVM. On a 2-vCPU instance the commonPool has exactly 1 worker thread; two
	 * simultaneous simulations fight over that single thread, causing the
	 * non-linear latency collapse observed in load testing.
	 *
	 * This dedicated pool is capped at (availableProcessors - 1), reserving at
	 * least 1 CPU core for Spring's web container and Hazelcast at all times.
	 */
	private final ForkJoinPool simulationPool;

	public MonteCarloSimulationService()
	{
		int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
		this.simulationPool = new ForkJoinPool(parallelism);
		log.info("MonteCarloSimulationService initialized with ForkJoinPool parallelism={}", parallelism);
	}

	@PreDestroy
	public void shutdown()
	{
		simulationPool.shutdown();
	}

	public SimulationResult simulate(WarPerformanceModel model)
	{
		long startTime = System.currentTimeMillis();
		int iterations = (model.getIterations() > 0) ? model.getIterations() : 20_000;
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

		double homeParticipationRate = (homeClan != null) ? homeClan.getAttackParticipationRate() : 0.90;
		double oppParticipationRate  = (opponentClan != null) ? opponentClan.getAttackParticipationRate() : 0.90;

		// --- Pre-compute flat probability tables (once per simulate() call) ------
		// Layout per player: double[TH_DIFF_SLOTS * PROB_VALUES_PER_SLOT]
		// Indexed as: row[((clampedDiff + 2)) * PROB_VALUES_PER_SLOT + valueIndex]
		// This replaces millions of Map.get(Integer) calls with a direct array read.
		double[][] homeProbs = buildProbTable(homePlayers);
		double[][] oppProbs  = buildProbTable(opponentPlayers);

		// --- Pre-extract per-player base stats (TH level, defense rating, etc.) --
		// Avoids repeated object field lookups inside the parallel loop.
		int[]    homeTh          = extractTownHallLevels(homePlayers, effectiveTeamSize);
		double[] homeDef         = extractDefenseRatings(homePlayers, effectiveTeamSize);
		int[]    homeMapPos      = extractMapPositions(homePlayers, effectiveTeamSize);
		int[]    homeInitStars   = extractInitialStars(homePlayers, effectiveTeamSize);
		double[] homeInitDest    = extractInitialDestruction(homePlayers, effectiveTeamSize);
		int[]    homeAttacksRem  = extractAttacksRemaining(homePlayers, effectiveTeamSize);

		int[]    oppTh           = extractTownHallLevels(opponentPlayers, effectiveTeamSize);
		double[] oppDef          = extractDefenseRatings(opponentPlayers, effectiveTeamSize);
		int[]    oppMapPos       = extractMapPositions(opponentPlayers, effectiveTeamSize);
		int[]    oppInitStars    = extractInitialStars(opponentPlayers, effectiveTeamSize);
		double[] oppInitDest     = extractInitialDestruction(opponentPlayers, effectiveTeamSize);
		int[]    oppAttacksRem   = extractAttacksRemaining(opponentPlayers, effectiveTeamSize);

		// --- Iteration result storage (one value per simulation run) -------------
		int[]    homeStars        = new int[iterations];
		double[] homeDestruction  = new double[iterations];
		int[]    opponentStars    = new int[iterations];
		double[] opponentDestruction = new double[iterations];
		int[]    outcomes         = new int[iterations]; // 1=Home Win, -1=Opp Win, 0=Draw

		// --- Run simulation on isolated pool ------------------------------------
		try
		{
			simulationPool.submit(() ->
				IntStream.range(0, iterations).parallel().forEach(i ->
				{
					ThreadLocalRandom random = ThreadLocalRandom.current();

					// Flat primitive arrays for this iteration's base states.
					// No SimulatedBase objects — zero heap allocation inside loop.
					int[]    hBestStars = new int[effectiveTeamSize];
					double[] hBestDest  = new double[effectiveTeamSize];
					int[]    oBestStars = new int[effectiveTeamSize];
					double[] oBestDest  = new double[effectiveTeamSize];

					// Initialise from pre-extracted current war state
					System.arraycopy(homeInitStars, 0, hBestStars, 0, effectiveTeamSize);
					System.arraycopy(homeInitDest,  0, hBestDest,  0, effectiveTeamSize);
					System.arraycopy(oppInitStars,  0, oBestStars, 0, effectiveTeamSize);
					System.arraycopy(oppInitDest,   0, oBestDest,  0, effectiveTeamSize);

					// Wave 1: primary attacks
					simulateClanAttacks(
							homePlayers, homeProbs, homeTh, homeMapPos, homeAttacksRem,
							oppTh, oppDef, oBestStars, oBestDest,
							1, homeParticipationRate, random);
					simulateClanAttacks(
							opponentPlayers, oppProbs, oppTh, oppMapPos, oppAttacksRem,
							homeTh, homeDef, hBestStars, hBestDest,
							1, oppParticipationRate, random);

					// Wave 2+: cleanup attacks
					if (attacksPerMember > 1)
					{
						for (int wave = 2; wave <= attacksPerMember; wave++)
						{
							simulateClanAttacks(
									homePlayers, homeProbs, homeTh, homeMapPos, homeAttacksRem,
									oppTh, oppDef, oBestStars, oBestDest,
									wave, homeParticipationRate, random);
							simulateClanAttacks(
									opponentPlayers, oppProbs, oppTh, oppMapPos, oppAttacksRem,
									homeTh, homeDef, hBestStars, hBestDest,
									wave, oppParticipationRate, random);
						}
					}

					int hStars   = sumArray(oBestStars);
					double hDest = avgArray(oBestDest);
					int oStars   = sumArray(hBestStars);
					double oDest = avgArray(hBestDest);

					double hDestRounded = Math.round(hDest * 100.0) / 100.0;
					double oDestRounded = Math.round(oDest * 100.0) / 100.0;

					homeStars[i]        = hStars;
					homeDestruction[i]  = hDestRounded;
					opponentStars[i]    = oStars;
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
						if (hDestRounded > oDestRounded)
						{
							outcomes[i] = 1;
						}
						else if (hDestRounded < oDestRounded)
						{
							outcomes[i] = -1;
						}
						else
						{
							outcomes[i] = 0; // True draw — equal stars and equal destruction
						}
					}
				})
			).get();
		}
		catch (Exception ex)
		{
			log.error("Monte Carlo simulation interrupted or failed", ex);
			Thread.currentThread().interrupt();
		}

		int homeWins     = 0;
		int opponentWins = 0;
		int draws        = 0;

		for (int outcome : outcomes)
		{
			if (outcome == 1) homeWins++;
			else if (outcome == -1) opponentWins++;
			else draws++;
		}

		double winProb  = (double) homeWins / iterations;
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

		String verdict        = determineVerdict(winProb, lossProb, drawProb);
		String dataConfidence = calculateDataConfidence(homePlayers, opponentPlayers);

		long executionTime = System.currentTimeMillis() - startTime;
		log.info("Monte Carlo simulation completed in {} ms. Win: {}%, Loss: {}%, Draw: {}%, Verdict: {}, Confidence: {}",
				executionTime,
				String.format("%.2f", winProb * 100),
				String.format("%.2f", lossProb * 100),
				String.format("%.2f", drawProb * 100),
				verdict,
				dataConfidence);

		return SimulationResult.builder()
				.warState(model.getWarState())
				.message("Simulation completed successfully.")
				.homeClanPrediction(homePrediction)
				.opponentClanPrediction(oppPrediction)
				.winProbability(winProb)
				.lossProbability(lossProb)
				.drawProbability(drawProb)
				.verdict(verdict)
				.dataConfidence(dataConfidence)
				.iterationsRun(iterations)
				.executionTimeMillis(executionTime)
				.performanceModel(model)
				.build();
	}

	// -------------------------------------------------------------------------
	// Pre-computation helpers — run once per simulate() call, outside the loop
	// -------------------------------------------------------------------------

	/**
	 * Builds a flat primitive probability table for all players.
	 *
	 * Each row corresponds to one player. Within a row, values are laid out as:
	 *   [ thDiff=-2: p0,p1,p2,p3,dest | thDiff=-1: p0,p1,p2,p3,dest | ... | thDiff=+2 ]
	 *
	 * Index formula: row[(clampedDiff + 2) * PROB_VALUES_PER_SLOT + valueIndex]
	 *
	 * This replaces Map<Integer, MatchupStarProbability> lookups (boxing + hash)
	 * with direct array reads inside the hot parallel loop.
	 */
	private double[][] buildProbTable(List<PlayerPerformanceModel> players)
	{
		int n = players.size();
		double[][] table = new double[n][TH_DIFF_SLOTS * PROB_VALUES_PER_SLOT];

		for (int p = 0; p < n; p++)
		{
			Map<Integer, MatchupStarProbability> matchupMap = players.get(p).getMatchupProbabilities();
			double[] row = table[p];

			for (int slot = 0; slot < TH_DIFF_SLOTS; slot++)
			{
				int diff   = slot - 2; // maps slot 0..4 → diff -2..+2
				int offset = slot * PROB_VALUES_PER_SLOT;

				if (matchupMap != null && matchupMap.containsKey(diff))
				{
					MatchupStarProbability m = matchupMap.get(diff);
					row[offset + 0] = m.getProb0Star();
					row[offset + 1] = m.getProb1Star();
					row[offset + 2] = m.getProb2Star();
					row[offset + 3] = m.getProb3Star();
					row[offset + 4] = m.getExpectedDestruction();
				}
				else
				{
					row[offset + 0] = DEFAULT_PROB_0;
					row[offset + 1] = DEFAULT_PROB_1;
					row[offset + 2] = DEFAULT_PROB_2;
					row[offset + 3] = DEFAULT_PROB_3;
					row[offset + 4] = DEFAULT_EXP_DEST;
				}
			}
		}
		return table;
	}

	private int[] extractTownHallLevels(List<PlayerPerformanceModel> players, int size)
	{
		int[] arr = new int[size];
		Arrays.fill(arr, 14); // default TH level for missing positions
		for (int i = 0; i < players.size() && i < size; i++)
		{
			arr[i] = players.get(i).getTownHallLevel();
		}
		return arr;
	}

	private double[] extractDefenseRatings(List<PlayerPerformanceModel> players, int size)
	{
		double[] arr = new double[size];
		Arrays.fill(arr, 1.0);
		for (int i = 0; i < players.size() && i < size; i++)
		{
			arr[i] = players.get(i).getDefenseRating();
		}
		return arr;
	}

	private int[] extractMapPositions(List<PlayerPerformanceModel> players, int size)
	{
		int[] arr = new int[size];
		for (int i = 0; i < size; i++)
		{
			if (i < players.size())
			{
				int pos = players.get(i).getMapPosition();
				arr[i] = (pos > 0) ? pos : (i + 1);
			}
			else
			{
				arr[i] = i + 1;
			}
		}
		return arr;
	}

	private int[] extractInitialStars(List<PlayerPerformanceModel> players, int size)
	{
		int[] arr = new int[size];
		for (int i = 0; i < players.size() && i < size; i++)
		{
			arr[i] = players.get(i).getCurrentBestOpponentStars();
		}
		return arr;
	}

	private double[] extractInitialDestruction(List<PlayerPerformanceModel> players, int size)
	{
		double[] arr = new double[size];
		for (int i = 0; i < players.size() && i < size; i++)
		{
			arr[i] = players.get(i).getCurrentBestOpponentDestruction();
		}
		return arr;
	}

	private int[] extractAttacksRemaining(List<PlayerPerformanceModel> players, int size)
	{
		int[] arr = new int[size];
		for (int i = 0; i < players.size() && i < size; i++)
		{
			arr[i] = players.get(i).getAttacksRemaining();
		}
		return arr;
	}

	// -------------------------------------------------------------------------
	// Hot-path simulation methods — called inside the parallel loop
	// -------------------------------------------------------------------------

	/**
	 * Simulates one wave of attacks from all active attackers against defender bases.
	 *
	 * All state is passed as primitives or primitive arrays. No objects are created
	 * inside this method (no SimulatedBase, no AttackResult, no boxing).
	 *
	 * @param attackers          list of attacker player models (for count / map position)
	 * @param probTable          pre-flattened [player][slot*5] probability table
	 * @param attackerTh         pre-extracted TH levels of attackers
	 * @param attackerMapPos     pre-extracted map positions of attackers
	 * @param attackerAttacksRem pre-extracted attacks-remaining counts
	 * @param defenderTh         pre-extracted TH levels of defender bases
	 * @param defenderDefRating  pre-extracted defense ratings of defender bases
	 * @param defBestStars       mutable best-stars state of each defender base (modified in place)
	 * @param defBestDest        mutable best-destruction state of each defender base (modified in place)
	 * @param waveNum            which attack wave this is (1 = primary, 2+ = cleanup)
	 * @param participationRate  probability that an attacker participates
	 * @param random             thread-local RNG
	 */
	private void simulateClanAttacks(
			List<PlayerPerformanceModel> attackers,
			double[][] probTable,
			int[] attackerTh,
			int[] attackerMapPos,
			int[] attackerAttacksRem,
			int[] defenderTh,
			double[] defenderDefRating,
			int[] defBestStars,
			double[] defBestDest,
			int waveNum,
			double participationRate,
			ThreadLocalRandom random)
	{
		int defenderCount = defBestStars.length;

		for (int a = 0; a < attackers.size(); a++)
		{
			if (attackerAttacksRem[a] < waveNum)
			{
				continue;
			}
			if (random.nextDouble() > participationRate)
			{
				continue;
			}

			int targetIdx = selectTargetIndex(
					attackerTh[a], attackerMapPos[a],
					defenderTh, defBestStars, defenderCount, waveNum, random);

			if (targetIdx < 0 || targetIdx >= defenderCount)
			{
				continue;
			}

			// executeAttack returns a long encoding: high 32 bits = stars, low 32 bits = destruction×100
			long encoded = executeAttack(
					probTable[a],
					attackerTh[a],
					defenderTh[targetIdx],
					defenderDefRating[targetIdx],
					random);

			int    stars       = (int) (encoded >>> 32);
			double destruction = (encoded & 0xFFFFFFFFL) / 100.0;

			// Apply result — only update if this attack is better than current best
			if (stars > defBestStars[targetIdx])
			{
				defBestStars[targetIdx] = stars;
				defBestDest[targetIdx]  = destruction;
			}
			else if (stars == defBestStars[targetIdx] && destruction > defBestDest[targetIdx])
			{
				defBestDest[targetIdx] = destruction;
			}
		}
	}

	/**
	 * Selects the index of the defender base to attack.
	 * No object allocation. Uses static CANDIDATE_OFFSETS for wave-1 mirror logic.
	 */
	private int selectTargetIndex(
			int attackerTh,
			int attackerMapPos,
			int[] defenderTh,
			int[] defBestStars,
			int defenderCount,
			int waveNum,
			ThreadLocalRandom random)
	{
		int mirrorIndex = Math.min(defenderCount - 1, Math.max(1, attackerMapPos) - 1);

		if (waveNum == 1)
		{
			// Try mirror position and ±1, ±2 neighbours (CANDIDATE_OFFSETS is a static constant)
			for (int offset : CANDIDATE_OFFSETS)
			{
				int idx = mirrorIndex + offset;
				if (idx >= 0 && idx < defenderCount && defBestStars[idx] < 3)
				{
					return idx;
				}
			}
		}
		else
		{
			// Cleanup wave: find the lowest-starred base this attacker can reasonably hit
			int bestIdx     = -1;
			int lowestStars = 4;
			int bestThDiff  = -99;

			for (int i = 0; i < defenderCount; i++)
			{
				if (defBestStars[i] >= 3)
				{
					continue;
				}
				int thDiff = attackerTh - defenderTh[i];
				if (thDiff >= -1)
				{
					if (defBestStars[i] < lowestStars
							|| (defBestStars[i] == lowestStars && thDiff > bestThDiff))
					{
						lowestStars = defBestStars[i];
						bestThDiff  = thDiff;
						bestIdx     = i;
					}
				}
			}
			if (bestIdx != -1)
			{
				return bestIdx;
			}
		}

		// Fallback: any base not yet 3-starred, prioritising by TH match
		int bestIdx     = -1;
		int lowestStars = 4;
		int bestThDiff  = -99;

		for (int i = 0; i < defenderCount; i++)
		{
			if (defBestStars[i] >= 3)
			{
				continue;
			}
			int thDiff = attackerTh - defenderTh[i];
			if (thDiff >= -1)
			{
				if (defBestStars[i] < lowestStars
						|| (defBestStars[i] == lowestStars && thDiff > bestThDiff))
				{
					lowestStars = defBestStars[i];
					bestThDiff  = thDiff;
					bestIdx     = i;
				}
			}
		}

		if (bestIdx != -1)
		{
			return bestIdx;
		}

		// Last resort: first non-3-star base
		for (int i = 0; i < defenderCount; i++)
		{
			if (defBestStars[i] < 3)
			{
				return i;
			}
		}

		return mirrorIndex;
	}

	/**
	 * Simulates one attack and returns the result packed into a single {@code long}.
	 *
	 * Encoding: high 32 bits = stars (0–3), low 32 bits = (destruction × 100) as int.
	 * This eliminates the {@code AttackResult} heap allocation that previously occurred
	 * on every single simulated attack (~6 million times per 50K-iteration request).
	 *
	 * @param probRow      the attacker's probability table row (pre-flattened, 25 doubles)
	 * @param attackerTh   attacker TH level
	 * @param defenderTh   defender TH level
	 * @param defenseRating defender's defense rating multiplier
	 * @param random       thread-local RNG
	 * @return packed long: (stars << 32) | (destruction * 100)
	 */
	private long executeAttack(
			double[] probRow,
			int attackerTh,
			int defenderTh,
			double defenseRating,
			ThreadLocalRandom random)
	{
		int thDiff      = attackerTh - defenderTh;
		int clampedDiff = Math.max(-2, Math.min(2, thDiff));
		int offset      = (clampedDiff + 2) * PROB_VALUES_PER_SLOT;

		double prob0   = probRow[offset + 0];
		double prob1   = probRow[offset + 1];
		double prob2   = probRow[offset + 2];
		double prob3   = probRow[offset + 3];
		double expDest = probRow[offset + 4];

		// Apply defense rating adjustment to prob3 and renormalise lower outcomes
		double clampedDefRating = Math.max(0.6, Math.min(1.4, defenseRating));
		prob3 = Math.max(0.01, Math.min(0.99, prob3 / clampedDefRating));
		double remaining       = 1.0 - prob3;
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
		int    stars;
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

		// Pack: stars in high 32 bits, destruction×100 (as unsigned int) in low 32 bits
		return ((long) stars << 32) | ((long) Math.round(destruction * 100.0) & 0xFFFFFFFFL);
	}

	// -------------------------------------------------------------------------
	// Primitive array utilities
	// -------------------------------------------------------------------------

	private int sumArray(int[] arr)
	{
		int total = 0;
		for (int v : arr)
		{
			total += v;
		}
		return total;
	}

	private double avgArray(double[] arr)
	{
		if (arr.length == 0) return 0.0;
		double total = 0.0;
		for (double v : arr)
		{
			total += v;
		}
		return total / arr.length;
	}

	// -------------------------------------------------------------------------
	// Post-simulation analysis helpers (run once, not in the hot loop)
	// -------------------------------------------------------------------------

	private String determineVerdict(double winProb, double lossProb, double drawProb)
	{
		if (drawProb >= 0.40)
		{
			return "DRAW";
		}
		if (Math.abs(winProb - lossProb) < 0.10)
		{
			return "TOO_CLOSE";
		}
		if (winProb > lossProb)
		{
			return "HOME_WIN";
		}
		return "OPPONENT_WIN";
	}

	private String calculateDataConfidence(List<PlayerPerformanceModel> homePlayers, List<PlayerPerformanceModel> oppPlayers)
	{
		long totalSamples = 0;
		int  matchupCount = 0;

		if (homePlayers != null)
		{
			for (PlayerPerformanceModel p : homePlayers)
			{
				if (p.getMatchupProbabilities() != null)
				{
					for (MatchupStarProbability m : p.getMatchupProbabilities().values())
					{
						totalSamples += m.getSampleCount();
						matchupCount++;
					}
				}
			}
		}

		if (oppPlayers != null)
		{
			for (PlayerPerformanceModel p : oppPlayers)
			{
				if (p.getMatchupProbabilities() != null)
				{
					for (MatchupStarProbability m : p.getMatchupProbabilities().values())
					{
						totalSamples += m.getSampleCount();
						matchupCount++;
					}
				}
			}
		}

		if (matchupCount == 0)
		{
			return "LOW";
		}

		double avgSamplesPerMatchup = (double) totalSamples / matchupCount;
		if (avgSamplesPerMatchup >= 15.0)
		{
			return "HIGH";
		}
		else if (avgSamplesPerMatchup >= 5.0)
		{
			return "MEDIUM";
		}
		else
		{
			return "LOW";
		}
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

		long   sumStars       = 0;
		double sumDest        = 0.0;
		int    maxPossible    = teamSize * 3;
		int    perfectWarCount = 0;
		Map<Integer, Long> starFreq = new HashMap<>();

		for (int i = 0; i < n; i++)
		{
			int s = starsArray[i];
			sumStars += s;
			sumDest  += destArray[i];
			if (s >= maxPossible)
			{
				perfectWarCount++;
			}
			starFreq.put(s, starFreq.getOrDefault(s, 0L) + 1);
		}

		double meanStars  = (double) sumStars / n;
		double meanDest   = sumDest / n;
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
}
