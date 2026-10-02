package com.kkdev.waroracle.dto.cwl;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClanWarLeagueMemberDto
{
	private String tag;
	private String name;
	private Integer townHallLevel;
}
