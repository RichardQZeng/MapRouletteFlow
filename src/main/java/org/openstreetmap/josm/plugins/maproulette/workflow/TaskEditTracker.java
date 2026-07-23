// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.workflow;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.maproulette.api.model.Task;
import org.openstreetmap.josm.plugins.maproulette.data.TaskPrimitives;

/** Tracks task-period JOSM commands and turns their final diff into an editable MapRoulette comment. */
public final class TaskEditTracker implements PropertyChangeListener {
    private static final TaskEditTracker INSTANCE = new TaskEditTracker(WorkflowController.getInstance());
    private static final int MAX_COMMENT_LENGTH = 5_000;
    private static final List<String> MERGE_IDENTITY_KEYS = List.of("name", "gnis:feature_id", "ref:gnis", "wikidata");

    private final WorkflowController workflow;
    private final Set<Command> commandsAtStart = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<OsmPrimitive, PrimitiveSnapshot> baseline = new IdentityHashMap<>();
    private final Set<OsmPrimitive> taskSeeds = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean listening;
    private long taskId = -1;
    private OsmDataLayer editLayer;

    TaskEditTracker(WorkflowController workflow) {
        this.workflow = Objects.requireNonNull(workflow);
    }

    public static TaskEditTracker getInstance() {
        return INSTANCE;
    }

    public synchronized void start() {
        if (!listening) {
            listening = true;
            workflow.addPropertyChangeListener(this);
            sync(workflow.snapshot());
        }
    }

    public synchronized void shutdown() {
        pause();
        clear();
    }

    /** Detach during plugin replacement while preserving a retained task's edit baseline. */
    public synchronized void pause() {
        if (listening) {
            workflow.removePropertyChangeListener(this);
            listening = false;
        }
    }

    public synchronized void discard() {
        clear();
    }

    @Override
    public synchronized void propertyChange(PropertyChangeEvent event) {
        if (WorkflowController.SNAPSHOT_PROPERTY.equals(event.getPropertyName())) {
            sync((WorkflowController.Snapshot) event.getNewValue());
        }
    }

    /** Compose the current task's active command diff. Returns blank when no task-period edits remain. */
    public synchronized String compose(Task task, OsmDataLayer layer) {
        if (task == null || layer == null || task.id() != taskId || layer != editLayer) {
            return "";
        }
        final var commands = scopedCommands(layer);
        if (commands.isEmpty()) {
            return "";
        }
        final var touched = Collections.newSetFromMap(new IdentityHashMap<OsmPrimitive, Boolean>());
        commands.forEach(command -> touched.addAll(command.getParticipatingPrimitives()));
        final var ordered = touched.stream().sorted(PRIMITIVE_ORDER).toList();
        final var sentences = new ArrayList<String>();
        final var mergedSources = Collections.newSetFromMap(new IdentityHashMap<OsmPrimitive, Boolean>());
        final var mergedTargets = Collections.newSetFromMap(new IdentityHashMap<OsmPrimitive, Boolean>());

        for (var command : commands) {
            final var participants = command.getParticipatingPrimitives();
            final var survivors = participants.stream().filter(primitive -> !primitive.isDeleted())
                    .filter(baseline::containsKey).sorted(MERGE_TARGET_ORDER).toList();
            final var sources = participants.stream()
                    .filter(primitive -> wasDeletedDuringTask(primitive) || canBeAbsorbedNode(primitive))
                    .sorted(PRIMITIVE_ORDER).toList();
            for (var source : sources) {
                if (mergedSources.contains(source) || !shouldDescribe(source)) {
                    continue;
                }
                final var target = survivors.stream().filter(candidate -> candidate != source)
                        .filter(candidate -> sharesIdentity(source, candidate))
                        .filter(candidate -> wasDeletedDuringTask(source) || isAbsorbedInto(source, candidate))
                        .findFirst().orElse(null);
                if (target != null) {
                    mergedSources.add(source);
                    mergedTargets.add(target);
                    sentences.add(describeMerge(source, target));
                }
            }
        }

        for (var primitive : ordered) {
            if (mergedSources.contains(primitive) || mergedTargets.contains(primitive)) {
                continue;
            }
            final var before = baseline.get(primitive);
            if (before == null && !primitive.isDeleted()) {
                sentences.add(tr("Created {0}.", describe(primitive)));
                continue;
            }
            if (wasDeletedDuringTask(primitive)) {
                if (shouldDescribe(primitive)) {
                    sentences.add(tr("Deleted {0}.", describe(primitive)));
                }
                continue;
            }
            if (before == null) {
                continue;
            }
            final var tagSummary = tagsChanged(before.tags(), primitive.getKeys());
            if (!tagSummary.isEmpty()) {
                sentences.add(tr("Updated tags on {0}: {1}.", describe(primitive), tagSummary));
            }
            if (!Objects.equals(before.geometry(), geometry(primitive))) {
                sentences.add(tr("Updated geometry of {0}.", describe(primitive)));
            }
        }
        final var result = String.join(" ", new LinkedHashSet<>(sentences));
        return result.length() <= MAX_COMMENT_LENGTH ? result : result.substring(0, MAX_COMMENT_LENGTH - 3) + "...";
    }

