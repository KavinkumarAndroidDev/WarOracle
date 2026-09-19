package com.kkdev.waroracle.dto.simulation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationQualityOption
{
    private String key;
    private String label;
    private int iterations;
    private boolean defaultOption;
}
