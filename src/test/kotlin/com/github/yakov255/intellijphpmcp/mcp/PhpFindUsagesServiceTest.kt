package com.github.yakov255.intellijphpmcp.mcp

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class PhpFindUsagesServiceTest : BasePlatformTestCase() {

    private val mockIndex: PhpIndex = Mockito.mock(PhpIndex::class.java)
    private lateinit var service: PhpFindUsagesService

    @Before
    override fun setUp() {
        super.setUp()
        service = object : PhpFindUsagesService(project) {
            override fun getPhpIndex(): PhpIndex = mockIndex
        }
    }

    private fun mockClass(fqcn: String): PhpClass {
        val cls: PhpClass = Mockito.mock(PhpClass::class.java)
        Mockito.`when`(cls.fqn).thenReturn(fqcn)
        val clean = fqcn.trimStart('\\')
        Mockito.`when`(mockIndex.getClassesByFQN(clean)).thenReturn(listOf(cls))
        Mockito.`when`(mockIndex.getInterfacesByFQN(clean)).thenReturn(emptyList())
        Mockito.`when`(mockIndex.getTraitsByFQN(clean)).thenReturn(emptyList())
        return cls
    }

    private fun mockClassWithMethods(fqcn: String, methodNames: List<String>): PhpClass {
        val cls = Mockito.mock(PhpClass::class.java)
        Mockito.`when`(cls.fqn).thenReturn(fqcn)
        val methods = methodNames.map { name ->
            val method = Mockito.mock(Method::class.java)
            Mockito.`when`(method.name).thenReturn(name)
            Mockito.`when`(method.fqn).thenReturn("$fqcn::$name")
            method
        }
        Mockito.`when`(cls.methods).thenReturn(methods)
        Mockito.`when`(cls.fields).thenReturn(emptyList())
        val clean = fqcn.trimStart('\\')
        Mockito.`when`(mockIndex.getClassesByFQN(clean)).thenReturn(listOf(cls))
        Mockito.`when`(mockIndex.getInterfacesByFQN(clean)).thenReturn(emptyList())
        Mockito.`when`(mockIndex.getTraitsByFQN(clean)).thenReturn(emptyList())
        return cls
    }

    // --- resolveSymbol tests ---

    @Test
    fun testResolveClassFqcn() {
        mockClass("App\\Mailer")
        val result = service.resolveSymbol("\\App\\Mailer")
        assertEquals(SymbolResolutionResult.Resolved("App\\Mailer"), result)
    }

    @Test
    fun testResolveClassNotFound() {
        val result = service.resolveSymbol("\\App\\NoSuch")
        assertEquals(SymbolResolutionResult.NotFound, result)
    }

    @Test
    fun testResolveShortName() {
        val cls: PhpClass = Mockito.mock(PhpClass::class.java)
        Mockito.`when`(cls.fqn).thenReturn("App\\Mailer")
        Mockito.`when`(mockIndex.getClassesByName("Mailer")).thenReturn(listOf(cls))

        val result = service.resolveSymbol("Mailer")

        assertEquals(SymbolResolutionResult.Resolved("App\\Mailer"), result)
    }

    @Test
    fun testResolveShortNameAmbiguous() {
        val cls1: PhpClass = Mockito.mock(PhpClass::class.java)
        Mockito.`when`(cls1.fqn).thenReturn("App\\Mailer")
        val cls2: PhpClass = Mockito.mock(PhpClass::class.java)
        Mockito.`when`(cls2.fqn).thenReturn("Util\\Mailer")
        Mockito.`when`(mockIndex.getClassesByName("Mailer")).thenReturn(listOf(cls1, cls2))

        val result = service.resolveSymbol("Mailer")

        assertTrue("expected Ambiguous, got $result", result is SymbolResolutionResult.Ambiguous)
        assertEquals(2, (result as SymbolResolutionResult.Ambiguous).fqcns.size)
    }

    @Test
    fun testResolveShortNameNoMatches() {
        Mockito.`when`(mockIndex.getClassesByName("NoSuchClass")).thenReturn(emptyList())

        val result = service.resolveSymbol("NoSuchClass")

        assertEquals(SymbolResolutionResult.NotFound, result)
    }

    @Test
    fun testResolveMethodWithFqcn() {
        mockClass("App\\Mailer")
        val result = service.resolveSymbol("\\App\\Mailer::send")
        assertEquals(SymbolResolutionResult.Resolved("App\\Mailer::send"), result)
    }

    @Test
    fun testResolveShortNameWithMethod() {
        val cls: PhpClass = Mockito.mock(PhpClass::class.java)
        Mockito.`when`(cls.fqn).thenReturn("App\\Mailer")
        Mockito.`when`(mockIndex.getClassesByName("Mailer")).thenReturn(listOf(cls))

        val result = service.resolveSymbol("Mailer::send")

        assertEquals(SymbolResolutionResult.Resolved("App\\Mailer::send"), result)
    }

    @Test
    fun testResolveMethodAmbiguousClass() {
        val cls1: PhpClass = Mockito.mock(PhpClass::class.java)
        Mockito.`when`(cls1.fqn).thenReturn("App\\Mailer")
        val cls2: PhpClass = Mockito.mock(PhpClass::class.java)
        Mockito.`when`(cls2.fqn).thenReturn("Util\\Mailer")
        Mockito.`when`(mockIndex.getClassesByName("Mailer")).thenReturn(listOf(cls1, cls2))

        val result = service.resolveSymbol("Mailer::send")

        assertTrue("expected Ambiguous, got $result", result is SymbolResolutionResult.Ambiguous)
        val fqcns = (result as SymbolResolutionResult.Ambiguous).fqcns
        assertTrue(fqcns.contains("App\\Mailer::send"))
        assertTrue(fqcns.contains("Util\\Mailer::send"))
    }

    @Test
    fun testResolveMethodClassNotFound() {
        val result = service.resolveSymbol("\\App\\NoSuch::method")
        assertEquals(SymbolResolutionResult.NotFound, result)
    }

    @Test
    fun testResolveInterfaceByFqcn() {
        val iface = Mockito.mock(PhpClass::class.java)
        Mockito.`when`(iface.fqn).thenReturn("App\\ServiceInterface")
        val clean = "App\\ServiceInterface"
        Mockito.`when`(mockIndex.getClassesByFQN(clean)).thenReturn(emptyList())
        Mockito.`when`(mockIndex.getInterfacesByFQN(clean)).thenReturn(listOf(iface))
        Mockito.`when`(mockIndex.getTraitsByFQN(clean)).thenReturn(emptyList())

        val result = service.resolveSymbol("\\App\\ServiceInterface")
        assertEquals(SymbolResolutionResult.Resolved("App\\ServiceInterface"), result)
    }

    @Test
    fun testResolveTraitByFqcn() {
        val trait = Mockito.mock(PhpClass::class.java)
        Mockito.`when`(trait.fqn).thenReturn("App\\Loggable")
        val clean = "App\\Loggable"
        Mockito.`when`(mockIndex.getClassesByFQN(clean)).thenReturn(emptyList())
        Mockito.`when`(mockIndex.getInterfacesByFQN(clean)).thenReturn(emptyList())
        Mockito.`when`(mockIndex.getTraitsByFQN(clean)).thenReturn(listOf(trait))

        val result = service.resolveSymbol("\\App\\Loggable")
        assertEquals(SymbolResolutionResult.Resolved("App\\Loggable"), result)
    }

    @Test
    fun testResolveMethodWithInterfaceFqcn() {
        val iface = Mockito.mock(PhpClass::class.java)
        Mockito.`when`(iface.fqn).thenReturn("App\\ServiceInterface")
        Mockito.`when`(mockIndex.getClassesByFQN("App\\ServiceInterface")).thenReturn(emptyList())
        Mockito.`when`(mockIndex.getInterfacesByFQN("App\\ServiceInterface")).thenReturn(listOf(iface))
        Mockito.`when`(mockIndex.getTraitsByFQN("App\\ServiceInterface")).thenReturn(emptyList())

        val result = service.resolveSymbol("\\App\\ServiceInterface::execute")
        assertEquals(SymbolResolutionResult.Resolved("App\\ServiceInterface::execute"), result)
    }

    // --- classExists (indirect) tests ---

    @Test
    fun testResolveFqcnChecksInterfaces() {
        val iface = Mockito.mock(PhpClass::class.java)
        Mockito.`when`(iface.fqn).thenReturn("App\\Repository")
        Mockito.`when`(mockIndex.getClassesByFQN("App\\Repository")).thenReturn(emptyList())
        Mockito.`when`(mockIndex.getInterfacesByFQN("App\\Repository")).thenReturn(listOf(iface))
        Mockito.`when`(mockIndex.getTraitsByFQN("App\\Repository")).thenReturn(emptyList())

        val result = service.resolveSymbol("\\App\\Repository")
        assertEquals(SymbolResolutionResult.Resolved("App\\Repository"), result)
    }

    @Test
    fun testResolveFqcnChecksTraits() {
        val trait = Mockito.mock(PhpClass::class.java)
        Mockito.`when`(trait.fqn).thenReturn("App\\Loggable")
        Mockito.`when`(mockIndex.getClassesByFQN("App\\Loggable")).thenReturn(emptyList())
        Mockito.`when`(mockIndex.getInterfacesByFQN("App\\Loggable")).thenReturn(emptyList())
        Mockito.`when`(mockIndex.getTraitsByFQN("App\\Loggable")).thenReturn(listOf(trait))

        val result = service.resolveSymbol("\\App\\Loggable")
        assertEquals(SymbolResolutionResult.Resolved("App\\Loggable"), result)
    }

    @Test
    fun testResolveFqcnNeitherClassNorInterfaceNorTrait() {
        Mockito.`when`(mockIndex.getClassesByFQN("App\\NoSuch")).thenReturn(emptyList())
        Mockito.`when`(mockIndex.getInterfacesByFQN("App\\NoSuch")).thenReturn(emptyList())
        Mockito.`when`(mockIndex.getTraitsByFQN("App\\NoSuch")).thenReturn(emptyList())

        val result = service.resolveSymbol("\\App\\NoSuch")
        assertEquals(SymbolResolutionResult.NotFound, result)
    }

    // --- findUsages ---

    @Test
    fun testFindUsagesClassNotFound() {
        Mockito.`when`(mockIndex.getClassesByFQN("App\\NoSuch")).thenReturn(emptyList())
        Mockito.`when`(mockIndex.getInterfacesByFQN("App\\NoSuch")).thenReturn(emptyList())
        Mockito.`when`(mockIndex.getTraitsByFQN("App\\NoSuch")).thenReturn(emptyList())

        val usages = service.findUsages("\\App\\NoSuch")
        assertTrue("expected empty list", usages.isEmpty())
    }

    @Test
    fun testFindUsagesMemberClassNotFound() {
        Mockito.`when`(mockIndex.getClassesByFQN("App\\NoSuch")).thenReturn(emptyList())

        val usages = service.findUsages("\\App\\NoSuch::method")
        assertTrue("expected empty list", usages.isEmpty())
    }

    // --- findDefinition ---

    @Test
    fun testFindDefinitionClassNotFound() {
        Mockito.`when`(mockIndex.getClassesByFQN("App\\NoSuch")).thenReturn(emptyList())
        Mockito.`when`(mockIndex.getInterfacesByFQN("App\\NoSuch")).thenReturn(emptyList())
        Mockito.`when`(mockIndex.getTraitsByFQN("App\\NoSuch")).thenReturn(emptyList())

        val def = service.findDefinition("\\App\\NoSuch")
        assertNull(def)
    }

    @Test
    fun testFindDefinitionMemberClassNotFound() {
        Mockito.`when`(mockIndex.getClassesByFQN("App\\NoSuch")).thenReturn(emptyList())
        Mockito.`when`(mockIndex.getInterfacesByFQN("App\\NoSuch")).thenReturn(emptyList())

        val def = service.findDefinition("\\App\\NoSuch::method")
        assertNull(def)
    }
}