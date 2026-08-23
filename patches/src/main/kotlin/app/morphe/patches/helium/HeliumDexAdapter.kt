package app.morphe.patches.helium

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

internal fun parameterRegisterMap(
    params: List<String>,
    registerCount: Int,
    isStatic: Boolean,
): Map<Int, Int> {
    val parameterWords = params.sumOf { if (it == "J" || it == "D") 2 else 1 }
    val words = parameterWords + if (isStatic) 0 else 1
    var register = registerCount - words + if (isStatic) 0 else 1
    return params.mapIndexed { index, type ->
        val entry = index to register
        register += if (type == "J" || type == "D") 2 else 1
        entry
    }.toMap()
}

internal fun propagateParameterUses(
    params: List<String>,
    registerCount: Int,
    isStatic: Boolean,
    facts: List<StructuralInstruction>,
): List<StructuralInstruction.ParameterUse> {
    val origins = parameterRegisterMap(params, registerCount, isStatic)
        .filter { params[it.key] == "I" }
        .entries
        .associate { it.value to it.key }
        .toMutableMap()
    val scores = mutableMapOf<Pair<Int, Int>, Int>()

    fun record(index: Int, parameter: Int, weight: Int) {
        val key = index to parameter
        scores[key] = maxOf(scores[key] ?: 0, weight)
    }

    for (fact in facts.sortedBy { it.index }) {
        when (fact) {
            is StructuralInstruction.Move -> {
                origins[fact.source]?.let { origins[fact.dest] = it } ?: origins.remove(fact.dest)
            }

            is StructuralInstruction.Const -> origins.remove(fact.dest)
            is StructuralInstruction.FieldRead -> origins.remove(fact.dest)
            is StructuralInstruction.MoveResultObject -> origins.remove(fact.dest)
            is StructuralInstruction.FieldWrite -> origins[fact.source]?.let {
                record(fact.index, it, 8)
            }

            is StructuralInstruction.Invoke -> fact.registers.forEach { register ->
                origins[register]?.let { parameter ->
                    val frameworkCall = fact.owner.startsWith("Ljava/") ||
                        fact.owner.startsWith("Landroid/") ||
                        fact.owner.startsWith("Lkotlin/")
                    record(fact.index, parameter, if (frameworkCall) 1 else 3)
                }
            }

            is StructuralInstruction.Other -> fact.registers.forEach { register ->
                origins[register]?.let { parameter ->
                    val branch = fact.opcode.startsWith("IF") || fact.opcode.startsWith("CMP")
                    record(fact.index, parameter, if (branch) 4 else 1)
                }
            }

            else -> Unit
        }
    }
    return scores.map { (key, weight) ->
        StructuralInstruction.ParameterUse(key.first, key.second, weight)
    }
}

internal fun expandInvokeRegisters(instruction: Instruction): List<Int> = when (instruction) {
    is FiveRegisterInstruction -> listOf(
        instruction.registerC,
        instruction.registerD,
        instruction.registerE,
        instruction.registerF,
        instruction.registerG,
    ).take(instruction.registerCount)

    is RegisterRangeInstruction ->
        (instruction.startRegister until instruction.startRegister + instruction.registerCount).toList()

    else -> emptyList()
}

internal fun Method.toStructuralMethod(): StructuralMethod {
    val params = parameterTypes.map { it.toString() }
    val isStatic = AccessFlags.STATIC.isSet(accessFlags)
    val implementation = implementation ?: return StructuralMethod(
        "$definingClass->$name",
        name,
        returnType.toString(),
        params,
        0,
        isStatic,
        emptyList(),
    )
    val facts = mutableListOf<StructuralInstruction>()
    implementation.instructions.forEachIndexed { index, instruction ->
        when {
            instruction is ReferenceInstruction && instruction.reference is StringReference -> {
                facts += StructuralInstruction.StringLiteral(
                    index,
                    (instruction.reference as StringReference).string,
                )
            }

            instruction is ReferenceInstruction && instruction.reference is MethodReference -> {
                val reference = instruction.reference as MethodReference
                facts += StructuralInstruction.Invoke(
                    index,
                    reference.definingClass,
                    reference.name,
                    reference.returnType.toString(),
                    reference.parameterTypes.map { it.toString() },
                    expandInvokeRegisters(instruction),
                    instruction.opcode == Opcode.INVOKE_STATIC,
                    instruction.opcode == Opcode.INVOKE_SUPER,
                )
            }

            instruction.opcode == Opcode.MOVE_RESULT_OBJECT && instruction is OneRegisterInstruction -> {
                facts += StructuralInstruction.MoveResultObject(index, instruction.registerA)
            }

            instruction.opcode.name.startsWith("IGET") &&
                instruction is TwoRegisterInstruction &&
                instruction is ReferenceInstruction -> {
                val reference = instruction.reference as? FieldReference
                facts += StructuralInstruction.FieldRead(
                    index,
                    instruction.registerA,
                    instruction.registerB,
                    reference?.type ?: "",
                )
            }

            instruction.opcode.name.startsWith("IPUT") &&
                instruction is TwoRegisterInstruction &&
                instruction is ReferenceInstruction -> {
                val reference = instruction.reference as? FieldReference
                facts += StructuralInstruction.FieldWrite(
                    index,
                    instruction.registerA,
                    instruction.registerB,
                    reference?.type ?: "",
                )
            }

            instruction.opcode.name.startsWith("MOVE") && instruction is TwoRegisterInstruction -> {
                facts += StructuralInstruction.Move(index, instruction.registerA, instruction.registerB)
            }

            instruction is NarrowLiteralInstruction && instruction is OneRegisterInstruction -> {
                facts += StructuralInstruction.Const(index, instruction.registerA, instruction.narrowLiteral)
            }
        }
    }
    val descriptor = "$definingClass->$name(${params.joinToString("")})$returnType"
    return StructuralMethod(
        descriptor,
        name,
        returnType.toString(),
        params,
        implementation.registerCount,
        isStatic,
        facts + propagateParameterUses(params, implementation.registerCount, isStatic, facts),
    )
}
