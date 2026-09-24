package com.kkdev.waroracle.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kkdev.waroracle.dto.clan.CurrentWar;
import com.kkdev.waroracle.dto.common.ErrorCodes;
import com.kkdev.waroracle.dto.simulation.SimulationQuality;
import com.kkdev.waroracle.dto.simulation.SimulationRequest;
import com.kkdev.waroracle.exception.ClashApiException;
import com.kkdev.waroracle.repository.WarAttackRepository;

class WarOracleServiceTest
{

    private WarOracleService warOracleService;
    private ClanService clanService;
    private PlayerService playerService;
    private PerformanceModelingService performanceModelingService;
    private MonteCarloSimulationService monteCarloSimulationService;
    private WarPersistenceService warPersistenceService;
    private WarAttackRepository warAttackRepository;

    @BeforeEach
    void setUp()
    {
        playerService = mock(PlayerService.class);
        clanService = mock(ClanService.class);
        performanceModelingService = mock(PerformanceModelingService.class);
        monteCarloSimulationService = mock(MonteCarloSimulationService.class);
        warPersistenceService = mock(WarPersistenceService.class);
        warAttackRepository = mock(WarAttackRepository.class);

        warOracleService = new WarOracleService(
                playerService,
                clanService,
                performanceModelingService,
                monteCarloSimulationService,
                warPersistenceService,
                warAttackRepository);
    }

    @Test
    void testSimulateWarThrowsWhenWarEnded()
    {
        CurrentWar endedWar = new CurrentWar();
        endedWar.setState("warEnded");

        when(clanService.getCurrentWar("#CLAN123")).thenReturn(endedWar);

        SimulationRequest request = new SimulationRequest();
        request.setClanTag("#CLAN123");
        request.setQuality(SimulationQuality.MEDIUM);

        ClashApiException ex = assertThrows(ClashApiException.class, () -> warOracleService.simulateWar(request));
        assertEquals(ErrorCodes.WAR_ALREADY_ENDED, ex.getErrorCode());
    }

    @Test
    void testSimulateWarThrowsWhenNotInWar()
    {
        CurrentWar notInWar = new CurrentWar();
        notInWar.setState("notInWar");

        when(clanService.getCurrentWar("#CLAN123")).thenReturn(notInWar);

        SimulationRequest request = new SimulationRequest();
        request.setClanTag("#CLAN123");
        request.setQuality(SimulationQuality.MEDIUM);

        ClashApiException ex = assertThrows(ClashApiException.class, () -> warOracleService.simulateWar(request));
        assertEquals(ErrorCodes.CLAN_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void testBuildPerformanceModelThrowsWhenWarEnded()
    {
        CurrentWar endedWar = new CurrentWar();
        endedWar.setState("warEnded");

        when(clanService.getCurrentWar("#CLAN123")).thenReturn(endedWar);

        SimulationRequest request = new SimulationRequest();
        request.setClanTag("#CLAN123");
        request.setQuality(SimulationQuality.MEDIUM);

        ClashApiException ex = assertThrows(ClashApiException.class, () -> warOracleService.buildPerformanceModel(request));
        assertEquals(ErrorCodes.WAR_ALREADY_ENDED, ex.getErrorCode());
    }
}
