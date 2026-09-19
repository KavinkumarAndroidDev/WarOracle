package com.kkdev.waroracle.dto.model;

import com.kkdev.waroracle.dto.simulation.SimulationQuality;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarPerformanceModel
{
    private String warState;
    private int teamSize;
    private int attacksPerMember;
    private ClanPerformanceModel homeClan;
    private ClanPerformanceModel opponentClan;
    private SimulationQuality simulationQuality;
    private int iterations;
}
