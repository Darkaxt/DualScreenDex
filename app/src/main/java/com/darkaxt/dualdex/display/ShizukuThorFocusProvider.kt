package com.darkaxt.dualdex.display

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku

internal const val SHIZUKU_PERMISSION_REQUEST_CODE = 48231

internal enum class ShizukuThorFocusFailure {
    BINDER_UNAVAILABLE,
    BINDER_DIED,
    PERMISSION_DENIED,
    PERMISSION_PERMANENTLY_DENIED,
    API_FAILURE,
    SERVICE_DISCONNECTED,
    COMMAND_FAILED,
}

internal sealed interface ShizukuThorFocusState {
    data object Unknown : ShizukuThorFocusState
    data object PermissionRequired : ShizukuThorFocusState
    data object PermissionRequested : ShizukuThorFocusState
    data class Binding(val backendUid: Int) : ShizukuThorFocusState
    data class Connected(val backendUid: Int) : ShizukuThorFocusState
    data class TerminalFailure(
        val reason: ShizukuThorFocusFailure,
        val backendUid: Int? = null,
    ) : ShizukuThorFocusState
}

internal sealed interface ThorFocusPrivilegedResult<out T> {
    data class Success<T>(val value: T) : ThorFocusPrivilegedResult<T>
    data class Failure(val reason: ShizukuThorFocusFailure) : ThorFocusPrivilegedResult<Nothing>
}

internal interface ShizukuUserServiceConnection {
    fun onConnected(service: IThorFocusPrivilegedService)
    fun onDisconnected()
}

internal interface ShizukuGateway {
    fun pingBinder(): Boolean
    fun checkSelfPermission(): Int
    fun shouldShowRequestPermissionRationale(): Boolean
    fun requestPermission(requestCode: Int)
    fun getUid(): Int
    fun bindUserService(connection: ShizukuUserServiceConnection)
    fun addBinderReceivedListener(listener: () -> Unit)
    fun removeBinderReceivedListener(listener: () -> Unit)
    fun addBinderDeadListener(listener: () -> Unit)
    fun removeBinderDeadListener(listener: () -> Unit)
    fun addPermissionResultListener(listener: (requestCode: Int, result: Int) -> Unit)
    fun removePermissionResultListener(listener: (requestCode: Int, result: Int) -> Unit)
}

internal class AndroidShizukuGateway(context: Context) : ShizukuGateway {
    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(context, ThorFocusShizukuService::class.java),
    )
        .tag(USER_SERVICE_TAG)
        .version(USER_SERVICE_VERSION)
        .daemon(false)

    private var androidConnection: ServiceConnection? = null
    private var binderReceivedRegistration: Pair<() -> Unit, Shizuku.OnBinderReceivedListener>? = null
    private var binderDeadRegistration: Pair<() -> Unit, Shizuku.OnBinderDeadListener>? = null
    private var permissionRegistration:
        Pair<(Int, Int) -> Unit, Shizuku.OnRequestPermissionResultListener>? = null

    override fun pingBinder(): Boolean = Shizuku.pingBinder()

    override fun checkSelfPermission(): Int = Shizuku.checkSelfPermission()

    override fun shouldShowRequestPermissionRationale(): Boolean =
        Shizuku.shouldShowRequestPermissionRationale()

    override fun requestPermission(requestCode: Int) = Shizuku.requestPermission(requestCode)

    override fun getUid(): Int = Shizuku.getUid()

    override fun bindUserService(connection: ShizukuUserServiceConnection) {
        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val typedService = IThorFocusPrivilegedService.Stub.asInterface(service)
                if (typedService == null) connection.onDisconnected() else connection.onConnected(typedService)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                connection.onDisconnected()
            }
        }
        androidConnection = serviceConnection
        Shizuku.bindUserService(userServiceArgs, serviceConnection)
    }

    override fun addBinderReceivedListener(listener: () -> Unit) {
        val registration = Shizuku.OnBinderReceivedListener(listener)
        binderReceivedRegistration = listener to registration
        Shizuku.addBinderReceivedListener(registration)
    }

    override fun removeBinderReceivedListener(listener: () -> Unit) {
        binderReceivedRegistration
            ?.takeIf { it.first === listener }
            ?.let { Shizuku.removeBinderReceivedListener(it.second) }
        binderReceivedRegistration = null
    }

    override fun addBinderDeadListener(listener: () -> Unit) {
        val registration = Shizuku.OnBinderDeadListener(listener)
        binderDeadRegistration = listener to registration
        Shizuku.addBinderDeadListener(registration)
    }

    override fun removeBinderDeadListener(listener: () -> Unit) {
        binderDeadRegistration
            ?.takeIf { it.first === listener }
            ?.let { Shizuku.removeBinderDeadListener(it.second) }
        binderDeadRegistration = null
    }

    override fun addPermissionResultListener(listener: (Int, Int) -> Unit) {
        val registration = Shizuku.OnRequestPermissionResultListener(listener)
        permissionRegistration = listener to registration
        Shizuku.addRequestPermissionResultListener(registration)
    }

    override fun removePermissionResultListener(listener: (Int, Int) -> Unit) {
        permissionRegistration
            ?.takeIf { it.first === listener }
            ?.let { Shizuku.removeRequestPermissionResultListener(it.second) }
        permissionRegistration = null
    }

    private companion object {
        const val USER_SERVICE_TAG = "dualdex_thor_focus"
        const val USER_SERVICE_VERSION = 1
    }
}

