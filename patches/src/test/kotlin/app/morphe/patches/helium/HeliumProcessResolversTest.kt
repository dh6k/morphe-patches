package app.morphe.patches.helium

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HeliumProcessResolversTest {
    @Test
    fun `createAndStart tolerates signature growth`() {
        val method = method(name = "createAndStart", params = listOf("J", "Z", "Z"))
        assertEquals(method, resolveCreateAndStart(listOf(method)))
    }

    @Test
    fun `createAndStart missing and ambiguous candidates fail`() {
        assertFailsWith<HeliumResolutionException> { resolveCreateAndStart(emptyList()) }
        assertFailsWith<HeliumResolutionException> {
            resolveCreateAndStart(listOf(method(name = "createAndStart"), method(name = "createAndStart")))
        }
    }

    @Test
    fun `renamed binding owner and method resolve from semantics`() {
        val resolution = resolveBindingTarget(bindingMethod("Lnew_obfuscation;", "renamed", 7))
        assertEquals(7, resolution.register)
        assertTrue(resolution.strategy == ResolutionStrategy.SEMANTIC_RELAXED)
    }

    @Test
    fun `sparse dex indices do not break TraceEvent scope`() {
        val instructions = listOf(
            StructuralInstruction.StringLiteral(100, "ChildProcessLauncher.start"),
            StructuralInstruction.Invoke(110, "Lorg/chromium/base/TraceEvent;", "begin", "V", emptyList(), emptyList(), isStatic = true),
            StructuralInstruction.Invoke(140, "Lrandom;", "launch", "Lconnection;", listOf("I"), listOf(2, 9)),
            StructuralInstruction.MoveResultObject(141, 4),
            StructuralInstruction.Invoke(180, "Lorg/chromium/base/TraceEvent;", "end", "V", emptyList(), emptyList(), isStatic = true),
        )
        assertEquals(9, resolveBindingTarget(method(instructions = instructions)).register)
    }

    @Test
    fun `binding register may move without changing target`() {
        assertEquals(3, resolveBindingTarget(bindingMethod("Lx;", "a", 3)).register)
        assertEquals(12, resolveBindingTarget(bindingMethod("Ly;", "b", 12)).register)
    }

    @Test
    fun `unrelated framework int calls are excluded`() {
        val instructions = listOf(
            StructuralInstruction.StringLiteral(0, "ChildProcessLauncher.start"),
            StructuralInstruction.Invoke(1, "Ljava/lang/Integer;", "valueOf", "Ljava/lang/Integer;", listOf("I"), listOf(4), isStatic = true),
            StructuralInstruction.MoveResultObject(2, 5),
            StructuralInstruction.Invoke(3, "Lrandom;", "launch", "Lconnection;", listOf("I"), listOf(2, 8)),
            StructuralInstruction.MoveResultObject(4, 6),
            StructuralInstruction.Invoke(9, "Lorg/chromium/base/TraceEvent;", "end", "V", emptyList(), emptyList(), isStatic = true),
        )
        assertEquals(8, resolveBindingTarget(method(instructions = instructions)).register)
    }

    @Test
    fun `ambiguous and missing binding candidates fail`() {
        val ambiguous = bindingMethod("Lx;", "a", 3).instructions.toMutableList().apply {
            add(3, StructuralInstruction.Invoke(3, "Ly;", "b", "Lconnection;", listOf("I"), listOf(2, 6)))
            add(4, StructuralInstruction.MoveResultObject(4, 7))
        }
        assertFailsWith<HeliumResolutionException> {
            resolveBindingTarget(method(instructions = ambiguous))
        }
        assertFailsWith<HeliumResolutionException> {
            resolveBindingTarget(method(instructions = listOf(StructuralInstruction.Other(0, "NOP"))))
        }
    }

    @Test
    fun `current priority shape selects final int and p12`() {
        val params = listOf("I", "Z", "Z", "Z", "Z", "J", "Z", "Z", "Z", "Z", "I")
        val uses = List(20) { StructuralInstruction.ParameterUse(it, 0, 3) } +
            StructuralInstruction.ParameterUse(30, 10, 8)
        val resolution = resolvePriorityTarget(listOf(method(name = "setPriority", returnType = "I", params = params, instructions = uses)))
        assertEquals(10, resolution.parameterIndex)
        assertEquals(12, resolution.pRegister)
        assertEquals(ResolutionStrategy.SEMANTIC_EXACT, resolution.strategy)
    }

    @Test
    fun `added boolean retains current priority fast path`() {
        val params = listOf("I", "Z", "Z", "Z", "Z", "Z", "J", "Z", "Z", "Z", "Z", "I")
        assertEquals(
            params.lastIndex,
            resolvePriorityTarget(listOf(method(name = "setPriority", returnType = "I", params = params))).parameterIndex,
        )
    }

    @Test
    fun `changed priority shape uses strongest data flow role`() {
        val uses = listOf(
            StructuralInstruction.ParameterUse(1, 0, 3),
            StructuralInstruction.ParameterUse(2, 1, 8),
            StructuralInstruction.ParameterUse(3, 2, 1),
        )
        val resolution = resolvePriorityTarget(
            listOf(method(name = "setPriority", returnType = "I", params = listOf("I", "I", "I"), instructions = uses)),
        )
        assertEquals(1, resolution.parameterIndex)
        assertEquals(ResolutionStrategy.DATA_FLOW, resolution.strategy)
    }

    @Test
    fun `equal priority roles fail closed`() {
        val uses = listOf(
            StructuralInstruction.ParameterUse(1, 0, 8),
            StructuralInstruction.ParameterUse(2, 1, 8),
        )
        assertFailsWith<HeliumResolutionException> {
            resolvePriorityTarget(
                listOf(method(name = "setPriority", returnType = "I", params = listOf("I", "I", "I"), instructions = uses)),
            )
        }
    }

    @Test
    fun `activity hook requires unique super onStart`() {
        val invokeSuper = StructuralInstruction.Invoke(
            4,
            "Lbase;",
            "onStart",
            "V",
            emptyList(),
            listOf(0),
            isSuper = true,
        )
        val activity = method(
            "Lfoo/ChromeTabbedActivity;",
            "onStart",
            returnType = "V",
            instructions = listOf(invokeSuper),
        )
        assertEquals(4, resolveActivityHook(listOf(activity)).superIndex)
        assertFailsWith<HeliumResolutionException> { resolveActivityHook(emptyList()) }
    }

    private fun bindingMethod(owner: String, name: String, register: Int): StructuralMethod {
        val instructions = listOf(
            StructuralInstruction.StringLiteral(0, "ChildProcessLauncher.start"),
            StructuralInstruction.Invoke(5, owner, name, "Lconnection;", listOf("I"), listOf(2, register)),
            StructuralInstruction.MoveResultObject(6, 4),
            StructuralInstruction.Invoke(10, "Lorg/chromium/base/TraceEvent;", "end", "V", emptyList(), emptyList(), isStatic = true),
        )
        return method(instructions = instructions)
    }

    private fun method(
        descriptor: String = "Ltest;",
        name: String = "createAndStart",
        returnType: String = "Lresult;",
        params: List<String> = emptyList(),
        instructions: List<StructuralInstruction> = listOf(StructuralInstruction.Other(0, "NOP")),
    ) = StructuralMethod(descriptor, name, returnType, params, 32, false, instructions)
}
