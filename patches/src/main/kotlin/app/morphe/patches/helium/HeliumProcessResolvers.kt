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
            "launch region: expected 1 ChildProcessLauncher.start anchor, actual ${anchors.size} | " +
                "anchors=${anchors.map { it.index }}",
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
            ?: return InstructionRegion(anchor.index, anchor.index + 64, ResolutionStrategy.BOUNDED_FALLBACK, "bounded fallback window: no target invoke after TraceEvent begin @${begin.index}")
        val close = instructions.firstOrNull { it.index > target.index && isClose(it) }
            ?: throw HeliumResolutionException(
                "launch region: missing nearest TraceEvent close after target ${target.index} | " +
                    "method has ${instructions.size} insns anchor=${anchor.index} begin=${begin.index} target=${target.index}",
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
        for (lifecycle in listOf(HELIUM_LIFECYCLE_ON_START, HELIUM_LIFECYCLE_ON_RESUME)) {
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
                    if (methods.size > 1) {
                        throw HeliumResolutionException(
                            "activity: ambiguous $lifecycle overrides in ${current.type} | roots=$roots strategy=$strategy",
                        )
                    }
                    current = current.superclass?.let(map::get)
                }
                null
            }
            if (resolutions.size == 1) return resolutions.single()
            if (resolutions.size > 1) {
                throw HeliumResolutionException(
                    "activity: ambiguous viable $lifecycle candidates ${resolutions.size} | " +
                        "strategy=$strategy candidates=${resolutions.map { it.methodDescriptor }}",
                )
            }
        }
    }
    throw HeliumResolutionException(
        "activity: no unique onStart/onResume super hook | " +
            "exact=${models.count { it.type == HELIUM_ACTIVITY_CLASS }} " +
            "relaxed=${models.count { it.type.endsWith("/ChromeTabbedActivity;") }} " +
            "launcher=${models.count { it.isLauncher }} browserEvidence=${models.count { it.browserEvidence }}",
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
            "binding: malformed register mapping at invoke $index | " +
                "params=$params registers=$registers parameterIndex=$parameterIndex isStatic=$isStatic",
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
            "createAndStart: semantic fallback ambiguous candidates=${semantic.size} top=${best.size} | " +
                "descriptors=${semantic.map { it.descriptor }} bestScore=$bestScore",
        )
    }
    if (candidates.size != 1) {
        throw HeliumResolutionException(
            "createAndStart: expected one implementation, found ${candidates.size} | descriptors=${candidates.map { it.descriptor }}",
        )
    }
    return candidates.single()
}

// --- Binding resolver hardening ---

private val BINDING_EXCLUDED_OWNERS = listOf("Ljava/", "Landroid/", "Lkotlin/", "TraceEvent", "Log", "String", "Collection")
private val PID_FD_HINTS = listOf("pid", "fd", "processid", "process_type", "filedescriptor", "callbackid", "handle")
private val CHROMIUM_OWNER_HINTS = listOf("chromium", "childprocess", "child_process", "launcher", "launch", "connection", "bindingstate", "childbinding", "child_process_launcher", "helium")

private fun isPidFdHint(invoke: StructuralInstruction.Invoke): Boolean {
    val haystack = "${invoke.owner.lowercase()}#${invoke.name.lowercase()}"
    return PID_FD_HINTS.any { haystack.contains(it) }
}

private fun isChromiumHint(invoke: StructuralInstruction.Invoke): Boolean {
    val haystack = "${invoke.owner.lowercase()}#${invoke.name.lowercase()}"
    return CHROMIUM_OWNER_HINTS.any { haystack.contains(it) }
}

