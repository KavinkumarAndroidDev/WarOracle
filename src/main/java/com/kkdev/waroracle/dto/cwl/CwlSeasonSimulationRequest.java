package com.kkdev.waroracle.dto.cwl;

import com.kkdev.waroracle.dto.simulation.SimulationQuality;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CwlSeasonSimulationRequest
{
	private String clanTag;
	private SimulationQuality quality;
}
