package com.kkdev.waroracle.dto.clan;

import java.util.List;

import lombok.Data;

@Data
public class Clan
{

    private String tag;

    private String name;

    private int warWins;
    private int warTies;
    private int warLosses;
    private int members;

    private List<ClanMember> memberList;
}
