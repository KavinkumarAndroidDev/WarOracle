package com.kkdev.waroracle.service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.kkdev.waroracle.dto.battlelog.PlayerBattleLogResponse;
import com.kkdev.waroracle.dto.clan.Clan;
import com.kkdev.waroracle.dto.clan.CurrentWar;
import com.kkdev.waroracle.dto.player.Player;
import com.kkdev.waroracle.dto.warlog.ClanWarLogResponse;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Service
public class ClashApiCacheService
{

	private final IMap<String, String> playerMap;
	private final IMap<String, String> clanMap;
	private final IMap<String, String> currentWarMap;
	private final IMap<String, String> battleLogMap;
	private final IMap<String, String> warLogMap;
	private final JsonMapper jsonMapper;

	@Value("${clash.api.cache.ttl-minutes:3}")
	private long defaultTtlMinutes;

	@Value("${clash.api.cache.war-ttl-minutes:1}")
	private long warTtlMinutes;

	public ClashApiCacheService(HazelcastInstance hazelcastInstance, JsonMapper jsonMapper)
	{
		this.playerMap = hazelcastInstance.getMap("clash-api-players");
		this.clanMap = hazelcastInstance.getMap("clash-api-clans");
		this.currentWarMap = hazelcastInstance.getMap("clash-api-current-wars");
		this.battleLogMap = hazelcastInstance.getMap("clash-api-battle-logs");
		this.warLogMap = hazelcastInstance.getMap("clash-api-war-logs");
		this.jsonMapper = jsonMapper;
	}

	public Player getPlayer(String playerTag)
	{
		String json = playerMap.get(playerTag);
		if (json != null)
		{
			try
			{
				log.debug("Hazelcast cache HIT for player: {}", playerTag);
				return jsonMapper.readValue(json, Player.class);
			}
			catch (Exception e)
			{
				log.warn("Failed to deserialize cached player {}", playerTag, e);
			}
		}
		return null;
	}

	public void putPlayer(String playerTag, Player player)
	{
		if (player == null || playerTag == null) return;
		try
		{
			String json = jsonMapper.writeValueAsString(player);
			playerMap.put(playerTag, json, defaultTtlMinutes, TimeUnit.MINUTES);
			log.debug("Cached player {} in Hazelcast (TTL={}m)", playerTag, defaultTtlMinutes);
		}
		catch (Exception e)
		{
			log.warn("Failed to serialize player for Hazelcast cache", e);
		}
	}

	public Clan getClan(String clanTag)
	{
		String json = clanMap.get(clanTag);
		if (json != null)
		{
			try
			{
				log.debug("Hazelcast cache HIT for clan: {}", clanTag);
				return jsonMapper.readValue(json, Clan.class);
			}
			catch (Exception e)
			{
				log.warn("Failed to deserialize cached clan {}", clanTag, e);
			}
		}
		return null;
	}

	public void putClan(String clanTag, Clan clan)
	{
		if (clan == null || clanTag == null) return;
		try
		{
			String json = jsonMapper.writeValueAsString(clan);
			clanMap.put(clanTag, json, defaultTtlMinutes, TimeUnit.MINUTES);
		}
		catch (Exception e)
		{
			log.warn("Failed to serialize clan for Hazelcast cache", e);
		}
	}

	public CurrentWar getCurrentWar(String clanTag)
	{
		String json = currentWarMap.get(clanTag);
		if (json != null)
		{
			try
			{
				log.debug("Hazelcast cache HIT for current war of clan: {}", clanTag);
				return jsonMapper.readValue(json, CurrentWar.class);
			}
			catch (Exception e)
			{
				log.warn("Failed to deserialize cached current war for clan {}", clanTag, e);
			}
		}
		return null;
	}

	public void putCurrentWar(String clanTag, CurrentWar war)
	{
		if (war == null || clanTag == null) return;
		try
		{
			String json = jsonMapper.writeValueAsString(war);
			long ttlSeconds;
			if ("warEnded".equalsIgnoreCase(war.getState()))
			{
				ttlSeconds = 15;
			}
			else if ("notInWar".equalsIgnoreCase(war.getState()))
			{
				ttlSeconds = 15;
			}
			else
			{
				long remainingSeconds = calculateWarRemainingSeconds(war.getEndTime());
				if (remainingSeconds <= 0)
				{
					ttlSeconds = 5;
				}
				else if (remainingSeconds < 60)
				{
					ttlSeconds = Math.max(5, remainingSeconds);
				}
				else
				{
					ttlSeconds = 60;
				}
			}

			currentWarMap.put(clanTag, json, ttlSeconds, TimeUnit.SECONDS);
		}
		catch (Exception e)
		{
			log.warn("Failed to serialize current war for Hazelcast cache", e);
		}
	}

	private long calculateWarRemainingSeconds(String endTimeStr)
	{
		if (endTimeStr == null || endTimeStr.trim().isEmpty())
		{
			return 60;
		}
		try
		{
			String cleaned = endTimeStr.trim();
			Instant endInstant;
			if (cleaned.contains("."))
			{
				endInstant = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'").withZone(ZoneOffset.UTC).parse(cleaned, Instant::from);
			}
			else
			{
				endInstant = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).parse(cleaned, Instant::from);
			}

			long secondsRemaining = Duration.between(Instant.now(), endInstant).getSeconds();
			return Math.max(0, secondsRemaining);
		}
		catch (Exception e)
		{
			return 60;
		}
	}

	public PlayerBattleLogResponse getPlayerBattleLog(String playerTag)
	{
		String json = battleLogMap.get(playerTag);
		if (json != null)
		{
			try
			{
				log.debug("Hazelcast cache HIT for battle log of player: {}", playerTag);
				return jsonMapper.readValue(json, PlayerBattleLogResponse.class);
			}
			catch (Exception e)
			{
				log.warn("Failed to deserialize cached battle log for {}", playerTag, e);
			}
		}
		return null;
	}

	public void putPlayerBattleLog(String playerTag, PlayerBattleLogResponse battleLog)
	{
		if (battleLog == null || playerTag == null) return;
		try
		{
			String json = jsonMapper.writeValueAsString(battleLog);
			battleLogMap.put(playerTag, json, defaultTtlMinutes, TimeUnit.MINUTES);
		}
		catch (Exception e)
		{
			log.warn("Failed to serialize battle log for Hazelcast cache", e);
		}
	}

	public ClanWarLogResponse getClanWarLog(String clanTag)
	{
		String json = warLogMap.get(clanTag);
		if (json != null)
		{
			try
			{
				log.debug("Hazelcast cache HIT for war log of clan: {}", clanTag);
				return jsonMapper.readValue(json, ClanWarLogResponse.class);
			}
			catch (Exception e)
			{
				log.warn("Failed to deserialize cached war log for clan {}", clanTag, e);
			}
		}
		return null;
	}

	public void putClanWarLog(String clanTag, ClanWarLogResponse warLog)
	{
		if (warLog == null || clanTag == null) return;
		try
		{
			String json = jsonMapper.writeValueAsString(warLog);
			warLogMap.put(clanTag, json, defaultTtlMinutes, TimeUnit.MINUTES);
		}
		catch (Exception e)
		{
			log.warn("Failed to serialize war log for Hazelcast cache", e);
		}
	}
}
