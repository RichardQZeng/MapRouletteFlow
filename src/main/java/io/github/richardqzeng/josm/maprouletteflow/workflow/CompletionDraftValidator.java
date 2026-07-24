// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.workflow;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.richardqzeng.josm.maprouletteflow.api.model.Challenge;
import org.openstreetmap.josm.tools.Utils;

/** Validation applied before a confirmation dialog mutates workflow state. */
public final class CompletionDraftValidator {
    private CompletionDraftValidator() {
    }

    public static List<String> validate(CompletionDraft draft, Challenge challenge, Set<String> requiredResponses) {
        final var errors = new java.util.ArrayList<String>();
        if (draft.comment().length() > CompletionDraft.MAX_COMMENT_LENGTH) {
            errors.add(tr("Comments cannot exceed {0} characters.", CompletionDraft.MAX_COMMENT_LENGTH));
        }
        if (draft.result() != CompletionResult.SKIP) {
            for (var name : requiredResponses) {
                final var value = draft.completionResponses().get(name);
                if (value == null || value instanceof String string && Utils.isStripEmpty(string)) {
                    errors.add(tr("A completion response is required for: {0}", name));
                }
            }
        }
        if (challenge != null && challenge.extra() != null && challenge.extra().limitTags()) {
            final var allowed = splitTags(challenge.extra().preferredTags());
            final var rejected = splitTags(draft.tags());
            rejected.removeAll(allowed);
            if (!rejected.isEmpty()) {
                errors.add(tr("Only the challenge's preferred MR tags may be used: {0}", String.join(", ", allowed)));
            }
        }
        return List.copyOf(errors);
    }

    public static Set<String> splitTags(String tags) {
        final var result = new LinkedHashSet<String>();
        if (!Utils.isStripEmpty(tags)) {
            Arrays.stream(tags.split(",")).map(String::trim).filter(tag -> !tag.isEmpty()).forEach(result::add);
        }
        return result;
    }
}
