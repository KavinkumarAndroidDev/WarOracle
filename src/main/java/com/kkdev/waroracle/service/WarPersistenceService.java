package com.kkdev.waroracle.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kkdev.waroracle.dto.clan.CurrentWar;
import com.kkdev.waroracle.dto.clan.WarAttack;
import com.kkdev.waroracle.dto.clan.WarClan;
import com.kkdev.waroracle.dto.clan.WarMember;
import com.kkdev.waroracle.dto.player.Player;
import com.kkdev.waroracle.dto.simulation.SimulationQuality;
import com.kkdev.waroracle.dto.simulation.SimulationResult;
import com.kkdev.waroracle.entity.ClanEntity;
import com.kkdev.waroracle.entity.ClanWarEntity;
import com.kkdev.waroracle.entity.PlayerSnapshotEntity;
import com.kkdev.waroracle.entity.SimulationRunEntity;
import com.kkdev.waroracle.entity.WarAttackEntity;
import com.kkdev.waroracle.repository.ClanRepository;
import com.kkdev.waroracle.repository.ClanWarRepository;
import com.kkdev.waroracle.repository.PlayerSnapshotRepository;
import com.kkdev.waroracle.repository.SimulationRunRepository;
import com.kkdev.waroracle.repository.WarAttackRepository;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Service
public class WarPersistenceService
{

	private final ClanRepository clanRepository;
	private final ClanWarRepository clanWarRepository;
	private final WarAttackRepository warAttackRepository;
	private final PlayerSnapshotRepository playerSnapshotRepository;
	private final SimulationRunRepository simulationRunRepository;
	private final JsonMapper jsonMapper;

	public WarPersistenceService(
			ClanRepository clanRepository,
			ClanWarRepository clanWarRepository,
			WarAttackRepository warAttackRepository,
			PlayerSnapshotRepository playerSnapshotRepository,
			SimulationRunRepository simulationRunRepository,
			JsonMapper jsonMapper)
	{
		this.clanRepository = clanRepository;
		this.clanWarRepository = clanWarRepository;
		this.warAttackRepository = warAttackRepository;
		this.playerSnapshotRepository = playerSnapshotRepository;
		this.simulationRunRepository = simulationRunRepository;
		this.jsonMapper = jsonMapper;
	}

	@Transactional
	public void persistPlayerSnapshot(Player player)
	{
		if (player == null || player.getTag() == null)
		{
			return;
		}

		try
		{
			String heroesJson = player.getHeroes() != null ? jsonMapper.writeValueAsString(player.getHeroes()) : null;
			String heroEquipmentJson = player.getHeroEquipment() != null ? jsonMapper.writeValueAsString(player.getHeroEquipment()) : null;
			String troopsJson = player.getTroops() != null ? jsonMapper.writeValueAsString(player.getTroops()) : null;
			String spellsJson = player.getSpells() != null ? jsonMapper.writeValueAsString(player.getSpells()) : null;

			PlayerSnapshotEntity snapshot = PlayerSnapshotEntity.builder()
					.playerTag(player.getTag())
					.playerName(player.getName())
					.townHallLevel(player.getTownHallLevel())
					.townHallWeaponLevel(player.getTownHallWeaponLevel())
					.warStars(player.getWarStars())
					.trophies(player.getTrophies())
					.expLevel(player.getExpLevel())
					.heroesJson(heroesJson)
					.heroEquipmentJson(heroEquipmentJson)
					.troopsJson(troopsJson)
					.spellsJson(spellsJson)
					.snapshotTime(LocalDateTime.now())
					.build();

			playerSnapshotRepository.save(snapshot);
			log.debug("Persisted player snapshot for playerTag: {}", player.getTag());
		}
		catch (Exception ex)
		{
			log.warn("Failed to persist player snapshot for playerTag {}: {}", player.getTag(), ex.getMessage());
		}
	}

