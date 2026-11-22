package com.darkaxt.dualdex.setup

import com.darkaxt.dualdex.retroarch.RomIndexEntry

/**
 * The game an embedding host (for example an emulator frontend running DualDex in-process) is running. Its [rom] is
 * matched against the host's `GET_STATUS` reply exactly like an indexed ROM, and loaded from its `file:` source id, so
 * the host does not need DualDex's ROM library or storage grants for identification. Saves are still discovered from
 * the host's `savefile_directory` reply.
 */
data class HostSession(val rom: RomIndexEntry)
