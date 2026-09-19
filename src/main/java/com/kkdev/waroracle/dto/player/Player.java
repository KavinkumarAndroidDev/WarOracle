package com.kkdev.waroracle.dto.player;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Player
{
	private String name;
	private Role role;
	private String tag;
	private Integer expLevel;
	private Integer trophies;
	private Integer bestTrophies;
	private Integer attackWins;
	private Integer defenseWins;
	private Integer townHallLevel;
	private Integer townHallWeaponLevel;
	private Integer builderHallLevel;
	private LeagueTier leagueTier;
	private PlayerClan clan;
	private Integer warStars;
	private List<Hero> heroes;
	private List<HeroEquipment> heroEquipment;
	private List<Troop> troops;
	private List<Spell> spells;
}