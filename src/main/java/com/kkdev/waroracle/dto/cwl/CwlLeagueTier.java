package com.kkdev.waroracle.dto.cwl;

import java.util.Arrays;

import lombok.Getter;

@Getter
public enum CwlLeagueTier
{
	BRONZE_III("Bronze League III", "Bronze III", true, 3, 0, 46, 42, null),
	BRONZE_II("Bronze League II", "Bronze II", true, 3, 1, 58, 45, null),
	BRONZE_I("Bronze League I", "Bronze I", true, 3, 1, 70, 48, null),

	SILVER_III("Silver League III", "Silver III", true, 2, 1, 88, 51, null),
	SILVER_II("Silver League II", "Silver II", true, 2, 2, 106, 54, null),
	SILVER_I("Silver League I", "Silver I", true, 2, 2, 124, 57, null),

	GOLD_III("Gold League III", "Gold III", true, 2, 2, 148, 60, null),
	GOLD_II("Gold League II", "Gold II", true, 2, 2, 172, 63, null),
	GOLD_I("Gold League I", "Gold I", true, 2, 2, 196, 66, null),

	CRYSTAL_III("Crystal League III", "Crystal III", true, 2, 2, 220, 69, null),
	CRYSTAL_II("Crystal League II", "Crystal II", true, 2, 2, 244, 72, null),
	CRYSTAL_I("Crystal League I", "Crystal I", true, 2, 2, 274, 75, null),

	MASTER_III("Master League III", "Master III", true, 2, 2, 304, 78, null),
	MASTER_II("Master League II", "Master II", true, 2, 2, 334, 81, null),
	MASTER_I("Master League I", "Master I", true, 1, 2, 364, 84, null),

	CHAMPION_III("Champion League III", "Champion III", false, 1, 2, 388, 87, null),
	CHAMPION_II("Champion League II", "Champion II", false, 1, 2, 412, 90, null),
	CHAMPION_I("Champion League I", "Champion I", false, 1, 2, 436, 93, null),

	TITAN_III("Titan League III", "Titan III", false, 1, 2, 454, 96, "Legend III"),
	TITAN_II("Titan League II", "Titan II", false, 1, 2, 472, 99, "Legend III"),
	TITAN_I("Titan League I", "Titan I", false, 1, 2, 490, 102, "Legend II"),

	LEGEND("Legend League", "Legend", false, 0, 2, 508, 105, "Legend I"),

	UNRANKED("Unranked", "Unranked", true, 0, 0, 0, 0, null);

	private final String displayName;
	private final String shortName;
	private final boolean supports30v30;
	private final int promotedCountStandard;
	private final int demotedCountStandard;
	private final int firstPlaceMedals;
	private final int bonusMedalSize;
	private final String difficultyModifierCode;

	CwlLeagueTier(
			String displayName,
			String shortName,
			boolean supports30v30,
			int promotedCountStandard,
			int demotedCountStandard,
			int firstPlaceMedals,
			int bonusMedalSize,
			String difficultyModifierCode)
	{
		this.displayName = displayName;
		this.shortName = shortName;
		this.supports30v30 = supports30v30;
		this.promotedCountStandard = promotedCountStandard;
		this.demotedCountStandard = demotedCountStandard;
		this.firstPlaceMedals = firstPlaceMedals;
		this.bonusMedalSize = bonusMedalSize;
		this.difficultyModifierCode = difficultyModifierCode;
	}

	public boolean hasDifficultyModifier()
	{
		return difficultyModifierCode != null;
	}

	public static CwlLeagueTier fromNameOrId(String name)
	{
		if (name == null || name.trim().isEmpty())
		{
			return UNRANKED;
		}
		String clean = name.trim().toLowerCase().replace(" ", "").replace("_", "").replace("-", "");
		return Arrays.stream(values())
				.filter(t -> clean.contains(t.name().toLowerCase().replace("_", "")) ||
						clean.contains(t.getShortName().toLowerCase().replace(" ", "")) ||
						clean.contains(t.getDisplayName().toLowerCase().replace(" ", "")))
				.findFirst()
				.orElse(UNRANKED);
	}
}
