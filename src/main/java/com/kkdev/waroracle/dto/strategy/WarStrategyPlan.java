package com.kkdev.waroracle.dto.strategy;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarStrategyPlan
{
	private String clanTag;
	private String clanName;
	private String opponentTag;
	private String opponentName;
	private String warState;
	private StrategyMode strategyMode;
	private double projectedTotalStars;
	private double projectedWinRate;
	private String deltaOverMirrorStrategy;
	private ClanStrategySummary clanLevelSummary;
	private List<PlayerWarAssignment> assignments;
}
