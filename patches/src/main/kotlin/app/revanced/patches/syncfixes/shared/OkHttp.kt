package app.revanced.patches.syncfixes.shared

import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.extensions.InstructionExtensions.replaceInstruction
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal const val OKHTTP_CLIENT = "Lokhttp3/OkHttpClient;"
internal const val OKHTTP_CLIENT_BUILDER = "Lokhttp3/OkHttpClient\$Builder;"

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
 * `builder.build()` whose result is stored in that field, and replaces that call with [installHook], a static
 * `(OkHttpClient.Builder) -> OkHttpClient` in the extension.
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
        val build = instructions.subList(0, store).indexOfLast { instruction ->
            instruction is ReferenceInstruction && (instruction.reference as? MethodReference)?.let {
                it.definingClass == OKHTTP_CLIENT_BUILDER && it.name == "build"
            } == true
        }
        if (build < 0) throw PatchException("OkHttpHelper.$name: no builder.build() before $client is set")
        val register = when (val call = instructions[build]) {
            is FiveRegisterInstruction -> call.registerC
            is RegisterRangeInstruction -> call.startRegister
            else -> throw PatchException("Unexpected build() call: ${call.opcode}")
        }
        replaceInstruction(
            build,
            "invoke-static/range { v$register .. v$register }, $installHook($OKHTTP_CLIENT_BUILDER)$OKHTTP_CLIENT",
        )
    }
}
