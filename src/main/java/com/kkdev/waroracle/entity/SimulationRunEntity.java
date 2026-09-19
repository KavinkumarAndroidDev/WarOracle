package com.kkdev.waroracle.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "simulation_runs")
public class SimulationRunEntity
{
	@Id
	@Column(name = "simulation_id", length = 36, nullable = false)
	private String simulationId;

	@Column(name = "war_id", length = 64)
	private String warId;

	@Column(name = "clan_tag", length = 15, nullable = false)
	private String clanTag;

	@Column(name = "opponent_tag", length = 15)
	private String opponentTag;

	@Column(name = "quality", length = 10, nullable = false)
	private String quality;

	@Column(name = "iterations", nullable = false)
	private Integer iterations;

	@Column(name = "execution_time_millis")
	private Long executionTimeMillis;

	@Column(name = "predicted_win_rate", precision = 6, scale = 4, nullable = false)
	private BigDecimal predictedWinRate;

	@Column(name = "predicted_loss_rate", precision = 6, scale = 4, nullable = false)
	private BigDecimal predictedLossRate;

	@Column(name = "predicted_draw_rate", precision = 6, scale = 4, nullable = false)
	private BigDecimal predictedDrawRate;

	@Column(name = "predicted_home_stars_mean", precision = 5, scale = 2)
	private BigDecimal predictedHomeStarsMean;

	@Column(name = "predicted_opp_stars_mean", precision = 5, scale = 2)
	private BigDecimal predictedOppStarsMean;

	@Column(name = "score_distribution_json", columnDefinition = "json")
	private String scoreDistributionJson;

	@Column(name = "created_at")
	private LocalDateTime createdAt;
}
