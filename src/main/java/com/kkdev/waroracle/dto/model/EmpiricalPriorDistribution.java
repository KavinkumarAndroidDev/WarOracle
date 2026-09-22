package com.kkdev.waroracle.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmpiricalPriorDistribution
{
	private int thDiff;
	private double[] starProbabilities;
	private double expectedDestruction;
	private long totalSamples;
}
