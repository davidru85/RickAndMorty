package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import org.gradle.testfixtures.ProjectBuilder

/** TEST-UNIT-018: inspecting an APK must not hash the entire SDK installation. */
class VerifySdkLevelsInputsTest {
    @Test
    fun `the selected tool is the only SDK input`() {
        val root = kotlin.io.path.createTempDirectory("sdk-inputs").toFile()
        val sdk = File(root, "sdk")
        val tool = File(sdk, "build-tools/37.0.0/aapt2")
        tool.parentFile.mkdirs()
        tool.writeText("fixture tool")
        File(sdk, "ndk/unrelated.bin").apply { parentFile.mkdirs(); writeText("unrelated installation") }
        val apk = File(root, "app.apk").apply { writeText("fixture APK") }
        val project = ProjectBuilder.builder().withProjectDir(root).build()
        val task = project.tasks.register("verifySdk", VerifySdkLevelsTask::class.java).get()
        task.sdkDirectory.set(sdk)
        task.apk.set(apk)
        task.report.set(File(root, "report.txt"))
        assertEquals(setOf(apk, tool), task.inputs.files.files)
    }
}
