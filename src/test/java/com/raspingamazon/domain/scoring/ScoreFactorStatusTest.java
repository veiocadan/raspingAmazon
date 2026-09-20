package com.raspingamazon.domain.scoring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoreFactorStatusTest {

    @Test
    void shouldExposeAvailableStatus() {
        assertEquals(
            "AVAILABLE",
            ScoreFactorStatus.AVAILABLE.name()
        );
    }

    @Test
    void shouldExposeUnavailableStatus() {
        assertEquals(
            "UNAVAILABLE",
            ScoreFactorStatus.UNAVAILABLE.name()
        );
    }
}
