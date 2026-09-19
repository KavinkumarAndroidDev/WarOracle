-- ==========================================================
-- Migration V1: Initial WarOracle Schema
-- Date: 2026-09-16
-- ==========================================================

CREATE TABLE IF NOT EXISTS clans (
    clan_tag VARCHAR(15) NOT NULL,
    name VARCHAR(100) NOT NULL,
    clan_level INT DEFAULT 1,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (clan_tag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS clan_wars (
    war_id VARCHAR(64) NOT NULL,
    clan_tag VARCHAR(15) NOT NULL,
    opponent_tag VARCHAR(15),
    state VARCHAR(20) NOT NULL,
    team_size INT DEFAULT 0,
    attacks_per_member INT DEFAULT 2,
    battle_modifier VARCHAR(50),
    preparation_start_time VARCHAR(30),
    start_time VARCHAR(30),
    end_time VARCHAR(30),
    clan_stars INT DEFAULT 0,
    clan_destruction DECIMAL(5, 2) DEFAULT 0.00,
    opponent_stars INT DEFAULT 0,
    opponent_destruction DECIMAL(5, 2) DEFAULT 0.00,
    result VARCHAR(10),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (war_id),
    KEY idx_clan_state (clan_tag, state),
    KEY idx_start_time (start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS war_attacks (
    attack_id BIGINT NOT NULL AUTO_INCREMENT,
    war_id VARCHAR(64) NOT NULL,
    attacker_tag VARCHAR(15) NOT NULL,
    attacker_th INT,
    defender_tag VARCHAR(15) NOT NULL,
    defender_th INT,
    th_diff INT,
    stars INT NOT NULL,
    destruction_percentage DECIMAL(5, 2) NOT NULL,
    attack_order INT,
    duration_seconds INT,
    is_clan_attack BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (attack_id),
    KEY idx_war_id (war_id),
    KEY idx_attacker_defender_th (attacker_th, defender_th),
    KEY idx_attacker_tag (attacker_tag),
    CONSTRAINT fk_war_attacks_war FOREIGN KEY (war_id) REFERENCES clan_wars(war_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS player_snapshots (
    snapshot_id BIGINT NOT NULL AUTO_INCREMENT,
    player_tag VARCHAR(15) NOT NULL,
    player_name VARCHAR(100),
    town_hall_level INT,
    town_hall_weapon_level INT,
    war_stars INT,
    trophies INT,
    exp_level INT,
    heroes_json JSON,
    hero_equipment_json JSON,
    troops_json JSON,
    spells_json JSON,
    snapshot_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (snapshot_id),
    KEY idx_player_snapshot (player_tag, snapshot_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS simulation_runs (
    simulation_id VARCHAR(36) NOT NULL,
    war_id VARCHAR(64),
    clan_tag VARCHAR(15) NOT NULL,
    opponent_tag VARCHAR(15),
    quality VARCHAR(10) NOT NULL,
    iterations INT NOT NULL,
    execution_time_millis BIGINT DEFAULT 0,
    predicted_win_rate DECIMAL(6, 4) NOT NULL,
    predicted_loss_rate DECIMAL(6, 4) NOT NULL,
    predicted_draw_rate DECIMAL(6, 4) NOT NULL,
    predicted_home_stars_mean DECIMAL(5, 2),
    predicted_opp_stars_mean DECIMAL(5, 2),
    score_distribution_json JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (simulation_id),
    KEY idx_sim_clan (clan_tag, created_at),
    KEY idx_sim_war (war_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
