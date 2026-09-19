package com.kkdev.waroracle.dto.simulation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationRequest
{
    private String clanTag;
    private SimulationQuality quality;
}
