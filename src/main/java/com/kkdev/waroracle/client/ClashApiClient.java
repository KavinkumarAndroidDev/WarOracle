package com.kkdev.waroracle.client;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.kkdev.waroracle.common.ClashAPIConstants;
import com.kkdev.waroracle.dto.battlelog.PlayerBattleLogResponse;
import com.kkdev.waroracle.dto.clan.Clan;
import com.kkdev.waroracle.dto.clan.CurrentWar;
import com.kkdev.waroracle.dto.common.ErrorCodes;
import com.kkdev.waroracle.dto.common.ErrorResponse;
import com.kkdev.waroracle.dto.cwl.ClanWarLeagueGroupResponse;
import com.kkdev.waroracle.dto.player.Player;
import com.kkdev.waroracle.dto.warlog.ClanWarLogResponse;
import com.kkdev.waroracle.exception.ClashApiException;
import com.kkdev.waroracle.service.ClashApiCacheService;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
public class ClashApiClient
{

	private final RestClient restClient;
	private final ClashApiTokenProvider tokenProvider;
	private final JsonMapper jsonMapper;
	private final ClashApiCacheService cacheService;

	public ClashApiClient(
			RestClient.Builder builder,
			ClashApiTokenProvider tokenProvider,
			JsonMapper jsonMapper,
			ClashApiCacheService cacheService)
	{
		this.tokenProvider = tokenProvider;
		this.jsonMapper = jsonMapper;
		this.cacheService = cacheService;
		this.restClient = builder.baseUrl(ClashAPIConstants.BASE_URL).build();
	}

	public Player getPlayer(String playerTag)
	{
		Player cached = cacheService.getPlayer(playerTag);
		if (cached != null)
		{
			return cached;
		}

		String token = tokenProvider.getNextToken();
		log.debug("Calling live Clash API for playerTag: {}", playerTag);
		Player player = restClient.get()
				.uri(ClashAPIConstants.GET_PLAYERS_DETAILS, playerTag)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.retrieve()
				.onStatus(status -> status.isError(), (request, response) -> handleError(response, ErrorCodes.PLAYER_NOT_FOUND, playerTag, token))
				.body(Player.class);

		if (player != null)
		{
			cacheService.putPlayer(playerTag, player);
		}
		return player;
	}

	public Clan getClan(String clanTag)
	{
		Clan cached = cacheService.getClan(clanTag);
		if (cached != null)
		{
			return cached;
		}

		String token = tokenProvider.getNextToken();
		log.debug("Calling live Clash API for clanTag: {}", clanTag);
		Clan clan = restClient.get()
				.uri(ClashAPIConstants.GET_CLAN_DETAILS, clanTag)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.retrieve()
				.onStatus(status -> status.isError(), (request, response) -> handleError(response, ErrorCodes.CLAN_NOT_FOUND, clanTag, token))
				.body(Clan.class);

		if (clan != null)
		{
			cacheService.putClan(clanTag, clan);
		}
		return clan;
	}

	public CurrentWar getCurrentWar(String clanTag)
	{
		CurrentWar cached = cacheService.getCurrentWar(clanTag);
		if (cached != null)
		{
			return cached;
		}

		String token = tokenProvider.getNextToken();
		log.debug("Calling live Clash API for current war of clanTag: {}", clanTag);
		CurrentWar currentWar = restClient.get()
				.uri(ClashAPIConstants.GET_CLANS_CURRENT_WAR, clanTag)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.retrieve()
				.onStatus(status -> status.isError(), (request, response) -> handleError(response, ErrorCodes.CLAN_NOT_FOUND, clanTag, token))
				.body(CurrentWar.class);

		if (currentWar != null)
		{
			cacheService.putCurrentWar(clanTag, currentWar);
		}
		return currentWar;
	}

	public ClanWarLogResponse getClanWarLog(String clanTag, int limit)
	{
		ClanWarLogResponse cached = cacheService.getClanWarLog(clanTag);
		if (cached != null)
		{
			return cached;
		}

		String token = tokenProvider.getNextToken();
		log.debug("Calling live Clash API for clan warlog of clanTag: {} (limit={})", clanTag, limit);
		ClanWarLogResponse warLog = restClient.get()
				.uri(ClashAPIConstants.GET_CLAN_WARLOG, clanTag, limit)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.retrieve()
				.onStatus(status -> status.isError(), (request, response) -> handleError(response, ErrorCodes.CLAN_NOT_FOUND, clanTag, token))
				.body(ClanWarLogResponse.class);

		if (warLog != null)
		{
			cacheService.putClanWarLog(clanTag, warLog);
		}
		return warLog;
	}

