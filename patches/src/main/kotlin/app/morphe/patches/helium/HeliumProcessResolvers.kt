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
            "launch region: expected 1 ChildProcessLauncher.start anchor, actual ${anchors.size} | anchors=${anchors.map { it.index }} method has ${instructions.size} insns",
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
            ?: return InstructionRegion(anchor.index, anchor.index + 64, ResolutionStrategy.BOUNDED_FALLBACK, "bounded fallback window: no target invoke after TraceEvent begin @${begin.index} anchor=${anchor.index}")
        val close = instructions.firstOrNull { it.index > target.index && isClose(it) }
            ?: throw HeliumResolutionException(
                "launch region: missing nearest TraceEvent close after target ${target.index} | anchor=${anchor.index} begin=${begin.index} target=${target.index}",
            )
        return InstructionRegion(begin.index, close.index, ResolutionStrategy.SEMANTIC_EXACT, "ordered TraceEvent scope anchor=${anchor.index} begin=${begin.index} target=${target.index} close=${close.index}")
    }
    val end = (anchor.index + 64).coerceAtMost(instructions.maxOfOrNull { it.index } ?: anchor.index)
    return InstructionRegion(anchor.index, end, ResolutionStrategy.BOUNDED_FALLBACK, "bounded fallback window anchor=${anchor.index} end=$end (no TraceEvent begin)")
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
    if (models.isEmpty()) throw HeliumResolutionException("activity: no candidates | models empty")
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
                            "root=${root.type} owner=${current.type} lifecycle=$lifecycle strategy=$strategy superIndex=$superIndex",
                        )
                    }
                    if (methods.size > 1) {
                        throw HeliumResolutionException(
                            "activity: ambiguous $lifecycle overrides in ${current.type} | roots=${roots.map { it.type }} strategy=$strategy",
                        )
                    }
                    current = current.superclass?.let(map::get)
                }
                null
            }
            if (resolutions.size == 1) return resolutions.single()
            if (resolutions.size > 1) {
                throw HeliumResolutionException(
                    "activity: ambiguous viable $lifecycle candidates ${resolutions.size} | strategy=$strategy candidates=${resolutions.map { it.methodDescriptor }}",
                )
            }
        }
    }
    throw HeliumResolutionException(
        "activity: no unique onStart/onResume super hook | exact=${models.count { it.type == HELIUM_ACTIVITY_CLASS }} relaxed=${models.count { it.type.endsWith("/ChromeTabbedActivity;") }} launcher=${models.count { it.isLauncher }} browserEvidence=${models.count { it.browserEvidence }}",
    )
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
        throw HeliumResolutionException(
            "binding: malformed register mapping at invoke $index | params=$params registers=$registers parameterIndex=$parameterIndex isStatic=$isStatic",
        )
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
            "createAndStart: semantic fallback ambiguous candidates=${semantic.size} top=${best.size} | descriptors=${semantic.map { it.descriptor }} bestScore=$bestScore",
        )
    }
    if (candidates.size != 1) {
        throw HeliumResolutionException(
            "createAndStart: expected one implementation, found ${candidates.size} | descriptors=${candidates.map { it.descriptor }}",
        )
    }
    return candidates.single()
}

// --- Binding resolver hardening: fail-closed without overly broad hints ---

private val BINDING_EXCLUDED_OWNERS = listOf("Ljava/", "Landroid/", "Lkotlin/", "TraceEvent", "Log", "String", "Collection")
private val PID_FD_HINTS = listOf("pid", "fd", "filedescriptor", "callback", "handle", "processid")
private val WEAK_CHROMIUM_HINTS = listOf("chromium", "connection", "launch", "launcher", "helium", "browser")

private fun isPidFdHint(invoke: StructuralInstruction.Invoke): Boolean {
    val haystack = "${invoke.owner.lowercase()}#${invoke.name.lowercase()}"
    return PID_FD_HINTS.any { haystack.contains(it) }
}

private fun weakChromiumHint(invoke: StructuralInstruction.Invoke): Boolean {
    val haystack = "${invoke.owner.lowercase()}#${invoke.name.lowercase()}"
    return WEAK_CHROMIUM_HINTS.any { haystack.contains(it) }
}

