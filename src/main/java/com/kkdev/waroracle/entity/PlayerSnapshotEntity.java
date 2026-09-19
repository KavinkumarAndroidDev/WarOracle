package com.kkdev.waroracle.entity;

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
@Table(name = "player_snapshots")
public class PlayerSnapshotEntity
{
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "snapshot_id")
	private Long snapshotId;

	@Column(name = "player_tag", length = 15, nullable = false)
	private String playerTag;

	@Column(name = "player_name", length = 100)
	private String playerName;

	@Column(name = "town_hall_level")
	private Integer townHallLevel;

	@Column(name = "town_hall_weapon_level")
	private Integer townHallWeaponLevel;

	@Column(name = "war_stars")
	private Integer warStars;

	@Column(name = "trophies")
	private Integer trophies;

	@Column(name = "exp_level")
	private Integer expLevel;

	@Column(name = "heroes_json", columnDefinition = "json")
	private String heroesJson;

	@Column(name = "hero_equipment_json", columnDefinition = "json")
	private String heroEquipmentJson;

	@Column(name = "troops_json", columnDefinition = "json")
	private String troopsJson;

	@Column(name = "spells_json", columnDefinition = "json")
	private String spellsJson;

	@Column(name = "snapshot_time")
	private LocalDateTime snapshotTime;
}
