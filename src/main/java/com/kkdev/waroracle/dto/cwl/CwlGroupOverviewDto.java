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
public class CwlGroupOverviewDto
{
	private String groupTag;
	private String state;
	private String season;
	private CwlLeagueTier tier;
	private int totalClans;
	private int totalRounds;
	private int activeRoundNumber;
	private String activeWarTag;
	private DifficultyModifierDetail difficultyModifier;
	private List<CwlClanStanding> standings;
	private List<ClanWarLeagueRoundDto> rounds;
	private List<ClanWarLeagueClanDto> clans;
}
