// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.plugins.maproulette.api.enums.TaskStatus;
import org.openstreetmap.josm.plugins.maproulette.api.model.Challenge;
import org.openstreetmap.josm.plugins.maproulette.api.model.ChallengeExtra;
import org.openstreetmap.josm.plugins.maproulette.api.model.Task;
import org.openstreetmap.josm.plugins.maproulette.gui.preferences.MapRouletteTaskPreference.NextMode;

class CompletionDraftValidatorTest {
    @Test
    void validatesCommentResponsesAndLimitedTags() {
        final var draft = new CompletionDraft(task(), CompletionResult.NOT_AN_ISSUE, "x".repeat(5_001),
                "allowed, other", null, Map.of("answered", ""), NextMode.NEARBY);
        final var errors = CompletionDraftValidator.validate(draft, challenge(true), Set.of("answered"));

        assertEquals(3, errors.size());
    }

    @Test
    void skipDoesNotRequireInstructionResponsesAndUnlimitedTagsAreAccepted() {
        final var draft = new CompletionDraft(task(), CompletionResult.SKIP, "", "anything", Boolean.FALSE, Map.of(),
                NextMode.RANDOM);
        assertTrue(CompletionDraftValidator.validate(draft, challenge(false), Set.of("answer")).isEmpty());
    }

    @Test
    void tagParsingTrimsAndDeduplicatesSuggestions() {
        assertEquals(Set.of("one", "two"), CompletionDraftValidator.splitTags(" one, two,one, "));
    }

    private static Task task() {
        return new Task(1, "task", null, null, 10, null, null, new DataSet(), null, TaskStatus.CREATED, null, null,
                null, null, 0, null, null, null, false, null, "");
    }

    private static Challenge challenge(boolean limitTags) {
        final var extra = new ChallengeExtra(1, 1, 1, 1, null, null, false, null, null, "allowed", null, limitTags,
                false, null, null, false, null);
        return new Challenge(10, "challenge", null, null, null, false, null, null, null, null, extra, null, null, null,
                null, null, null, null, null);
    }
}
