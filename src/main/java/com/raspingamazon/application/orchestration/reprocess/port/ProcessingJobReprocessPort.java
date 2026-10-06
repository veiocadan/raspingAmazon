package com.raspingamazon.application.orchestration.reprocess.port;

import com.raspingamazon.application.orchestration.reprocess.ProcessingJobReprocessRequest;
import com.raspingamazon.application.orchestration.reprocess.ProcessingJobReprocessResult;

import java.time.OffsetDateTime;

/**
 * Porta para reprocessamento controlado de ProcessingJob terminal.
 *
 * <p>A implementação deve ser transacional e idempotente por
 * requestKey.</p>
 *
 * <p>Somente um job DEAD pode receber uma nova decisão de
 * reprocessamento. Uma repetição da mesma requestKey pode retornar o
 * resultado anteriormente aplicado mesmo que o job já tenha avançado
 * para outro estado.</p>
 */
public interface ProcessingJobReprocessPort {

    ProcessingJobReprocessResult reprocess(
        ProcessingJobReprocessRequest request,
        OffsetDateTime requestedAt
    );
}
