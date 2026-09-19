package com.kkdev.waroracle.dto.simulation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClanWarPrediction
{
	private String clanTag;
	private String clanName;
	private double expectedStars;
	private double expectedDestructionPercentage;
	private ScoreDistribution starDistribution;
	private ConfidenceInterval starConfidenceInterval95;
	private double perfectWarProbability;
	private double projectedWinRate;
}
