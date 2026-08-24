package app.morphe.patches.helium

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction12x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction22c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction22t
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction23x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction31i
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction3rc
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeliumDexAdapterTest {
    @Test
    fun `instructionRegisters reads one two and three register interfaces`() {
        assertEquals(listOf(1, 2), instructionRegisters(ImmutableInstruction12x(Opcode.MOVE, 1, 2)))
        assertEquals(listOf(3, 4), instructionRegisters(ImmutableInstruction22t(Opcode.IF_EQ, 3, 4, 1)))
        assertEquals(listOf(5, 6, 7), instructionRegisters(ImmutableInstruction23x(Opcode.ADD_INT, 5, 6, 7)))
        assertEquals(listOf(8), instructionRegisters(ImmutableInstruction31i(Opcode.CONST, 8, 4)))
    }

    @Test
    fun `instructionRegisters reads five and range invoke interfaces`() {
        val method = ImmutableMethodReference("Lhelper;", "start", listOf("I", "I", "I", "I", "I"), "V")
        assertEquals(
            listOf(1, 2, 3, 4, 5),
            instructionRegisters(ImmutableInstruction35c(Opcode.INVOKE_STATIC, 5, 1, 2, 3, 4, 5, method)),
        )
        assertEquals(
            listOf(7, 8, 9),
            instructionRegisters(ImmutableInstruction3rc(Opcode.INVOKE_STATIC_RANGE, 7, 3, method)),
        )
    }

    @Test
    fun `adapter emits move branch arithmetic const and field facts`() {
        val field = ImmutableFieldReference("Lstate;", "binding", "I")
        val dexMethod = immutableMethod(
            listOf(
                ImmutableInstruction31i(Opcode.CONST, 1, 4),
                ImmutableInstruction12x(Opcode.MOVE, 2, 1),
                ImmutableInstruction22c(Opcode.IGET, 3, 0, field),
                ImmutableInstruction22t(Opcode.IF_EQ, 2, 3, 1),
                ImmutableInstruction23x(Opcode.ADD_INT, 4, 2, 3),
            ),
        )
        assertEquals(5, dexMethod.implementation!!.instructions.count())
        val method = dexMethod.toStructuralMethod()

        assertTrue(method.instructions.any { it is StructuralInstruction.Const && it.index == 0 }, method.instructions.toString())
        assertTrue(method.instructions.any { it is StructuralInstruction.Move && it.index == 1 }, method.instructions.toString())
        assertTrue(method.instructions.any { it is StructuralInstruction.FieldRead && it.index == 2 }, method.instructions.toString())
        assertTrue(method.instructions.any { it is StructuralInstruction.Other && it.opcode == "IF_EQ" })
        assertTrue(method.instructions.any { it is StructuralInstruction.Other && it.opcode == "ADD_INT" })
    }

    private fun immutableMethod(instructions: List<com.android.tools.smali.dexlib2.iface.instruction.Instruction>) =
        ImmutableMethod(
            "Ltest;",
            "flow",
            emptyList<ImmutableMethodParameter>(),
            "V",
            AccessFlags.PUBLIC.value,
            emptySet(),
            emptySet(),
            ImmutableMethodImplementation(10, instructions, emptyList(), emptyList()),
        )
}
