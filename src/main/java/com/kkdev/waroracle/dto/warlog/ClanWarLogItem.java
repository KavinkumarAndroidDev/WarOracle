package com.kkdev.waroracle.dto.warlog;

import lombok.Data;

@Data
public class ClanWarLogItem
{
    private String result;
    private String endTime;
    private int teamSize;
    private int attacksPerMember;
    private String battleModifier;
    private ClanWarLogClan clan;
    private ClanWarLogOpponent opponent;
}
