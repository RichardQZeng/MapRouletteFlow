// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.data;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.ConditionalOptionPaneUtil;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.plugins.maproulette.api.model.OSMChange;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Apply a cooperative change
 */
public class ApplyCooperativeChange {
    private static final String BULK_OPERATION_VERSION_MISMATCH = "maproulette.command.cooperative-change";
    private final OSMChange change;

    /**
     * Create a new cooperative change
     *
     * @param change The change to apply
     */
    public ApplyCooperativeChange(@Nonnull OSMChange change) {
        Objects.requireNonNull(change, "change");
        this.change = change;
    }

    /**
     * Generate the command for a specified dataset
     *
     * @param ds The dataset to generate the command for
     * @return The generated command
     */
    @Nullable
    public Command generateCommand(@Nonnull DataSet ds) {
        return generateCommand(ds, (update, key) -> true);
    }

    /** Generate a command containing only tag changes retained by the user. */
    @Nullable
    public Command generateCommand(@Nonnull DataSet ds, BiPredicate<org.openstreetmap.josm.plugins.maproulette.api.model.ElementUpdate, String> keep) {
        Objects.requireNonNull(ds, "ds");
        Objects.requireNonNull(keep, "keep");
        if (this.change.creates().length > 0) {
            return null;
        }
        ConditionalOptionPaneUtil.startBulkOperation(BULK_OPERATION_VERSION_MISMATCH);
        final List<Command> commands = new ArrayList<>();
        try {
            for (var update : this.change.updates()) {
                final var primitive = ds.getPrimitiveById(update.osmId(), update.osmType());
                if (primitive == null) {
                    return null;
                }
                if (update.version() >= 0 && primitive.getVersion() != update.version()
                        && !ConditionalOptionPaneUtil.showConfirmationDialog(BULK_OPERATION_VERSION_MISMATCH,
                                MainApplication.getMainFrame(),
                                tr("The {0} {1} (v{2}) has changed since the challenge was created.<br>Continue?",
                                        update.osmType().getAPIName(), update.osmId(), update.version()),
                                tr("Cooperative Challenge: Mismatched version types"), JOptionPane.YES_NO_OPTION,
                                JOptionPane.WARNING_MESSAGE, JOptionPane.YES_OPTION)) {
                    return null;
                }
                final var tagMap = new HashMap<String, String>();
                update.tags().updates().forEach((key, value) -> {
                    if (keep.test(update, key)) {
                        tagMap.put(key, value);
                    }
                });
                for (var delete : update.tags().deletes()) {
                    if (keep.test(update, delete)) {
                        tagMap.put(delete, null);
                    }
                }
                if (!tagMap.isEmpty()) {
                    commands.add(new ChangePropertyCommand(ds, Collections.singleton(primitive), tagMap));
                }
            }
        } finally {
            ConditionalOptionPaneUtil.endBulkOperation(BULK_OPERATION_VERSION_MISMATCH);
        }
        return commands.isEmpty() ? null : SequenceCommand.wrapIfNeeded(tr("MapRoulette Cooperative Challenge"), commands);
    }

    @Override
    public int hashCode() {
        return this.change.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj instanceof ApplyCooperativeChange
                && this.change.equals(((ApplyCooperativeChange) obj).change);
    }
}
