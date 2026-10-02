package com.kkdev.waroracle.dto.cwl;

import java.util.Map;

import com.kkdev.waroracle.dto.player.BadgeUrls;
import com.kkdev.waroracle.dto.simulation.ConfidenceInterval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CwlClanSeasonPrediction
{
	private String tag;
	private String name;
	private Integer clanLevel;
	private BadgeUrls badgeUrls;

	private double firstPlaceProbability;
	private double promotionProbability;
	private double demotionProbability;

	private double expectedRank;
	private double expectedTotalStars;
	private double expectedAttackStars;
	private double expectedWins;
	private double expectedDestruction;
	private int expectedMedals;

	private ConfidenceInterval confidenceIntervalStars;
	private Map<Integer, Double> rankDistribution;
}
