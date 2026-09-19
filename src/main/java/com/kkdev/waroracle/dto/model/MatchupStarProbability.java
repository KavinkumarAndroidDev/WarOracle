package com.kkdev.waroracle.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchupStarProbability
{
    private int thDifference;
    private double prob0Star;
    private double prob1Star;
    private double prob2Star;
    private double prob3Star;
    private double expectedDestruction;
    private int sampleCount;
}
