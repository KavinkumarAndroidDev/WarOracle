package com.kkdev.waroracle.dto.simulation;

import com.kkdev.waroracle.dto.clan.CurrentWar;
import com.kkdev.waroracle.dto.model.WarPerformanceModel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationResult
{
	private String warState;
	private String message;
	private CurrentWar currentWar;
	private ClanWarPrediction homeClanPrediction;
	private ClanWarPrediction opponentClanPrediction;
	private double winProbability;
	private double lossProbability;
	private double drawProbability;
	private int iterationsRun;
	private long executionTimeMillis;
	private WarPerformanceModel performanceModel;
}

