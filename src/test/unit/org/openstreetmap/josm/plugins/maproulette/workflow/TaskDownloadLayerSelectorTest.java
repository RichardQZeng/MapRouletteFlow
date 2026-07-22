// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.workflow;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.DownloadPolicy;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;

@BasicPreferences
class TaskDownloadLayerSelectorTest {
    @Test
    void prefersActiveDownloadableEditLayer() {
        final var active = layer("active");
        final var other = layer("other");

        assertSame(active, TaskDownloadLayerSelector.chooseTarget(active, List.of(other, active)));
    }

    @Test
    void usesOnlyDownloadableLayerOrLetsJosmCreateOneWhenAmbiguous() {
        final var blocked = layer("blocked");
        blocked.getDataSet().setDownloadPolicy(DownloadPolicy.BLOCKED);
        final var downloadable = layer("downloadable");

        assertSame(downloadable,
                TaskDownloadLayerSelector.chooseTarget(blocked, List.of(blocked, downloadable)));
        assertNull(TaskDownloadLayerSelector.chooseTarget(null, List.of(downloadable, layer("second"))));
        assertNull(TaskDownloadLayerSelector.chooseTarget(null, List.of(blocked)));
    }

    @Test
    void resolvesMergedTargetAndNaturallyCreatedLayer() {
        final var existing = layer("existing");
        final var created = layer("created");

        assertSame(existing,
                TaskDownloadLayerSelector.resolveResult(List.of(existing), existing, existing, List.of(existing)));
        assertSame(created,
                TaskDownloadLayerSelector.resolveResult(List.of(existing), null, created, List.of(existing, created)));
    }

    private static OsmDataLayer layer(String name) {
        return new OsmDataLayer(new DataSet(), name, null);
    }
}
