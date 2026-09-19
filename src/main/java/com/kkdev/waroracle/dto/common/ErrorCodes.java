package com.kkdev.waroracle.dto.common;

public enum ErrorCodes
{

	PLAYER_NOT_FOUND(1004, "Player not found"),
	CLAN_NOT_FOUND(1002, "Clan not found or not currently in war"),
	CLAN_WAR_LOG_PRIVATE(1007, "This clan's war log is set to private. Make it public in clan settings to predict wars"),
	CLASH_API_BAD_REQUEST(1001, "Invalid request parameters"),
	CLASH_API_UNAUTHORIZED(1003, "Service authentication failed"),
	CLASH_API_RATE_LIMITED(1005, "Too many requests. Please try again shortly"),
	SERVICE_UNAVAILABLE(1006, "Unable to reach data services. Please try again later"),
	CLASH_API_ERROR(1000, "Unable to process request at this time. Please try again later");

	private final Integer	errorCode;
	private final String	errorDescription;

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