package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentMfaChallengeBinding
import com.microsoft.identity.nativeauth.AuthMethod
import com.microsoft.identity.nativeauth.statemachine.errors.MFARequestChallengeError
import com.microsoft.identity.nativeauth.statemachine.errors.MFARequestChallengeErrorV2
import com.microsoft.identity.nativeauth.statemachine.errors.MFASubmitChallengeError
import com.microsoft.identity.nativeauth.statemachine.errors.MFASubmitChallengeErrorV2
import com.microsoft.identity.nativeauth.statemachine.errors.NativeAuthErrorV2
import com.microsoft.identity.nativeauth.statemachine.results.MFARequiredResult
import com.microsoft.identity.nativeauth.statemachine.results.NativeAuthResultV2
import com.microsoft.identity.nativeauth.statemachine.results.SignInResult
import com.microsoft.identity.nativeauth.statemachine.states.MFARequiredState
import com.microsoft.identity.nativeauth.statemachine.states.MFARequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.MFAVerificationRequiredStateV2
import kotlinx.coroutines.launch

class MFAVerificationFragment : Fragment() {
    private var currentState: MFARequiredState? = null
    private var mfaStateV2: MFARequiredStateV2? = null
    private var verificationStateV2: MFAVerificationRequiredStateV2? = null
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
        if (Configuration.useNativeAuthV2) {
            mfaStateV2 = bundle.getParcelable(Constants.MFA_STATE) as? MFARequiredStateV2
            verificationStateV2 = bundle.getParcelable(Constants.STATE) as? MFAVerificationRequiredStateV2
        } else {
            currentState = bundle.getParcelable(Constants.STATE) as? MFARequiredState
        }
        authMethod = (bundle.getParcelable(Constants.AUTH_METHOD) as? AuthMethod)!!
        sentTo = bundle.getString(Constants.SENT_TO)!!
        channel = bundle.getString(Constants.CHANNEL)!!

        init()

        return binding.root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (Configuration.useNativeAuthV2) {
            outState.putParcelable(Constants.MFA_STATE, mfaStateV2)
            outState.putParcelable(Constants.STATE, verificationStateV2)
        } else {
            outState.putParcelable(Constants.STATE, currentState)
        }
        outState.putParcelable(Constants.AUTH_METHOD, authMethod)
        outState.putString(Constants.SENT_TO, sentTo)
        outState.putString(Constants.CHANNEL, channel)
    }

    private fun init() {
        initializeLabels()
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

            if (Configuration.useNativeAuthV2) {
                verifyChallengeV2(emailCode)
                return@launchWhenStarted
            }

            val actionResult = currentState!!.submitChallenge(emailCode)

            when (actionResult) {
                is SignInResult.Complete -> {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.sign_in_successful_message),
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
                is MFASubmitChallengeError -> {
                    handleMFASubmitChallengeError(actionResult)
                }
                else -> {
                    displayDialog(getString(R.string.unexpected_sdk_result_title), actionResult.toString())
                }
            }
        }
    }

    private suspend fun verifyChallengeV2(emailCode: String) {
        when (val result = verificationStateV2?.submitChallenge(emailCode)) {
            is NativeAuthResultV2.Complete -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.sign_in_successful_message),
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
            is MFASubmitChallengeErrorV2 -> {
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
            null -> {
                displayDialog(getString(R.string.unexpected_sdk_result_title), "No MFA verification state was available.")
            }
            else -> {
                displayDialog(getString(R.string.unexpected_sdk_result_title), result.toString())
            }
        }
    }

    private fun resendChallenge() {
        clearChallengeText()

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            if (Configuration.useNativeAuthV2) {
                resendChallengeV2()
                return@launchWhenStarted
            }

            val actionResult = currentState!!.requestChallenge(authMethod)

            when (actionResult) {
                is MFARequiredResult.VerificationRequired -> {
                    currentState = actionResult.nextState
                    Toast.makeText(requireContext(), getString(R.string.resend_challenge_message), Toast.LENGTH_LONG).show()
                }
                is MFARequestChallengeError -> {
                    handleMFARequestChallengeError(actionResult)
                }
                else -> {
                    displayDialog(getString(R.string.unexpected_sdk_result_title), actionResult.toString())
                }
            }
        }
    }

    private suspend fun resendChallengeV2() {
        when (val result = mfaStateV2?.selectAuthMethod(authMethod)) {
            is NativeAuthResultV2.MFAVerificationRequired -> {
                verificationStateV2 = result.nextState
                sentTo = result.sentTo
                channel = result.channel
                initializeLabels()
                Toast.makeText(requireContext(), getString(R.string.resend_challenge_message), Toast.LENGTH_LONG).show()
            }
            is MFARequestChallengeErrorV2 -> {
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
            null -> {
                displayDialog(getString(R.string.unexpected_sdk_result_title), "No MFA state was available.")
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
        if (Configuration.useNativeAuthV2) {
            fragmentManager.popBackStack()
            return
        }

        // Pop back to MFAFragment fragment
        val name: String = MFAFragment::class.java.name
        fragmentManager.popBackStack(name, 0)
    }
}
