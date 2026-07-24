// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.gui.layer;

import static org.openstreetmap.josm.gui.layer.OsmDataLayer.PROPERTY_HIDE_LABELS_WHILE_DRAGGING;
import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.swing.Action;
import javax.swing.Icon;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.INode;
import org.openstreetmap.josm.data.osm.IWay;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.QuadBuckets;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.data.osm.visitor.paint.StyledMapRenderer;
import org.openstreetmap.josm.data.preferences.CachingProperty;
import org.openstreetmap.josm.data.preferences.IntegerProperty;
import org.openstreetmap.josm.data.preferences.NamedColorProperty;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.draw.SymbolShape;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.mappaint.mapcss.MapCSSStyleSource;
import org.openstreetmap.josm.gui.mappaint.styleelement.MapImage;
import org.openstreetmap.josm.gui.mappaint.styleelement.Symbol;
import org.openstreetmap.josm.gui.util.GuiHelper;
import io.github.richardqzeng.josm.maprouletteflow.api.MRColors;
import io.github.richardqzeng.josm.maprouletteflow.api.enums.TaskStatus;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Identifier;
import io.github.richardqzeng.josm.maprouletteflow.api.model.TaskClusteredPoint;
import io.github.richardqzeng.josm.maprouletteflow.api_caching.TaskCache;
import io.github.richardqzeng.josm.maprouletteflow.gui.task.list.TaskListPanel;
import io.github.richardqzeng.josm.maprouletteflow.gui.task.list.TaskPreviewBounds;
import io.github.richardqzeng.josm.maprouletteflow.workflow.WorkflowController;
import org.openstreetmap.josm.tools.ColorHelper;
import org.openstreetmap.josm.tools.Geometry;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.ListenerList;

/**
 * A layer for showing task locations
 */
public class MapRouletteClusteredPointLayer extends Layer implements MouseListener {
    /** The number of clicks for deselection */
    private static final CachingProperty<Integer> DESELECT_CLICK_COUNT = new IntegerProperty(
            "maprouletteflow.task.deselect.mouse.click.count", 3).cached();

    /**
     * The style source, mostly used for preferences
     */
    private static final MapCSSStyleSource STYLE = new MapCSSStyleSource(
            "setting::maprouletteflow{type:color;label:tr(\"MapRoulette Flow task geometry\");default:maprouletteflow#6495ed;}"
                    + "way::maprouletteflow{casing-width:3;casing-color:setting(\"maprouletteflow\");opacity:0.2;}"
                    + "node:tagged::maprouletteflow{symbol-shape:circle;symbol-size:15;symbol-stroke-color:setting(\"maprouletteflow\");"
                    + "symbol-fill-color:setting(\"maprouletteflow\");symbol-fill-opacity:0.5;symbol-stroke-opacity:0.5;}");

    /**
     * The color for locked MR tasks
     */
    private static final NamedColorProperty LOCKED_TASK_COLOR = new NamedColorProperty(
            NamedColorProperty.COLOR_CATEGORY_MAPPAINT, STYLE.url, marktr("MapRoulette Flow task geometry"),
            ColorHelper.html2color("#6495ed"));

    /**
     * The cached image for tasks
     */
    private static final MapImage MR_IMAGE = new MapImage("maprouletteflow/plugin", STYLE);

    /**
     * The point bucket
     */
    private final QuadBuckets<TaskClusteredPoint> pointBucket = new QuadBuckets<>();
    /**
     * The id mapping
     */
    private final Set<TaskClusteredPoint> pointMap = new TreeSet<>(Comparator.comparingLong(Identifier::id));
    /**
     * The selected points
     */
    private final Collection<TaskClusteredPoint> selected = new HashSet<>();
    /**
     * The listener for selection updates
     */
    private final ListenerList<Consumer<Collection<TaskClusteredPoint>>> selectionListeners = ListenerList.create();
    /**
     * The listeners for notifying of updated data
     */
    private final ListenerList<Consumer<Map<Long, TaskClusteredPoint>>> updatedDataListeners = ListenerList.create();
    /**
     * The bounds of the points
     */
    private Bounds bounds;

