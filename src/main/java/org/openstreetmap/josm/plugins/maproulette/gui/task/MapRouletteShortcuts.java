// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.gui.task;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.KeyEvent;
import java.util.EnumMap;
import java.util.Map;

import org.openstreetmap.josm.plugins.maproulette.workflow.CompletionResult;
import org.openstreetmap.josm.tools.Shortcut;

/** Registers task-workflow shortcuts independently of the map panel lifecycle. */
public final class MapRouletteShortcuts {
    private static final Map<CompletionResult, Shortcut> COMPLETION = registerCompletionShortcuts();
    private static final Shortcut INSTRUCTIONS = Shortcut.registerShortcut("maproulette:task",
            tr("MapRoulette: Current task"), KeyEvent.CHAR_UNDEFINED, Shortcut.NONE);
    private static final Shortcut SELECT_PRIMITIVES = Shortcut.registerShortcut("maproulette:select_task_primitives",
            tr("MapRoulette: Select task primitives"), KeyEvent.CHAR_UNDEFINED, Shortcut.NONE);

    private MapRouletteShortcuts() {
    }

    public static void registerAll() {
        // Class initialization performs registration.
    }

    public static Shortcut completion(CompletionResult result) {
        return COMPLETION.get(result);
    }

    public static Shortcut instructions() {
        return INSTRUCTIONS;
    }

    public static Shortcut selectPrimitives() {
        return SELECT_PRIMITIVES;
    }

    private static Map<CompletionResult, Shortcut> registerCompletionShortcuts() {
        final var shortcuts = new EnumMap<CompletionResult, Shortcut>(CompletionResult.class);
        for (var result : CompletionResult.values()) {
            final var key = result == CompletionResult.FIXED ? KeyEvent.VK_ENTER : KeyEvent.CHAR_UNDEFINED;
            final var modifiers = result == CompletionResult.FIXED ? Shortcut.CTRL_SHIFT : Shortcut.NONE;
            shortcuts.put(result, Shortcut.registerShortcut("maproulette:" + result.name().toLowerCase(java.util.Locale.ENGLISH),
                    tr("MapRoulette: {0}", result.label()), key, modifiers));
        }
        return Map.copyOf(shortcuts);
    }
}
