package com.kkdev.waroracle.service;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import com.kkdev.waroracle.client.ClashApiClient;
import com.kkdev.waroracle.dto.battlelog.PlayerBattleLogItem;
import com.kkdev.waroracle.dto.battlelog.PlayerBattleLogResponse;
import com.kkdev.waroracle.dto.player.Player;
import com.kkdev.waroracle.dto.player.Role;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PlayerService
{

	private final ClashApiClient clashApiClient;

	public PlayerService(ClashApiClient clashApiClient)
	{
		this.clashApiClient = clashApiClient;
	}

	public Player getPlayerDetails(String playerTag)
	{
		log.info("Fetching player details for playerTag: {}", playerTag);
		return clashApiClient.getPlayer(playerTag);
	}

	public List<PlayerBattleLogItem> getPlayerBattleLog(String playerTag)
	{
		log.info("Fetching battle log for playerTag: {}", playerTag);
		try
		{
			PlayerBattleLogResponse response = clashApiClient.getPlayerBattleLog(playerTag);
			if (response != null && response.getItems() != null)
			{
				return response.getItems();
			}
		}
		catch (Exception ex)
		{
			log.warn("Failed to fetch battle log for playerTag: {} (possibly private or not found): {}", playerTag, ex.getMessage());
		}
		return Collections.emptyList();
	}

	public boolean isPlayerInClan(Player player)
	{
		if (player == null)
		{
			return false;
		}

		if (player.getRole() == Role.NOT_MEMBER || player.getClan() == null || player.getClan().getTag() == null || player.getClan().getTag().trim().isEmpty())
		{
			return false;
		}

		return true;
	}
}