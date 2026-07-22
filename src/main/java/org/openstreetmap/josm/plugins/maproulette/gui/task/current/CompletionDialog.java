// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.gui.task.current;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;

import javax.swing.ButtonGroup;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.Option;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.tagging.ac.AutoCompComboBox;
import org.openstreetmap.josm.gui.widgets.HtmlPanel;
import org.openstreetmap.josm.gui.widgets.JosmTextArea;
import org.openstreetmap.josm.plugins.maproulette.api.model.Challenge;
import org.openstreetmap.josm.plugins.maproulette.api.model.Task;
import org.openstreetmap.josm.plugins.maproulette.gui.MRGuiHelper;
import org.openstreetmap.josm.plugins.maproulette.gui.preferences.MapRouletteTaskPreference;
import org.openstreetmap.josm.plugins.maproulette.gui.preferences.MapRouletteTaskPreference.NextMode;
import org.openstreetmap.josm.plugins.maproulette.io.upload.FixedUploadCoordinator;
import org.openstreetmap.josm.plugins.maproulette.util.ExceptionDialogUtil;
import org.openstreetmap.josm.plugins.maproulette.workflow.CompletionDraft;
import org.openstreetmap.josm.plugins.maproulette.workflow.CompletionDraftValidator;
import org.openstreetmap.josm.plugins.maproulette.workflow.CompletionResult;
import org.openstreetmap.josm.plugins.maproulette.workflow.CompletionSubmissionController;
import org.openstreetmap.josm.plugins.maproulette.workflow.WorkflowController;
import org.openstreetmap.josm.tools.GBC;

/** Reusable web-style completion confirmation dialog. */
final class CompletionDialog extends JDialog {
    private final WorkflowController workflow;
    private final CompletionSubmissionController submissions;
    private final Task task;
    private final Challenge challenge;
    private final CompletionResult result;
    private final Consumer<Boolean> closed;
    private final JosmTextArea comment = new JosmTextArea(5, 42);
    private final JLabel commentCount = new JLabel();
    private final HtmlPanel preview = new HtmlPanel();
    private final AutoCompComboBox<String> tags = new AutoCompComboBox<>();
    private final JComboBox<ReviewChoice> review = new JComboBox<>(ReviewChoice.values());
    private final JRadioButton random = new JRadioButton(tr("Random"));
    private final JRadioButton nearby = new JRadioButton(tr("Nearby"));
    private final HtmlPanel instructions = new HtmlPanel();
    private final JPanel instructionsContainer = new JPanel(new BorderLayout());
    private final JButton cancel = new JButton(tr("Cancel"));
    private final JButton submit = new JButton(tr("Submit"));
    private boolean busy;

