package com.kkdev.waroracle.dto.clan;

import java.util.List;

import lombok.Data;

@Data
public class WarMember
{

    private String tag;
    private String name;

    private int townhallLevel;
    private int mapPosition;
    private List<WarAttack> attacks;
    private int opponentAttacks;
    private WarAttack bestOpponentAttack;
}