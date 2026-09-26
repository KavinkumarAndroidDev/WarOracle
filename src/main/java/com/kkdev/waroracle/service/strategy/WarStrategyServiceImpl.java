package com.kkdev.waroracle.service.strategy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.kkdev.waroracle.dto.common.ErrorCodes;
import com.kkdev.waroracle.dto.model.ClanPerformanceModel;
import com.kkdev.waroracle.dto.model.MatchupStarProbability;
import com.kkdev.waroracle.dto.model.PlayerPerformanceModel;
import com.kkdev.waroracle.dto.model.WarPerformanceModel;
import com.kkdev.waroracle.dto.simulation.SimulationQuality;
import com.kkdev.waroracle.dto.simulation.SimulationRequest;
import com.kkdev.waroracle.dto.strategy.ClanStrategySummary;
import com.kkdev.waroracle.dto.strategy.ContingencyCleanup;
import com.kkdev.waroracle.dto.strategy.EnemyThreatBriefing;
import com.kkdev.waroracle.dto.strategy.PlayerWarAssignment;
import com.kkdev.waroracle.dto.strategy.StrategyMode;
import com.kkdev.waroracle.dto.strategy.TargetBriefing;
import com.kkdev.waroracle.dto.strategy.VulnerableBaseBriefing;
import com.kkdev.waroracle.dto.strategy.WarStrategyPlan;
import com.kkdev.waroracle.dto.strategy.WarStrategyRequest;
import com.kkdev.waroracle.exception.ClashApiException;
import com.kkdev.waroracle.service.WarOracleService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class WarStrategyServiceImpl implements WarStrategyService
{

	private final WarOracleService warOracleService;
	private final KuhnBipartiteOptimizer bipartiteOptimizer;

	public WarStrategyServiceImpl(
			WarOracleService warOracleService,
			KuhnBipartiteOptimizer bipartiteOptimizer)
	{
		this.warOracleService = warOracleService;
		this.bipartiteOptimizer = bipartiteOptimizer;
	}

	@Override
	public WarStrategyPlan generateOptimalWarStrategy(WarStrategyRequest request)
	{
		if (request == null || request.getClanTag() == null || request.getClanTag().trim().isEmpty())
		{
			throw new ClashApiException(ErrorCodes.CLASH_API_BAD_REQUEST);
		}

		String clanTag = request.getClanTag().trim();
		StrategyMode mode = request.getStrategyMode() != null ? request.getStrategyMode() : StrategyMode.BALANCED;
		log.info("Generating optimal war strategy plan for clanTag: {} with mode: {}", clanTag, mode);

		SimulationRequest simRequest = SimulationRequest.builder()
				.clanTag(clanTag)
				.quality(SimulationQuality.MEDIUM)
				.build();

		WarPerformanceModel warModel = warOracleService.buildPerformanceModel(simRequest);
		if (warModel == null || warModel.getHomeClan() == null || warModel.getOpponentClan() == null)
		{
			throw new ClashApiException(ErrorCodes.CLAN_NOT_FOUND);
		}

		ClanPerformanceModel homeClan = warModel.getHomeClan();
		ClanPerformanceModel opponentClan = warModel.getOpponentClan();
		List<PlayerPerformanceModel> homePlayers = homeClan.getPlayers() != null ? homeClan.getPlayers() : List.of();
		List<PlayerPerformanceModel> oppPlayers = opponentClan.getPlayers() != null ? opponentClan.getPlayers() : List.of();

		int numAttackers = homePlayers.size();
		int numDefenders = oppPlayers.size();

		if (numAttackers == 0 || numDefenders == 0)
		{
			throw new ClashApiException(ErrorCodes.CLAN_NOT_FOUND);
		}

		// Calculate Current Live State & Progression
		int currentHomeStars = 0;
		int currentOpponentStars = 0;
		int totalAttacksUsed = 0;
		int totalAttacksRemaining = 0;
		int enemyBasesCleared = 0;

		List<PlayerPerformanceModel> unclearedDefenders = new ArrayList<>();
		for (PlayerPerformanceModel def : oppPlayers)
		{
			int stars = def.getCurrentBestOpponentStars();
			currentHomeStars += stars;
			if (stars >= 3)
			{
				enemyBasesCleared++;
			}
			else
			{
				unclearedDefenders.add(def);
			}
		}

		for (PlayerPerformanceModel home : homePlayers)
		{
			currentOpponentStars += home.getCurrentBestOpponentStars();
			totalAttacksUsed += home.getAttacksMade();
			totalAttacksRemaining += home.getAttacksRemaining();
		}

		int enemyBasesRemaining = oppPlayers.size() - enemyBasesCleared;

		// Filter active home attackers (attacksRemaining > 0)
		List<PlayerPerformanceModel> activeAttackers = new ArrayList<>();
		for (PlayerPerformanceModel p : homePlayers)
		{
			if (p.getAttacksRemaining() > 0)
			{
				activeAttackers.add(p);
			}
		}

		// Determine target pool for optimization:
		// If war is fresh (no attacks) or uncleared bases exist, optimize on open bases.
		List<PlayerPerformanceModel> targetDefendersPool = !unclearedDefenders.isEmpty() ? unclearedDefenders : oppPlayers;
		List<PlayerPerformanceModel> activeAttackersPool = !activeAttackers.isEmpty() ? activeAttackers : homePlayers;

		int numActive = activeAttackersPool.size();
		int numTargets = targetDefendersPool.size();

		// Construct Marginal Utility Matrix
		double[][] utilityMatrix = new double[numActive][numTargets];
		for (int i = 0; i < numActive; i++)
		{
			PlayerPerformanceModel attacker = activeAttackersPool.get(i);
			for (int j = 0; j < numTargets; j++)
			{
				PlayerPerformanceModel defender = targetDefendersPool.get(j);
				utilityMatrix[i][j] = computeMarginalMatchupUtility(attacker, defender, mode);
			}
		}

		// Run Maximum Weight Bipartite Matching
		int[] matching = bipartiteOptimizer.findMaxWeightMatching(utilityMatrix);

		// Build Player Assignments
		List<PlayerWarAssignment> assignments = new ArrayList<>();
		double marginalStarsSum = 0.0;
		double mirrorBaselineStarsSum = 0.0;

		int scoutCount = Math.max(1, (int) Math.ceil(numAttackers * 0.20));
		int anchorThreshold = Math.max(1, (int) Math.ceil(numAttackers * 0.20));

		for (int i = 0; i < numAttackers; i++)
		{
			PlayerPerformanceModel attacker = homePlayers.get(i);
			int attacksRemaining = attacker.getAttacksRemaining();
			int attacksMade = attacker.getAttacksMade();

			String role;
			if (i < anchorThreshold)
			{
				role = "ANCHOR";
			}
			else if (i >= numAttackers - scoutCount)
			{
				role = "SCOUT";
			}
			else
			{
				role = "VANGUARD";
			}

			if (attacksRemaining <= 0)
			{
				// Player has finished both attacks
				assignments.add(PlayerWarAssignment.builder()
						.attackerTag(attacker.getPlayerTag())
						.attackerName(attacker.getName())
						.attackerTownHall(attacker.getTownHallLevel())
						.mapPosition(attacker.getMapPosition())
						.attacksMade(attacksMade)
						.attacksRemaining(0)
						.assignmentStatus("COMPLETED")
						.strategicRole(role)
						.primaryTarget(null)
						.contingencyCleanup(null)
						.build());
				continue;
			}

			// Find assigned target for active player
			int activeIdx = activeAttackersPool.indexOf(attacker);
			int matchedTargetIdx = (activeIdx >= 0 && activeIdx < matching.length && matching[activeIdx] >= 0 && matching[activeIdx] < numTargets)
					? matching[activeIdx]
					: -1;

			TargetBriefing primaryTarget = null;
			String status;

			if (matchedTargetIdx >= 0)
			{
				PlayerPerformanceModel defender = targetDefendersPool.get(matchedTargetIdx);
				primaryTarget = buildTargetBriefing(attacker, defender);
				marginalStarsSum += primaryTarget.getMarginalStarsGain();
				status = "ASSIGNED";
			}
			else if (!unclearedDefenders.isEmpty())
			{
				// Fallback to nearest eligible open base
				PlayerPerformanceModel fallbackDef = findBestFallbackTarget(attacker, unclearedDefenders);
				primaryTarget = buildTargetBriefing(attacker, fallbackDef);
				marginalStarsSum += primaryTarget.getMarginalStarsGain();
				status = "ASSIGNED";
			}
			else
			{
				status = "STANDBY";
			}

			// Mirror baseline for comparison
			int mirrorIdx = Math.min(i, numDefenders - 1);
			PlayerPerformanceModel mirrorDefender = oppPlayers.get(mirrorIdx);
			TargetBriefing mirrorTarget = buildTargetBriefing(attacker, mirrorDefender);
			mirrorBaselineStarsSum += mirrorTarget.getMarginalStarsGain();

			// Build contingency cleanup recommendations on remaining open bases
			ContingencyCleanup cleanup = buildContingencyCleanup(attacker, targetDefendersPool, matchedTargetIdx, role);

			assignments.add(PlayerWarAssignment.builder()
					.attackerTag(attacker.getPlayerTag())
					.attackerName(attacker.getName())
					.attackerTownHall(attacker.getTownHallLevel())
					.mapPosition(attacker.getMapPosition())
					.attacksMade(attacksMade)
					.attacksRemaining(attacksRemaining)
					.assignmentStatus(status)
					.strategicRole(role)
					.primaryTarget(primaryTarget)
					.contingencyCleanup(cleanup)
					.build());
		}

		// Vulnerability & Threat Briefings
		VulnerableBaseBriefing mostVulnerableBase = findMostVulnerableBase(homePlayers, oppPlayers);
		EnemyThreatBriefing biggestEnemyThreat = findBiggestEnemyThreat(oppPlayers);

		ClanStrategySummary summary = ClanStrategySummary.builder()
				.totalMembers(numAttackers)
				.currentHomeStars(currentHomeStars)
				.currentOpponentStars(currentOpponentStars)
				.totalAttacksUsed(totalAttacksUsed)
				.totalAttacksRemaining(totalAttacksRemaining)
				.enemyBasesCleared(enemyBasesCleared)
				.enemyBasesRemaining(enemyBasesRemaining)
				.primaryAttacksAssigned(activeAttackers.size())
				.scoutAttacksAssigned(scoutCount)
				.cleanupReserveAttacks(totalAttacksRemaining)
				.mostVulnerableEnemyBase(mostVulnerableBase)
				.biggestEnemyThreat(biggestEnemyThreat)
				.build();

		double projectedTotalStars = Math.min(numDefenders * 3.0, currentHomeStars + marginalStarsSum);
		double deltaStars = Math.max(0.0, marginalStarsSum - mirrorBaselineStarsSum);
		double projectedWinRate = Math.min(0.99, 0.50 + (projectedTotalStars / (numDefenders * 3.0) * 0.45));
		String deltaFormatted = String.format("+%.1f Stars over mirror strategy", deltaStars);

		return WarStrategyPlan.builder()
				.clanTag(homeClan.getClanTag())
				.clanName(homeClan.getName())
				.opponentTag(opponentClan.getClanTag())
				.opponentName(opponentClan.getName())
				.warState(warModel.getWarState())
				.strategyMode(mode)
				.projectedTotalStars(Math.round(projectedTotalStars * 10.0) / 10.0)
				.projectedWinRate(Math.round(projectedWinRate * 100.0) / 100.0)
				.deltaOverMirrorStrategy(deltaFormatted)
				.clanLevelSummary(summary)
				.assignments(assignments)
				.build();
	}

	private double computeMarginalMatchupUtility(PlayerPerformanceModel attacker, PlayerPerformanceModel defender, StrategyMode mode)
	{
		int thDiff = attacker.getTownHallLevel() - defender.getTownHallLevel();
		int clampedDiff = Math.max(-2, Math.min(2, thDiff));

		MatchupStarProbability matchup = attacker.getMatchupProbabilities() != null
				? attacker.getMatchupProbabilities().get(clampedDiff)
				: null;

		double prob3 = matchup != null ? matchup.getProb3Star() : 0.35;
		double prob2 = matchup != null ? matchup.getProb2Star() : 0.50;
		double prob1 = matchup != null ? matchup.getProb1Star() : 0.10;
		double dest = matchup != null ? matchup.getExpectedDestruction() : 75.0;

		double defenseMultiplier = defender.getDefenseRating() > 0 ? (1.0 / defender.getDefenseRating()) : 1.0;
		prob3 = Math.min(0.99, prob3 * defenseMultiplier);

		int currentStars = defender.getCurrentBestOpponentStars();
		double expectedStars = (3.0 * prob3) + (2.0 * prob2) + (1.0 * prob1);
		double marginalStars = Math.max(0.0, expectedStars - currentStars);

		double rawUtility;
		switch (mode)
		{
			case AGGRESSIVE_3STAR:
				rawUtility = (3.0 * prob3) + (0.5 * marginalStars) + (0.005 * dest);
				break;
			case SAFE_2STAR:
				rawUtility = (marginalStars * 1.5) + (prob2 * 1.0) + (0.005 * dest);
				break;
			case BALANCED:
			default:
				rawUtility = (marginalStars * 2.0) + (0.5 * prob3) + (0.005 * dest);
				break;
		}

		// Proximity incentive
		int posDiff = Math.abs(attacker.getMapPosition() - defender.getMapPosition());
		double proximityBonus = Math.max(0.0, 0.15 - (posDiff * 0.02));

		// Dipping penalty in Wave 1
		double dippingPenalty = 0.0;
		if (thDiff > 1)
		{
			dippingPenalty = (thDiff - 1) * 0.45;
		}
		else if (thDiff < -1)
		{
			dippingPenalty = Math.abs(thDiff + 1) * 0.85;
		}

		return Math.max(0.01, rawUtility + proximityBonus - dippingPenalty);
	}

	private TargetBriefing buildTargetBriefing(PlayerPerformanceModel attacker, PlayerPerformanceModel defender)
	{
		int thDiff = attacker.getTownHallLevel() - defender.getTownHallLevel();
		int clampedDiff = Math.max(-2, Math.min(2, thDiff));

		MatchupStarProbability matchup = attacker.getMatchupProbabilities() != null
				? attacker.getMatchupProbabilities().get(clampedDiff)
				: null;

		double prob3 = matchup != null ? matchup.getProb3Star() : 0.35;
		double prob2 = matchup != null ? matchup.getProb2Star() : 0.50;
		double prob1 = matchup != null ? matchup.getProb1Star() : 0.10;
		double prob0 = matchup != null ? matchup.getProb0Star() : 0.05;
		double dest = matchup != null ? matchup.getExpectedDestruction() : 75.0;

		double defenseMultiplier = defender.getDefenseRating() > 0 ? (1.0 / defender.getDefenseRating()) : 1.0;
		prob3 = Math.min(0.99, prob3 * defenseMultiplier);

		double expectedStars = (3.0 * prob3) + (2.0 * prob2) + (1.0 * prob1);
		int currentStars = defender.getCurrentBestOpponentStars();
		double currentDest = defender.getCurrentBestOpponentDestruction();
		double marginalStarsGain = Math.max(0.0, expectedStars - currentStars);

		String reason;
		if (currentStars > 0)
		{
			reason = String.format("Star conversion on %d★ base. Projected gain: +%.2f stars.", currentStars, marginalStarsGain);
		}
		else if (prob3 >= 0.85)
		{
			reason = "High probability 3★ target. Favorable Town Hall and offensive equipment matchup.";
		}
		else if (prob3 >= 0.60)
		{
			reason = "Balanced primary matchup. Solid expected 2-3★ conversion.";
		}
		else
		{
			reason = "Competitive target. Aim for secure 2★ Town Hall destruction.";
		}

		return TargetBriefing.builder()
				.defenderTag(defender.getPlayerTag())
				.defenderName(defender.getName())
				.defenderTownHall(defender.getTownHallLevel())
				.defenderMapPosition(defender.getMapPosition())
				.currentStarsOnBase(currentStars)
				.currentDestructionOnBase(Math.round(currentDest * 100.0) / 100.0)
				.expectedStars(Math.round(expectedStars * 100.0) / 100.0)
				.marginalStarsGain(Math.round(marginalStarsGain * 100.0) / 100.0)
				.expectedDestruction(Math.round(dest * 100.0) / 100.0)
				.prob3Star(Math.round(prob3 * 100.0) / 100.0)
				.prob2Star(Math.round(prob2 * 100.0) / 100.0)
				.prob1Star(Math.round(prob1 * 100.0) / 100.0)
				.prob0Star(Math.round(prob0 * 100.0) / 100.0)
				.strategicReason(reason)
				.build();
	}

	private PlayerPerformanceModel findBestFallbackTarget(PlayerPerformanceModel attacker, List<PlayerPerformanceModel> unclearedDefenders)
	{
		return unclearedDefenders.stream()
				.min(Comparator.comparingInt(def -> Math.abs(attacker.getTownHallLevel() - def.getTownHallLevel())))
				.orElse(unclearedDefenders.get(0));
	}

	private ContingencyCleanup buildContingencyCleanup(
			PlayerPerformanceModel attacker,
			List<PlayerPerformanceModel> defenders,
			int primaryTargetIdx,
			String role)
	{
		List<Integer> priorityPositions = new ArrayList<>();
		for (int i = 0; i < defenders.size(); i++)
		{
			if (i != primaryTargetIdx)
			{
				PlayerPerformanceModel def = defenders.get(i);
				if (def.getCurrentBestOpponentStars() < 3)
				{
					int diff = attacker.getTownHallLevel() - def.getTownHallLevel();
					if (diff >= -1 && diff <= 1)
					{
						priorityPositions.add(def.getMapPosition());
					}
				}
			}
			if (priorityPositions.size() >= 2)
			{
				break;
			}
		}

		String instructions;
		switch (role)
		{
			case "ANCHOR":
				instructions = "Hold second attack for late-war clean-up on un-cleared top enemy bases.";
				break;
			case "SCOUT":
				instructions = "Attack early to reveal defensive Clan Castle compositions and trap locations.";
				break;
			case "VANGUARD":
			default:
				instructions = "Execute primary attack in Wave 1. Clean up any 1★ or 2★ bases in your Town Hall range in Wave 2.";
				break;
		}

		return ContingencyCleanup.builder()
				.priorityTargetPositions(priorityPositions)
				.cleanupRole(role)
				.instructions(instructions)
				.build();
	}

	private VulnerableBaseBriefing findMostVulnerableBase(
			List<PlayerPerformanceModel> homePlayers,
			List<PlayerPerformanceModel> oppPlayers)
	{
		VulnerableBaseBriefing best = null;
		double highestExpectedStars = -1.0;

		for (PlayerPerformanceModel defender : oppPlayers)
		{
			if (defender.getCurrentBestOpponentStars() < 3)
			{
				for (PlayerPerformanceModel attacker : homePlayers)
				{
					if (attacker.getAttacksRemaining() > 0 && attacker.getTownHallLevel() >= defender.getTownHallLevel())
					{
						TargetBriefing briefing = buildTargetBriefing(attacker, defender);
						if (briefing.getExpectedStars() > highestExpectedStars)
						{
							highestExpectedStars = briefing.getExpectedStars();
							best = VulnerableBaseBriefing.builder()
									.mapPosition(defender.getMapPosition())
									.defenderTag(defender.getPlayerTag())
									.defenderName(defender.getName())
									.defenderTownHall(defender.getTownHallLevel())
									.recommendedAttackerTag(attacker.getPlayerTag())
									.recommendedAttackerName(attacker.getName())
									.expectedStars(briefing.getExpectedStars())
									.prob3Star(briefing.getProb3Star())
									.vulnerabilityReason("Highest statistical 3-star conversion rate across available roster.")
									.build();
						}
					}
				}
			}
		}

		return best;
	}

	private EnemyThreatBriefing findBiggestEnemyThreat(List<PlayerPerformanceModel> oppPlayers)
	{
		PlayerPerformanceModel highestThreat = oppPlayers.stream()
				.filter(p -> p.getAttacksRemaining() > 0)
				.max(Comparator.comparingInt(PlayerPerformanceModel::getTownHallLevel)
						.thenComparing(PlayerPerformanceModel::getMapPosition))
				.orElse(oppPlayers.stream().max(Comparator.comparingInt(PlayerPerformanceModel::getTownHallLevel)).orElse(null));

		if (highestThreat == null)
		{
			return null;
		}

		String threatLevel = highestThreat.getTownHallLevel() >= 16 ? "CRITICAL" : "HIGH";

		return EnemyThreatBriefing.builder()
				.mapPosition(highestThreat.getMapPosition())
				.attackerTag(highestThreat.getPlayerTag())
				.attackerName(highestThreat.getName())
				.attackerTownHall(highestThreat.getTownHallLevel())
				.threatLevel(threatLevel)
				.estimatedOffensiveRating(1.25)
				.threatAnalysis("Top active enemy attacker with high Town Hall firepower.")
				.build();
	}
}
