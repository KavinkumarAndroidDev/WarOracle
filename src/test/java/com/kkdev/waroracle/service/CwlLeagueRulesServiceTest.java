package com.kkdev.waroracle.service;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kkdev.waroracle.dto.clan.CurrentWar;
import com.kkdev.waroracle.dto.clan.WarClan;
import com.kkdev.waroracle.dto.cwl.ClanWarLeagueClanDto;
import com.kkdev.waroracle.dto.cwl.ClanWarLeagueGroupResponse;
import com.kkdev.waroracle.dto.cwl.CwlClanStanding;
import com.kkdev.waroracle.dto.cwl.CwlLeagueTier;
import com.kkdev.waroracle.service.cwl.CwlLeagueRulesService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CwlLeagueRulesServiceTest
{

	private CwlLeagueRulesService rulesService;

	@BeforeEach
	void setUp()
	{
		rulesService = new CwlLeagueRulesService();
	}

	@Test
	@DisplayName("Should return correct promotion and demotion counts for Bronze III to Legend")
	void shouldReturnCorrectPromotionAndDemotionCounts()
	{
		// Bronze III: 3 promote, 0 demote
		assertEquals(3, rulesService.getPromotedCount(CwlLeagueTier.BRONZE_III, 8));
		assertEquals(0, rulesService.getDemotedCount(CwlLeagueTier.BRONZE_III, 8));

		// Crystal I: 2 promote, 2 demote
		assertEquals(2, rulesService.getPromotedCount(CwlLeagueTier.CRYSTAL_I, 8));
		assertEquals(2, rulesService.getDemotedCount(CwlLeagueTier.CRYSTAL_I, 8));

		// Master I: 1 promote, 2 demote
		assertEquals(1, rulesService.getPromotedCount(CwlLeagueTier.MASTER_I, 8));
		assertEquals(2, rulesService.getDemotedCount(CwlLeagueTier.MASTER_I, 8));

		// Legend: 0 promote, 2 demote
		assertEquals(0, rulesService.getPromotedCount(CwlLeagueTier.LEGEND, 8));
		assertEquals(2, rulesService.getDemotedCount(CwlLeagueTier.LEGEND, 8));
	}

	@Test
	@DisplayName("Should disable demotion and promote top 2 in 6-clan group (February rule)")
	void shouldHandle6ClanGroupFallbackRule()
	{
		assertEquals(2, rulesService.getPromotedCount(CwlLeagueTier.CRYSTAL_I, 6));
		assertEquals(0, rulesService.getDemotedCount(CwlLeagueTier.CRYSTAL_I, 6));
	}

	@Test
	@DisplayName("Should calculate individual medals using 20% + 10% per star formula")
	void shouldCalculateIndividualMedalsAccurately()
	{
		int clanPlacementMedals = 300;

		// 0 stars -> 20% (60)
		assertEquals(60, rulesService.calculateIndividualMedals(clanPlacementMedals, 0));

		// 1 star -> 30% (90)
		assertEquals(90, rulesService.calculateIndividualMedals(clanPlacementMedals, 1));

		// 5 stars -> 70% (210)
		assertEquals(210, rulesService.calculateIndividualMedals(clanPlacementMedals, 5));

		// 8 stars -> 100% (300)
		assertEquals(300, rulesService.calculateIndividualMedals(clanPlacementMedals, 8));

		// 15 stars -> max 100% (300)
		assertEquals(300, rulesService.calculateIndividualMedals(clanPlacementMedals, 15));
	}

	@Test
	@DisplayName("Should calculate standings correctly with +10 bonus stars per win and destruction tie-breaker")
	void shouldCalculateStandingsWithBonusStarsAndTieBreaker()
	{
		ClanWarLeagueClanDto clan1 = ClanWarLeagueClanDto.builder().tag("#CLAN1").name("Clan One").build();
		ClanWarLeagueClanDto clan2 = ClanWarLeagueClanDto.builder().tag("#CLAN2").name("Clan Two").build();

		ClanWarLeagueGroupResponse group = ClanWarLeagueGroupResponse.builder()
				.clans(List.of(clan1, clan2))
				.build();

		CurrentWar war = new CurrentWar();
		war.setState("warEnded");
		WarClan wc1 = new WarClan();
		wc1.setTag("#CLAN1");
		wc1.setStars(40);
		wc1.setDestructionPercentage(95.0);

		WarClan wc2 = new WarClan();
		wc2.setTag("#CLAN2");
		wc2.setStars(40);
		wc2.setDestructionPercentage(90.0);

		war.setClan(wc1);
		war.setOpponent(wc2);

		List<CwlClanStanding> standings = rulesService.calculateStandings(group, List.of(war), CwlLeagueTier.CRYSTAL_I);

		assertEquals(2, standings.size());
		// Clan 1 won due to destruction tie-breaker (95% > 90%)
		assertEquals("#CLAN1", standings.get(0).getTag());
		assertEquals(1, standings.get(0).getRank());
		assertEquals(1, standings.get(0).getWarsWon());
		assertEquals(10, standings.get(0).getBonusStars());
		assertEquals(50, standings.get(0).getTotalStars()); // 40 attack + 10 bonus
		assertTrue(standings.get(0).isPromotionZone());

		assertEquals("#CLAN2", standings.get(1).getTag());
		assertEquals(2, standings.get(1).getRank());
		assertEquals(0, standings.get(1).getWarsWon());
		assertEquals(0, standings.get(1).getBonusStars());
		assertEquals(40, standings.get(1).getTotalStars());
	}
}
