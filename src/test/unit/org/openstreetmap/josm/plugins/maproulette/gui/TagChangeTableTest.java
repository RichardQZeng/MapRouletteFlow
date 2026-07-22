// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TagChangeTableTest {
    @Test
    void keepColumnDefaultsTrueAndHonorsUserSelection() {
        final var table = new TagChangeTable();
        table.setValueAt("name", 0, 0);
        table.setValueAt("old", 0, 1);
        table.setValueAt("new", 0, 2);

        assertTrue(table.isKept(0));
        table.setValueAt(false, 0, 3);
        assertFalse(table.isKept(0));
        assertFalse(table.isKept(1));
    }

    @Test
    void readOnlyTableDoesNotExposeEditableKeepColumn() {
        final var table = new TagChangeTable(false);
        table.setValueAt("name", 0, 0);
        assertFalse(table.isCellEditable(0, 0));
        org.junit.jupiter.api.Assertions.assertEquals(3, table.getColumnCount());
    }
}
