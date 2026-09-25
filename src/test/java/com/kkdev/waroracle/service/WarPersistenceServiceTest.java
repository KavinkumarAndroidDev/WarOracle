package com.kkdev.waroracle.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kkdev.waroracle.dto.clan.CurrentWar;
import com.kkdev.waroracle.dto.clan.WarAttack;
import com.kkdev.waroracle.dto.clan.WarClan;
import com.kkdev.waroracle.dto.clan.WarMember;
import com.kkdev.waroracle.entity.ClanWarEntity;
import com.kkdev.waroracle.entity.WarAttackEntity;
import com.kkdev.waroracle.repository.ClanRepository;
import com.kkdev.waroracle.repository.ClanWarRepository;
import com.kkdev.waroracle.repository.PlayerSnapshotRepository;
import com.kkdev.waroracle.repository.SimulationRunRepository;
import com.kkdev.waroracle.repository.WarAttackRepository;

import tools.jackson.databind.json.JsonMapper;

class WarPersistenceServiceTest
{

    private WarPersistenceService warPersistenceService;
    private ClanRepository clanRepository;
    private ClanWarRepository clanWarRepository;
    private WarAttackRepository warAttackRepository;
    private PlayerSnapshotRepository playerSnapshotRepository;
    private SimulationRunRepository simulationRunRepository;
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp()
    {
        clanRepository = mock(ClanRepository.class);
        clanWarRepository = mock(ClanWarRepository.class);
        warAttackRepository = mock(WarAttackRepository.class);
        playerSnapshotRepository = mock(PlayerSnapshotRepository.class);
        simulationRunRepository = mock(SimulationRunRepository.class);
        jsonMapper = JsonMapper.builder().build();

        warPersistenceService = new WarPersistenceService(
                clanRepository,
                clanWarRepository,
                warAttackRepository,
                playerSnapshotRepository,
                simulationRunRepository,
                jsonMapper);
    }

    @Test
    void testPersistClanAndWarInsertsNewAttacksIncrementally()
    {
        CurrentWar war = createSampleWar(2);
        String expectedWarId = warPersistenceService.generateWarId("#HOME", "#OPP", "20260925T180000.000Z");

        when(clanWarRepository.findById(expectedWarId)).thenReturn(Optional.empty());
        when(warAttackRepository.findByWarId(expectedWarId)).thenReturn(Collections.emptyList());

        String warId = warPersistenceService.persistClanAndWar(war);

        assertNotNull(warId);
        assertEquals(expectedWarId, warId);
        verify(clanWarRepository, times(1)).save(any(ClanWarEntity.class));
        verify(warAttackRepository, never()).deleteByWarId(any());
        verify(warAttackRepository, times(1)).saveAll(anyList());
    }

    @Test
    void testPersistClanAndWarSkipsExistingAttacksWithoutDeleting()
    {
        CurrentWar war = createSampleWar(1);
        String expectedWarId = warPersistenceService.generateWarId("#HOME", "#OPP", "20260925T180000.000Z");

        ClanWarEntity existingWar = ClanWarEntity.builder()
                .warId(expectedWarId)
                .clanTag("#HOME")
                .opponentTag("#OPP")
                .clanStars(3)
                .clanDestruction(BigDecimal.valueOf(100.0))
                .build();

        WarAttackEntity existingAttack = WarAttackEntity.builder()
                .warId(expectedWarId)
                .attackerTag("#P1")
                .defenderTag("#E1")
                .attackOrder(1)
                .stars(3)
                .destructionPercentage(BigDecimal.valueOf(100.0))
                .build();

        when(clanWarRepository.findById(expectedWarId)).thenReturn(Optional.of(existingWar));
        when(warAttackRepository.findByWarId(expectedWarId)).thenReturn(Collections.singletonList(existingAttack));

        String warId = warPersistenceService.persistClanAndWar(war);

        assertNotNull(warId);
        verify(clanWarRepository, times(1)).save(existingWar);
        verify(warAttackRepository, never()).deleteByWarId(any());
        // No new attacks to insert since attack 1 is already in DB
        verify(warAttackRepository, never()).saveAll(anyList());
    }

    private CurrentWar createSampleWar(int attackCount)
    {
        CurrentWar war = new CurrentWar();
        war.setState("inWar");
        war.setTeamSize(10);
        war.setAttacksPerMember(2);
        war.setPreparationStartTime("20260925T180000.000Z");
        war.setStartTime("20260926T180000.000Z");
        war.setEndTime("20260927T180000.000Z");

        WarClan homeClan = new WarClan();
        homeClan.setTag("#HOME");
        homeClan.setName("Home Clan");
        homeClan.setClanLevel(15);
        homeClan.setStars(3);
        homeClan.setDestructionPercentage(100.0);

        List<WarMember> homeMembers = new ArrayList<>();
        WarMember m1 = new WarMember();
        m1.setTag("#P1");
        m1.setName("Leader");
        m1.setTownhallLevel(16);
        m1.setMapPosition(1);

        List<WarAttack> attacks = new ArrayList<>();
        for (int i = 1; i <= attackCount; i++)
        {
            attacks.add(WarAttack.builder()
                    .attackerTag("#P1")
                    .defenderTag("#E" + i)
                    .stars(3)
                    .destructionPercentage(100)
                    .order(i)
                    .duration(120)
                    .build());
        }
        m1.setAttacks(attacks);
        homeMembers.add(m1);
        homeClan.setMembers(homeMembers);

        WarClan oppClan = new WarClan();
        oppClan.setTag("#OPP");
        oppClan.setName("Opponent Clan");
        oppClan.setClanLevel(14);
        oppClan.setStars(0);
        oppClan.setDestructionPercentage(0.0);
        oppClan.setMembers(Collections.emptyList());

        war.setClan(homeClan);
        war.setOpponent(oppClan);
        return war;
    }
}
