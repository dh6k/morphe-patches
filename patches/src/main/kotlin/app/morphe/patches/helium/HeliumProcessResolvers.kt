package app.morphe.patches.helium

enum class ResolutionStrategy {
    SEMANTIC_EXACT,
    SEMANTIC_RELAXED,
    DATA_FLOW,
    LEGACY_STRUCTURAL,
}

sealed class StructuralInstruction {
    abstract val index: Int

    data class Invoke(
        override val index: Int,
        val owner: String,
        val name: String,
        val returnType: String,
        val params: List<String>,
        val registers: List<Int>,
        val isStatic: Boolean = false,
        val isSuper: Boolean = false,
    ) : StructuralInstruction()

    data class MoveResultObject(override val index: Int, val dest: Int) : StructuralInstruction()

    data class FieldRead(
        override val index: Int,
        val dest: Int,
        val objectRegister: Int?,
        val type: String,
    ) : StructuralInstruction()

    data class FieldWrite(
        override val index: Int,
        val source: Int,
        val objectRegister: Int?,
        val type: String,
    ) : StructuralInstruction()

    data class Move(override val index: Int, val dest: Int, val source: Int) : StructuralInstruction()
    data class Const(override val index: Int, val dest: Int, val value: Int) : StructuralInstruction()

    data class ParameterUse(
        override val index: Int,
        val parameterIndex: Int,
        val roleWeight: Int,
    ) : StructuralInstruction()

    data class StringLiteral(override val index: Int, val value: String) : StructuralInstruction()

    data class Other(
        override val index: Int,
        val opcode: String,
        val registers: List<Int> = emptyList(),
    ) : StructuralInstruction()
}

data class StructuralMethod(
    val descriptor: String,
    val name: String,
    val returnType: String,
    val params: List<String>,
    val registerCount: Int,
    val isStatic: Boolean,
    val instructions: List<StructuralInstruction>,
    val strings: Set<String> = emptySet(),
)

data class BindingResolution(
    val index: Int,
    val register: Int,
    val strategy: ResolutionStrategy,
    val diagnostics: String,
)

data class PriorityResolution(
    val parameterIndex: Int,
    val pRegister: Int,
    val strategy: ResolutionStrategy,
    val diagnostics: String,
)

data class ActivityResolution(
    val methodDescriptor: String,
    val superIndex: Int,
    val strategy: ResolutionStrategy,
    val diagnostics: String,
)

class HeliumResolutionException(message: String) : IllegalStateException(message)

private fun width(type: String) = if (type == "J" || type == "D") 2 else 1

private fun StructuralInstruction.Invoke.paramRegister(parameterIndex: Int): Int {
    var registerIndex = if (isStatic) 0 else 1
    for (index in 0 until parameterIndex) registerIndex += width(params[index])
    if (registerIndex !in registers.indices) {
        throw HeliumResolutionException("binding: malformed register mapping at invoke $index")
    }
    return registers[registerIndex]
}

fun resolveCreateAndStart(methods: List<StructuralMethod>): StructuralMethod {
    val candidates = methods.filter { it.name == "createAndStart" && it.instructions.isNotEmpty() }
    if (candidates.size != 1) {
        throw HeliumResolutionException(
            "createAndStart: expected one implementation, found ${candidates.size}",
        )
    }
    return candidates.single()
}

fun resolveBindingTarget(method: StructuralMethod): BindingResolution {
    val start = method.instructions
        .firstOrNull { it is StructuralInstruction.StringLiteral && it.value == "ChildProcessLauncher.start" }
        ?.index
        ?: -1
    val end = method.instructions
        .filter {
            it.index > start &&
                ((it is StructuralInstruction.StringLiteral && it.value.contains("TraceEvent")) ||
                    (it is StructuralInstruction.Invoke && it.owner.contains("TraceEvent")))
        }
        .maxOfOrNull { it.index }
        ?: -1
    if (start < 0 || end <= start) {
        throw HeliumResolutionException("semantic binding candidates: 0 (missing anchors)")
    }

    val excludedOwners = listOf("Ljava/", "Landroid/", "Lkotlin/", "TraceEvent", "Log", "String", "Collection")
    val candidates = method.instructions
        .filterIsInstance<StructuralInstruction.Invoke>()
        .filter { invoke ->
            invoke.index > start &&
                invoke.index < end &&
                invoke.returnType != "V" &&
                invoke.params.any { it == "I" } &&
                excludedOwners.none { invoke.owner.contains(it) } &&
                method.instructions.any {
                    it is StructuralInstruction.MoveResultObject && it.index in invoke.index..invoke.index + 1
                }
        }
    if (candidates.isEmpty()) throw HeliumResolutionException("semantic binding candidates: 0")

    val scored = candidates.map { invoke ->
        val intParameter = invoke.params.indexOfLast { it == "I" }
        val intRegister = invoke.paramRegister(intParameter)
        val hasLocalFieldProducer = method.instructions.any {
            it is StructuralInstruction.FieldRead &&
                it.dest == intRegister &&
                it.index in invoke.index - 6 until invoke.index
        }
        invoke to (2 + if (hasLocalFieldProducer) 2 else 0)
    }
    val bestScore = scored.maxOf { it.second }
    val best = scored.filter { it.second == bestScore }
    if (best.size != 1) {
        throw HeliumResolutionException("semantic binding candidates: ${best.size}")
    }

    val invoke = best.single().first
    val register = invoke.paramRegister(invoke.params.indexOfLast { it == "I" })
    return BindingResolution(
        invoke.index,
        register,
        if (bestScore > 2) ResolutionStrategy.DATA_FLOW else ResolutionStrategy.SEMANTIC_RELAXED,
        "score=$bestScore",
    )
}

