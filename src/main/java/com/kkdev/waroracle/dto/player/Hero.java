package com.kkdev.waroracle.dto.player;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Hero
{
	private String name;
	private Integer level;
	private Integer maxLevel;
	private String village;
	private List<HeroEquipment> equipment;
}
