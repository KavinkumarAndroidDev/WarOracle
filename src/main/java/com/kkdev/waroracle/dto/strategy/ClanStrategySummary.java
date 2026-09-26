package com.kkdev.waroracle.dto.strategy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClanStrategySummary
{
	private int totalMembers;
	private int currentHomeStars;
	private int currentOpponentStars;
	private int totalAttacksUsed;
	private int totalAttacksRemaining;
	private int enemyBasesCleared;
	private int enemyBasesRemaining;
	private int primaryAttacksAssigned;
	private int scoutAttacksAssigned;
	private int cleanupReserveAttacks;
	private VulnerableBaseBriefing mostVulnerableEnemyBase;
	private EnemyThreatBriefing biggestEnemyThreat;
}
