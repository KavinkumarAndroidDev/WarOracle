package com.kkdev.waroracle.service.cwl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.kkdev.waroracle.dto.clan.CurrentWar;
import com.kkdev.waroracle.dto.clan.WarMember;
import com.kkdev.waroracle.dto.cwl.CwlPlayerAssignment;
import com.kkdev.waroracle.dto.cwl.CwlStrategyPlan;
import com.kkdev.waroracle.dto.cwl.DifficultyModifierDetail;
import com.kkdev.waroracle.dto.strategy.StrategyMode;
import com.kkdev.waroracle.service.strategy.KuhnBipartiteOptimizer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CwlStrategyService
{

	private final KuhnBipartiteOptimizer bipartiteOptimizer;
	private final DifficultyModifierCalculator difficultyModifierCalculator;

	public CwlStrategyService(
			KuhnBipartiteOptimizer bipartiteOptimizer,
			DifficultyModifierCalculator difficultyModifierCalculator)
	{
		this.bipartiteOptimizer = bipartiteOptimizer;
		this.difficultyModifierCalculator = difficultyModifierCalculator;
	}

	public CwlStrategyPlan generateStrategyPlan(
			CurrentWar roundWar,
			String warTag,
			StrategyMode mode,
			DifficultyModifierDetail modifier)
	{
		if (roundWar == null || roundWar.getClan() == null || roundWar.getOpponent() == null)
		{
			return CwlStrategyPlan.builder()
					.warTag(warTag)
					.strategyMode(mode != null ? mode : StrategyMode.BALANCED)
					.assignments(Collections.emptyList())
					.build();
		}

		StrategyMode activeMode = (mode != null) ? mode : StrategyMode.BALANCED;
		List<WarMember> attackers = new ArrayList<>(roundWar.getClan().getMembers() != null ? roundWar.getClan().getMembers() : Collections.emptyList());
		List<WarMember> defenders = new ArrayList<>(roundWar.getOpponent().getMembers() != null ? roundWar.getOpponent().getMembers() : Collections.emptyList());

		// Sort by map position ascending
		attackers.sort(Comparator.comparingInt(WarMember::getMapPosition));
		defenders.sort(Comparator.comparingInt(WarMember::getMapPosition));

		int numAttackers = attackers.size();
		int numDefenders = defenders.size();

		double[][] utilityMatrix = new double[numAttackers][numDefenders];

		for (int i = 0; i < numAttackers; i++)
		{
			WarMember att = attackers.get(i);
			int attTh = att.getTownhallLevel();

			for (int j = 0; j < numDefenders; j++)
			{
				WarMember def = defenders.get(j);
				int defTh = def.getTownhallLevel();
				int thDiff = attTh - defTh;

				double utility = computeMatchupUtility(att, def, thDiff, activeMode, modifier);
				utilityMatrix[i][j] = utility;
			}
		}

		int[] matching = bipartiteOptimizer.findMaxWeightMatching(utilityMatrix);

		List<CwlPlayerAssignment> assignments = new ArrayList<>();
		double totalProjectedStars = 0.0;
		double totalProjectedDest = 0.0;

		for (int i = 0; i < numAttackers; i++)
		{
			WarMember att = attackers.get(i);
			int matchedDefIdx = matching[i];

			if (matchedDefIdx >= 0 && matchedDefIdx < numDefenders)
			{
				WarMember def = defenders.get(matchedDefIdx);
				int thDiff = att.getTownhallLevel() - def.getTownhallLevel();

				double expStars = estimateStars(thDiff, activeMode, modifier);
				double expDest = estimateDestruction(thDiff, activeMode, modifier);
				double confidence = calculateConfidence(thDiff, activeMode);
				String reasoning = generateReasoning(att, def, thDiff, activeMode);

				totalProjectedStars += expStars;
				totalProjectedDest += expDest;

				assignments.add(CwlPlayerAssignment.builder()
						.attackerTag(att.getTag())
						.attackerName(att.getName())
						.attackerMapPosition(att.getMapPosition())
						.attackerTownHallLevel(att.getTownhallLevel())
						.targetTag(def.getTag())
						.targetName(def.getName())
						.targetMapPosition(def.getMapPosition())
						.targetTownHallLevel(def.getTownhallLevel())
						.expectedStars(Math.round(expStars * 10.0) / 10.0)
						.expectedDestruction(Math.round(expDest * 10.0) / 10.0)
						.confidenceRating(Math.round(confidence * 100.0) / 100.0)
						.strategicReasoning(reasoning)
						.build());
			}
			else
			{
				// Fallback mirror if unassigned
				int fallbackIdx = Math.min(i, numDefenders - 1);
				WarMember def = defenders.get(fallbackIdx);
				assignments.add(CwlPlayerAssignment.builder()
						.attackerTag(att.getTag())
						.attackerName(att.getName())
						.attackerMapPosition(att.getMapPosition())
						.attackerTownHallLevel(att.getTownhallLevel())
						.targetTag(def.getTag())
						.targetName(def.getName())
						.targetMapPosition(def.getMapPosition())
						.targetTownHallLevel(def.getTownhallLevel())
						.expectedStars(2.0)
						.expectedDestruction(80.0)
						.confidenceRating(0.80)
						.strategicReasoning("Default mirror assignment (unconstrained)")
						.build());
			}
		}

		double avgDest = numAttackers > 0 ? (totalProjectedDest / numAttackers) : 0.0;
		double winProb = calculateWinProbability(totalProjectedStars, numAttackers * 3);

		String narrative = String.format(
				"CWL Single-Attack Plan generated under %s mode. Projected total stars: %.1f / %d with %.1f%% average destruction.",
				activeMode.name(),
				totalProjectedStars,
				numAttackers * 3,
				avgDest);

		return CwlStrategyPlan.builder()
				.clanTag(roundWar.getClan().getTag())
				.opponentTag(roundWar.getOpponent().getTag())
				.warTag(warTag)
				.teamSize(roundWar.getTeamSize())
				.strategyMode(activeMode)
				.projectedStars(Math.round(totalProjectedStars * 10.0) / 10.0)
				.projectedDestruction(Math.round(avgDest * 10.0) / 10.0)
				.projectedWinProbability(Math.round(winProb * 100.0) / 100.0)
				.difficultyModifier(modifier)
				.assignments(assignments)
				.summaryNarrative(narrative)
				.build();
	}

	private double computeMatchupUtility(
			WarMember att,
			WarMember def,
			int thDiff,
			StrategyMode mode,
			DifficultyModifierDetail modifier)
	{
		double stars = estimateStars(thDiff, mode, modifier);
		double dest = estimateDestruction(thDiff, mode, modifier);

		int posDiff = Math.abs(att.getMapPosition() - def.getMapPosition());
		
		// In CWL, attacks are strictly anchored around mirror positions (within +/- 2 slots)
		double proximityPenalty = 0.0;
		if (posDiff == 0)
		{
			proximityPenalty = 0.0; // Perfect mirror
		}
		else if (posDiff == 1)
		{
			proximityPenalty = 15.0; // Allowed 1-slot tactical swap
		}
		else if (posDiff == 2)
		{
			proximityPenalty = 45.0; // Allowed 2-slot tactical swap
		}
		else
		{
			proximityPenalty = 300.0 + (posDiff * 50.0); // Heavily disallow distant cross-map hits
		}

		double utility = (stars * 100.0) + (dest * 0.5) - proximityPenalty;
		return Math.max(0.1, utility);
	}

	private double estimateStars(int thDiff, StrategyMode mode, DifficultyModifierDetail modifier)
	{
		double baseStars;
		if (thDiff >= 2) baseStars = 3.0;
		else if (thDiff == 1) baseStars = 2.8;
		else if (thDiff == 0) baseStars = 2.3;
		else if (thDiff == -1) baseStars = 1.9;
		else baseStars = 1.3;

		if (modifier != null && modifier.getAttackingHeroDpsHpPenalty() < 0)
		{
			baseStars *= (1.0 + (modifier.getAttackingHeroDpsHpPenalty() * 0.5));
		}

		if (mode == StrategyMode.SAFE_2STAR)
		{
			return Math.max(2.0, Math.min(2.8, baseStars));
		}

		if (mode == StrategyMode.AGGRESSIVE_3STAR)
		{
			return (thDiff >= 0) ? Math.min(3.0, baseStars + 0.2) : baseStars;
		}

		return Math.max(0.5, Math.min(3.0, baseStars));
	}

	private double estimateDestruction(int thDiff, StrategyMode mode, DifficultyModifierDetail modifier)
	{
		double baseDest = 85.0 + (thDiff * 7.0);
		if (modifier != null && modifier.getDefenseBuildingDpsBoost() > 0)
		{
			baseDest -= (modifier.getDefenseBuildingDpsBoost() * 20.0);
		}
		return Math.max(40.0, Math.min(100.0, baseDest));
	}

	private double calculateConfidence(int thDiff, StrategyMode mode)
	{
		if (thDiff >= 1) return 0.95;
		if (thDiff == 0) return 0.82;
		if (thDiff == -1) return 0.65;
		return 0.45;
	}

	private String generateReasoning(WarMember att, WarMember def, int thDiff, StrategyMode mode)
	{
		if (thDiff > 0)
		{
			return String.format("TH%d attacking TH%d: High probability 3-star secure attack.", att.getTownhallLevel(), def.getTownhallLevel());
		}
		else if (thDiff == 0)
		{
			return String.format("Mirror matchup (TH%d vs TH%d): Balanced attack maximizing star probability.", att.getTownhallLevel(), def.getTownhallLevel());
		}
		else
		{
			return String.format("TH%d attacking TH%d: Scout / Safe 2-star funnel securing Town Hall destruction.", att.getTownhallLevel(), def.getTownhallLevel());
		}
	}

	private double calculateWinProbability(double projectedStars, int maxStars)
	{
		if (maxStars <= 0) return 0.5;
		double starRatio = projectedStars / maxStars;
		return Math.min(0.98, Math.max(0.05, (starRatio - 0.5) * 2.0 + 0.5));
	}
}
