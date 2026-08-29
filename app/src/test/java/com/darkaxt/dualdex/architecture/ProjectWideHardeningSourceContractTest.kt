package com.darkaxt.dualdex.architecture

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectWideHardeningSourceContractTest {
    @Test
    fun `guide retry fault is production owned inert by default and packaged test uses production retry`() {
        val root = repositoryRoot()
        val fault = root.resolve("app/src/main/java/com/darkaxt/dualdex/setup/GuideLoadFault.kt")
        val coordinator = read(root, "app/src/main/java/com/darkaxt/dualdex/setup/RetroArchSetupCoordinator.kt")
        val application = read(root, "app/src/main/java/com/darkaxt/dualdex/DualDexApplication.kt")
        val activity = read(root, "app/src/main/java/com/darkaxt/dualdex/MainActivity.kt")
        val runner = read(root, "app/src/androidTest/java/com/darkaxt/dualdex/QaAndroidJUnitRunner.kt")
        val packaged = read(root, "app/src/androidTest/java/com/darkaxt/dualdex/PackagedAcceptanceInstrumentedTest.kt")

        assertTrue("GuideLoadFault must be production-owned", Files.isRegularFile(fault))
        assertTrue(read(fault, "").contains("object NoGuideLoadFault"))
        assertTrue(coordinator.contains("guideLoadFault: GuideLoadFault = NoGuideLoadFault"))
        assertTrue(coordinator.contains("guideLoadFault.beforeLoad(entry)"))
        assertTrue(application.contains("protected open fun guideLoadFault(): GuideLoadFault = NoGuideLoadFault"))
        assertTrue(activity.contains("NativeSetupRoute.RETRY_GUIDE -> application.retroArchSetup?.retryGuideLoad()"))
        assertFalse("Packaged acceptance must not clear a test-only guide failure", packaged.contains("clearGuideFailure()"))
        assertTrue(runner.contains("fun prepareGuideFixture()"))
        assertTrue(runner.contains("fun isGuideFixtureIndexed(): Boolean"))
        assertTrue(runner.contains("fun armGuideFailure()"))
        assertTrue(runner.contains("AtomicBoolean(false)"))
        assertTrue(runner.contains("if (!armed.get()) return null"))
        assertFalse(runner.contains("QaGuideLoadFault {"))
        assertTrue(
            runner.contains(
                "    fun resetGuideFailure() {\n        guideLoadFault.reset()\n        guideStatus.set(RetroArchStatus.Contentless)\n    }",
            ),
        )
        assertTrue(packaged.contains("waitFor(\"exact guide fixture indexed\")"))
        assertTrue(packaged.contains("application.armGuideFailure()"))
        assertTrue(packaged.contains("assertFalse(application.guideFailureArmed())"))
        assertTrue(packaged.contains("assertEquals(listOf(\"FAILED\", \"LOADING\", \"FAILED\"), retryTransition)"))
        assertTrue(packaged.contains("assertEquals(2, application.guideLoadAttempts())"))
        assertTrue(packaged.contains("} finally {\n                application.resetGuideFailure()\n            }"))
        val terminalFailure = packaged.indexOf("waitFor(\"production retry terminal\")")
        val cleanup = packaged.indexOf("application.resetGuideFailure()")
        assertTrue("Guide fault cleanup must follow terminal FAILED observation", terminalFailure >= 0 && cleanup > terminalFailure)
    }

    @Test
    fun `overlay picker uses one canonical dispatcher from service through both activity delivery paths`() {
        val root = repositoryRoot()
        val request = read(root, "app/src/main/java/com/darkaxt/dualdex/setup/SetupPickerRequest.kt")
        val picker = read(root, "app/src/main/java/com/darkaxt/dualdex/setup/SetupDocumentPicker.kt")
        val registry = root.resolve("app/src/main/java/com/darkaxt/dualdex/setup/SetupPickerActivityResultRegistry.kt")
        val application = read(root, "app/src/main/java/com/darkaxt/dualdex/DualDexApplication.kt")
        val service = read(root, "app/src/main/java/com/darkaxt/dualdex/overlay/FloatingCompanionService.kt")
        val activity = read(root, "app/src/main/java/com/darkaxt/dualdex/MainActivity.kt")
        val instrumentation = read(root, "app/src/androidTest/java/com/darkaxt/dualdex/OverlayPickerDeliveryInstrumentedTest.kt")

        assertTrue(request.contains("class SetupPickerRequestDispatcher"))
        assertTrue(request.contains("interface SetupPickerDispatch"))
        assertTrue(Files.isRegularFile(registry))
        assertTrue(picker.contains("SetupPickerActivityResultRegistry"))
        assertTrue(picker.contains("SetupPickerDispatch"))
        assertTrue(picker.contains("openConfigTree() = configLauncher.launch(RETROARCH_INITIAL_URI)"))
        assertTrue(picker.contains("openRomTree() = romLauncher.launch(null)"))
        assertTrue(application.contains("setupPickerActivityResultRegistry"))
        assertTrue(service.contains("OverlaySetupRouteHandler"))
        assertTrue(service.contains("setupRouteHandler.handleNativeRoute(route)"))
        assertTrue(activity.contains("SetupDocumentPicker("))
        assertTrue(activity.contains("setupPickerActivityResultRegistry(this)"))
        assertTrue(instrumentation.contains("OverlaySetupRouteHandler"))
        assertTrue(instrumentation.contains("pickerRegistrationCount()"))
        assertTrue(instrumentation.contains("deliverLatestPickerResult"))
        assertTrue(activity.contains("setupPickerDispatcher.consume(intent)"))
        assertTrue("Both cold create and onNewIntent must dispatch", activity.split("setupPickerDispatcher.consume(intent)").size - 1 == 2)
    }

    private fun read(root: Path, relative: String): String =
        String(Files.readAllBytes(root.resolve(relative)), Charsets.UTF_8)

    private fun repositoryRoot(): Path {
        var candidate: Path? = Path.of("").toAbsolutePath().normalize()
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle.kts"))) return candidate
            candidate = candidate.parent
        }
        error("Could not locate the repository root from ${Path.of("").toAbsolutePath()}")
    }
}
