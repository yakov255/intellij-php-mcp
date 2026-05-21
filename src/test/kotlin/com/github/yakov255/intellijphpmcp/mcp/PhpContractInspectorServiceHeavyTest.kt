package com.github.yakov255.intellijphpmcp.mcp

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.php.lang.psi.elements.Field
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpModifier
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class PhpContractInspectorServiceHeavyTest {

    private val service = PhpContractInspectorService(Mockito.mock())
    private var sharedFile: PsiFile = Mockito.mock()

    @Before
    fun setUp() {
        sharedFile = Mockito.mock(PsiFile::class.java)
    }

    private fun tr(text: String, substring: String): TextRange {
        val start = text.indexOf(substring)
        require(start >= 0) { "Substring '$substring' not found in '$text'" }
        return TextRange(start, start + substring.length)
    }

    private fun mockPublicMethod(name: String, textRange: TextRange, bodyRange: TextRange?): Method {
        val method = Mockito.mock(Method::class.java)
        val modifier = Mockito.mock(PhpModifier::class.java)
        Mockito.`when`(method.name).thenReturn(name)
        Mockito.`when`(method.modifier).thenReturn(modifier)
        Mockito.`when`(modifier.isPublic).thenReturn(true)
        Mockito.`when`(method.textRange).thenReturn(textRange)
        Mockito.`when`(method.text).thenReturn("")
        Mockito.`when`(method.containingFile).thenReturn(sharedFile)
        val children: Array<PsiElement> = if (bodyRange != null) {
            val body = Mockito.mock(PsiElement::class.java)
            Mockito.`when`(body.textRange).thenReturn(bodyRange)
            Mockito.`when`(body.text).thenReturn("{")
            arrayOf(body)
        } else emptyArray()
        Mockito.`when`(method.children).thenReturn(children)
        return method
    }

    private fun mockMethod(name: String, textRange: TextRange, isPublic: Boolean): Method {
        val method = Mockito.mock(Method::class.java)
        val modifier = Mockito.mock(PhpModifier::class.java)
        Mockito.`when`(method.name).thenReturn(name)
        Mockito.`when`(method.modifier).thenReturn(modifier)
        Mockito.`when`(modifier.isPublic).thenReturn(isPublic)
        Mockito.`when`(method.textRange).thenReturn(textRange)
        Mockito.`when`(method.text).thenReturn("")
        Mockito.`when`(method.children).thenReturn(emptyArray())
        Mockito.`when`(method.containingFile).thenReturn(sharedFile)
        return method
    }

    private fun mockField(name: String, textRange: TextRange, isPublic: Boolean): Field {
        val field = Mockito.mock(Field::class.java)
        val modifier = Mockito.mock(PhpModifier::class.java)
        Mockito.`when`(field.name).thenReturn(name)
        Mockito.`when`(field.modifier).thenReturn(modifier)
        Mockito.`when`(modifier.isPublic).thenReturn(isPublic)
        Mockito.`when`(field.textRange).thenReturn(textRange)
        Mockito.`when`(field.text).thenReturn("")
        Mockito.`when`(field.containingFile).thenReturn(sharedFile)
        val parent = Mockito.mock(PsiElement::class.java)
        Mockito.`when`(parent.textRange).thenReturn(textRange)
        Mockito.`when`(field.parent).thenReturn(parent)
        return field
    }

    private fun mockClass(methods: List<Method>, fields: List<Field>): PhpClass {
        val cls = Mockito.mock(PhpClass::class.java)
        Mockito.`when`(cls.containingFile).thenReturn(sharedFile)
        Mockito.`when`(cls.methods).thenReturn(methods)
        Mockito.`when`(cls.fields).thenReturn(fields)
        return cls
    }

    @Test
    fun `test keep public methods strip bodies`() {
        val text = "<?php\nclass Test {\n    public function foo(): string { return 'foo'; }\n    public function bar(): void { echo 'bar'; }\n}\n"
        val bodyA = tr(text, "{ return 'foo'; }")
        val bodyB = tr(text, "{ echo 'bar'; }")

        val result = service.generateContract(text, listOf(mockClass(
            listOf(
                mockPublicMethod("foo", tr(text, "public function foo(): string { return 'foo'; }"), bodyA),
                mockPublicMethod("bar", tr(text, "public function bar(): void { echo 'bar'; }"), bodyB),
            ),
            emptyList()
        )), sharedFile)

        Assert.assertTrue("should keep foo signature", result.contains("function foo(): string"))
        Assert.assertTrue("should keep bar signature", result.contains("function bar(): void"))
        Assert.assertFalse("should strip foo body", result.contains("return 'foo'"))
        Assert.assertFalse("should strip bar body", result.contains("echo 'bar'"))
    }

    @Test
    fun `test remove non-public methods`() {
        val text = "<?php\nclass Test {\n    public function foo(): void {}\n    private function bar(): void {}\n    protected function baz(): void {}\n}\n"
        val mFoo = tr(text, "public function foo(): void {}")
        val mBar = tr(text, "private function bar(): void {}")
        val mBaz = tr(text, "protected function baz(): void {}")

        val result = service.generateContract(text, listOf(mockClass(
            listOf(
                mockMethod("foo", mFoo, true),
                mockMethod("bar", mBar, false),
                mockMethod("baz", mBaz, false),
            ),
            emptyList()
        )), sharedFile)

        Assert.assertTrue("should keep public", result.contains("function foo()"))
        Assert.assertFalse("should remove private", result.contains("function bar()"))
        Assert.assertFalse("should remove protected", result.contains("function baz()"))
    }

    @Test
    fun `test remove non-public fields`() {
        val text = "<?php\nclass Test {\n    public string \$name = '';\n    private int \$id;\n    protected float \$price;\n}\n"
        val fPub = tr(text, "public string \$name = '';")
        val fPriv = tr(text, "private int \$id;")
        val fProt = tr(text, "protected float \$price;")

        val result = service.generateContract(text, listOf(mockClass(
            emptyList(),
            listOf(
                mockField("name", fPub, true),
                mockField("id", fPriv, false),
                mockField("price", fProt, false),
            )
        )), sharedFile)

        Assert.assertTrue("should keep public field", result.contains("public string"))
        Assert.assertFalse("should remove private field", result.contains("private int"))
        Assert.assertFalse("should remove protected field", result.contains("protected float"))
    }

    @Test
    fun `test file without classes returns as is`() {
        val text = "<?php function helper(): void {}"
        val result = service.generateContract(text, emptyList(), sharedFile)
        Assert.assertEquals(text, result)
    }

    @Test
    fun `test body replacement ends with semicolon`() {
        val text = "<?php class Semi { public function go(): void { doWork(); } }"
        val body = tr(text, "{ doWork(); }")
        val cls = mockClass(
            listOf(mockPublicMethod("go", tr(text, "public function go(): void { doWork(); }"), body)),
            emptyList()
        )
        val result = service.generateContract(text, listOf(cls), sharedFile)
        Assert.assertTrue("body should be replaced with semicolon", result.contains(": void;"))
    }

    @Test
    fun `test mixed visibility`() {
        val text = "<?php\nclass Vis {\n    public function pub(): void {}\n    private function priv(): void {}\n    protected function prot(): void {}\n    function implicit(): void {}\n}\n"
        val cls = mockClass(
            listOf(
                mockMethod("pub", tr(text, "public function pub(): void {}"), true),
                mockMethod("priv", tr(text, "private function priv(): void {}"), false),
                mockMethod("prot", tr(text, "protected function prot(): void {}"), false),
                mockMethod("implicit", tr(text, "function implicit(): void {}"), true),
            ),
            emptyList()
        )
        val result = service.generateContract(text, listOf(cls), sharedFile)

        Assert.assertTrue("should keep public", result.contains("function pub()"))
        Assert.assertFalse("should remove private", result.contains("function priv()"))
        Assert.assertFalse("should remove protected", result.contains("function prot()"))
        Assert.assertTrue("should keep implicit public", result.contains("function implicit()"))
    }

    @Test
    fun `test operations applied in reverse order by start offset`() {
        val text = "keepA {bodyA} keepB {bodyB}"
        val bodyA = tr(text, "{bodyA}")
        val bodyB = tr(text, "{bodyB}")
        val cls = mockClass(
            listOf(
                mockPublicMethod("a", tr(text, "keepA {bodyA}"), bodyA),
                mockPublicMethod("b", tr(text, "keepB {bodyB}"), bodyB),
            ),
            emptyList()
        )
        val result = service.generateContract(text, listOf(cls), sharedFile)
        Assert.assertEquals("keepA; keepB;", result)
    }

    @Test
    fun `test interface methods with no bodies are kept`() {
        val text = "<?php\ninterface MyInterface {\n    public function doSomething(): string;\n    public function doMore(): void;\n}\n"
        val cls = mockClass(
            listOf(
                mockPublicMethod("doSomething", tr(text, "public function doSomething(): string;"), null),
                mockPublicMethod("doMore", tr(text, "public function doMore(): void;"), null),
            ),
            emptyList()
        )
        val result = service.generateContract(text, listOf(cls), sharedFile)
        Assert.assertTrue("should keep doSomething", result.contains("function doSomething(): string"))
        Assert.assertTrue("should keep doMore", result.contains("function doMore(): void"))
    }

    @Test
    fun `test abstract class keeps abstract method strips concrete body`() {
        val text = "<?php\nabstract class AbstractBase {\n    abstract public function abs(): void;\n    public function concrete(): string { return 'hello'; }\n}\n"
        val concreteBody = tr(text, "{ return 'hello'; }")
        val cls = mockClass(
            listOf(
                mockPublicMethod("abs", tr(text, "abstract public function abs(): void;"), null),
                mockPublicMethod("concrete", tr(text, "public function concrete(): string { return 'hello'; }"), concreteBody),
            ),
            emptyList()
        )
        val result = service.generateContract(text, listOf(cls), sharedFile)
        Assert.assertTrue("should keep abstract method", result.contains("function abs()"))
        Assert.assertTrue("should keep concrete method", result.contains("function concrete(): string"))
        Assert.assertFalse("should strip concrete body", result.contains("return 'hello'"))
    }

    @Test
    fun `test multiple classes in one file`() {
        val text = "<?php\nclass Alpha {\n    public function a(): void { echo 'a'; }\n}\nclass Beta {\n    private function b(): void {}\n    public function c(): void { echo 'c'; }\n}\n"
        val bodyA = tr(text, "{ echo 'a'; }")
        val bodyC = tr(text, "{ echo 'c'; }")
        val alpha = mockClass(
            listOf(mockPublicMethod("a", tr(text, "public function a(): void { echo 'a'; }"), bodyA)),
            emptyList()
        )
        val beta = mockClass(
            listOf(
                mockMethod("b", tr(text, "private function b(): void {}"), false),
                mockPublicMethod("c", tr(text, "public function c(): void { echo 'c'; }"), bodyC),
            ),
            emptyList()
        )
        val result = service.generateContract(text, listOf(alpha, beta), sharedFile)
        Assert.assertTrue("should keep Alpha", result.contains("Alpha"))
        Assert.assertTrue("should keep a signature", result.contains("function a()"))
        Assert.assertFalse("should remove Beta private b", result.contains("function b()"))
        Assert.assertTrue("should keep c signature", result.contains("function c()"))
        Assert.assertFalse("should strip a body", result.contains("echo 'a'"))
    }

    @Test
    fun `test method with no body in file without classes`() {
        val text = "<?php\nfunction helper(): void {}\n"
        val result = service.generateContract(text, emptyList(), sharedFile)
        Assert.assertEquals(text, result)
    }

    @Test
    fun `test class with only public fields`() {
        val text = "<?php\nclass Config {\n    public string \$env = 'dev';\n    public int \$debug = 1;\n}\n"
        val fEnv = tr(text, "public string \$env = 'dev';")
        val fDebug = tr(text, "public int \$debug = 1;")
        val cls = mockClass(
            emptyList(),
            listOf(
                mockField("env", fEnv, true),
                mockField("debug", fDebug, true),
            )
        )
        val result = service.generateContract(text, listOf(cls), sharedFile)
        Assert.assertTrue("should keep env field", result.contains("public string"))
        Assert.assertTrue("should keep debug field", result.contains("public int"))
    }

    @Test
    fun `test class with mixed methods and fields`() {
        val text = "<?php\nclass User {\n    public string \$name = '';\n    private int \$id;\n    public function getName(): string { return \$this->name; }\n    private function getId(): int { return \$this->id; }\n}\n"
        val bodyGetName = tr(text, "{ return \$this->name; }")
        val cls = mockClass(
            listOf(
                mockPublicMethod("getName", tr(text, "public function getName(): string { return \$this->name; }"), bodyGetName),
                mockMethod("getId", tr(text, "private function getId(): int { return \$this->id; }"), false),
            ),
            listOf(
                mockField("name", tr(text, "public string \$name = '';"), true),
                mockField("id", tr(text, "private int \$id;"), false),
            )
        )
        val result = service.generateContract(text, listOf(cls), sharedFile)
        Assert.assertTrue("should keep public field", result.contains("public string"))
        Assert.assertFalse("should remove private field", result.contains("private int"))
        Assert.assertTrue("should keep public method", result.contains("function getName()"))
        Assert.assertFalse("should remove private method", result.contains("function getId()"))
        Assert.assertFalse("should strip body", result.contains("return \$this->name"))
    }

    @Test
    fun `test public method without body group statement keeps signature`() {
        val text = "<?php\nclass Foo {\n    public function bar(): void;\n}\n"
        val cls = mockClass(
            listOf(mockPublicMethod("bar", tr(text, "public function bar(): void;"), null)),
            emptyList()
        )
        val result = service.generateContract(text, listOf(cls), sharedFile)
        Assert.assertTrue("should keep method signature", result.contains("function bar(): void"))
    }

    @Test
    fun `test operations with overlapping method and field removal`() {
        val text = "<?php\nclass Demo {\n    private int \$x;\n    public function run(): void { doit(); }\n}\n"
        val body = tr(text, "{ doit(); }")
        val cls = mockClass(
            listOf(mockPublicMethod("run", tr(text, "public function run(): void { doit(); }"), body)),
            listOf(mockField("x", tr(text, "private int \$x;"), false))
        )
        val result = service.generateContract(text, listOf(cls), sharedFile)
        Assert.assertTrue("should keep public method", result.contains("function run()"))
        Assert.assertFalse("should remove private field", result.contains("private int"))
        Assert.assertFalse("should strip body", result.contains("doit()"))
    }
}
