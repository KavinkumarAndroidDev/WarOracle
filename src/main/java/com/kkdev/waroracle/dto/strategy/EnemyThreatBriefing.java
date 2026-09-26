package com.kkdev.waroracle.dto.strategy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnemyThreatBriefing
{
	private int mapPosition;
	private String attackerTag;
	private String attackerName;
	private int attackerTownHall;
	private String threatLevel;
	private double estimatedOffensiveRating;
	private String threatAnalysis;
}
