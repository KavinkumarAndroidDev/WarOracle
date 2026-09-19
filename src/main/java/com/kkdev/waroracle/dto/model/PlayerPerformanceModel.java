package com.kkdev.waroracle.dto.model;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerPerformanceModel
{
    private String playerTag;
    private String name;
    private int townHallLevel;
    private int mapPosition;
    private int attacksRemaining;
    private int attacksMade;
    private int currentBestOpponentStars;
    private double currentBestOpponentDestruction;
    private Map<Integer, MatchupStarProbability> matchupProbabilities;
    private double defenseRating;
    private double avgStarsConceded;
    private double avgDestructionConceded;
}
