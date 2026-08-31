package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentEmailPasswordBinding
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.nativeauth.NativeAuthRequestInterceptor
import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication
import com.microsoft.identity.nativeauth.NativeAuthPublicClientApplicationConfiguration
import com.microsoft.identity.nativeauth.parameters.NativeAuthGetAccessTokenParameters
import com.microsoft.identity.nativeauth.parameters.NativeAuthSignInParameters
import com.microsoft.identity.nativeauth.parameters.NativeAuthSignUpParameters
import com.microsoft.identity.nativeauth.statemachine.errors.GetAccessTokenError
import com.microsoft.identity.nativeauth.statemachine.errors.GetAccountError
import com.microsoft.identity.nativeauth.statemachine.errors.MFARequestChallengeErrorV2
import com.microsoft.identity.nativeauth.statemachine.errors.SignInError
import com.microsoft.identity.nativeauth.statemachine.errors.SignInErrorV2
import com.microsoft.identity.nativeauth.statemachine.errors.SignUpError
import com.microsoft.identity.nativeauth.statemachine.errors.NativeAuthErrorV2
import com.microsoft.identity.nativeauth.statemachine.results.GetAccessTokenResult
import com.microsoft.identity.nativeauth.statemachine.results.GetAccountResult
import com.microsoft.identity.nativeauth.statemachine.results.SignInResult
import com.microsoft.identity.nativeauth.statemachine.results.SignOutResult
import com.microsoft.identity.nativeauth.statemachine.results.SignUpResult
import com.microsoft.identity.nativeauth.statemachine.results.NativeAuthResultV2
import com.microsoft.identity.nativeauth.statemachine.states.AccountState
import com.microsoft.identity.nativeauth.statemachine.states.MFARequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.MFAVerificationRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.SignUpCodeRequiredState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URL

class EmailPasswordSignInSignUpFragment : Fragment(), NativeAuthRequestInterceptor {

    private lateinit var authClient: INativeAuthPublicClientApplication
    private lateinit var authManager: AuthManager
    private var _binding: FragmentEmailPasswordBinding? = null
    private val binding get() = _binding!!

    companion object {
        private val TAG = EmailPasswordSignInSignUpFragment::class.java.simpleName
        private enum class STATUS { SignedIn, SignedOut }
    }

    override fun additionalHeaders(requestUrl: URL): Map<String, String>? {
        if (requestUrl.path.contains("oauth2/v2.0/initiate")) {
            return mapOf(
                "ignored-custom-header-value" to "ignored-custom-header",       // Will be ignored: doesn't start with "x-"
                "x-client-header" to "customer_header_2",                       // Will be ignored: starts with reserved prefix "x-client-"
                "X-my-custom-header" to "my data"                               // Will be added to the network request
            )
        }
        return null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEmailPasswordBinding.inflate(inflater, container, false)
        val view = binding.root

        authClient = AuthClient.getAuthClient()
        authManager = AuthClient.getAuthManager()
        (authClient as? PublicClientApplication)?.let { app ->
            (app.configuration as? NativeAuthPublicClientApplicationConfiguration)?.requestInterceptor = this
        }

        init()

        return view
    }

    override fun onResume() {
        super.onResume()
        getStateAndUpdateUI()
    }

    private fun init() {
        initializeButtonListeners()
    }

    private fun initializeButtonListeners() {
        binding.signIn.setOnClickListener {
            signIn()
        }

        binding.signUp.setOnClickListener {
            signUp()
        }

        binding.signOut.setOnClickListener {
            signOut()
        }
    }

    private fun getStateAndUpdateUI() {
        CoroutineScope(Dispatchers.Main).launch {
            val accountResult = authClient.getCurrentAccount()

            when (accountResult) {
                is GetAccountResult.AccountFound -> {
                    displaySignedInState(accountResult.resultValue)
                }
                is GetAccountResult.NoAccountFound -> {
                    displaySignedOutState()
                }
                is GetAccountError -> {
                    displayDialog(getString(R.string.msal_exception_title), accountResult.exception?.message ?: accountResult.errorMessage)
                }
            }
        }
    }

    private fun signIn() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            val email = binding.emailText.text.toString()
            val password = CharArray(binding.passwordText.length())
            binding.passwordText.text?.getChars(0, binding.passwordText.length(), password, 0)

