package com.kkdev.waroracle.dto.strategy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TargetBriefing
{
	private String defenderTag;
	private String defenderName;
	private int defenderTownHall;
	private int defenderMapPosition;
	private int currentStarsOnBase;
	private double currentDestructionOnBase;
	private double expectedStars;
	private double marginalStarsGain;
	private double expectedDestruction;
	private double prob3Star;
	private double prob2Star;
	private double prob1Star;
	private double prob0Star;
	private String strategicReason;
}
