// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.workflow;

import java.util.List;

import org.openstreetmap.josm.gui.layer.MainLayerManager;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

/** Mirrors the target-layer choices made by JOSM downloads using {@code withNewLayer(false)}. */
public final class TaskDownloadLayerSelector {
    private TaskDownloadLayerSelector() {
        // Utility class.
    }

    static LayerPlan prepare(MainLayerManager layerManager) {
        final var layers = List.copyOf(layerManager.getLayersOfType(OsmDataLayer.class));
        final var target = chooseTarget(layerManager.getEditLayer(), layers);
        if (target != null && layerManager.getActiveLayer() != target) {
            layerManager.setActiveLayer(target);
        }
        return new LayerPlan(layers, target);
    }

    static LayerPlan prepareExact(MainLayerManager layerManager, OsmDataLayer target) {
        final var layers = List.copyOf(layerManager.getLayersOfType(OsmDataLayer.class));
        if (!layers.contains(target) || !target.isDownloadable()) {
            throw new IllegalStateException("The active task edit layer is not available for download");
        }
        if (layerManager.getActiveLayer() != target) {
            layerManager.setActiveLayer(target);
        }
        return new LayerPlan(layers, target);
    }

    static OsmDataLayer chooseTarget(OsmDataLayer editLayer, List<OsmDataLayer> layers) {
        if (editLayer != null && editLayer.isDownloadable()) {
            return editLayer;
        }
        final var downloadable = layers.stream().filter(OsmDataLayer::isDownloadable).toList();
        return downloadable.size() == 1 ? downloadable.get(0) : null;
    }

    static OsmDataLayer resolveResult(List<OsmDataLayer> before, OsmDataLayer target, OsmDataLayer editLayer,
            List<OsmDataLayer> after) {
        if (target != null && after.contains(target)) {
            return target;
        }
        final var added = after.stream().filter(layer -> !before.contains(layer)).toList();
        if (added.size() == 1) {
            return added.get(0);
        }
        return editLayer != null && after.contains(editLayer) && editLayer.isDownloadable() ? editLayer : null;
    }

    static OsmDataLayer resolveExact(OsmDataLayer expected, OsmDataLayer editLayer, List<OsmDataLayer> after) {
        return editLayer == expected && after.contains(expected) ? expected : null;
    }

    record LayerPlan(List<OsmDataLayer> layersBefore, OsmDataLayer target) {
    }
}
