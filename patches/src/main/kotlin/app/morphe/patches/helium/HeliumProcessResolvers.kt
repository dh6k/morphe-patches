package app.morphe.patches.helium

enum class ResolutionStrategy {
    SEMANTIC_EXACT,
    SEMANTIC_RELAXED,
    DATA_FLOW,
    BOUNDED_FALLBACK,
    HIERARCHY_FALLBACK,
    MANIFEST_FALLBACK,
}

data class InstructionRegion(val startIndex: Int, val endIndex: Int, val strategy: ResolutionStrategy, val diagnostics: String)
sealed class RegisterOrigin {
    data class Parameter(val index: Int) : RegisterOrigin()
    data class Constant(val value: Int) : RegisterOrigin()
    data class Field(val type: String) : RegisterOrigin()
    data class Arithmetic(val opcode: String, val sources: List<RegisterOrigin?>) : RegisterOrigin()
}
data class InvokeArgumentCandidate(val parameterIndex: Int, val register: Int, val score: Int, val evidence: List<String>)

fun originAt(method: StructuralMethod, register: Int, beforeIndex: Int, depth: Int = 0, seen: Set<Int> = emptySet()): RegisterOrigin? {
    if (depth > 8 || register in seen) return null
    val fact = method.instructions.filter { it.index < beforeIndex }.lastOrNull {
        when (it) {
            is StructuralInstruction.Move -> it.dest == register
            is StructuralInstruction.Const -> it.dest == register
            is StructuralInstruction.FieldRead -> it.dest == register
            is StructuralInstruction.Other ->
                it.registers.firstOrNull() == register &&
                    (it.opcode.startsWith("AGET") ||
                        it.opcode.startsWith("ADD_") ||
                        it.opcode.startsWith("SUB_") ||
                        it.opcode.startsWith("AND_") ||
                        it.opcode.startsWith("OR_") ||
                        it.opcode.startsWith("XOR_"))
            else -> false
        }
    }
        ?: return parameterRegisterMap(method.params, method.registerCount, method.isStatic)
            .entries
            .firstOrNull { it.value == register }
            ?.let { RegisterOrigin.Parameter(it.key) }
    return when (fact) {
        is StructuralInstruction.Move -> originAt(method, fact.source, fact.index, depth + 1, seen + register)
        is StructuralInstruction.Const -> RegisterOrigin.Constant(fact.value)
        is StructuralInstruction.FieldRead -> RegisterOrigin.Field(fact.type)
        is StructuralInstruction.Other -> RegisterOrigin.Arithmetic(
            fact.opcode,
            fact.registers.drop(1).map {
                originAt(method, it, fact.index, depth + 1, seen + register)
            },
        )
        else -> null
    }
}

fun resolveLaunchRegion(instructions: List<StructuralInstruction>): InstructionRegion {
    val anchors = instructions.filter { it is StructuralInstruction.StringLiteral && it.value == "ChildProcessLauncher.start" }
    if (anchors.size != 1) {
        throw HeliumResolutionException(
            "launch region: expected 1 ChildProcessLauncher.start anchor, actual ${anchors.size}",
        )
    }
    val anchor = anchors.single()
    fun isTrace(i: StructuralInstruction) = i is StructuralInstruction.StringLiteral && i.value.contains("TraceEvent") || i is StructuralInstruction.Invoke && i.owner.contains("TraceEvent")
    fun isBegin(i: StructuralInstruction) = isTrace(i) && (i !is StructuralInstruction.Invoke || i.params.size >= 2)
    fun isClose(i: StructuralInstruction) = isTrace(i) && (i !is StructuralInstruction.Invoke || i.params.size <= 1)
    val begin = instructions.firstOrNull { it.index > anchor.index && isBegin(it) }
    if (begin != null) {
        val target = instructions.firstOrNull {
            it.index > begin.index &&
                it is StructuralInstruction.Invoke &&
                it.returnType != "V" &&
                it.params.any { parameter -> parameter == "I" } &&
                !it.owner.startsWith("Ljava/") &&
                !it.owner.startsWith("Landroid/")
        }
            ?: return InstructionRegion(anchor.index, anchor.index + 64, ResolutionStrategy.BOUNDED_FALLBACK, "bounded fallback window")
        val close = instructions.firstOrNull { it.index > target.index && isClose(it) }
            ?: throw HeliumResolutionException("launch region: missing nearest TraceEvent close")
        return InstructionRegion(begin.index, close.index, ResolutionStrategy.SEMANTIC_EXACT, "ordered TraceEvent scope")
    }
    val end = (anchor.index + 64).coerceAtMost(instructions.maxOfOrNull { it.index } ?: anchor.index)
    return InstructionRegion(anchor.index, end, ResolutionStrategy.BOUNDED_FALLBACK, "bounded fallback window")
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
    val methodDescriptor: String,
    val parameterIndex: Int,
    val parameterWordOffset: Int,
    val strategy: ResolutionStrategy,
    val diagnostics: String,
)

