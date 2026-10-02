package com.kkdev.waroracle.service;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kkdev.waroracle.dto.clan.CurrentWar;
import com.kkdev.waroracle.dto.clan.WarClan;
import com.kkdev.waroracle.dto.clan.WarMember;
import com.kkdev.waroracle.dto.cwl.CwlStrategyPlan;
import com.kkdev.waroracle.dto.cwl.DifficultyModifierDetail;
import com.kkdev.waroracle.dto.strategy.StrategyMode;
import com.kkdev.waroracle.service.cwl.CwlStrategyService;
import com.kkdev.waroracle.service.cwl.DifficultyModifierCalculator;
import com.kkdev.waroracle.service.strategy.KuhnBipartiteOptimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CwlStrategyServiceTest
{

	private CwlStrategyService strategyService;

	@BeforeEach
	void setUp()
	{
		strategyService = new CwlStrategyService(new KuhnBipartiteOptimizer(), new DifficultyModifierCalculator());
	}

	@Test
	@DisplayName("Should generate 1-to-1 single attack assignments for CWL war")
	void shouldGenerateSingleAttackAssignments()
	{
		WarMember a1 = new WarMember();
		a1.setTag("#A1");
		a1.setName("Attacker 1");
		a1.setMapPosition(1);
		a1.setTownhallLevel(16);

		WarMember a2 = new WarMember();
		a2.setTag("#A2");
		a2.setName("Attacker 2");
		a2.setMapPosition(2);
		a2.setTownhallLevel(15);

		WarMember d1 = new WarMember();
		d1.setTag("#D1");
		d1.setName("Defender 1");
		d1.setMapPosition(1);
		d1.setTownhallLevel(16);

		WarMember d2 = new WarMember();
		d2.setTag("#D2");
		d2.setName("Defender 2");
		d2.setMapPosition(2);
		d2.setTownhallLevel(15);

		WarClan home = new WarClan();
		home.setTag("#HOME");
		home.setMembers(List.of(a1, a2));

		WarClan opp = new WarClan();
		opp.setTag("#OPP");
		opp.setMembers(List.of(d1, d2));

		CurrentWar war = new CurrentWar();
		war.setTeamSize(2);
		war.setClan(home);
		war.setOpponent(opp);

		DifficultyModifierDetail modifier = DifficultyModifierDetail.builder()
				.modifierCode("none")
				.build();

		CwlStrategyPlan plan = strategyService.generateStrategyPlan(war, "#WAR1", StrategyMode.BALANCED, modifier);

		assertNotNull(plan);
		assertEquals(2, plan.getAssignments().size());
		assertTrue(plan.getProjectedStars() > 0.0);
		assertTrue(plan.getProjectedWinProbability() > 0.0);
	}
}
