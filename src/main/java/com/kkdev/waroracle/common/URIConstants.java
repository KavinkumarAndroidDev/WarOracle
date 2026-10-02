package com.kkdev.waroracle.common;

public class URIConstants
{
	private URIConstants() {
		
	}
	public final static String PLAYER_DETAILS = "warOracle/player/{playerTag}";
	public final static String CLAN_DETAILS = "warOracle/clan/{clanTag}";
	public final static String BASE_URL = "warOracle/{playerTag}";
	public final static String PERFORMANCE_MODEL = "warOracle/performance-model";
	public final static String SIMULATE = "warOracle/simulate";
	public final static String SIMULATION_QUALITIES = "warOracle/qualities";
	public final static String FEEDBACK = "warOracle/feedback";
	public final static String WAR_STRATEGY = "warOracle/strategy/plan";
	public final static String CWL_GROUP = "warOracle/cwl/group/{clanTag}";
	public final static String CWL_ROUND_WAR = "warOracle/cwl/round/{warTag}";
	public final static String CWL_SIMULATE_ROUND = "warOracle/cwl/simulate/round";
	public final static String CWL_SIMULATE_SEASON = "warOracle/cwl/simulate/season";
	public final static String CWL_STRATEGY = "warOracle/cwl/strategy";
}
