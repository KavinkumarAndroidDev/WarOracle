package com.kkdev.waroracle.service.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KuhnBipartiteOptimizerTest
{

    private KuhnBipartiteOptimizer optimizer;

    @BeforeEach
    void setUp()
    {
        optimizer = new KuhnBipartiteOptimizer();
    }

    @Test
    void testOptimal2x2Assignment()
    {
        // Player 0: Base 0 -> 2.0, Base 1 -> 2.9
        // Player 1: Base 0 -> 2.8, Base 1 -> 1.5
        // Optimal: Player 0 -> Base 1 (2.9) and Player 1 -> Base 0 (2.8) = Total 5.7
        // (Suboptimal: Player 0 -> Base 0 (2.0) and Player 1 -> Base 1 (1.5) = Total 3.5)
        double[][] utilityMatrix = {
            {2.0, 2.9},
            {2.8, 1.5}
        };

        int[] assignment = optimizer.findMaxWeightMatching(utilityMatrix);

        assertNotNull(assignment);
        assertEquals(2, assignment.length);
        assertEquals(1, assignment[0], "Player 0 should be assigned to Base 1");
        assertEquals(0, assignment[1], "Player 1 should be assigned to Base 0");
    }

    @Test
    void testOptimal3x3Assignment()
    {
        double[][] utilityMatrix = {
            {10.0, 20.0, 30.0},
            {30.0, 10.0, 20.0},
            {20.0, 30.0, 10.0}
        };

        // Optimal:
        // Player 0 -> Base 2 (30)
        // Player 1 -> Base 0 (30)
        // Player 2 -> Base 1 (30)
        // Total = 90
        int[] assignment = optimizer.findMaxWeightMatching(utilityMatrix);

        assertNotNull(assignment);
        assertEquals(2, assignment[0]);
        assertEquals(0, assignment[1]);
        assertEquals(1, assignment[2]);
    }

    @Test
    void testEmptyAndSingleElementMatrix()
    {
        int[] emptyResult = optimizer.findMaxWeightMatching(new double[0][0]);
        assertEquals(0, emptyResult.length);

        double[][] single = {{2.5}};
        int[] singleResult = optimizer.findMaxWeightMatching(single);
        assertEquals(1, singleResult.length);
        assertEquals(0, singleResult[0]);
    }
}
