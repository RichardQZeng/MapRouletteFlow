// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.gui.task.current;

import static org.openstreetmap.josm.gui.help.HelpUtil.ht;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.HashMap;

import javax.swing.DefaultComboBoxModel;
import javax.swing.Action;
import javax.swing.ButtonModel;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.Option;

import org.openstreetmap.josm.actions.AutoScaleAction;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.widgets.HtmlPanel;
import org.openstreetmap.josm.gui.widgets.VerticallyScrollablePanel;
import org.openstreetmap.josm.plugins.maproulette.api.model.Task;
import org.openstreetmap.josm.plugins.maproulette.data.TaskPrimitives;
import org.openstreetmap.josm.plugins.maproulette.data.ApplyCooperativeChange;
import org.openstreetmap.josm.plugins.maproulette.data.MergeDataSetsCommand;
import org.openstreetmap.josm.plugins.maproulette.gui.MRGuiHelper;
import org.openstreetmap.josm.plugins.maproulette.gui.TagChangeTable;
import org.openstreetmap.josm.plugins.maproulette.gui.task.MapRouletteShortcuts;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.Shortcut;
import org.openstreetmap.josm.tools.Utils;
import org.openstreetmap.josm.plugins.maproulette.workflow.WorkflowController;
import org.openstreetmap.josm.plugins.maproulette.workflow.CompletionResult;

/**
 * A panel for currently locked tasks
 */
public final class CurrentTaskPanel extends JPanel {
    /**
     * The serial UID for this panel
     */
    @Serial
    private static final long serialVersionUID = 6628674700523633284L;
    /**
     * The model for the task
     */
    private final VerticallyScrollablePanel panel = new VerticallyScrollablePanel(new GridBagLayout());
    /**
     * The label for the task id
     */
    private final JLabel idLabel = new JLabel();
    /**
     * The panel for instructions
     */
    private final HtmlPanel instructionPane = new HtmlPanel();
    /**
     * The panel for cooperative work
     */
    private final JPanel cooperativeWork = new JPanel();
    private TagChangeTable cooperativeTagTable = new TagChangeTable();
    private final ArrayList<CooperativeTagSelection> cooperativeSelections = new ArrayList<>();
    private boolean cooperativePrimitiveMissing;
    private Long cooperativeTaskId;
    /**
     * The actions for the task
     */
    private final InnerAction[] actions;
    /**
     * The current task
     */
    private Task task;
    /**
     * Create a new task panel
     */
    public CurrentTaskPanel() {
        super(new BorderLayout());

        final Supplier<Task> supplier = () -> this.task;
        final Supplier<HTMLDocument> docSupplier = () -> (HTMLDocument) this.instructionPane.getEditorPane()
                .getDocument();
        final var newActions = new InnerAction[] {
                new TaskStatusAction(CompletionResult.FIXED, supplier, docSupplier, this::prepareCooperativeFixed),
                new TaskStatusAction(CompletionResult.ALREADY_FIXED, supplier, docSupplier,
                        this::prepareCooperativeFixed),
                new TaskStatusAction(CompletionResult.NOT_AN_ISSUE, supplier, docSupplier,
                        this::prepareCooperativeFixed),
                new TaskStatusAction(CompletionResult.CANT_COMPLETE, supplier, docSupplier,
                        this::prepareCooperativeFixed),
                new TaskStatusAction(CompletionResult.SKIP, supplier, docSupplier, this::prepareCooperativeFixed),
                new SelectOsmPrimitives(supplier) };

        this.actions = newActions;
        this.instructionPane.enableClickableHyperlinks();
        final var gbc = GBC.eol();
        this.panel.add(this.idLabel, gbc);
        this.panel.add(new JLabel(tr("Instructions: ")), gbc);
        gbc.fill(GridBagConstraints.BOTH);
        this.panel.add(this.instructionPane, gbc);
        this.panel.add(this.cooperativeWork, gbc);
        add(this.panel.getVerticalScrollPane(), BorderLayout.CENTER);
        refreshPanel();
    }

    /**
     * Update the internal model
     *
     * @param task The task to show
     */
    public void refreshModel(final Task task) {
        final var sameTask = this.task == task || this.task != null && task != null && this.task.id() == task.id();
        this.task = task;
        if (sameTask) {
            cooperativeTagTable.setEnabled(WorkflowController.getInstance().snapshot().completionDraft() == null);
            updateActionStates();
        } else {
            refreshPanel();
        }
        revalidate();
        repaint();
    }

    /**
     * Get the actions for the panel
     * @return The actions
     */
    public Action[] actions() {
        return actions.clone();
    }

    HTMLDocument instructionDocument() {
        return (HTMLDocument) instructionPane.getEditorPane().getDocument();
    }

    public void destroy() {
        for (var action : actions) {
            action.destroy();
        }
    }