fun resolveBindingTarget(method: StructuralMethod): BindingResolution {
    val region = resolveLaunchRegion(method.instructions)
    val start = region.startIndex
    val end = region.endIndex

    val candidates = method.instructions
        .filterIsInstance<StructuralInstruction.Invoke>()
        .filter { invoke ->
            invoke.index > start &&
                invoke.index < end &&
                invoke.returnType != "V" &&
                invoke.params.any { it == "I" } &&
                BINDING_EXCLUDED_OWNERS.none { invoke.owner.contains(it) } &&
                method.instructions.any {
                    it is StructuralInstruction.MoveResultObject && it.index in invoke.index..invoke.index + 1
                }
        }
    if (candidates.isEmpty()) {
        throw HeliumResolutionException(
            "binding: no candidates within launch region | method=${method.descriptor} region=${region.startIndex}..${region.endIndex} strategy=${region.strategy} | allInvokes=${method.instructions.filterIsInstance<StructuralInstruction.Invoke>().map { "${it.owner}->${it.name}(${it.params.joinToString("")})@${it.index}" }}",
        )
    }

    data class Scored(
        val invoke: StructuralInstruction.Invoke,
        val candidate: InvokeArgumentCandidate,
        val rejectedReason: String?,
        val strongEvidence: Boolean,
    )

    val scored = mutableListOf<Scored>()
    val rejectedDiagnostics = mutableListOf<String>()

    for (invoke in candidates) {
        // Narrow PID/FD rejection: only reject when hint present; real binding (Li92/a) never matches.
        val pidHint = isPidFdHint(invoke)
        for ((parameter, type) in invoke.params.withIndex()) {
            if (type != "I") continue
            val register: Int
            try {
                register = invoke.paramRegister(parameter)
            } catch (e: HeliumResolutionException) {
                val reason = "malformed register mapping ${e.message}"
                rejectedDiagnostics += "reject invoke=${invoke.owner}->${invoke.name} intArg=$parameter reason=$reason"
                continue
            }
            val evidence = mutableListOf<String>()
            evidence += "integer argument"
            var score = 2
            var rejectedReason: String? = null
            var strongEvidence = false

            val origin = originAt(method, register, invoke.index)
            when (origin) {
                is RegisterOrigin.Field -> {
                    score += 8
                    evidence += "field:${origin.type}"
                    strongEvidence = true
                }
                is RegisterOrigin.Constant -> {
                    if (origin.value in 0..8) {
                        score += 4
                        evidence += "small-enum:${origin.value}"
                        strongEvidence = true
                    } else {
                        rejectedReason = "non-enum-constant:${origin.value}"
                        evidence += rejectedReason
                        score -= 2
                    }
                }
                is RegisterOrigin.Parameter -> {
                    score += 3
                    evidence += "method-parameter:${origin.index}"
                    // parameter alone is not strong without branch; branch check below may make it strong
                }
                is RegisterOrigin.Arithmetic -> {
                    val hasField = origin.sources.any { it is RegisterOrigin.Field }
                    if (hasField) {
                        score += 2
                        strongEvidence = true
                    } else {
                        score -= 1
                    }
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
                evidence += "branch-uses:$branchUses"
                // small-enum + branch is strong, parameter + branch could be considered strong but we keep parameter alone weak
                if (origin is RegisterOrigin.Parameter) {
                    // parameter with branch indicates binding-state test
                    // treat as strong only if we have additional context; for now keep not strong to avoid generic pass
                }
            }
            if (weakChromiumHint(invoke)) {
                evidence += "weak-hint:${invoke.owner}->${invoke.name}"
                // weak hint contributes but does not make candidate valid alone
            } else {
                evidence += "generic-owner:${invoke.owner}->${invoke.name}"
            }
            // move-result-object is structural filter, not evidence per task; do not count

            // PID/FD hint is defensible rejection
            if (pidHint) {
                rejectedReason = "pid/fd hint"
                evidence += rejectedReason
            }

            // Credibility: require strong binding-state evidence (field, small-enum, derived field)
            // This preserves real APK (small-enum 3 via move) while failing lone generic unknown-origin.
            val credible = strongEvidence && rejectedReason == null

            if (!credible) {
                if (rejectedReason == null) rejectedReason = "insufficient binding-state evidence evidence=$evidence score=$score"
                rejectedDiagnostics += "reject invoke=${invoke.owner}->${invoke.name} intArg=$parameter register=v$register score=$score evidence=$evidence reason=$rejectedReason"
                scored += Scored(invoke, InvokeArgumentCandidate(parameter, register, score, evidence.toList()), rejectedReason, false)
                continue
            }

            scored += Scored(invoke, InvokeArgumentCandidate(parameter, register, score, evidence.toList()), null, true)
        }
    }
    val valid = scored.filter { it.rejectedReason == null }
    if (valid.isEmpty()) {
        val candidateDump = candidates.map { "${it.owner}->${it.name}(${it.params.joinToString("")})@${it.index} regs=${it.registers}" }
        throw HeliumResolutionException(
            "binding: no credible candidates | method=${method.descriptor} region=${region.startIndex}..${region.endIndex} strategy=${region.strategy} | candidates=$candidateDump | intPositions=${scored.map { "${it.invoke.owner}->${it.invoke.name}#${it.candidate.parameterIndex}:v${it.candidate.register} score=${it.candidate.score} evidence=${it.candidate.evidence}" }} | rejected=${rejectedDiagnostics.joinToString("; ")}",
        )
    }

    val bestScore = valid.maxOf { it.candidate.score }
    val best = valid.filter { it.candidate.score == bestScore }
    if (best.size != 1) {
        throw HeliumResolutionException(
            "binding: ambiguous top candidates ${best.size} score=$bestScore | method=${method.descriptor} region=${region.startIndex}..${region.endIndex} | tied=${best.map { "${it.invoke.owner}->${it.invoke.name}#${it.candidate.parameterIndex}:v${it.candidate.register} evidence=${it.candidate.evidence}" }} | allValid=${valid.map { "${it.invoke.owner}->${it.invoke.name}#${it.candidate.parameterIndex} score=${it.candidate.score}" }} | rejected=${rejectedDiagnostics.joinToString("; ")}",
        )
    }

    // Low-confidence threshold: even single strong candidate must meet minimal score
    val threshold = 3
    if (bestScore < threshold) {
        throw HeliumResolutionException(
            "binding: low confidence bestScore=$bestScore < $threshold | method=${method.descriptor} region=${region.startIndex}..${region.endIndex} | best=${best.single().let { "${it.invoke.owner}->${it.invoke.name}#${it.candidate.parameterIndex} evidence=${it.candidate.evidence}" }}",
        )
    }

    val (invoke, argument) = best.single().let { it.invoke to it.candidate }
    val strategy = if (bestScore >= 6) ResolutionStrategy.DATA_FLOW else ResolutionStrategy.SEMANTIC_RELAXED
    return BindingResolution(
        invoke.index,
        argument.register,
        strategy,
        "method=${method.descriptor} region=${region.startIndex}..${region.endIndex} strategy=${region.strategy} invoke=${invoke.owner}->${invoke.name}(${invoke.params.joinToString("")}) intArg=${argument.parameterIndex} register=v${argument.register} score=$bestScore evidence=${argument.evidence.joinToString()} origin=${originAt(method, argument.register, invoke.index)} | rejected=${rejectedDiagnostics.size}",
    )
}

fun resolvePriorityTarget(methods: List<StructuralMethod>): PriorityResolution {
    if (methods.isEmpty()) throw HeliumResolutionException("setPriority: no methods provided")

    fun isVerifiedShape(m: StructuralMethod): Boolean {
        if (m.returnType != "I") return false
        if (m.params.count { it == "I" } != 2) return false
        if (m.params.count { it == "Z" } < 4) return false
        if (m.params.count { it == "J" } != 1) return false
        // name may be obfuscated but verified shape requires setPriority exact name
        if (m.name != HELIUM_SET_PRIORITY_METHOD) return false
        return true
    }

    val exactByName = methods.filter { it.name == HELIUM_SET_PRIORITY_METHOD && it.returnType == "I" }
    val verifiedExact = exactByName.filter(::isVerifiedShape)

    when {
        verifiedExact.size == 1 -> {
            val method = verifiedExact.single()
            val integers = method.params.withIndex().filter { it.value == "I" }.map { it.index }
            val parameter = integers.last()
            // Secondary data-flow sanity check where available
            val uses = method.instructions.filterIsInstance<StructuralInstruction.ParameterUse>()
            if (uses.isNotEmpty()) {
                val peaks = integers.associateWith { p -> uses.filter { it.parameterIndex == p }.maxOfOrNull { it.roleWeight } ?: 0 }
                val top = peaks.maxByOrNull { it.value } ?: throw HeliumResolutionException("setPriority: no peaks")
                // If data-flow exists, selected last int should be the peak and unique
                if (top.key != parameter || peaks.count { it.value == top.value } != 1 || top.value <= 0) {
                    throw HeliumResolutionException(
                        "setPriority: verified shape sanity failed peaks=$peaks selected=$parameter | method=${method.descriptor}",
                    )
                }
            }
            return PriorityResolution(
                methodDescriptor = method.descriptor,
                parameterIndex = parameter,
                parameterWordOffset = method.parameterWordOffset(parameter),
                strategy = ResolutionStrategy.SEMANTIC_EXACT,
                diagnostics = "verified Chromium shape ints=$integers Z=${method.params.count { it == "Z" }} J=${method.params.count { it == "J" }} param=$parameter offset=${method.parameterWordOffset(parameter)}",
            )
        }
        verifiedExact.size > 1 -> throw HeliumResolutionException(
            "setPriority: multiple verified shapes ${verifiedExact.size} | descriptors=${verifiedExact.map { it.descriptor }}",
        )
        exactByName.size == 1 -> {
            val method = exactByName.single()
            val integers = method.params.withIndex().filter { it.value == "I" }.map { it.index }
            if (integers.isEmpty()) throw HeliumResolutionException("setPriority: no integer parameter | method=${method.descriptor}")
            val uses = method.instructions.filterIsInstance<StructuralInstruction.ParameterUse>()
            val sums = integers.associateWith { p -> uses.filter { it.parameterIndex == p }.sumOf { it.roleWeight } }
            val peaks = integers.associateWith { p -> uses.filter { it.parameterIndex == p }.maxOfOrNull { it.roleWeight } ?: 0 }
            val top = peaks.maxByOrNull { it.value }
            if (top == null || top.value <= 0 || peaks.count { it.value == top.value } != 1) {
                throw HeliumResolutionException(
                    "setPriority: ambiguous integer parameters scores=$sums peaks=$peaks | method=${method.descriptor} params=${method.params} expected verified shape 2xI >=4xZ 1xJ",
                )
            }
            if (top.value < 4) {
                throw HeliumResolutionException(
                    "setPriority: weak data-flow peak ${top.value} <4 for unverified shape | method=${method.descriptor} params=${method.params} peaks=$peaks",
                )
            }
            return PriorityResolution(
                methodDescriptor = method.descriptor,
                parameterIndex = top.key,
                parameterWordOffset = method.parameterWordOffset(top.key),
                strategy = ResolutionStrategy.DATA_FLOW,
                diagnostics = "unverified shape data-flow sums=$sums peaks=$peaks selected=${top.key}",
            )
        }
        exactByName.size > 1 -> throw HeliumResolutionException(
            "setPriority: multiple exact-name candidates ${exactByName.size} | descriptors=${exactByName.map { it.descriptor }}",
        )
        else -> {
            val structural = methods.filter {
                it.returnType == "I" &&
                    it.params.count { p -> p == "I" } >= 2 &&
                    it.params.count { p -> p == "Z" } >= 2
            }
            if (structural.isEmpty()) {
                throw HeliumResolutionException(
                    "setPriority: no structural candidates | methods=${methods.map { "${it.descriptor} params=${it.params} return=${it.returnType}" }}",
                )
            }
            val scored = structural.map { method ->
                var score = 0
                val evidence = mutableListOf<String>()
                if (method.params.count { it == "Z" } >= 4) { score += 4; evidence += "many-Z:${method.params.count { it == "Z" }}" }
                if (method.params.count { it == "J" } == 1) { score += 3; evidence += "single-J" }
                if (method.params.count { it == "I" } == 2) { score += 2; evidence += "two-I" }
                val peak = method.instructions.filterIsInstance<StructuralInstruction.ParameterUse>().maxOfOrNull { it.roleWeight } ?: 0
                if (peak >= 4) { score += 4; evidence += "peak-weight:$peak" }
                else if (peak > 0) { score += 1; evidence += "weak-peak:$peak" }
                val fieldWrites = method.instructions.count { it is StructuralInstruction.FieldWrite }
                if (fieldWrites > 0) { score += minOf(fieldWrites, 2) * 2; evidence += "fieldWrites:$fieldWrites" }
                val branchOps = method.instructions.count { it is StructuralInstruction.Other && (it.opcode.startsWith("IF") || it.opcode.startsWith("CMP")) }
                if (branchOps > 0) { score += 2; evidence += "branchOps:$branchOps" }
                if (method.name.contains("Priority", ignoreCase = true)) { score += 2; evidence += "name-hint:${method.name}" }
                method to (score to evidence)
            }
            val bestScore = scored.maxOf { it.second.first }
            val best = scored.filter { it.second.first == bestScore }
            if (best.size != 1) {
                throw HeliumResolutionException(
                    "setPriority: structural fallback ambiguous ${best.size} score=$bestScore | tied=${best.map { "${it.first.descriptor} evidence=${it.second.second}" }} | all=${scored.map { "${it.first.descriptor} score=${it.second.first} evidence=${it.second.second}" }}",
                )
            }
            val (method, pair) = best.single()
            val (score, evidence) = pair
            if (score < 8) {
                throw HeliumResolutionException(
                    "setPriority: structural fallback low confidence score=$score <8 | method=${method.descriptor} evidence=$evidence params=${method.params}",
                )
            }
            if (evidence.size < 3) {
                throw HeliumResolutionException(
                    "setPriority: structural fallback insufficient evidence ${evidence.size} <3 | method=${method.descriptor} evidence=$evidence",
                )
            }
            val integers = method.params.withIndex().filter { it.value == "I" }.map { it.index }
            val uses = method.instructions.filterIsInstance<StructuralInstruction.ParameterUse>()
            val sums = integers.associateWith { p -> uses.filter { it.parameterIndex == p }.sumOf { it.roleWeight } }
            val peaks = integers.associateWith { p -> uses.filter { it.parameterIndex == p }.maxOfOrNull { it.roleWeight } ?: 0 }
            val top = peaks.maxByOrNull { it.value }
            if (top == null || top.value <= 0 || peaks.count { it.value == top.value } != 1) {
                throw HeliumResolutionException(
                    "setPriority: ambiguous integer parameters scores=$sums peaks=$peaks | method=${method.descriptor} evidence=$evidence",
                )
            }
            if (top.key !in method.params.indices || method.params[top.key] != "I") {
                throw HeliumResolutionException("setPriority: selected non-int param ${top.key} method=${method.descriptor}")
            }
            return PriorityResolution(
                methodDescriptor = method.descriptor,
                parameterIndex = top.key,
                parameterWordOffset = method.parameterWordOffset(top.key),
                strategy = ResolutionStrategy.DATA_FLOW,
                diagnostics = "structural fallback score=$score evidence=$evidence sums=$sums peaks=$peaks selected=${top.key}",
            )
        }
    }
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
        throw HeliumResolutionException(
            "activity onStart: expected one super hook, found 0 | methods=${methods.map { it.descriptor }}",
        )
    }

    fun score(method: StructuralMethod) = when {
        method.descriptor == HELIUM_ACTIVITY_CLASS -> 2
        method.descriptor.endsWith("/ChromeTabbedActivity;") -> 1
        else -> 0
    }

    val best = candidates.maxBy(::score)
    if (candidates.count { score(it) == score(best) } > 1) {
        throw HeliumResolutionException(
            "activity onStart: ambiguous candidates: ${candidates.size} | descriptors=${candidates.map { it.descriptor }} bestScore=${score(best)}",
        )
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
        "unique super lifecycle=onStart superIndex=$superIndex",
    )
}
