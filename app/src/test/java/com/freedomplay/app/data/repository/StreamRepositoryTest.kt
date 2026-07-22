package com.freedomplay.app.data.repository

import org.junit.Test

/**
 * The original Retrofit-based unit tests were removed: [StreamRepository] no longer talks to
 * the injected [com.freedomplay.app.data.api.piped.PipedApiService] /
 * Retrofit services. It now uses
 * NewPipeExtractor as the primary source with raw-OkHttp Piped/Invidious/InnerTube fallbacks,
 * none of which are exercised through those mockable interfaces.
 *
 * Meaningful coverage here requires an OkHttp MockWebServer harness (fallback paths) plus a
 * fake extractor for the NewPipe path — tracked as follow-up work. This placeholder keeps the
 * test source set compiling.
 */
class StreamRepositoryTest {

    @Test
    fun `placeholder keeps test source set compiling`() {
        // Intentionally empty. See class KDoc.
    }
}
