/*
 * Brave NTP four-column tiles (issue #24).
 *
 * Brave's NTP keeps the pinned and most-visited tiles in
 * MostVisitedTilesLayout, which switches between two layouts based on one
 * boolean instance flag:
 *
 *   flag == false -> TilesLinearLayout: every tile in one row inside a
 *                    HorizontalScrollView, so tiles past the screen edge have
 *                    to be scrolled into view sideways.
 *   flag == true  -> GridLayout with a hardcoded 4 columns, laid out downwards.
 *
 * Brave sets that flag while building the NTP, and only when the
 * `brave.new_tab_page.show_background_image` pref is OFF:
 *
 *   pref = getBoolean("brave.new_tab_page.show_background_image")
 *   if (!pref) tilesLayout.setGridMode(true)
 *
 * So turning on a background image is what collapses the tiles into a single
 * scrollable row.
 *
 * This patch neutralizes that one skip branch so the flag is always written as
 * true. It deliberately does NOT touch the measure pass that reads the flag:
 * rewriting the reader makes the value provably constant, and the reassembler
 * then folds away the surrounding four-column code (`const/4 v2, 4` and its
 * width guard disappear, leaving setColumnCount with a stale register).
 * Writing the flag instead leaves the reader and all of its code intact.
 */
package app.morphe.patches.brave

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.BytecodePatch
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

internal const val NTP_TILES_LAYOUT =
    "Lorg/chromium/chrome/browser/suggestions/tile/MostVisitedTilesLayout;"

/** Brave reads this pref to decide whether the four-column grid is allowed. */
internal const val NTP_BACKGROUND_IMAGE_PREF = "brave.new_tab_page.show_background_image"

/**
 * The single `iput-boolean <v>, <obj>, MostVisitedTilesLayout-><flag>:Z` that
 * writes the layout-mode flag. Brave R8-renames the owning class and the flag
 * itself, so identify the write structurally and anchor the enclosing method on
 * the pref literal it must also contain.
 */
internal object NtpTilesGridModeWriterFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf(NTP_BACKGROUND_IMAGE_PREF),
    custom = { method, _ ->
        val implementation = method.implementation
        if (implementation != null) {
            implementation.instructions.any { isGridModeWrite(it) }
        } else {
            false
        }
    },
)

/** `iput-boolean <src>, <obj>, MostVisitedTilesLayout-><any>:Z`. */
internal fun isGridModeWrite(instruction: Instruction): Boolean {
    if (instruction.opcode.name != "iput-boolean") return false
    val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference ?: return false
    return field.definingClass == NTP_TILES_LAYOUT && field.type == "Z"
}

/**
 * Locates the branch that skips the flag write, i.e. the `if-nez <reg>, <target>`
 * testing the pref result. Brave reads the pref into a register and immediately
 * branches on it, so the gate is the first branch after the instruction that
 * returns the pref value.
 *
 * Anchoring on "nearest preceding branch" is not enough: this method also contains
 * an `if-nez` on the `instance-of MostVisitedTilesLayout` assertion, which sits
 * much closer to the write but guards something else entirely.
 *
 * Returns the index of the gate branch, or null when the shape is unexpected.
 */
internal fun findSkipBranchIndex(instructions: List<Instruction>, writeIndex: Int): Int? {
    if (instructions.isEmpty() || writeIndex !in 1..instructions.size) return null

    // The pref read result arrives with a `move-result`; the gate tests that
    // register immediately afterwards. Walk back over move-result sites and take
    // the first branch that follows one, rather than stopping at the first
    // move-result seen.
    for (index in (writeIndex - 1) downTo maxOf(0, writeIndex - 32)) {
        if (instructions[index].opcode.name != "move-result") continue
        for (next in (index + 1)..writeIndex) {
            val candidate = instructions[next]
            if (candidate.opcode.name != "if-nez") continue
            if (candidate !is OffsetInstruction) continue
            if (candidate !is OneRegisterInstruction) continue
            if (candidate.codeOffset <= 0) continue
            return next
        }
    }
    return null
}

