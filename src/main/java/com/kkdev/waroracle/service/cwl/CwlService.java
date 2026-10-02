package com.kkdev.waroracle.service.cwl;

import com.kkdev.waroracle.dto.clan.CurrentWar;
import com.kkdev.waroracle.dto.cwl.CwlGroupOverviewDto;
import com.kkdev.waroracle.dto.cwl.CwlRoundSimulationRequest;
import com.kkdev.waroracle.dto.cwl.CwlSeasonSimulationRequest;
import com.kkdev.waroracle.dto.cwl.CwlSeasonSimulationResult;
import com.kkdev.waroracle.dto.cwl.CwlStrategyPlan;
import com.kkdev.waroracle.dto.cwl.CwlStrategyRequest;
import com.kkdev.waroracle.dto.simulation.SimulationResult;

public interface CwlService
{
	CwlGroupOverviewDto getGroupOverview(String clanTag);

	CurrentWar getRoundWar(String warTag);

	SimulationResult simulateRound(CwlRoundSimulationRequest request);

	CwlSeasonSimulationResult simulateSeason(CwlSeasonSimulationRequest request);

	CwlStrategyPlan generateStrategyPlan(CwlStrategyRequest request);
}
