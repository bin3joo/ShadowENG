package com.bremenband.shadowengapi.domain.game.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Tier - 승급/강등 경계 조건")
class TierTest {

    @Test
    @DisplayName("BRONZE에서 demote()는 BRONZE를 유지한다")
    void demote_최하위_유지() {
        assertThat(Tier.BRONZE.demote()).isEqualTo(Tier.BRONZE);
    }

    @Test
    @DisplayName("CHALLENGER에서 promote()는 CHALLENGER를 유지한다")
    void promote_최상위_유지() {
        assertThat(Tier.CHALLENGER.promote()).isEqualTo(Tier.CHALLENGER);
    }

    @Test
    @DisplayName("GOLD.promote() == PLATINUM")
    void promote_GOLD_to_PLATINUM() {
        assertThat(Tier.GOLD.promote()).isEqualTo(Tier.PLATINUM);
    }

    @Test
    @DisplayName("GOLD.demote() == SILVER")
    void demote_GOLD_to_SILVER() {
        assertThat(Tier.GOLD.demote()).isEqualTo(Tier.SILVER);
    }

    @Test
    @DisplayName("BRONZE.isLowest() == true, 나머지는 false")
    void isLowest() {
        assertThat(Tier.BRONZE.isLowest()).isTrue();
        assertThat(Tier.SILVER.isLowest()).isFalse();
        assertThat(Tier.CHALLENGER.isLowest()).isFalse();
    }

    @Test
    @DisplayName("CHALLENGER.isHighest() == true, 나머지는 false")
    void isHighest() {
        assertThat(Tier.CHALLENGER.isHighest()).isTrue();
        assertThat(Tier.RUBY.isHighest()).isFalse();
        assertThat(Tier.BRONZE.isHighest()).isFalse();
    }

    @Test
    @DisplayName("연속 승급: BRONZE → SILVER → GOLD → PLATINUM")
    void promote_연속() {
        Tier tier = Tier.BRONZE;
        assertThat(tier.promote()).isEqualTo(Tier.SILVER);
        assertThat(tier.promote().promote()).isEqualTo(Tier.GOLD);
        assertThat(tier.promote().promote().promote()).isEqualTo(Tier.PLATINUM);
    }
}