	@Transactional
	public String persistClanAndWar(CurrentWar currentWar)
	{
		if (currentWar == null || currentWar.getClan() == null)
		{
			return null;
		}

		WarClan homeClan = currentWar.getClan();
		WarClan opponentClan = currentWar.getOpponent();

		upsertClan(homeClan);
		if (opponentClan != null)
		{
			upsertClan(opponentClan);
		}

		String warId = generateWarId(
				homeClan.getTag(),
				opponentClan != null ? opponentClan.getTag() : "UNKNOWN",
				currentWar.getPreparationStartTime());

		ClanWarEntity warEntity = ClanWarEntity.builder()
				.warId(warId)
				.clanTag(homeClan.getTag())
				.opponentTag(opponentClan != null ? opponentClan.getTag() : null)
				.state(currentWar.getState())
				.teamSize(currentWar.getTeamSize())
				.attacksPerMember(currentWar.getAttacksPerMember())
				.battleModifier(currentWar.getBattleModifier())
				.preparationStartTime(currentWar.getPreparationStartTime())
				.startTime(currentWar.getStartTime())
				.endTime(currentWar.getEndTime())
				.clanStars(homeClan.getStars())
				.clanDestruction(BigDecimal.valueOf(homeClan.getDestructionPercentage()))
				.opponentStars(opponentClan != null ? opponentClan.getStars() : 0)
				.opponentDestruction(BigDecimal.valueOf(opponentClan != null ? opponentClan.getDestructionPercentage() : 0.0))
				.createdAt(LocalDateTime.now())
				.updatedAt(LocalDateTime.now())
				.build();

		clanWarRepository.save(warEntity);

		persistWarAttacks(warId, currentWar);

		log.debug("Persisted clan war record with warId: {}", warId);
		return warId;
	}

	@Transactional
	public void persistSimulationRun(
			SimulationResult result,
			String warId,
			String clanTag,
			String opponentTag,
			SimulationQuality quality)
	{
		if (result == null || clanTag == null)
		{
			return;
		}

		try
		{
			String scoreDistJson = result.getHomeClanPrediction() != null && result.getHomeClanPrediction().getStarDistribution() != null
					? jsonMapper.writeValueAsString(result.getHomeClanPrediction().getStarDistribution())
					: null;

			BigDecimal predictedHomeStars = result.getHomeClanPrediction() != null
					? BigDecimal.valueOf(result.getHomeClanPrediction().getExpectedStars())
					: BigDecimal.ZERO;

			BigDecimal predictedOppStars = result.getOpponentClanPrediction() != null
					? BigDecimal.valueOf(result.getOpponentClanPrediction().getExpectedStars())
					: BigDecimal.ZERO;

			SimulationRunEntity runEntity = SimulationRunEntity.builder()
					.simulationId(UUID.randomUUID().toString())
					.warId(warId)
					.clanTag(clanTag)
					.opponentTag(opponentTag)
					.quality(quality != null ? quality.name() : "MEDIUM")
					.iterations(result.getIterationsRun())
					.executionTimeMillis(result.getExecutionTimeMillis())
					.predictedWinRate(BigDecimal.valueOf(result.getWinProbability()))
					.predictedLossRate(BigDecimal.valueOf(result.getLossProbability()))
					.predictedDrawRate(BigDecimal.valueOf(result.getDrawProbability()))
					.predictedHomeStarsMean(predictedHomeStars)
					.predictedOppStarsMean(predictedOppStars)
					.scoreDistributionJson(scoreDistJson)
					.createdAt(LocalDateTime.now())
					.build();

			simulationRunRepository.save(runEntity);
			log.info("Persisted simulation run {} for warId: {}", runEntity.getSimulationId(), warId);
		}
		catch (Exception ex)
		{
			log.warn("Failed to persist simulation run: {}", ex.getMessage());
		}
	}

	private void upsertClan(WarClan warClan)
	{
		if (warClan == null || warClan.getTag() == null)
		{
			return;
		}

		ClanEntity clanEntity = ClanEntity.builder()
				.clanTag(warClan.getTag())
				.name(warClan.getName())
				.clanLevel(warClan.getClanLevel())
				.updatedAt(LocalDateTime.now())
				.build();

		clanRepository.save(clanEntity);
	}

