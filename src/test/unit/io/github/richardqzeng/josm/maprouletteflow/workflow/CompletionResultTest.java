// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;
import io.github.richardqzeng.josm.maprouletteflow.api.enums.TaskStatus;

class CompletionResultTest {
    @Test
    void webLabelsHaveExactStatusMappings() {
        assertEquals(Map.of("I fixed it!", TaskStatus.FIXED, "Already fixed", TaskStatus.ALREADY_FIXED,
                "Not an Issue", TaskStatus.FALSE_POSITIVE, "Can't Complete", TaskStatus.TOO_HARD,
                "Skip", TaskStatus.SKIPPED),
                java.util.Arrays.stream(CompletionResult.values())
                        .collect(java.util.stream.Collectors.toMap(CompletionResult::label, CompletionResult::status)));
    }
}