data class ActivityResolution(
    val methodDescriptor: String,
    val superIndex: Int,
    val strategy: ResolutionStrategy,
    val diagnostics: String,
)
data class ActivityClassModel(
    val type: String,
    val superclass: String?,
    val methods: List<StructuralMethod>,
    val isLauncher: Boolean = false,
    val browserEvidence: Boolean = false,
)

@JvmName("resolveActivityHookModels")
fun resolveActivityHook(models: List<ActivityClassModel>): ActivityResolution {
    if (models.isEmpty()) throw HeliumResolutionException("activity: no candidates")
    val map = models.associateBy { it.type }
    val groups = listOf(
        models.filter { it.type == HELIUM_ACTIVITY_CLASS } to ResolutionStrategy.SEMANTIC_EXACT,
        models.filter { it.type.endsWith("/ChromeTabbedActivity;") } to ResolutionStrategy.SEMANTIC_RELAXED,
        models.filter { it.isLauncher } to ResolutionStrategy.MANIFEST_FALLBACK,
        models.filter { it.browserEvidence } to ResolutionStrategy.HIERARCHY_FALLBACK,
    )
    for ((roots, strategy) in groups) {
        if (roots.isEmpty()) continue
        for (lifecycle in listOf("onStart", "onResume")) {
            val resolutions = roots.mapNotNull { root ->
                var current: ActivityClassModel? = root
                while (current != null) {
                    val methods = current.methods.filter { method ->
                        method.name == lifecycle &&
                            method.returnType == "V" &&
                            method.params.isEmpty() &&
                            method.instructions.count { instruction ->
                                instruction is StructuralInstruction.Invoke &&
                                    instruction.isSuper &&
                                    instruction.name == lifecycle
                            } == 1
                    }
                    if (methods.size == 1) {
                        val method = methods.single()
                        val superIndex = method.instructions
                            .filterIsInstance<StructuralInstruction.Invoke>()
                            .single { it.isSuper && it.name == lifecycle }
                            .index
                        return@mapNotNull ActivityResolution(
                            method.descriptor,
                            superIndex,
                            strategy,
                            "root=${root.type} owner=${current.type} lifecycle=$lifecycle",
                        )
                    }
                    current = current.superclass?.let(map::get)
                }
                null
            }
            if (resolutions.size == 1) return resolutions.single()
            if (resolutions.size > 1) {
                throw HeliumResolutionException("activity: ambiguous viable $lifecycle candidates ${resolutions.size}")
            }
        }
    }
    throw HeliumResolutionException("activity: no unique onStart/onResume super hook")
}

class HeliumResolutionException(message: String) : IllegalStateException(message)

private fun width(type: String) = if (type == "J" || type == "D") 2 else 1
fun StructuralMethod.parameterWordOffset(parameterIndex: Int): Int {
    require(parameterIndex in params.indices)
    return (if (isStatic) 0 else 1) + params.take(parameterIndex).sumOf(::width)
}

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
    if (candidates.isEmpty()) {
        val semantic = methods.filter {
            it.instructions.any { instruction ->
                instruction is StructuralInstruction.StringLiteral &&
                    instruction.value == "ChildProcessLauncher.start"
            }
        }
        val scored = semantic.map { method ->
            val strings = method.instructions.filterIsInstance<StructuralInstruction.StringLiteral>()
                .map { it.value }
                .toSet()
            val launchCalls = method.instructions.filterIsInstance<StructuralInstruction.Invoke>().count {
                it.returnType != "V" && it.params.contains("I")
            }
            val score = 10 +
                (if ("renderer" in strings) 3 else 0) +
                (if ("gpu-process" in strings) 3 else 0) +
                minOf(launchCalls, 2)
            method to score
        }
        val bestScore = scored.maxOfOrNull { it.second }
        val best = if (bestScore == null) emptyList() else scored.filter { it.second == bestScore }
        if (best.size == 1) return best.single().first
        throw HeliumResolutionException(
            "createAndStart: semantic fallback ambiguous candidates=${semantic.size} top=${best.size}",
        )
    }
    if (candidates.size != 1) {
        throw HeliumResolutionException(
            "createAndStart: expected one implementation, found ${candidates.size}",
        )
    }
    return candidates.single()
}

