package com.kkdev.waroracle.dto.battlelog;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlayerBattleLogResponse
{
    private List<PlayerBattleLogItem> items;
}
