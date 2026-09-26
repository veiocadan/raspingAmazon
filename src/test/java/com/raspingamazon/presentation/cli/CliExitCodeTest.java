package com.raspingamazon.presentation.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CliExitCodeTest {

    @Test
    void shouldExposeStableProcessCodes() {

        assertEquals(
            0,
            CliExitCode.SUCCESS.code()
        );

        assertEquals(
            1,
            CliExitCode.OPERATIONAL_ERROR.code()
        );

        assertEquals(
            2,
            CliExitCode.USAGE_ERROR.code()
        );

        assertEquals(
            3,
            CliExitCode.NOT_FOUND.code()
        );
    }
}
