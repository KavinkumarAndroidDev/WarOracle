package com.kkdev.waroracle.dto.cwl;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CwlPlayerAssignment
{
	private String attackerTag;
	private String attackerName;
	private int attackerMapPosition;
	private int attackerTownHallLevel;

	private String targetTag;
	private String targetName;
	private int targetMapPosition;
	private int targetTownHallLevel;

	private double expectedStars;
	private double expectedDestruction;
	private double confidenceRating;
	private String strategicReasoning;
}
