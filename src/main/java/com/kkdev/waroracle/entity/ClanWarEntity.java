package com.kkdev.waroracle.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "clan_wars")
public class ClanWarEntity
{
	@Id
	@Column(name = "war_id", length = 64, nullable = false)
	private String warId;

	@Column(name = "clan_tag", length = 15, nullable = false)
	private String clanTag;

	@Column(name = "opponent_tag", length = 15)
	private String opponentTag;

	@Column(name = "state", length = 20, nullable = false)
	private String state;

	@Column(name = "team_size")
	private Integer teamSize;

	@Column(name = "attacks_per_member")
	private Integer attacksPerMember;

	@Column(name = "battle_modifier", length = 50)
	private String battleModifier;

	@Column(name = "preparation_start_time", length = 30)
	private String preparationStartTime;

	@Column(name = "start_time", length = 30)
	private String startTime;

	@Column(name = "end_time", length = 30)
	private String endTime;

	@Column(name = "clan_stars")
	private Integer clanStars;

	@Column(name = "clan_destruction", precision = 5, scale = 2)
	private BigDecimal clanDestruction;

	@Column(name = "opponent_stars")
	private Integer opponentStars;

	@Column(name = "opponent_destruction", precision = 5, scale = 2)
	private BigDecimal opponentDestruction;

	@Column(name = "result", length = 10)
	private String result;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;
}
