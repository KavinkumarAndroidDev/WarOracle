package com.kkdev.waroracle.dto.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClanPerformanceModel
{
    private String clanTag;
    private String name;
    private int clanLevel;
    private int totalMembersInWar;
    private double attackParticipationRate;
    private double avgStarsPerWar;
    private double avgDestructionPerWar;
    private List<PlayerPerformanceModel> players;
}
