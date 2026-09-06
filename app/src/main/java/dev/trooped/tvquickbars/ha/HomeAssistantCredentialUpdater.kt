package dev.trooped.tvquickbars.ha

class HomeAssistantCredentialUpdater(
    private val validate: suspend (url: String, token: String) -> ValidationResult,
    private val persist: (url: String, token: String) -> Unit
) {
    suspend fun update(url: String, token: String): ValidationResult {
        val result = validate(url, token)
        if (result is ValidationResult.Success) {
            persist(url, token)
        }
        return result
    }
}
