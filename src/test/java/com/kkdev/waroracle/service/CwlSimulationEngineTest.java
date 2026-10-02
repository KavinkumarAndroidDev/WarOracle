package com.kkdev.waroracle.service;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kkdev.waroracle.dto.cwl.DifficultyModifierDetail;
import com.kkdev.waroracle.dto.model.ClanPerformanceModel;
import com.kkdev.waroracle.dto.model.PlayerPerformanceModel;
import com.kkdev.waroracle.dto.model.WarPerformanceModel;
import com.kkdev.waroracle.dto.simulation.SimulationQuality;
import com.kkdev.waroracle.dto.simulation.SimulationResult;
import com.kkdev.waroracle.service.cwl.CwlSimulationEngine;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CwlSimulationEngineTest
{

	private CwlSimulationEngine engine;

	@BeforeEach
	void setUp()
	{
		engine = new CwlSimulationEngine();
	}

	@Test
	@DisplayName("Should run Monte Carlo single-attack simulation for 15v15 CWL round")
	void shouldRunCwlSingleAttackSimulation()
	{
		PlayerPerformanceModel homeP1 = PlayerPerformanceModel.builder()
				.playerTag("#HP1")
				.name("Home Chief")
				.townHallLevel(16)
				.mapPosition(1)
				.attacksRemaining(1)
				.defenseRating(1.1)
				.build();

		PlayerPerformanceModel oppP1 = PlayerPerformanceModel.builder()
				.playerTag("#OP1")
				.name("Opp Chief")
				.townHallLevel(16)
				.mapPosition(1)
				.attacksRemaining(1)
				.defenseRating(1.0)
				.build();

		ClanPerformanceModel homeClan = ClanPerformanceModel.builder()
				.clanTag("#HCLAN")
				.name("Home Clan")
				.players(List.of(homeP1))
				.attackParticipationRate(1.0)
				.build();

		ClanPerformanceModel oppClan = ClanPerformanceModel.builder()
				.clanTag("#OCLAN")
				.name("Opp Clan")
				.players(List.of(oppP1))
				.attackParticipationRate(1.0)
				.build();

		WarPerformanceModel model = WarPerformanceModel.builder()
				.warState("inWar")
				.teamSize(1)
				.attacksPerMember(1)
				.homeClan(homeClan)
				.opponentClan(oppClan)
				.simulationQuality(SimulationQuality.LOW)
				.iterations(1000)
				.build();

		DifficultyModifierDetail modifier = DifficultyModifierDetail.builder()
				.modifierCode("none")
				.defenseRatingMultiplier(1.0)
				.build();

		SimulationResult result = engine.simulateRound(model, modifier);

		assertNotNull(result);
		assertNotNull(result.getHomeClanPrediction());
		assertNotNull(result.getOpponentClanPrediction());
		assertTrue(result.getWinProbability() >= 0.0 && result.getWinProbability() <= 1.0);
		assertTrue(result.getIterationsRun() == 1000);
	}
}
