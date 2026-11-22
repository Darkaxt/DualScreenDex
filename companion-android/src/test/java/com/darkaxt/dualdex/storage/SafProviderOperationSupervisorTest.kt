package com.darkaxt.dualdex.storage

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafProviderOperationSupervisorTest {
    @Test
    fun `timeout cancels a blocked provider operation and allows an immediate retry generation`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        var cancelled = false
        val supervisor = SafProviderOperationSupervisor(timeoutMillis = 25)
        try {
            val timedOut = runCatching {
                supervisor.await(onTimeout = { cancelled = true }) {
                    started.countDown()
                    while (release.count > 0L) {
                        try {
                            release.await(5, TimeUnit.SECONDS)
                        } catch (_: InterruptedException) {
                            // A hostile provider ignores interruption until its own operation completes.
                        }
                    }
                    "late"
                }
            }

            assertTrue(started.await(1, TimeUnit.SECONDS))
            assertTrue(timedOut.exceptionOrNull() is SafProviderOperationTimeout)
            assertTrue(cancelled)
            assertEquals("retry", supervisor.await { "retry" })
        } finally {
            release.countDown()
            supervisor.close()
        }
    }

    @Test
    fun `first read-only timeout is retryable and a following read succeeds`() {
        val release = CountDownLatch(1)
        val supervisor = SafProviderOperationSupervisor(timeoutMillis = 25)
        try {
            val failure = runCatching {
                supervisor.await {
                    while (release.count > 0L) {
                        try {
                            release.await(5, TimeUnit.SECONDS)
                        } catch (_: InterruptedException) {
                            // A hostile provider ignores interruption until its own operation completes.
                        }
                    }
                }
            }.exceptionOrNull()

            assertEquals(SafProviderRetryDisposition.Retryable, (failure as SafProviderOperationTimeout).disposition)
            assertEquals("retry", supervisor.await { "retry" })
        } finally {
            release.countDown()
            supervisor.close()
        }
    }

    @Test
    fun `fails closed after the bounded ignored-cancellation worker capacity is exhausted`() {
        val release = CountDownLatch(1)
        val supervisor = SafProviderOperationSupervisor(timeoutMillis = 25, maximumRetiredExecutors = 2)
        try {
            repeat(2) {
                val timedOut = runCatching {
                    supervisor.await {
                        while (release.count > 0L) {
                            try {
                                release.await(5, TimeUnit.SECONDS)
                            } catch (_: InterruptedException) {
                                // A hostile provider ignores interruption until its own operation completes.
                            }
                        }
                    }
                }
                assertTrue(timedOut.exceptionOrNull() is SafProviderOperationTimeout)
            }

            val unavailable = runCatching {
                supervisor.await(SafProviderOperationKind.MUTATION) { "must not run" }
            }

            assertTrue(unavailable.exceptionOrNull() is SafProviderOperationUnavailable)
        } finally {
            release.countDown()
            supervisor.close()
        }
    }

    @Test
    fun `provider-scoped half-open probe restores a healthy provider without poisoning another authority`() {
        val release = CountDownLatch(1)
        val registry = SafProviderOperationRegistry(
            maximumAuthorities = 2,
            supervisorFactory = { SafProviderOperationSupervisor(timeoutMillis = 25, maximumRetiredExecutors = 2) },
        )
        try {
            val blocked = registry.forAuthority("blocked")
            repeat(2) {
                val timeout = runCatching {
                    blocked.await {
                        while (release.count > 0L) {
                            try {
                                release.await(5, TimeUnit.SECONDS)
                            } catch (_: InterruptedException) {
                                // A hostile provider ignores interruption until its own operation completes.
                            }
                        }
                    }
                }
                assertTrue(timeout.exceptionOrNull() is SafProviderOperationTimeout)
            }

            assertEquals("healthy", registry.forAuthority("healthy").await { "healthy" })
            assertEquals("probe", blocked.await { "probe" })
            assertEquals("normal", blocked.await { "normal" })
        } finally {
            release.countDown()
            registry.close()
        }
    }

    @Test
    fun `late timed-out mutation cannot overwrite a retry because retry mutation is rejected`() {
        val release = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val writes = mutableListOf<String>()
        val supervisor = SafProviderOperationSupervisor(timeoutMillis = 25)
        try {
            val timeout = runCatching {
                supervisor.await(SafProviderOperationKind.MUTATION) {
                    while (release.count > 0L) {
                        try {
                            release.await(5, TimeUnit.SECONDS)
                        } catch (_: InterruptedException) {
                            // A hostile provider ignores interruption until its own operation completes.
                        }
                    }
                    writes += "late"
                    completed.countDown()
                }
            }
            assertTrue(timeout.exceptionOrNull() is SafProviderOperationTimeout)

            val retry = runCatching {
                supervisor.await(SafProviderOperationKind.MUTATION) { writes += "retry" }
            }

            assertTrue(retry.exceptionOrNull() is SafProviderOperationUnavailable)
            assertTrue(writes.isEmpty())
            release.countDown()
            assertTrue(completed.await(1, TimeUnit.SECONDS))
            assertEquals(listOf("late"), writes)
        } finally {
            release.countDown()
            supervisor.close()
        }
    }

    @Test
    fun `late operation token cannot commit over a retry generation`() {
        val generations = SafOperationGenerations()
        val first = generations.begin()
        val retry = generations.begin()
        var committed = ""

        assertFalse(generations.commitIfCurrent(first) { committed = "late" })
        assertTrue(generations.commitIfCurrent(retry) { committed = "retry" })
        assertEquals("retry", committed)
    }
}
