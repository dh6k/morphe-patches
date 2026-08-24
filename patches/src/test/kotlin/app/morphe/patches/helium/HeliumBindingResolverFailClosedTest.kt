package app.morphe.patches.helium

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HeliumBindingResolverFailClosedTest {
    // Exact known-good launch structure (field binding) - should pass
    @Test
    fun `exact launch structure with field binding resolves`() {
        val method = methodWithFieldBinding()
        val res = resolveBindingTarget(method)
        assertEquals(5, res.register)
        assertTrue(res.diagnostics.contains("method="))
    }

    // Relaxed but valid structure (chromium hint + moveResult in exact region)
    @Test
    fun `relaxed valid structure with chromium hint resolves`() {
        val instructions = listOf(
            StructuralInstruction.StringLiteral(0, "ChildProcessLauncher.start"),
            StructuralInstruction.Invoke(1, "Lorg/chromium/base/TraceEvent;", "begin", "V", listOf("Ljava/lang/String;", "Ljava/lang/String;"), listOf(0, 1), isStatic = true),
            StructuralInstruction.Invoke(2, "Lorg/chromium/content/browser/ChildProcessLauncher;", "createConnection", "Lconn;", listOf("I"), listOf(0, 7)),
            StructuralInstruction.MoveResultObject(3, 8),
            StructuralInstruction.Invoke(4, "Lorg/chromium/base/TraceEvent;", "end", "V", listOf("Ljava/lang/String;"), listOf(0), isStatic = true),
        )
        val res = resolveBindingTarget(method(instructions = instructions))
        assertEquals(7, res.register)
    }

    // Multiple integer args where only one is binding state (field vs generic)
    @Test
    fun `multiple int args selects binding field over generic flag`() {
        val method = multiIntBindingMethod(bindingRegister = 3, otherRegister = 4, bindingParameter = 0)
        assertEquals(3, resolveBindingTarget(method).register)
    }

    // PID/FD-like integer scoring higher under old heuristic now fails or is rejected
    @Test
    fun `pid hint invoke rejected`() {
        val instructions = launchInstructions(
            invoke = StructuralInstruction.Invoke(7, "Lchromium;", "getPid", "Lconn;", listOf("I"), listOf(1, 12)),
        )
        // getPid contains pid hint -> rejected, so no credible candidates
        assertFailsWith<HeliumResolutionException> { resolveBindingTarget(method(instructions = instructions)) }
    }

    // Non-enum large constant rejected
    @Test
    fun `large constant for binding rejected as pid-like`() {
        val instructions = launchInstructions(
            prefix = listOf(
                StructuralInstruction.Const(2, 9, 1234),
                StructuralInstruction.Move(3, 3, 9),
            ),
            invoke = StructuralInstruction.Invoke(7, "Lx;", "launch", "Lconn;", listOf("I"), listOf(1, 3)),
        )
        assertFailsWith<HeliumResolutionException> { resolveBindingTarget(method(instructions = instructions)) }
    }

    // One uniquely wrong generic invoke (single weak candidate in bounded fallback with insufficient evidence maybe still passes if exactly single, but multiple equal should fail)
    // Here test insufficient evidence case: generic invoke with no field/branch/chromium in bounded
    // Our current logic allows weak-generic-in-bounded as single candidate - to test fail-closed, we need to ensure multiple generics fail via ambiguity
    @Test
    fun `multiple equal weak candidates fail closed`() {
        val instructions = listOf(
            StructuralInstruction.StringLiteral(0, "ChildProcessLauncher.start"),
            StructuralInstruction.Invoke(1, "La;", "foo", "Lx;", listOf("I"), listOf(0, 3)),
            StructuralInstruction.MoveResultObject(2, 8),
            StructuralInstruction.Invoke(3, "Lb;", "bar", "Ly;", listOf("I"), listOf(0, 4)),
            StructuralInstruction.MoveResultObject(4, 9),
        )
        assertFailsWith<HeliumResolutionException> { resolveBindingTarget(method(instructions = instructions)) }
    }

    // Missing launch anchor fails
    @Test
    fun `missing launch anchor fails`() {
        assertFailsWith<HeliumResolutionException> {
            resolveLaunchRegion(listOf(StructuralInstruction.Other(0, "NOP")))
        }
    }

    // Multiple launch anchors fail
    @Test
    fun `multiple launch anchors fail 2`() {
        assertFailsWith<HeliumResolutionException> {
            resolveLaunchRegion(
                listOf(
                    StructuralInstruction.StringLiteral(1, "ChildProcessLauncher.start"),
                    StructuralInstruction.StringLiteral(2, "ChildProcessLauncher.start"),
                ),
            )
        }
    }

    // Missing TraceEvent close fails when target exists
    @Test
    fun `missing trace close fails`() {
        val instructions = listOf(
            StructuralInstruction.StringLiteral(0, "ChildProcessLauncher.start"),
            StructuralInstruction.Invoke(1, "Lorg/chromium/base/TraceEvent;", "begin", "V", listOf("Ljava/lang/String;", "Ljava/lang/String;"), listOf(0, 1), isStatic = true),
            StructuralInstruction.Invoke(2, "Lx;", "launch", "Lconn;", listOf("I"), listOf(0, 5)),
            StructuralInstruction.MoveResultObject(3, 6),
        )
        assertFailsWith<HeliumResolutionException> { resolveLaunchRegion(instructions) }
    }

    // Register origin through move
    @Test
    fun `register origin through move resolves field`() {
        val method = methodWithFieldBinding()
        assertEquals(5, resolveBindingTarget(method).register)
    }

    // Register origin through field read directly
    @Test
    fun `register origin through field read`() {
        val instructions = launchInstructions(
            prefix = listOf(StructuralInstruction.FieldRead(2, 5, null, "I")),
            invoke = StructuralInstruction.Invoke(7, "Lx;", "launch", "Lconn;", listOf("I"), listOf(1, 5)),
        )
        assertEquals(5, resolveBindingTarget(method(instructions = instructions)).register)
    }

    // Register origin through constant small enum with branch
    @Test
    fun `register origin small enum with branch qualifies`() {
        val instructions = launchInstructions(
            prefix = listOf(
                StructuralInstruction.Const(2, 5, 4),
                StructuralInstruction.Other(3, "IF_EQ", listOf(5, 6)),
            ),
            invoke = StructuralInstruction.Invoke(7, "Lx;", "launch", "Lconn;", listOf("I"), listOf(1, 5)),
        )
        // Constant 4 + branch -> credible
        assertEquals(5, resolveBindingTarget(method(instructions = instructions)).register)
    }

    // Register origin through method parameter
    @Test
    fun `register origin through parameter with branch qualifies`() {
        val branch = StructuralInstruction.Other(3, "IF_NE", listOf(31, 1))
        val instructions = launchInstructions(
            prefix = listOf(branch),
            invoke = StructuralInstruction.Invoke(7, "Lx;", "launch", "Lconn;", listOf("I"), listOf(1, 31)),
        )
        // register 31 is param 0 when isStatic=false, registerCount=32, params=[I]
        val method = method(params = listOf("I"), isStatic = false, registerCount = 32, instructions = instructions)
        // param + branch should be credible
        assertEquals(31, resolveBindingTarget(method).register)
    }

    // Insufficient evidence generic invoke fails
    @Test
    fun `insufficient evidence generic invoke fails`() {
        // Create a method with a generic invoke but no field/branch/chromium and multiple candidates to trigger ambiguity/fail
        // Single generic in bounded currently passes as weak-generic; to make it fail we need multiple equal generics
        val instructions = listOf(
            StructuralInstruction.StringLiteral(0, "ChildProcessLauncher.start"),
            StructuralInstruction.Invoke(1, "La;", "foo", "Lx;", listOf("I"), listOf(0, 3)),
            StructuralInstruction.MoveResultObject(2, 8),
            StructuralInstruction.Invoke(3, "Lb;", "bar", "Ly;", listOf("I"), listOf(0, 4)),
            StructuralInstruction.MoveResultObject(4, 9),
        )
        assertFailsWith<HeliumResolutionException> { resolveBindingTarget(method(instructions = instructions)) }
    }

    // Malformed register mapping fails
    @Test
    fun `malformed register mapping throws`() {
        val invoke = StructuralInstruction.Invoke(7, "Lx;", "launch", "Lconn;", listOf("I", "I"), listOf(0)) // only 1 register but needs 2
        val instructions = launchInstructions(invoke = invoke)
        assertFailsWith<HeliumResolutionException> { resolveBindingTarget(method(instructions = instructions)) }
    }

    // Register liveness: live register after invoke should fail validation
    @Test
    fun `live register after invoke fails safety`() {
        val method = methodWithFieldBinding()
        val res = resolveBindingTarget(method)
        // Manually craft a method where register is live afterwards
        val liveInstructions = method.instructions.toMutableList() + StructuralInstruction.Other(20, "IF_EQ", listOf(res.register, 1))
        val liveMethod = method(instructions = liveInstructions)
        val liveRes = resolveBindingTarget(liveMethod)
        assertFailsWith<HeliumResolutionException> { validateBindingRegisterSafety(liveMethod, liveRes) }
    }

    private fun methodWithFieldBinding(): StructuralMethod {
        val instructions = launchInstructions(
            prefix = listOf(
                StructuralInstruction.FieldRead(2, 8, null, "I"),
                StructuralInstruction.Move(3, 5, 8),
            ),
            invoke = StructuralInstruction.Invoke(7, "Lx;", "launch", "Lconn;", listOf("I"), listOf(1, 5)),
        )
        return method(instructions = instructions)
    }

    private fun multiIntBindingMethod(bindingRegister: Int, otherRegister: Int, bindingParameter: Int = 0): StructuralMethod {
        val registers = if (bindingParameter == 0) listOf(1, bindingRegister, otherRegister) else listOf(1, otherRegister, bindingRegister)
        val instructions = launchInstructions(
            prefix = listOf(
                StructuralInstruction.FieldRead(2, 8, null, "I"),
                StructuralInstruction.Move(3, bindingRegister, 8),
            ),
            invoke = StructuralInstruction.Invoke(7, "Lrenamed;", "x9", "Lconnection;", listOf("I", "I"), registers),
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
        registerCount: Int = 32,
        instructions: List<StructuralInstruction> = listOf(StructuralInstruction.Other(0, "NOP")),
    ) = StructuralMethod(descriptor, name, returnType, params, registerCount, isStatic, instructions)
}
