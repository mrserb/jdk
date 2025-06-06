/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 7073631 7159445 7156633 8028235 8065753 8205418 8205913 8228451 8237041 8253584 8246774 8256411 8256149 8259050 8266436 8267221 8271928 8275097 8293897 8295401 8304671 8310326 8312093 8312204 8315452 8337976 8324859 8344706
 * @summary tests error and diagnostics positions
 * @author  Jan Lahoda
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.tree
 */

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ErroneousTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.main.Main;
import com.sun.tools.javac.main.Main.Result;
import com.sun.tools.javac.tree.JCTree;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import com.sun.source.tree.CaseTree;
import com.sun.source.tree.DefaultCaseLabelTree;
import com.sun.source.tree.ModuleTree;
import com.sun.source.util.TreePathScanner;
import com.sun.tools.javac.api.JavacTaskPool;
import com.sun.tools.javac.api.JavacTaskPool.Worker;
import com.sun.tools.javac.tree.JCTree.JCErroneous;
import com.sun.tools.javac.tree.Pretty;

public class JavacParserTest extends TestCase {
    static final JavaCompiler tool = ToolProvider.getSystemJavaCompiler();
    static final JavaFileManager fm = tool.getStandardFileManager(null, null, null);
    public static final String SOURCE_VERSION =
        Integer.toString(Runtime.version().feature());

    private JavacParserTest(){}

    public static void main(String... args) throws Exception {
        try (fm) {
            new JavacParserTest().run(args);
        }
    }

    class MyFileObject extends SimpleJavaFileObject {

        private String text;

        public MyFileObject(String text) {
            this("Test", text);
        }

        public MyFileObject(String fileName, String text) {
            super(URI.create("myfo:/" + fileName + ".java"), JavaFileObject.Kind.SOURCE);
            this.text = text;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return text;
        }
    }
    /*
     * converts Windows to Unix style LFs for comparing strings
     */
    String normalize(String in) {
        return in.replace(System.getProperty("line.separator"), "\n");
    }

    CompilationUnitTree getCompilationUnitTree(String code) throws IOException {

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, null, null,
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();
        return cut;
    }

    List<String> getErroneousTreeValues(ErroneousTree node) {

        List<String> values = new ArrayList<>();
        if (node.getErrorTrees() != null) {
            for (Tree t : node.getErrorTrees()) {
                values.add(t.toString());
            }
        } else {
            throw new RuntimeException("ERROR: No Erroneous tree "
                    + "has been created.");
        }
        return values;
    }

    @Test
    void testPositionForSuperConstructorCalls() throws IOException {
        assert tool != null;

        String code = "package test; public class Test {public Test() {super();}}";

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, null, null,
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();
        SourcePositions pos = Trees.instance(ct).getSourcePositions();

        MethodTree method =
                (MethodTree) ((ClassTree) cut.getTypeDecls().get(0)).getMembers().get(0);
        ExpressionStatementTree es =
                (ExpressionStatementTree) method.getBody().getStatements().get(0);

        final int esStartPos = code.indexOf(es.toString());
        final int esEndPos = esStartPos + es.toString().length();
        assertEquals("testPositionForSuperConstructorCalls",
                esStartPos, pos.getStartPosition(cut, es));
        assertEquals("testPositionForSuperConstructorCalls",
                esEndPos, pos.getEndPosition(cut, es));

        MethodInvocationTree mit = (MethodInvocationTree) es.getExpression();

        final int mitStartPos = code.indexOf(mit.toString());
        final int mitEndPos = mitStartPos + mit.toString().length();
        assertEquals("testPositionForSuperConstructorCalls",
                mitStartPos, pos.getStartPosition(cut, mit));
        assertEquals("testPositionForSuperConstructorCalls",
                mitEndPos, pos.getEndPosition(cut, mit));

        final int methodStartPos = mitStartPos;
        final int methodEndPos = methodStartPos + mit.getMethodSelect().toString().length();
        assertEquals("testPositionForSuperConstructorCalls",
                methodStartPos, pos.getStartPosition(cut, mit.getMethodSelect()));
        assertEquals("testPositionForSuperConstructorCalls",
                methodEndPos, pos.getEndPosition(cut, mit.getMethodSelect()));
    }

    @Test
    void testPositionForEnumModifiers() throws IOException {
        final String theString = "public";
        String code = "package test; " + theString + " enum Test {A;}";

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, null, null,
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();
        SourcePositions pos = Trees.instance(ct).getSourcePositions();

        ClassTree clazz = (ClassTree) cut.getTypeDecls().get(0);
        ModifiersTree mt = clazz.getModifiers();
        int spos = code.indexOf(theString);
        int epos = spos + theString.length();
        assertEquals("testPositionForEnumModifiers",
                spos, pos.getStartPosition(cut, mt));
        assertEquals("testPositionForEnumModifiers",
                epos, pos.getEndPosition(cut, mt));
    }

    @Test
    void testNewClassWithEnclosing() throws IOException {

        final String theString = "Test.this.new d()";
        String code = "package test; class Test { " +
                "class d {} private void method() { " +
                "Object o = " + theString + "; } }";

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, null, null,
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();
        SourcePositions pos = Trees.instance(ct).getSourcePositions();

        ClassTree clazz = (ClassTree) cut.getTypeDecls().get(0);
        ExpressionTree est =
                ((VariableTree) ((MethodTree) clazz.getMembers().get(1)).getBody().getStatements().get(0)).getInitializer();

        final int spos = code.indexOf(theString);
        final int epos = spos + theString.length();
        assertEquals("testNewClassWithEnclosing",
                spos, pos.getStartPosition(cut, est));
        assertEquals("testNewClassWithEnclosing",
                epos, pos.getEndPosition(cut, est));
    }

    @Test
    void testPreferredPositionForBinaryOp() throws IOException {

        String code = "package test; public class Test {"
                + "private void test() {"
                + "Object o = null; boolean b = o != null && o instanceof String;"
                + "} private Test() {}}";

        CompilationUnitTree cut = getCompilationUnitTree(code);
        ClassTree clazz = (ClassTree) cut.getTypeDecls().get(0);
        MethodTree method = (MethodTree) clazz.getMembers().get(0);
        VariableTree condSt = (VariableTree) method.getBody().getStatements().get(1);
        BinaryTree cond = (BinaryTree) condSt.getInitializer();

        JCTree condJC = (JCTree) cond;
        int condStartPos = code.indexOf("&&");
        assertEquals("testPreferredPositionForBinaryOp",
                condStartPos, condJC.pos);
    }