	private void persistWarAttacks(String warId, CurrentWar currentWar)
	{
		warAttackRepository.deleteByWarId(warId);

		List<WarAttackEntity> attackEntities = new ArrayList<>();

		Map<String, Integer> homeMemberThMap = new java.util.HashMap<>();
		if (currentWar.getClan() != null && currentWar.getClan().getMembers() != null)
		{
			for (WarMember m : currentWar.getClan().getMembers())
			{
				homeMemberThMap.put(m.getTag(), m.getTownhallLevel());
			}
		}

		Map<String, Integer> oppMemberThMap = new java.util.HashMap<>();
		if (currentWar.getOpponent() != null && currentWar.getOpponent().getMembers() != null)
		{
			for (WarMember m : currentWar.getOpponent().getMembers())
			{
				oppMemberThMap.put(m.getTag(), m.getTownhallLevel());
			}
		}

		if (currentWar.getClan() != null && currentWar.getClan().getMembers() != null)
		{
			for (WarMember member : currentWar.getClan().getMembers())
			{
				if (member.getAttacks() != null)
				{
					for (WarAttack attack : member.getAttacks())
					{
						int attackerTh = member.getTownhallLevel();
						int defenderTh = oppMemberThMap.getOrDefault(attack.getDefenderTag(), attackerTh);
						attackEntities.add(WarAttackEntity.builder()
								.warId(warId)
								.attackerTag(attack.getAttackerTag())
								.attackerTh(attackerTh)
								.defenderTag(attack.getDefenderTag())
								.defenderTh(defenderTh)
								.thDiff(attackerTh - defenderTh)
								.stars(attack.getStars())
								.destructionPercentage(BigDecimal.valueOf(attack.getDestructionPercentage()))
								.attackOrder(attack.getOrder())
								.durationSeconds(attack.getDuration())
								.isClanAttack(true)
								.createdAt(LocalDateTime.now())
								.build());
					}
				}
			}
		}

		if (currentWar.getOpponent() != null && currentWar.getOpponent().getMembers() != null)
		{
			for (WarMember member : currentWar.getOpponent().getMembers())
			{
				if (member.getAttacks() != null)
				{
					for (WarAttack attack : member.getAttacks())
					{
						int attackerTh = member.getTownhallLevel();
						int defenderTh = homeMemberThMap.getOrDefault(attack.getDefenderTag(), attackerTh);
						attackEntities.add(WarAttackEntity.builder()
								.warId(warId)
								.attackerTag(attack.getAttackerTag())
								.attackerTh(attackerTh)
								.defenderTag(attack.getDefenderTag())
								.defenderTh(defenderTh)
								.thDiff(attackerTh - defenderTh)
								.stars(attack.getStars())
								.destructionPercentage(BigDecimal.valueOf(attack.getDestructionPercentage()))
								.attackOrder(attack.getOrder())
								.durationSeconds(attack.getDuration())
								.isClanAttack(false)
								.createdAt(LocalDateTime.now())
								.build());
					}
				}
			}
		}

		if (!attackEntities.isEmpty())
		{
			warAttackRepository.saveAll(attackEntities);
			log.debug("Saved {} completed war attacks for warId: {}", attackEntities.size(), warId);
		}
	}

	public String generateWarId(String homeClanTag, String opponentClanTag, String preparationStartTime)
	{
		String rawKey = (homeClanTag != null ? homeClanTag.trim() : "") + "_"
				+ (opponentClanTag != null ? opponentClanTag.trim() : "") + "_"
				+ (preparationStartTime != null ? preparationStartTime.trim() : "");
		try
		{
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] hash = md.digest(rawKey.getBytes(StandardCharsets.UTF_8));
			StringBuilder hexString = new StringBuilder();
			for (byte b : hash)
			{
				String hex = Integer.toHexString(0xff & b);
				if (hex.length() == 1) hexString.append('0');
				hexString.append(hex);
			}
			return hexString.substring(0, 32);
		}
		catch (NoSuchAlgorithmException e)
		{
			return UUID.nameUUIDFromBytes(rawKey.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
		}
	}
}
