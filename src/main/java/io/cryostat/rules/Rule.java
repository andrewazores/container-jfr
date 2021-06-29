/*
 * Copyright The Cryostat Authors
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.cryostat.rules;

import java.io.IOException;
import java.io.StringReader;

import io.cryostat.core.log.Logger;

import jdk.nashorn.api.tree.BreakTree;
import jdk.nashorn.api.tree.CaseTree;
import jdk.nashorn.api.tree.CatchTree;
import jdk.nashorn.api.tree.ClassDeclarationTree;
import jdk.nashorn.api.tree.ClassExpressionTree;
import jdk.nashorn.api.tree.CompilationUnitTree;
import jdk.nashorn.api.tree.ContinueTree;
import jdk.nashorn.api.tree.DebuggerTree;
import jdk.nashorn.api.tree.DoWhileLoopTree;
import jdk.nashorn.api.tree.ErroneousTree;
import jdk.nashorn.api.tree.ExportEntryTree;
import jdk.nashorn.api.tree.ForInLoopTree;
import jdk.nashorn.api.tree.ForLoopTree;
import jdk.nashorn.api.tree.ForOfLoopTree;
import jdk.nashorn.api.tree.FunctionCallTree;
import jdk.nashorn.api.tree.FunctionDeclarationTree;
import jdk.nashorn.api.tree.FunctionExpressionTree;
import jdk.nashorn.api.tree.ImportEntryTree;
import jdk.nashorn.api.tree.InstanceOfTree;
import jdk.nashorn.api.tree.LabeledStatementTree;
import jdk.nashorn.api.tree.ModuleTree;
import jdk.nashorn.api.tree.NewTree;
import jdk.nashorn.api.tree.Parser;
import jdk.nashorn.api.tree.RegExpLiteralTree;
import jdk.nashorn.api.tree.ReturnTree;
import jdk.nashorn.api.tree.SimpleTreeVisitorES5_1;
import jdk.nashorn.api.tree.SpreadTree;
import jdk.nashorn.api.tree.ThrowTree;
import jdk.nashorn.api.tree.Tree;
import jdk.nashorn.api.tree.TreeVisitor;
import jdk.nashorn.api.tree.TryTree;
import jdk.nashorn.api.tree.UnaryTree;
import jdk.nashorn.api.tree.WhileLoopTree;
import jdk.nashorn.api.tree.WithTree;
import jdk.nashorn.api.tree.YieldTree;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class Rule {

    private final String name;
    private final String description;
    private final String matchExpression;
    private final String eventSpecifier;
    private final int archivalPeriodSeconds;
    private final int preservedArchives;
    private final int maxAgeSeconds;
    private final int maxSizeBytes;

    Rule(Builder builder) throws IOException {
        this.name = sanitizeRuleName(requireNonBlank(builder.name, Attribute.NAME));
        this.description = builder.description == null ? "" : builder.description;
        this.matchExpression =
                validateMatchExpression(
                        this.name,
                        requireNonBlank(builder.matchExpression, Attribute.MATCH_EXPRESSION));
        this.eventSpecifier = requireNonBlank(builder.eventSpecifier, Attribute.EVENT_SPECIFIER);
        this.archivalPeriodSeconds =
                requireNonNegative(
                        builder.archivalPeriodSeconds, Attribute.ARCHIVAL_PERIOD_SECONDS);
        this.preservedArchives =
                requireNonNegative(builder.preservedArchives, Attribute.PRESERVED_ARCHIVES);
        this.maxAgeSeconds =
                builder.maxAgeSeconds > 0 ? builder.maxAgeSeconds : this.archivalPeriodSeconds;
        this.maxSizeBytes = builder.maxSizeBytes;
    }

    public String getName() {
        return this.name;
    }

    public String getRecordingName() {
        // FIXME do something other than simply prepending "auto_"
        return String.format("auto_%s", this.getName());
    }

    public String getDescription() {
        return this.description;
    }

    public String getMatchExpression() {
        return this.matchExpression;
    }

    public String getEventSpecifier() {
        return this.eventSpecifier;
    }

    public int getArchivalPeriodSeconds() {
        return this.archivalPeriodSeconds;
    }

    public int getPreservedArchives() {
        return this.preservedArchives;
    }

    public int getMaxAgeSeconds() {
        return this.maxAgeSeconds;
    }

    public int getMaxSizeBytes() {
        return this.maxSizeBytes;
    }

    static String sanitizeRuleName(String name) {
        // FIXME this is not robust
        return name.replaceAll("\\s", "_");
    }

    static String validateMatchExpression(String name, String matchExpression)
            throws IOException, RuleMatchExpressionParseException {
        Parser parser = Parser.create();
        TreeVisitor<Void, Void> visitor =
                new SimpleTreeVisitorES5_1<Void, Void>() {
                    Void fail(Tree node, Void data) {
                        Logger.INSTANCE.error("Failed parsing on a {}", node.getKind());
                        throw new RuleMatchExpressionParseException(node, matchExpression);
                    }

                    @Override
                    public Void visitBreak(BreakTree node, Void data) {
                        return fail(node, data);
                    }

                    @Override
                    public Void visitCase(CaseTree node, Void data) {
                        return fail(node, data);
                    }

                    @Override
                    public Void visitCatch(CatchTree node, Void data) {
                        return fail(node, data);
                    }

                    @Override
                    public Void visitClassDeclaration(ClassDeclarationTree node, Void data) {
                        return fail(node, data);
                    }

                    @Override
                    public Void visitClassExpression(ClassExpressionTree node, Void data) {
                        return fail(node, data);
                    }

                    @Override
                    public Void visitDoWhileLoop(DoWhileLoopTree node, Void data) {
                        return fail(node, data);
                    }

                    @Override
                    public Void visitErroneous(ErroneousTree node, Void data) {
                        return fail(node, data);
                    }

                    @Override
                    public Void visitExportEntry(ExportEntryTree node, Void data) {
                        return fail(node, data);
                    }

                    @Override
                    public Void visitForInLoop(ForInLoopTree node, Void data) {
                        return fail(node, data);
                    }

                    @Override
                    public Void visitForLoop(ForLoopTree node, Void data) {
                        return fail(node, data);
                    }

                    @Override
                    public Void visitForOfLoop(ForOfLoopTree node, Void data) {
                        return fail(node, data);
                    }

                    @Override
                    public Void visitFunctionCall(FunctionCallTree node, Void data) {
                        return fail(node, data);
                    }

                    @Override
                    public Void visitFunctionDeclaration(FunctionDeclarationTree node, Void data) {
                        return fail(node, data);
                    }

                    @Override
                    public Void visitFunctionExpression(FunctionExpressionTree node, Void data) {
                        return fail(node, data);
                    }

                    @Override
                    public Void visitImportEntry(ImportEntryTree node, Void data) {
                        return fail(node, data);
                    }

                    @Override
                    public Void visitInstanceOf(InstanceOfTree node, Void data) {
                        return fail(node, data);
                    }

                    @Override
                    public Void visitLabeledStatement(LabeledStatementTree node, Void data) {
                        return fail(node, data);
                    }

                    @Override
                    public Void visitModule(ModuleTree node, Void data) {
                        return fail(node, data);
                    }

                    @Override
                    public Void visitNew(NewTree node, Void data) {
                        return fail(node, data);
                    }

                    @Override
                    public Void visitRegExpLiteral(RegExpLiteralTree node, Void data) {
                        return fail(node, data);
                    }

                    @Override
                    public Void visitReturn(ReturnTree node, Void data) {
                        return fail(node, data);
                    }

                    @Override
                    public Void visitSpread(SpreadTree node, Void data) {
                        return fail(node, data);
                    }

                    @Override
                    public Void visitUnary(UnaryTree node, Void data) {
                        return fail(node, data);
                    }

                    @Override
                    public Void visitUnknown(Tree node, Void data) {
                        return fail(node, data);
                    }

                    @Override
                    public Void visitWhileLoop(WhileLoopTree node, Void data) {
                        return fail(node, data);
                    }

                    @Override
                    public Void visitWith(WithTree node, Void data) {
                        return fail(node, data);
                    }

                    @Override
                    public Void visitYield(YieldTree node, Void data) {
                        return fail(node, data);
                    }

                    @Override
                    public Void visitContinue(ContinueTree node, Void data) {
                        return fail(node, data);
                    }

                    @Override
                    public Void visitDebugger(DebuggerTree node, Void data) {
                        return fail(node, data);
                    }

                    @Override
                    public Void visitThrow(ThrowTree node, Void data) {
                        return fail(node, data);
                    }

                    @Override
                    public Void visitTry(TryTree node, Void data) {
                        return fail(node, data);
                    }
                };
        try (StringReader reader = new StringReader(matchExpression)) {
            CompilationUnitTree cut = parser.parse("script.js", reader, null);
            if (cut == null) {
                throw new RuleMatchExpressionParseException();
            }
            cut.accept(visitor, null);
        }
        return matchExpression;
    }

    private static String requireNonBlank(String s, Attribute attr) {
        if (StringUtils.isBlank(s)) {
            throw new IllegalArgumentException(
                    String.format("\"%s\" cannot be blank, was \"%s\"", attr, s));
        }
        return s;
    }

    private static int requireNonNegative(int i, Attribute attr) {
        if (i < 0) {
            throw new IllegalArgumentException(
                    String.format("\"%s\" cannot be negative, was \"%d\"", attr, i));
        }
        return i;
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    public static class Builder {
        private String name;
        private String description;
        private String matchExpression;
        private String eventSpecifier;
        private int archivalPeriodSeconds = 30;
        private int preservedArchives = 1;
        private int maxAgeSeconds = -1;
        private int maxSizeBytes = -1;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder matchExpression(String matchExpression) {
            this.matchExpression = matchExpression;
            return this;
        }

        public Builder eventSpecifier(String eventSpecifier) {
            this.eventSpecifier = eventSpecifier;
            return this;
        }

        public Builder archivalPeriodSeconds(int archivalPeriodSeconds) {
            this.archivalPeriodSeconds = archivalPeriodSeconds;
            return this;
        }

        public Builder preservedArchives(int preservedArchives) {
            this.preservedArchives = preservedArchives;
            return this;
        }

        public Builder maxAgeSeconds(int maxAgeSeconds) {
            this.maxAgeSeconds = maxAgeSeconds;
            return this;
        }

        public Builder maxSizeBytes(int maxSizeBytes) {
            this.maxSizeBytes = maxSizeBytes;
            return this;
        }

        public Rule build() throws IOException {
            return new Rule(this);
        }
    }

    public enum Attribute {
        NAME("name"),
        DESCRIPTION("description"),
        MATCH_EXPRESSION("matchExpression"),
        EVENT_SPECIFIER("eventSpecifier"),
        ARCHIVAL_PERIOD_SECONDS("archivalPeriodSeconds"),
        PRESERVED_ARCHIVES("preservedArchives"),
        MAX_AGE_SECONDS("maxAgeSeconds"),
        MAX_SIZE_BYTES("maxSizeBytes"),
        ;

        private final String serialKey;

        Attribute(String serialKey) {
            this.serialKey = serialKey;
        }

        public String getSerialKey() {
            return serialKey;
        }

        @Override
        public String toString() {
            return getSerialKey();
        }
    }

    @SuppressWarnings("serial")
    public static class RuleMatchExpressionParseException extends RuntimeException {
        RuleMatchExpressionParseException() {
            super("matchExpression parsing failed");
        }

        RuleMatchExpressionParseException(Tree node, String matchExpression) {
            super(
                    String.format(
                            "matchExpression rejected, illegal %s at [%d, %d]: %s",
                            node.getKind(),
                            node.getStartPosition(),
                            node.getEndPosition(),
                            matchExpression.substring(
                                    (int) node.getStartPosition(), (int) node.getEndPosition())));
        }
    }
}
