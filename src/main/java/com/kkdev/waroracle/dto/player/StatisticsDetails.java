package com.kkdev.waroracle.dto.player;

import com.kkdev.waroracle.dto.clan.Clan;
import com.kkdev.waroracle.dto.clan.CurrentWar;

import lombok.Data;

@Data
public class StatisticsDetails
{
	private int id;
	private Player player;
	private Clan clan;
	private CurrentWar currentWar;
	
}
