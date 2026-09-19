package com.kkdev.waroracle.dto.battlelog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlayerBattleLogItem
{
    private String battleType;
    private Boolean attack;
    private String opponentPlayerTag;
    private String opponentName;
    private Integer opponentTownHallLevel;
    private Integer stars;
    private Double destructionPercentage;
    private Integer battleTime;
    private String battleTimestamp;
}
