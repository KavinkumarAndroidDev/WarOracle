package com.kkdev.waroracle.client;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
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
import com.kkdev.waroracle.dto.player.Player;
import com.kkdev.waroracle.dto.warlog.ClanWarLogResponse;
import com.kkdev.waroracle.exception.ClashApiException;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
public class ClashApiClient
{

	private final RestClient restClient;
	private final String apiToken;
	private final JsonMapper jsonMapper;

	public ClashApiClient(RestClient.Builder builder, @Value("${clash.api.token}") String apiToken, JsonMapper jsonMapper)
	{
		this.apiToken = (apiToken != null) ? apiToken.trim() : "";
		this.jsonMapper = jsonMapper;
		this.restClient = builder.baseUrl(ClashAPIConstants.BASE_URL).build();
	}

	public Player getPlayer(String playerTag)
	{
		log.debug("Calling Clash API for playerTag: {}", playerTag);
		return restClient.get()
				.uri(ClashAPIConstants.GET_PLAYERS_DETAILS, playerTag)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
				.retrieve()
				.onStatus(status -> status.isError(), (request, response) -> handleError(response, ErrorCodes.PLAYER_NOT_FOUND, playerTag))
				.body(Player.class);
	}

	public Clan getClan(String clanTag)
	{
		log.debug("Calling Clash API for clanTag: {}", clanTag);
		return restClient.get()
				.uri(ClashAPIConstants.GET_CLAN_DETAILS, clanTag)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
				.retrieve()
				.onStatus(status -> status.isError(), (request, response) -> handleError(response, ErrorCodes.CLAN_NOT_FOUND, clanTag))
				.body(Clan.class);
	}

	public CurrentWar getCurrentWar(String clanTag)
	{
		log.debug("Calling Clash API for current war of clanTag: {}", clanTag);
		return restClient.get()
				.uri(ClashAPIConstants.GET_CLANS_CURRENT_WAR, clanTag)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
				.retrieve()
				.onStatus(status -> status.isError(), (request, response) -> handleError(response, ErrorCodes.CLAN_NOT_FOUND, clanTag))
				.body(CurrentWar.class);
	}

	public ClanWarLogResponse getClanWarLog(String clanTag, int limit)
	{
		log.debug("Calling Clash API for clan warlog of clanTag: {} (limit={})", clanTag, limit);
		return restClient.get()
				.uri(ClashAPIConstants.GET_CLAN_WARLOG, clanTag, limit)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
				.retrieve()
				.onStatus(status -> status.isError(), (request, response) -> handleError(response, ErrorCodes.CLAN_NOT_FOUND, clanTag))
				.body(ClanWarLogResponse.class);
	}

	public PlayerBattleLogResponse getPlayerBattleLog(String playerTag)
	{
		log.debug("Calling Clash API for battlelog of playerTag: {}", playerTag);
		return restClient.get()
				.uri(ClashAPIConstants.GET_PLAYER_BATTLELOG, playerTag)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
				.retrieve()
				.onStatus(status -> status.isError(), (request, response) -> handleError(response, ErrorCodes.PLAYER_NOT_FOUND, playerTag))
				.body(PlayerBattleLogResponse.class);
	}

	private void handleError(ClientHttpResponse response, ErrorCodes notFoundCode, String tag) throws IOException
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
				throw new ClashApiException(ErrorCodes.CLASH_API_RATE_LIMITED);
			default:
				throw new ClashApiException(ErrorCodes.CLASH_API_ERROR);
		}
	}
}