            try {
                if (Configuration.useNativeAuthV2) {
                    handleResultV2(authManager.signIn(email, password))
                    return@launchWhenStarted
                }

                val parameters = NativeAuthSignInParameters(username = email)
                parameters.password = password
                when (val actionResult: SignInResult = authClient.signIn(parameters)) {
                    is SignInResult.Complete -> {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.sign_in_successful_message),
                            Toast.LENGTH_SHORT
                        ).show()
                        displaySignedInState(accountState = actionResult.resultValue)
                    }
                    is SignInResult.CodeRequired -> {
                        displayDialog(message = getString(R.string.sign_in_switch_to_otp_message))
                    }
                    is SignInResult.MFARequired -> {
                        // Please refer to the MFA Fragment for handling MFA branches if conditional access - MFA is enabled.
                        displayDialog(getString(R.string.unexpected_sdk_result_title), actionResult.toString())
                    }
                    is SignInResult.StrongAuthMethodRegistrationRequired -> {
                        // Please refer to the MFA Fragment for handling Strong Auth Method Registration flow.
                        displayDialog(getString(R.string.unexpected_sdk_result_title), actionResult.toString())
                    }
                    is SignInError -> {
                        handleSignInError(actionResult)
                    }
                }
            } finally {
                _binding?.passwordText?.text?.clear()
                password.fill('\u0000')
            }
        }
    }

    private fun signUp() {
        CoroutineScope(Dispatchers.Main).launch {
            val email = binding.emailText.text.toString()
            val password = CharArray(binding.passwordText.length())
            binding.passwordText.text?.getChars(0, binding.passwordText.length(), password, 0)

            if (Configuration.useNativeAuthV2) {
                val result = authManager.signUp(email, password)
                binding.passwordText.text?.clear()
                password.fill('\u0000')
                handleResultV2(result)
                return@launch
            }

            val parameters = NativeAuthSignUpParameters(username = email)
            parameters.password = password
            val actionResult: SignUpResult = authClient.signUp(parameters)

            binding.passwordText.text?.clear()
            password.fill('\u0000')

            when (actionResult) {
                is SignUpResult.CodeRequired -> {
                    navigateToSignUp(
                        nextState = actionResult.nextState
                    )
                }
                is SignUpError -> {
                    handleSignUpError(actionResult)
                }
            }
        }
    }

    private fun signOut() {
        CoroutineScope(Dispatchers.Main).launch {
            val getAccountResult = authClient.getCurrentAccount()

            if (getAccountResult is GetAccountResult.AccountFound) {
                val signOutResult = getAccountResult.resultValue.signOut()
                if (signOutResult is SignOutResult.Complete) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.sign_out_successful_message),
                        Toast.LENGTH_SHORT
                    ).show()
                    displaySignedOutState()
                } else {
                    displayDialog(getString(R.string.unexpected_sdk_result_title), signOutResult.toString())
                }
            }
        }
    }

    private fun displaySignedInState(accountState: AccountState) {
        emptyFields()
        updateUI(STATUS.SignedIn)
        displayAccount(accountState)
    }

    private fun displaySignedOutState() {
        emptyFields()
        updateUI(STATUS.SignedOut)
        emptyResults()
    }

    private fun updateUI(status: STATUS) {
        when (status) {
            STATUS.SignedIn -> {
                binding.signIn.isEnabled = false
                binding.signUp.isEnabled = false
                binding.signOut.isEnabled = true
            }
            STATUS.SignedOut -> {
                binding.signIn.isEnabled = true
                binding.signUp.isEnabled = true
                binding.signOut.isEnabled = false
            }
        }
    }

    private fun emptyFields() {
        binding.emailText.setText("")
        binding.passwordText.setText("")
    }

    private fun emptyResults() {
        binding.resultAccessToken.text = ""
        binding.resultIdToken.text = ""
    }

    private fun displayAccount(accountState: AccountState) {
        CoroutineScope(Dispatchers.Main).launch {
            val getAccessTokenParameters = NativeAuthGetAccessTokenParameters()
            val accessTokenResult = accountState.getAccessToken(getAccessTokenParameters)

            when (accessTokenResult) {
                is GetAccessTokenResult.Complete -> {
                    val accessToken = accessTokenResult.resultValue.accessToken
                    binding.resultAccessToken.text = getString(R.string.result_access_token_text) + accessToken

                    val idToken = accountState.getIdToken()
                    binding.resultIdToken.text = getString(R.string.result_id_token_text) + idToken
                }
                is GetAccessTokenError -> {
                    displayDialog(getString(R.string.msal_exception_title), accessTokenResult.exception?.message ?: accessTokenResult.errorMessage)
                }
            }
        }
    }

    private suspend fun handleResultV2(result: NativeAuthResultV2) {
        when (result) {
            is NativeAuthResultV2.Complete -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.sign_in_successful_message),
                    Toast.LENGTH_SHORT
                ).show()
                displaySignedInState(accountState = result.resultValue)
            }
            is NativeAuthResultV2.MFARequired -> {
                val emailMethod = result.authMethods.firstOrNull {
                    it.challengeChannel.equals("email", ignoreCase = true)
                }
                if (emailMethod == null) {
                    displayDialog(
                        getString(R.string.unexpected_sdk_result_title),
                        "No supported email authentication method was returned."
                    )
                    return
                }

                when (val challengeResult = authManager.selectAuthMethod(emailMethod)) {
                    is NativeAuthResultV2.MFAVerificationRequired -> {
                        navigateToMfaVerificationV2(
                            mfaState = result.nextState,
                            verificationState = challengeResult.nextState,
                            sentTo = challengeResult.sentTo,
                            channel = challengeResult.channel,
                            authMethod = emailMethod
                        )
                    }
                    is MFARequestChallengeErrorV2 -> {
                        displayDialog(
                            challengeResult.error ?: getString(R.string.unexpected_sdk_error_title),
                            challengeResult.errorMessage
                        )
                    }
                    is NativeAuthErrorV2 -> {
                        displayDialog(
                            challengeResult.error ?: getString(R.string.unexpected_sdk_error_title),
                            challengeResult.exception?.message ?: challengeResult.errorMessage
                        )
                    }
                    null -> {
                        displayDialog(getString(R.string.unexpected_sdk_result_title), "No MFA challenge result was returned.")
                    }
                    else -> {
                        displayDialog(getString(R.string.unexpected_sdk_result_title), challengeResult.toString())
                    }
                }
            }
            is SignInErrorV2 -> {
                displayDialog(
                    result.error ?: getString(R.string.unexpected_sdk_error_title),
                    result.exception?.message ?: result.errorMessage
                )
            }
            is NativeAuthErrorV2 -> {
                displayDialog(
                    result.error ?: getString(R.string.unexpected_sdk_error_title),
                    result.exception?.message ?: result.errorMessage
                )
            }
            else -> {
                displayDialog(getString(R.string.unexpected_sdk_result_title), result.toString())
            }
        }
    }

    private fun handleSignInError(error: SignInError) {
        when {
            error.isInvalidCredentials() || error.isBrowserRequired() || error.isUserNotFound() -> {
                displayDialog(error.error, error.errorMessage)
            }
            else -> {
                // Unexpected error
                displayDialog(getString(R.string.unexpected_sdk_error_title), error.exception?.message ?: error.errorMessage)
            }
        }
    }

    private fun handleSignUpError(error: SignUpError) {
        when {
            error.isInvalidUsername() || error.isInvalidPassword() || error.isUserAlreadyExists() ||
                    error.isAuthNotSupported() || error.isBrowserRequired() || error.isInvalidAttributes()
            -> {
                displayDialog(error.error, error.errorMessage)
            }
            else -> {
                // Unexpected error
                displayDialog(getString(R.string.unexpected_sdk_error_title), error.exception?.message ?: error.errorMessage)
            }
        }
    }

    private fun displayDialog(error: String? = null, message: String?) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(error)
            .setMessage(message)
        val alertDialog = builder.create()
        alertDialog.show()
    }

    private fun navigateToMfaVerificationV2(
        mfaState: MFARequiredStateV2,
        verificationState: MFAVerificationRequiredStateV2,
        sentTo: String,
        channel: String,
        authMethod: com.microsoft.identity.nativeauth.AuthMethod
    ) {
        if (!isAdded || parentFragmentManager.isStateSaved) {
            return
        }

        val fragment = MFAVerificationFragment().apply {
            arguments = Bundle().apply {
                putParcelable(Constants.MFA_STATE, mfaState)
                putParcelable(Constants.STATE, verificationState)
                putString(Constants.SENT_TO, sentTo)
                putString(Constants.CHANNEL, channel)
                putParcelable(Constants.AUTH_METHOD, authMethod)
            }
        }

        parentFragmentManager
            .beginTransaction()
            .setReorderingAllowed(true)
            .addToBackStack(fragment::class.java.name)
            .replace(R.id.scenario_fragment, fragment)
            .commit()
    }

    private fun navigateToSignUp(nextState: SignUpCodeRequiredState) {
        val bundle = Bundle()
        bundle.putParcelable(Constants.STATE, nextState)
        val fragment = SignUpCodeFragment()
        fragment.arguments = bundle

        requireActivity().supportFragmentManager
            .beginTransaction()
            .setReorderingAllowed(true)
            .addToBackStack(fragment::class.java.name)
            .replace(R.id.scenario_fragment, fragment)
            .commit()
    }
}
