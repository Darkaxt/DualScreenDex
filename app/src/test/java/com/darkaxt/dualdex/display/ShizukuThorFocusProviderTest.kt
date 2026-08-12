package com.darkaxt.dualdex.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuThorFocusProviderTest {
    @Test
    fun deadBinderIsUnavailableWithoutCallingAnyOtherApi() {
        val gateway = FakeShizukuGateway(alive = false)
        val provider = ShizukuThorFocusProvider(gateway)

        assertEquals(
            ShizukuThorFocusState.TerminalFailure(ShizukuThorFocusFailure.BINDER_UNAVAILABLE),
            provider.prepare(userInitiated = true),
        )
        assertEquals(listOf("ping"), gateway.events)
    }

    @Test
    fun grantedPermissionBindsUserServiceExactlyOnce() {
        val gateway = FakeShizukuGateway(permission = PERMISSION_GRANTED, backendUid = 2000)
        val provider = ShizukuThorFocusProvider(gateway)

        assertEquals(ShizukuThorFocusState.Binding(backendUid = 2000), provider.prepare(true))
        assertEquals(ShizukuThorFocusState.Binding(backendUid = 2000), provider.prepare(true))
        assertEquals(1, gateway.binds)
    }

    @Test
    fun explicitUndecidedPermissionRequestIsSingleFlight() {
        val gateway = FakeShizukuGateway(permission = PERMISSION_DENIED)
        val provider = ShizukuThorFocusProvider(gateway)

        assertEquals(ShizukuThorFocusState.PermissionRequested, provider.prepare(true))
        assertEquals(ShizukuThorFocusState.PermissionRequested, provider.prepare(true))
        assertEquals(1, gateway.permissionRequests)
    }

    @Test
    fun failedPermissionRequestDoesNotLeaveAFalseInFlightState() {
        val gateway = FakeShizukuGateway(
            permission = PERMISSION_DENIED,
            failPermissionRequest = true,
        )
        val provider = ShizukuThorFocusProvider(gateway)

        assertEquals(
            ShizukuThorFocusState.TerminalFailure(ShizukuThorFocusFailure.API_FAILURE),
            provider.prepare(userInitiated = true),
        )
        assertEquals(
            ShizukuThorFocusState.TerminalFailure(ShizukuThorFocusFailure.API_FAILURE),
            provider.prepare(userInitiated = true),
        )
    }

    @Test
    fun newlyReceivedBinderInvalidatesAnOldPermissionRequest() {
        val gateway = FakeShizukuGateway(permission = PERMISSION_DENIED)
        val provider = ShizukuThorFocusProvider(gateway)
        provider.prepare(userInitiated = true)

        gateway.receiveBinder()
        provider.prepare(userInitiated = true)

        assertEquals(2, gateway.permissionRequests)
    }

    @Test
    fun deniedPermissionIsATypedTerminalFailureAndDoesNotPromptAgain() {
        val gateway = FakeShizukuGateway(permission = PERMISSION_DENIED)
        val provider = ShizukuThorFocusProvider(gateway)
        provider.prepare(userInitiated = true)

        gateway.deliverPermissionResult(PERMISSION_DENIED)

        assertEquals(
            ShizukuThorFocusState.TerminalFailure(ShizukuThorFocusFailure.PERMISSION_DENIED),
            provider.state,
        )
        assertEquals(
            ShizukuThorFocusState.TerminalFailure(ShizukuThorFocusFailure.PERMISSION_DENIED),
            provider.prepare(userInitiated = true),
        )
        assertEquals(1, gateway.permissionRequests)
    }

    @Test
    fun permanentPermissionDenialNeverPrompts() {
        val gateway = FakeShizukuGateway(
            permission = PERMISSION_DENIED,
            showRationale = true,
        )
        val provider = ShizukuThorFocusProvider(gateway)

        assertEquals(
            ShizukuThorFocusState.TerminalFailure(
                ShizukuThorFocusFailure.PERMISSION_PERMANENTLY_DENIED,
            ),
            provider.prepare(userInitiated = true),
        )
        assertEquals(0, gateway.permissionRequests)
    }

    @Test
    fun passivePreparationNeverOpensPermissionUi() {
        val gateway = FakeShizukuGateway(permission = PERMISSION_DENIED)
        val provider = ShizukuThorFocusProvider(gateway)

        assertEquals(ShizukuThorFocusState.PermissionRequired, provider.prepare(false))
        assertEquals(0, gateway.permissionRequests)
    }

    @Test
    fun binderDeathDisconnectsTheServiceWithoutAnotherApiCall() {
        val gateway = FakeShizukuGateway(permission = PERMISSION_GRANTED)
        val provider = ShizukuThorFocusProvider(gateway)
        provider.prepare(userInitiated = true)
        gateway.connect(FakePrivilegedService())
        gateway.events.clear()

        gateway.die()

        assertEquals(
            ShizukuThorFocusState.TerminalFailure(ShizukuThorFocusFailure.BINDER_DIED),
            provider.state,
        )
        assertFalse(provider.readMode() is ThorFocusPrivilegedResult.Success)
        assertEquals(emptyList<String>(), gateway.events)
    }

    @Test
    fun uidZeroSuiUsesTheSamePermissionAndUserServicePathWithoutRawSu() {
        val gateway = FakeShizukuGateway(permission = PERMISSION_GRANTED, backendUid = 0)
        val provider = ShizukuThorFocusProvider(gateway)

        assertEquals(ShizukuThorFocusState.Binding(backendUid = 0), provider.prepare(true))
        assertEquals(listOf("ping", "permission", "uid", "bind"), gateway.events)
        assertTrue(gateway.events.none { it.contains("su", ignoreCase = true) })
    }

    @Test
    fun permissionGrantContinuesThroughTheSameUidZeroUserServicePath() {
        val gateway = FakeShizukuGateway(permission = PERMISSION_DENIED, backendUid = 0)
        val provider = ShizukuThorFocusProvider(gateway)
        provider.prepare(userInitiated = true)
        gateway.permission = PERMISSION_GRANTED

        gateway.deliverPermissionResult(PERMISSION_GRANTED)

        assertEquals(ShizukuThorFocusState.Binding(backendUid = 0), provider.state)
        assertEquals(1, gateway.permissionRequests)
        assertEquals(1, gateway.binds)
        assertTrue(gateway.events.none { it.contains("su", ignoreCase = true) })
    }

    @Test
    fun connectedUserServiceReadsAndWritesOnlyTypedModes() {
        val gateway = FakeShizukuGateway(permission = PERMISSION_GRANTED)
        val service = FakePrivilegedService(readValue = ThorFocusMode.TOP)
        val provider = ShizukuThorFocusProvider(gateway)
        provider.prepare(userInitiated = true)
        gateway.connect(service)

        assertEquals(ThorFocusPrivilegedResult.Success(ThorFocusMode.TOP), provider.readMode())
        assertEquals(ThorFocusPrivilegedResult.Success(true), provider.writeMode(ThorFocusMode.BOTTOM))
        assertEquals(listOf(ThorFocusMode.BOTTOM), service.writes)
        assertThrows(IllegalArgumentException::class.java) { provider.writeMode(3) }
    }

    @Test
    fun commandExecutorUsesOnlyFixedCommandsAndRequiresValidResults() {
        val runner = FakeThorFocusCommandRunner()
        val executor = ThorFocusCommandExecutor(runner)

        runner.nextResult = ThorFocusProcessResult(0, "1\n", "ignored but drained")
        assertEquals(ThorFocusMode.TOP, executor.readMode())
        assertEquals(ThorFocusCommand.read(), runner.commands.single())

        runner.nextResult = ThorFocusProcessResult(0, "", "")
        assertTrue(executor.writeMode(ThorFocusMode.BOTTOM))
        assertEquals(ThorFocusCommand.write(ThorFocusMode.BOTTOM), runner.commands.last())
        assertThrows(IllegalArgumentException::class.java) { executor.writeMode(3) }

        runner.nextResult = ThorFocusProcessResult(1, "0", "failed")
        assertThrows(IllegalStateException::class.java) { executor.readMode() }
        runner.nextResult = ThorFocusProcessResult(0, "3", "")
        assertThrows(IllegalStateException::class.java) { executor.readMode() }
    }

    private class FakeShizukuGateway(
        var alive: Boolean = true,
        var permission: Int = PERMISSION_DENIED,
        private val showRationale: Boolean = false,
        private val backendUid: Int = 2000,
        private val failPermissionRequest: Boolean = false,
    ) : ShizukuGateway {
        val events = mutableListOf<String>()
        var permissionRequests = 0
        var binds = 0
        private var binderReceivedListener: (() -> Unit)? = null
        private var binderDeadListener: (() -> Unit)? = null
        private var permissionResultListener: ((Int, Int) -> Unit)? = null
        private var connection: ShizukuUserServiceConnection? = null

        override fun pingBinder(): Boolean {
            events += "ping"
            return alive
        }

        override fun checkSelfPermission(): Int {
            events += "permission"
            return permission
        }

        override fun shouldShowRequestPermissionRationale(): Boolean {
            events += "rationale"
            return showRationale
        }

        override fun requestPermission(requestCode: Int) {
            events += "request:$requestCode"
            permissionRequests++
            if (failPermissionRequest) error("request failed")
        }

        override fun getUid(): Int {
            events += "uid"
            return backendUid
        }

        override fun bindUserService(connection: ShizukuUserServiceConnection) {
            events += "bind"
            binds++
            this.connection = connection
        }

        override fun addBinderReceivedListener(listener: () -> Unit) {
            binderReceivedListener = listener
        }

        override fun removeBinderReceivedListener(listener: () -> Unit) {
            if (binderReceivedListener === listener) binderReceivedListener = null
        }

        override fun addBinderDeadListener(listener: () -> Unit) {
            binderDeadListener = listener
        }

        override fun removeBinderDeadListener(listener: () -> Unit) {
            if (binderDeadListener === listener) binderDeadListener = null
        }

        override fun addPermissionResultListener(listener: (Int, Int) -> Unit) {
            permissionResultListener = listener
        }

        override fun removePermissionResultListener(listener: (Int, Int) -> Unit) {
            if (permissionResultListener === listener) permissionResultListener = null
        }

        fun connect(service: IThorFocusPrivilegedService) {
            connection?.onConnected(service)
        }

        fun die() {
            alive = false
            binderDeadListener?.invoke()
        }

        fun receiveBinder() {
            alive = true
            binderReceivedListener?.invoke()
        }

        fun deliverPermissionResult(result: Int) {
            permissionResultListener?.invoke(SHIZUKU_PERMISSION_REQUEST_CODE, result)
        }
    }

    private class FakePrivilegedService(
        private val readValue: Int = ThorFocusMode.AUTO,
    ) : IThorFocusPrivilegedService.Default() {
        val writes = mutableListOf<Int>()

        override fun readMode(): Int = readValue

        override fun writeMode(mode: Int): Boolean {
            writes += mode
            return true
        }
    }

    private class FakeThorFocusCommandRunner : ThorFocusCommandRunner {
        val commands = mutableListOf<List<String>>()
        var nextResult = ThorFocusProcessResult(0, "0", "")

        override fun run(command: List<String>): ThorFocusProcessResult {
            commands += command
            return nextResult
        }
    }

    private companion object {
        const val PERMISSION_GRANTED = 0
        const val PERMISSION_DENIED = -1
    }
}
