package com.kkdev.waroracle.service.strategy;

import com.kkdev.waroracle.dto.strategy.WarStrategyPlan;
import com.kkdev.waroracle.dto.strategy.WarStrategyRequest;

public interface WarStrategyService
{
	WarStrategyPlan generateOptimalWarStrategy(WarStrategyRequest request);
}
