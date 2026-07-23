// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.workflow;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.DownloadPolicy;
import org.openstreetmap.josm.gui.layer.MainLayerManager;
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

    @Test
    void exactResolutionNeverSubstitutesAnotherLayer() {
        final var expected = layer("expected");
        final var other = layer("other");

        assertSame(expected, TaskDownloadLayerSelector.resolveExact(expected, expected, List.of(expected, other)));
        assertNull(TaskDownloadLayerSelector.resolveExact(expected, other, List.of(expected, other)));
        assertNull(TaskDownloadLayerSelector.resolveExact(expected, expected, List.of(other)));
    }

    @Test
    void exactPreparationActivatesTheRetainedLayer() {
        final var layerManager = new MainLayerManager();
        final var expected = layer("expected");
        final var other = layer("other");
        try {
            layerManager.addLayer(expected);
            layerManager.addLayer(other);
            layerManager.setActiveLayer(other);

            final var plan = TaskDownloadLayerSelector.prepareExact(layerManager, expected);

            assertSame(expected, plan.target());
            assertSame(expected, layerManager.getActiveLayer());
            assertSame(expected, layerManager.getEditLayer());
        } finally {
            layerManager.resetState();
        }
    }

    private static OsmDataLayer layer(String name) {
        return new OsmDataLayer(new DataSet(), name, null);
    }
}
