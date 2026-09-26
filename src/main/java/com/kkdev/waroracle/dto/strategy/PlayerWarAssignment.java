package com.kkdev.waroracle.dto.strategy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerWarAssignment
{
	private String attackerTag;
	private String attackerName;
	private int attackerTownHall;
	private int mapPosition;
	private int attacksMade;
	private int attacksRemaining;
	private String assignmentStatus; // "ASSIGNED" | "COMPLETED" | "STANDBY"
	private String strategicRole;    // "ANCHOR" | "VANGUARD" | "SCOUT"
	private TargetBriefing primaryTarget;
	private ContingencyCleanup contingencyCleanup;
}
