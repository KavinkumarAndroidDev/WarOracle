package com.kkdev.waroracle.dto.strategy;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContingencyCleanup
{
	private List<Integer> priorityTargetPositions;
	private String cleanupRole;
	private String instructions;
}
