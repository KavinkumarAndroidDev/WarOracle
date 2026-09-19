package com.kkdev.waroracle.service;

import org.springframework.stereotype.Service;

import com.kkdev.waroracle.client.ClashApiClient;
import com.kkdev.waroracle.dto.clan.Clan;
import com.kkdev.waroracle.dto.clan.CurrentWar;
import com.kkdev.waroracle.dto.warlog.ClanWarLogResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ClanService
{

	private final ClashApiClient clashApiClient;

	public ClanService(ClashApiClient clashApiClient)
	{
		this.clashApiClient = clashApiClient;
	}

	public Clan getClanDetails(String clanTag)
	{
		log.info("Fetching clan details for clanTag: {}", clanTag);
		return clashApiClient.getClan(clanTag);
	}

	public CurrentWar getCurrentWar(String clanTag)
	{
		log.info("Fetching current war details for clanTag: {}", clanTag);
		return clashApiClient.getCurrentWar(clanTag);
	}

	public ClanWarLogResponse getClanWarLog(String clanTag, int limit)
	{
		log.info("Fetching clan war log for clanTag: {} with limit: {}", clanTag, limit);
		return clashApiClient.getClanWarLog(clanTag, limit);
	}
}
