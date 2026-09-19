package com.kkdev.waroracle.dto.simulation;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreDistribution
{
	private double meanStars;
	private double medianStars;
	private int minStars;
	private int maxStars;
	private double standardDeviation;
	private Map<Integer, Long> starFrequency;
}
