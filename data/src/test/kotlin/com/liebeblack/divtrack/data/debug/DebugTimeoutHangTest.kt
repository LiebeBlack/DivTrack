package com.liebeblack.divtrack.data.debug

import java.io.File
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test

/** TEMPORAL: aísla el cuelgue de withTimeoutOrNull + UnconfinedTestDispatcher. */
class DebugTimeoutHangTest {

    private val externalDispatcher = UnconfinedTestDispatcher()

    private fun mark(text: String) {
        val file = File(System.getProperty("user.dir")).absoluteFile.resolve("debug-markers.txt")
        file.appendText(text + "\n")
        println("DEBUG-MARK: $text -> ${file.absolutePath}")
    }

    @Test
    fun `A timeout con dispatcher externo`() = runTest {
        mark("A-start")
        val result = withContext(externalDispatcher) {
            withTimeoutOrNull(30_000L) {
                mark("A-block")
                42
            }
        }
        mark("A-end:$result")
    }

    @Test
    fun `B timeout con scheduler del test`() = runTest {
        mark("B-start")
        val result = withContext(UnconfinedTestDispatcher(testScheduler)) {
            withTimeoutOrNull(30_000L) {
                mark("B-block")
                42
            }
        }
        mark("B-end:$result")
    }

    @Test
    fun `C timeout sin withContext`() = runTest {
        mark("C-start")
        val result = withTimeoutOrNull(30_000L) {
            mark("C-block")
            42
        }
        mark("C-end:$result")
    }
}
