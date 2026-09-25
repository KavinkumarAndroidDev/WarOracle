package com.kkdev.waroracle.dto.simulation;

public enum SimulationQuality
{
    LOW(5_000),
    MEDIUM(20_000),
    HIGH(50_000),
    MAX(100_000);

    private final int iterations;

    SimulationQuality(int iterations)
    {
        this.iterations = iterations;
    }

    public int getIterations()
    {
        return iterations;
    }
}