    CompletionDialog(Window owner, WorkflowController workflow, CompletionSubmissionController submissions, Task task,
            Challenge challenge, CompletionResult result, HTMLDocument currentInstructions, Consumer<Boolean> closed) {
        super(owner, tr("Complete MapRoulette Task"), Dialog.ModalityType.APPLICATION_MODAL);
        this.workflow = workflow;
        this.submissions = submissions;
        this.task = task;
        this.challenge = challenge;
        this.result = result;
        this.closed = closed;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        final var prior = workflow.snapshot().completionDraft();
        final var initial = prior != null && prior.task().id() == task.id() ? prior : null;
        comment.setText(initial == null ? "" : initial.comment());
        ((AbstractDocument) comment.getDocument()).setDocumentFilter(new LengthFilter(CompletionDraft.MAX_COMMENT_LENGTH));
        comment.getDocument().addDocumentListener(new SimpleDocumentListener() {
            @Override
            public void changed() {
                updateComment();
            }
        });
        updateComment();

        if (challenge != null && challenge.extra() != null && challenge.extra().preferredTags() != null) {
            tags.getModel().addAllElements(new ArrayList<>(CompletionDraftValidator
                    .splitTags(challenge.extra().preferredTags())));
        }
        tags.setEditable(true);
        tags.setText(initial == null ? "" : initial.tags());
        review.setSelectedItem(ReviewChoice.forValue(initial == null ? null : initial.requestReview()));

        final var mode = initial == null ? workflow.snapshot().nextMode() : initial.nextMode();
        random.setSelected(mode == NextMode.RANDOM);
        nearby.setSelected(mode == NextMode.NEARBY);
        final var modes = new ButtonGroup();
        modes.add(random);
        modes.add(nearby);

        instructions.setText(MRGuiHelper.getInstructionText(task));
        instructions.enableClickableHyperlinks();
        applyResponses((HTMLDocument) instructions.getEditorPane().getDocument(),
                initial == null ? responses(currentInstructions) : initial.completionResponses());
        instructionsContainer.add(instructions.getEditorPane(), BorderLayout.CENTER);
        instructionsContainer.setVisible(false);

        final var tabs = new JTabbedPane();
        tabs.addTab(tr("Write"), new JScrollPane(comment));
        tabs.addTab(tr("Markdown Preview"), new JScrollPane(preview.getEditorPane()));
        tabs.addChangeListener(event -> updateComment());

        final var fields = new JPanel(new GridBagLayout());
        fields.add(new JLabel(tr("Complete task as: {0}", result.label())), GBC.eol().anchor(GBC.LINE_START));
        fields.add(tabs, GBC.eol().fill(GridBagConstraints.BOTH));
        fields.add(commentCount, GBC.eol().anchor(GBC.LINE_END));
        fields.add(new JLabel(tr("MR tags (comma-separated):")), GBC.eol().anchor(GBC.LINE_START));
        fields.add(tags, GBC.eol().fill(GridBagConstraints.HORIZONTAL));
        fields.add(new JLabel(tr("Request review:")), GBC.std().anchor(GBC.LINE_START));
        fields.add(review, GBC.eol().fill(GridBagConstraints.HORIZONTAL));
        final var modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        modePanel.add(random);
        modePanel.add(nearby);
        fields.add(new JLabel(tr("Next task:")), GBC.std().anchor(GBC.LINE_START));
        fields.add(modePanel, GBC.eol().anchor(GBC.LINE_START));
        final var showInstructions = new JToggleButton(tr("Show instructions"));
        showInstructions.addActionListener(event -> {
            instructionsContainer.setVisible(showInstructions.isSelected());
            showInstructions.setText(showInstructions.isSelected() ? tr("Hide instructions") : tr("Show instructions"));
            packWithinLimit();
        });
        fields.add(showInstructions, GBC.eol().anchor(GBC.LINE_START));
        fields.add(instructionsContainer, GBC.eol().fill(GridBagConstraints.BOTH));

        final var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(submit);
        cancel.addActionListener(event -> close(false));
        submit.addActionListener(event -> submit());

        final var content = new JPanel(new BorderLayout(8, 8));
        content.add(fields, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(submit);
        packWithinLimit();
        setLocationRelativeTo(owner);
    }

    private void submit() {
        final var draft = draft();
        final var required = responseNames((HTMLDocument) instructions.getEditorPane().getDocument());
        final var errors = CompletionDraftValidator.validate(draft, challenge, required);
        if (!errors.isEmpty()) {
            JOptionPane.showMessageDialog(this, String.join("\n", errors), tr("Cannot submit task"),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        MapRouletteTaskPreference.setNextMode(draft.task().parentId(), draft.nextMode());
        if (result == CompletionResult.FIXED) {
            submissions.preserveFixedDraft(draft);
            final var uploads = FixedUploadCoordinator.getInstance();
            if (!uploads.hasPendingEdits()) {
                final var choice = JOptionPane.showConfirmDialog(this,
                        tr("No edits in the task layer require upload. Mark this task Fixed without a new changeset?"),
                        tr("No edits to upload"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (choice == JOptionPane.YES_OPTION) {
                    submitFixedWithoutUpload();
                    return;
                }
                close(false);
                return;
            }
            try {
                uploads.start(draft);
            } catch (RuntimeException exception) {
                ExceptionDialogUtil.explainException(exception);
                return;
            }
            close(false);
            return;
        }
        setBusy(true);
        MainApplication.worker.execute(() -> {
            try {
                submissions.submit(draft);
                SwingUtilities.invokeLater(() -> close(true));
            } catch (Exception exception) {
                SwingUtilities.invokeLater(() -> {
                    setBusy(false);
                    ExceptionDialogUtil.explainException(exception);
                });
            }
        });
    }

    private void submitFixedWithoutUpload() {
        if (workflow.state() == WorkflowController.State.WAITING_FOR_UPLOAD) {
            FixedUploadCoordinator.getInstance().cancel();
        }
        setBusy(true);
        MainApplication.worker.execute(() -> {
            try {
                submissions.submitFixedWithoutUpload();
                SwingUtilities.invokeLater(() -> close(true));
            } catch (Exception exception) {
                SwingUtilities.invokeLater(() -> {
                    setBusy(false);
                    ExceptionDialogUtil.explainException(exception);
                });
            }
        });
    }

    private CompletionDraft draft() {
        return new CompletionDraft(task, result, comment.getText(), tags.getText(),
                ((ReviewChoice) review.getSelectedItem()).value,
                responses((HTMLDocument) instructions.getEditorPane().getDocument()),
                nearby.isSelected() ? NextMode.NEARBY : NextMode.RANDOM);
    }

    private void updateComment() {
        commentCount.setText(tr("{0} / {1}", comment.getDocument().getLength(), CompletionDraft.MAX_COMMENT_LENGTH));
        preview.setText(MRGuiHelper.renderMarkdown(comment.getText()));
    }

    private void setBusy(boolean value) {
        busy = value;
        setEnabledRecursively(getContentPane(), !value);
        cancel.setEnabled(!value);
        submit.setEnabled(!value);
    }

    private static void setEnabledRecursively(Container parent, boolean enabled) {
        for (var component : parent.getComponents()) {
            component.setEnabled(enabled);
            if (component instanceof Container container) {
                setEnabledRecursively(container, enabled);
            }
        }
    }

    private void close(boolean completed) {
        if (!busy || completed) {
            dispose();
            closed.accept(completed);
        }
    }

    private void packWithinLimit() {
        pack();
        setSize(new Dimension(Math.min(700, getWidth()), Math.min(760, getHeight())));
    }

    static Map<String, Object> responses(HTMLDocument document) {
        final var result = new TreeMap<String, Object>();
        final var iterator = document.getIterator(HTML.Tag.SELECT);
        while (iterator.isValid()) {
            final var attributes = iterator.getAttributes();
            final var name = attributes.getAttribute(HTML.Attribute.NAME);
            final var model = attributes.getAttribute(StyleConstants.ModelAttribute);
            if (name instanceof String key && model instanceof javax.swing.ComboBoxModel<?> comboModel
                    && comboModel.getSelectedItem() instanceof Option option && !option.getValue().isBlank()) {
                result.put(key, option.getValue());
            }
            iterator.next();
        }
        final var inputIterator = document.getIterator(HTML.Tag.INPUT);
        while (inputIterator.isValid()) {
            final var attributes = inputIterator.getAttributes();
            final var name = attributes.getAttribute(HTML.Attribute.NAME);
            final var model = attributes.getAttribute(StyleConstants.ModelAttribute);
            if (name instanceof String key && model instanceof ButtonModel buttonModel) {
                result.put(key, buttonModel.isSelected());
            }
            inputIterator.next();
        }
        return result;
    }

    static Set<String> responseNames(HTMLDocument document) {
        final var result = new TreeSet<String>();
        final var iterator = document.getIterator(HTML.Tag.SELECT);
        while (iterator.isValid()) {
            final var name = iterator.getAttributes().getAttribute(HTML.Attribute.NAME);
            if (name instanceof String key && !key.isBlank()) {
                result.add(key);
            }
            iterator.next();
        }
        final var inputIterator = document.getIterator(HTML.Tag.INPUT);
        while (inputIterator.isValid()) {
            final var name = inputIterator.getAttributes().getAttribute(HTML.Attribute.NAME);
            if (name instanceof String key && !key.isBlank()) {
                result.add(key);
            }
            inputIterator.next();
        }
        return result;
    }

    private static void applyResponses(HTMLDocument document, Map<String, Object> responses) {
        final var iterator = document.getIterator(HTML.Tag.SELECT);
        while (iterator.isValid()) {
            final var attributes = iterator.getAttributes();
            final var name = attributes.getAttribute(HTML.Attribute.NAME);
            final var model = attributes.getAttribute(StyleConstants.ModelAttribute);
            if (name instanceof String key && model instanceof javax.swing.ComboBoxModel<?> comboModel
                    && responses.containsKey(key)) {
                for (var index = 0; index < comboModel.getSize(); index++) {
                    if (comboModel.getElementAt(index) instanceof Option option
                            && option.getValue().equals(responses.get(key).toString())) {
                        comboModel.setSelectedItem(option);
                        break;
                    }
                }
            }
            iterator.next();
        }
        final var inputIterator = document.getIterator(HTML.Tag.INPUT);
        while (inputIterator.isValid()) {
            final var attributes = inputIterator.getAttributes();
            final var name = attributes.getAttribute(HTML.Attribute.NAME);
            final var model = attributes.getAttribute(StyleConstants.ModelAttribute);
            if (name instanceof String key && model instanceof ButtonModel buttonModel
                    && responses.get(key) instanceof Boolean selected) {
                buttonModel.setSelected(selected);
            }
            inputIterator.next();
        }
    }

    private enum ReviewChoice {
        DEFAULT(tr("Use account setting"), null), YES(tr("Request review"), Boolean.TRUE),
        NO(tr("Do not request review"), Boolean.FALSE);

        private final String label;
        private final Boolean value;

        ReviewChoice(String label, Boolean value) {
            this.label = label;
            this.value = value;
        }

        static ReviewChoice forValue(Boolean value) {
            return value == null ? DEFAULT : value ? YES : NO;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    static final class LengthFilter extends DocumentFilter {
        private final int maximum;

        LengthFilter(int maximum) {
            this.maximum = maximum;
        }

        @Override
        public void insertString(FilterBypass bypass, int offset, String string, AttributeSet attributes)
                throws BadLocationException {
            replace(bypass, offset, 0, string, attributes);
        }

        @Override
        public void replace(FilterBypass bypass, int offset, int length, String text, AttributeSet attributes)
                throws BadLocationException {
            final var replacement = text == null ? "" : text;
            final var available = maximum - (bypass.getDocument().getLength() - length);
            if (available > 0) {
                super.replace(bypass, offset, length, replacement.substring(0, Math.min(available, replacement.length())),
                        attributes);
            }
        }
    }

    private interface SimpleDocumentListener extends javax.swing.event.DocumentListener {
        void changed();

        @Override
        default void insertUpdate(javax.swing.event.DocumentEvent event) {
            changed();
        }

        @Override
        default void removeUpdate(javax.swing.event.DocumentEvent event) {
            changed();
        }

        @Override
        default void changedUpdate(javax.swing.event.DocumentEvent event) {
            changed();
        }
    }
}
