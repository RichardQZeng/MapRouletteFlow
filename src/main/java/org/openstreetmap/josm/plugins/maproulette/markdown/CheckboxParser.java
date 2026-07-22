// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.markdown;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.CustomNode;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.parser.PostProcessor;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.HtmlWriter;

/** Parses MapRoulette {@code [checkbox "label" name="response"]} instruction fields. */
public final class CheckboxParser implements Parser.ParserExtension, HtmlRenderer.HtmlRendererExtension {
    private static final Pattern CHECKBOX_PATTERN = Pattern
            .compile("(\\[checkbox[/ ]?\"([^\"]+)\"\\s+name=\"([^\"]+)\"[^]]*])");

    private static final class CheckboxNode extends CustomNode {
        private final String label;
        private final String name;

        CheckboxNode(String label, String name) {
            this.label = label;
            this.name = name;
        }
    }

    private static final class CheckboxVisitor extends AbstractVisitor {
        @Override
        public void visit(Text text) {
            final var matcher = CHECKBOX_PATTERN.matcher(text.getLiteral());
            if (matcher.find()) {
                final var before = new StringBuilder();
                final var after = new StringBuilder();
                matcher.appendReplacement(before, "");
                matcher.appendTail(after);
                text.setLiteral(before.toString());
                text.insertAfter(new Text(after.toString()));
                text.insertAfter(new CheckboxNode(matcher.group(2), matcher.group(3)));
            } else {
                super.visit(text);
            }
        }
    }

    private static final class CheckboxPostProcessor implements PostProcessor {
        @Override
        public Node process(Node node) {
            node.accept(new CheckboxVisitor());
            return node;
        }
    }

    private static final class CheckboxNodeRenderer implements NodeRenderer {
        private final HtmlWriter html;

        CheckboxNodeRenderer(HtmlNodeRendererContext context) {
            html = context.getWriter();
        }

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            return Collections.singleton(CheckboxNode.class);
        }

        @Override
        public void render(Node node) {
            if (node instanceof CheckboxNode checkbox) {
                html.tag("input", Map.of("type", "checkbox", "name", checkbox.name));
                html.text(" " + checkbox.label);
            }
        }
    }

    @Override
    public void extend(Parser.Builder parserBuilder) {
        parserBuilder.postProcessor(new CheckboxPostProcessor());
    }

    @Override
    public void extend(HtmlRenderer.Builder rendererBuilder) {
        rendererBuilder.nodeRendererFactory(CheckboxNodeRenderer::new);
    }
}
