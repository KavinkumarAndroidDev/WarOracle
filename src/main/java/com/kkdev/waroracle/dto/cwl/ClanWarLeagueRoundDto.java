package com.kkdev.waroracle.dto.cwl;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClanWarLeagueRoundDto
{
	private List<String> warTags;

	public static boolean isWarReady(String warTag)
	{
		return warTag != null && !warTag.trim().isEmpty() && !warTag.equals("#0");
	}
}
