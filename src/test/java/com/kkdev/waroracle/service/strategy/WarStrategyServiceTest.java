package com.kkdev.waroracle.service.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kkdev.waroracle.dto.common.ErrorCodes;
import com.kkdev.waroracle.dto.model.ClanPerformanceModel;
import com.kkdev.waroracle.dto.model.MatchupStarProbability;
import com.kkdev.waroracle.dto.model.PlayerPerformanceModel;
import com.kkdev.waroracle.dto.model.WarPerformanceModel;
import com.kkdev.waroracle.dto.strategy.PlayerWarAssignment;
import com.kkdev.waroracle.dto.strategy.StrategyMode;
import com.kkdev.waroracle.dto.strategy.WarStrategyPlan;
import com.kkdev.waroracle.dto.strategy.WarStrategyRequest;
import com.kkdev.waroracle.exception.ClashApiException;
import com.kkdev.waroracle.service.WarOracleService;

class WarStrategyServiceTest
{

    private WarStrategyService warStrategyService;
    private WarOracleService warOracleService;
    private KuhnBipartiteOptimizer bipartiteOptimizer;

    @BeforeEach
    void setUp()
    {
        warOracleService = mock(WarOracleService.class);
        bipartiteOptimizer = new KuhnBipartiteOptimizer();
        warStrategyService = new WarStrategyServiceImpl(warOracleService, bipartiteOptimizer);
    }

    @Test
    void testGenerateOptimalWarStrategySuccess()
    {
        ClanPerformanceModel homeClan = createTestClan("HOME", "#HOME", 5);
        ClanPerformanceModel oppClan = createTestClan("OPPONENT", "#OPP", 5);

        WarPerformanceModel warModel = WarPerformanceModel.builder()
                .warState("inWar")
                .teamSize(5)
                .attacksPerMember(2)
                .homeClan(homeClan)
                .opponentClan(oppClan)
                .build();

        when(warOracleService.buildPerformanceModel(any())).thenReturn(warModel);

        WarStrategyRequest request = WarStrategyRequest.builder()
                .clanTag("#HOME")
                .strategyMode(StrategyMode.BALANCED)
                .build();

        WarStrategyPlan plan = warStrategyService.generateOptimalWarStrategy(request);

        assertNotNull(plan);
        assertEquals("#HOME", plan.getClanTag());
        assertEquals("#OPP", plan.getOpponentTag());
        assertEquals(StrategyMode.BALANCED, plan.getStrategyMode());
        assertTrue(plan.getProjectedTotalStars() > 0.0);
        assertNotNull(plan.getClanLevelSummary());
        assertEquals(5, plan.getClanLevelSummary().getTotalMembers());
        assertNotNull(plan.getClanLevelSummary().getMostVulnerableEnemyBase());
        assertNotNull(plan.getClanLevelSummary().getBiggestEnemyThreat());

        List<PlayerWarAssignment> assignments = plan.getAssignments();
        assertNotNull(assignments);
        assertEquals(5, assignments.size());

        for (PlayerWarAssignment assignment : assignments)
        {
            assertNotNull(assignment.getAttackerTag());
            assertNotNull(assignment.getStrategicRole());
            assertNotNull(assignment.getPrimaryTarget());
            assertTrue(assignment.getPrimaryTarget().getExpectedStars() > 0.0);
            assertNotNull(assignment.getContingencyCleanup());
            assertNotNull(assignment.getContingencyCleanup().getInstructions());
        }
    }

