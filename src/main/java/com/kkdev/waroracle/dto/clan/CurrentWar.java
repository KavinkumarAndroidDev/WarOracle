package com.kkdev.waroracle.dto.clan;

import lombok.Data;

@Data
public class CurrentWar
{
    private String state;
    private int teamSize;
    private int attacksPerMember;
    private String battleModifier;
    private String preparationStartTime;
    private String startTime;
    private String endTime;

    private WarClan clan;
    private WarClan opponent;
}