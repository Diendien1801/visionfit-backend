package com.visionfit.analysis.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogContextTest {

    @AfterEach
    void cleanUp() {
        MDC.clear();
    }

    @Test
    void anAnalysisLogsWithTheIdOfTheUploadThatStartedIt() throws Exception {
        AtomicReference<String> seenByTheJob = new AtomicReference<>();
        MDC.put("requestId", "req-1");
        Runnable job = new LogContext.CopyToJobThreads().decorate(() -> seenByTheJob.set(MDC.get("requestId")));
        MDC.clear(); // the upload's request ends before the analysis runs

        Thread jobThread = new Thread(job);
        jobThread.start();
        jobThread.join();

        assertEquals("req-1", seenByTheJob.get());
    }
}
