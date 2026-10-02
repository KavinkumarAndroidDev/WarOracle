package com.kkdev.waroracle.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.kkdev.waroracle.annotation.LogTag;
import com.kkdev.waroracle.common.URIConstants;
import com.kkdev.waroracle.config.LogTagInterceptor;
import com.kkdev.waroracle.dto.clan.CurrentWar;
import com.kkdev.waroracle.dto.common.ApiResponse;
import com.kkdev.waroracle.dto.cwl.CwlGroupOverviewDto;
import com.kkdev.waroracle.dto.cwl.CwlRoundSimulationRequest;
import com.kkdev.waroracle.dto.cwl.CwlSeasonSimulationRequest;
import com.kkdev.waroracle.dto.cwl.CwlSeasonSimulationResult;
import com.kkdev.waroracle.dto.cwl.CwlStrategyPlan;
import com.kkdev.waroracle.dto.cwl.CwlStrategyRequest;
import com.kkdev.waroracle.dto.simulation.SimulationResult;
import com.kkdev.waroracle.service.cwl.CwlService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class CwlController
{

	private final CwlService cwlService;

	public CwlController(CwlService cwlService)
	{
		this.cwlService = cwlService;
	}

	@LogTag("CWL_GET_GROUP")
	@GetMapping(URIConstants.CWL_GROUP)
	public ApiResponse getGroupOverview(@PathVariable("clanTag") String clanTag)
	{
		log.info("Received request for CWL group overview for clanTag: {}", clanTag);
		CwlGroupOverviewDto overview = cwlService.getGroupOverview(clanTag);
		return ApiResponse.success(overview, LogTagInterceptor.getCurrentTraceId());
	}

	@LogTag("CWL_GET_ROUND_WAR")
	@GetMapping(URIConstants.CWL_ROUND_WAR)
	public ApiResponse getRoundWar(@PathVariable("warTag") String warTag)
	{
		log.info("Received request for CWL round war for warTag: {}", warTag);
		CurrentWar war = cwlService.getRoundWar(warTag);
		return ApiResponse.success(war, LogTagInterceptor.getCurrentTraceId());
	}

	@LogTag("CWL_SIMULATE_ROUND")
	@PostMapping(URIConstants.CWL_SIMULATE_ROUND)
	public ApiResponse simulateRound(@RequestBody CwlRoundSimulationRequest request)
	{
		log.info("Received request to simulate CWL round war: {}", request != null ? request.getWarTag() : null);
		SimulationResult result = cwlService.simulateRound(request);
		return ApiResponse.success(result, LogTagInterceptor.getCurrentTraceId());
	}

	@LogTag("CWL_SIMULATE_SEASON")
	@PostMapping(URIConstants.CWL_SIMULATE_SEASON)
	public ApiResponse simulateSeason(@RequestBody CwlSeasonSimulationRequest request)
	{
		log.info("Received request to simulate full CWL season for clanTag: {}", request != null ? request.getClanTag() : null);
		CwlSeasonSimulationResult result = cwlService.simulateSeason(request);
		return ApiResponse.success(result, LogTagInterceptor.getCurrentTraceId());
	}

	@LogTag("CWL_GENERATE_STRATEGY")
	@PostMapping(URIConstants.CWL_STRATEGY)
	public ApiResponse generateStrategy(@RequestBody CwlStrategyRequest request)
	{
		log.info("Received request to generate CWL strategy for warTag: {}", request != null ? request.getWarTag() : null);
		CwlStrategyPlan plan = cwlService.generateStrategyPlan(request);
		return ApiResponse.success(plan, LogTagInterceptor.getCurrentTraceId());
	}
}
