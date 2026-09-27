package com.raspingamazon.infrastructure.amazon;

import com.raspingamazon.application.collection.contract.CollectionCollector;
import com.raspingamazon.application.collection.contract.CollectionException;
import com.raspingamazon.application.collection.contract.CollectionRequest;
import com.raspingamazon.application.collection.contract.CollectionResult;
import com.raspingamazon.application.collection.contract.SourceRestrictionException;
import com.raspingamazon.application.collection.contract.SourceRestrictionType;
import com.raspingamazon.application.observability.OperationalLogContext;
import com.raspingamazon.application.orchestration.ProcessingJobType;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AmazonDealsCollectorTest {

    private static final OffsetDateTime COLLECTED_AT =
        OffsetDateTime.parse(
            "2026-09-16T22:00:00Z"
        );

    @Test
    void shouldCollectAmazonDealsSource() {

        AtomicReference<CollectionRequest> receivedRequest =
            new AtomicReference<>();

        CollectionCollector collector =
            request -> {

                receivedRequest.set(
                    request
                );

                return result(
                    "normal amazon deals content",
                    request
                );
            };

        AmazonDealsCollector amazonDealsCollector =
            new AmazonDealsCollector(
                collector
            );

        CollectionResult result =
            amazonDealsCollector.collect();

        assertEquals(
            "normal amazon deals content",
            result.content()
        );

        assertEquals(
            "https://www.amazon.com.br/deals",
            result.source()
        );

        assertEquals(
            "https://www.amazon.com.br/deals",
            receivedRequest.get()
                .source()
                .toString()
        );
    }

    @Test
    void shouldPreserveOperationalContextWhenDelegating() {

        AtomicReference<OperationalLogContext> receivedContext =
            new AtomicReference<>();

        CollectionCollector collector =
            new CollectionCollector() {

                @Override
                public CollectionResult collect(
                    CollectionRequest request
                ) {

                    throw new AssertionError(
                        "Contextual overload should be used"
                    );
                }

                @Override
                public CollectionResult collect(
                    CollectionRequest request,
                    OperationalLogContext context
                ) {

                    receivedContext.set(
                        context
                    );

                    return result(
                        "normal amazon deals content",
                        request
                    );
                }
            };

        AmazonDealsCollector amazonDealsCollector =
            new AmazonDealsCollector(
                collector
            );

        OperationalLogContext context =
            new OperationalLogContext(
                101L,
                202L,
                ProcessingJobType.COLLECT_DEALS,
                null,
                null,
                null,
                null,
                null,
                null
            );

        CollectionRequest request =
            new CollectionRequest(
                URI.create(
                    "https://www.amazon.com.br/deals"
                )
            );

        amazonDealsCollector.collect(
            request,
            context
        );

        assertSame(
            context,
            receivedContext.get()
        );
    }

    @Test
    void shouldRejectCaptchaValidationForm() {

        SourceRestrictionException exception =
            assertRestriction(
                """
                <html>
                    <body>
                        <form action="/errors/validateCaptcha">
                        </form>
                    </body>
                </html>
                """
            );

        assertEquals(
            SourceRestrictionType.CAPTCHA,
            exception.restrictionType()
        );
    }

    @Test
    void shouldRejectCaptchaInstruction() {

        SourceRestrictionException exception =
            assertRestriction(
                """
                <html>
                    <body>
                        Enter the characters you see below
                    </body>
                </html>
                """
            );

        assertEquals(
            SourceRestrictionType.CAPTCHA,
            exception.restrictionType()
        );
    }

    @Test
    void shouldRejectRobotChallenge() {

        SourceRestrictionException exception =
            assertRestriction(
                """
                <html>
                    <body>
                        Sorry, we just need to make sure
                        you're not a robot.
                    </body>
                </html>
                """
            );

        assertEquals(
            SourceRestrictionType.CHALLENGE,
            exception.restrictionType()
        );
    }

    @Test
    void shouldRejectAutomatedAccessBlock() {

        SourceRestrictionException exception =
            assertRestriction(
                """
                <html>
                    <body>
                        To discuss automated access to Amazon data,
                        contact the appropriate Amazon service.
                    </body>
                </html>
                """
            );

        assertEquals(
            SourceRestrictionType.BLOCKED,
            exception.restrictionType()
        );
    }

    @Test
    void shouldNotTreatOrdinaryRobotWordAsChallenge() {

        CollectionCollector collector =
            request ->
                result(
                    """
                    <html>
                        <body>
                            Great deals on robotic vacuum cleaners.
                        </body>
                    </html>
                    """,
                    request
                );

        AmazonDealsCollector amazonDealsCollector =
            new AmazonDealsCollector(
                collector
            );

        CollectionResult result =
            amazonDealsCollector.collect();

        assertEquals(
            "https://www.amazon.com.br/deals",
            result.source()
        );
    }

    @Test
    void shouldPropagateCollectionException() {

        CollectionException expectedException =
            new CollectionException(
                "HTTP request failed"
            );

        CollectionCollector collector =
            request -> {
                throw expectedException;
            };

        AmazonDealsCollector amazonDealsCollector =
            new AmazonDealsCollector(
                collector
            );

        CollectionException exception =
            assertThrows(
                CollectionException.class,
                amazonDealsCollector::collect
            );

        assertSame(
            expectedException,
            exception
        );
    }

    @Test
    void shouldWrapUnexpectedCollectionFailure() {

        IllegalStateException cause =
            new IllegalStateException(
                "Unexpected collector failure"
            );

        CollectionCollector collector =
            request -> {
                throw cause;
            };

        AmazonDealsCollector amazonDealsCollector =
            new AmazonDealsCollector(
                collector
            );

        CollectionException exception =
            assertThrows(
                CollectionException.class,
                amazonDealsCollector::collect
            );

        assertEquals(
            "Amazon deals collection failed",
            exception.getMessage()
        );

        assertSame(
            cause,
            exception.getCause()
        );
    }

    @Test
    void shouldRejectNullCollectionCollector() {

        assertThrows(
            NullPointerException.class,
            () -> new AmazonDealsCollector(
                null
            )
        );
    }

    private SourceRestrictionException assertRestriction(
        String content
    ) {

        CollectionCollector collector =
            request ->
                result(
                    content,
                    request
                );

        AmazonDealsCollector amazonDealsCollector =
            new AmazonDealsCollector(
                collector
            );

        return assertThrows(
            SourceRestrictionException.class,
            amazonDealsCollector::collect
        );
    }

    private static CollectionResult result(
        String content,
        CollectionRequest request
    ) {

        return new CollectionResult(
            content,
            COLLECTED_AT,
            request.source()
                .toString()
        );
    }
}
