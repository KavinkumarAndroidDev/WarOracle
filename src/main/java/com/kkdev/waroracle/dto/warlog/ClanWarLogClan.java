package com.kkdev.waroracle.dto.warlog;

import com.kkdev.waroracle.dto.player.BadgeUrls;

import lombok.Data;

@Data
public class ClanWarLogClan
{
    private String tag;
    private String name;
    private BadgeUrls badgeUrls;
    private int clanLevel;
    private Integer attacks;
    private Integer stars;
    private Double destructionPercentage;
    private Integer expEarned;
}
