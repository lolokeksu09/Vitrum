package app.rayclient

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Device-only gate for the future Xray Core Manager.
 *
 * It compares the current nativeLibraryDir launch path with a staged copy
 * started through Android's system linker. It deliberately does not alter
 * RayVpnService, the active TUN, or the installed Xray binary.
 */
@RunWith(AndroidJUnit4::class)
class XrayRuntimeProbeTest {
    @Test
    fun currentAndStagedBinariesStartOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val installed = File(context.applicationInfo.nativeLibraryDir, "libxray.so")
        assertTrue("Packaged Xray binary is missing: ${installed.absolutePath}", installed.isFile)
        assertTrue("Packaged Xray binary is not executable", installed.canExecute())

        val direct = runCommand(listOf(installed.absolutePath, "version"))
        assertEquals("nativeLibraryDir launch failed: ${direct.output}", 0, direct.exitCode)
        assertTrue("Unexpected Xray version: ${direct.output}", direct.output.contains(XRAY_VERSION))

        if (Build.SUPPORTED_64_BIT_ABIS.none { it == "arm64-v8a" }) {
            throw AssertionError("This probe is intended for the app's ARM64 runtime.")
        }

        val linker = File("/system/bin/linker64")
        assertTrue("Android 64-bit system linker is missing", linker.canExecute())

        val staged = File(context.cacheDir, "xray-runtime-probe/libxray.so")
        staged.parentFile?.mkdirs()
        installed.copyTo(staged, overwrite = true)
        check(staged.setExecutable(true, true)) { "Could not mark staged Xray binary executable" }

        val viaLinker = runCommand(listOf(linker.absolutePath, staged.absolutePath, "version"))
        assertEquals("Staged linker launch failed: ${viaLinker.output}", 0, viaLinker.exitCode)
        assertTrue("Staged binary reported an unexpected version: ${viaLinker.output}",
            viaLinker.output.contains(XRAY_VERSION))
    }

    private fun runCommand(command: List<String>): CommandResult {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw AssertionError("Timed out after 15 seconds: ${command.joinToString(" ")}")
        }
        return CommandResult(process.exitValue(), process.inputStream.bufferedReader().use { it.readText() })
    }

    private data class CommandResult(val exitCode: Int, val output: String)
}
