package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentMfaChallengeBinding
import com.microsoft.identity.nativeauth.AuthMethod
import com.microsoft.identity.nativeauth.statemachine.errors.MFARequestChallengeError
import com.microsoft.identity.nativeauth.statemachine.errors.MFASubmitChallengeError
import com.microsoft.identity.nativeauth.statemachine.errors.NativeAuthErrorV2
import com.microsoft.identity.nativeauth.statemachine.results.MFARequiredResult
import com.microsoft.identity.nativeauth.statemachine.results.NativeAuthResultV2
import com.microsoft.identity.nativeauth.statemachine.results.SignInResult
import com.microsoft.identity.nativeauth.statemachine.states.MFARequiredState
import com.microsoft.identity.nativeauth.statemachine.states.MFAVerificationRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.StrongAuthVerificationRequiredStateV2
import kotlinx.coroutines.launch

class MFAVerificationFragment : Fragment() {
    private lateinit var currentState: Parcelable
    private var returnToMfaTab = false
    private lateinit var authMethod: AuthMethod
    private lateinit var sentTo: String
    private lateinit var channel: String
    private var _binding: FragmentMfaChallengeBinding? = null
    private val binding get() = _binding!!

    companion object {
        private val TAG = MFAVerificationFragment::class.java.simpleName
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMfaChallengeBinding.inflate(inflater, container, false)

        val bundle = savedInstanceState ?: requireArguments()
        currentState = requireNotNull(bundle.getParcelable(Constants.STATE) as? Parcelable)
        returnToMfaTab = bundle.getBoolean(Constants.RETURN_TO_MFA_TAB)
        authMethod = requireNotNull(bundle.getParcelable(Constants.AUTH_METHOD) as? AuthMethod)
        sentTo = requireNotNull(bundle.getString(Constants.SENT_TO))
        channel = requireNotNull(bundle.getString(Constants.CHANNEL))

        init()

        return binding.root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(Constants.STATE, currentState)
        outState.putParcelable(Constants.AUTH_METHOD, authMethod)
        outState.putString(Constants.SENT_TO, sentTo)
        outState.putString(Constants.CHANNEL, channel)
        outState.putBoolean(Constants.RETURN_TO_MFA_TAB, returnToMfaTab)
    }

    private fun init() {
        initializeLabels()
        if (currentState is StrongAuthVerificationRequiredStateV2) {
            binding.resendChallengeText.visibility = View.GONE
        }
        initializeButtonListeners()
    }

    private fun initializeLabels() {
        binding.hintText.text = getString(R.string.mfa_challenge_hint_text_value)
            .replace("challengeChannel", channel)
            .replace("loginHint", sentTo)
    }

    private fun initializeButtonListeners() {
        binding.verifyChallenge.setOnClickListener {
            verifyChallenge()
        }

        binding.resendChallengeText.setOnClickListener {
            resendChallenge()
        }
    }

    private fun verifyChallenge() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            val emailCode = binding.challengeText.text.toString()

            when (val state = currentState) {
                is MFARequiredState -> verifyChallengeV1(state, emailCode)
                is MFAVerificationRequiredStateV2 -> verifyChallengeV2(state.submitChallenge(emailCode))
                is StrongAuthVerificationRequiredStateV2 -> verifyChallengeV2(state.submitChallenge(emailCode))
                else -> displayDialog(getString(R.string.unexpected_sdk_result_title), state.toString())
            }
        }
    }

    private suspend fun verifyChallengeV1(state: MFARequiredState, emailCode: String) {
        when (val result = state.submitChallenge(emailCode)) {
            is SignInResult.Complete -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.sign_in_successful_message),
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
            is MFASubmitChallengeError -> {
                handleMFASubmitChallengeError(result)
            }
            else -> {
                displayDialog(getString(R.string.unexpected_sdk_result_title), result.toString())
            }
        }
    }

    private fun verifyChallengeV2(result: NativeAuthResultV2) {
        when (result) {
            is NativeAuthResultV2.Complete -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.sign_in_successful_message),
                    Toast.LENGTH_SHORT
                ).show()
                finish()
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

    private fun resendChallenge() {
        clearChallengeText()

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            when (val state = currentState) {
                is MFARequiredState -> resendChallengeV1(state)
                is MFAVerificationRequiredStateV2 -> resendChallengeV2(state)
                else -> displayDialog(getString(R.string.unexpected_sdk_result_title), state.toString())
            }
        }
    }

    private suspend fun resendChallengeV1(state: MFARequiredState) {
        when (val result = state.requestChallenge(authMethod)) {
            is MFARequiredResult.VerificationRequired -> {
                currentState = result.nextState
                sentTo = result.sentTo
                channel = result.channel
                initializeLabels()
                Toast.makeText(requireContext(), getString(R.string.resend_challenge_message), Toast.LENGTH_LONG).show()
            }
            is MFARequestChallengeError -> {
                handleMFARequestChallengeError(result)
            }
            else -> {
                displayDialog(getString(R.string.unexpected_sdk_result_title), result.toString())
            }
        }
    }

    private suspend fun resendChallengeV2(state: MFAVerificationRequiredStateV2) {
        when (val result = state.resendChallenge()) {
            is NativeAuthResultV2.MFAVerificationRequired -> {
                currentState = result.nextState
                sentTo = result.sentTo
                channel = result.channel
                initializeLabels()
                Toast.makeText(requireContext(), getString(R.string.resend_challenge_message), Toast.LENGTH_LONG).show()
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

    private fun clearChallengeText() {
        binding.challengeText.text?.clear()
    }

    fun displayDialog(error: String? = null, message: String?) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle(error)
            .setMessage(message)
        val alertDialog = builder.create()
        alertDialog.show()
    }

    private fun handleMFASubmitChallengeError(error: MFASubmitChallengeError) {
        when {
            error.isInvalidChallenge() -> {
                displayDialog(error.error, error.errorMessage)
            }
            else -> {
                displayDialog(getString(R.string.unexpected_sdk_error_title), error.exception?.message ?: error.errorMessage)
            }
        }
    }

    private fun handleMFARequestChallengeError(error: MFARequestChallengeError) {
        when {
            error.isBrowserRequired() -> {
                displayDialog(error.error, error.errorMessage)
            }
            else -> {
                displayDialog(getString(R.string.unexpected_sdk_error_title), error.exception?.message ?: error.errorMessage)
            }
        }
    }

    private fun finish() {
        val fragmentManager = requireActivity().supportFragmentManager
        if (!returnToMfaTab && Configuration.useNativeAuthV2) {
            fragmentManager.popBackStack()
            return
        }

        // Pop back to MFAFragment fragment
        val name: String = MFAFragment::class.java.name
        fragmentManager.popBackStack(name, 0)
    }
}
