package com.kkdev.waroracle.service.cwl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

import com.kkdev.waroracle.dto.clan.CurrentWar;
import com.kkdev.waroracle.dto.cwl.ClanWarLeagueClanDto;
import com.kkdev.waroracle.dto.cwl.ClanWarLeagueGroupResponse;
import com.kkdev.waroracle.dto.cwl.ClanWarLeagueMemberDto;
import com.kkdev.waroracle.dto.cwl.CwlClanSeasonPrediction;
import com.kkdev.waroracle.dto.cwl.CwlClanStanding;
import com.kkdev.waroracle.dto.cwl.CwlLeagueTier;
import com.kkdev.waroracle.dto.cwl.CwlSeasonSimulationResult;
import com.kkdev.waroracle.dto.cwl.DifficultyModifierDetail;
import com.kkdev.waroracle.dto.model.ClanPerformanceModel;
import com.kkdev.waroracle.dto.simulation.ConfidenceInterval;
import com.kkdev.waroracle.dto.simulation.SimulationQuality;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CwlTournamentSimulationService
{

	private static final int BONUS_STARS_PER_WIN = 10;
	private final CwlLeagueRulesService cwlLeagueRulesService;
	private final ForkJoinPool tournamentPool;

	public CwlTournamentSimulationService(CwlLeagueRulesService cwlLeagueRulesService)
	{
		this.cwlLeagueRulesService = cwlLeagueRulesService;
		int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
		this.tournamentPool = new ForkJoinPool(parallelism);
		log.info("CwlTournamentSimulationService initialized with ForkJoinPool parallelism={}", parallelism);
	}

	@PreDestroy
	public void shutdown()
	{
		tournamentPool.shutdown();
	}

	public CwlSeasonSimulationResult simulateSeason(
			ClanWarLeagueGroupResponse groupResponse,
			List<CurrentWar> knownWars,
			Map<String, ClanPerformanceModel> clanModels,
			CwlLeagueTier tier,
			SimulationQuality quality,
			DifficultyModifierDetail modifier)
	{
		long startTime = System.currentTimeMillis();
		int iterations = (quality != null && quality.getIterations() > 0) ? quality.getIterations() : 10_000;

		List<ClanWarLeagueClanDto> clans = groupResponse != null && groupResponse.getClans() != null
				? groupResponse.getClans() : Collections.emptyList();
		int numClans = clans.size();

		if (numClans == 0)
		{
			return CwlSeasonSimulationResult.builder()
					.groupTag(groupResponse != null ? groupResponse.getTag() : "")
					.season(groupResponse != null ? groupResponse.getSeason() : "")
					.tier(tier)
					.iterations(iterations)
					.executionTimeMs(0)
					.clanPredictions(Collections.emptyList())
					.currentStandings(Collections.emptyList())
					.build();
		}

		int promotedCount = cwlLeagueRulesService.getPromotedCount(tier, numClans);
		int demotedCount = cwlLeagueRulesService.getDemotedCount(tier, numClans);

		Map<String, Integer> clanIndexMap = new HashMap<>();
		for (int i = 0; i < numClans; i++)
		{
			clanIndexMap.put(clans.get(i).getTag(), i);
		}

		// Calculate known actual stats from completed wars
		int[] knownAttackStars = new int[numClans];
		int[] knownWins = new int[numClans];
		int[] knownTies = new int[numClans];
		double[] knownDestruction = new double[numClans];
		int[] knownWarsPlayed = new int[numClans];

		// Matrix of played matches: playedMatrix[i][j] = true if clan i and j already fought
		boolean[][] playedMatrix = new boolean[numClans][numClans];

		if (knownWars != null)
		{
			for (CurrentWar war : knownWars)
			{
				if (war == null || war.getClan() == null || war.getOpponent() == null) continue;
				Integer idxA = clanIndexMap.get(war.getClan().getTag());
				Integer idxB = clanIndexMap.get(war.getOpponent().getTag());
				if (idxA == null || idxB == null) continue;

				if ("warEnded".equalsIgnoreCase(war.getState()))
				{
					playedMatrix[idxA][idxB] = true;
					playedMatrix[idxB][idxA] = true;

					knownAttackStars[idxA] += war.getClan().getStars();
					knownAttackStars[idxB] += war.getOpponent().getStars();

					knownDestruction[idxA] += war.getClan().getDestructionPercentage();
					knownDestruction[idxB] += war.getOpponent().getDestructionPercentage();

					knownWarsPlayed[idxA]++;
					knownWarsPlayed[idxB]++;

					if (war.getClan().getStars() > war.getOpponent().getStars())
					{
						knownWins[idxA]++;
					}
					else if (war.getClan().getStars() < war.getOpponent().getStars())
					{
						knownWins[idxB]++;
					}
					else
					{
						if (war.getClan().getDestructionPercentage() > war.getOpponent().getDestructionPercentage())
						{
							knownWins[idxA]++;
						}
						else if (war.getClan().getDestructionPercentage() < war.getOpponent().getDestructionPercentage())
						{
							knownWins[idxB]++;
						}
						else
						{
							knownTies[idxA]++;
							knownTies[idxB]++;
						}
					}
				}
			}
		}

		// Identify remaining unplayed pairs
		List<int[]> remainingMatchups = new ArrayList<>();
		for (int i = 0; i < numClans; i++)
		{
			for (int j = i + 1; j < numClans; j++)
			{
				if (!playedMatrix[i][j])
				{
					remainingMatchups.add(new int[]{i, j});
				}
			}
		}

		// Calculate average Town Hall level of top roster for each clan
		double[] avgTh = new double[numClans];
		for (int i = 0; i < numClans; i++)
		{
			ClanWarLeagueClanDto cDto = clans.get(i);
			if (cDto.getMembers() != null && !cDto.getMembers().isEmpty())
			{
				avgTh[i] = cDto.getMembers().stream()
						.mapToInt(ClanWarLeagueMemberDto::getTownHallLevel)
						.limit(15)
						.average()
						.orElse(15.0);
			}
			else
			{
				avgTh[i] = 15.0;
			}
		}

		// Estimate baseline expected stars & dest for each clan
		double[] expStarsPerMatch = new double[numClans];
		double[] expDestPerMatch = new double[numClans];

		for (int i = 0; i < numClans; i++)
		{
			ClanPerformanceModel pm = clanModels != null ? clanModels.get(clans.get(i).getTag()) : null;
			if (pm != null && pm.getAvgStarsPerWar() > 0)
			{
				expStarsPerMatch[i] = Math.min(45.0, pm.getAvgStarsPerWar());
				expDestPerMatch[i] = Math.min(100.0, Math.max(50.0, pm.getAvgDestructionPerWar()));
			}
			else
			{
				double thOffset = (avgTh[i] - 14.0) * 1.5;
				double levelOffset = Math.min(2.0, clans.get(i).getClanLevel() * 0.1);
				expStarsPerMatch[i] = Math.max(25.0, Math.min(44.0, 36.0 + thOffset + levelOffset));
				expDestPerMatch[i] = Math.max(65.0, Math.min(98.0, 84.0 + (thOffset * 2.0)));
			}
		}

		// Monte Carlo Accumulators per clan
		int[][] clanRanks = new int[numClans][iterations];
		int[][] clanTotalStars = new int[numClans][iterations];
		int[][] clanWins = new int[numClans][iterations];
		double[][] clanAvgDest = new double[numClans][iterations];

		try
		{
			tournamentPool.submit(() ->
				IntStream.range(0, iterations).parallel().forEach(iter ->
				{
					ThreadLocalRandom random = ThreadLocalRandom.current();

					int[] iterAttackStars = Arrays.copyOf(knownAttackStars, numClans);
					int[] iterWins = Arrays.copyOf(knownWins, numClans);
					double[] iterDest = Arrays.copyOf(knownDestruction, numClans);
					int[] iterPlayed = Arrays.copyOf(knownWarsPlayed, numClans);

					// Simulate all remaining round-robin matches
					for (int[] match : remainingMatchups)
					{
						int cA = match[0];
						int cB = match[1];

						double thDelta = avgTh[cA] - avgTh[cB];

						// Sample score for clan A
						double meanA = Math.max(20.0, Math.min(44.5, expStarsPerMatch[cA] + (thDelta * 0.8)));
						double devA = 2.0;
						int starsA = (int) Math.round(meanA + (random.nextGaussian() * devA));
						starsA = Math.max(15, Math.min(45, starsA));
						double destA = Math.min(100.0, Math.max(40.0, expDestPerMatch[cA] + (thDelta * 1.2) + (random.nextGaussian() * 3.5)));

						// Sample score for clan B
						double meanB = Math.max(20.0, Math.min(44.5, expStarsPerMatch[cB] - (thDelta * 0.8)));
						double devB = 2.0;
						int starsB = (int) Math.round(meanB + (random.nextGaussian() * devB));
						starsB = Math.max(15, Math.min(45, starsB));
						double destB = Math.min(100.0, Math.max(40.0, expDestPerMatch[cB] - (thDelta * 1.2) + (random.nextGaussian() * 3.5)));

						iterAttackStars[cA] += starsA;
						iterAttackStars[cB] += starsB;
						iterDest[cA] += destA;
						iterDest[cB] += destB;
						iterPlayed[cA]++;
						iterPlayed[cB]++;

						if (starsA > starsB)
						{
							iterWins[cA]++;
						}
						else if (starsA < starsB)
						{
							iterWins[cB]++;
						}
						else
						{
							if (destA > destB) iterWins[cA]++;
							else if (destA < destB) iterWins[cB]++;
						}
					}

					// Compute total group score = Attack Stars + 10 * Wins
					int[] finalTotalStars = new int[numClans];
					double[] finalAvgDest = new double[numClans];
					Integer[] order = new Integer[numClans];

					for (int c = 0; c < numClans; c++)
					{
						finalTotalStars[c] = iterAttackStars[c] + (iterWins[c] * BONUS_STARS_PER_WIN);
						finalAvgDest[c] = iterPlayed[c] > 0 ? (iterDest[c] / iterPlayed[c]) : 0.0;
						order[c] = c;

						clanTotalStars[c][iter] = finalTotalStars[c];
						clanWins[c][iter] = iterWins[c];
						clanAvgDest[c][iter] = finalAvgDest[c];
					}

					// Rank clans by Total Stars DESC, then Destruction % DESC
					Arrays.sort(order, (a, b) ->
					{
						int starDiff = Integer.compare(finalTotalStars[b], finalTotalStars[a]);
						if (starDiff != 0) return starDiff;
						return Double.compare(finalAvgDest[b], finalAvgDest[a]);
					});

					for (int rankIdx = 0; rankIdx < numClans; rankIdx++)
					{
						int clanIdx = order[rankIdx];
						clanRanks[clanIdx][iter] = rankIdx + 1;
					}
				})
			).get();
		}
		catch (Exception ex)
		{
			log.error("CWL Season Monte Carlo simulation failed", ex);
			Thread.currentThread().interrupt();
		}

		List<CwlClanSeasonPrediction> predictions = new ArrayList<>();

		for (int c = 0; c < numClans; c++)
		{
			ClanWarLeagueClanDto clanDto = clans.get(c);
			int[] ranks = clanRanks[c];
			int[] totalStarsArr = clanTotalStars[c];
			int[] winsArr = clanWins[c];
			double[] avgDestArr = clanAvgDest[c];

			int firstPlaceWins = 0;
			int promoWins = 0;
			int demoWins = 0;
			double rankSum = 0.0;
			double starsSum = 0.0;
			double winsSum = 0.0;
			double destSum = 0.0;

			Map<Integer, Integer> rankCounts = new HashMap<>();
			for (int iter = 0; iter < iterations; iter++)
			{
				int r = ranks[iter];
				rankCounts.put(r, rankCounts.getOrDefault(r, 0) + 1);
				rankSum += r;
				starsSum += totalStarsArr[iter];
				winsSum += winsArr[iter];
				destSum += avgDestArr[iter];

				if (r == 1) firstPlaceWins++;
				if (r <= promotedCount && promotedCount > 0) promoWins++;
				if (r > (numClans - demotedCount) && demotedCount > 0) demoWins++;
			}

			Map<Integer, Double> rankDist = new HashMap<>();
			for (int r = 1; r <= numClans; r++)
			{
				rankDist.put(r, (double) rankCounts.getOrDefault(r, 0) / iterations);
			}

			int[] sortedTotalStars = Arrays.copyOf(totalStarsArr, totalStarsArr.length);
			Arrays.sort(sortedTotalStars);

			int p05 = (int) (iterations * 0.05);
			int p95 = (int) (iterations * 0.95);

			ConfidenceInterval ciStars = ConfidenceInterval.builder()
					.lowerBound(sortedTotalStars[p05])
					.upperBound(sortedTotalStars[p95])
					.confidenceLevel(0.90)
					.build();

			double expRank = rankSum / iterations;
			int expPlacementMedals = cwlLeagueRulesService.calculateClanPlacementMedals(tier, (int) Math.round(expRank), numClans);

			predictions.add(CwlClanSeasonPrediction.builder()
					.tag(clanDto.getTag())
					.name(clanDto.getName())
					.clanLevel(clanDto.getClanLevel())
					.badgeUrls(clanDto.getBadgeUrls())
					.firstPlaceProbability(Math.round(((double) firstPlaceWins / iterations) * 1000.0) / 1000.0)
					.promotionProbability(Math.round(((double) promoWins / iterations) * 1000.0) / 1000.0)
					.demotionProbability(Math.round(((double) demoWins / iterations) * 1000.0) / 1000.0)
					.expectedRank(Math.round(expRank * 10.0) / 10.0)
					.expectedTotalStars(Math.round((starsSum / iterations) * 10.0) / 10.0)
					.expectedWins(Math.round((winsSum / iterations) * 10.0) / 10.0)
					.expectedDestruction(Math.round((destSum / iterations) * 100.0) / 100.0)
					.expectedMedals(expPlacementMedals)
					.confidenceIntervalStars(ciStars)
					.rankDistribution(rankDist)
					.build());
		}

		predictions.sort(Comparator.comparingDouble(CwlClanSeasonPrediction::getExpectedRank));

		List<CwlClanStanding> currentStandings = cwlLeagueRulesService.calculateStandings(groupResponse, knownWars, tier);
		long elapsed = System.currentTimeMillis() - startTime;

		int totalRounds = (groupResponse != null && groupResponse.getRounds() != null) ? groupResponse.getRounds().size() : (numClans == 6 ? 5 : 7);
		int completedRounds = 0;
		if (knownWars != null)
		{
			for (CurrentWar w : knownWars)
			{
				if ("warEnded".equalsIgnoreCase(w.getState())) completedRounds++;
			}
			completedRounds /= Math.max(1, numClans / 2);
		}

		String narrative = generateSeasonNarrative(predictions.isEmpty() ? null : predictions.get(0), tier, promotedCount, demotedCount);

		return CwlSeasonSimulationResult.builder()
				.groupTag(groupResponse != null ? groupResponse.getTag() : "")
				.season(groupResponse != null ? groupResponse.getSeason() : "")
				.tier(tier)
				.iterations(iterations)
				.executionTimeMs(elapsed)
				.simulationQuality(quality)
				.totalRounds(totalRounds)
				.completedRounds(completedRounds)
				.remainingRounds(Math.max(0, totalRounds - completedRounds))
				.clanPredictions(predictions)
				.currentStandings(currentStandings)
				.summaryNarrative(narrative)
				.build();
	}

	private String generateSeasonNarrative(CwlClanSeasonPrediction leader, CwlLeagueTier tier, int promotedCount, int demotedCount)
	{
		if (leader == null)
		{
			return "No active tournament predictions available.";
		}
		return String.format(
				"%s is currently projected to finish in 1st place with a %.1f%% championship probability. Promotion zone covers top %d clans; demotion zone covers bottom %d clans.",
				leader.getName(),
				leader.getFirstPlaceProbability() * 100.0,
				promotedCount,
				demotedCount);
	}
}
