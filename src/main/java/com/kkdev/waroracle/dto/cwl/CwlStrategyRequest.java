package com.kkdev.waroracle.dto.cwl;

import com.kkdev.waroracle.dto.strategy.StrategyMode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CwlStrategyRequest
{
	private String clanTag;
	private String warTag;
	private StrategyMode strategyMode;
}
