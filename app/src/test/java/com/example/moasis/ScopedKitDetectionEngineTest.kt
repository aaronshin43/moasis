package com.example.moasis

import com.example.moasis.ai.melange.KitInferenceSession
import com.example.moasis.ai.melange.ScopedKitDetectionEngine
import com.example.moasis.ai.model.DetectedObject
import com.example.moasis.ai.model.VisionDetectionResult
import com.example.moasis.domain.model.VisionTaskType
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Verifies the lifecycle invariants of [ScopedKitDetectionEngine]:
 *  - It never reports a resident session.
 *  - It always closes the inference session, even when warmup or detect fails.
 *  - Concurrent detect calls are serialized.
 *
 * The ONNX Runtime cannot run on the JVM without native libs, so these tests
 * inject a fake [KitInferenceSession]. The lifecycle contract checked here is
 * the same one [ScopedKitDetectionEngine] enforces against the real ONNX
 * session in production.
 */
class ScopedKitDetectionEngineTest {

    private val configuredAsset = "yoloe_s.onnx"
    private val unconfiguredAsset = ""

    @Test
    fun `isPreparedInMemory is always false`() {
        val engine = ScopedKitDetectionEngine(
            modelAssetName = configuredAsset,
            sessionFactory = { FakeKitInferenceSession() },
        )
        assertFalse(engine.isPreparedInMemory())
    }

    @Test
    fun `isConfigured reflects the model asset name`() {
        val configured = ScopedKitDetectionEngine(
            modelAssetName = configuredAsset,
            sessionFactory = { FakeKitInferenceSession() },
        )
        val unconfigured = ScopedKitDetectionEngine(
            modelAssetName = unconfiguredAsset,
            sessionFactory = { FakeKitInferenceSession() },
        )
        assertTrue(configured.isConfigured())
        assertFalse(unconfigured.isConfigured())
    }

    @Test
    fun `prepareIfNeeded does not build a session when configured`() = runBlocking {
        var built = 0
        val engine = ScopedKitDetectionEngine(
            modelAssetName = configuredAsset,
            sessionFactory = {
                built++
                FakeKitInferenceSession()
            },
        )
        val result = engine.prepareIfNeeded()
        assertTrue(result.isSuccess)
        assertEquals("prepareIfNeeded must not load anything", 0, built)
    }

    @Test
    fun `prepareIfNeeded fails fast when not configured`() = runBlocking {
        val engine = ScopedKitDetectionEngine(
            modelAssetName = unconfiguredAsset,
            sessionFactory = { FakeKitInferenceSession() },
        )
        val result = engine.prepareIfNeeded()
        assertTrue(result.isFailure)
    }

    @Test
    fun `detect releases session after success`() = runBlocking {
        val session = FakeKitInferenceSession(
            detectResult = Result.success(VisionDetectionResult(emptyList())),
        )
        val engine = ScopedKitDetectionEngine(
            modelAssetName = configuredAsset,
            sessionFactory = { session },
        )
        engine.detect("image.jpg", VisionTaskType.KIT_DETECTION)
        assertEquals(1, session.warmupCalls.get())
        assertEquals(1, session.detectCalls.get())
        assertEquals(1, session.closeCalls.get())
    }

    @Test
    fun `detect releases session even when warmup fails`() = runBlocking {
        val session = FakeKitInferenceSession(
            warmupResult = Result.failure(IllegalStateException("warmup boom")),
        )
        val engine = ScopedKitDetectionEngine(
            modelAssetName = configuredAsset,
            sessionFactory = { session },
        )
        val result = engine.detect("image.jpg", VisionTaskType.KIT_DETECTION)
        assertTrue(result.isFailure)
        assertEquals("warmup must run once", 1, session.warmupCalls.get())
        assertEquals("detect must be skipped on warmup failure", 0, session.detectCalls.get())
        assertEquals("session must be released even on warmup failure", 1, session.closeCalls.get())
    }

    @Test
    fun `detect releases session even when inference throws`() = runBlocking {
        val session = FakeKitInferenceSession(
            detectThrowable = RuntimeException("inference boom"),
        )
        val engine = ScopedKitDetectionEngine(
            modelAssetName = configuredAsset,
            sessionFactory = { session },
        )
        val result = engine.detect("image.jpg", VisionTaskType.KIT_DETECTION)
        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
        assertEquals("session must be released even on inference exception", 1, session.closeCalls.get())
    }

    @Test
    fun `detect fails fast when not configured and never builds a session`() = runBlocking {
        var built = 0
        val engine = ScopedKitDetectionEngine(
            modelAssetName = unconfiguredAsset,
            sessionFactory = {
                built++
                FakeKitInferenceSession()
            },
        )
        val result = engine.detect("image.jpg", VisionTaskType.KIT_DETECTION)
        assertTrue(result.isFailure)
        assertEquals("Unconfigured engine must not load a session", 0, built)
    }

    @Test
    fun `detect calls are serialized so the model is never loaded twice in parallel`() = runBlocking {
        val concurrency = AtomicInteger(0)
        var maxObserved = 0
        val engine = ScopedKitDetectionEngine(
            modelAssetName = configuredAsset,
            sessionFactory = {
                FakeKitInferenceSession(
                    detectAction = {
                        val now = concurrency.incrementAndGet()
                        if (now > maxObserved) maxObserved = now
                        delay(20)
                        concurrency.decrementAndGet()
                        Result.success(
                            VisionDetectionResult(
                                listOf(DetectedObject(label = "test", confidence = 0.9f)),
                            ),
                        )
                    },
                )
            },
        )
        val a = async { engine.detect("a.jpg", VisionTaskType.KIT_DETECTION) }
        val b = async { engine.detect("b.jpg", VisionTaskType.KIT_DETECTION) }
        a.await()
        b.await()
        assertEquals("Mutex must serialize concurrent detect calls", 1, maxObserved)
    }

    private class FakeKitInferenceSession(
        private val warmupResult: Result<Unit> = Result.success(Unit),
        private val detectResult: Result<VisionDetectionResult> =
            Result.success(VisionDetectionResult(emptyList())),
        private val detectThrowable: Throwable? = null,
        private val detectAction: (suspend () -> Result<VisionDetectionResult>)? = null,
    ) : KitInferenceSession {
        val warmupCalls = AtomicInteger(0)
        val detectCalls = AtomicInteger(0)
        val closeCalls = AtomicInteger(0)

        override suspend fun warmup(): Result<Unit> {
            warmupCalls.incrementAndGet()
            return warmupResult
        }

        override suspend fun detect(
            imagePath: String,
            taskType: VisionTaskType,
        ): Result<VisionDetectionResult> {
            detectCalls.incrementAndGet()
            detectThrowable?.let { throw it }
            detectAction?.let { return it.invoke() }
            return detectResult
        }

        override fun close() {
            closeCalls.incrementAndGet()
        }
    }
}
