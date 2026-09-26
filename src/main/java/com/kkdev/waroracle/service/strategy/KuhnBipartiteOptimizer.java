package com.kkdev.waroracle.service.strategy;

import java.util.Arrays;

import org.springframework.stereotype.Component;

/**
 * High-performance Maximum Weight Bipartite Matching solver implementing the Kuhn-Munkres (Hungarian) algorithm.
 * Optimally assigns N attackers to M defenders to maximize overall matching utility in O(N^3) time.
 */
@Component
public class KuhnBipartiteOptimizer
{

	/**
	 * Computes the optimal 1-to-1 assignment maximizing total utility.
	 *
	 * @param utilityMatrix Matrix where utilityMatrix[i][j] is the weight/utility of assigning attacker i to defender j.
	 * @return An array of length N (number of attackers), where result[i] is the matched defender index for attacker i,
	 *         or -1 if attacker i is unassigned.
	 */
	public int[] findMaxWeightMatching(double[][] utilityMatrix)
	{
		if (utilityMatrix == null || utilityMatrix.length == 0 || utilityMatrix[0].length == 0)
		{
			return new int[0];
		}

		int numAttackers = utilityMatrix.length;
		int numDefenders = utilityMatrix[0].length;
		int n = Math.max(numAttackers, numDefenders);

		// Find maximum utility value to convert maximization problem to minimization problem
		double maxUtility = 0.0;
		for (int i = 0; i < numAttackers; i++)
		{
			for (int j = 0; j < numDefenders; j++)
			{
				if (utilityMatrix[i][j] > maxUtility)
				{
					maxUtility = utilityMatrix[i][j];
				}
			}
		}

		// Pad into square cost matrix (1-indexed for Hungarian algorithm)
		double[][] cost = new double[n + 1][n + 1];
		for (int i = 1; i <= n; i++)
		{
			for (int j = 1; j <= n; j++)
			{
				if (i <= numAttackers && j <= numDefenders)
				{
					cost[i][j] = maxUtility - utilityMatrix[i - 1][j - 1];
				}
				else
				{
					cost[i][j] = maxUtility; // Neutral padding for unbalanced dimensions
				}
			}
		}

		double[] u = new double[n + 1];
		double[] v = new double[n + 1];
		int[] p = new int[n + 1];
		int[] way = new int[n + 1];

		for (int i = 1; i <= n; i++)
		{
			p[0] = i;
			int j0 = 0;
			double[] minv = new double[n + 1];
			Arrays.fill(minv, Double.MAX_VALUE);
			boolean[] used = new boolean[n + 1];

			do
			{
				used[j0] = true;
				int i0 = p[j0];
				double delta = Double.MAX_VALUE;
				int j1 = 0;

				for (int j = 1; j <= n; j++)
				{
					if (!used[j])
					{
						double cur = cost[i0][j] - u[i0] - v[j];
						if (cur < minv[j])
						{
							minv[j] = cur;
							way[j] = j0;
						}
						if (minv[j] < delta)
						{
							delta = minv[j];
							j1 = j;
						}
					}
				}

				for (int j = 0; j <= n; j++)
				{
					if (used[j])
					{
						u[p[j]] += delta;
						v[j] -= delta;
					}
					else
					{
						minv[j] -= delta;
					}
				}
				j0 = j1;
			} while (p[j0] != 0);

			do
			{
				int j1 = way[j0];
				p[j0] = p[j1];
				j0 = j1;
			} while (j0 != 0);
		}

		int[] result = new int[numAttackers];
		Arrays.fill(result, -1);

		for (int j = 1; j <= numDefenders; j++)
		{
			if (p[j] > 0 && p[j] <= numAttackers)
			{
				result[p[j] - 1] = j - 1;
			}
		}

		return result;
	}
}
