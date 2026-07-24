// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.gui.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import org.junit.jupiter.api.Test;
import io.github.richardqzeng.josm.maprouletteflow.workflow.CompletionResult;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;

@BasicPreferences
class MapRouletteShortcutsTest {
    @Test
    void registersAllTaskActionsWithFixedDefault() {
        MapRouletteShortcuts.registerAll();

        for (var result : CompletionResult.values()) {
            assertNotNull(MapRouletteShortcuts.completion(result));
        }
        final var fixed = MapRouletteShortcuts.completion(CompletionResult.FIXED);
        assertEquals(KeyEvent.VK_ENTER, fixed.getAssignedKey());
        assertEquals(InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, fixed.getAssignedModifier());
        assertTrue(fixed.isAssignedDefault());
        assertNotNull(MapRouletteShortcuts.instructions());
        assertNotNull(MapRouletteShortcuts.selectPrimitives());
    }
}