	public PlayerBattleLogResponse getPlayerBattleLog(String playerTag)
	{
		PlayerBattleLogResponse cached = cacheService.getPlayerBattleLog(playerTag);
		if (cached != null)
		{
			return cached;
		}

		String token = tokenProvider.getNextToken();
		log.debug("Calling live Clash API for battlelog of playerTag: {}", playerTag);
		PlayerBattleLogResponse battleLog = restClient.get()
				.uri(ClashAPIConstants.GET_PLAYER_BATTLELOG, playerTag)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.retrieve()
				.onStatus(status -> status.isError(), (request, response) -> handleError(response, ErrorCodes.PLAYER_NOT_FOUND, playerTag, token))
				.body(PlayerBattleLogResponse.class);

		if (battleLog != null)
		{
			cacheService.putPlayerBattleLog(playerTag, battleLog);
		}
		return battleLog;
	}

	public ClanWarLeagueGroupResponse getClanWarLeagueGroup(String clanTag)
	{
		ClanWarLeagueGroupResponse cached = cacheService.getClanWarLeagueGroup(clanTag);
		if (cached != null)
		{
			return cached;
		}

		String token = tokenProvider.getNextToken();
		log.debug("Calling live Clash API for CWL group of clanTag: {}", clanTag);
		ClanWarLeagueGroupResponse group = restClient.get()
				.uri(ClashAPIConstants.GET_CLANS_CURRENT_WAR_LEAGUE_GROUP, clanTag)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.retrieve()
				.onStatus(status -> status.isError(), (request, response) -> handleError(response, ErrorCodes.CWL_GROUP_NOT_FOUND, clanTag, token))
				.body(ClanWarLeagueGroupResponse.class);

		if (group != null)
		{
			cacheService.putClanWarLeagueGroup(clanTag, group);
		}
		return group;
	}

	public CurrentWar getCwlWar(String warTag)
	{
		if (warTag == null || warTag.trim().isEmpty() || warTag.equals("#0"))
		{
			return null;
		}

		CurrentWar cached = cacheService.getCwlWar(warTag);
		if (cached != null)
		{
			return cached;
		}

		String token = tokenProvider.getNextToken();
		log.debug("Calling live Clash API for CWL war round: {}", warTag);
		CurrentWar roundWar = restClient.get()
				.uri(ClashAPIConstants.GET_CWL_WAR, warTag)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.retrieve()
				.onStatus(status -> status.isError(), (request, response) -> handleError(response, ErrorCodes.RESOURCE_NOT_FOUND, warTag, token))
				.body(CurrentWar.class);

		if (roundWar != null)
		{
			cacheService.putCwlWar(warTag, roundWar);
		}
		return roundWar;
	}

	private void handleError(ClientHttpResponse response, ErrorCodes notFoundCode, String tag, String tokenUsed) throws IOException
	{
		int statusCode = response.getStatusCode().value();
		log.error("Clash API returned error status {} for tag: {}", statusCode, tag);

		ErrorResponse error = null;
		try
		{
			error = jsonMapper.readValue(response.getBody(), ErrorResponse.class);
			if (error != null)
			{
				log.warn("Clash API error details: reason={}, message={}", error.getReason(), error.getMessage());
			}
		}
		catch (Exception ex)
		{
			log.warn("Unable to parse Clash API error response body", ex);
		}

		switch (statusCode)
		{
			case 404:
				throw new ClashApiException(notFoundCode);
			case 400:
				throw new ClashApiException(ErrorCodes.CLASH_API_BAD_REQUEST);
			case 403:
				if (error != null && error.getReason() != null && error.getReason().toLowerCase().contains("invalidip"))
				{
					throw new ClashApiException(ErrorCodes.CLASH_API_UNAUTHORIZED);
				}
				if (notFoundCode == ErrorCodes.CLAN_NOT_FOUND)
				{
					throw new ClashApiException(ErrorCodes.CLAN_WAR_LOG_PRIVATE);
				}
				throw new ClashApiException(ErrorCodes.CLASH_API_UNAUTHORIZED);
			case 429:
				tokenProvider.markRateLimited(tokenUsed);
				throw new ClashApiException(ErrorCodes.CLASH_API_RATE_LIMITED);
			default:
				throw new ClashApiException(ErrorCodes.CLASH_API_ERROR);
		}
	}
}