    private void refreshPanel() {
        final var currentTask = this.task;
        this.panel.setBackground(UIManager.getColor("Panel.background"));
        updateActionStates();
        if (currentTask == null) {
            cooperativeSelections.clear();
            cooperativeTaskId = null;
            cooperativePrimitiveMissing = false;
            this.idLabel.setText(null);
            this.instructionPane.setText(tr("Please select a locked task"));
            this.cooperativeWork.setVisible(false);
            return;
        }
        this.idLabel.setText(tr("ID: {0}", currentTask.id()));

        this.instructionPane.setText(MRGuiHelper.getInstructionText(currentTask));
        updateSelections((HTMLDocument) this.instructionPane.getEditorPane().getDocument(), this.task);
        if (currentTask.isCooperativeWorkOsmChange()) {
            final var cooperativePanel = this.cooperativeWork;
            final var previousKeep = new HashMap<String, Boolean>();
            if (Objects.equals(cooperativeTaskId, currentTask.id())) {
                for (var row = 0; row < cooperativeSelections.size(); row++) {
                    previousKeep.put(cooperativeSelections.get(row).key(), cooperativeTagTable.isKept(row));
                }
            }
            cooperativeTaskId = currentTask.id();
            cooperativeSelections.clear();
            cooperativePrimitiveMissing = false;
            cooperativeTagTable = new TagChangeTable();
            cooperativePanel.removeAll();
            cooperativePanel.setVisible(true);
            cooperativePanel.setLayout(new GridBagLayout());
            final var taskCooperativeWork = Objects.requireNonNull(currentTask.cooperativeWorkAsOsmChange());
            if (taskCooperativeWork.creates() != null && taskCooperativeWork.creates().length > 0) {
                cooperativePanel.add(new JLabel(tr("This task creates OSM objects, which is unsupported in this release.")),
                        GBC.eol());
            }
            if (taskCooperativeWork.updates() != null && taskCooperativeWork.updates().length > 0
                    && WorkflowController.getInstance().snapshot().editLayer() != null) {
                final var dataSet = WorkflowController.getInstance().snapshot().editLayer().getDataSet();
                cooperativePanel.add(new JLabel(tr("Tag Updates")), GBC.eol());
                final var table = cooperativeTagTable;
                table.setEnabled(WorkflowController.getInstance().snapshot().completionDraft() == null);
                cooperativePanel.add(table.getTableHeader(), GBC.eol().fill(GridBagConstraints.HORIZONTAL));
                cooperativePanel.add(table, GBC.eol().fill(GridBagConstraints.HORIZONTAL));
                var row = 0;
                for (var updates : taskCooperativeWork.updates()) {
                    final var current = dataSet.getPrimitiveById(updates.osmId(), updates.osmType());
                    if (current == null) {
                        cooperativePrimitiveMissing = true;
                        cooperativePanel.add(new JLabel(tr("Missing {0} {1}; cooperative changes cannot be applied.",
                                updates.osmType().getAPIName(), updates.osmId())), GBC.eol());
                        continue;
                    }
                    for (var change : updates.tags().updates().entrySet()) {
                        final var old = current.get(change.getKey());
                        table.setValueAt(change.getKey(), row, 0);
                        table.setValueAt(old, row, 1);
                        table.setValueAt(change.getValue(), row, 2);
                        cooperativeSelections.add(new CooperativeTagSelection(updates, change.getKey()));
                        table.setValueAt(previousKeep.getOrDefault(cooperativeSelections.get(row).key(), true), row, 3);
                        row++;
                    }
                    for (var change : updates.tags().deletes()) {
                        table.setValueAt(change, row, 0);
                        table.setValueAt(current.get(change), row, 1);
                        cooperativeSelections.add(new CooperativeTagSelection(updates, change));
                        table.setValueAt(previousKeep.getOrDefault(cooperativeSelections.get(row).key(), true), row, 3);
                        row++;
                    }
                }
            } else {
                this.cooperativeWork.setVisible(false);
            }
        } else {
            this.cooperativeWork.setVisible(false);
        }
    }

    private void updateActionStates() {
        for (var action : actions) {
            action.updateEnabledState();
        }
    }

