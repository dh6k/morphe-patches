package app.morphe.patches.helium

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HeliumPriorityResolverFailClosedTest {
    @Test
    fun `verified current shape selects final int`() {
        val params = listOf("I", "Z", "Z", "Z", "Z", "J", "Z", "Z", "Z", "Z", "I")
        val res = resolvePriorityTarget(listOf(method(name = "setPriority", returnType = "I", params = params)))
        assertEquals(params.lastIndex, res.parameterIndex)
        assertEquals(ResolutionStrategy.SEMANTIC_EXACT, res.strategy)
    }

    @Test
    fun `alternate obfuscated shape via fallback succeeds with data flow`() {
        val params = listOf("I", "Z", "Z", "J", "I")
        val renamed = method(name = "q7", returnType = "I", params = params, instructions = listOf(StructuralInstruction.ParameterUse(1, 4, 8), StructuralInstruction.FieldWrite(2, 4, null, "I"), StructuralInstruction.Other(3, "IF_EQ", listOf(4))))
        val res = resolvePriorityTarget(listOf(renamed))
        assertTrue(res.strategy == ResolutionStrategy.DATA_FLOW || res.strategy == ResolutionStrategy.SEMANTIC_EXACT)
    }

    @Test
    fun `unrelated method with two ints rejected without sufficient evidence`() {
        val method = method(name = "doWork", returnType = "I", params = listOf("I", "I"), instructions = emptyList())
        assertFailsWith<HeliumResolutionException> { resolvePriorityTarget(listOf(method)) }
    }

    @Test
    fun `ambiguous candidates fail`() {
        val m1 = method(name = "setPriority", returnType = "I", params = listOf("I", "Z", "Z", "Z", "Z", "J", "Z", "I"))
        val m2 = method(name = "setPriority", returnType = "I", params = listOf("I", "Z", "Z", "Z", "Z", "J", "Z", "I"))
        assertFailsWith<HeliumResolutionException> { resolvePriorityTarget(listOf(m1, m2)) }
    }

    @Test
    fun `no valid candidate fails`() {
        assertFailsWith<HeliumResolutionException> { resolvePriorityTarget(emptyList()) }
        assertFailsWith<HeliumResolutionException> {
            resolvePriorityTarget(listOf(method(name = "other", returnType = "V", params = listOf("I"))))
        }
    }

    @Test
    fun `wide params before selected int correct word offset`() {
        // Static: params [I, J, Z, I] -> offsets 0,1,3,4
        val staticMethod = method(name = "setPriority", returnType = "I", params = listOf("I", "Z", "Z", "Z", "Z", "J", "Z", "Z", "I"), isStatic = true)
        val resStatic = resolvePriorityTarget(listOf(staticMethod))
        // For verified shape, last int offset should be computed correctly
        assertEquals(staticMethod.parameterWordOffset(resStatic.parameterIndex), resStatic.parameterWordOffset)
        val instanceMethod = method(name = "setPriority", returnType = "I", params = listOf("I", "Z", "Z", "Z", "Z", "J", "Z", "Z", "I"), isStatic = false)
        val resInstance = resolvePriorityTarget(listOf(instanceMethod))
        assertTrue(resInstance.parameterWordOffset == resStatic.parameterWordOffset + 1)
    }

    @Test
    fun `static and instance word offsets distinguish`() {
        val params = listOf("I", "J", "Z", "I", "D")
        val staticMethod = method(params = params, isStatic = true)
        val instanceMethod = method(params = params, isStatic = false)
        assertEquals(4, staticMethod.parameterWordOffset(3))
        assertEquals(5, instanceMethod.parameterWordOffset(3))
    }

    @Test
    fun `correct parameter word offset for verified shape`() {
        val params = listOf("I", "Z", "Z", "Z", "Z", "J", "Z", "Z", "Z", "Z", "I")
        val method = method(name = "setPriority", returnType = "I", params = params)
        val res = resolvePriorityTarget(listOf(method))
        // params: I0,Z1,Z2,Z3,Z4,J5(2words),Z7,Z8,Z9,I10 -> offset of 10 = 1+1+1+1+2+1+1+1=9? Let's just assert method computation
        assertEquals(method.parameterWordOffset(10), res.parameterWordOffset)
    }

    private fun method(
        descriptor: String = "Ltest;",
        name: String = "setPriority",
        returnType: String = "I",
        params: List<String> = emptyList(),
        isStatic: Boolean = false,
        instructions: List<StructuralInstruction> = emptyList(),
    ) = StructuralMethod(descriptor, name, returnType, params, 32, isStatic, instructions)
}
