package com.kkdev.waroracle.dto.common;

public enum ErrorCodes
{

	CLASH_API_ERROR(1000, "Unable to process request at this time. Please try again later"),
	CLASH_API_BAD_REQUEST(1001, "Invalid request parameters"),
	CLAN_NOT_FOUND(1002, "Clan not found or not currently in war"),
	CLASH_API_UNAUTHORIZED(1003, "Service authentication failed"),
	PLAYER_NOT_FOUND(1004, "Player not found"),
	CLASH_API_RATE_LIMITED(1005, "Too many requests. Please try again shortly"),
	SERVICE_UNAVAILABLE(1006, "Unable to reach data services. Please try again later"),
	CLAN_WAR_LOG_PRIVATE(1007, "This clan's war log is set to private. Make it public in clan settings to predict wars"),
	METHOD_NOT_ALLOWED(1008, "HTTP request method is not supported for this endpoint"),
	UNSUPPORTED_MEDIA_TYPE(1009, "Unsupported media type format"),
	RESOURCE_NOT_FOUND(1010, "Requested endpoint resource was not found"),
	DATABASE_ERROR(1011, "A database error occurred while processing your request"),
	WAR_ALREADY_ENDED(1013, "War has ended. Simulation is only available for preparation or active battle days"),
	CWL_GROUP_NOT_FOUND(1014, "Clan is not currently participating in Clan War Leagues or no active CWL season was found");

	private final Integer errorCode;
	private final String errorDescription;

	ErrorCodes(Integer errorCode, String errorDescription)
	{
		this.errorCode = errorCode;
		this.errorDescription = errorDescription;
	}

	public Integer getErrorCode()
	{
		return errorCode;
	}

	public String getErrorDescription()
	{
		return errorDescription;
	}
}