    private boolean prepareCooperativeFixed(Task candidate) {
        if (candidate == null || candidate.cooperativeWork() == null) {
            return true;
        }
        final var editLayer = WorkflowController.getInstance().snapshot().editLayer();
        if (editLayer == null) {
            return false;
        }
        final var dataSet = editLayer.getDataSet();
        if (candidate.isCooperativeWorkOsmChange()) {
            final var change = candidate.cooperativeWorkAsOsmChange();
            if (change.creates().length > 0) {
                javax.swing.JOptionPane.showMessageDialog(MainApplication.getMainFrame(),
                        tr("Cooperative object creation is unsupported in this release."),
                        tr("Cannot apply cooperative task"), javax.swing.JOptionPane.WARNING_MESSAGE);
                return false;
            }
            if (cooperativePrimitiveMissing) {
                return false;
            }
            if (!cooperativeSelections.isEmpty()) {
                var kept = false;
                for (var row = 0; row < cooperativeSelections.size() && !kept; row++) {
                    kept = cooperativeTagTable.isKept(row);
                }
                if (!kept) {
                    return true;
                }
            }
            final var command = new ApplyCooperativeChange(change).generateCommand(dataSet,
                    (update, key) -> isCooperativeChangeKept(update, key));
            if (command == null) {
                javax.swing.JOptionPane.showMessageDialog(MainApplication.getMainFrame(),
                        tr("The cooperative changes could not be applied. Check missing objects and selected changes."),
                        tr("Cannot apply cooperative task"), javax.swing.JOptionPane.WARNING_MESSAGE);
                return false;
            }
            UndoRedoHandler.getInstance().add(command);
            return true;
        }
        if (candidate.isCooperativeWorkOsc()) {
            final var choice = javax.swing.JOptionPane.showOptionDialog(MainApplication.getMainFrame(),
                    tr("Apply the cooperative OSC to the task edit layer?"), tr("Cooperative MapRoulette task"),
                    javax.swing.JOptionPane.YES_NO_CANCEL_OPTION, javax.swing.JOptionPane.QUESTION_MESSAGE, null,
                    new String[] { tr("Apply"), tr("Show"), tr("Cancel") }, tr("Apply"));
            final var osc = candidate.cooperativeWorkAsOsc();
            if (choice == 0) {
                UndoRedoHandler.getInstance().add(new MergeDataSetsCommand(dataSet, osc.a, true, null));
                return true;
            }
            if (choice == 1) {
                MainApplication.getLayerManager().addLayer(new OsmDataLayer(osc.a, candidate.name(), null));
            }
            return false;
        }
        return false;
    }

    private boolean isCooperativeChangeKept(
            org.openstreetmap.josm.plugins.maproulette.api.model.ElementUpdate update, String key) {
        for (var row = 0; row < cooperativeSelections.size(); row++) {
            final var selection = cooperativeSelections.get(row);
            if (selection.update().equals(update) && selection.tag().equals(key)) {
                return cooperativeTagTable.isKept(row);
            }
        }
        return false;
    }

    private record CooperativeTagSelection(
            org.openstreetmap.josm.plugins.maproulette.api.model.ElementUpdate update, String tag) {
        String key() {
            return update.osmType() + ":" + update.osmId() + ":" + tag;
        }
    }

    /**
     * Update the selections in a doc from the stored selections
     * @param doc The doc to update
     * @param task The task to get the selections for
     */
    private static void updateSelections(HTMLDocument doc, Task task) {
        final var modifiedTask = WorkflowController.getInstance().snapshot().completionDraft();
        if (modifiedTask != null && modifiedTask.task().id() == task.id()) {
            final var selectIterator = doc.getIterator(HTML.Tag.SELECT);
            final var selectListener = new SelectComboBoxListener(doc, task);
            while (selectIterator.isValid()) {
                final var attribs = selectIterator.getAttributes();
                if (attribs.getAttribute(HTML.Attribute.NAME) != null) {
                    final var name = (String) attribs.getAttribute(HTML.Attribute.NAME);
                    if (modifiedTask.completionResponses().get(name) != null && attribs
                            .getAttribute(StyleConstants.ModelAttribute)instanceof DefaultComboBoxModel<?> listModel) {
                        listModel.addListDataListener(selectListener);
                        final var expectedOption = modifiedTask.completionResponses().get(name);
                        for (var i = 0; i < listModel.getSize(); i++) {
                            final var currentOption = (Option) listModel.getElementAt(i);
                            if (Objects.equals(currentOption.getValue(), expectedOption.toString())) {
                                listModel.setSelectedItem(currentOption);
                                break;
                            }
                        }
                    }
                }
                selectIterator.next();
            }
            final var inputIterator = doc.getIterator(HTML.Tag.INPUT);
            while (inputIterator.isValid()) {
                final var attribs = inputIterator.getAttributes();
                final var name = attribs.getAttribute(HTML.Attribute.NAME);
                final var model = attribs.getAttribute(StyleConstants.ModelAttribute);
                if (name instanceof String key && model instanceof ButtonModel buttonModel
                        && modifiedTask.completionResponses().get(key) instanceof Boolean selected) {
                    buttonModel.setSelected(selected);
                }
                inputIterator.next();
            }
        }
    }

