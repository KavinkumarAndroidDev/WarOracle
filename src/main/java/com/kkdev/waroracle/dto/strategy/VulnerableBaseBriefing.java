package com.kkdev.waroracle.dto.strategy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VulnerableBaseBriefing
{
	private int mapPosition;
	private String defenderTag;
	private String defenderName;
	private int defenderTownHall;
	private String recommendedAttackerTag;
	private String recommendedAttackerName;
	private double expectedStars;
	private double prob3Star;
	private String vulnerabilityReason;
}