    @Test
    void testGenerateOptimalWarStrategyMidWarProgression()
    {
        ClanPerformanceModel homeClan = createTestClan("HOME", "#HOME", 5);
        ClanPerformanceModel oppClan = createTestClan("OPPONENT", "#OPP", 5);

        // Mid-war state: Home Player 1 completed both attacks
        homeClan.getPlayers().get(0).setAttacksMade(2);
        homeClan.getPlayers().get(0).setAttacksRemaining(0);

        // Mid-war state: Opponent Base 1 is already 3-starred
        oppClan.getPlayers().get(0).setCurrentBestOpponentStars(3);
        oppClan.getPlayers().get(0).setCurrentBestOpponentDestruction(100.0);

        WarPerformanceModel warModel = WarPerformanceModel.builder()
                .warState("inWar")
                .teamSize(5)
                .attacksPerMember(2)
                .homeClan(homeClan)
                .opponentClan(oppClan)
                .build();

        when(warOracleService.buildPerformanceModel(any())).thenReturn(warModel);

        WarStrategyRequest request = WarStrategyRequest.builder()
                .clanTag("#HOME")
                .strategyMode(StrategyMode.BALANCED)
                .build();

        WarStrategyPlan plan = warStrategyService.generateOptimalWarStrategy(request);

        assertNotNull(plan);
        assertEquals(3, plan.getClanLevelSummary().getCurrentHomeStars());
        assertEquals(1, plan.getClanLevelSummary().getEnemyBasesCleared());
        assertEquals(4, plan.getClanLevelSummary().getEnemyBasesRemaining());

        // Player 1 (0 attacks left) should be marked COMPLETED
        PlayerWarAssignment completedPlayer = plan.getAssignments().get(0);
        assertEquals("COMPLETED", completedPlayer.getAssignmentStatus());
        assertNull(completedPlayer.getPrimaryTarget());

        // Player 2 (active) should be assigned an uncleared base (NOT base 1)
        PlayerWarAssignment activePlayer = plan.getAssignments().get(1);
        assertEquals("ASSIGNED", activePlayer.getAssignmentStatus());
        assertNotNull(activePlayer.getPrimaryTarget());
        assertTrue(activePlayer.getPrimaryTarget().getDefenderMapPosition() > 1,
                "Active player should be assigned to an uncleared base > 1");
    }

    @Test
    void testGenerateOptimalWarStrategyAggressiveMode()
    {
        ClanPerformanceModel homeClan = createTestClan("HOME", "#HOME", 5);
        ClanPerformanceModel oppClan = createTestClan("OPPONENT", "#OPP", 5);

        WarPerformanceModel warModel = WarPerformanceModel.builder()
                .warState("inWar")
                .teamSize(5)
                .attacksPerMember(2)
                .homeClan(homeClan)
                .opponentClan(oppClan)
                .build();

        when(warOracleService.buildPerformanceModel(any())).thenReturn(warModel);

        WarStrategyRequest request = WarStrategyRequest.builder()
                .clanTag("#HOME")
                .strategyMode(StrategyMode.AGGRESSIVE_3STAR)
                .build();

        WarStrategyPlan plan = warStrategyService.generateOptimalWarStrategy(request);

        assertNotNull(plan);
        assertEquals(StrategyMode.AGGRESSIVE_3STAR, plan.getStrategyMode());
        assertEquals(5, plan.getAssignments().size());
    }

    @Test
    void testGenerateOptimalWarStrategyThrowsOnInvalidTag()
    {
        WarStrategyRequest request = WarStrategyRequest.builder()
                .clanTag("")
                .strategyMode(StrategyMode.BALANCED)
                .build();

        ClashApiException ex = assertThrows(ClashApiException.class,
                () -> warStrategyService.generateOptimalWarStrategy(request));
        assertEquals(ErrorCodes.CLASH_API_BAD_REQUEST, ex.getErrorCode());
    }

    private ClanPerformanceModel createTestClan(String name, String tag, int size)
    {
        List<PlayerPerformanceModel> players = new ArrayList<>();
        for (int i = 1; i <= size; i++)
        {
            Map<Integer, MatchupStarProbability> matchups = new HashMap<>();
            for (int diff = -2; diff <= 2; diff++)
            {
                matchups.put(diff, MatchupStarProbability.builder()
                        .thDifference(diff)
                        .prob0Star(0.05)
                        .prob1Star(0.15)
                        .prob2Star(0.40)
                        .prob3Star(0.40)
                        .expectedDestruction(85.0)
                        .sampleCount(15)
                        .build());
            }

            players.add(PlayerPerformanceModel.builder()
                    .playerTag(tag + "_P" + i)
                    .name("Member " + i)
                    .townHallLevel(16 - (i / 3))
                    .mapPosition(i)
                    .attacksMade(0)
                    .attacksRemaining(2)
                    .matchupProbabilities(matchups)
                    .defenseRating(1.0)
                    .build());
        }

        return ClanPerformanceModel.builder()
                .clanTag(tag)
                .name(name)
                .clanLevel(15)
                .totalMembersInWar(size)
                .players(players)
                .build();
    }
}
