package app.morphe.patches.helium

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HeliumProcessResolversTest {
    @Test
    fun `field read through move selects first binding int over flags`() {
        val method = multiIntBindingMethod(bindingRegister = 3, otherRegister = 4)
        assertEquals(3, resolveBindingTarget(method).register)
    }

    @Test
    fun `field read through move selects last binding int`() {
        val method = multiIntBindingMethod(bindingRegister = 4, otherRegister = 3, bindingParameter = 1)
        assertEquals(4, resolveBindingTarget(method).register)
    }

    @Test
    fun `equal multi int field evidence fails closed`() {
        val instructions = launchInstructions(
            prefix = listOf(
                StructuralInstruction.FieldRead(2, 8, null, "I"),
                StructuralInstruction.Move(3, 3, 8),
                StructuralInstruction.FieldRead(4, 9, null, "I"),
                StructuralInstruction.Move(5, 4, 9),
            ),
            invoke = StructuralInstruction.Invoke(7, "Lx;", "a", "Ly;", listOf("I", "I"), listOf(1, 3, 4)),
        )
        assertFailsWith<HeliumResolutionException> {
            resolveBindingTarget(method(instructions = instructions))
        }
    }

    @Test
    fun `nearest TraceEvent close excludes later scope`() {
        val first = StructuralInstruction.Invoke(4, "Lx;", "launch", "Ly;", listOf("I"), listOf(1, 3))
        val instructions = launchInstructions(invoke = first) + listOf(
            StructuralInstruction.Invoke(20, "Lorg/chromium/base/TraceEvent;", "begin", "V", listOf("Ljava/lang/String;", "Ljava/lang/String;"), listOf(0, 1), isStatic = true),
            StructuralInstruction.Invoke(21, "Llater;", "noise", "Lz;", listOf("I"), listOf(1, 9)),
            StructuralInstruction.MoveResultObject(22, 10),
            StructuralInstruction.Invoke(23, "Lorg/chromium/base/TraceEvent;", "end", "V", listOf("Ljava/lang/String;"), listOf(0), isStatic = true),
        )
        val region = resolveLaunchRegion(instructions)
        assertTrue(region.endIndex < 20)
        assertEquals(3, resolveBindingTarget(method(instructions = instructions)).register)
    }

    @Test
    fun `multiple launch anchors fail closed`() {
        assertFailsWith<HeliumResolutionException> {
            resolveLaunchRegion(
                listOf(
                    StructuralInstruction.StringLiteral(1, "ChildProcessLauncher.start"),
                    StructuralInstruction.StringLiteral(2, "ChildProcessLauncher.start"),
                ),
            )
        }
    }
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
            StructuralInstruction.Invoke(110, "Lorg/chromium/base/TraceEvent;", "begin", "V", listOf("Ljava/lang/String;", "Ljava/lang/String;"), listOf(0, 1), isStatic = true),
            StructuralInstruction.Invoke(140, "Lrandom;", "launch", "Lconnection;", listOf("I"), listOf(2, 9)),
            StructuralInstruction.MoveResultObject(141, 4),
            StructuralInstruction.Invoke(180, "Lorg/chromium/base/TraceEvent;", "end", "V", listOf("Ljava/lang/String;"), listOf(0), isStatic = true),
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
        assertEquals(12, resolution.parameterWordOffset)
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
    fun `parameter word offsets distinguish static instance and wide values`() {
        val params = listOf("I", "J", "Z", "I", "D")
        val staticMethod = method(params = params, isStatic = true)
        val instanceMethod = method(params = params, isStatic = false)
        assertEquals(4, staticMethod.parameterWordOffset(3))
        assertEquals(5, instanceMethod.parameterWordOffset(3))
        assertEquals(0, staticMethod.parameterWordOffset(0))
        assertEquals(1, instanceMethod.parameterWordOffset(0))
    }

    @Test
    fun `renamed create method resolves from launch semantics`() {
        val renamed = method(
            name = "x9",
            instructions = bindingMethod("Lx;", "a", 3).instructions,
        )
        assertEquals("x9", resolveCreateAndStart(listOf(renamed)).name)
    }

    @Test
    fun `renamed priority method resolves structurally`() {
        val params = listOf("I", "Z", "Z", "J", "I")
        val renamed = method(
            name = "q7",
            returnType = "I",
            params = params,
            instructions = listOf(StructuralInstruction.ParameterUse(1, 4, 8)),
        )
        val resolution = resolvePriorityTarget(listOf(renamed))
        assertEquals(renamed.descriptor, resolution.methodDescriptor)
        assertEquals(6, resolution.parameterWordOffset)
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
        assertFailsWith<HeliumResolutionException> {
            resolveActivityHook(emptyList<StructuralMethod>())
        }
    }

    @Test
    fun `renamed launcher walks hierarchy to onStart owner`() {
        val lifecycle = lifecycleMethod("Lbase;->onStart()V", "onStart", 6)
        val models = listOf(
            ActivityClassModel("Lrenamed/BrowserShell;", "Lbase;", emptyList(), isLauncher = true),
            ActivityClassModel("Lbase;", "Landroid/app/Activity;", listOf(lifecycle)),
        )
        val result = resolveActivityHook(models)
        assertEquals(lifecycle.descriptor, result.methodDescriptor)
        assertEquals(ResolutionStrategy.MANIFEST_FALLBACK, result.strategy)
    }

    @Test
    fun `activity hierarchy uses onResume only when onStart is absent`() {
        val lifecycle = lifecycleMethod("Lbase;->onResume()V", "onResume", 3)
        val models = listOf(
            ActivityClassModel("Lrenamed/BrowserShell;", "Lbase;", emptyList(), browserEvidence = true),
            ActivityClassModel("Lbase;", null, listOf(lifecycle)),
        )
        val result = resolveActivityHook(models)
        assertEquals(lifecycle.descriptor, result.methodDescriptor)
        assertTrue(result.diagnostics.contains("onResume"))
    }

    @Test
    fun `invalid exact activity falls through to viable browser candidate`() {
        val lifecycle = lifecycleMethod("Lrenamed/BrowserShell;->onStart()V", "onStart", 8)
        val models = listOf(
            ActivityClassModel(HELIUM_ACTIVITY_CLASS, null, emptyList()),
            ActivityClassModel("Lrenamed/BrowserShell;", null, listOf(lifecycle), browserEvidence = true),
        )
        val result = resolveActivityHook(models)
        assertEquals(lifecycle.descriptor, result.methodDescriptor)
        assertEquals(ResolutionStrategy.HIERARCHY_FALLBACK, result.strategy)
    }

    private fun lifecycleMethod(descriptor: String, name: String, index: Int) = StructuralMethod(
        descriptor,
        name,
        "V",
        emptyList(),
        2,
        false,
        listOf(
            StructuralInstruction.Invoke(
                index,
                "Lsuper;",
                name,
                "V",
                emptyList(),
                listOf(0),
                isSuper = true,
            ),
        ),
    )

    private fun bindingMethod(owner: String, name: String, register: Int): StructuralMethod {
        val instructions = listOf(
            StructuralInstruction.StringLiteral(0, "ChildProcessLauncher.start"),
            StructuralInstruction.Invoke(5, owner, name, "Lconnection;", listOf("I"), listOf(2, register)),
            StructuralInstruction.MoveResultObject(6, 4),
            StructuralInstruction.Invoke(10, "Lorg/chromium/base/TraceEvent;", "end", "V", emptyList(), emptyList(), isStatic = true),
        )
        return method(instructions = instructions)
    }

    private fun multiIntBindingMethod(
        bindingRegister: Int,
        otherRegister: Int,
        bindingParameter: Int = 0,
    ): StructuralMethod {
        val registers = if (bindingParameter == 0) {
            listOf(1, bindingRegister, otherRegister)
        } else {
            listOf(1, otherRegister, bindingRegister)
        }
        val instructions = launchInstructions(
            prefix = listOf(
                StructuralInstruction.FieldRead(2, 8, null, "I"),
                StructuralInstruction.Move(3, bindingRegister, 8),
            ),
            invoke = StructuralInstruction.Invoke(
                7,
                "Lrenamed;",
                "x9",
                "Lconnection;",
                listOf("I", "I"),
                registers,
            ),
        )
        return method(instructions = instructions)
    }

    private fun launchInstructions(
        prefix: List<StructuralInstruction> = emptyList(),
        invoke: StructuralInstruction.Invoke,
    ): List<StructuralInstruction> = listOf(
        StructuralInstruction.StringLiteral(0, "ChildProcessLauncher.start"),
        StructuralInstruction.Invoke(
            1,
            "Lorg/chromium/base/TraceEvent;",
            "begin",
            "V",
            listOf("Ljava/lang/String;", "Ljava/lang/String;"),
            listOf(0, 1),
            isStatic = true,
        ),
    ) + prefix + listOf(
        invoke,
        StructuralInstruction.MoveResultObject(invoke.index + 1, 12),
        StructuralInstruction.Invoke(
            invoke.index + 3,
            "Lorg/chromium/base/TraceEvent;",
            "end",
            "V",
            listOf("Ljava/lang/String;"),
            listOf(0),
            isStatic = true,
        ),
    )

    private fun method(
        descriptor: String = "Ltest;",
        name: String = "createAndStart",
        returnType: String = "Lresult;",
        params: List<String> = emptyList(),
        isStatic: Boolean = false,
        instructions: List<StructuralInstruction> = listOf(StructuralInstruction.Other(0, "NOP")),
    ) = StructuralMethod(descriptor, name, returnType, params, 32, isStatic, instructions)
}
