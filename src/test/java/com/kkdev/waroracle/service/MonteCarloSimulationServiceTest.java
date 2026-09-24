package com.kkdev.waroracle.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kkdev.waroracle.dto.model.ClanPerformanceModel;
import com.kkdev.waroracle.dto.model.MatchupStarProbability;
import com.kkdev.waroracle.dto.model.PlayerPerformanceModel;
import com.kkdev.waroracle.dto.model.WarPerformanceModel;
import com.kkdev.waroracle.dto.simulation.SimulationQuality;
import com.kkdev.waroracle.dto.simulation.SimulationResult;

class MonteCarloSimulationServiceTest
{

    private MonteCarloSimulationService simulationService;

    @BeforeEach
    void setUp()
    {
        simulationService = new MonteCarloSimulationService();
    }

    @Test
    void testPerfectWarSymmetricMatchupDraws()
    {
        // Setup two symmetric elite clans where both always 3-star
        ClanPerformanceModel homeClan = createEliteClan("HOME", "#HOME123", 10);
        ClanPerformanceModel oppClan = createEliteClan("OPPONENT", "#OPP123", 10);

        WarPerformanceModel warModel = WarPerformanceModel.builder()
                .warState("inWar")
                .teamSize(10)
                .attacksPerMember(2)
                .homeClan(homeClan)
                .opponentClan(oppClan)
                .simulationQuality(SimulationQuality.MEDIUM)
                .iterations(2000)
                .build();

        SimulationResult result = simulationService.simulate(warModel);

        assertNotNull(result);
        assertNotNull(result.getHomeClanPrediction());
        assertNotNull(result.getOpponentClanPrediction());

        // Both elite clans should have near 100% perfect war probabilities
        assertTrue(result.getHomeClanPrediction().getPerfectWarProbability() > 0.90,
                "Home perfect war prob should be > 90%");
        assertTrue(result.getOpponentClanPrediction().getPerfectWarProbability() > 0.90,
                "Opponent perfect war prob should be > 90%");

        // The draw probability should be high since both clans get 30 stars / 100% destruction
        assertTrue(result.getDrawProbability() > 0.80,
                "Draw probability should be > 80% when both clans reliably get perfect wars");
        assertEquals("DRAW", result.getVerdict(), "Verdict should be DRAW");
        assertEquals("HIGH", result.getDataConfidence(), "Confidence should be HIGH with 30 samples per player");
    }

    @Test
    void testAsymmetricMatchupWinProbability()
    {
        // Setup an elite home clan against a weak opponent clan
        ClanPerformanceModel homeClan = createEliteClan("HOME_ELITE", "#HOME123", 10);
        ClanPerformanceModel oppClan = createWeakClan("OPP_WEAK", "#OPP123", 10);

        WarPerformanceModel warModel = WarPerformanceModel.builder()
                .warState("inWar")
                .teamSize(10)
                .attacksPerMember(2)
                .homeClan(homeClan)
                .opponentClan(oppClan)
                .simulationQuality(SimulationQuality.MEDIUM)
                .iterations(1000)
                .build();

        SimulationResult result = simulationService.simulate(warModel);

        assertNotNull(result);
        assertTrue(result.getWinProbability() > 0.85, "Elite clan should have > 85% win probability over weak clan");
        assertTrue(result.getLossProbability() < 0.10, "Elite clan loss probability should be < 10%");
        assertEquals("HOME_WIN", result.getVerdict(), "Verdict should be HOME_WIN");
        assertEquals("HIGH", result.getDataConfidence());
    }

    private ClanPerformanceModel createEliteClan(String name, String tag, int size)
    {
        List<PlayerPerformanceModel> players = new ArrayList<>();
        for (int i = 1; i <= size; i++)
        {
            Map<Integer, MatchupStarProbability> matchups = new HashMap<>();
            for (int diff = -2; diff <= 2; diff++)
            {
                matchups.put(diff, MatchupStarProbability.builder()
                        .thDifference(diff)
                        .prob0Star(0.0)
                        .prob1Star(0.0)
                        .prob2Star(0.02)
                        .prob3Star(0.98)
                        .expectedDestruction(100.0)
                        .sampleCount(30)
                        .build());
            }

            players.add(PlayerPerformanceModel.builder()
                    .playerTag(tag + "_P" + i)
                    .name("Player " + i)
                    .townHallLevel(16)
                    .mapPosition(i)
                    .attacksMade(0)
                    .attacksRemaining(2)
                    .currentBestOpponentStars(0)
                    .currentBestOpponentDestruction(0.0)
                    .matchupProbabilities(matchups)
                    .defenseRating(1.0)
                    .build());
        }

        return ClanPerformanceModel.builder()
                .clanTag(tag)
                .name(name)
                .clanLevel(20)
                .totalMembersInWar(size)
                .attackParticipationRate(1.0)
                .avgStarsPerWar(size * 3.0)
                .avgDestructionPerWar(100.0)
                .players(players)
                .build();
    }

    private ClanPerformanceModel createWeakClan(String name, String tag, int size)
    {
        List<PlayerPerformanceModel> players = new ArrayList<>();
        for (int i = 1; i <= size; i++)
        {
            Map<Integer, MatchupStarProbability> matchups = new HashMap<>();
            for (int diff = -2; diff <= 2; diff++)
            {
                matchups.put(diff, MatchupStarProbability.builder()
                        .thDifference(diff)
                        .prob0Star(0.10)
                        .prob1Star(0.40)
                        .prob2Star(0.45)
                        .prob3Star(0.05)
                        .expectedDestruction(65.0)
                        .sampleCount(10)
                        .build());
            }

            players.add(PlayerPerformanceModel.builder()
                    .playerTag(tag + "_P" + i)
                    .name("Weak Player " + i)
                    .townHallLevel(14)
                    .mapPosition(i)
                    .attacksMade(0)
                    .attacksRemaining(2)
                    .currentBestOpponentStars(0)
                    .currentBestOpponentDestruction(0.0)
                    .matchupProbabilities(matchups)
                    .defenseRating(0.8)
                    .build());
        }

        return ClanPerformanceModel.builder()
                .clanTag(tag)
                .name(name)
                .clanLevel(5)
                .totalMembersInWar(size)
                .attackParticipationRate(0.80)
                .avgStarsPerWar(size * 1.5)
                .avgDestructionPerWar(65.0)
                .players(players)
                .build();
    }
}
