package com.kkdev.waroracle.dto.player;

import lombok.Data;

@Data
public class PlayerClan {

    private String tag;
    private Integer clanLevel;
    private String name;
    private BadgeUrls badgeUrls;
}