/**
 * Neutralizes the skip branch.
 *
 * `if-nez` is a 2-byte format-21t instruction and `nop` is a 2-byte format-10x,
 * so the swap is byte-identical and every branch offset in the method — including
 * the ~6300-byte factory method this lives in — stays valid.
 */
internal fun forceGridModeWriteSmali(): String = "nop"

private fun ntpFourColumnCompatibilities() = listOf(
    Compatibility(
        name = "Brave Browser",
        packageName = "com.brave.browser",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0xFF4500,
        targets = listOf(AppTarget(version = null, isExperimental = true)),
    ),
    Compatibility(
        name = "Brave Beta",
        packageName = "com.brave.browser_beta",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0xFF4500,
        targets = listOf(AppTarget(version = null, isExperimental = true)),
    ),
    Compatibility(
        name = "Brave Nightly",
        packageName = "com.brave.browser_nightly",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0xFF4500,
        targets = listOf(AppTarget(version = null, isExperimental = true)),
    ),
)

/**
 * Experimental version-unpinned patch (issue #24): keeps Brave's NTP pinned and
 * most-visited tiles in a four-column grid that extends downwards, the layout
 * Brave already uses when no background image is set, and keeps it while a
 * background image is enabled. Neutralizes the `brave.new_tab_page.show_background_image`
 * gate in the NTP builder that otherwise forces the tiles into a single
 * horizontally scrolling row. Default off.
 */
@Suppress("unused")
val braveNtpFourColumnTilesPatch: BytecodePatch = bytecodePatch(
    name = "Brave NTP four-column tiles",
    description = "Experimental version-unpinned patch (issue #24): keeps the new-tab " +
        "pinned and most-visited tiles in a four-column grid that extends downwards, " +
        "the same layout Brave uses with no background image, and keeps that layout " +
        "when a background image is enabled. Neutralizes the " +
        "\"brave.new_tab_page.show_background_image\" gate in the NTP builder that " +
        "otherwise forces the tiles into a single horizontally scrolling row. " +
        "Default off.",
    default = false,
) {
    compatibleWith(*ntpFourColumnCompatibilities().toTypedArray())

    execute {
        val method = NtpTilesGridModeWriterFingerprint.methodOrNull
            ?: error("NTP builder writing the MostVisitedTilesLayout layout-mode flag not found")

        // Resolve the mutable method so the indexes below address the same list
        // that gets mutated. Indexes taken from a read-only view of the original
        // dex do not line up: the builder's internal list also carries switch
        // payloads, so addressing it with a view index throws IndexOutOfBounds.
        val owner = NtpTilesGridModeWriterFingerprint.originalClassDef.type
        val mutableMethod = mutableClassDefBy(owner).methods.first {
            it.name == method.name && it.parameterTypes == method.parameterTypes
        }
        val mutableImplementation = mutableMethod.implementation
            ?: error("NTP builder method ${method.name} has no implementation")

        val instructions = mutableImplementation.instructions.toList()
        val writeIndexes = instructions.withIndex()
            .filter { (_, instruction) -> isGridModeWrite(instruction) }
            .map { (index, _) -> index }
        if (writeIndexes.size != 1) {
            error(
                "expected exactly 1 layout-mode flag write in $owner->${method.name}, " +
                    "found ${writeIndexes.size}",
            )
        }

        val writeIndex = writeIndexes.first()
        val branchIndex = findSkipBranchIndex(instructions, writeIndex)
            ?: error(
                "no forward if-nez skip branch guarding the layout-mode flag write " +
                    "at #$writeIndex in $owner->${method.name}",
            )

        // Replace in place rather than remove+add: the builder's internal list also
        // carries switch payloads, so removing by an index taken from the readable
        // instruction view throws IndexOutOfBounds. In-place replacement keeps the
        // list length and every existing branch target intact.
        mutableMethod.replaceInstruction(branchIndex, forceGridModeWriteSmali())

        println(
            "[NTP Four Column] Forced four-column grid by neutralizing the " +
                "show_background_image skip branch at #$branchIndex (if-nez) before the " +
                "layout-mode flag write at #$writeIndex; the measure pass is untouched",
        )
    }
}
