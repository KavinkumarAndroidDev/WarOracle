package com.kkdev.waroracle.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.kkdev.waroracle.annotation.LogTag;
import com.kkdev.waroracle.common.URIConstants;
import com.kkdev.waroracle.config.LogTagInterceptor;
import com.kkdev.waroracle.dto.common.ApiResponse;
import com.kkdev.waroracle.dto.strategy.WarStrategyPlan;
import com.kkdev.waroracle.dto.strategy.WarStrategyRequest;
import com.kkdev.waroracle.service.strategy.WarStrategyService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class WarStrategyController
{

	private final WarStrategyService warStrategyService;

	public WarStrategyController(WarStrategyService warStrategyService)
	{
		this.warStrategyService = warStrategyService;
	}

	@LogTag("GENERATE_WAR_STRATEGY")
	@PostMapping(URIConstants.WAR_STRATEGY)
	public ApiResponse generateOptimalWarStrategy(@RequestBody WarStrategyRequest request)
	{
		log.info("Received request to generate optimal war strategy for clanTag: {}", request != null ? request.getClanTag() : null);
		WarStrategyPlan plan = warStrategyService.generateOptimalWarStrategy(request);
		log.info("Successfully generated optimal war strategy plan for clanTag: {}", request != null ? request.getClanTag() : null);
		return ApiResponse.success(plan, LogTagInterceptor.getCurrentTraceId());
	}
}
