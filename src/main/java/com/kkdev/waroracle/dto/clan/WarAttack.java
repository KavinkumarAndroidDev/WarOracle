package com.kkdev.waroracle.dto.clan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarAttack
{
	private String attackerTag;
	private String defenderTag;
	private int stars;
	private int destructionPercentage;
	private int order;
	private int duration;
}
