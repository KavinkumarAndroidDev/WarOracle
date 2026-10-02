package com.kkdev.waroracle.dto.cwl;

import java.util.List;

import com.kkdev.waroracle.dto.strategy.StrategyMode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CwlStrategyPlan
{
	private String clanTag;
	private String opponentTag;
	private String warTag;
	private int teamSize;
	private StrategyMode strategyMode;
	private double projectedStars;
	private double projectedDestruction;
	private double projectedWinProbability;
	private DifficultyModifierDetail difficultyModifier;
	private List<CwlPlayerAssignment> assignments;
	private String summaryNarrative;
}