    /** Rebase untouched server data after an active re-download without losing task-period command history. */
    public synchronized void redownloaded(Task task, OsmDataLayer layer) {
        if (task == null || layer == null || task.id() != taskId || layer != editLayer) {
            return;
        }
        final var touched = Collections.newSetFromMap(new IdentityHashMap<OsmPrimitive, Boolean>());
        activeCommands(layer).forEach(command -> touched.addAll(command.getParticipatingPrimitives()));
        for (var primitive : layer.getDataSet().allPrimitives()) {
            if (!touched.contains(primitive)) {
                baseline.put(primitive, snapshot(primitive));
            }
        }
    }

    private void sync(WorkflowController.Snapshot snapshot) {
        if (snapshot.activeTask() == null || snapshot.editLayer() == null) {
            clear();
        } else if (snapshot.activeTask().id() != taskId || snapshot.editLayer() != editLayer) {
            activate(snapshot.activeTask(), snapshot.editLayer());
        }
    }

    private void activate(Task task, OsmDataLayer layer) {
        clear();
        taskId = task.id();
        editLayer = layer;
        commandsAtStart.addAll(UndoRedoHandler.getInstance().getUndoCommands());
        commandsAtStart.addAll(UndoRedoHandler.getInstance().getRedoCommands());
        for (var primitive : layer.getDataSet().allPrimitives()) {
            baseline.put(primitive, snapshot(primitive));
        }
        if (task.geometries() != null) {
            task.geometries().allNonDeletedPrimitives().stream()
                    .map(primitive -> layer.getDataSet().getPrimitiveById(primitive.getPrimitiveId()))
                    .filter(Objects::nonNull).forEach(taskSeeds::add);
        }
        TaskPrimitives.getPrimitiveIds(task, null).stream().map(layer.getDataSet()::getPrimitiveById)
                .filter(Objects::nonNull).forEach(taskSeeds::add);
    }

    private void clear() {
        taskId = -1;
        editLayer = null;
        commandsAtStart.clear();
        baseline.clear();
        taskSeeds.clear();
    }

    private List<Command> activeCommands(OsmDataLayer layer) {
        return UndoRedoHandler.getInstance().getUndoCommands().stream()
                .filter(command -> !commandsAtStart.contains(command) && command.getAffectedDataSet() == layer.getDataSet())
                .toList();
    }

    private List<Command> scopedCommands(OsmDataLayer layer) {
        final var commands = activeCommands(layer);
        if (taskSeeds.isEmpty()) {
            return commands;
        }
        final var included = Collections.newSetFromMap(new IdentityHashMap<OsmPrimitive, Boolean>());
        included.addAll(taskSeeds);
        final var selected = Collections.newSetFromMap(new IdentityHashMap<Command, Boolean>());
        boolean changed;
        do {
            changed = false;
            for (var command : commands) {
                final var participants = command.getParticipatingPrimitives();
                if (participants.stream().anyMatch(included::contains) && selected.add(command)) {
                    included.addAll(participants);
                    changed = true;
                }
            }
        } while (changed);
        return commands.stream().filter(selected::contains).toList();
    }

    private boolean wasDeletedDuringTask(OsmPrimitive primitive) {
        final var before = baseline.get(primitive);
        return before != null && !before.deleted() && primitive.isDeleted();
    }

    private boolean shouldDescribe(OsmPrimitive primitive) {
        final var before = baseline.get(primitive);
        return !(primitive instanceof Node) || !primitive.getKeys().isEmpty()
                || before != null && !before.tags().isEmpty();
    }

    private boolean canBeAbsorbedNode(OsmPrimitive primitive) {
        final var before = baseline.get(primitive);
        return primitive instanceof Node && !primitive.isDeleted() && primitive.getKeys().isEmpty() && before != null
                && !before.tags().isEmpty();
    }

    private static boolean isAbsorbedInto(OsmPrimitive source, OsmPrimitive target) {
        return source instanceof Node node && target instanceof Way way && way.isClosed() && way.getNodes().contains(node);
    }

    private boolean sharesIdentity(OsmPrimitive first, OsmPrimitive second) {
        final var firstTags = baseline.containsKey(first) ? baseline.get(first).tags() : first.getKeys();
        final var secondTags = baseline.containsKey(second) ? baseline.get(second).tags() : second.getKeys();
        for (var key : MERGE_IDENTITY_KEYS) {
            final var value = firstTags.get(key);
            if (value != null && !value.isBlank() && value.equals(secondTags.get(key))) {
                return true;
            }
        }
        return false;
    }

