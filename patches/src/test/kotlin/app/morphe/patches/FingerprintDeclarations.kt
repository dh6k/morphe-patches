package app.morphe.patches

import app.morphe.patcher.Fingerprint

/**
 * Morphe Patcher 1.10.0 made [Fingerprint]'s declaringClass, name, returnType and
 * parameters internal, and its deprecated accessors raise DeprecationLevel.ERROR.
 * A test that pins a fingerprint's declared shape therefore reads the backing
 * field off the base class.
 *
 * The field is read off [Fingerprint] rather than the runtime class because
 * fingerprints are usually `object` declarations, so the fields live on the base
 * class while javaClass is the anonymous subclass.
 */
internal fun Fingerprint.declaredField(name: String): Any? =
    Fingerprint::class.java.getDeclaredField(name).apply { isAccessible = true }.get(this)