package com.kkdev.waroracle.dto.simulation;

public enum SimulationQuality
{
    LOW(10_000),
    MEDIUM(50_000),
    HIGH(100_000),
    MAX(250_000);

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