    /**
     * Get the selections from the comboboxes of a document
     * @param doc The document to parse
     * @return The selected options
     */
    public static Map<String, Option> getSelections(HTMLDocument doc) {
        final var selectionMap = new TreeMap<String, Option>();
        final var selectIterator = doc.getIterator(HTML.Tag.SELECT);
        while (selectIterator.isValid()) {
            final var attribs = selectIterator.getAttributes();
            if (attribs.getAttribute(HTML.Attribute.NAME) != null) {
                final var name = (String) attribs.getAttribute(HTML.Attribute.NAME);
                if (attribs.getAttribute(StyleConstants.ModelAttribute)instanceof DefaultComboBoxModel<?> listModel) {
                    final var option = (Option) listModel.getSelectedItem();
                    if (!Utils.isStripEmpty(option.getValue())) {
                        selectionMap.put(name, option);
                    }
                }
            }
            selectIterator.next();
        }
        return Collections.unmodifiableMap(selectionMap);
    }

    /**
     * A listener for select combo boxes from a {@link HTMLDocument}
     * @param doc The document to use to update a task from
     * @param task The originating task
     */
    private record SelectComboBoxListener(HTMLDocument doc, Task task) implements ListDataListener {

    @Override
    public void intervalAdded(ListDataEvent e) {
        updateModifiedTask();
    }

    @Override
    public void intervalRemoved(ListDataEvent e) {
        updateModifiedTask();
    }

    @Override
    public void contentsChanged(ListDataEvent e) {
        updateModifiedTask();
    }

    private void updateModifiedTask() {
        // Completion-dialog submission snapshots the current document; changing a select is not a workflow mutation.
    }
}

/**
 * An action purely for making it easier to call {@link JosmAction#updateEnabledState()}.
 */
abstract static class InnerAction extends JosmAction {
    /**
     * Serial UID for this action
     */
    @Serial
    private static final long serialVersionUID = -6428448152026023173L;

    /**
     * Constructs a new {@code JosmAction} and installs layer changed and selection changed adapters.
     * <br>
     * Use this super constructor to setup your action.
     *
     * @param name              the action's text as displayed on the menu (if it is added to a menu)
     * @param iconName          the filename of the icon to use
     * @param tooltip           a longer description of the action that will be displayed in the tooltip. Please note
     *                          that html is not supported for menu actions on some platforms.
     * @param shortcut          a ready-created shortcut object or null if you don't want a shortcut. But you always
     *                          do want a shortcut, remember you can always register it with group=none, so you
     *                          won't be assigned a shortcut unless the user configures one. If you pass null here,
     *                          the user CANNOT configure a shortcut for your action.
     * @param registerInToolbar register this action for the toolbar preferences?
     */
    protected InnerAction(String name, String iconName, String tooltip, Shortcut shortcut, boolean registerInToolbar) {
        super(name, iconName, tooltip, shortcut, registerInToolbar, true);
        putValue("help", ht("/Dialog/MapRouletteCurrentTask"));
    }

    @Override
    public void updateEnabledState() {
        super.updateEnabledState();
    }
}

/**
 * Select the task primitives
 */
private static class SelectOsmPrimitives extends InnerAction {
    /**
     * The serial UID for this action
     */
    @Serial
    private static final long serialVersionUID = -5705885041487335379L;
    private final Supplier<Task> taskSuppler;

    SelectOsmPrimitives(Supplier<Task> taskSupplier) {
        super(tr("Select Primitives"), "dialogs/select", tr("Select the OSM primitives for this task"),
                MapRouletteShortcuts.selectPrimitives(),
                false);
        Objects.requireNonNull(taskSupplier);
        this.taskSuppler = taskSupplier;
        putValue("help", ht("/Dialog/MapRouletteCurrentTask#SelectOsmPrimitives"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        final var task = this.taskSuppler.get();
        final var layer = WorkflowController.getInstance().snapshot().editLayer();
        if (task != null && layer != null) {
            final var primitives = primitiveIds(task);
            if (!primitives.isEmpty()) {
                layer.getDataSet().setSelected(primitives);
                AutoScaleAction.autoScale(AutoScaleAction.AutoScaleMode.SELECTION);
            }
        }
    }

    @Override
    public void updateEnabledState() {
        if (this.taskSuppler != null) { // This check is only needed for the constructor. Watch JEP draft 8300786.
            final var task = this.taskSuppler.get();
            this.setEnabled(WorkflowController.getInstance().snapshot().editLayer() != null && task != null
                    && !primitiveIds(task).isEmpty());
        }
    }

    private static java.util.Collection<org.openstreetmap.josm.data.osm.PrimitiveId> primitiveIds(Task task) {
        final var challenge = WorkflowController.getInstance().snapshot().activeChallenge();
        final var property = challenge != null && challenge.id() == task.parentId() && challenge.extra() != null
                ? challenge.extra().osmIdProperty()
                : null;
        return TaskPrimitives.getPrimitiveIds(task, property);
    }
}}
