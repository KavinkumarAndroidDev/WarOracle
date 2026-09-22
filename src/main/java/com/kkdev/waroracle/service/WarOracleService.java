package com.kkdev.waroracle.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Service;

import com.kkdev.waroracle.dto.battlelog.PlayerBattleLogItem;
import com.kkdev.waroracle.dto.clan.Clan;
import com.kkdev.waroracle.dto.clan.CurrentWar;
import com.kkdev.waroracle.dto.clan.WarMember;
import com.kkdev.waroracle.dto.common.ErrorCodes;
import com.kkdev.waroracle.dto.model.WarPerformanceModel;
import com.kkdev.waroracle.dto.player.Player;
import com.kkdev.waroracle.dto.player.StatisticsDetails;
import com.kkdev.waroracle.dto.simulation.SimulationQuality;
import com.kkdev.waroracle.dto.simulation.SimulationQualityOption;
import com.kkdev.waroracle.dto.simulation.SimulationRequest;
import com.kkdev.waroracle.dto.simulation.SimulationResult;
import com.kkdev.waroracle.dto.warlog.ClanWarLogResponse;
import com.kkdev.waroracle.entity.WarAttackEntity;
import com.kkdev.waroracle.exception.ClashApiException;
import com.kkdev.waroracle.repository.WarAttackRepository;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class WarOracleService
{

	private final PlayerService playerService;
	private final ClanService clanService;
	private final PerformanceModelingService performanceModelingService;
	private final MonteCarloSimulationService monteCarloSimulationService;
	private final WarPersistenceService warPersistenceService;
	private final WarAttackRepository warAttackRepository;
	private final ExecutorService executorService = Executors.newFixedThreadPool(10);

	public WarOracleService(
			PlayerService playerService,
			ClanService clanService,
			PerformanceModelingService performanceModelingService,
			MonteCarloSimulationService monteCarloSimulationService,
			WarPersistenceService warPersistenceService,
			WarAttackRepository warAttackRepository)
	{
		this.playerService = playerService;
		this.clanService = clanService;
		this.performanceModelingService = performanceModelingService;
		this.monteCarloSimulationService = monteCarloSimulationService;
		this.warPersistenceService = warPersistenceService;
		this.warAttackRepository = warAttackRepository;
	}

	public StatisticsDetails getStatistics(String playerTag)
	{
		String normalizedTag = (playerTag != null && !playerTag.trim().startsWith("#")) ? "#" + playerTag.trim() : (playerTag != null ? playerTag.trim() : null);
		log.info("Aggregating statistics for playerTag: {}", normalizedTag);
		StatisticsDetails details = new StatisticsDetails();
		Player player = null;
		Clan clan = null;
		CurrentWar currentWar = null;

		if (normalizedTag != null && !normalizedTag.isEmpty())
		{
			player = playerService.getPlayerDetails(normalizedTag);
			if (player != null)
			{
				warPersistenceService.persistPlayerSnapshot(player);
			}
		}

		if (playerService.isPlayerInClan(player))
		{
			String clanTag = normalizeTag(player.getClan().getTag());
			log.debug("Player is affiliated with clanTag: {}. Fetching clan & war data.", clanTag);
			try
			{
				clan = clanService.getClanDetails(clanTag);
			}
			catch (Exception ex)
			{
				log.warn("Failed to fetch clan details for {}: {}", clanTag, ex.getMessage());
			}

			try
			{
				currentWar = clanService.getCurrentWar(clanTag);
			}
			catch (Exception ex)
			{
				log.warn("Current war details private or unavailable for {}: {}", clanTag, ex.getMessage());
			}
		}
		else
		{
			log.debug("Player is not affiliated with any active clan.");
		}

		details.setPlayer(player);
		details.setClan(clan);
		details.setCurrentWar(currentWar);

		return details;
	}

	public StatisticsDetails getClanStatistics(String clanTag)
	{
		String normalizedTag = normalizeTag(clanTag);
		log.info("Aggregating statistics for clanTag: {}", normalizedTag);
		StatisticsDetails details = new StatisticsDetails();
		Clan clan = null;
		CurrentWar currentWar = null;

		if (normalizedTag != null && !normalizedTag.isEmpty())
		{
			try
			{
				clan = clanService.getClanDetails(normalizedTag);
			}
			catch (Exception ex)
			{
				log.warn("Failed to fetch clan details for {}: {}", normalizedTag, ex.getMessage());
			}

			try
			{
				currentWar = clanService.getCurrentWar(normalizedTag);
			}
			catch (Exception ex)
			{
				log.warn("Current war details private or unavailable for {}: {}", normalizedTag, ex.getMessage());
			}
		}

		if (clan == null && currentWar == null)
		{
			throw new ClashApiException(ErrorCodes.CLAN_NOT_FOUND);
		}

		details.setClan(clan);
		details.setCurrentWar(currentWar);
		return details;
	}

	public WarPerformanceModel buildPerformanceModel(SimulationRequest request)
	{
		String clanTag = normalizeTag(request != null ? request.getClanTag() : null);
		if (request != null) { request.setClanTag(clanTag); }
		log.info("Building performance model for clanTag: {} with quality: {}", clanTag, request != null ? request.getQuality() : null);

		CurrentWar currentWar = clanService.getCurrentWar(clanTag);
		if (currentWar == null || "notInWar".equalsIgnoreCase(currentWar.getState()))
		{
			throw new ClashApiException(ErrorCodes.CLAN_NOT_FOUND);
		}

		return buildPerformanceModelWithWar(request, currentWar);
	}

	public SimulationResult simulateWar(SimulationRequest request)
	{
		String clanTag = normalizeTag(request != null ? request.getClanTag() : null);
		if (request != null) { request.setClanTag(clanTag); }
		log.info("Executing end-to-end war simulation for clanTag: {}", clanTag);

		CurrentWar currentWar = clanService.getCurrentWar(clanTag);
		if (currentWar == null || "notInWar".equalsIgnoreCase(currentWar.getState()))
		{
			log.info("Clan {} is not currently in war.", clanTag);
			return SimulationResult.builder()
					.warState(currentWar != null ? currentWar.getState() : "notInWar")
					.message("Clan is not currently in an active or upcoming war.")
					.currentWar(currentWar)
					.build();
		}

		if ("warEnded".equalsIgnoreCase(currentWar.getState()))
		{
			log.info("War has already ended for clanTag: {}. Returning ended war summary without running simulation.", clanTag);
			return SimulationResult.builder()
					.warState("warEnded")
					.message("War has already ended. Simulation is only applicable for active or upcoming wars. Start next war to run simulation.")
					.currentWar(currentWar)
					.build();
		}

		String warId = warPersistenceService.persistClanAndWar(currentWar);

		WarPerformanceModel performanceModel = buildPerformanceModelWithWar(request, currentWar);
		SimulationResult result = monteCarloSimulationService.simulate(performanceModel);
		result.setCurrentWar(currentWar);

		String oppTag = currentWar.getOpponent() != null ? currentWar.getOpponent().getTag() : null;
		warPersistenceService.persistSimulationRun(result, warId, clanTag, oppTag, request != null ? request.getQuality() : null);

		return result;
	}

	private WarPerformanceModel buildPerformanceModelWithWar(SimulationRequest request, CurrentWar currentWar)
	{
		String clanTag = request.getClanTag();
		ClanWarLogResponse homeClanWarLog = null;
		try
		{
			homeClanWarLog = clanService.getClanWarLog(clanTag, 100);
		}
		catch (Exception ex)
		{
			log.warn("Clan war log for {} is private or unavailable: {}", clanTag, ex.getMessage());
		}

		ClanWarLogResponse opponentClanWarLog = null;
		if (currentWar.getOpponent() != null && currentWar.getOpponent().getTag() != null)
		{
			try
			{
				opponentClanWarLog = clanService.getClanWarLog(currentWar.getOpponent().getTag(), 100);
			}
			catch (Exception ex)
			{
				log.warn("Opponent war log is private or unavailable: {}", ex.getMessage());
			}
		}

		List<String> participantTags = new ArrayList<>();
		if (currentWar.getClan() != null && currentWar.getClan().getMembers() != null)
		{
			for (WarMember member : currentWar.getClan().getMembers())
			{
				participantTags.add(member.getTag());
			}
		}
		if (currentWar.getOpponent() != null && currentWar.getOpponent().getMembers() != null)
		{
			for (WarMember member : currentWar.getOpponent().getMembers())
			{
				participantTags.add(member.getTag());
			}
		}

		Map<String, List<PlayerBattleLogItem>> playerBattleLogs = fetchPlayerBattleLogsInParallel(participantTags);
		Map<String, Player> livePlayerProfiles = fetchPlayerProfilesInParallel(participantTags);
		Map<String, List<WarAttackEntity>> playerWarAttacks = fetchHistoricalWarAttacks(participantTags);

		return performanceModelingService.buildWarPerformanceModel(
				currentWar,
				homeClanWarLog,
				opponentClanWarLog,
				playerBattleLogs,
				playerWarAttacks,
				livePlayerProfiles,
				request.getQuality());
	}

	private Map<String, List<PlayerBattleLogItem>> fetchPlayerBattleLogsInParallel(List<String> playerTags)
	{
		Map<String, List<PlayerBattleLogItem>> results = new ConcurrentHashMap<>();
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (String tag : playerTags)
		{
			CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
				List<PlayerBattleLogItem> logs = playerService.getPlayerBattleLog(tag);
				results.put(tag, logs);
			}, executorService);
			futures.add(future);
		}

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
		return results;
	}

	private Map<String, Player> fetchPlayerProfilesInParallel(List<String> playerTags)
	{
		Map<String, Player> results = new ConcurrentHashMap<>();
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (String tag : playerTags)
		{
			CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
				try
				{
					Player player = playerService.getPlayerDetails(tag);
					if (player != null)
					{
						results.put(tag, player);
					}
				}
				catch (Exception ex)
				{
					log.debug("Could not fetch live profile for tag {}: {}", tag, ex.getMessage());
				}
			}, executorService);
			futures.add(future);
		}

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
		return results;
	}

	private Map<String, List<WarAttackEntity>> fetchHistoricalWarAttacks(List<String> playerTags)
	{
		Map<String, List<WarAttackEntity>> results = new HashMap<>();
		if (playerTags == null || playerTags.isEmpty())
		{
			return results;
		}

		try
		{
			List<WarAttackEntity> attacks = warAttackRepository.findByAttackerTagIn(playerTags);
			if (attacks != null)
			{
				for (WarAttackEntity attack : attacks)
				{
					results.computeIfAbsent(attack.getAttackerTag(), k -> new ArrayList<>()).add(attack);
				}
			}
		}
		catch (Exception ex)
		{
			log.warn("Failed to fetch batch historical war attacks from MySQL: {}", ex.getMessage());
		}

		return results;
	}

	public List<SimulationQualityOption> getSimulationQualities()
	{
		List<SimulationQualityOption> options = new ArrayList<>();
		for (SimulationQuality quality : SimulationQuality.values())
		{
			String label = switch (quality)
			{
				case LOW -> "Quick Analysis";
				case MEDIUM -> "Balanced";
				case HIGH -> "High Accuracy";
				case MAX -> "Maximum Precision";
			};
			options.add(SimulationQualityOption.builder()
					.key(quality.name())
					.label(label)
					.iterations(quality.getIterations())
					.defaultOption(quality == SimulationQuality.MEDIUM)
					.build());
		}
		return options;
	}

	private String normalizeTag(String tag)
	{
		if (tag == null || tag.trim().isEmpty())
		{
			return tag;
		}
		String trimmed = tag.trim();
		while (trimmed.startsWith("%23") || trimmed.startsWith("%2523"))
		{
			if (trimmed.startsWith("%2523"))
			{
				trimmed = trimmed.substring(5);
			}
			else
			{
				trimmed = trimmed.substring(3);
			}
		}
		return trimmed.startsWith("#") ? trimmed : "#" + trimmed;
	}

	@PreDestroy
	public void shutdown()
	{
		executorService.shutdown();
	}
}
