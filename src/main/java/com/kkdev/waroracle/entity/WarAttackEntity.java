package com.kkdev.waroracle.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
@Table(name = "war_attacks")
public class WarAttackEntity
{
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "attack_id")
	private Long attackId;

	@Column(name = "war_id", length = 64, nullable = false)
	private String warId;

	@Column(name = "attacker_tag", length = 15, nullable = false)
	private String attackerTag;

	@Column(name = "attacker_th")
	private Integer attackerTh;

	@Column(name = "defender_tag", length = 15, nullable = false)
	private String defenderTag;

	@Column(name = "defender_th")
	private Integer defenderTh;

	@Column(name = "th_diff")
	private Integer thDiff;

	@Column(name = "stars", nullable = false)
	private Integer stars;

	@Column(name = "destruction_percentage", precision = 5, scale = 2, nullable = false)
	private BigDecimal destructionPercentage;

	@Column(name = "attack_order")
	private Integer attackOrder;

	@Column(name = "duration_seconds")
	private Integer durationSeconds;

	@Column(name = "is_clan_attack", nullable = false)
	private Boolean isClanAttack;

	@Column(name = "created_at")
	private LocalDateTime createdAt;
}
