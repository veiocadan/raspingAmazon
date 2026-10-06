package com.raspingamazon.infrastructure.amazon;

import com.raspingamazon.application.collection.contract.SourceRestrictionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AmazonSourceRestrictionDetectorTest {

    private final AmazonSourceRestrictionDetector detector =
        new AmazonSourceRestrictionDetector();

    @Test
    void shouldDetectCaptchaForm() {

        assertEquals(
            SourceRestrictionType.CAPTCHA,
            detector.detect(
                """
                <html>
                    <form action="/errors/validateCaptcha">
                    </form>
                </html>
                """
            )
        );
    }

    @Test
    void shouldDetectRobotChallenge() {

        assertEquals(
            SourceRestrictionType.CHALLENGE,
            detector.detect(
                """
                <html>
                    <body>
                        Sorry, we just need to make sure
                        you're not a robot
                    </body>
                </html>
                """
            )
        );
    }

    @Test
    void shouldDetectExplicitAutomatedAccessBlock() {

        assertEquals(
            SourceRestrictionType.BLOCKED,
            detector.detect(
                """
                <html>
                    <body>
                        Automated access to Amazon data
                    </body>
                </html>
                """
            )
        );
    }

    @Test
    void shouldNotTreatIsolatedCaptchaWordAsRestriction() {

        assertNull(
            detector.detect(
                """
                <html>
                    <script>
                        const captchaTelemetry = true;
                    </script>
                </html>
                """
            )
        );
    }

    @Test
    void shouldReturnNullForNormalProductContent() {

        assertNull(
            detector.detect(
                """
                <html>
                    <body>
                        <span id="productTitle">
                            Produto normal
                        </span>
                    </body>
                </html>
                """
            )
        );
    }
}