fun resolvePriorityTarget(methods: List<StructuralMethod>): PriorityResolution {
    val candidates = methods.filter { it.name == "setPriority" && it.returnType == "I" }
    if (candidates.size != 1) {
        throw HeliumResolutionException("setPriority: expected one implementation, found ${candidates.size}")
    }
    val method = candidates.single()
    val integers = method.params.withIndex().filter { it.value == "I" }.map { it.index }
    if (integers.isEmpty()) throw HeliumResolutionException("setPriority: no integer parameter")

    val currentShape = integers.size == 2 &&
        method.params.count { it == "Z" } >= 4 &&
        method.params.count { it == "J" } == 1
    if (currentShape) {
        val parameter = integers.last()
        return PriorityResolution(
            parameter,
            parameterRegister(parameter, method.params),
            ResolutionStrategy.SEMANTIC_EXACT,
            "current Chromium shape",
        )
    }

    val uses = method.instructions.filterIsInstance<StructuralInstruction.ParameterUse>()
    val sums = integers.associateWith { parameter ->
        uses.filter { it.parameterIndex == parameter }.sumOf { it.roleWeight }
    }
    val peaks = integers.associateWith { parameter ->
        uses.filter { it.parameterIndex == parameter }.maxOfOrNull { it.roleWeight } ?: 0
    }
    val top = peaks.maxByOrNull { it.value }
    if (top == null || top.value <= 0 || peaks.count { it.value == top.value } != 1) {
        throw HeliumResolutionException("setPriority: ambiguous integer parameters scores=$sums")
    }
    return PriorityResolution(
        top.key,
        parameterRegister(top.key, method.params),
        ResolutionStrategy.DATA_FLOW,
        "scores=$sums peaks=$peaks",
    )
}

private fun parameterRegister(parameterIndex: Int, parameters: List<String>): Int {
    var register = 1
    repeat(parameterIndex) { register += width(parameters[it]) }
    return register
}

fun resolveActivityHook(methods: List<StructuralMethod>): ActivityResolution {
    val candidates = methods.filter { method ->
        method.name == "onStart" &&
            method.returnType == "V" &&
            method.params.isEmpty() &&
            method.instructions.count {
                it is StructuralInstruction.Invoke && it.isSuper && it.name == "onStart"
            } == 1
    }
    if (candidates.isEmpty()) {
        throw HeliumResolutionException("activity onStart: expected one super hook, found 0")
    }

    fun score(method: StructuralMethod) = when {
        method.descriptor == HELIUM_ACTIVITY_CLASS -> 2
        method.descriptor.endsWith("/ChromeTabbedActivity;") -> 1
        else -> 0
    }

    val best = candidates.maxBy(::score)
    if (candidates.count { score(it) == score(best) } > 1) {
        throw HeliumResolutionException("activity onStart: ambiguous candidates: ${candidates.size}")
    }
    val superIndex = best.instructions
        .filterIsInstance<StructuralInstruction.Invoke>()
        .single { it.isSuper && it.name == "onStart" }
        .index
    return ActivityResolution(
        best.descriptor,
        superIndex,
        if (best.descriptor == HELIUM_ACTIVITY_CLASS) {
            ResolutionStrategy.SEMANTIC_EXACT
        } else {
            ResolutionStrategy.SEMANTIC_RELAXED
        },
        "unique super",
    )
}
