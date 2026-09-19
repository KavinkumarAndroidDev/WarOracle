package com.kkdev.waroracle.common;

public class ClashAPIConstants
{
	private ClashAPIConstants() {
		
	}
	public final static String BASE_URL = "https://api.clashofclans.com/";
	
	public final static String GET_PLAYERS_DETAILS = BASE_URL + "v1/players/{playerTag}";
	
	public final static String GET_CLAN_DETAILS = BASE_URL + "v1/clans/{clanTag}";
	
	public final static String GET_CLANS_CURRENT_WAR = BASE_URL + "v1/clans/{clanTag}/currentwar";

	public final static String GET_CLAN_WARLOG = BASE_URL + "v1/clans/{clanTag}/warlog?limit={limit}";

	public final static String GET_PLAYER_BATTLELOG = BASE_URL + "v1/players/{playerTag}/battlelog";
}
