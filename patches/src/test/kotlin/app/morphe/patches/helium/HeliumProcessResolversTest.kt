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
        // Provide small-enum evidence for hardened resolver
        val prefix = listOf(StructuralInstruction.Const(2, 20, 4), StructuralInstruction.Move(3, 3, 20))
        val instructions = launchInstructions(prefix = prefix, invoke = first) + listOf(
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
        assertTrue(resolution.strategy == ResolutionStrategy.DATA_FLOW || resolution.strategy == ResolutionStrategy.SEMANTIC_RELAXED)
    }

    @Test
    fun `sparse dex indices do not break TraceEvent scope`() {
        val instructions = listOf(
            StructuralInstruction.StringLiteral(100, "ChildProcessLauncher.start"),
            StructuralInstruction.Invoke(110, "Lorg/chromium/base/TraceEvent;", "begin", "V", listOf("Ljava/lang/String;", "Ljava/lang/String;"), listOf(0, 1), isStatic = true),
            StructuralInstruction.Const(120, 20, 5),
            StructuralInstruction.Move(121, 9, 20),
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
            StructuralInstruction.Const(2, 20, 2),
            StructuralInstruction.Move(3, 8, 20),
            StructuralInstruction.Invoke(5, "Lrandom;", "launch", "Lconnection;", listOf("I"), listOf(2, 8)),
            StructuralInstruction.MoveResultObject(6, 6),
            StructuralInstruction.Invoke(9, "Lorg/chromium/base/TraceEvent;", "end", "V", emptyList(), emptyList(), isStatic = true),
        )
        assertEquals(8, resolveBindingTarget(method(instructions = instructions)).register)
    }

    @Test
    fun `ambiguous and missing binding candidates fail`() {
        val ambiguous = bindingMethod("Lx;", "a", 3).instructions.toMutableList().apply {
            add(2, StructuralInstruction.Const(2, 21, 4))
            add(3, StructuralInstruction.Move(3, 6, 21))
            add(4, StructuralInstruction.Invoke(4, "Ly;", "b", "Lconnection;", listOf("I"), listOf(2, 6)))
            add(5, StructuralInstruction.MoveResultObject(5, 7))
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


    @Test
    fun `single generic unrelated invoke fails closed`() {
        // One generic object-returning invoke with one int arg and no binding-specific evidence
        val instructions = listOf(
            StructuralInstruction.StringLiteral(0, "ChildProcessLauncher.start"),
            StructuralInstruction.Invoke(1, "Lorg/chromium/base/TraceEvent;", "begin", "V", listOf("Ljava/lang/String;", "Ljava/lang/String;"), listOf(0, 1), isStatic = true),
            StructuralInstruction.Invoke(3, "Lgeneric;", "create", "Lgeneric/Some;", listOf("I"), listOf(1, 5)),
            StructuralInstruction.MoveResultObject(4, 6),
            StructuralInstruction.Invoke(6, "Lorg/chromium/base/TraceEvent;", "end", "V", listOf("Ljava/lang/String;"), listOf(0), isStatic = true),
        )
        assertFailsWith<HeliumResolutionException> {
            resolveBindingTarget(method(instructions = instructions))
        }
    }

    @Test
    fun `realapk shape small enum constant resolves`() {
        // Mirrors real 152: const/4 v3,3 -> move v7,v3 -> invoke Li92->a(La82;Ld92;I)Lx82
        val instructions = listOf(
            StructuralInstruction.StringLiteral(0, "ChildProcessLauncher.start"),
            StructuralInstruction.Invoke(1, "Lorg/chromium/base/TraceEvent;", "begin", "V", listOf("Ljava/lang/String;", "Ljava/lang/String;"), listOf(0, 1), isStatic = true),
            StructuralInstruction.Const(2, 3, 3),
            StructuralInstruction.Move(3, 7, 3),
            StructuralInstruction.Invoke(5, "Li92;", "a", "Lx82;", listOf("La82;", "Ld92;", "I"), listOf(2, 3, 0, 7)),
            StructuralInstruction.MoveResultObject(6, 4),
            StructuralInstruction.Invoke(7, "Lorg/chromium/base/TraceEvent;", "end", "V", listOf("Ljava/lang/String;"), listOf(0), isStatic = true),
        )
        val res = resolveBindingTarget(method(instructions = instructions))
        assertEquals(7, res.register)
        assertTrue(res.diagnostics.contains("small-enum"))
    }

    @Test
    fun `field origin binding state resolves`() {
        val instructions = launchInstructions(
            prefix = listOf(
                StructuralInstruction.FieldRead(2, 8, null, "I"),
            ),
            invoke = StructuralInstruction.Invoke(7, "Lx;", "launch", "Ly;", listOf("I"), listOf(1, 8)),
        )
        val res = resolveBindingTarget(method(instructions = instructions))
        assertEquals(8, res.register)
        assertTrue(res.diagnostics.contains("field"))
    }

    @Test
    fun `move propagated field origin resolves`() {
        val instructions = launchInstructions(
            prefix = listOf(
                StructuralInstruction.FieldRead(2, 8, null, "I"),
                StructuralInstruction.Move(3, 6, 8),
            ),
            invoke = StructuralInstruction.Invoke(7, "Lx;", "launch", "Ly;", listOf("I"), listOf(1, 6)),
        )
        val res = resolveBindingTarget(method(instructions = instructions))
        assertEquals(6, res.register)
    }

    @Test
    fun `pid fd like invoke is rejected`() {
        val instructions = listOf(
            StructuralInstruction.StringLiteral(0, "ChildProcessLauncher.start"),
            StructuralInstruction.Invoke(1, "Lorg/chromium/base/TraceEvent;", "begin", "V", listOf("Ljava/lang/String;", "Ljava/lang/String;"), listOf(0, 1), isStatic = true),
            StructuralInstruction.Invoke(3, "Lchromium/childprocess/FdProvider;", "getPid", "Lx;", listOf("I"), listOf(1, 5)),
            StructuralInstruction.MoveResultObject(4, 6),
            StructuralInstruction.Invoke(6, "Lorg/chromium/base/TraceEvent;", "end", "V", listOf("Ljava/lang/String;"), listOf(0), isStatic = true),
        )
        assertFailsWith<HeliumResolutionException> {
            resolveBindingTarget(method(instructions = instructions))
        }
    }

    @Test
    fun `large constant is rejected`() {
        val instructions = launchInstructions(
            prefix = listOf(StructuralInstruction.Const(2, 6, 999)),
            invoke = StructuralInstruction.Invoke(7, "Lx;", "launch", "Ly;", listOf("I"), listOf(1, 6)),
        )
        assertFailsWith<HeliumResolutionException> {
            resolveBindingTarget(method(instructions = instructions))
        }
    }

    @Test
    fun `missing anchor fails closed`() {
        assertFailsWith<HeliumResolutionException> {
            resolveBindingTarget(method(instructions = listOf(StructuralInstruction.Other(0, "NOP"))))
        }
    }

    @Test
    fun `bounded fallback requires credible evidence`() {
        val instructions = listOf(
            StructuralInstruction.StringLiteral(0, "ChildProcessLauncher.start"),
            StructuralInstruction.Invoke(3, "Lgeneric;", "create", "Lx;", listOf("I"), listOf(1, 5)),
            StructuralInstruction.MoveResultObject(4, 6),
        )
        assertFailsWith<HeliumResolutionException> {
            resolveBindingTarget(method(instructions = instructions))
        }
    }

    @Test
    fun `malformed register mapping fails closed`() {
        val invoke = StructuralInstruction.Invoke(7, "Lx;", "a", "Ly;", listOf("I", "I"), listOf(1))
        val instructions = launchInstructions(prefix = emptyList(), invoke = invoke)
        assertFailsWith<HeliumResolutionException> {
            resolveBindingTarget(method(instructions = instructions))
        }
    }

    @Test
    fun `priority verified shape requires setPriority name`() {
        // Wrong name with verified-like params but no data-flow must not succeed via shape alone
        val params = listOf("I", "Z", "Z", "Z", "Z", "J", "Z", "Z", "Z", "Z", "I")
        assertFailsWith<HeliumResolutionException> {
            resolvePriorityTarget(listOf(method(name = "other", returnType = "I", params = params, instructions = emptyList())))
        }
    }

    @Test
    fun `priority unrelated two int method without dataflow fails`() {
        val method2 = method(name = "other", returnType = "I", params = listOf("I", "I"), instructions = emptyList())
        assertFailsWith<HeliumResolutionException> {
            resolvePriorityTarget(listOf(method2))
        }
    }

    @Test
    fun `priority wide param offset accounts for J D`() {
        val params = listOf("I", "J", "I")
        val m = method(params = params, isStatic = true, name = "other", returnType = "I", instructions = listOf(StructuralInstruction.ParameterUse(1, 2, 5), StructuralInstruction.ParameterUse(2, 2, 1)))
        // structural fallback viability needs >=2 Z etc, so test offset directly
        assertEquals(3, m.parameterWordOffset(2))
        assertEquals(0, m.parameterWordOffset(0))
    }

    @Test
    fun `priority ambiguous peaks fail closed`() {
        val uses = listOf(StructuralInstruction.ParameterUse(1, 0, 5), StructuralInstruction.ParameterUse(2, 1, 5))
        assertFailsWith<HeliumResolutionException> {
            resolvePriorityTarget(listOf(method(name = "setPriority", returnType = "I", params = listOf("I", "I"), instructions = uses)))
        }
    }

    @Test
    fun `priority no candidate fails`() {
        assertFailsWith<HeliumResolutionException> {
            resolvePriorityTarget(emptyList())
        }
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
        // Include small-enum constant evidence so hardened resolver accepts synthetic fixtures
        val evidenceReg = 20
        val instructions = listOf(
            StructuralInstruction.StringLiteral(0, "ChildProcessLauncher.start"),
            StructuralInstruction.Const(2, evidenceReg, 3),
            StructuralInstruction.Move(3, register, evidenceReg),
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
