package com.kkdev.waroracle.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.kkdev.waroracle.dto.model.EmpiricalMatchupStat;
import com.kkdev.waroracle.dto.model.EmpiricalPriorDistribution;
import com.kkdev.waroracle.repository.WarAttackRepository;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Service
public class EmpiricalPriorCalibrationService
{

	private final WarAttackRepository warAttackRepository;
	private final IMap<String, String> hazelcastPriorsMap;
	private final JsonMapper jsonMapper;
	private final Map<Integer, EmpiricalPriorDistribution> localPriorsCache = new ConcurrentHashMap<>();

	private static final double MIN_EMPIRICAL_CONFIDENCE_THRESHOLD = 100.0;

	public EmpiricalPriorCalibrationService(
			WarAttackRepository warAttackRepository,
			HazelcastInstance hazelcastInstance,
			JsonMapper jsonMapper)
	{
		this.warAttackRepository = warAttackRepository;
		this.hazelcastPriorsMap = hazelcastInstance.getMap("empirical-priors-cache");
		this.jsonMapper = jsonMapper;
	}

	@PostConstruct
	public void initialize()
	{
		log.info("Initializing Empirical Prior Calibration Service from MySQL and Hazelcast");
		refreshGlobalPriors();
	}

	@Scheduled(fixedRate = 300000)
	public void refreshGlobalPriors()
	{
		try
		{
			List<EmpiricalMatchupStat> stats = warAttackRepository.aggregateGlobalMatchupStats();
			Map<Integer, long[]> countsByDiff = new HashMap<>();
			Map<Integer, double[]> destByDiff = new HashMap<>();

			for (int diff = -2; diff <= 2; diff++)
			{
				countsByDiff.put(diff, new long[4]); // 0, 1, 2, 3 stars
				destByDiff.put(diff, new double[]{0.0, 0.0}); // sumDest, totalCount
			}

			if (stats != null)
			{
				for (EmpiricalMatchupStat stat : stats)
				{
					if (stat.getThDiff() != null && stat.getStars() != null && stat.getSampleCount() != null)
					{
						int diff = Math.max(-2, Math.min(2, stat.getThDiff()));
						int stars = Math.max(0, Math.min(3, stat.getStars()));
						long count = stat.getSampleCount();
						double avgDest = stat.getAvgDestruction() != null ? stat.getAvgDestruction().doubleValue() : 50.0;

						countsByDiff.get(diff)[stars] += count;
						destByDiff.get(diff)[0] += (avgDest * count);
						destByDiff.get(diff)[1] += count;
					}
				}
			}

			for (int diff = -2; diff <= 2; diff++)
			{
				double[] heuristic = getBaselineHeuristicPriors(diff);
				double heuristicDest = getBaselineHeuristicDestruction(diff);

				long[] counts = countsByDiff.get(diff);
				long totalSamples = counts[0] + counts[1] + counts[2] + counts[3];

				double[] finalProbs = new double[4];
				double finalDest;

				if (totalSamples == 0)
				{
					finalProbs = heuristic;
					finalDest = heuristicDest;
				}
				else
				{
					double beta = Math.min(1.0, (double) totalSamples / MIN_EMPIRICAL_CONFIDENCE_THRESHOLD);
					double[] empiricalProbs = new double[]{
							(double) counts[0] / totalSamples,
							(double) counts[1] / totalSamples,
							(double) counts[2] / totalSamples,
							(double) counts[3] / totalSamples
					};

					for (int k = 0; k < 4; k++)
					{
						finalProbs[k] = ((1.0 - beta) * heuristic[k]) + (beta * empiricalProbs[k]);
					}

					double empiricalAvgDest = destByDiff.get(diff)[1] > 0 ? (destByDiff.get(diff)[0] / destByDiff.get(diff)[1]) : heuristicDest;
					finalDest = ((1.0 - beta) * heuristicDest) + (beta * empiricalAvgDest);
				}

				EmpiricalPriorDistribution dist = EmpiricalPriorDistribution.builder()
						.thDiff(diff)
						.starProbabilities(finalProbs)
						.expectedDestruction(finalDest)
						.totalSamples(totalSamples)
						.build();

				localPriorsCache.put(diff, dist);
				hazelcastPriorsMap.put(String.valueOf(diff), jsonMapper.writeValueAsString(dist));
			}

			log.info("Successfully recalibrated global empirical priors across TH diffs [-2..+2]");
		}
		catch (Exception ex)
		{
			log.warn("Failed to recalibrate global empirical priors from MySQL: {}", ex.getMessage());
			populateFallbackHeuristics();
		}
	}

	public double[] getPriorStarProbabilities(int thDiff)
	{
		int clampedDiff = Math.max(-2, Math.min(2, thDiff));
		EmpiricalPriorDistribution dist = localPriorsCache.get(clampedDiff);
		if (dist != null && dist.getStarProbabilities() != null)
		{
			return dist.getStarProbabilities();
		}
		return getBaselineHeuristicPriors(clampedDiff);
	}

	public double getPriorDestruction(int thDiff)
	{
		int clampedDiff = Math.max(-2, Math.min(2, thDiff));
		EmpiricalPriorDistribution dist = localPriorsCache.get(clampedDiff);
		if (dist != null)
		{
			return dist.getExpectedDestruction();
		}
		return getBaselineHeuristicDestruction(clampedDiff);
	}

	private void populateFallbackHeuristics()
	{
		for (int diff = -2; diff <= 2; diff++)
		{
			EmpiricalPriorDistribution dist = EmpiricalPriorDistribution.builder()
					.thDiff(diff)
					.starProbabilities(getBaselineHeuristicPriors(diff))
					.expectedDestruction(getBaselineHeuristicDestruction(diff))
					.totalSamples(0)
					.build();
			localPriorsCache.put(diff, dist);
		}
	}

	private double[] getBaselineHeuristicPriors(int thDiff)
	{
		if (thDiff >= 2) return new double[]{0.01, 0.02, 0.07, 0.90};
		if (thDiff == 1) return new double[]{0.02, 0.05, 0.18, 0.75};
		if (thDiff == 0) return new double[]{0.05, 0.15, 0.50, 0.30};
		if (thDiff == -1) return new double[]{0.10, 0.40, 0.45, 0.05};
		return new double[]{0.25, 0.55, 0.19, 0.01};
	}

	private double getBaselineHeuristicDestruction(int thDiff)
	{
		if (thDiff >= 2) return 99.0;
		if (thDiff == 1) return 95.0;
		if (thDiff == 0) return 85.0;
		if (thDiff == -1) return 68.0;
		return 52.0;
	}
}
