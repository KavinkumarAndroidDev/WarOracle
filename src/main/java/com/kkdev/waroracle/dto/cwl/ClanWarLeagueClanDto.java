package com.kkdev.waroracle.dto.cwl;

import java.util.List;

import com.kkdev.waroracle.dto.player.BadgeUrls;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClanWarLeagueClanDto
{
	private String tag;
	private String name;
	private Integer clanLevel;
	private BadgeUrls badgeUrls;
	private List<ClanWarLeagueMemberDto> members;
}
