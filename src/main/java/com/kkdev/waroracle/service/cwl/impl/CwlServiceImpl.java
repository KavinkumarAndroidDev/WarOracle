package com.kkdev.waroracle.service.cwl.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.kkdev.waroracle.client.ClashApiClient;
import com.kkdev.waroracle.dto.clan.Clan;
import com.kkdev.waroracle.dto.clan.CurrentWar;
import com.kkdev.waroracle.dto.clan.WarClan;
import com.kkdev.waroracle.dto.clan.WarMember;
import com.kkdev.waroracle.dto.common.ErrorCodes;
import com.kkdev.waroracle.dto.cwl.ClanWarLeagueClanDto;
import com.kkdev.waroracle.dto.cwl.ClanWarLeagueGroupResponse;
import com.kkdev.waroracle.dto.cwl.ClanWarLeagueMemberDto;
import com.kkdev.waroracle.dto.cwl.ClanWarLeagueRoundDto;
import com.kkdev.waroracle.dto.cwl.CwlClanStanding;
import com.kkdev.waroracle.dto.cwl.CwlGroupOverviewDto;
import com.kkdev.waroracle.dto.cwl.CwlLeagueTier;
import com.kkdev.waroracle.dto.cwl.CwlRoundSimulationRequest;
import com.kkdev.waroracle.dto.cwl.CwlSeasonSimulationRequest;
import com.kkdev.waroracle.dto.cwl.CwlSeasonSimulationResult;
import com.kkdev.waroracle.dto.cwl.CwlStrategyPlan;
import com.kkdev.waroracle.dto.cwl.CwlStrategyRequest;
import com.kkdev.waroracle.dto.cwl.DifficultyModifierDetail;
import com.kkdev.waroracle.dto.model.ClanPerformanceModel;
import com.kkdev.waroracle.dto.model.WarPerformanceModel;
import com.kkdev.waroracle.dto.simulation.SimulationQuality;
import com.kkdev.waroracle.dto.simulation.SimulationResult;
import com.kkdev.waroracle.dto.warlog.ClanWarLogResponse;
import com.kkdev.waroracle.exception.ClashApiException;
import com.kkdev.waroracle.service.ClanService;
import com.kkdev.waroracle.service.PerformanceModelingService;
import com.kkdev.waroracle.service.WarPersistenceService;
import com.kkdev.waroracle.service.cwl.CwlLeagueRulesService;
import com.kkdev.waroracle.service.cwl.CwlService;
import com.kkdev.waroracle.service.cwl.CwlSimulationEngine;
import com.kkdev.waroracle.service.cwl.CwlStrategyService;
import com.kkdev.waroracle.service.cwl.CwlTournamentSimulationService;
import com.kkdev.waroracle.service.cwl.DifficultyModifierCalculator;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CwlServiceImpl implements CwlService
{

	private final ClashApiClient clashApiClient;
	private final ClanService clanService;
	private final PerformanceModelingService performanceModelingService;
	private final CwlLeagueRulesService cwlLeagueRulesService;
	private final DifficultyModifierCalculator difficultyModifierCalculator;
	private final CwlSimulationEngine cwlSimulationEngine;
	private final CwlTournamentSimulationService cwlTournamentSimulationService;
	private final CwlStrategyService cwlStrategyService;
	private final WarPersistenceService warPersistenceService;

	public CwlServiceImpl(
			ClashApiClient clashApiClient,
			ClanService clanService,
			PerformanceModelingService performanceModelingService,
			CwlLeagueRulesService cwlLeagueRulesService,
			DifficultyModifierCalculator difficultyModifierCalculator,
			CwlSimulationEngine cwlSimulationEngine,
			CwlTournamentSimulationService cwlTournamentSimulationService,
			CwlStrategyService cwlStrategyService,
			WarPersistenceService warPersistenceService)
	{
		this.clashApiClient = clashApiClient;
		this.clanService = clanService;
		this.performanceModelingService = performanceModelingService;
		this.cwlLeagueRulesService = cwlLeagueRulesService;
		this.difficultyModifierCalculator = difficultyModifierCalculator;
		this.cwlSimulationEngine = cwlSimulationEngine;
		this.cwlTournamentSimulationService = cwlTournamentSimulationService;
		this.cwlStrategyService = cwlStrategyService;
		this.warPersistenceService = warPersistenceService;
	}

	@Override
	public CwlGroupOverviewDto getGroupOverview(String clanTag)
	{
		String normalizedTag = normalizeTag(clanTag);
		log.info("Fetching CWL group overview for clanTag: {}", normalizedTag);

		ClanWarLeagueGroupResponse group = clashApiClient.getClanWarLeagueGroup(normalizedTag);
		if (group == null || "notInWar".equalsIgnoreCase(group.getState()) || "groupNotFound".equalsIgnoreCase(group.getState()))
		{
			throw new ClashApiException(ErrorCodes.CWL_GROUP_NOT_FOUND);
		}

		Clan clan = null;
		try
		{
			clan = clanService.getClanDetails(normalizedTag);
		}
		catch (Exception e)
		{
			log.warn("Could not fetch clan details for tier resolution: {}", normalizedTag);
		}

		CwlLeagueTier tier = resolveTier(clan);
		DifficultyModifierDetail modifier = difficultyModifierCalculator.getModifierForTier(tier);

		List<CurrentWar> finishedOrActiveWars = fetchKnownWars(group);
		List<CwlClanStanding> standings = cwlLeagueRulesService.calculateStandings(group, finishedOrActiveWars, tier);

		int activeRoundNumber = 1;
		String activeWarTag = null;

		if (group.getRounds() != null)
		{
			for (int r = 0; r < group.getRounds().size(); r++)
			{
				ClanWarLeagueRoundDto round = group.getRounds().get(r);
				if (round.getWarTags() != null)
				{
					for (String wTag : round.getWarTags())
					{
						if (ClanWarLeagueRoundDto.isWarReady(wTag))
						{
							CurrentWar war = clashApiClient.getCwlWar(wTag);
							if (war != null && containsClan(war, normalizedTag))
							{
								if ("inWar".equalsIgnoreCase(war.getState()) || "preparation".equalsIgnoreCase(war.getState()))
								{
									activeRoundNumber = r + 1;
									activeWarTag = wTag;
									break;
								}
							}
						}
					}
				}
				if (activeWarTag != null) break;
			}
		}

		return CwlGroupOverviewDto.builder()
				.groupTag(group.getTag())
				.state(group.getState())
				.season(group.getSeason())
				.tier(tier)
				.totalClans(group.getClans() != null ? group.getClans().size() : 8)
				.totalRounds(group.getRounds() != null ? group.getRounds().size() : 7)
				.activeRoundNumber(activeRoundNumber)
				.activeWarTag(activeWarTag)
				.difficultyModifier(modifier)
				.standings(standings)
				.rounds(group.getRounds())
				.clans(group.getClans())
				.build();
	}

	@Override
	public CurrentWar getRoundWar(String warTag)
	{
		if (warTag == null || warTag.trim().isEmpty() || warTag.equals("#0"))
		{
			throw new ClashApiException(ErrorCodes.RESOURCE_NOT_FOUND);
		}
		CurrentWar war = clashApiClient.getCwlWar(warTag);
		if (war == null)
		{
			throw new ClashApiException(ErrorCodes.RESOURCE_NOT_FOUND);
		}
		return war;
	}

	@Override
	public SimulationResult simulateRound(CwlRoundSimulationRequest request)
	{
		String warTag = request != null ? request.getWarTag() : null;
		if (warTag == null || warTag.trim().isEmpty() || warTag.equals("#0"))
		{
			throw new ClashApiException(ErrorCodes.RESOURCE_NOT_FOUND);
		}

		CurrentWar war = clashApiClient.getCwlWar(warTag);
		if (war == null)
		{
			throw new ClashApiException(ErrorCodes.RESOURCE_NOT_FOUND);
		}

		if ("warEnded".equalsIgnoreCase(war.getState()))
		{
			throw new ClashApiException(ErrorCodes.WAR_ALREADY_ENDED);
		}

		SimulationQuality quality = (request != null && request.getQuality() != null)
				? request.getQuality() : SimulationQuality.MEDIUM;

		DifficultyModifierDetail modifier = difficultyModifierCalculator.getModifierByCode(war.getBattleModifier());

		ClanWarLogResponse homeWarLog = fetchSafeWarLog(war.getClan() != null ? war.getClan().getTag() : null);
		ClanWarLogResponse oppWarLog = fetchSafeWarLog(war.getOpponent() != null ? war.getOpponent().getTag() : null);

		ClanPerformanceModel homeClanModel = performanceModelingService.buildClanPerformanceModel(
				war.getClan(), homeWarLog, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), 1);
		ClanPerformanceModel oppClanModel = performanceModelingService.buildClanPerformanceModel(
				war.getOpponent(), oppWarLog, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), 1);

		WarPerformanceModel model = WarPerformanceModel.builder()
				.warState(war.getState())
				.teamSize(war.getTeamSize())
				.attacksPerMember(1)
				.homeClan(homeClanModel)
				.opponentClan(oppClanModel)
				.simulationQuality(quality)
				.iterations(quality.getIterations())
				.build();

		SimulationResult result = cwlSimulationEngine.simulateRound(model, modifier);
		result.setCurrentWar(war);

		String warId = warPersistenceService.persistClanAndWar(war);
		String homeTag = war.getClan() != null ? war.getClan().getTag() : null;
		String oppTag = war.getOpponent() != null ? war.getOpponent().getTag() : null;
		warPersistenceService.persistSimulationRun(result, warId, homeTag, oppTag, quality);

		return result;
	}

	@Override
	public CwlSeasonSimulationResult simulateSeason(CwlSeasonSimulationRequest request)
	{
		String normalizedTag = normalizeTag(request != null ? request.getClanTag() : null);
		if (normalizedTag == null)
		{
			throw new ClashApiException(ErrorCodes.CLAN_NOT_FOUND);
		}

		ClanWarLeagueGroupResponse group = clashApiClient.getClanWarLeagueGroup(normalizedTag);
		if (group == null || "notInWar".equalsIgnoreCase(group.getState()) || "groupNotFound".equalsIgnoreCase(group.getState()))
		{
			throw new ClashApiException(ErrorCodes.CWL_GROUP_NOT_FOUND);
		}

		Clan clan = null;
		try
		{
			clan = clanService.getClanDetails(normalizedTag);
		}
		catch (Exception e)
		{
			log.warn("Failed to fetch clan details for season tier resolution: {}", normalizedTag);
		}

		CwlLeagueTier tier = resolveTier(clan);
		DifficultyModifierDetail modifier = difficultyModifierCalculator.getModifierForTier(tier);
		List<CurrentWar> knownWars = fetchKnownWars(group);

		Map<String, ClanPerformanceModel> clanModels = new HashMap<>();
		if (group.getClans() != null)
		{
			for (ClanWarLeagueClanDto cDto : group.getClans())
			{
				ClanWarLogResponse warLog = fetchSafeWarLog(cDto.getTag());
				WarClan synthClan = new WarClan();
				synthClan.setTag(cDto.getTag());
				synthClan.setName(cDto.getName());
				synthClan.setClanLevel(cDto.getClanLevel());
				synthClan.setBadgeUrls(cDto.getBadgeUrls());
				if (cDto.getMembers() != null)
				{
					List<WarMember> members = new ArrayList<>();
					List<ClanWarLeagueMemberDto> sorted = new ArrayList<>(cDto.getMembers());
					sorted.sort((a, b) -> Integer.compare(b.getTownHallLevel(), a.getTownHallLevel()));
					for (int i = 0; i < sorted.size(); i++)
					{
						ClanWarLeagueMemberDto mDto = sorted.get(i);
						WarMember wm = new WarMember();
						wm.setTag(mDto.getTag());
						wm.setName(mDto.getName());
						wm.setTownhallLevel(mDto.getTownHallLevel());
						wm.setMapPosition(i + 1);
						members.add(wm);
					}
					synthClan.setMembers(members);
				}
				ClanPerformanceModel cModel = performanceModelingService.buildClanPerformanceModel(
						synthClan, warLog, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), 1);
				clanModels.put(cDto.getTag(), cModel);
			}
		}

		SimulationQuality quality = (request != null && request.getQuality() != null)
				? request.getQuality() : SimulationQuality.MEDIUM;

		return cwlTournamentSimulationService.simulateSeason(group, knownWars, clanModels, tier, quality, modifier);
	}

	@Override
	public CwlStrategyPlan generateStrategyPlan(CwlStrategyRequest request)
	{
		String warTag = request != null ? request.getWarTag() : null;
		if (warTag == null || warTag.trim().isEmpty() || warTag.equals("#0"))
		{
			throw new ClashApiException(ErrorCodes.RESOURCE_NOT_FOUND);
		}

		CurrentWar war = clashApiClient.getCwlWar(warTag);
		if (war == null)
		{
			throw new ClashApiException(ErrorCodes.RESOURCE_NOT_FOUND);
		}

		DifficultyModifierDetail modifier = difficultyModifierCalculator.getModifierByCode(war.getBattleModifier());
		return cwlStrategyService.generateStrategyPlan(war, warTag, request.getStrategyMode(), modifier);
	}

	private List<CurrentWar> fetchKnownWars(ClanWarLeagueGroupResponse group)
	{
		List<CurrentWar> wars = new ArrayList<>();
		if (group == null || group.getRounds() == null)
		{
			return wars;
		}

		for (ClanWarLeagueRoundDto round : group.getRounds())
		{
			if (round.getWarTags() != null)
			{
				for (String wTag : round.getWarTags())
				{
					if (ClanWarLeagueRoundDto.isWarReady(wTag))
					{
						try
						{
							CurrentWar war = clashApiClient.getCwlWar(wTag);
							if (war != null)
							{
								wars.add(war);
							}
						}
						catch (Exception e)
						{
							log.warn("Unable to fetch round war {}", wTag, e);
						}
					}
				}
			}
		}
		return wars;
	}

	private ClanWarLogResponse fetchSafeWarLog(String clanTag)
	{
		if (clanTag == null) return null;
		try
		{
			return clashApiClient.getClanWarLog(clanTag, 20);
		}
		catch (Exception e)
		{
			log.warn("War log unavailable or private for {}", clanTag);
			return null;
		}
	}

	private CwlLeagueTier resolveTier(Clan clan)
	{
		if (clan != null && clan.getWarLeague() != null && clan.getWarLeague().getName() != null)
		{
			return CwlLeagueTier.fromNameOrId(clan.getWarLeague().getName());
		}
		return CwlLeagueTier.CRYSTAL_I; // Default baseline if unspecified
	}

	private boolean containsClan(CurrentWar war, String clanTag)
	{
		if (war == null || clanTag == null) return false;
		String norm = normalizeTag(clanTag);
		boolean inClan = war.getClan() != null && norm.equalsIgnoreCase(normalizeTag(war.getClan().getTag()));
		boolean inOpp = war.getOpponent() != null && norm.equalsIgnoreCase(normalizeTag(war.getOpponent().getTag()));
		return inClan || inOpp;
	}

	private String normalizeTag(String tag)
	{
		if (tag == null) return null;
		String clean = tag.trim();
		return clean.startsWith("#") ? clean : "#" + clean;
	}
}
