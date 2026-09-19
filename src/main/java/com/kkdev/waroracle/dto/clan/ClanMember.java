package com.kkdev.waroracle.dto.clan;

import com.kkdev.waroracle.dto.player.Role;

import lombok.Data;

@Data
public class ClanMember
{
    private String tag;
    private String name;
    private int townHallLevel;
    private Role role;
}