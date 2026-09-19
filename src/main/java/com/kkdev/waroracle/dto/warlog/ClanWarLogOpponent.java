package com.kkdev.waroracle.dto.warlog;

import com.kkdev.waroracle.dto.player.BadgeUrls;

import lombok.Data;

@Data
public class ClanWarLogOpponent
{
    private String tag;
    private String name;
    private BadgeUrls badgeUrls;
    private Integer clanLevel;
    private Integer stars;
    private Double destructionPercentage;
}
