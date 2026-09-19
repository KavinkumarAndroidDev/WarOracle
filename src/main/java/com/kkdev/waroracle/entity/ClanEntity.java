package com.kkdev.waroracle.entity;

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
@Table(name = "clans")
public class ClanEntity
{
	@Id
	@Column(name = "clan_tag", length = 15, nullable = false)
	private String clanTag;

	@Column(name = "name", length = 100, nullable = false)
	private String name;

	@Column(name = "clan_level")
	private Integer clanLevel;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;
}
