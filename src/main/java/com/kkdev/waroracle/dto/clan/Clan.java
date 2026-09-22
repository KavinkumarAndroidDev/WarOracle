package com.kkdev.waroracle.dto.clan;

import java.util.List;

import com.kkdev.waroracle.dto.player.BadgeUrls;

import lombok.Data;

@Data
public class Clan
{

    private String tag;
    private String name;
    private String type;
    private String description;
    private int clanLevel;
    private int clanPoints;
    private int warWins;
    private int warTies;
    private int warLosses;
    private int warWinStreak;
    private int members;
    private BadgeUrls badgeUrls;

    private List<ClanMember> memberList;
}

