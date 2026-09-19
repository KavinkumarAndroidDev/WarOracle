package com.kkdev.waroracle.dto.clan;

import java.util.List;

import com.kkdev.waroracle.dto.player.BadgeUrls;

import lombok.Data;

@Data
public class WarClan
{
    private String tag;
    private String name;
    private BadgeUrls badgeUrls;
    private int clanLevel;

    private int attacks;
    private int stars;
    private double destructionPercentage;

    private List<WarMember> members;
}