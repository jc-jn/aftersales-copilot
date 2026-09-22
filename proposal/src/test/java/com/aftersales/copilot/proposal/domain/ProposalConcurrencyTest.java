package com.aftersales.copilot.proposal.domain;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

class ProposalConcurrencyTest {
    @Test void concurrentSameKeyHasSingleWinner() throws Exception {
        var executions = new ConcurrentHashMap<String, String>();
        var pool = Executors.newFixedThreadPool(8);
        try {
            var tasks = java.util.stream.IntStream.range(0, 20).mapToObj(i -> (Callable<Boolean>) () ->
                    executions.putIfAbsent("confirm-key", "execution-1") == null).toList();
            long winners = pool.invokeAll(tasks).stream().filter(f -> {
                try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); }
            }).count();
            assertThat(winners).isEqualTo(1);
            assertThat(executions).containsEntry("confirm-key", "execution-1");
        } finally { pool.shutdownNow(); }
    }
}
