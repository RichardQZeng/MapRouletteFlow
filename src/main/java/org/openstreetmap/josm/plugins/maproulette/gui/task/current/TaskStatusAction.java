// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.gui.task.current;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.Serial;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.function.Predicate;

import javax.swing.text.html.HTMLDocument;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.plugins.maproulette.api.model.Task;
import org.openstreetmap.josm.plugins.maproulette.config.MapRouletteConfig;
import org.openstreetmap.josm.plugins.maproulette.util.AuthenticationManager;
import org.openstreetmap.josm.plugins.maproulette.io.upload.FixedUploadCoordinator;
import org.openstreetmap.josm.plugins.maproulette.workflow.ApiTaskCompletionGateway;
import org.openstreetmap.josm.plugins.maproulette.workflow.CompletionResult;
import org.openstreetmap.josm.plugins.maproulette.workflow.CompletionSubmissionController;
import org.openstreetmap.josm.plugins.maproulette.workflow.WorkflowController;
import org.openstreetmap.josm.plugins.maproulette.workflow.WorkflowController.State;
import org.openstreetmap.josm.tools.Shortcut;

/** Opens the focused completion dialog for one web-style task result. */
class TaskStatusAction extends CurrentTaskPanel.InnerAction {
    @Serial
    private static final long serialVersionUID = -7143294590412539737L;
    private final CompletionResult result;
    private final Supplier<Task> currentTaskProvider;
    private final Supplier<HTMLDocument> currentDocumentProvider;
    private final Predicate<Task> cooperativePreparation;
    private final WorkflowController workflow = WorkflowController.getInstance();
    private final CompletionSubmissionController submissions = new CompletionSubmissionController(workflow,
            new ApiTaskCompletionGateway());

    TaskStatusAction(CompletionResult result, Supplier<Task> currentTaskProvider,
            Supplier<HTMLDocument> currentDocumentProvider, Predicate<Task> cooperativePreparation) {
        super(result.label(), icon(result), result.label(),
                Shortcut.registerShortcut("maproulette:" + result.name().toLowerCase(Locale.ENGLISH),
                        tr("MapRoulette: {0}", result.label()), KeyEvent.CHAR_UNDEFINED, Shortcut.NONE),
                false);
        this.result = result;
        this.currentTaskProvider = currentTaskProvider;
        this.currentDocumentProvider = currentDocumentProvider;
        this.cooperativePreparation = cooperativePreparation;
    }

    private static String icon(CompletionResult result) {
        return switch (result) {
        case FIXED, ALREADY_FIXED -> "dialogs/validator";
        case CANT_COMPLETE, NOT_AN_ISSUE -> "cancel";
        case SKIP -> "svpRight";
        };
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        updateEnabledState();
        if (!isEnabled()) {
            return;
        }
        final var task = currentTaskProvider.get();
        final var snapshot = workflow.snapshot();
        if (snapshot.auxiliaryRetry() != null) {
            setEnabled(false);
            MainApplication.worker.execute(() -> {
                try {
                    submissions.retryAuxiliary();
                } catch (Exception exception) {
                    javax.swing.SwingUtilities.invokeLater(
                            () -> org.openstreetmap.josm.plugins.maproulette.util.ExceptionDialogUtil
                                    .explainException(exception));
                }
            });
            return;
        }
        final var dialog = new CompletionDialog(MainApplication.getMainFrame(), workflow, submissions, task,
                snapshot.activeChallenge(), result, currentDocumentProvider.get(), completed -> {
                    if (completed && MainApplication.getMap() != null) {
                        final var panel = MainApplication.getMap().getToggleDialog(CurrentTaskPanel.class);
                        if (panel != null) {
                            panel.refreshModel(null);
                        }
                    }
                    updateEnabledState();
                }, () -> cooperativePreparation.test(task));
        dialog.setVisible(true);
    }

    @Override
    public void updateEnabledState() {
        if (currentTaskProvider == null || workflow.snapshot().suspended() || !isAuthenticated()
                || currentTaskProvider.get() == null) {
            setEnabled(false);
            return;
        }
        final var state = workflow.state();
        final var draft = workflow.snapshot().completionDraft();
        final var matchingDraft = draft != null && draft.result() == result
                && (state == State.COMPLETION_DRAFT || state == State.RECOVERABLE_ERROR
                        || state == State.WAITING_FOR_UPLOAD && result == CompletionResult.FIXED
                                && FixedUploadCoordinator.getInstance().canRetry());
        setEnabled(state == State.ACTIVE_EDITING || matchingDraft);
    }

    private static boolean isAuthenticated() {
        if (MapRouletteConfig.getInstance() == null) {
            return false;
        }
        final var account = AuthenticationManager.getAuthenticatedUser(MapRouletteConfig.getBaseUrl());
        return account != null && WorkflowController.getInstance().isOwnedBy(MapRouletteConfig.getBaseUrl(), account);
    }
}