    @Test
    void testErrorRecoveryForEnhancedForLoop142381() throws IOException {

        String code = "package test; class Test { " +
                "private void method() { " +
                "java.util.Set<String> s = null; for (a : s) {} } }";

        final List<Diagnostic<? extends JavaFileObject>> errors =
                new LinkedList<Diagnostic<? extends JavaFileObject>>();

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm,
                new DiagnosticListener<JavaFileObject>() {
            public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
                errors.add(diagnostic);
            }
        }, null, null, Arrays.asList(new MyFileObject(code)));

        CompilationUnitTree cut = ct.parse().iterator().next();

        ClassTree clazz = (ClassTree) cut.getTypeDecls().get(0);
        StatementTree forStatement =
                ((MethodTree) clazz.getMembers().get(0)).getBody().getStatements().get(1);

        assertEquals("testErrorRecoveryForEnhancedForLoop142381",
                Kind.ENHANCED_FOR_LOOP, forStatement.getKind());
        assertFalse("testErrorRecoveryForEnhancedForLoop142381", errors.isEmpty());
    }

    @Test
    void testPositionAnnotationNoPackage187551() throws IOException {

        String code = "\n@interface Test {}";

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, null, null,
                null, Arrays.asList(new MyFileObject(code)));

        CompilationUnitTree cut = ct.parse().iterator().next();
        ClassTree clazz = (ClassTree) cut.getTypeDecls().get(0);
        Trees t = Trees.instance(ct);

        assertEquals("testPositionAnnotationNoPackage187551",
                1, t.getSourcePositions().getStartPosition(cut, clazz));
    }

    @Test
    void testPositionMissingStatement() throws IOException {
        String code = "class C { void t() { if (true) } }";
        DiagnosticCollector<JavaFileObject> dc = new DiagnosticCollector<>();

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, dc, null,
                null, Arrays.asList(new MyFileObject(code)));

        CompilationUnitTree cut = ct.parse().iterator().next();
        Trees trees = Trees.instance(ct);
        SourcePositions positions = trees.getSourcePositions();

        new TreeScanner<Void, Void>() {
            @Override
            public Void visitIf(IfTree it, Void v) {
                StatementTree st = it.getThenStatement();
                int startpos = (int) positions.getStartPosition(cut, st);
                int endpos = (int) positions.getEndPosition(cut, st);
                assertEquals("testPositionMissingStatement.execpos", startpos, endpos);
                assertEquals("testPositionMissingStatement.execkind",
                             Kind.EXPRESSION_STATEMENT,
                             st.getKind());
                Tree err = ((ExpressionStatementTree) st).getExpression();
                startpos = (int) positions.getStartPosition(cut, err);
                endpos = (int) positions.getEndPosition(cut, err);
                assertEquals("testPositionMissingStatement.errpos", startpos, endpos);
                assertEquals("testPositionMissingStatement.errkind",
                             Kind.ERRONEOUS,
                             err.getKind());
                return super.visitIf(it, v);
            }
        }.scan(cut, null);

        assertEquals("testPositionMissingStatement.diags", 1, dc.getDiagnostics().size());
        Diagnostic<? extends JavaFileObject> d = dc.getDiagnostics().get(0);
        int startpos = (int) d.getStartPosition();
        int pos = (int) d.getPosition();
        int endpos = (int) d.getEndPosition();
        assertEquals("testPositionMissingStatement.diagspan", startpos, endpos);
        assertEquals("testPositionMissingStatement.diagpref", startpos, pos);
    }

    @Test
    void testPositionsSane1() throws IOException {
        performPositionsSanityTest("package test; class Test { " +
                "private void method() { " +
                "java.util.List<? extends java.util.List<? extends String>> l; " +
                "} }");
    }

    @Test
    void testPositionsSane2() throws IOException {
        performPositionsSanityTest("package test; class Test { " +
                "private void method() { " +
                "java.util.List<? super java.util.List<? super String>> l; " +
                "} }");
    }

    @Test
    void testPositionsSane3() throws IOException {
        performPositionsSanityTest("package test; class Test { " +
                "private void method() { " +
                "java.util.List<? super java.util.List<?>> l; } }");
    }

    private void performPositionsSanityTest(String code) throws IOException {

        final List<Diagnostic<? extends JavaFileObject>> errors =
                new LinkedList<Diagnostic<? extends JavaFileObject>>();

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm,
                new DiagnosticListener<JavaFileObject>() {

            public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
                errors.add(diagnostic);
            }
        }, null, null, Arrays.asList(new MyFileObject(code)));

        final CompilationUnitTree cut = ct.parse().iterator().next();
        final Trees trees = Trees.instance(ct);

        new TreeScanner<Void, Void>() {

            private long parentStart = 0;
            private long parentEnd = Integer.MAX_VALUE;

            @Override
            public Void scan(Tree node, Void p) {
                if (node == null) {
                    return null;
                }

                long start = trees.getSourcePositions().getStartPosition(cut, node);

                if (start == (-1)) {
                    return null; // synthetic tree
                }
                assertTrue(node.toString() + ":" + start + "/" + parentStart,
                        parentStart <= start);

                long prevParentStart = parentStart;

                parentStart = start;

                long end = trees.getSourcePositions().getEndPosition(cut, node);

                assertTrue(node.toString() + ":" + end + "/" + parentEnd,
                        end <= parentEnd);

                long prevParentEnd = parentEnd;

                parentEnd = end;

                super.scan(node, p);

                parentStart = prevParentStart;
                parentEnd = prevParentEnd;

                return null;
            }

            private void assertTrue(String message, boolean b) {
                if (!b) fail(message);
            }
        }.scan(cut, null);
    }

    @Test
    void testCorrectWildcardPositions1() throws IOException {
        performWildcardPositionsTest("package test; import java.util.List; " +
                "class Test { private void method() { List<? extends List<? extends String>> l; } }",

                Arrays.asList("List<? extends List<? extends String>> l;",
                "List<? extends List<? extends String>>",
                "List",
                "? extends List<? extends String>",
                "List<? extends String>",
                "List",
                "? extends String",
                "String"));
    }

    @Test
    void testCorrectWildcardPositions2() throws IOException {
        performWildcardPositionsTest("package test; import java.util.List; "
                + "class Test { private void method() { List<? super List<? super String>> l; } }",
                Arrays.asList("List<? super List<? super String>> l;",
                "List<? super List<? super String>>",
                "List",
                "? super List<? super String>",
                "List<? super String>",
                "List",
                "? super String",
                "String"));
    }

    @Test
    void testCorrectWildcardPositions3() throws IOException {
        performWildcardPositionsTest("package test; import java.util.List; " +
                "class Test { private void method() { List<? super List<?>> l; } }",

                Arrays.asList("List<? super List<?>> l;",
                "List<? super List<?>>",
                "List",
                "? super List<?>",
                "List<?>",
                "List",
                "?"));
    }

    @Test
    void testCorrectWildcardPositions4() throws IOException {
        performWildcardPositionsTest("package test; import java.util.List; " +
                "class Test { private void method() { " +
                "List<? extends List<? extends List<? extends String>>> l; } }",

                Arrays.asList("List<? extends List<? extends List<? extends String>>> l;",
                "List<? extends List<? extends List<? extends String>>>",
                "List",
                "? extends List<? extends List<? extends String>>",
                "List<? extends List<? extends String>>",
                "List",
                "? extends List<? extends String>",
                "List<? extends String>",
                "List",
                "? extends String",
                "String"));
    }

    @Test
    void testCorrectWildcardPositions5() throws IOException {
        performWildcardPositionsTest("package test; import java.util.List; " +
                "class Test { private void method() { " +
                "List<? extends List<? extends List<? extends String   >>> l; } }",
                Arrays.asList("List<? extends List<? extends List<? extends String   >>> l;",
                "List<? extends List<? extends List<? extends String   >>>",
                "List",
                "? extends List<? extends List<? extends String   >>",
                "List<? extends List<? extends String   >>",
                "List",
                "? extends List<? extends String   >",
                "List<? extends String   >",
                "List",
                "? extends String",
                "String"));
    }

    void performWildcardPositionsTest(final String code,
            List<String> golden) throws IOException {

        final List<Diagnostic<? extends JavaFileObject>> errors =
                new LinkedList<Diagnostic<? extends JavaFileObject>>();

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm,
                new DiagnosticListener<JavaFileObject>() {
                    public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
                        errors.add(diagnostic);
                    }
                }, null, null, Arrays.asList(new MyFileObject(code)));

        final CompilationUnitTree cut = ct.parse().iterator().next();
        final List<String> content = new LinkedList<String>();
        final Trees trees = Trees.instance(ct);

        new TreeScanner<Void, Void>() {
            @Override
            public Void scan(Tree node, Void p) {
                if (node == null) {
                    return null;
                }
                long start = trees.getSourcePositions().getStartPosition(cut, node);

                if (start == (-1)) {
                    return null; // synthetic tree
                }
                long end = trees.getSourcePositions().getEndPosition(cut, node);
                String s = code.substring((int) start, (int) end);
                content.add(s);

                return super.scan(node, p);
            }
        }.scan(((MethodTree) ((ClassTree) cut.getTypeDecls().get(0)).getMembers().get(0)).getBody().getStatements().get(0), null);

        assertEquals("performWildcardPositionsTest",golden.toString(),
                content.toString());
    }

    @Test
    void testStartPositionForMethodWithoutModifiers() throws IOException {

        String code = "package t; class Test { <T> void t() {} }";

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, null, null,
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();
        ClassTree clazz = (ClassTree) cut.getTypeDecls().get(0);
        MethodTree mt = (MethodTree) clazz.getMembers().get(0);
        Trees t = Trees.instance(ct);
        int start = (int) t.getSourcePositions().getStartPosition(cut, mt);
        int end = (int) t.getSourcePositions().getEndPosition(cut, mt);

        assertEquals("testStartPositionForMethodWithoutModifiers",
                "<T> void t() {}", code.substring(start, end));
    }

    @Test
    void testVariableInIfThen1() throws IOException {

        String code = "package t; class Test { " +
                "private static void t(String name) { " +
                "if (name != null) String nn = name.trim(); } }";

        DiagnosticCollector<JavaFileObject> coll =
                new DiagnosticCollector<JavaFileObject>();

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, coll, null,
                null, Arrays.asList(new MyFileObject(code)));

        ct.parse();

        List<String> codes = new LinkedList<String>();

        for (Diagnostic<? extends JavaFileObject> d : coll.getDiagnostics()) {
            codes.add(d.getCode());
        }

        assertEquals("testVariableInIfThen1",
                Arrays.<String>asList("compiler.err.variable.not.allowed"),
                codes);
    }

    @Test
   void testVariableInIfThen2() throws IOException {

        String code = "package t; class Test { " +
                "private static void t(String name) { " +
                "if (name != null) class X {} } }";
        DiagnosticCollector<JavaFileObject> coll =
                new DiagnosticCollector<JavaFileObject>();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, coll, null,
                null, Arrays.asList(new MyFileObject(code)));

        ct.parse();

        List<String> codes = new LinkedList<String>();

        for (Diagnostic<? extends JavaFileObject> d : coll.getDiagnostics()) {
            codes.add(d.getCode());
        }

        assertEquals("testVariableInIfThen2",
                Arrays.<String>asList("compiler.err.class.not.allowed"), codes);
    }

    @Test
    void testVariableInIfThen3() throws IOException {

        String code = "package t; class Test { "+
                "private static void t() { " +
                "if (true) abstract class F {} }}";
        DiagnosticCollector<JavaFileObject> coll =
                new DiagnosticCollector<JavaFileObject>();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, coll, null,
                null, Arrays.asList(new MyFileObject(code)));

        ct.parse();

        List<String> codes = new LinkedList<String>();

        for (Diagnostic<? extends JavaFileObject> d : coll.getDiagnostics()) {
            codes.add(d.getCode());
        }

        assertEquals("testVariableInIfThen3",
                Arrays.<String>asList("compiler.err.class.not.allowed"), codes);
    }

    @Test
    void testVariableInIfThen4() throws IOException {

        String code = "package t; class Test { "+
                "private static void t(String name) { " +
                "if (name != null) interface X {} } }";
        DiagnosticCollector<JavaFileObject> coll =
                new DiagnosticCollector<JavaFileObject>();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, coll, null,
                null, Arrays.asList(new MyFileObject(code)));

        ct.parse();

        List<String> codes = new LinkedList<String>();

        for (Diagnostic<? extends JavaFileObject> d : coll.getDiagnostics()) {
            codes.add(d.getCode());
        }

        assertEquals("testVariableInIfThen4",
                Arrays.<String>asList("compiler.err.class.not.allowed"), codes);
    }

    @Test
    void testVariableInIfThen5() throws IOException {

        String code = "package t; class Test { "+
                "private static void t() { " +
                "if (true) } }";
        DiagnosticCollector<JavaFileObject> coll =
                new DiagnosticCollector<JavaFileObject>();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, coll, null,
                null, Arrays.asList(new MyFileObject(code)));

        ct.parse();

        List<String> codes = new LinkedList<String>();

        for (Diagnostic<? extends JavaFileObject> d : coll.getDiagnostics()) {
            codes.add(d.getCode());
        }

        assertEquals("testVariableInIfThen5",
                Arrays.<String>asList("compiler.err.illegal.start.of.stmt"),
                codes);
    }

    // see javac bug #6882235, NB bug #98234:
    @Test
    void testMissingExponent() throws IOException {

        String code = "\nclass Test { { System.err.println(0e); } }";

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, null, null,
                null, Arrays.asList(new MyFileObject(code)));

        assertNotNull(ct.parse().iterator().next());
    }

    @Test
    void testTryResourcePos() throws IOException {

        final String code = "package t; class Test { " +
                "{ try (java.io.InputStream in = null) { } } }";

        CompilationUnitTree cut = getCompilationUnitTree(code);

        new TreeScanner<Void, Void>() {
            @Override
            public Void visitVariable(VariableTree node, Void p) {
                if ("in".contentEquals(node.getName())) {
                    JCTree.JCVariableDecl var = (JCTree.JCVariableDecl) node;
                    assertEquals("testTryResourcePos", "in = null) { } } }",
                            code.substring(var.pos));
                }
                return super.visitVariable(node, p);
            }
        }.scan(cut, null);
    }

    @Test
    void testVarPos() throws IOException {

        final String code = "package t; class Test { " +
                "{ java.io.InputStream in = null; } }";

        CompilationUnitTree cut = getCompilationUnitTree(code);

        new TreeScanner<Void, Void>() {

            @Override
            public Void visitVariable(VariableTree node, Void p) {
                if ("in".contentEquals(node.getName())) {
                    JCTree.JCVariableDecl var = (JCTree.JCVariableDecl) node;
                    assertEquals("testVarPos","in = null; } }",
                            code.substring(var.pos));
                }
                return super.visitVariable(node, p);
            }
        }.scan(cut, null);
    }

    // expected erroneous tree: int x = y;(ERROR);
    @Test
    void testOperatorMissingError() throws IOException {

        String code = "package test; public class ErrorTest { "
                + "void method() { int x = y  z } }";
        CompilationUnitTree cut = getCompilationUnitTree(code);
        final List<String> values = new ArrayList<>();
        final List<String> expectedValues =
                new ArrayList<>(Arrays.asList("[z]"));

        new TreeScanner<Void, Void>() {
            @Override
            public Void visitErroneous(ErroneousTree node, Void p) {
                values.add(getErroneousTreeValues(node).toString());
                return null;

            }
        }.scan(cut, null);

        assertEquals("testOperatorMissingError: The Erroneous tree "
                + "error values: " + values
                + " do not match expected error values: "
                + expectedValues, values, expectedValues);
    }

    // expected erroneous tree:  String s = (ERROR);
    @Test
    void testMissingParenthesisError() throws IOException {

        String code = "package test; public class ErrorTest { "
                + "void f() {String s = new String; } }";
        CompilationUnitTree cut = getCompilationUnitTree(code);
        final List<String> values = new ArrayList<>();
        final List<String> expectedValues =
                new ArrayList<>(Arrays.asList("[new String()]"));

        new TreeScanner<Void, Void>() {
            @Override
            public Void visitErroneous(ErroneousTree node, Void p) {
                values.add(getErroneousTreeValues(node).toString());
                return null;
            }
        }.scan(cut, null);

        assertEquals("testMissingParenthesisError: The Erroneous tree "
                + "error values: " + values
                + " do not match expected error values: "
                + expectedValues, values, expectedValues);
    }

    // expected erroneous tree: package test; (ERROR)(ERROR)
    @Test
    void testMissingClassError() throws IOException {

        String code = "package Test; clas ErrorTest {  "
                + "void f() {String s = new String(); } }";
        CompilationUnitTree cut = getCompilationUnitTree(code);
        final List<String> values = new ArrayList<>();
        final List<String> expectedValues =
                new ArrayList<>(Arrays.asList("[, clas]", "[]"));

        new TreeScanner<Void, Void>() {
            @Override
            public Void visitErroneous(ErroneousTree node, Void p) {
                values.add(getErroneousTreeValues(node).toString());
                return null;
            }
        }.scan(cut, null);

        assertEquals("testMissingClassError: The Erroneous tree "
                + "error values: " + values
                + " do not match expected error values: "
                + expectedValues, values, expectedValues);
    }

    // expected erroneous tree: void m1(int i) {(ERROR);{(ERROR);}
    @Test
    void testSwitchError() throws IOException {

        String code = "package test; public class ErrorTest { "
                + "int numDays; void m1(int i) { switchh {i} { case 1: "
                + "numDays = 31; break; } } }";
        CompilationUnitTree cut = getCompilationUnitTree(code);
        final List<String> values = new ArrayList<>();
        final List<String> expectedValues =
                new ArrayList<>(Arrays.asList("[switchh]", "[i]"));

        new TreeScanner<Void, Void>() {
            @Override
            public Void visitErroneous(ErroneousTree node, Void p) {
                values.add(getErroneousTreeValues(node).toString());
                return null;
            }
        }.scan(cut, null);

        assertEquals("testSwitchError: The Erroneous tree "
                + "error values: " + values
                + " do not match expected error values: "
                + expectedValues, values, expectedValues);
    }

    // expected erroneous tree: class ErrorTest {(ERROR)
    @Test
    void testMethodError() throws IOException {

        String code = "package Test; class ErrorTest {  "
                + "static final void f) {String s = new String(); } }";
        CompilationUnitTree cut = cut = getCompilationUnitTree(code);

        final List<String> values = new ArrayList<>();
        final List<String> expectedValues =
                new ArrayList<>(Arrays.asList("[\nstatic final void f();]"));

        new TreeScanner<Void, Void>() {
            @Override
            public Void visitErroneous(ErroneousTree node, Void p) {
                values.add(normalize(getErroneousTreeValues(node).toString()));
                return null;
            }
        }.scan(cut, null);

        assertEquals("testMethodError: The Erroneous tree "
                + "error value: " + values
                + " does not match expected error values: "
                + expectedValues, values, expectedValues);
    }

    @Test
    void testPositionBrokenSource126732a() throws IOException {
        String[] commands = new String[]{
            "return Runnable()",
            "do { } while (true)",
            "throw UnsupportedOperationException()",
            "assert true",
            "1 + 1",};

        for (String command : commands) {

            String code = "package test;\n"
                    + "public class Test {\n"
                    + "    public static void test() {\n"
                    + "        " + command + " {\n"
                    + "                new Runnable() {\n"
                    + "        };\n"
                    + "    }\n"
                    + "}";
            JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, null,
                    null, null, Arrays.asList(new MyFileObject(code)));
            CompilationUnitTree cut = ct.parse().iterator().next();

            ClassTree clazz = (ClassTree) cut.getTypeDecls().get(0);
            MethodTree method = (MethodTree) clazz.getMembers().get(0);
            List<? extends StatementTree> statements =
                    method.getBody().getStatements();

            StatementTree ret = statements.get(0);
            StatementTree block = statements.get(1);

            Trees t = Trees.instance(ct);
            int len = code.indexOf(command + " {") + (command + " ").length();
            assertEquals(command, len,
                    t.getSourcePositions().getEndPosition(cut, ret));
            assertEquals(command, len,
                    t.getSourcePositions().getStartPosition(cut, block));
        }
    }

    @Test
    void testPositionBrokenSource126732b() throws IOException {
        String[] commands = new String[]{
            "break",
            "break A",
            "continue ",
            "continue A",};

        for (String command : commands) {

            String code = "package test;\n"
                    + "public class Test {\n"
                    + "    public static void test() {\n"
                    + "        while (true) {\n"
                    + "            " + command + " {\n"
                    + "                new Runnable() {\n"
                    + "        };\n"
                    + "        }\n"
                    + "    }\n"
                    + "}";

            JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, null,
                    null, null, Arrays.asList(new MyFileObject(code)));
            CompilationUnitTree cut = ct.parse().iterator().next();

            ClassTree clazz = (ClassTree) cut.getTypeDecls().get(0);
            MethodTree method = (MethodTree) clazz.getMembers().get(0);
            List<? extends StatementTree> statements =
                    ((BlockTree) ((WhileLoopTree) method.getBody().getStatements().get(0)).getStatement()).getStatements();

            StatementTree ret = statements.get(0);
            StatementTree block = statements.get(1);

            Trees t = Trees.instance(ct);
            int len = code.indexOf(command + " {") + (command + " ").length();
            assertEquals(command, len,
                    t.getSourcePositions().getEndPosition(cut, ret));
            assertEquals(command, len,
                    t.getSourcePositions().getStartPosition(cut, block));
        }
    }

    @Test
    void testStartPositionEnumConstantInit() throws IOException {

        String code = "package t; enum Test { AAA; }";

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, null, null,
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();
        ClassTree clazz = (ClassTree) cut.getTypeDecls().get(0);
        VariableTree enumAAA = (VariableTree) clazz.getMembers().get(0);
        Trees t = Trees.instance(ct);
        int start = (int) t.getSourcePositions().getStartPosition(cut,
                enumAAA.getInitializer());

        assertEquals("testStartPositionEnumConstantInit", 23, start);
    }

    @Test
    void testVoidLambdaParameter() throws IOException {
        String code = "package t; class Test { " +
                "Runnable r = (void v) -> { };" +
                "}";
        DiagnosticCollector<JavaFileObject> coll =
                new DiagnosticCollector<>();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, coll, null,
                null, Arrays.asList(new MyFileObject(code)));

        CompilationUnitTree cut = ct.parse().iterator().next();
        ClassTree clazz = (ClassTree) cut.getTypeDecls().get(0);
        VariableTree field = (VariableTree) clazz.getMembers().get(0);

        assertEquals("actual kind: " + field.getInitializer().getKind(),
                     field.getInitializer().getKind(),
                     Kind.LAMBDA_EXPRESSION);

        LambdaExpressionTree lambda = (LambdaExpressionTree) field.getInitializer();

        assertEquals("actual parameters: " + lambda.getParameters().size(),
                     lambda.getParameters().size(),
                     1);

        Tree paramType = lambda.getParameters().get(0).getType();

        assertEquals("actual parameter type: " + paramType.getKind(),
                     paramType.getKind(),
                     Kind.PRIMITIVE_TYPE);

        TypeKind primitiveTypeKind = ((PrimitiveTypeTree) paramType).getPrimitiveTypeKind();

        assertEquals("actual parameter type: " + primitiveTypeKind,
                     primitiveTypeKind,
                     TypeKind.VOID);
    }

    @Test //JDK-8065753
    void testWrongFirstToken() throws IOException {
        String code = "<";
        String expectedErrors = "Test.java:1:1: compiler.err.class.method.or.field.expected\n" +
                                "1 error\n";
        StringWriter out = new StringWriter();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(out, fm, null,
                Arrays.asList("-XDrawDiagnostics"), null, Arrays.asList(new MyFileObject(code)));

        Result errorCode = ct.doCall();
        assertEquals("the error code is not correct; actual:" + errorCode, Main.Result.ERROR, errorCode);
        String actualErrors = normalize(out.toString());
        assertEquals("the error message is not correct, actual: " + actualErrors, expectedErrors, actualErrors);
    }

    @Test //JDK-8205913
    void testForInit() throws IOException {
        String code = "class T { void t() { for (n : ns) { } } }";
        String expectedErrors = "Test.java:1:27: compiler.err.bad.initializer: for-loop\n";
        StringWriter out = new StringWriter();
        JavacTask ct = (JavacTask) tool.getTask(out, fm, null,
                Arrays.asList("-XDrawDiagnostics"), null, Arrays.asList(new MyFileObject(code)));

        Iterable<? extends CompilationUnitTree> cuts = ct.parse();
        boolean[] foundVar = new boolean[1];

        new TreePathScanner<Void, Void>() {
            @Override public Void visitVariable(VariableTree vt, Void p) {
                assertNotNull(vt.getModifiers());
                assertNotNull(vt.getType());
                assertNotNull(vt.getName());
                assertEquals("name should be <error>", "<error>", vt.getName().toString());
                foundVar[0] = true;
                return super.visitVariable(vt, p);
            }
        }.scan(cuts, null);

        if (!foundVar[0]) {
            fail("haven't found a variable");
        }

        String actualErrors = normalize(out.toString());
        assertEquals("the error message is not correct, actual: " + actualErrors, expectedErrors, actualErrors);
    }

    @Test //JDK-821742
    void testCompDeclVarType() throws IOException {
        String code = "package test; public class Test {"
                + "private void test() {"
                + "var v1 = 10,v2 = 12;"
                + "} private Test() {}}";

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, null,
                null, null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();
        ct.enter();
        ct.analyze();
        ClassTree clazz = (ClassTree) cut.getTypeDecls().get(0);
        MethodTree method = (MethodTree) clazz.getMembers().get(0);
        VariableTree stmt1 = (VariableTree) method.getBody().getStatements().get(0);
        VariableTree stmt2 = (VariableTree) method.getBody().getStatements().get(1);
        Tree v1Type = stmt1.getType();
        Tree v2Type = stmt2.getType();
        assertEquals("Implicit type for v1 is not correct: ", Kind.PRIMITIVE_TYPE, v1Type.getKind());
        assertEquals("Implicit type for v2 is not correct: ", Kind.PRIMITIVE_TYPE, v2Type.getKind());
    }

    @Test
    void testCaseBodyStatements() throws IOException {
        String code = "class C {" +
                      "    void t(int i) {" +
                      "        switch (i) {" +
                      "            case 0 -> i++;" +
                      "            case 1 -> { i++; }" +
                      "            case 2 -> throw new RuntimeException();" +
                      "            case 3 -> if (true) ;" +
                      "            default -> i++;" +
                      "        }" +
                      "        switch (i) {" +
                      "            case 0: i++; break;" +
                      "            case 1: { i++; break;}" +
                      "            case 2: throw new RuntimeException();" +
                      "            case 3: if (true) ; break;" +
                      "            default: i++; break;" +
                      "        }" +
                      "        int j = switch (i) {" +
                      "            case 0 -> i + 1;" +
                      "            case 1 -> { yield i + 1; }" +
                      "            default -> throw new RuntimeException();" +
                      "        };" +
                      "        int k = switch (i) {" +
                      "            case 0: yield i + 1;" +
                      "            case 1: { yield i + 1; }" +
                      "            default: throw new RuntimeException();" +
                      "        };" +
                      "    }" +
                      "}";
        String expectedErrors = "Test.java:1:178: compiler.err.switch.case.unexpected.statement\n";
        StringWriter out = new StringWriter();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(out, fm, null,
                Arrays.asList("-XDrawDiagnostics"),
                null, Arrays.asList(new MyFileObject(code)));

        CompilationUnitTree cut = ct.parse().iterator().next();
        Trees trees = Trees.instance(ct);
        List<String> spans = new ArrayList<>();

        new TreePathScanner<Void, Void>() {
            @Override
            public Void visitCase(CaseTree tree, Void v) {
                if (tree.getBody() != null) {
                    int start = (int) trees.getSourcePositions().getStartPosition(cut, tree.getBody());
                    int end = (int) trees.getSourcePositions().getEndPosition(cut, tree.getBody());
                    spans.add(code.substring(start, end));
                } else {
                    spans.add("<null>");
                }
                return super.visitCase(tree, v);
            }
        }.scan(cut, null);

        List<String> expectedSpans = List.of(
                "i++;", "{ i++; }", "throw new RuntimeException();", "if (true) ;", "i++;",
                "<null>", "<null>", "<null>", "<null>", "<null>",
                "i + 1"/*TODO semicolon?*/, "{ yield i + 1; }", "throw new RuntimeException();",
                "<null>", "<null>", "<null>");
        assertEquals("the error spans are not correct; actual:" + spans, expectedSpans, spans);
        String toString = normalize(cut.toString());
        String expectedToString =
                "\n" +
                "class C {\n" +
                "    \n" +
                "    void t(int i) {\n" +
                "        switch (i) {\n" +
                "        case 0 -> i++;\n" +
                "        case 1 -> {\n" +
                "            i++;\n" +
                "        }\n" +
                "        case 2 -> throw new RuntimeException();\n" +
                "        case 3 -> if (true) ;\n" +
                "        default -> i++;\n" +
                "        }\n" +
                "        switch (i) {\n" +
                "        case 0:\n" +
                "            i++;\n" +
                "            break;\n" +
                "        \n" +
                "        case 1:\n" +
                "            {\n" +
                "                i++;\n" +
                "                break;\n" +
                "            }\n" +
                "        \n" +
                "        case 2:\n" +
                "            throw new RuntimeException();\n" +
                "        \n" +
                "        case 3:\n" +
                "            if (true) ;\n" +
                "            break;\n" +
                "        \n" +
                "        default:\n" +
                "            i++;\n" +
                "            break;\n" +
                "        \n" +
                "        }\n" +
                "        int j = switch (i) {\n" +
                "        case 0 -> yield i + 1;\n" +
                "        case 1 -> {\n" +
                "            yield i + 1;\n" +
                "        }\n" +
                "        default -> throw new RuntimeException();\n" +
                "        };\n" +
                "        int k = switch (i) {\n" +
                "        case 0:\n" +
                "            yield i + 1;\n" +
                "        \n" +
                "        case 1:\n" +
                "            {\n" +
                "                yield i + 1;\n" +
                "            }\n" +
                "        \n" +
                "        default:\n" +
                "            throw new RuntimeException();\n" +
                "        \n" +
                "        };\n" +
                "    }\n" +
                "}";
        System.err.println("toString:");
        System.err.println(toString);
        System.err.println("expectedToString:");
        System.err.println(expectedToString);
        assertEquals("the error spans are not correct; actual:" + toString, expectedToString, toString);
        String actualErrors = normalize(out.toString());
        assertEquals("the error message is not correct, actual: " + actualErrors, expectedErrors, actualErrors);
    }

    @Test
    void testTypeParamsWithoutMethod() throws IOException {
        assert tool != null;

        String code = "package test; class Test { /**javadoc*/ |public <T> |}";
        String[] parts = code.split("\\|");

        code = parts[0] + parts[1] + parts[2];

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, null, null,
                null, Arrays.asList(new MyFileObject(code)));
        Trees trees = Trees.instance(ct);
        SourcePositions pos = trees.getSourcePositions();
        CompilationUnitTree cut = ct.parse().iterator().next();
        ClassTree clazz = (ClassTree) cut.getTypeDecls().get(0);
        ErroneousTree err = (ErroneousTree) clazz.getMembers().get(0);
        MethodTree method = (MethodTree) err.getErrorTrees().get(0);

        final int methodStart = parts[0].length();
        final int methodEnd = parts[0].length() + parts[1].length();
        assertEquals("testTypeParamsWithoutMethod",
                methodStart, pos.getStartPosition(cut, method));
        assertEquals("testTypeParamsWithoutMethod",
                methodEnd, pos.getEndPosition(cut, method));

        TreePath path2Method = new TreePath(new TreePath(new TreePath(cut), clazz), method);
        String javadoc = trees.getDocComment(path2Method);

        if (!"javadoc".equals(javadoc)) {
            throw new AssertionError("Expected javadoc not found, actual javadoc: " + javadoc);
        }
    }

    @Test
    void testAnalyzeParensWithComma1() throws IOException {
        assert tool != null;

        String code = "package test; class Test { FI fi = |(s, |";
        String[] parts = code.split("\\|", 3);

        code = parts[0] + parts[1] + parts[2];

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, null, null,
                null, Arrays.asList(new MyFileObject(code)));
        Trees trees = Trees.instance(ct);
        SourcePositions pos = trees.getSourcePositions();
        CompilationUnitTree cut = ct.parse().iterator().next();
        boolean[] found = new boolean[1];

        new TreeScanner<Void, Void>() {
            @Override
            public Void visitLambdaExpression(LambdaExpressionTree tree, Void v) {
                found[0] = true;
                int lambdaStart = parts[0].length();
                int lambdaEnd = parts[0].length() + parts[1].length();
                assertEquals("testAnalyzeParensWithComma1",
                        lambdaStart, pos.getStartPosition(cut, tree));
                assertEquals("testAnalyzeParensWithComma1",
                        lambdaEnd, pos.getEndPosition(cut, tree));
                return null;
            }
        }.scan(cut, null);

        assertTrue("testAnalyzeParensWithComma1", found[0]);
    }

    @Test
    void testAnalyzeParensWithComma2() throws IOException {
        assert tool != null;

        String code = "package test; class Test { FI fi = |(s, o)|";
        String[] parts = code.split("\\|", 3);

        code = parts[0] + parts[1] + parts[2];

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, null, null,
                null, Arrays.asList(new MyFileObject(code)));
        Trees trees = Trees.instance(ct);
        SourcePositions pos = trees.getSourcePositions();
        CompilationUnitTree cut = ct.parse().iterator().next();
        boolean[] found = new boolean[1];

        new TreeScanner<Void, Void>() {
            @Override
            public Void visitLambdaExpression(LambdaExpressionTree tree, Void v) {
                found[0] = true;
                int lambdaStart = parts[0].length();
                int lambdaEnd = parts[0].length() + parts[1].length();
                assertEquals("testAnalyzeParensWithComma2",
                        lambdaStart, pos.getStartPosition(cut, tree));
                assertEquals("testAnalyzeParensWithComma2",
                        lambdaEnd, pos.getEndPosition(cut, tree));
                return null;
            }
        }.scan(cut, null);

        assertTrue("testAnalyzeParensWithComma2", found[0]);
    }

    @Test
    void testBrokenEnum1() throws IOException {
        assert tool != null;

        String code = "package test; class Test { enum E { A, B, C. D, E, F; } }";
        StringWriter output = new StringWriter();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(output, fm, null, List.of("-XDrawDiagnostics"),
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();
        List<String> actual = List.of(output.toString().split("\r?\n"));
        List<String> expected = List.of("Test.java:1:44: compiler.err.expected3: ',', '}', ';'");

        assertEquals("The expected and actual errors do not match, actual errors: " + actual,
                     actual,
                     expected);

        String actualAST = cut.toString().replaceAll("\r*\n", "\n");
        String expectedAST = "package test;\n" +
                             "\n" +
                             "class Test {\n" +
                             "    \n" +
                             "    enum E {\n" +
                             "        /*public static final*/ A /* = new E() */ /*enum*/ ,\n" +
                             "        /*public static final*/ B /* = new E() */ /*enum*/ ,\n" +
                             "        /*public static final*/ C /* = new E() */ /*enum*/ ,\n" +
                             "        /*public static final*/ D /* = new E() */ /*enum*/ ,\n" +
                             "        /*public static final*/ E /* = new E() */ /*enum*/ ,\n" +
                             "        /*public static final*/ F /* = new E() */ /*enum*/ ;\n" +
                             "        (ERROR) <error>;\n" +
                             "    }\n" +
                             "}";
        assertEquals("The expected and actual AST do not match, actual AST: " + actualAST,
                     actualAST,
                     expectedAST);
    }

    @Test
    void testBrokenEnum2() throws IOException {
        assert tool != null;

        String code = "package test; class Test { enum E { A, B, C void t() {} } }";
        StringWriter output = new StringWriter();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(output, fm, null, List.of("-XDrawDiagnostics"),
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();
        List<String> actual = List.of(output.toString().split("\r?\n"));
        List<String> expected = List.of("Test.java:1:44: compiler.err.expected3: ',', '}', ';'");

        assertEquals("The expected and actual errors do not match, actual errors: " + actual,
                     actual,
                     expected);

        String actualAST = cut.toString().replaceAll("\r*\n", "\n");
        String expectedAST = "package test;\n" +
                             "\n" +
                             "class Test {\n" +
                             "    \n" +
                             "    enum E {\n" +
                             "        /*public static final*/ A /* = new E() */ /*enum*/ ,\n" +
                             "        /*public static final*/ B /* = new E() */ /*enum*/ ,\n" +
                             "        /*public static final*/ C /* = new E() */ /*enum*/ ;\n" +
                             "        \n" +
                             "        void t() {\n" +
                             "        }\n" +
                             "    }\n" +
                             "}";
        assertEquals("The expected and actual AST do not match, actual AST: " + actualAST,
                     actualAST,
                     expectedAST);
    }

    @Test
    void testBrokenEnum3() throws IOException {
        assert tool != null;

        String code = "package test; class Test { enum E { , void t() {} } }";
        StringWriter output = new StringWriter();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(output, fm, null, List.of("-XDrawDiagnostics"),
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();
        List<String> actual = List.of(output.toString().split("\r?\n"));
        List<String> expected = List.of("Test.java:1:38: compiler.err.expected2: '}', ';'");

        assertEquals("The expected and actual errors do not match, actual errors: " + actual,
                     actual,
                     expected);

        String actualAST = cut.toString().replaceAll("\r*\n", "\n");
        String expectedAST = "package test;\n" +
                             "\n" +
                             "class Test {\n" +
                             "    \n" +
                             "    enum E {\n" +
                             ";\n" +
                             "        \n" +
                             "        void t() {\n" +
                             "        }\n" +
                             "    }\n" +
                             "}";
        assertEquals("The expected and actual AST do not match, actual AST: " + actualAST,
                     actualAST,
                     expectedAST);
    }

    @Test
    void testBrokenEnum4() throws IOException {
        assert tool != null;

        String code = "package test; class Test { enum E { A, B, C, void t() {} } }";
        StringWriter output = new StringWriter();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(output, fm, null, List.of("-XDrawDiagnostics"),
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();
        List<String> actual = List.of(output.toString().split("\r?\n"));
        List<String> expected = List.of("Test.java:1:46: compiler.err.enum.constant.expected");

        assertEquals("The expected and actual errors do not match, actual errors: " + actual,
                     actual,
                     expected);

        String actualAST = cut.toString().replaceAll("\r*\n", "\n");
        String expectedAST = "package test;\n" +
                             "\n" +
                             "class Test {\n" +
                             "    \n" +
                             "    enum E {\n" +
                             "        /*public static final*/ A /* = new E() */ /*enum*/ ,\n" +
                             "        /*public static final*/ B /* = new E() */ /*enum*/ ,\n" +
                             "        /*public static final*/ C /* = new E() */ /*enum*/ ;\n" +
                             "        \n" +
                             "        void t() {\n" +
                             "        }\n" +
                             "    }\n" +
                             "}";
        assertEquals("The expected and actual AST do not match, actual AST: " + actualAST,
                     actualAST,
                     expectedAST);
    }

    @Test
    void testBrokenEnum5() throws IOException {
        assert tool != null;

        String code = "package test; class Test { enum E { A; void t() {} B; } }";
        StringWriter output = new StringWriter();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(output, fm, null, List.of("-XDrawDiagnostics"),
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();
        List<String> actual = List.of(output.toString().split("\r?\n"));
        List<String> expected = List.of("Test.java:1:52: compiler.err.enum.constant.not.expected");

        assertEquals("The expected and actual errors do not match, actual errors: " + actual,
                     actual,
                     expected);

        String actualAST = cut.toString().replaceAll("\r*\n", "\n");
        String expectedAST = "package test;\n" +
                             "\n" +
                             "class Test {\n" +
                             "    \n" +
                             "    enum E {\n" +
                             "        /*public static final*/ A /* = new E() */ /*enum*/ ,\n" +
                             "        /*public static final*/ B /* = new E() */ /*enum*/ ;\n" +
                             "        \n" +
                             "        void t() {\n" +
                             "        }\n" +
                             "    }\n" +
                             "}";
        assertEquals("The expected and actual AST do not match, actual AST: " + actualAST,
                     actualAST,
                     expectedAST);
    }

    @Test
    void testCompoundAssignment() throws IOException {
        assert tool != null;

        String code = "package test; class Test { v += v v;}";
        StringWriter output = new StringWriter();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(output, fm, null, List.of("-XDrawDiagnostics"),
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();
        List<String> actual = List.of(output.toString().split("\r?\n"));
        List<String> expected = List.of("Test.java:1:29: compiler.err.expected: token.identifier");

        assertEquals("The expected and actual errors do not match, actual errors: " + actual,
                     actual,
                     expected);

        String actualAST = cut.toString().replaceAll("\\R", "\n");
        String expectedAST = "package test;\n" +
                             "\n" +
                             "class Test {\n" +
                             "    v <error>;\n" +
                             "    v v;\n" +
                             "}";
        assertEquals("The expected and actual AST do not match, actual AST: " + actualAST,
                     actualAST,
                     expectedAST);
    }

    @Test
    void testStartAndEndPositionForClassesInPermitsClause() throws IOException {
        String code = "package t; sealed class Test permits Sub1, Sub2 {} final class Sub1 extends Test {} final class Sub2 extends Test {}";
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, null,
                null, null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();
        ClassTree clazz = (ClassTree) cut.getTypeDecls().get(0);
        List<? extends Tree> permitsList = clazz.getPermitsClause();
        assertEquals("testStartAndEndPositionForClassesInPermitsClause", 2, permitsList.size());
        Trees t = Trees.instance(ct);
        List<String> expected = List.of("Sub1", "Sub2");
        int i = 0;
        for (Tree permitted: permitsList) {
            int start = (int) t.getSourcePositions().getStartPosition(cut, permitted);
            int end = (int) t.getSourcePositions().getEndPosition(cut, permitted);
            assertEquals("testStartAndEndPositionForClassesInPermitsClause", expected.get(i++), code.substring(start, end));
        }
    }

    @Test //JDK-8237041
    void testDeepNestingNoClose() throws IOException {
        //verify that many nested unclosed classes do not crash javac
        //due to the safety fallback in JavacParser.reportSyntaxError:
        String code = "package t; class Test {\n";
        for (int i = 0; i < 100; i++) {
            code += "class C" + i + " {\n";
        }
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, null, List.of("-XDdev"),
                null, Arrays.asList(new MyFileObject(code)));
        Result result = ct.doCall();
        assertEquals("Expected a (plain) error, got: " + result, result, Result.ERROR);
    }

    @Test //JDK-8237041
    void testErrorRecoveryClassNotBrace() throws IOException {
        //verify the AST form produced for classes without opening brace
        //(classes without an opening brace do not nest the upcoming content):
        String code = """
                      package t;
                      class Test {
                          String.class,
                          String.class,
                          class A
                          public
                          class B
                      }
                      """;
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, null, List.of("-XDdev"),
                null, Arrays.asList(new MyFileObject(code)));
        String ast = ct.parse().iterator().next().toString().replaceAll("\\R", "\n");
        String expected = """
                          package t;
                          \n\
                          class Test {
                              String.<error> <error>;
                              \n\
                              class <error> {
                              }
                              \n\
                              class <error> {
                              }
                              \n\
                              class A {
                              }
                              \n\
                              public class B {
                              }
                          }""";
        assertEquals("Unexpected AST, got:\n" + ast, expected, ast);
    }

    @Test //JDK-8253584
    void testElseRecovery() throws IOException {
        //verify the errors and AST form produced for member selects which are
        //missing the selected member name:
        String code = """
                      package t;
                      class Test {
                          void t() {
                              if (true) {
                                  s().
                              } else {
                              }
                          }
                          String s() {
                              return null;
                          }
                      }
                      """;
        StringWriter out = new StringWriter();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(out, fm, null, List.of("-XDrawDiagnostics"),
                null, Arrays.asList(new MyFileObject(code)));
        String ast = ct.parse().iterator().next().toString().replaceAll("\\R", "\n");
        String expected = """
                          package t;
                          \n\
                          class Test {
                              \n\
                              void t() {
                                  if (true) {
                                      (ERROR);
                                  } else {
                                  }
                              }
                              \n\
                              String s() {
                                  return null;
                              }
                          } """;
        assertEquals("Unexpected AST, got:\n" + ast, expected, ast);
        assertEquals("Unexpected errors, got:\n" + out.toString(),
                     out.toString().replaceAll("\\R", "\n"),
                     """
                     Test.java:5:17: compiler.err.expected: token.identifier
                     Test.java:5:16: compiler.err.not.stmt
                     """);
    }

    @Test
    void testAtRecovery() throws IOException {
        //verify the errors and AST form produced for member selects which are
        //missing the selected member name and are followed by an annotation:
        String code = """
                      package t;
                      class Test {
                          int i1 = "".
                          @Deprecated
                          void t1() {
                          }
                          int i2 = String.
                          @Deprecated
                          void t2() {
                          }
                      }
                      """;
        StringWriter out = new StringWriter();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(out, fm, null, List.of("-XDrawDiagnostics"),
                null, Arrays.asList(new MyFileObject(code)));
        String ast = ct.parse().iterator().next().toString().replaceAll("\\R", "\n");
        String expected = """
                          package t;
                          \n\
                          class Test {
                              int i1 = "".<error>;
                              \n\
                              @Deprecated
                              void t1() {
                              }
                              int i2 = String.<error>;
                              \n\
                              @Deprecated
                              void t2() {
                              }
                          } """;
        assertEquals("Unexpected AST, got:\n" + ast, expected, ast);
        assertEquals("Unexpected errors, got:\n" + out.toString(),
                     out.toString().replaceAll("\\R", "\n"),
                     """
                     Test.java:3:17: compiler.err.expected: token.identifier
                     Test.java:7:21: compiler.err.expected: token.identifier
                     """);
    }

    @Test //JDK-8256411
    void testBasedAnonymous() throws IOException {
        String code = """
                      package t;
                      class Test {
                          class I {}
                          static Object I = new Test().new I() {};
                      }
                      """;
        StringWriter out = new StringWriter();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(out, fm, null, null,
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();
        Trees trees = Trees.instance(ct);
        SourcePositions sp = trees.getSourcePositions();
        ct.analyze();
        List<String> span = new ArrayList<>();
        new TreeScanner<Void, Void>() {
            public Void visitClass(ClassTree ct, Void v) {
                if (ct.getExtendsClause() != null) {
                    int start = (int) sp.getStartPosition(cut,
                                                           ct.getExtendsClause());
                    int end   = (int) sp.getEndPosition(cut,
                                                        ct.getExtendsClause());
                    span.add(code.substring(start, end));
                }
                return super.visitClass(ct, v);
            }
        }.scan(cut, null);
        if (!Objects.equals(span, Arrays.asList("I"))) {
            throw new AssertionError("Unexpected span: " + span);
        }
    }

    @Test //JDK-8259050
    void testBrokenUnicodeEscape() throws IOException {
        String code = "package t;\n" +
                      "class Test {\n" +
                      "    private String s1 = \"\\" + "uaaa\";\n" +
                      "    private String s2 = \\" + "uaaa;\n" +
                      "}\n";
        DiagnosticCollector<JavaFileObject> coll =
                new DiagnosticCollector<>();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, coll, null,
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();
        Trees trees = Trees.instance(ct);
        String ast = cut.toString().replaceAll("\\R", "\n");
        String expected = """
                          package t;

                          class Test {
                              private String s1 = "";
                              private String s2 = (ERROR);
                          } """;
        assertEquals("Unexpected AST, got:\n" + ast, expected, ast);
        List<String> codes = new LinkedList<>();

        for (Diagnostic<? extends JavaFileObject> d : coll.getDiagnostics()) {
            codes.add(d.getCode());
        }

        assertEquals("testBrokenUnicodeEscape: " + codes,
                Arrays.<String>asList("compiler.err.illegal.unicode.esc",
                                      "compiler.err.illegal.unicode.esc"),
                codes);
    }

    @Test //JDK-8259050
    void testUsupportedTextBlock() throws IOException {
        String code = """
                      package t;
                      class Test {
                          private String s = \"""
                                             \""";
                      }""";
        DiagnosticCollector<JavaFileObject> coll =
                new DiagnosticCollector<>();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, coll, List.of("--release", "14"),
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();
        Trees trees = Trees.instance(ct);
        String ast = cut.toString().replaceAll("\\R", "\n");
        String expected = """
                          package t;

                          class Test {
                              private String s = "";
                          } """;
        assertEquals("Unexpected AST, got:\n" + ast, expected, ast);
        List<String> codes = new LinkedList<>();

        for (Diagnostic<? extends JavaFileObject> d : coll.getDiagnostics()) {
            codes.add(d.getCode());
        }

        assertEquals("testUsupportedTextBlock: " + codes,
                Arrays.<String>asList("compiler.err.feature.not.supported.in.source.plural"),
                codes);
    }

    @Test //JDK-8266436
    void testSyntheticConstructorReturnType() throws IOException {
        String code = """
                      package test;
                      public class Test {
                      }
                      """;

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, null,
                null, null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();
        ct.analyze();
        ClassTree clazz = (ClassTree) cut.getTypeDecls().get(0);
        MethodTree constr = (MethodTree) clazz.getMembers().get(0);
        assertEquals("expected null as constructor return type", constr.getReturnType(), null);
    }

    @Test //JDK-8267221
    void testVarArgArrayParameter() throws IOException {
        String code = """
                      package test;
                      public class Test {
                           private void test(int[]... p) {}
                      }
                      """;

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, null,
                null, null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();
        ClassTree clazz = (ClassTree) cut.getTypeDecls().get(0);
        MethodTree constr = (MethodTree) clazz.getMembers().get(0);
        VariableTree param = constr.getParameters().get(0);
        SourcePositions sp = Trees.instance(ct).getSourcePositions();
        int typeStart = (int) sp.getStartPosition(cut, param.getType());
        int typeEnd   = (int) sp.getEndPosition(cut, param.getType());
        assertEquals("correct parameter type span", code.substring(typeStart, typeEnd), "int[]...");
    }

    @Test //JDK-8271928
    void testX() throws IOException {
        String code = """
                      package test;
                          public static void test() {
                              return test;
                          }
                      """;

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, null,
                null, null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();
        SourcePositions sp = Trees.instance(ct).getSourcePositions();
        new TreePathScanner<Void, Void>() {
            @Override
            public Void visitErroneous(ErroneousTree tree, Void p) {
                int pos = (int) sp.getStartPosition(cut, tree);
                if (pos == (-1)) {
                    fail("Invalid source position for an ErroneousTree");
                }
                return scan(tree.getErrorTrees(), p);
            }
        }.scan(cut, null);
    }

    @Test //JDK-8275097
    void testDefaultTagPosition() throws IOException {
        String code = """
                      package t;
                      class Test {
                          private void test1(int i) {
                              switch (i) {
                                  default:
                              }
                          }
                          private void test2(int i) {
                              switch (i) {
                                  case default:
                              }
                          }
                          private int test3(int i) {
                              return switch (i) {
                                  default: yield 0;
                              }
                          }
                          private int test4(int i) {
                              return switch (i) {
                                  case default: yield 0;
                              }
                          }
                          private void test5(int i) {
                              switch (i) {
                                  default -> {}
                              }
                          }
                          private void test6(int i) {
                              switch (i) {
                                  case default -> {}
                              }
                          }
                          private int test5(int i) {
                              return switch (i) {
                                  default -> { yield 0; }
                              }
                          }
                          private int test6(int i) {
                              return switch (i) {
                                  case default -> { yield 0; }
                              }
                          }
                          private int test7(int i) {
                              return switch (i) {
                                  default -> 0;
                              }
                          }
                          private int test8(int i) {
                              return switch (i) {
                                  case default -> 0;
                              }
                          }
                      }
                      """;

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, null, null,
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();
        Trees t = Trees.instance(ct);
        SourcePositions sp = t.getSourcePositions();
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitDefaultCaseLabel(DefaultCaseLabelTree tree, Void p) {
                int start = (int) sp.getStartPosition(cut, tree);
                int end   = (int) sp.getEndPosition(cut, tree);
                String defaultName = code.substring(start, end);
                if (!"default".equals(defaultName)) {
                    throw new AssertionError("Incorrect span: " + defaultName);
                }
                return super.visitDefaultCaseLabel(tree, p);
            }

            @Override
            public Void visitCase(CaseTree node, Void p) {
                scan(node.getLabels(), p);
                if (node.getCaseKind() == CaseTree.CaseKind.RULE)
                    scan(node.getBody(), p);
                else
                    scan(node.getStatements(), p);
                return null;
            }
        }.scan(cut, null);
    }

    @Test //JDK-8293897
    void testImplicitFinalInTryWithResources() throws IOException {
        String code = """
                      package t;
                      class Test {
                          void test1() {
                              try (AutoCloseable ac = null) {}
                          }
                          void test2() {
                              try (@Ann AutoCloseable withAnnotation = null) {}
                          }
                          void test3() {
                              try (final AutoCloseable withFinal = null) {}
                          }
                          void test4() {
                              try (final @Ann AutoCloseable withAnnotationFinal = null) {}
                          }
                          @interface Ann {}
                      }
                      """;

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, null, null,
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();
        Trees t = Trees.instance(ct);
        SourcePositions sp = t.getSourcePositions();
        new TreeScanner<Void, Void>() {
            boolean modifiersHaveFinal;
            boolean modifiersHaveSpan;

            @Override
            public Void visitVariable(VariableTree node, Void p) {
                boolean prevModifiersHaveFinal = modifiersHaveFinal;
                boolean prevModifiersHaveSpan = modifiersHaveSpan;
                try {
                    modifiersHaveFinal = node.getName().toString().contains("Final");
                    modifiersHaveSpan = modifiersHaveFinal ||
                                        node.getName().toString().contains("Annotation");
                    return super.visitVariable(node, p);
                } finally {
                    modifiersHaveFinal = prevModifiersHaveFinal;
                    modifiersHaveSpan = prevModifiersHaveSpan;
                }
            }
            @Override
            public Void visitClass(ClassTree node, Void p) {
                boolean prevModifiersHaveSpan = modifiersHaveSpan;
                try {
                    modifiersHaveSpan = node.getKind() == Kind.ANNOTATION_TYPE;
                    return super.visitClass(node, p);
                } finally {
                    modifiersHaveSpan = prevModifiersHaveSpan;
                }
            }
            @Override
            public Void visitModifiers(ModifiersTree node, Void p) {
                if (modifiersHaveFinal) {
                    if (!node.getFlags().contains(Modifier.FINAL)) {
                        throw new AssertionError("Expected final missing.");
                    }
                } else {
                    if (node.getFlags().contains(Modifier.FINAL)) {
                        throw new AssertionError("Unexpected final modified.");
                    }
                }
                long start = sp.getStartPosition(cut, node);
                long end = sp.getEndPosition(cut, node);
                if (modifiersHaveSpan) {
                    if (start == (-1) || end == (-1)) {
                        throw new AssertionError("Incorrect modifier span: " + start + "-" + end);
                    }
                } else {
                    if (start != (-1) || end != (-1)) {
                        throw new AssertionError("Incorrect modifier span: " + start + "-" + end);
                    }
                }
                return super.visitModifiers(node, p);
            }
        }.scan(cut, null);
    }

    @Test //JDK-8295401
    void testModuleInfoProvidesRecovery() throws IOException {
        String code = """
                      module m {
                          $DIRECTIVE
                      }
                      """;
        record Test(String directive, int prefix, Kind expectedKind) {}
        Test[] tests = new Test[] {
            new Test("uses api.api.API;", 4, Kind.USES),
            new Test("opens api.api to other.module;", 5, Kind.OPENS),
            new Test("exports api.api to other.module;", 7, Kind.EXPORTS),
            new Test("provides java.util.spi.ToolProvider with impl.ToolProvider;", 8, Kind.PROVIDES),
        };
        JavacTaskPool pool = new JavacTaskPool(1);
        for (Test test : tests) {
            String directive = test.directive();
            for (int i = test.prefix(); i < directive.length(); i++) {
                String replaced = code.replace("$DIRECTIVE", directive.substring(0, i));
                pool.getTask(null, null, d -> {}, List.of(), null, List.of(new MyFileObject(replaced)), task -> {
                    try {
                        CompilationUnitTree cut = task.parse().iterator().next();
                        new TreePathScanner<Void, Void>() {
                            @Override
                            public Void visitModule(ModuleTree node, Void p) {
                                assertEquals("Unexpected directives size: " + node.getDirectives().size(),
                                             node.getDirectives().size(),
                                             1);
                                assertEquals("Unexpected directive: " + node.getDirectives().get(0).getKind(),
                                             node.getDirectives().get(0).getKind(),
                                             test.expectedKind);
                                return super.visitModule(node, p);
                            }
                        }.scan(cut, null);
                        return null;
                    } catch (IOException ex) {
                        throw new IllegalStateException(ex);
                    }
                });
            }
        }
        String extendedCode = """
                              module m {
                                  provides ;
                                  provides java.;
                                  provides java.util.spi.ToolProvider with ;
                                  provides java.util.spi.ToolProvider with impl.;
                              """;
        pool.getTask(null, null, d -> {}, List.of(), null, List.of(new MyFileObject("module-info", extendedCode)), task -> {
            try {
                CompilationUnitTree cut = task.parse().iterator().next();
                task.analyze();
                new TreePathScanner<Void, Void>() {
                    @Override
                    public Void visitModule(ModuleTree node, Void p) {
                        assertEquals("Unexpected directives size: " + node.getDirectives().size(),
                                     node.getDirectives().size(),
                                     4);
                        return super.visitModule(node, p);
                    }
                }.scan(cut, null);
                return null;
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    @Test //JDK-8304671
    void testEnumConstantUnderscore() throws IOException {
        record TestCase(String code, String release, String ast, String errors) {}
        TestCase[] testCases = new TestCase[] {
            new TestCase("""
                         package t;
                         enum Test {
                             _
                         }
                         """,
                         "8",
                         """
                         package t;
                         \n\
                         enum Test {
                             /*public static final*/ _ /* = new Test() */ /*enum*/ ;
                         } """,
                         """
                         - compiler.warn.option.obsolete.source: 8
                         - compiler.warn.option.obsolete.target: 8
                         - compiler.warn.option.obsolete.suppression
                         Test.java:3:5: compiler.warn.underscore.as.identifier
                         """),
            new TestCase("""
                         package t;
                         enum Test {
                             _
                         }
                         """,
                         System.getProperty("java.specification.version"),
                         """
                         package t;
                         \n\
                         enum Test {
                             /*public static final*/ _ /* = new Test() */ /*enum*/ ;
                         } """,
                         """
                         Test.java:3:5: compiler.err.use.of.underscore.not.allowed.non.variable
                         """),
            new TestCase("""
                         package t;
                         enum Test {
                             _;
                         }
                         """,
                         "8",
                         """
                         package t;
                         \n\
                         enum Test {
                             /*public static final*/ _ /* = new Test() */ /*enum*/ ;
                         } """,
                         """
                         - compiler.warn.option.obsolete.source: 8
                         - compiler.warn.option.obsolete.target: 8
                         - compiler.warn.option.obsolete.suppression
                         Test.java:3:5: compiler.warn.underscore.as.identifier
                         """),
            new TestCase("""
                         package t;
                         enum Test {
                             _;
                         }
                         """,
                         System.getProperty("java.specification.version"),
                         """
                         package t;
                         \n\
                         enum Test {
                             /*public static final*/ _ /* = new Test() */ /*enum*/ ;
                         } """,
                         """
                         Test.java:3:5: compiler.err.use.of.underscore.not.allowed.non.variable
                         """),
            new TestCase("""
                         package t;
                         enum Test {
                             A;
                             void t() {}
                             _;
                         }
                         """,
                         "8",
                         """
                         package t;
                         \n\
                         enum Test {
                             /*public static final*/ A /* = new Test() */ /*enum*/ ,
                             /*public static final*/ _ /* = new Test() */ /*enum*/ ;
                             \n\
                             void t() {
                             }
                         } """,
                         """
                         - compiler.warn.option.obsolete.source: 8
                         - compiler.warn.option.obsolete.target: 8
                         - compiler.warn.option.obsolete.suppression
                         Test.java:5:5: compiler.err.enum.constant.not.expected
                         Test.java:5:5: compiler.warn.underscore.as.identifier
                         """),
            new TestCase("""
                         package t;
                         enum Test {
                             A;
                             void t() {}
                             _;
                         }
                         """,
                         System.getProperty("java.specification.version"),
                         """
                         package t;
                         \n\
                         enum Test {
                             /*public static final*/ A /* = new Test() */ /*enum*/ ,
                             /*public static final*/ _ /* = new Test() */ /*enum*/ ;
                             \n\
                             void t() {
                             }
                         } """,
                         """
                         Test.java:5:5: compiler.err.enum.constant.not.expected
                         """),
            new TestCase("""
                         package t;
                         enum Test {
                             _ {},
                             A;
                         }
                         """,
                         "8",
                         """
                         package t;
                         \n\
                         enum Test {
                             /*public static final*/ _ /* = new Test() */ /*enum*/  {
                             },
                             /*public static final*/ A /* = new Test() */ /*enum*/ ;
                         } """,
                         """
                         - compiler.warn.option.obsolete.source: 8
                         - compiler.warn.option.obsolete.target: 8
                         - compiler.warn.option.obsolete.suppression
                         Test.java:3:5: compiler.warn.underscore.as.identifier
                         """),
            new TestCase("""
                         package t;
                         enum Test {
                             _ {},
                             A;
                         }
                         """,
                         System.getProperty("java.specification.version"),
                         """
                         package t;
                         \n\
                         enum Test {
                             /*public static final*/ _ /* = new Test() */ /*enum*/  {
                             },
                             /*public static final*/ A /* = new Test() */ /*enum*/ ;
                         } """,
                         """
                         Test.java:3:5: compiler.err.use.of.underscore.not.allowed.non.variable
                         """),
        };
        for (TestCase testCase : testCases) {
            StringWriter out = new StringWriter();
            JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(out, fm, null,
                    List.of("-XDrawDiagnostics", "--release", testCase.release),
                    null, Arrays.asList(new MyFileObject(testCase.code)));
            String ast = ct.parse().iterator().next().toString().replaceAll("\\R", "\n");
            assertEquals("Unexpected AST, got:\n" + ast, testCase.ast, ast);
            assertEquals("Unexpected errors, got:\n" + out.toString(),
                         out.toString().replaceAll("\\R", "\n"),
                         testCase.errors);
        }
    }

    @Test
    void testGuardRecovery() throws IOException {
        String code = """
                      package t;
                      class Test {
                          private int t(Integer i, boolean b) {
                              switch (i) {
                                  case 0 when b -> {}
                                  case null when b -> {}
                                  default when b -> {}
                              }
                              return switch (i) {
                                  case 0 when b -> 0;
                                  case null when b -> 0;
                                  default when b -> 0;
                              };
                          }
                      }""";
        DiagnosticCollector<JavaFileObject> coll =
                new DiagnosticCollector<>();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, coll, null,
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitCase(CaseTree node, Void p) {
                assertNotNull(node.getGuard());
                assertEquals("guard kind", Kind.ERRONEOUS, node.getGuard().getKind());
                assertEquals("guard content",
                             List.of("b"),
                             ((ErroneousTree) node.getGuard()).getErrorTrees()
                                                              .stream()
                                                              .map(t -> t.toString()).toList());
                return super.visitCase(node, p);
            }
        }.scan(cut, null);

        List<String> codes = new LinkedList<>();

        for (Diagnostic<? extends JavaFileObject> d : coll.getDiagnostics()) {
            codes.add(d.getLineNumber() + ":" + d.getColumnNumber() + ":" +  d.getCode());
        }

        assertEquals("testUsupportedTextBlock: " + codes,
                List.of("5:20:compiler.err.guard.not.allowed",
                        "6:23:compiler.err.guard.not.allowed",
                        "7:21:compiler.err.guard.not.allowed",
                        "10:20:compiler.err.guard.not.allowed",
                        "11:23:compiler.err.guard.not.allowed",
                        "12:21:compiler.err.guard.not.allowed"),
                codes);
    }

    @Test //JDK-8310326
    void testUnnamedClassPositions() throws IOException {
        //             0         1         2
        //             012345678901234567890
        String code = "void main() { }";
        DiagnosticCollector<JavaFileObject> coll =
                new DiagnosticCollector<>();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, coll, null,
                null, Arrays.asList(new MyFileObject(code)));
        Trees trees = Trees.instance(ct);
        SourcePositions sp = trees.getSourcePositions();
        CompilationUnitTree cut = ct.parse().iterator().next();
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitClass(ClassTree node, Void p) {
                assertEquals("Wrong start position", 0, sp.getStartPosition(cut, node));
                assertEquals("Wrong end position", 15, sp.getEndPosition(cut, node));
                assertEquals("Wrong modifiers start position", -1, sp.getStartPosition(cut, node.getModifiers()));
                assertEquals("Wrong modifiers end position", -1, sp.getEndPosition(cut, node.getModifiers()));
                return super.visitClass(node, p);
            }
        }.scan(cut, null);
    }

    @Test //JDK-8312093
    void testJavadoc() throws IOException {
        String code = """
                      public class Test {
                          /***/
                          void main() {
                          }
                      }
                      """;
        DiagnosticCollector<JavaFileObject> coll =
                new DiagnosticCollector<>();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, coll, null,
                null, Arrays.asList(new MyFileObject(code)));
        Trees trees = Trees.instance(ct);
        CompilationUnitTree cut = ct.parse().iterator().next();
        new TreePathScanner<Void, Void>() {
            @Override
            public Void visitMethod(MethodTree node, Void p) {
                if (!node.getName().contentEquals("main")) {
                    return null;
                }
                String comment = trees.getDocComment(getCurrentPath());
                assertEquals("Expecting empty comment", "", comment);
                return null;
            }
        }.scan(cut, null);
    }

    @Test //JDK-8312204
    void testDanglingElse() throws IOException {
        String code = """
                      void main() {
                          else ;
                      }
                      """;
        DiagnosticCollector<JavaFileObject> coll =
                new DiagnosticCollector<>();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, coll,
                List.of("--enable-preview", "--source", SOURCE_VERSION),
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();

        String result = cut.toString().replaceAll("\\R", "\n");
        System.out.println("RESULT\n" + result);
        assertEquals("incorrect AST",
                     result,
                     """
                     \n\
                     final class Test {
                         \n\
                         void main() {
                             (ERROR);
                         }
                     }""");

        List<String> codes = new LinkedList<>();

        for (Diagnostic<? extends JavaFileObject> d : coll.getDiagnostics()) {
            codes.add(d.getLineNumber() + ":" + d.getColumnNumber() + ":" + d.getCode());
        }

        assertEquals("testDanglingElse: " + codes,
                     List.of("2:5:compiler.err.else.without.if"),
                     codes);
    }

    @Test //JDK-8315452
    void testPartialTopLevelModifiers() throws IOException {
        String code = """
                      package test;
                      public
                      """;
        DiagnosticCollector<JavaFileObject> coll =
                new DiagnosticCollector<>();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, coll,
                List.of("--enable-preview", "--source", SOURCE_VERSION),
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();

        String result = toStringWithErrors(cut).replaceAll("\\R", "\n");
        System.out.println("RESULT\n" + result);
        assertEquals("incorrect AST",
                     result,
                     """
                     package test;
                     (ERROR: public )""");
    }

    @Test //JDK-8337976
    void testStatementsInClass() throws IOException {
        String code = """
                      package test;
                      public class Test {
                          if (true);
                          while (true);
                          do {} while (true);
                          for ( ; ; );
                          switch (0) { default: }
                          assert true;
                          break;
                          continue;
                          return ;
                          throw new RuntimeException();
                          try {
                          } catch (RuntimeException ex) {}
                      }
                      """;
        DiagnosticCollector<JavaFileObject> coll =
                new DiagnosticCollector<>();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, coll,
                List.of("--enable-preview", "--source", SOURCE_VERSION),
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();

        String result = toStringWithErrors(cut).replaceAll("\\R", "\n");
        System.out.println("RESULT\n" + result);
        assertEquals("incorrect AST",
                     result,
                     """
                     package test;
                     \n\
                     public class Test {
                         (ERROR: if (true) ;)
                         (ERROR: while (true) ;)
                         (ERROR: do {
                     } while (true);)
                         (ERROR: for (; ; ) ;)
                         (ERROR: switch (0) {
                     default:

                     })
                         (ERROR: assert true;)
                         (ERROR: break;)
                         (ERROR: continue;)
                         (ERROR: return;)
                         (ERROR: throw new RuntimeException();)
                         (ERROR: try {
                     } catch (RuntimeException ex) {
                     })
                     }""");

        List<String> codes = new LinkedList<>();

        for (Diagnostic<? extends JavaFileObject> d : coll.getDiagnostics()) {
            codes.add(d.getLineNumber() + ":" + d.getColumnNumber() + ":" + d.getCode());
        }

        assertEquals("testStatementsInClass: " + codes,
                     List.of("3:5:compiler.err.statement.not.expected",
                             "4:5:compiler.err.statement.not.expected",
                             "5:5:compiler.err.statement.not.expected",
                             "6:5:compiler.err.statement.not.expected",
                             "7:5:compiler.err.statement.not.expected",
                             "8:5:compiler.err.statement.not.expected",
                             "9:5:compiler.err.statement.not.expected",
                             "10:5:compiler.err.statement.not.expected",
                             "11:5:compiler.err.statement.not.expected",
                             "12:5:compiler.err.statement.not.expected",
                             "13:5:compiler.err.statement.not.expected"),
                     codes);
    }

    @Test //JDK-8324859
    void testImplicitlyDeclaredClassesConfusion1() throws IOException {
        String code = """
                      package tests;
                      public class TestB {
                          public static boolean test() // missing open brace
                              return true;
                          }
                          public static boolean test2() {
                              return true;
                          }
                      }""";
        DiagnosticCollector<JavaFileObject> coll =
                new DiagnosticCollector<>();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, coll,
                List.of("--enable-preview", "--source", SOURCE_VERSION),
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();

        List<String> codes = new LinkedList<>();

        for (Diagnostic<? extends JavaFileObject> d : coll.getDiagnostics()) {
            codes.add(d.getLineNumber() + ":" + d.getColumnNumber() + ":" + d.getCode());
        }

        assertEquals("testImplicitlyDeclaredClassesConfusion1: " + codes,
                     List.of("3:33:compiler.err.expected2"),
                     codes);
        String result = toStringWithErrors(cut).replaceAll("\\R", "\n");
        System.out.println("RESULT\n" + result);
        assertEquals("incorrect AST",
                     result,
                     """
                     package tests;
                     \n\
                     public class TestB {
                         \n\
                         public static boolean test() {
                             return true;
                         }
                         \n\
                         public static boolean test2() {
                             return true;
                         }
                     }""");
    }

    @Test //JDK-8324859
    void testImplicitlyDeclaredClassesConfusion2() throws IOException {
        String code = """
                      package tests;
                      public class TestB {
                          public static boolean test() // missing open brace

                          public static boolean test2() {
                              return true;
                          }
                      }                       """;
        DiagnosticCollector<JavaFileObject> coll =
                new DiagnosticCollector<>();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, coll,
                List.of("--enable-preview", "--source", SOURCE_VERSION),
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();

        List<String> codes = new LinkedList<>();

        for (Diagnostic<? extends JavaFileObject> d : coll.getDiagnostics()) {
            codes.add(d.getLineNumber() + ":" + d.getColumnNumber() + ":" + d.getCode());
        }

        assertEquals("testImplicitlyDeclaredClassesConfusion2: " + codes,
                     List.of("3:33:compiler.err.expected2"),
                     codes);
        String result = toStringWithErrors(cut).replaceAll("\\R", "\n");
        System.out.println("RESULT\n" + result);
        assertEquals("incorrect AST",
                     result,
                     """
                     package tests;
                     \n\
                     public class TestB {
                         \n\
                         public static boolean test();
                         \n\
                         public static boolean test2() {
                             return true;
                         }
                     }""");
    }

    @Test //JDK-8324859
    void testImplicitlyDeclaredClassesConfusion3() throws IOException {
        String code = """
                      package tests;
                      public class TestB {
                          public static boolean test() // missing open brace
                      }
                      """;
        DiagnosticCollector<JavaFileObject> coll =
                new DiagnosticCollector<>();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, coll,
                List.of("--enable-preview", "--source", SOURCE_VERSION),
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();

        List<String> codes = new LinkedList<>();

        for (Diagnostic<? extends JavaFileObject> d : coll.getDiagnostics()) {
            codes.add(d.getLineNumber() + ":" + d.getColumnNumber() + ":" + d.getCode());
        }

        assertEquals("testImplicitlyDeclaredClassesConfusion3: " + codes,
                     List.of("3:33:compiler.err.expected2"),
                     codes);
        String result = toStringWithErrors(cut).replaceAll("\\R", "\n");
        System.out.println("RESULT\n" + result);
        assertEquals("incorrect AST",
                     result,
                     """
                     package tests;
                     \n\
                     public class TestB {
                         \n\
                         public static boolean test();
                     }""");
    }

    @Test //JDK-8324859
    void testImplicitlyDeclaredClassesConfusion4() throws IOException {
        String code = """
                      package tests;
                      public class TestB {
                          public static boolean test() // missing open brace
                          }
                      }
                      """;
        DiagnosticCollector<JavaFileObject> coll =
                new DiagnosticCollector<>();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, coll,
                List.of("--enable-preview", "--source", SOURCE_VERSION),
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();

        List<String> codes = new LinkedList<>();

        for (Diagnostic<? extends JavaFileObject> d : coll.getDiagnostics()) {
            codes.add(d.getLineNumber() + ":" + d.getColumnNumber() + ":" + d.getCode());
        }

        assertEquals("testImplicitlyDeclaredClassesConfusion4: " + codes,
                     List.of("3:33:compiler.err.expected2"),
                     codes);
        String result = toStringWithErrors(cut).replaceAll("\\R", "\n");
        System.out.println("RESULT\n" + result);
        assertEquals("incorrect AST",
                     result,
                     """
                     package tests;
                     \n\
                     public class TestB {
                         \n\
                         public static boolean test() {
                         }
                     }""");
    }

    @Test //JDK-8324859
    void testImplicitlyDeclaredClassesConfusion5() throws IOException {
        String code = """
                      package tests;
                      public class TestB {
                          public static boolean test(String,
                      }
                      class T {}
                      """;
        DiagnosticCollector<JavaFileObject> coll =
                new DiagnosticCollector<>();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, coll,
                List.of("--enable-preview", "--source", SOURCE_VERSION),
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();

        List<String> codes = new LinkedList<>();

        for (Diagnostic<? extends JavaFileObject> d : coll.getDiagnostics()) {
            codes.add(d.getLineNumber() + ":" + d.getColumnNumber() + ":" + d.getCode());
        }

        assertEquals("testImplicitlyDeclaredClassesConfusion5: " + codes,
                     List.of("3:38:compiler.err.expected",
                             "4:1:compiler.err.illegal.start.of.type"),
                     codes);
        String result = toStringWithErrors(cut).replaceAll("\\R", "\n");
        System.out.println("RESULT\n" + result);
        assertEquals("incorrect AST",
                     result,
                     """
                     package tests;
                     \n\
                     public class TestB {
                         \n\
                         public static boolean test(String <error>, (ERROR: ) <error>);
                     }
                     class T {
                     }""");
    }

    @Test //JDK-8324859
    void testImplicitlyDeclaredClassesConfusion6() throws IOException {
        String code = """
                      package tests;
                      public class TestB {
                          private Object testMethod(final String arg1 final String arg2) {
                              return null;
                          }
                      }
                      """;
        DiagnosticCollector<JavaFileObject> coll =
                new DiagnosticCollector<>();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, coll,
                List.of("--enable-preview", "--source", SOURCE_VERSION),
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();

        List<String> codes = new LinkedList<>();

        for (Diagnostic<? extends JavaFileObject> d : coll.getDiagnostics()) {
            codes.add(d.getLineNumber() + ":" + d.getColumnNumber() + ":" + d.getCode());
        }

        assertEquals("testImplicitlyDeclaredClassesConfusion5: " + codes,
                     List.of("3:48:compiler.err.expected3",
                             "3:66:compiler.err.expected"),
                     codes);
        String result = toStringWithErrors(cut).replaceAll("\\R", "\n");
        System.out.println("RESULT\n" + result);
        assertEquals("incorrect AST",
                     result,
                     """
                     package tests;
                     \n\
                     public class TestB {
                         \n\
                         private Object testMethod(final String arg1);
                         final String arg2;
                         {
                             return null;
                         }
                     }""");
    }

    @Test //JDK-8324859
    void testImplicitlyDeclaredClassesConfusion7() throws IOException {
        //after 'default' attribute value, only semicolon (';') is expected,
        //not left brace ('{'):
        String code = """
                      package tests;
                      public @interface A {
                          public String value() default ""
                      }
                      """;
        DiagnosticCollector<JavaFileObject> coll =
                new DiagnosticCollector<>();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, coll,
                List.of("--enable-preview", "--source", SOURCE_VERSION),
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();

        List<String> codes = new LinkedList<>();

        for (Diagnostic<? extends JavaFileObject> d : coll.getDiagnostics()) {
            codes.add(d.getLineNumber() + ":" + d.getColumnNumber() + ":" + d.getCode());
        }

        assertEquals("testImplicitlyDeclaredClassesConfusion5: " + codes,
                     List.of("3:37:compiler.err.expected"),
                     codes);
        String result = toStringWithErrors(cut).replaceAll("\\R", "\n");
        System.out.println("RESULT\n" + result);
        assertEquals("incorrect AST",
                     result,
                     """
                     package tests;
                     \n\
                     public @interface A {
                         \n\
                         public String value() default "";
                     }""");
    }

    @Test //JDK-8324859
    void testImplicitlyDeclaredClassesConfusion10() throws IOException {
        String code = """
                      package tests;
                      public class TestB {
                          public static boolean test() // missing open brace
                              String s = "";
                              return s.isEmpty();
                          }
                          public static boolean test2() {
                              return true;
                          }
                      }""";
        DiagnosticCollector<JavaFileObject> coll =
                new DiagnosticCollector<>();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, coll,
                List.of("--enable-preview", "--source", SOURCE_VERSION),
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();

        List<String> codes = new LinkedList<>();

        for (Diagnostic<? extends JavaFileObject> d : coll.getDiagnostics()) {
            codes.add(d.getLineNumber() + ":" + d.getColumnNumber() + ":" + d.getCode());
        }

        assertEquals("testImplicitlyDeclaredClassesConfusion1: " + codes,
                     List.of("3:33:compiler.err.expected2"),
                     codes);
        String result = toStringWithErrors(cut).replaceAll("\\R", "\n");
        System.out.println("RESULT\n" + result);
        assertEquals("incorrect AST",
                     result,
                     """
                     package tests;

                     public class TestB {
                         \n\
                         public static boolean test() {
                             String s = "";
                             return s.isEmpty();
                         }
                         \n\
                         public static boolean test2() {
                             return true;
                         }
                     }""");
    }

    @Test //JDK-8324859
    void testImplicitlyDeclaredClassesConfusion11() throws IOException {
        String code = """
                      package tests;
                      public class TestB {
                          public static boolean test() // missing open brace
                          String s = ""; //field declaration
                          public static boolean test2() {
                              return true;
                          }
                      }""";
        DiagnosticCollector<JavaFileObject> coll =
                new DiagnosticCollector<>();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, coll,
                List.of("--enable-preview", "--source", SOURCE_VERSION),
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();

        List<String> codes = new LinkedList<>();

        for (Diagnostic<? extends JavaFileObject> d : coll.getDiagnostics()) {
            codes.add(d.getLineNumber() + ":" + d.getColumnNumber() + ":" + d.getCode());
        }

        assertEquals("testImplicitlyDeclaredClassesConfusion1: " + codes,
                     List.of("3:33:compiler.err.expected2"),
                     codes);
        String result = toStringWithErrors(cut).replaceAll("\\R", "\n");
        System.out.println("RESULT\n" + result);
        assertEquals("incorrect AST",
                     result,
                     """
                     package tests;
                     \n\
                     public class TestB {
                         \n\
                         public static boolean test();
                         String s = "";
                         \n\
                         public static boolean test2() {
                             return true;
                         }
                     }""");
    }

    @Test //JDK-8324859
    void testImplicitlyDeclaredClassesConfusion12() throws IOException {
        String code = """
                      package tests;
                      public class TestB {
                          public static boolean test() // missing open brace
                              final String s = "";
                              return s.isEmpty();
                          }
                          public static boolean test2() {
                              return true;
                          }
                      }""";
        DiagnosticCollector<JavaFileObject> coll =
                new DiagnosticCollector<>();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, coll,
                List.of("--enable-preview", "--source", SOURCE_VERSION),
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();

        List<String> codes = new LinkedList<>();

        for (Diagnostic<? extends JavaFileObject> d : coll.getDiagnostics()) {
            codes.add(d.getLineNumber() + ":" + d.getColumnNumber() + ":" + d.getCode());
        }

        assertEquals("testImplicitlyDeclaredClassesConfusion1: " + codes,
                     List.of("3:33:compiler.err.expected2"),
                     codes);
        String result = toStringWithErrors(cut).replaceAll("\\R", "\n");
        System.out.println("RESULT\n" + result);
        assertEquals("incorrect AST",
                     result,
                     """
                     package tests;
                     \n\
                     public class TestB {
                         \n\
                         public static boolean test() {
                             final String s = "";
                             return s.isEmpty();
                         }
                         \n\
                         public static boolean test2() {
                             return true;
                         }
                     }""");
    }

    @Test //JDK-8324859
    void testImplicitlyDeclaredClassesConfusion13() throws IOException {
        String code = """
                      package tests;
                      public class TestB {
                          public static boolean test() // missing open brace
                          final String s = ""; //field declaration?
                          public static boolean test2() {
                              return true;
                          }
                      }""";
        DiagnosticCollector<JavaFileObject> coll =
                new DiagnosticCollector<>();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, coll,
                List.of("--enable-preview", "--source", SOURCE_VERSION),
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();

        List<String> codes = new LinkedList<>();

        for (Diagnostic<? extends JavaFileObject> d : coll.getDiagnostics()) {
            codes.add(d.getLineNumber() + ":" + d.getColumnNumber() + ":" + d.getCode());
        }

        assertEquals("testImplicitlyDeclaredClassesConfusion1: " + codes,
                     List.of("3:33:compiler.err.expected2"),
                     codes);
        String result = toStringWithErrors(cut).replaceAll("\\R", "\n");
        System.out.println("RESULT\n" + result);
        assertEquals("incorrect AST",
                     result,
                     """
                     package tests;
                     \n\
                     public class TestB {
                         \n\
                         public static boolean test();
                         final String s = "";
                         \n\
                         public static boolean test2() {
                             return true;
                         }
                     }""");
    }

    @Test //JDK-8324859
    void testImplicitlyDeclaredClassesConfusion14() throws IOException {
        String code = """
                      package tests;
                      public class TestB {
                          public static boolean test() // missing open brace
                              String s = "";
                              s.length();
                              if (true); //force parse as block
                          public static boolean test2() {
                              return true;
                          }
                      }""";
        DiagnosticCollector<JavaFileObject> coll =
                new DiagnosticCollector<>();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, fm, coll,
                List.of("--enable-preview", "--source", SOURCE_VERSION),
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();

        List<String> codes = new LinkedList<>();

        for (Diagnostic<? extends JavaFileObject> d : coll.getDiagnostics()) {
            codes.add(d.getLineNumber() + ":" + d.getColumnNumber() + ":" + d.getCode());
        }

        assertEquals("testImplicitlyDeclaredClassesConfusion1: " + codes,
                     List.of("3:33:compiler.err.expected2",
                             "7:5:compiler.err.illegal.start.of.expr"),
                     codes);
        String result = toStringWithErrors(cut).replaceAll("\\R", "\n");
        System.out.println("RESULT\n" + result);
        assertEquals("incorrect AST",
                     result,
                     """
                     package tests;
                     \n\
                     public class TestB {
                         \n\
                         public static boolean test() {
                             String s = "";
                             s.length();
                             if (true) ;
                             (ERROR: );
                         }
                         \n\
                         public static boolean test2() {
                             return true;
                         }
                     }""");
    }

    void run(String[] args) throws Exception {
        int passed = 0, failed = 0;
        final Pattern p = (args != null && args.length > 0)
                ? Pattern.compile(args[0])
                : null;
        for (Method m : this.getClass().getDeclaredMethods()) {
            boolean selected = (p == null)
                    ? m.isAnnotationPresent(Test.class)
                    : p.matcher(m.getName()).matches();
            if (selected) {
                try {
                    m.invoke(this, (Object[]) null);
                    System.out.println(m.getName() + ": OK");
                    passed++;
                } catch (Throwable ex) {
                    System.out.printf("Test %s failed: %s %n", m, ex.getCause());
                    failed++;
                }
            }
        }
        System.out.printf("Passed: %d, Failed %d%n", passed, failed);
        if (failed > 0) {
            throw new RuntimeException("Tests failed: " + failed);
        }
        if (passed == 0 && failed == 0) {
            throw new AssertionError("No test(s) selected: passed = " +
                    passed + ", failed = " + failed + " ??????????");
        }
    }

    private String toStringWithErrors(Tree tree) {
        StringWriter s = new StringWriter();
        try {
            new PrettyWithErrors(s, false).printExpr((JCTree) tree);
        } catch (IOException e) {
            // should never happen, because StringWriter is defined
            // never to throw any IOExceptions
            throw new AssertionError(e);
        }
        return s.toString();
    }

    private static final class PrettyWithErrors extends Pretty {

        public PrettyWithErrors(Writer out, boolean sourceOutput) {
            super(out, sourceOutput);
        }

        @Override
        public void visitErroneous(JCErroneous tree) {
            try {
                print("(ERROR: ");
                print(tree.errs);
                print(")");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

    }

}

abstract class TestCase {

    void assertEquals(String message, int i, int pos) {
        if (i != pos) {
            fail(message);
        }
    }

    void assertFalse(String message, boolean bvalue) {
        if (bvalue == true) {
            fail(message);
        }
    }

    void assertTrue(String message, boolean bvalue) {
        if (bvalue == false) {
            fail(message);
        }
    }

    void assertEquals(String message, int i, long l) {
        if (i != l) {
            fail(message + ":" + i + ":" + l);
        }
    }

    void assertEquals(String message, Object o1, Object o2) {
        if (!Objects.equals(o1, o2)) {
            fail(message);
        }
    }

    void assertNotNull(Object o) {
        if (o == null) {
            fail();
        }
    }

    void fail() {
        fail("test failed");
    }

    void fail(String message) {
        throw new RuntimeException(message);
    }

    /**
     * Indicates that the annotated method is a test method.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Test {}
}
