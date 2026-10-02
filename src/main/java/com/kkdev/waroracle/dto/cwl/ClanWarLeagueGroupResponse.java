package com.kkdev.waroracle.dto.cwl;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClanWarLeagueGroupResponse
{
	private String tag;
	private String state;
	private String season;
	private List<ClanWarLeagueClanDto> clans;
	private List<ClanWarLeagueRoundDto> rounds;
}
