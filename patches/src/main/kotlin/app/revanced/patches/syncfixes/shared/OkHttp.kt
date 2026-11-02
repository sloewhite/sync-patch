package app.revanced.patches.syncfixes.shared

import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal const val OKHTTP_CLIENT = "Lokhttp3/OkHttpClient;"

/** True if this is a call to a static, no-argument method returning the OkHttp client. */
internal fun Instruction.isClientGetterCall(): Boolean {
    if (opcode != Opcode.INVOKE_STATIC && opcode != Opcode.INVOKE_STATIC_RANGE) return false
    val ref = (this as? ReferenceInstruction)?.reference as? MethodReference ?: return false
    return ref.returnType == OKHTTP_CLIENT && ref.parameterTypes.isEmpty()
}

internal fun Instruction.fieldReference() =
    ((this as? ReferenceInstruction)?.reference as? FieldReference)?.toString()

/**
 * Follows the OkHttpHelper client getter called from [caller] to the static field it returns, then to the
 * method that stores the client in that field, and wraps the stored value with [installHook], a static
 * `(OkHttpClient) -> OkHttpClient` in the extension. Working on the stored value (not on a `builder.build()` call) keeps
 * it working when another patch has already changed how the client is built.
 *
 * Sync's OkHttpHelper has several clients (Volley uses one, Glide another), so each is reached through
 * whoever uses it instead of by obfuscated name.
 */
internal fun BytecodePatchContext.hookClientBuilder(caller: MutableMethod, installHook: String) {
    val getterRef = (caller.instructions.first { it.isClientGetterCall() } as ReferenceInstruction)
        .reference as MethodReference
    val helper = proxy(classes.first { it.type == getterRef.definingClass }).mutableClass
    if (helper.sourceFile != "OkHttpHelper.java") {
        throw PatchException("OkHttp client getter is not in OkHttpHelper: ${getterRef.definingClass}")
    }
    val getter = helper.methods.single {
        it.name == getterRef.name && it.parameterTypes.isEmpty() && it.returnType == OKHTTP_CLIENT
    }
    // The getter may lazily create the client, in which case it reads the field more than once.
    val client = getter.instructions.filter { it.opcode == Opcode.SGET_OBJECT }
        .map { it.fieldReference() }.distinct().single()

    val builder = helper.methods.singleOrNull { method ->
        method.implementation != null && method.instructions.any {
            it.opcode == Opcode.SPUT_OBJECT && it.fieldReference() == client
        }
    } ?: throw PatchException("No single method in OkHttpHelper sets $client")

    builder.apply {
        val store = instructions.indexOfFirst { it.opcode == Opcode.SPUT_OBJECT && it.fieldReference() == client }
        val register = (instructions[store] as OneRegisterInstruction).registerA
        addInstructions(
            store,
            """
                invoke-static/range { v$register .. v$register }, $installHook($OKHTTP_CLIENT)$OKHTTP_CLIENT
                move-result-object v$register
            """,
        )
    }
}