    private String describeMerge(OsmPrimitive source, OsmPrimitive target) {
        final var sourceBefore = baseline.get(source).tags();
        final var targetBefore = baseline.get(target).tags();
        final var targetAfter = target.getKeys();
        final var handled = new LinkedHashSet<String>();
        final var changes = new ArrayList<String>();
        if (("reservoir".equals(sourceBefore.get("landuse")) || "reservoir".equals(targetBefore.get("landuse")))
                && !targetAfter.containsKey("landuse") && "reservoir".equals(targetAfter.get("water"))) {
            handled.add("landuse");
            handled.add("water");
            changes.add(tr("replaced {0} with {1}", code("landuse=reservoir"), code("water=reservoir")));
        }
        final var transferred = new ArrayList<String>();
        new TreeMap<>(sourceBefore).forEach((key, value) -> {
            if (!handled.contains(key) && value.equals(targetAfter.get(key))
                    && !value.equals(targetBefore.get(key))) {
                handled.add(key);
                if (!MERGE_IDENTITY_KEYS.contains(key)) {
                    transferred.add(code(key + "=" + value));
                }
            }
        });
        if (!transferred.isEmpty()) {
            changes.add(tr("transferred {0}", String.join(", ", transferred)));
        }
        final var remainingBefore = new LinkedHashMap<>(targetBefore);
        final var remainingAfter = new LinkedHashMap<>(targetAfter);
        handled.forEach(key -> {
            remainingBefore.remove(key);
            remainingAfter.remove(key);
        });
        final var remainingChanges = tagsChanged(remainingBefore, remainingAfter);
        if (!remainingChanges.isEmpty()) {
            changes.add(remainingChanges);
        }
        return changes.isEmpty() ? tr("Merged {0} into {1}.", describe(source), describe(target))
                : tr("Merged {0} into {1}; {2}.", describe(source), describe(target), String.join("; ", changes));
    }

    private String describe(OsmPrimitive primitive) {
        final var before = baseline.get(primitive);
        final var name = primitive.get("name") != null ? primitive.get("name")
                : before == null ? null : before.tags().get("name");
        final var type = primitive instanceof Way way && way.isClosed() ? tr("area")
                : primitive.getType().getAPIName();
        final var id = primitive.getUniqueId();
        if (name != null && !name.isBlank()) {
            return id > 0 ? tr("{0} ({1} {2})", name, type, id) : tr("{0} ({1})", name, type);
        }
        return id > 0 ? tr("{0} {1}", type, id) : type;
    }

    private static String tagsChanged(Map<String, String> before, Map<String, String> after) {
        final var removed = new ArrayList<String>();
        final var set = new ArrayList<String>();
        new TreeMap<>(before).forEach((key, value) -> {
            if (!after.containsKey(key)) {
                removed.add(code(key + "=" + value));
            }
        });
        new TreeMap<>(after).forEach((key, value) -> {
            if (!Objects.equals(before.get(key), value)) {
                set.add(code(key + "=" + value));
            }
        });
        final var changes = new ArrayList<String>();
        if (!removed.isEmpty()) {
            changes.add(tr("removed {0}", String.join(", ", removed)));
        }
        if (!set.isEmpty()) {
            changes.add(tr("set {0}", String.join(", ", set)));
        }
        return String.join("; ", changes);
    }

    private static String code(String value) {
        return "`" + value.replace("`", "'") + "`";
    }

    private static PrimitiveSnapshot snapshot(OsmPrimitive primitive) {
        return new PrimitiveSnapshot(primitive.getKeys(), primitive.isDeleted(), geometry(primitive));
    }

    private static List<String> geometry(OsmPrimitive primitive) {
        if (primitive instanceof Node node) {
            return node.getCoor() == null ? List.of() : List.of(node.getCoor().toString());
        }
        if (primitive instanceof Way way) {
            return way.getNodes().stream().map(node -> Long.toString(node.getUniqueId())).toList();
        }
        if (primitive instanceof Relation relation) {
            return relation.getMembers().stream().map(member -> member.getType().getAPIName() + ':'
                    + member.getMember().getUniqueId() + ':' + member.getRole()).toList();
        }
        return List.of();
    }

    private static final Comparator<OsmPrimitive> PRIMITIVE_ORDER = Comparator
            .comparing((OsmPrimitive primitive) -> primitive.getType().ordinal())
            .thenComparingLong(OsmPrimitive::getUniqueId);
    private static final Comparator<OsmPrimitive> MERGE_TARGET_ORDER = Comparator
            .comparing((OsmPrimitive primitive) -> primitive instanceof Node)
            .thenComparing(PRIMITIVE_ORDER);

    private record PrimitiveSnapshot(Map<String, String> tags, boolean deleted, List<String> geometry) {
        PrimitiveSnapshot {
            tags = Map.copyOf(new LinkedHashMap<>(tags));
            geometry = List.copyOf(geometry);
        }
    }
}