internal class ShizukuThorFocusProvider(
    private val gateway: ShizukuGateway,
    private val onStateChanged: (ShizukuThorFocusState) -> Unit = {},
) : AutoCloseable {
    constructor(context: Context, onStateChanged: (ShizukuThorFocusState) -> Unit = {}) : this(
        AndroidShizukuGateway(context.applicationContext),
        onStateChanged,
    )

    var state: ShizukuThorFocusState = ShizukuThorFocusState.Unknown
        private set

    private var permissionRequestInFlight = false
    private var permissionDenied = false
    private var service: IThorFocusPrivilegedService? = null
    private var bindStarted = false
    private var closed = false

    private val binderReceivedListener: () -> Unit = {
        permissionRequestInFlight = false
        permissionDenied = false
        bindStarted = false
        service = null
        updateState(ShizukuThorFocusState.Unknown)
    }
    private val binderDeadListener: () -> Unit = {
        permissionRequestInFlight = false
        bindStarted = false
        service = null
        updateState(
            ShizukuThorFocusState.TerminalFailure(ShizukuThorFocusFailure.BINDER_DIED),
        )
    }
    private val permissionResultListener: (Int, Int) -> Unit = permissionResult@{ requestCode, result ->
        if (requestCode != SHIZUKU_PERMISSION_REQUEST_CODE || !permissionRequestInFlight) {
            return@permissionResult
        }
        permissionRequestInFlight = false
        if (result == PackageManager.PERMISSION_GRANTED) {
            prepare(userInitiated = false)
        } else {
            permissionDenied = true
            updateState(
                ShizukuThorFocusState.TerminalFailure(
                    ShizukuThorFocusFailure.PERMISSION_DENIED,
                ),
            )
        }
    }
    private val connection = object : ShizukuUserServiceConnection {
        override fun onConnected(service: IThorFocusPrivilegedService) {
            this@ShizukuThorFocusProvider.service = service
            val uid = (state as? ShizukuThorFocusState.Binding)?.backendUid
            if (uid == null) {
                disconnect(ShizukuThorFocusFailure.API_FAILURE)
            } else {
                updateState(ShizukuThorFocusState.Connected(uid))
            }
        }

        override fun onDisconnected() {
            disconnect(ShizukuThorFocusFailure.SERVICE_DISCONNECTED)
        }
    }

    init {
        gateway.addBinderReceivedListener(binderReceivedListener)
        gateway.addBinderDeadListener(binderDeadListener)
        gateway.addPermissionResultListener(permissionResultListener)
    }

    fun prepare(userInitiated: Boolean): ShizukuThorFocusState {
        if (closed) return terminal(ShizukuThorFocusFailure.SERVICE_DISCONNECTED)
        if (permissionDenied) return state
        if (!binderAlive()) return terminal(ShizukuThorFocusFailure.BINDER_UNAVAILABLE)
        if (bindStarted) return state

        return guardedApiCall {
            if (gateway.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                bindOnce()
            } else if (gateway.shouldShowRequestPermissionRationale()) {
                permissionDenied = true
                terminal(ShizukuThorFocusFailure.PERMISSION_PERMANENTLY_DENIED)
            } else if (!userInitiated) {
                updateState(ShizukuThorFocusState.PermissionRequired)
            } else if (permissionRequestInFlight) {
                updateState(ShizukuThorFocusState.PermissionRequested)
            } else {
                permissionRequestInFlight = true
                gateway.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                updateState(ShizukuThorFocusState.PermissionRequested)
            }
        }
    }

    fun readMode(): ThorFocusPrivilegedResult<Int> {
        val connected = service ?: return ThorFocusPrivilegedResult.Failure(
            ShizukuThorFocusFailure.SERVICE_DISCONNECTED,
        )
        if (!binderAlive()) return ThorFocusPrivilegedResult.Failure(
            ShizukuThorFocusFailure.BINDER_UNAVAILABLE,
        )
        return runCatching { connected.readMode() }
            .mapCatching { mode ->
                require(mode in ThorFocusMode.AUTO..ThorFocusMode.BOTTOM)
                ThorFocusPrivilegedResult.Success(mode)
            }
            .getOrElse { ThorFocusPrivilegedResult.Failure(ShizukuThorFocusFailure.COMMAND_FAILED) }
    }

    fun writeMode(mode: Int): ThorFocusPrivilegedResult<Boolean> {
        require(mode in ThorFocusMode.AUTO..ThorFocusMode.BOTTOM)
        val connected = service ?: return ThorFocusPrivilegedResult.Failure(
            ShizukuThorFocusFailure.SERVICE_DISCONNECTED,
        )
        if (!binderAlive()) return ThorFocusPrivilegedResult.Failure(
            ShizukuThorFocusFailure.BINDER_UNAVAILABLE,
        )
        return runCatching { connected.writeMode(mode) }
            .fold(
                onSuccess = { ThorFocusPrivilegedResult.Success(it) },
                onFailure = {
                    ThorFocusPrivilegedResult.Failure(ShizukuThorFocusFailure.COMMAND_FAILED)
                },
            )
    }

    override fun close() {
        if (closed) return
        closed = true
        service = null
        bindStarted = false
        permissionRequestInFlight = false
        gateway.removePermissionResultListener(permissionResultListener)
        gateway.removeBinderDeadListener(binderDeadListener)
        gateway.removeBinderReceivedListener(binderReceivedListener)
    }

    private fun bindOnce(): ShizukuThorFocusState {
        if (bindStarted) return state
        val uid = gateway.getUid()
        bindStarted = true
        updateState(ShizukuThorFocusState.Binding(uid))
        return try {
            gateway.bindUserService(connection)
            state
        } catch (failure: RuntimeException) {
            bindStarted = false
            terminal(ShizukuThorFocusFailure.API_FAILURE, uid)
        }
    }

    private fun binderAlive(): Boolean = runCatching { gateway.pingBinder() }.getOrDefault(false)

    private inline fun guardedApiCall(block: () -> ShizukuThorFocusState): ShizukuThorFocusState =
        try {
            block()
        } catch (failure: RuntimeException) {
            permissionRequestInFlight = false
            terminal(ShizukuThorFocusFailure.API_FAILURE)
        }

    private fun disconnect(reason: ShizukuThorFocusFailure) {
        service = null
        bindStarted = false
        updateState(ShizukuThorFocusState.TerminalFailure(reason, state.backendUidOrNull()))
    }

    private fun terminal(
        reason: ShizukuThorFocusFailure,
        backendUid: Int? = state.backendUidOrNull(),
    ): ShizukuThorFocusState = updateState(ShizukuThorFocusState.TerminalFailure(reason, backendUid))

    private fun updateState(newState: ShizukuThorFocusState): ShizukuThorFocusState {
        state = newState
        onStateChanged(newState)
        return newState
    }

    private fun ShizukuThorFocusState.backendUidOrNull(): Int? = when (this) {
        is ShizukuThorFocusState.Binding -> backendUid
        is ShizukuThorFocusState.Connected -> backendUid
        is ShizukuThorFocusState.TerminalFailure -> backendUid
        else -> null
    }
}
