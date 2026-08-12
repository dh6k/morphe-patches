package app.morphe.patches.quetta

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch

internal const val QUETTA_PACKAGE = "net.quetta.browser"
internal const val QUETTA_OFFICIAL_PACKAGE = "net.quetta.browser.official"
internal val QUETTA_EXTENSION_IDS = setOf("nnedfbcpeenmccjbdcnlnhogapndfeoa", "gadlcodpkkelmagfhkldjlobfncbkbmd")
internal val QUETTA_ASSETS = setOf("q30_from_quetta.crx", "q30_from_translator.crx")
internal const val QUETTA_BLOCK_INSTRUCTION = "return-void"
internal val quettaCompatibilities = listOf(
    Compatibility(name = "Quetta Browser", packageName = QUETTA_PACKAGE, apkFileType = ApkFileType.APK, targets = listOf(AppTarget(version = null, isExperimental = true))),
    Compatibility(name = "Quetta Browser Official", packageName = QUETTA_OFFICIAL_PACKAGE, apkFileType = ApkFileType.APK, targets = listOf(AppTarget(version = null, isExperimental = true))),
)

@Suppress("unused")
val removeQuettaExtensionsPatch = bytecodePatch(
    name = "Block Quetta bundled extension installation",
    description = "Blocks bundled extension installation/reinstallation on arm64-v8a APKs (framework does not enforce ABI). Does not remove copies already present in existing profiles.",
    default = true,
) {
    compatibleWith(*quettaCompatibilities.toTypedArray())
    execute {
        val installer = Fingerprint(returnType = "V", strings = (QUETTA_EXTENSION_IDS + QUETTA_ASSETS).toList())
        require(installer.method.implementation != null) { "Quetta installer fingerprint has no implementation" }
        installer.method.addInstructions(0, QUETTA_BLOCK_INSTRUCTION)
    }
}
