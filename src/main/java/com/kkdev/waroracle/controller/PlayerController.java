package com.kkdev.waroracle.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.kkdev.waroracle.annotation.LogTag;
import com.kkdev.waroracle.common.URIConstants;
import com.kkdev.waroracle.config.LogTagInterceptor;
import com.kkdev.waroracle.dto.common.ApiResponse;
import com.kkdev.waroracle.dto.model.WarPerformanceModel;
import com.kkdev.waroracle.dto.player.StatisticsDetails;
import com.kkdev.waroracle.dto.simulation.SimulationQualityOption;
import com.kkdev.waroracle.dto.simulation.SimulationRequest;
import com.kkdev.waroracle.dto.simulation.SimulationResult;
import com.kkdev.waroracle.service.WarOracleService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class PlayerController
{

	private final WarOracleService warOracleService;

	public PlayerController(WarOracleService warOracleService)
	{
		this.warOracleService = warOracleService;
	}

	@LogTag("GET_PLAYER_WAR_DETAILS")
	@GetMapping({URIConstants.PLAYER_DETAILS, URIConstants.BASE_URL})
	public ApiResponse getDetails(@PathVariable String playerTag)
	{
		log.info("Received request to fetch statistics for playerTag: {}", playerTag);
		StatisticsDetails details = warOracleService.getStatistics(playerTag);
		log.info("Successfully fetched statistics for playerTag: {}", playerTag);
		return ApiResponse.success(details, LogTagInterceptor.getCurrentTraceId());
	}

	@LogTag("GET_CLAN_WAR_DETAILS")
	@GetMapping(URIConstants.CLAN_DETAILS)
	public ApiResponse getClanDetails(@PathVariable String clanTag)
	{
		log.info("Received request to fetch statistics for clanTag: {}", clanTag);
		StatisticsDetails details = warOracleService.getClanStatistics(clanTag);
		log.info("Successfully fetched statistics for clanTag: {}", clanTag);
		return ApiResponse.success(details, LogTagInterceptor.getCurrentTraceId());
	}

	@LogTag("BUILD_PERFORMANCE_MODEL")
	@PostMapping(URIConstants.PERFORMANCE_MODEL)
	public ApiResponse buildPerformanceModel(@RequestBody SimulationRequest request)
	{
		log.info("Received request to build performance model for clanTag: {}", request != null ? request.getClanTag() : null);
		WarPerformanceModel model = warOracleService.buildPerformanceModel(request);
		log.info("Successfully built performance model for clanTag: {}", request != null ? request.getClanTag() : null);
		return ApiResponse.success(model, LogTagInterceptor.getCurrentTraceId());
	}

	@LogTag("SIMULATE_WAR")
	@PostMapping(URIConstants.SIMULATE)
	public ApiResponse simulateWar(@RequestBody SimulationRequest request)
	{
		log.info("Received request to simulate war for clanTag: {}", request != null ? request.getClanTag() : null);
		SimulationResult result = warOracleService.simulateWar(request);
		log.info("Successfully completed war simulation for clanTag: {}", request != null ? request.getClanTag() : null);
		return ApiResponse.success(result, LogTagInterceptor.getCurrentTraceId());
	}

	@LogTag("GET_SIMULATION_QUALITIES")
	@GetMapping(URIConstants.SIMULATION_QUALITIES)
	public ApiResponse getSimulationQualities()
	{
		log.info("Received request to fetch simulation quality options");
		List<SimulationQualityOption> options = warOracleService.getSimulationQualities();
		return ApiResponse.success(options, LogTagInterceptor.getCurrentTraceId());
	}
}


