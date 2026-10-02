package com.kkdev.waroracle.service.cwl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.kkdev.waroracle.dto.clan.CurrentWar;
import com.kkdev.waroracle.dto.clan.WarClan;
import com.kkdev.waroracle.dto.cwl.ClanWarLeagueClanDto;
import com.kkdev.waroracle.dto.cwl.ClanWarLeagueGroupResponse;
import com.kkdev.waroracle.dto.cwl.CwlClanStanding;
import com.kkdev.waroracle.dto.cwl.CwlLeagueTier;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CwlLeagueRulesService
{

	private static final int BONUS_STARS_PER_WIN = 10;

	public int getPromotedCount(CwlLeagueTier tier, int totalClans)
	{
		if (tier == null || tier == CwlLeagueTier.UNRANKED)
		{
			return 2;
		}
		if (totalClans <= 6)
		{
			// 6-clan group edge-case / February rule: Top 2 promote
			return Math.min(2, totalClans);
		}
		return tier.getPromotedCountStandard();
	}

	public int getDemotedCount(CwlLeagueTier tier, int totalClans)
	{
		if (tier == null || tier == CwlLeagueTier.UNRANKED)
		{
			return 2;
		}
		if (totalClans <= 6)
		{
			// 6-clan group edge-case / February rule: Demotion is completely disabled
			return 0;
		}
		return tier.getDemotedCountStandard();
	}

	public int calculateClanPlacementMedals(CwlLeagueTier tier, int rank, int totalClans)
	{
		if (tier == null || tier == CwlLeagueTier.UNRANKED)
		{
			return 0;
		}

		int firstPlaceMedals = tier.getFirstPlaceMedals();
		if (rank <= 1)
		{
			return firstPlaceMedals;
		}

		// Supercell CWL medal placement curve: drops by fixed steps per rank
		int medalStep = Math.max(5, firstPlaceMedals / (totalClans > 0 ? totalClans + 2 : 10));
		int medals = firstPlaceMedals - ((rank - 1) * medalStep);
		return Math.max(20, medals);
	}

	public int calculateIndividualMedals(int clanPlacementMedals, int starsScored)
	{
		// Formula from Section 4.2 of specification:
		// Medal Percentage = min(100, 20 + (Stars Scored * 10))%
		double percentage = Math.min(100.0, 20.0 + (starsScored * 10.0)) / 100.0;
		return (int) Math.round(clanPlacementMedals * percentage);
	}

	public int calculateBonusMedalBatches(int baseBatches, int warsWon)
	{
		// Base bonus packs allocated by league tier + 1 extra pack per war won
		return Math.max(0, baseBatches) + Math.max(0, warsWon);
	}

	public List<CwlClanStanding> calculateStandings(
			ClanWarLeagueGroupResponse groupResponse,
			List<CurrentWar> finishedOrActiveWars,
			CwlLeagueTier tier)
	{
		if (groupResponse == null || groupResponse.getClans() == null)
		{
			return Collections.emptyList();
		}

		int totalClans = groupResponse.getClans().size();
		int promotedCount = getPromotedCount(tier, totalClans);
		int demotedCount = getDemotedCount(tier, totalClans);

		Map<String, CwlClanStanding> standingsMap = new HashMap<>();

		for (ClanWarLeagueClanDto clanDto : groupResponse.getClans())
		{
			standingsMap.put(clanDto.getTag(), CwlClanStanding.builder()
					.tag(clanDto.getTag())
					.name(clanDto.getName())
					.clanLevel(clanDto.getClanLevel())
					.badgeUrls(clanDto.getBadgeUrls())
					.warsPlayed(0)
					.warsWon(0)
					.warsLost(0)
					.warsTied(0)
					.attackStars(0)
					.bonusStars(0)
					.totalStars(0)
					.destructionPercentage(0.0)
					.estimatedMedals(0)
					.promotionZone(false)
					.demotionZone(false)
					.build());
		}

		if (finishedOrActiveWars != null)
		{
			for (CurrentWar war : finishedOrActiveWars)
			{
				if (war == null || war.getClan() == null || war.getOpponent() == null)
				{
					continue;
				}

				WarClan clanA = war.getClan();
				WarClan clanB = war.getOpponent();

				CwlClanStanding standingA = standingsMap.get(clanA.getTag());
				CwlClanStanding standingB = standingsMap.get(clanB.getTag());

				if (standingA == null || standingB == null)
				{
					continue;
				}

				standingA.setWarsPlayed(standingA.getWarsPlayed() + 1);
				standingB.setWarsPlayed(standingB.getWarsPlayed() + 1);

				standingA.setAttackStars(standingA.getAttackStars() + clanA.getStars());
				standingB.setAttackStars(standingB.getAttackStars() + clanB.getStars());

				// Accumulate total destruction across rounds
				standingA.setDestructionPercentage(standingA.getDestructionPercentage() + clanA.getDestructionPercentage());
				standingB.setDestructionPercentage(standingB.getDestructionPercentage() + clanB.getDestructionPercentage());

				if ("warEnded".equalsIgnoreCase(war.getState()))
				{
					if (clanA.getStars() > clanB.getStars())
					{
						standingA.setWarsWon(standingA.getWarsWon() + 1);
						standingB.setWarsLost(standingB.getWarsLost() + 1);
					}
					else if (clanA.getStars() < clanB.getStars())
					{
						standingB.setWarsWon(standingB.getWarsWon() + 1);
						standingA.setWarsLost(standingA.getWarsLost() + 1);
					}
					else
					{
						if (clanA.getDestructionPercentage() > clanB.getDestructionPercentage())
						{
							standingA.setWarsWon(standingA.getWarsWon() + 1);
							standingB.setWarsLost(standingB.getWarsLost() + 1);
						}
						else if (clanA.getDestructionPercentage() < clanB.getDestructionPercentage())
						{
							standingB.setWarsWon(standingB.getWarsWon() + 1);
							standingA.setWarsLost(standingA.getWarsLost() + 1);
						}
						else
						{
							standingA.setWarsTied(standingA.getWarsTied() + 1);
							standingB.setWarsTied(standingB.getWarsTied() + 1);
						}
					}
				}
			}
		}

		List<CwlClanStanding> sortedStandings = new ArrayList<>(standingsMap.values());

		// Compute average destruction percentage and total stars (attackStars + 10 * wins)
		for (CwlClanStanding s : sortedStandings)
		{
			int bonus = s.getWarsWon() * BONUS_STARS_PER_WIN;
			s.setBonusStars(bonus);
			s.setTotalStars(s.getAttackStars() + bonus);
			if (s.getWarsPlayed() > 0)
			{
				double avgDest = s.getDestructionPercentage() / s.getWarsPlayed();
				s.setDestructionPercentage(Math.round(avgDest * 100.0) / 100.0);
			}
		}

		// Sort by: Total Stars DESC, then Destruction % DESC, then Attack Stars DESC
		sortedStandings.sort(Comparator
				.comparingInt(CwlClanStanding::getTotalStars).reversed()
				.thenComparing(Comparator.comparingDouble(CwlClanStanding::getDestructionPercentage).reversed())
				.thenComparing(Comparator.comparingInt(CwlClanStanding::getAttackStars).reversed()));

		// Assign ranks and promotion / demotion flags
		for (int i = 0; i < sortedStandings.size(); i++)
		{
			int rank = i + 1;
			CwlClanStanding s = sortedStandings.get(i);
			s.setRank(rank);

			if (rank <= promotedCount && promotedCount > 0)
			{
				s.setPromotionZone(true);
			}
			if (rank > (totalClans - demotedCount) && demotedCount > 0)
			{
				s.setDemotionZone(true);
			}

			int placementMedals = calculateClanPlacementMedals(tier, rank, totalClans);
			s.setEstimatedMedals(placementMedals);
		}

		return sortedStandings;
	}
}
