package com.kkdev.waroracle.dto.model;

import java.math.BigDecimal;

public interface EmpiricalMatchupStat
{
	Integer getThDiff();
	Integer getStars();
	Long getSampleCount();
	BigDecimal getAvgDestruction();
}
