package com.kkdev.waroracle.dto.cwl;

import java.util.List;

import com.kkdev.waroracle.dto.simulation.SimulationQuality;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CwlSeasonSimulationResult
{
	private String groupTag;
	private String season;
	private CwlLeagueTier tier;
	private int iterations;
	private long executionTimeMs;
	private SimulationQuality simulationQuality;

	private int totalRounds;
	private int completedRounds;
	private int remainingRounds;

	private List<CwlClanSeasonPrediction> clanPredictions;
	private List<CwlClanStanding> currentStandings;
	private String summaryNarrative;
}
