package com.kkdev.waroracle.dto.player;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum Role {

    @JsonProperty("leader")
    LEADER,

    @JsonProperty("member")
    MEMBER,

    @JsonProperty("coLeader")
    COLEADER,

    @JsonProperty("notMember")
    NOT_MEMBER,

    @JsonProperty("admin")
    ADMIN
}