    /**
     * Create a new layer
     *
     * @param bounds The bounds of the layer
     * @param points The points for the layer
     */
    public MapRouletteClusteredPointLayer(Bounds bounds, Collection<TaskClusteredPoint> points) {
        super(tr("MapRoulette Flow Task Layer"));
        this.pointBucket.addAll(points);
        this.bounds = TaskPreviewBounds.forTasks(points).orElse(bounds);
        this.pointMap.addAll(points);
    }

    /**
     * Refresh tasks
     *
     * @param tcMap The map of task id to point
     */
    public void refreshTasks(Map<Long, ? extends TaskClusteredPoint> tcMap) {
        if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
            GuiHelper.runInEDTAndWaitAndReturn(() -> {
                refreshTasks(tcMap);
                return null;
            });
            return;
        }
        final var current = this.pointMap.stream().collect(Collectors.toMap(Identifier::id, c -> c));
        for (var entry : tcMap.entrySet()) {
            if (current.containsKey(entry.getKey())) {
                final var toRemove = current.get(entry.getKey());
                this.pointBucket.remove(toRemove);
                this.pointMap.remove(toRemove);
            }
            this.pointMap.add(entry.getValue());
            this.pointBucket.add(entry.getValue());
        }
        this.bounds = TaskPreviewBounds.forTasks(this.pointMap).orElse(null);
        final Map<Long, TaskClusteredPoint> updateCollection = this.pointBucket.stream()
                .collect(Collectors.toMap(Identifier::id, c -> c));
        this.updatedDataListeners.fireEvent(consumer -> consumer.accept(updateCollection));
        GuiHelper.runInEDT(this::invalidate);
    }

    @Override
    public Icon getIcon() {
        return ImageProvider.get("maprouletteflow", "plugin", ImageProvider.ImageSizes.LAYER);
    }

    @Override
    public String getToolTipText() {
        return tr("MapRoulette Flow Task Layer: ");
    }

    @Override
    public void mergeFrom(Layer from) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isMergable(Layer other) {
        return false;
    }

    @Override
    public void visitBoundingBox(BoundingXYVisitor v) {
        if (this.bounds != null) {
            v.visit(this.bounds);
        }
    }

    @Override
    public Object getInfoComponent() {
        return null;
    }

    @Override
    public Action[] getMenuEntries() {
        return new Action[0];
    }

    @Override
    public void paint(Graphics2D g, MapView mv, Bounds bounds) {
        final var box = bounds.toBBox();
        final var painter = new StyledMapRenderer(g, mv, false);
        final var mrColor = LOCKED_TASK_COLOR.get();
        final var mrColorOpacity = new Color(mrColor.getRed(), mrColor.getGreen(), mrColor.getBlue(), 128);
        final var taskListPanel = MainApplication.getMap().getToggleDialog(TaskListPanel.class);
        final var listSelected = taskListPanel == null ? Collections.<TaskClusteredPoint>emptyList()
                : taskListPanel.getSelected();

        painter.enableSlowOperations(mv.getMapMover() == null || !mv.getMapMover().movementInProgress()
                || !PROPERTY_HIDE_LABELS_WHILE_DRAGGING.get());
        painter.getSettings(false);

        final var fixedColor = ColorHelper.alphaMultiply(MRColors.statusColor(TaskStatus.FIXED), 0.5f);
        final var skippedColor = ColorHelper.alphaMultiply(MRColors.statusColor(TaskStatus.SKIPPED), 0.5f);
        final var falsePositiveColor = ColorHelper.alphaMultiply(MRColors.statusColor(TaskStatus.FALSE_POSITIVE), 0.5f);
        final var alreadyFixedColor = ColorHelper.alphaMultiply(MRColors.statusColor(TaskStatus.ALREADY_FIXED), 0.5f);
        final var tooHardColor = ColorHelper.alphaMultiply(MRColors.statusColor(TaskStatus.TOO_HARD), 0.5f);
        final var statusSymbol = new Symbol(SymbolShape.SQUARE, 25, null, fixedColor, fixedColor);
        final var disabledStatusSymbol = new Symbol(SymbolShape.SQUARE, 13, null, fixedColor, fixedColor);
        for (var point : this.pointBucket.search(box)) {
            if (WorkflowController.getInstance().getLockedTask(point.id()) == null && !TaskCache.isHidden(point)) {
                final boolean isSelected = this.selected.contains(point) || listSelected.contains(point);
                final var symbolColor = switch (point.status()) {
                case FIXED -> fixedColor;
                case FALSE_POSITIVE -> falsePositiveColor;
                case ALREADY_FIXED -> alreadyFixedColor;
                case SKIPPED -> skippedColor;
                case TOO_HARD -> tooHardColor;
                default -> null;
                };
                final var disabled = switch (point.status()) {
                case FIXED, FALSE_POSITIVE -> true;
                default -> false;
                };
                if (!disabled) {
                    if (symbolColor != null) {
                        painter.drawNodeSymbol(point.location(), statusSymbol, symbolColor, symbolColor);
                    }
                    painter.drawNodeIcon(point.location(), MR_IMAGE, false, isSelected, false, 0);
                } else {
                    // Yes, we want to switch the draw order
                    painter.drawNodeIcon(point.location(), MR_IMAGE, true, isSelected, false, 0);
                    if (symbolColor != null) {
                        painter.drawNodeSymbol(point.location(), disabledStatusSymbol, symbolColor, symbolColor);
                    }
                }
            }
        }

        final var symbol = new Symbol(SymbolShape.CIRCLE, 10, null, mrColorOpacity, mrColorOpacity);
        final var stroke = new BasicStroke(8f);
        for (var task : WorkflowController.getInstance().getLockedTasks()) {
            if (task.geometries().allNonDeletedPrimitives().isEmpty() && task.location() != null) {
                painter.drawNodeIcon(task.location(), MR_IMAGE, false, listSelected.contains(task), false, 0);
            }
            for (INode n : task.geometries().searchNodes(box)) {
                if (n.isTagged() || !n.isReferredByWays(1)) {
                    painter.drawNodeSymbol(n, symbol, mrColorOpacity, mrColorOpacity);
                }
            }
            for (IWay<?> w : task.geometries().searchWays(box)) {
                if (w.isTagged()) {
                    if (w.isClosed()) {
                        painter.drawArea(w, mrColorOpacity, null, 12.5f, 0.5f, false);
                    } else {
                        painter.drawWay(w, mrColorOpacity, stroke, stroke, null, 0, false, false, false, false);
                    }
                }
            }
            for (Relation r : task.geometries().searchRelations(box)) {
                if (r.isTagged() && r.isMultipolygon()) {
                    painter.drawArea(r, mrColorOpacity, null, null, null, false);
                }
            }
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getButton() != MouseEvent.BUTTON1) {
            return;
        }
        final var mapView = MainApplication.getMap().mapView;
        final var clickLocation = mapView.getLatLon(e.getPoint().x, e.getPoint().y);
        final var bbox = new BBox(clickLocation);
        final ArrayList<TaskClusteredPoint> add = e.isShiftDown() ? new ArrayList<>(this.selected) : new ArrayList<>();
        add.addAll(this.pointBucket.search(bbox));
        add.removeIf(TaskCache::isHidden);
        if (!add.isEmpty() && !this.selected.containsAll(add) && !add.containsAll(this.selected)) {
            this.setSelected(add);
            return;
        }
        bbox.addLatLon(clickLocation, 0.000_000_8 * mapView.getDist100Pixel());
        add.addAll(this.pointBucket.search(bbox));
        add.removeIf(TaskCache::isHidden);
        if (add.isEmpty()) {
            final var tNode = new Node(clickLocation);
            for (var task : WorkflowController.getInstance().getLockedTasks()) {
                if (!TaskCache.isHidden(task)) {
                    final var minDistance = task.geometries().searchPrimitives(bbox).stream()
                            .mapToDouble(prim -> Geometry.getDistance(tNode, prim)).min().orElse(Double.NaN);
                    if (!Double.isNaN(minDistance) && minDistance < mapView.getDist100Pixel() / 10) {
                        this.pointBucket.stream().filter(p -> p.id() == task.id()).findFirst().ifPresent(add::add);
                    }
                }
            }
        }
        if (add.isEmpty() && e.getClickCount() >= DESELECT_CLICK_COUNT.get()) {
            this.setSelected(Collections.emptyList());
        } else if (!add.isEmpty()) {
            this.setSelected(add);
        }
        this.invalidate();
    }

    /**
     * Set points as selected
     *
     * @param points The points to set as selected
     */
    @SuppressWarnings("UndefinedEquals")
    private void setSelected(Collection<TaskClusteredPoint> points) {
        final var lastSelected = new HashSet<>(this.selected);
        this.selected.clear();
        for (var p : points) {
            if (this.pointBucket.contains(p)) {
                this.selected.add(p);
            }
        }
        // Only fire listeners if the selection actually changed
        if (!this.selected.equals(lastSelected)) {
            this.selectionListeners.fireEvent(listener -> listener.accept(points));
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        // Do nothing
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        // Do nothing
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        // Do nothing
    }

    @Override
    public void mouseExited(MouseEvent e) {
        // Do nothing
    }

    @Override
    public synchronized void destroy() {
        super.destroy();
        this.selectionListeners.fireEvent(listener -> listener.accept(Collections.emptyList()));
        if (MainApplication.getMap() != null) {
            MainApplication.getMap().mapView.removeMouseListener(this);
        }
    }

    /**
     * Add a listener for data updates
     *
     * @param updateConsumer The consumer for data updates
     */
    public void addListener(Consumer<Map<Long, TaskClusteredPoint>> updateConsumer) {
        this.updatedDataListeners.addListener(updateConsumer);
    }

    /**
     * Remove a listener for data updates
     *
     * @param updateConsumer The consumer to remove for data updates
     */
    public void removeListener(Consumer<Map<Long, TaskClusteredPoint>> updateConsumer) {
        this.updatedDataListeners.removeListener(updateConsumer);
    }

    /**
     * Add a selection listener
     *
     * @param listener The listener for updated selection events
     */
    public void addSelectionListener(Consumer<Collection<TaskClusteredPoint>> listener) {
        this.selectionListeners.addListener(listener);
        listener.accept(Collections.unmodifiableCollection(this.selected));
    }

    /**
     * Get the tasks from this layer
     *
     * @return The tasks for this layer
     */
    public synchronized Collection<TaskClusteredPoint> getTasks() {
        return List.copyOf(this.pointBucket);
    }

    /** Replace layer content with the current single-task preview. */
    public void replaceTasks(Collection<? extends TaskClusteredPoint> tasks) {
        if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
            GuiHelper.runInEDTAndWaitAndReturn(() -> {
                replaceTasks(tasks);
                return null;
            });
            return;
        }
        this.pointBucket.clear();
        this.pointMap.clear();
        this.selected.clear();
        this.pointBucket.addAll(tasks);
        this.pointMap.addAll(tasks);
        this.bounds = TaskPreviewBounds.forTasks(tasks).orElse(null);
        final Map<Long, TaskClusteredPoint> updateCollection = this.pointMap.stream()
                .collect(Collectors.toMap(Identifier::id, point -> point));
        this.updatedDataListeners.fireEvent(consumer -> consumer.accept(updateCollection));
        GuiHelper.runInEDT(this::invalidate);
    }

    /** Current dynamic data bounds, primarily for autoscale and tests. */
    public synchronized Bounds getDataBounds() {
        return bounds;
    }

    /**
     * Remove a selection listener
     *
     * @param listener The listener for updated selection events
     */
    public void removeSelectionListener(Consumer<Collection<TaskClusteredPoint>> listener) {
        this.selectionListeners.removeListener(listener);
    }
}
