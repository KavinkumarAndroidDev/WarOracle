package com.kkdev.waroracle.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kkdev.waroracle.dto.clan.WarClan;
import com.kkdev.waroracle.dto.clan.WarMember;
import com.kkdev.waroracle.dto.model.ClanPerformanceModel;
import com.kkdev.waroracle.dto.warlog.ClanWarLogClan;
import com.kkdev.waroracle.dto.warlog.ClanWarLogItem;
import com.kkdev.waroracle.dto.warlog.ClanWarLogResponse;

class PerformanceModelingServiceTest
{

    private PerformanceModelingService modelingService;
    private EmpiricalPriorCalibrationService priorCalibrationService;
    private HeroEquipmentPowerCalculator equipmentPowerCalculator;

    @BeforeEach
    void setUp()
    {
        priorCalibrationService = mock(EmpiricalPriorCalibrationService.class);
        equipmentPowerCalculator = mock(HeroEquipmentPowerCalculator.class);

        when(priorCalibrationService.getPriorStarProbabilities(anyInt())).thenReturn(new double[]{0.05, 0.15, 0.50, 0.30});
        when(priorCalibrationService.getPriorDestruction(anyInt())).thenReturn(85.0);
        when(equipmentPowerCalculator.calculateOffensiveMultiplier(null)).thenReturn(1.0);

        modelingService = new PerformanceModelingService(priorCalibrationService, equipmentPowerCalculator);
    }

    @Test
    void testEliteClanTierMultiplierFromWarLog()
    {
        WarClan warClan = new WarClan();
        warClan.setTag("#ELITE123");
        warClan.setName("Elite Warriors");
        warClan.setClanLevel(20);

        WarMember member = new WarMember();
        member.setTag("#P1");
        member.setName("Leader");
        member.setTownhallLevel(16);
        member.setMapPosition(1);
        warClan.setMembers(Collections.singletonList(member));

        // Create war log with 10 consecutive wins and 99.5% average destruction
        ClanWarLogResponse warLog = new ClanWarLogResponse();
        List<ClanWarLogItem> items = new ArrayList<>();
        for (int i = 0; i < 10; i++)
        {
            ClanWarLogItem item = new ClanWarLogItem();
            item.setResult("win");
            item.setTeamSize(10);
            item.setAttacksPerMember(2);

            ClanWarLogClan clan = new ClanWarLogClan();
            clan.setAttacks(20);
            clan.setStars(30);
            clan.setDestructionPercentage(99.5);
            item.setClan(clan);

            items.add(item);
        }
        warLog.setItems(items);

        ClanPerformanceModel model = modelingService.buildClanPerformanceModel(
                warClan,
                warLog,
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                2);

        assertNotNull(model);
        assertEquals(10, model.getEstimatedConsecutiveWins());
        assertTrue(model.getAvgDestructionPerWar() >= 99.0);
        assertTrue(model.getClanTierMultiplier() >= 1.40,
                "Clan tier multiplier should be >= 1.40 for high destruction + win streak");
    }

    @Test
    void testClanPerformanceModelWithNullWarLog()
    {
        WarClan warClan = new WarClan();
        warClan.setTag("#CASUAL123");
        warClan.setName("Casual Clan");
        warClan.setClanLevel(5);
        warClan.setMembers(Collections.emptyList());

        ClanPerformanceModel model = modelingService.buildClanPerformanceModel(
                warClan,
                null,
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                2);

        assertNotNull(model);
        assertEquals(0, model.getEstimatedConsecutiveWins());
        assertEquals(1.0, model.getClanTierMultiplier());
    }
}