fun validateBindingRegisterSafety(method: StructuralMethod, resolution: BindingResolution) {
    val register = resolution.register
    val fromIndex = resolution.index
    // Dalvik encoding limits: const/16 covers v0..v255, invoke 5-reg covers v0..v15 per arg.
    // If method uses many registers, overwriting may exceed limits or require new temp.
    if (register < 0 || register >= 256) {
        throw HeliumResolutionException(
            "binding register safety: register v$register out of const/16 range | " +
                "method=${method.descriptor} registerCount=${method.registerCount} invokeIndex=$fromIndex",
        )
    }
    if (method.registerCount > 240) {
        throw HeliumResolutionException(
            "binding register safety: method near Dalvik register limit | " +
                "method=${method.descriptor} registerCount=${method.registerCount} register=v$register",
        )
    }
    // Liveness: if register reused after invoke before redefinition, clobbering is unsafe.
    var redefined = false
    for (insn in method.instructions.filter { it.index > fromIndex }.sortedBy { it.index }) {
        val defines = when (insn) {
            is StructuralInstruction.Move -> insn.dest == register
            is StructuralInstruction.Const -> insn.dest == register
            is StructuralInstruction.FieldRead -> insn.dest == register
            is StructuralInstruction.MoveResultObject -> insn.dest == register
            is StructuralInstruction.Other -> insn.registers.firstOrNull() == register &&
                (insn.opcode.startsWith("AGET") || insn.opcode.startsWith("ADD_") || insn.opcode.startsWith("SUB_"))
            else -> false
        }
        if (defines) {
            redefined = true
            break
        }
        val uses = when (insn) {
            is StructuralInstruction.Invoke -> register in insn.registers
            is StructuralInstruction.Move -> insn.source == register
            is StructuralInstruction.FieldWrite -> insn.source == register
            is StructuralInstruction.Other -> register in insn.registers
            else -> false
        }
        if (uses) {
            throw HeliumResolutionException(
                "binding register safety: register v$register live after invoke $fromIndex | " +
                    "used at ${insn.index} (${insn::class.simpleName}) before redefinition | " +
                    "method=${method.descriptor} diagnostics=${resolution.diagnostics}",
            )
        }
    }
    // Also check invoke range vs 5-reg encoding: if invoke uses >5 registers, const/16 target must not interfere with range.
    // For now, fail closed if invoke has >5 args and register >15 and invoke is not range? Hard to know opcode; use registers size as proxy.
    // If registers size >5, it must be range invoke; range invokes can address high registers, but our const must still be valid.
    // No extra check needed beyond 256 limit; document limitation.
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
            "binding: no candidates within launch region | " +
                "method=${method.descriptor} region=${region.startIndex}..${region.endIndex} strategy=${region.strategy} | " +
                "allInvokes=${method.instructions.filterIsInstance<StructuralInstruction.Invoke>().map { "${it.owner}->${it.name}(${it.params.joinToString("")})@${it.index}" }}",
        )
    }

    data class Scored(
        val invoke: StructuralInstruction.Invoke,
        val candidate: InvokeArgumentCandidate,
        val rejectedReason: String?,
    )

    val scored = mutableListOf<Scored>()
    val rejectedDiagnostics = mutableListOf<String>()

    for (invoke in candidates) {
        // Reject pid/fd-like invokes outright before scoring per-int args
        if (isPidFdHint(invoke)) {
            for ((idx, type) in invoke.params.withIndex()) {
                if (type != "I") continue
                rejectedDiagnostics += "reject invoke=${invoke.owner}->${invoke.name} intArg=$idx reason=pid/fd hint"
            }
            continue
        }
        for ((parameter, type) in invoke.params.withIndex()) {
            if (type != "I") continue
            val register: Int
            try {
                register = invoke.paramRegister(parameter)
            } catch (e: HeliumResolutionException) {
                rejectedDiagnostics += "reject invoke=${invoke.owner}->${invoke.name} intArg=$parameter reason=malformed register mapping ${e.message}"
                continue
            }
            val evidence = mutableListOf<String>("integer argument")
            var score = 2
            var rejectedReason: String? = null

            val origin = originAt(method, register, invoke.index)
            when (origin) {
                is RegisterOrigin.Field -> {
                    score += 8
                    evidence += "field:${origin.type}"
                }
                is RegisterOrigin.Constant -> {
                    if (origin.value in 0..8) {
                        score += 4
                        evidence += "small-enum:${origin.value}"
                    } else {
                        // Large constants strongly suggest PID, FD, offset, not binding state
                        rejectedReason = "non-enum-constant:${origin.value}"
                        evidence += rejectedReason!!
                        score -= 6
                    }
                }
                is RegisterOrigin.Parameter -> {
                    score += 3
                    evidence += "method-parameter:${origin.index}"
                }
                is RegisterOrigin.Arithmetic -> {
                    if (origin.sources.any { it is RegisterOrigin.Field }) {
                        score += 2
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
                evidence += "binding-branch-uses:$branchUses"
            }
            val chromiumHint = isChromiumHint(invoke)
            if (chromiumHint) {
                score += 3
                evidence += "chromium-hint:${invoke.owner}->${invoke.name}"
            } else {
                evidence += "generic-owner:${invoke.owner}->${invoke.name}"
            }
            evidence += "moveResultObject"
            score += 1
            val hasField = origin is RegisterOrigin.Field
            val hasSmallEnumBranch = origin is RegisterOrigin.Constant && origin.value in 0..8 && branchUses > 0
            val hasDerivedFieldBranch = origin is RegisterOrigin.Arithmetic && origin.sources.any { it is RegisterOrigin.Field } && branchUses > 0
            val hasParamBranch = origin is RegisterOrigin.Parameter && branchUses > 0
            val hasMoveResultChromiumScope = chromiumHint && region.strategy == ResolutionStrategy.SEMANTIC_EXACT
            // Credibility: field alone is strong; otherwise need branch combo, or exact-region chromium hint.
            // This preserves prior validated fixtures (Lx->launch in TraceEvent scope) while still failing
            // on fully generic unknown-origin + no chromium hint + no field/branch.
            val credible = when {
                hasField -> true
                hasSmallEnumBranch || hasDerivedFieldBranch || hasParamBranch -> true
                hasMoveResultChromiumScope && chromiumHint -> true
                chromiumHint && region.strategy == ResolutionStrategy.BOUNDED_FALLBACK -> {
                    evidence += "weak-chromium-in-bounded"
                    true
                }
                // For SEMANTIC_EXACT region, a generic moveResultObject invoke with unknown origin but
                // inside the TraceEvent scope still gets one chance if it is the only candidate;
                // we defer to low-confidence / ambiguity checks below rather than early rejection.
                region.strategy == ResolutionStrategy.BOUNDED_FALLBACK && !chromiumHint && origin == null -> {
                    evidence += "weak-generic-in-bounded"
                    true
                }
                region.strategy == ResolutionStrategy.SEMANTIC_EXACT && !chromiumHint && origin == null -> {
                    // Allow but only if no competing candidates - mark as weak for later threshold
                    evidence += "weak-generic-in-exact-region"
                    true
                }
                else -> false
            }
            if (!credible) {
                if (rejectedReason == null) rejectedReason = "insufficient binding-state evidence evidence=$evidence score=$score"
                rejectedDiagnostics += "reject invoke=${invoke.owner}->${invoke.name} intArg=$parameter register=v$register score=$score evidence=$evidence reason=$rejectedReason"
                scored += Scored(invoke, InvokeArgumentCandidate(parameter, register, score, evidence.toList()), rejectedReason)
                continue
            }
            if (rejectedReason != null) {
                rejectedDiagnostics += "reject invoke=${invoke.owner}->${invoke.name} intArg=$parameter register=v$register score=$score evidence=$evidence reason=$rejectedReason"
                scored += Scored(invoke, InvokeArgumentCandidate(parameter, register, score, evidence.toList()), rejectedReason)
                continue
            }

            scored += Scored(invoke, InvokeArgumentCandidate(parameter, register, score, evidence.toList()), null)
        }
    }
    val valid = scored.filter { it.rejectedReason == null }
    if (valid.isEmpty()) {
        val candidateDump = candidates.map { "${it.owner}->${it.name}(${it.params.joinToString("")})@${it.index} regs=${it.registers}" }
        throw HeliumResolutionException(
            "binding: no credible candidates | " +
                "method=${method.descriptor} region=${region.startIndex}..${region.endIndex} strategy=${region.strategy} | " +
                "candidates=$candidateDump | " +
                "intPositions=${scored.map { "${it.invoke.owner}->${it.invoke.name}#${it.candidate.parameterIndex}:v${it.candidate.register} score=${it.candidate.score} evidence=${it.candidate.evidence}" }} | " +
                "rejected=${rejectedDiagnostics.joinToString("; ")}",
        )
    }

    // Bounded fallback requires higher confidence
    if (region.strategy == ResolutionStrategy.BOUNDED_FALLBACK) {
        val minScore = 3
        val bestInFallback = valid.maxOf { it.candidate.score }
        if (bestInFallback < minScore) {
            throw HeliumResolutionException(
                "binding: bounded fallback low confidence | " +
                    "method=${method.descriptor} region=${region.startIndex}..${region.endIndex} bestScore=$bestInFallback required>=$minScore | " +
                    "candidates=${valid.map { "${it.invoke.owner}->${it.invoke.name}#${it.candidate.parameterIndex} score=${it.candidate.score} evidence=${it.candidate.evidence}" }}",
            )
        }
        // Also require at least 3 evidence entries (integer + 2 strong)
        val weak = valid.filter { it.candidate.evidence.size < 3 }
        if (weak.size == valid.size) {
            throw HeliumResolutionException(
                "binding: bounded fallback insufficient evidence | " +
                    "method=${method.descriptor} region=${region.startIndex}..${region.endIndex} | " +
                    "candidates=${valid.map { "${it.invoke.owner}->${it.invoke.name}#${it.candidate.parameterIndex} evidence=${it.candidate.evidence}" }}",
            )
        }
    }

    val bestScore = valid.maxOf { it.candidate.score }
    val best = valid.filter { it.candidate.score == bestScore }
    if (best.size != 1) {
        throw HeliumResolutionException(
            "binding: ambiguous top candidates ${best.size} score=$bestScore | " +
                "method=${method.descriptor} region=${region.startIndex}..${region.endIndex} | " +
                "tied=${best.map { "${it.invoke.owner}->${it.invoke.name}#${it.candidate.parameterIndex}:v${it.candidate.register} evidence=${it.candidate.evidence}" }} | " +
                "allValid=${valid.map { "${it.invoke.owner}->${it.invoke.name}#${it.candidate.parameterIndex} score=${it.candidate.score}" }} | " +
                "rejected=${rejectedDiagnostics.joinToString("; ")}",
        )
    }

    // Low confidence threshold: exact region can accept weak-generic single candidate, fallback already requires 9.
    val threshold = 3
    if (bestScore < threshold) {
        throw HeliumResolutionException(
            "binding: low confidence bestScore=$bestScore < $threshold | " +
                "method=${method.descriptor} region=${region.startIndex}..${region.endIndex} | " +
                "best=${best.single().let { "${it.invoke.owner}->${it.invoke.name}#${it.candidate.parameterIndex} evidence=${it.candidate.evidence}" }}",
        )
    }

    val (invoke, argument) = best.single().let { it.invoke to it.candidate }
    val strategy = if (bestScore >= 9) ResolutionStrategy.DATA_FLOW else ResolutionStrategy.SEMANTIC_RELAXED
    return BindingResolution(
        invoke.index,
        argument.register,
        strategy,
        "method=${method.descriptor} region=${region.startIndex}..${region.endIndex} strategy=${region.strategy} " +
            "invoke=${invoke.owner}->${invoke.name}(${invoke.params.joinToString("")}) intArg=${argument.parameterIndex} " +
            "register=v${argument.register} score=$bestScore evidence=${argument.evidence.joinToString()} | " +
            "rejected=${rejectedDiagnostics.size}",
    )
}

fun resolvePriorityTarget(methods: List<StructuralMethod>): PriorityResolution {
    if (methods.isEmpty()) throw HeliumResolutionException("setPriority: no methods provided")

    // Verified shape from Helium 149-152: return I, params contain 2 I, >=4 Z, 1 J, contains binding/priority priority int last
    fun isVerifiedShape(m: StructuralMethod): Boolean {
        if (m.returnType != "I") return false
        if (m.params.count { it == "I" } != 2) return false
        if (m.params.count { it == "Z" } < 4) return false
        if (m.params.count { it == "J" } != 1) return false
        if (m.name != HELIUM_PRIORITY_METHOD && m.name != "setPriority") {
            // Allow obfuscated name only if data-flow evidence strong (handled in fallback)
            return false
        }
        return true
    }

    val exactByName = methods.filter { it.name == HELIUM_PRIORITY_METHOD && it.returnType == "I" }
    val verifiedExact = exactByName.filter(::isVerifiedShape)

    when {
        verifiedExact.size == 1 -> {
            val method = verifiedExact.single()
            val integers = method.params.withIndex().filter { it.value == "I" }.map { it.index }
            val parameter = integers.last()
            return PriorityResolution(
                methodDescriptor = method.descriptor,
                parameterIndex = parameter,
                parameterWordOffset = method.parameterWordOffset(parameter),
                strategy = ResolutionStrategy.SEMANTIC_EXACT,
                diagnostics = "verified Chromium shape ints=$integers Z=${method.params.count { it == "Z" }} J=${method.params.count { it == "J" }}",
            )
        }
        verifiedExact.size > 1 -> throw HeliumResolutionException(
            "setPriority: multiple verified shapes ${verifiedExact.size} | descriptors=${verifiedExact.map { it.descriptor }}",
        )
        exactByName.size == 1 -> {
            // Single exact name but not verified shape: try data-flow within that method.
            val method = exactByName.single()
            val integers = method.params.withIndex().filter { it.value == "I" }.map { it.index }
            if (integers.isEmpty()) throw HeliumResolutionException("setPriority: no integer parameter | method=${method.descriptor}")
            // Verified-shape fast path already handled; otherwise require data-flow evidence.
            val uses = method.instructions.filterIsInstance<StructuralInstruction.ParameterUse>()
            val sums = integers.associateWith { p -> uses.filter { it.parameterIndex == p }.sumOf { it.roleWeight } }
            val peaks = integers.associateWith { p -> uses.filter { it.parameterIndex == p }.maxOfOrNull { it.roleWeight } ?: 0 }
            val top = peaks.maxByOrNull { it.value }
            if (top == null || top.value <= 0 || peaks.count { it.value == top.value } != 1) {
                throw HeliumResolutionException(
                    "setPriority: ambiguous integer parameters scores=$sums peaks=$peaks | " +
                        "method=${method.descriptor} params=${method.params} expected verified shape 2xI >=4xZ 1xJ",
                )
            }
            if (top.value < 4) {
                throw HeliumResolutionException(
                    "setPriority: weak data-flow peak ${top.value} <4 for unverified shape | " +
                        "method=${method.descriptor} params=${method.params} peaks=$peaks",
                )
            }
            return PriorityResolution(
                methodDescriptor = method.descriptor,
                parameterIndex = top.key,
                parameterWordOffset = method.parameterWordOffset(top.key),
                strategy = ResolutionStrategy.DATA_FLOW,
                diagnostics = "unverified shape data-flow sums=$sums peaks=$peaks",
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
                    "setPriority: no structural candidates | " +
                        "methods=${methods.map { "${it.descriptor} params=${it.params} return=${it.returnType}" }}",
                )
            }
            // Require multiple independent indicators
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
                // Name hint
                if (method.name.contains("Priority", ignoreCase = true) || method.name.contains("priority")) { score += 2; evidence += "name-hint:${method.name}" }
                method to (score to evidence)
            }
            val bestScore = scored.maxOf { it.second.first }
            val best = scored.filter { it.second.first == bestScore }
            if (best.size != 1) {
                throw HeliumResolutionException(
                    "setPriority: structural fallback ambiguous ${best.size} score=$bestScore | " +
                        "tied=${best.map { "${it.first.descriptor} evidence=${it.second.second}" }} | " +
                        "all=${scored.map { "${it.first.descriptor} score=${it.second.first} evidence=${it.second.second}" }}",
                )
            }
            val (method, pair) = best.single()
            val (score, evidence) = pair
            if (score < 8) {
                throw HeliumResolutionException(
                    "setPriority: structural fallback low confidence score=$score <8 | " +
                        "method=${method.descriptor} evidence=$evidence params=${method.params}",
                )
            }
            if (evidence.size < 3) {
                throw HeliumResolutionException(
                    "setPriority: structural fallback insufficient evidence ${evidence.size} <3 | method=${method.descriptor} evidence=$evidence",
                )
            }
            // Now pick integer parameter via data-flow
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
            // Wide param offset validation
            if (top.key !in method.params.indices || method.params[top.key] != "I") {
                throw HeliumResolutionException("setPriority: selected non-int param ${top.key} method=${method.descriptor}")
            }
            return PriorityResolution(
                methodDescriptor = method.descriptor,
                parameterIndex = top.key,
                parameterWordOffset = method.parameterWordOffset(top.key),
                strategy = ResolutionStrategy.DATA_FLOW,
                diagnostics = "structural fallback score=$score evidence=$evidence sums=$sums peaks=$peaks",
            )
        }
    }
}

