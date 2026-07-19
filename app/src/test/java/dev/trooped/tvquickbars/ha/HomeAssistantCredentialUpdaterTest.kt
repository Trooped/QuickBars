package dev.trooped.tvquickbars.ha

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class HomeAssistantCredentialUpdaterTest {

    @Test
    fun `validates and persists the candidate URL and token as one pair`() = runBlocking {
        val candidate = "http://new-ha.local:8123" to "new-token"
        val validationCalls = mutableListOf<Pair<String, String>>()
        var persisted: Pair<String, String>? = null
        val updater = HomeAssistantCredentialUpdater(
            validate = { url, token ->
                validationCalls += url to token
                ValidationResult.Success
            },
            persist = { url, token -> persisted = url to token }
        )

        val result = updater.update(candidate.first, candidate.second)

        assertSame(ValidationResult.Success, result)
        assertEquals(listOf(candidate), validationCalls)
        assertEquals(candidate, persisted)
    }

    @Test
    fun `keeps the previously persisted pair when validation fails`() = runBlocking {
        val existing = "http://old-ha.local:8123" to "old-token"
        var persisted = existing
        val updater = HomeAssistantCredentialUpdater(
            validate = { _, _ ->
                ValidationResult.Error(ConnectionState.Reason.AUTH_FAILED)
            },
            persist = { url, token -> persisted = url to token }
        )

        val result = updater.update("http://new-ha.local:8123", "new-token")

        assertEquals(
            ValidationResult.Error(ConnectionState.Reason.AUTH_FAILED),
            result
        )
        assertEquals(existing, persisted)
    }
}