fun resolveBindingTarget(method: StructuralMethod): BindingResolution {
    val region = resolveLaunchRegion(method.instructions)
    val start = region.startIndex
    val end = region.endIndex

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

    val scored = candidates.flatMap { invoke ->
        invoke.params.mapIndexedNotNull { parameter, type ->
            if (type != "I") return@mapIndexedNotNull null
            val register = invoke.paramRegister(parameter)
            val evidence = mutableListOf("integer argument")
            var score = 2
            when (val origin = originAt(method, register, invoke.index)) {
                is RegisterOrigin.Field -> {
                    score += 8
                    evidence += "field:${origin.type}"
                }

                is RegisterOrigin.Constant -> {
                    if (origin.value in 0..8) {
                        score += 4
                        evidence += "small-enum:${origin.value}"
                    } else {
                        score -= 2
                        evidence += "non-enum-constant:${origin.value}"
                    }
                }

                is RegisterOrigin.Parameter -> {
                    score += 3
                    evidence += "method-parameter:${origin.index}"
                }

                is RegisterOrigin.Arithmetic -> {
                    score += if (origin.sources.any { it is RegisterOrigin.Field }) 2 else -1
                    evidence += "derived:${origin.opcode}"
                }

                null -> evidence += "unknown-origin"
            }
            val branchUses = method.instructions.count {
                it is StructuralInstruction.Other &&
                    it.index < invoke.index &&
                    it.index >= invoke.index - 24 &&
                    it.registers.contains(register) &&
                    (it.opcode.startsWith("IF") || it.opcode.startsWith("CMP"))
            }
            if (branchUses > 0) {
                score += minOf(branchUses, 2) * 2
                evidence += "binding-branch-uses:$branchUses"
            }
            invoke to InvokeArgumentCandidate(parameter, register, score, evidence)
        }
    }
    val bestScore = scored.maxOf { it.second.score }
    val best = scored.filter { it.second.score == bestScore }
    if (best.size != 1) {
        throw HeliumResolutionException("semantic binding candidates: ${best.size}")
    }

    val (invoke, argument) = best.single()
    return BindingResolution(
        invoke.index,
        argument.register,
        if (bestScore > 2) ResolutionStrategy.DATA_FLOW else ResolutionStrategy.SEMANTIC_RELAXED,
        "region=${region.startIndex}..${region.endIndex} strategy=${region.strategy} " +
            "invoke=${invoke.owner}->${invoke.name} intArg=${argument.parameterIndex} " +
            "register=v${argument.register} score=$bestScore evidence=${argument.evidence.joinToString()}",
    )
}

fun resolvePriorityTarget(methods: List<StructuralMethod>): PriorityResolution {
    val exact = methods.filter { it.name == "setPriority" && it.returnType == "I" }
    val candidates = if (exact.isNotEmpty()) {
        exact
    } else {
        val structural = methods.filter {
            it.returnType == "I" &&
                it.params.count { parameter -> parameter == "I" } >= 2
        }
        val scored = structural.map { method ->
            val score =
                method.params.count { it == "Z" } * 2 +
                    method.params.count { it == "J" } * 3 +
                    method.instructions.filterIsInstance<StructuralInstruction.ParameterUse>()
                        .maxOfOrNull { it.roleWeight }.orZero() +
                    method.instructions.count { it is StructuralInstruction.FieldWrite } * 2
            method to score
        }
        val bestScore = scored.maxOfOrNull { it.second }
        if (bestScore == null) emptyList() else scored.filter { it.second == bestScore }.map { it.first }
    }
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
            methodDescriptor = method.descriptor,
            parameterIndex = parameter,
            parameterWordOffset = method.parameterWordOffset(parameter),
            strategy = ResolutionStrategy.SEMANTIC_EXACT,
            diagnostics = "current Chromium shape",
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
        methodDescriptor = method.descriptor,
        parameterIndex = top.key,
        parameterWordOffset = method.parameterWordOffset(top.key),
        strategy = ResolutionStrategy.DATA_FLOW,
        diagnostics = "scores=$sums peaks=$peaks",
    )
}

private fun Int?.orZero() = this ?: 0

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
