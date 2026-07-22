// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.markdown;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.commonmark.text.Characters;
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

/**
 * A parser for [select "label" name="Unique form name" values="csv delimited list"]
 */
public class SelectParser implements Parser.ParserExtension, HtmlRenderer.HtmlRendererExtension {
    /**
     * Render a {@link SelectNode} to HTML
     */
    private static class SelectNodeRenderer implements NodeRenderer {
        private final HtmlWriter html;

        /**
         * Create a new render object for {@link SelectNode}
         * @param context The context to which we are rendering
         */
        SelectNodeRenderer(HtmlNodeRendererContext context) {
            this.html = context.getWriter();
        }

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            return Collections.singleton(SelectNode.class);
        }

        @Override
        public void render(Node node) {
            if (node instanceof SelectNode selectNode) {
                html.line();
                if (selectNode.name != null) {
                    html.tag("select", Map.of("name", selectNode.name));
                } else {
                    html.tag("select");
                }
                html.line();
                html.tag("option", new TreeMap<>(Map.of("value", "", "selected", "")));
                html.tag("/option");
                html.line();
                for (var value : selectNode.values) {
                    html.tag("option", Collections.singletonMap("value", value));
                    html.text(value);
                    html.tag("/option");
                    html.line();
                }
                html.tag("/select");
                html.text(" " + selectNode.label);
                html.line();
            }
        }
    }

    /**
     * A custom node for [select...] blocks
     */
    private static class SelectNode extends CustomNode {
        /** The values for the select block */
        private final String[] values;
        /** The label shown next to the select block. */
        private final String label;
        /** The name for the select block (for form submission) */
        private final String name;

        /**
         * A node for [select...] blocks
         * @param selectBlock The full select block
         */
        SelectNode(String selectBlock) {
            final var startLabel = Characters.find('"', selectBlock, 0) + 1;
            final var endLabel = Characters.find('"', selectBlock, startLabel);
            this.label = selectBlock.substring(startLabel, endLabel);
            final var valueMatcher = Pattern.compile("values=\"([^\"]*)").matcher(selectBlock);
            final var nameMatcher = Pattern.compile("name=\"([^\"]*)").matcher(selectBlock);
            if (nameMatcher.find()) {
                this.name = nameMatcher.group(1);
            } else {
                this.name = null;
            }
            if (valueMatcher.find()) {
                values = Pattern.compile(",\\s*").split(valueMatcher.group(1), -1);
            } else {
                throw new IllegalArgumentException("A select object must have values");
            }
        }
    }

    /**
     * A visitor that looks for [select...] blocks
     */
    private static class SelectVisitor extends AbstractVisitor {
        private static final Pattern SELECT_PATTERN = Pattern.compile("(\\[select[^]]*])");

        @Override
        public void visit(Text text) {
            final var matcher = SELECT_PATTERN.matcher(text.getLiteral());
            if (matcher.find()) {
                final var sb1 = new StringBuilder();
                final var sb2 = new StringBuilder();
                matcher.appendReplacement(sb1, "");
                matcher.appendTail(sb2);
                final var first = sb1.toString();
                final var second = sb2.toString();
                text.setLiteral(first);
                text.insertAfter(new Text(second));
                text.insertAfter(new SelectNode(matcher.group(1)));
            } else {
                super.visit(text);
            }
        }
    }

    /**
     * A post processor to convert [select...] blocks to something usable
     */
    private static class SelectPostProcessor implements PostProcessor {
        @Override
        public Node process(Node node) {
            node.accept(new SelectVisitor());
            return node;
        }
    }

    @Override
    public void extend(Parser.Builder parserBuilder) {
        parserBuilder.postProcessor(new SelectPostProcessor());
    }

    @Override
    public void extend(HtmlRenderer.Builder rendererBuilder) {
        rendererBuilder.nodeRendererFactory(SelectNodeRenderer::new);
    }
}