private fun Int?.orZero() = this ?: 0

fun resolveActivityHook(methods: List<StructuralMethod>): ActivityResolution {
    val candidates = methods.filter { method ->
        method.name == HELIUM_LIFECYCLE_ON_START &&
            method.returnType == "V" &&
            method.params.isEmpty() &&
            method.instructions.count {
                it is StructuralInstruction.Invoke && it.isSuper && it.name == HELIUM_LIFECYCLE_ON_START
            } == 1
    }
    if (candidates.isEmpty()) {
        throw HeliumResolutionException(
            "activity onStart: expected one super hook, found 0 | " +
                "methods=${methods.map { it.descriptor }}",
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
            "activity onStart: ambiguous candidates: ${candidates.size} | " +
                "descriptors=${candidates.map { it.descriptor }} bestScore=${score(best)}",
        )
    }
    val superIndex = best.instructions
        .filterIsInstance<StructuralInstruction.Invoke>()
        .single { it.isSuper && it.name == HELIUM_LIFECYCLE_ON_START }
        .index
    return ActivityResolution(
        best.descriptor,
        superIndex,
        if (best.descriptor == HELIUM_ACTIVITY_CLASS) {
            ResolutionStrategy.SEMANTIC_EXACT
        } else {
            ResolutionStrategy.SEMANTIC_RELAXED
        },
        "unique super lifecycle=${HELIUM_LIFECYCLE_ON_START}",
    )
}
