package com.darkaxt.dualdex.retroarch

data class RetroArchConfigSetupOutcome(
    val configState: String,
    val restartRequired: Boolean,
    val message: String,
)

/** File readback proves only the selected document, never which config RetroArch loaded. */
object RetroArchSetupPolicy {
    // Command status and document readback do not report the running autosave interval.
    const val RUNTIME_AUTOSAVE_STATUS = "UNVERIFIED"

    const val MISSING_PUBLIC_CONFIG_MESSAGE =
        "No public RetroArch/retroarch.cfg was found. RetroArch may use a protected Android/data config. Enable and save settings inside RetroArch; copying its config does not make that copy active."

    fun afterInstall(
        result: ConfigInstallResult,
        connection: RetroArchConnection,
        restartRequired: Boolean,
    ): RetroArchConfigSetupOutcome = when {
        result is ConfigInstallResult.Failed -> RetroArchConfigSetupOutcome("FAILED", restartRequired, result.message)
        result is ConfigInstallResult.Installed || restartRequired -> RetroArchConfigSetupOutcome(
            "RESTART_REQUIRED", true,
            "The selected config file enables Network Commands and 10-second SaveRAM autosave. This does not prove RetroArch loads that file. Save the active configuration inside RetroArch, fully restart it, then return for live verification.",
        )
        connection.hasValidCommandStatus -> RetroArchConfigSetupOutcome(
            "VERIFIED", false,
            "RetroArch's command interface replied and the selected file is configured. The reply does not identify which config file RetroArch loaded.",
        )
        else -> RetroArchConfigSetupOutcome(
            "UNVERIFIED", false,
            "The selected config file is configured, but the command interface has not replied. Check the active configuration inside RetroArch. An inactive copy or a build without a working command listener cannot enable the connection.",
        )
    }
}
