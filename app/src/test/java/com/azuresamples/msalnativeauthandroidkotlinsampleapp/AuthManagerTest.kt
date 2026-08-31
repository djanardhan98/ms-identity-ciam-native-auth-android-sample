package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import com.microsoft.identity.common.java.nativeauth.providers.responses.v2.NativeAuthV2ContinuationState
import com.microsoft.identity.nativeauth.AuthMethod
import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication
import com.microsoft.identity.nativeauth.NativeAuthPublicClientApplicationConfiguration
import com.microsoft.identity.nativeauth.statemachine.NativeAuthFlowScenarioV2
import com.microsoft.identity.nativeauth.statemachine.errors.NativeAuthErrorV2
import com.microsoft.identity.nativeauth.statemachine.errors.SignInErrorV2
import com.microsoft.identity.nativeauth.statemachine.results.NativeAuthResultV2
import com.microsoft.identity.nativeauth.statemachine.states.CodeRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.MFARequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.NativeAuthBaseStateV2
import com.microsoft.identity.nativeauth.statemachine.states.SignInAfterResetPasswordStateV2
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertSame
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.ArrayDeque

class AuthManagerTest {

    @Test
    fun resetPasswordPreservesTrackedStateOnError() = runBlocking {
        val nextState = createState(CodeRequiredStateV2::class.java)
        val authManager = AuthManager(
            applicationReturning(
                NativeAuthResultV2.CodeRequired(
                    nextState = nextState,
                    scenario = NativeAuthFlowScenarioV2.RESET_PASSWORD,
                    codeLength = 6,
                    sentTo = "u***@example.com",
                    channel = "email"
                ),
                NativeAuthErrorV2(
                    errorMessage = "Network error",
                    correlationId = "correlation-id",
                    scenario = NativeAuthFlowScenarioV2.RESET_PASSWORD
                )
            )
        )

        authManager.resetPassword("user@example.com")
        authManager.resetPassword("user@example.com")

        assertSame(nextState, authManager.currentState)
    }

    @Test
    fun signInTracksMfaRequiredState() = runBlocking {
        val nextState = createMfaState()
        val result = NativeAuthResultV2.MFARequired(
            nextState = nextState,
            scenario = NativeAuthFlowScenarioV2.SIGN_IN,
            authMethods = nextState.authMethods
        )
        val authManager = AuthManager(applicationReturning(result))

        authManager.signIn("user@example.com", "password".toCharArray())

        assertSame(nextState, authManager.currentState)
    }

    @Test
    fun signInPreservesTrackedMfaStateOnError() = runBlocking {
        val nextState = createMfaState()
        val authManager = AuthManager(
            applicationReturning(
                NativeAuthResultV2.MFARequired(
                    nextState = nextState,
                    scenario = NativeAuthFlowScenarioV2.SIGN_IN,
                    authMethods = nextState.authMethods
                ),
                SignInErrorV2(
                    errorMessage = "Invalid credentials",
                    correlationId = "correlation-id",
                    scenario = NativeAuthFlowScenarioV2.SIGN_IN
                )
            )
        )

        authManager.signIn("user@example.com", "password".toCharArray())
        authManager.signIn("user@example.com", "password".toCharArray())

        assertSame(nextState, authManager.currentState)
    }

    @Test
    fun resetPasswordTracksSignInAfterResetPasswordState() = runBlocking {
        val nextState = createState(SignInAfterResetPasswordStateV2::class.java)
        val result = NativeAuthResultV2.SignInAfterResetPasswordRequired(
            nextState = nextState,
            scenario = NativeAuthFlowScenarioV2.RESET_PASSWORD
        )
        val authManager = AuthManager(applicationReturning(result))

        authManager.resetPassword("user@example.com")

        assertSame(nextState, authManager.currentState)
    }

    private fun applicationReturning(vararg results: NativeAuthResultV2): INativeAuthPublicClientApplication {
        val queuedResults = ArrayDeque(results.toList())
        return Proxy.newProxyInstance(
            INativeAuthPublicClientApplication::class.java.classLoader,
            arrayOf(INativeAuthPublicClientApplication::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "resetPasswordV2", "signInV2" -> queuedResults.removeFirst()
                else -> throw UnsupportedOperationException(method.name)
            }
        } as INativeAuthPublicClientApplication
    }

    private fun createMfaState(): MFARequiredStateV2 {
        val authMethods = listOf(AuthMethod("email-1", "oob", "u***@example.com", "email"))
        val constructor = MFARequiredStateV2::class.java.getDeclaredConstructor(
            String::class.java,
            String::class.java,
            NativeAuthFlowScenarioV2::class.java,
            NativeAuthPublicClientApplicationConfiguration::class.java,
            NativeAuthV2ContinuationState::class.java,
            List::class.java
        )
        constructor.isAccessible = true
        return constructor.newInstance(
            "continuation-token",
            "correlation-id",
            NativeAuthFlowScenarioV2.SIGN_IN,
            NativeAuthPublicClientApplicationConfiguration(),
            null,
            authMethods
        )
    }

    private fun <T : NativeAuthBaseStateV2> createState(stateClass: Class<T>): T {
        val constructor = stateClass.getDeclaredConstructor(
            String::class.java,
            String::class.java,
            NativeAuthFlowScenarioV2::class.java,
            NativeAuthPublicClientApplicationConfiguration::class.java,
            NativeAuthV2ContinuationState::class.java
        )
        constructor.isAccessible = true
        return constructor.newInstance(
            "continuation-token",
            "correlation-id",
            NativeAuthFlowScenarioV2.RESET_PASSWORD,
            NativeAuthPublicClientApplicationConfiguration(),
            null
        )
    }
}
