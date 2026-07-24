// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.gui.task.current;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import javax.swing.DefaultComboBoxModel;
import javax.swing.ButtonModel;
import javax.swing.text.PlainDocument;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.Option;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.widgets.HtmlPanel;
import io.github.richardqzeng.josm.maprouletteflow.api.enums.TaskStatus;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Task;
import io.github.richardqzeng.josm.maprouletteflow.gui.MRGuiHelper;
import io.github.richardqzeng.josm.maprouletteflow.gui.preferences.MapRouletteTaskPreference.NextMode;
import io.github.richardqzeng.josm.maprouletteflow.workflow.CompletionDraft;
import io.github.richardqzeng.josm.maprouletteflow.workflow.CompletionResult;

class CompletionDialogTest {
    @Test
    void commentFilterEnforcesFiveThousandCharacters() throws Exception {
        final var document = new PlainDocument();
        document.setDocumentFilter(new CompletionDialog.LengthFilter(5_000));
        document.insertString(0, "x".repeat(5_001), null);
        assertEquals(5_000, document.getLength());
    }

    @Test
    void markdownPreviewUsesExistingRenderer() {
        assertTrue(MRGuiHelper.renderMarkdown("**rendered**").contains("<strong>rendered</strong>"));
    }

    @Test
    void namedInstructionSelectIsRequiredAndProducesCurrentWebResponseBodyValues() {
        final var task = new Task(1, "task", null, null, 10,
                "[select \"Choose one\" name=\"answer\" values=\"yes, no\"]", null, new DataSet(), null,
                TaskStatus.CREATED, null, null, null, null, 0, null, null, null, false, null, "");
        final var panel = new HtmlPanel();
        panel.setText(MRGuiHelper.getInstructionText(task));
        final var document = (HTMLDocument) panel.getEditorPane().getDocument();

        assertEquals(Set.of("answer"), CompletionDialog.responseNames(document));
        assertTrue(CompletionDialog.responses(document).isEmpty());

        final var iterator = document.getIterator(HTML.Tag.SELECT);
        final var model = (DefaultComboBoxModel<?>) iterator.getAttributes()
                .getAttribute(StyleConstants.ModelAttribute);
        model.setSelectedItem(model.getElementAt(1));
        assertEquals(Map.of("answer", "yes"), CompletionDialog.responses(document));
        assertTrue(model.getElementAt(1) instanceof Option);
    }

    @Test
    void namedInstructionCheckboxProducesBooleanWebResponse() {
        final var task = new Task(1, "task", null, null, 10,
                "[checkbox \"I checked this\" name=\"confirmed\"]", null, new DataSet(), null,
                TaskStatus.CREATED, null, null, null, null, 0, null, null, null, false, null, "");
        final var panel = new HtmlPanel();
        panel.setText(MRGuiHelper.getInstructionText(task));
        final var document = (HTMLDocument) panel.getEditorPane().getDocument();

        assertEquals(Set.of("confirmed"), CompletionDialog.responseNames(document));
        assertEquals(Map.of("confirmed", false), CompletionDialog.responses(document));

        final var iterator = document.getIterator(HTML.Tag.INPUT);
        final var model = (ButtonModel) iterator.getAttributes().getAttribute(StyleConstants.ModelAttribute);
        model.setSelected(true);
        assertEquals(Map.of("confirmed", true), CompletionDialog.responses(document));
    }

    @Test
    void generatedCommentPrefillsOnlyNewFixedDrafts() {
        final var task = new Task(1, "task", null, null, 10, null, null, new DataSet(), null, TaskStatus.CREATED,
                null, null, null, null, 0, null, null, null, false, null, "");
        final var prior = new CompletionDraft(task, CompletionResult.FIXED, "Mapper text", "", null, Map.of(),
                NextMode.RANDOM);

        assertEquals("Generated actions", CompletionDialog.initialComment(null, CompletionResult.FIXED,
                "Generated actions"));
        assertEquals("", CompletionDialog.initialComment(null, CompletionResult.ALREADY_FIXED, "Generated actions"));
        assertEquals("Mapper text", CompletionDialog.initialComment(prior, CompletionResult.FIXED,
                "Generated actions"));
    }
}
