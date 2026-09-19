package com.kkdev.waroracle.dto.simulation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfidenceInterval
{
	private double lowerBound;
	private double upperBound;
	private double confidenceLevel;
}
