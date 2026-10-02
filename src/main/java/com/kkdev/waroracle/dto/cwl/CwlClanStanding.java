package com.kkdev.waroracle.dto.cwl;

import com.kkdev.waroracle.dto.player.BadgeUrls;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CwlClanStanding
{
	private int rank;
	private String tag;
	private String name;
	private Integer clanLevel;
	private BadgeUrls badgeUrls;
	private int warsPlayed;
	private int warsWon;
	private int warsLost;
	private int warsTied;
	private int attackStars;
	private int bonusStars;
	private int totalStars; // attackStars + (10 * warsWon) + (possibly ties)
	private double destructionPercentage;
	private int estimatedMedals;
	private boolean promotionZone;
	private boolean demotionZone;
}
