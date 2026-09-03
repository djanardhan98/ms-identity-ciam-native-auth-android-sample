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
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.microsoft.identity.nativeauth.AuthMethod
import com.microsoft.identity.nativeauth.statemachine.errors.MFARequestChallengeError
import com.microsoft.identity.nativeauth.statemachine.errors.NativeAuthErrorV2
import com.microsoft.identity.nativeauth.statemachine.results.MFARequiredResult
import com.microsoft.identity.nativeauth.statemachine.results.NativeAuthResultV2
import com.microsoft.identity.nativeauth.statemachine.states.MFARequiredState
import com.microsoft.identity.nativeauth.statemachine.states.MFARequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.RegisterStrongAuthState
import com.microsoft.identity.nativeauth.statemachine.states.StrongAuthRegistrationRequiredStateV2
import kotlinx.coroutines.launch

class PickAuthMethodFragment : Fragment() {

    private var authMethods: List<AuthMethod> = emptyList()
    private lateinit var currentState: Parcelable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bundle = savedInstanceState ?: requireArguments()
        currentState = requireNotNull(bundle.getParcelable(Constants.STATE) as? Parcelable)
        authMethods = requireNotNull(bundle.getParcelableArrayList<AuthMethod>(Constants.AUTH_METHOD_LIST))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(Constants.STATE, currentState)
        outState.putParcelableArrayList(Constants.AUTH_METHOD_LIST, ArrayList(authMethods))
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_pick_auth_method_list, container, false)

        val recyclerView: RecyclerView = view.findViewById(R.id.authMethodList)
        recyclerView.layoutManager = LinearLayoutManager(context)

        val adapter = AuthMethodRecyclerViewAdapter(authMethods, object : OnItemClickListener {
            override fun onItemClick(position: Int) {
                val selectedItem = authMethods.getOrNull(position)

                if (selectedItem == null) {
                    Toast.makeText(requireContext(), getString(R.string.unknown_error_message), Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                    return
                }

                onMethodSelected(selectedItem)
            }
        })
        recyclerView.adapter = adapter

        val dividerItemDecoration = DividerItemDecoration(context, LinearLayoutManager.VERTICAL)
        recyclerView.addItemDecoration(dividerItemDecoration)

        return view
    }

    private fun onMethodSelected(authMethod: AuthMethod) {
        when (val state = currentState) {
            is MFARequiredState -> requestMfaChallengeV1(state, authMethod)
            is MFARequiredStateV2 -> requestChallengeV2(
                call = { state.selectAuthMethod(authMethod) },
                authMethod = authMethod
            )
            is RegisterStrongAuthState -> navigateToVerificationContact(state, authMethod)
            is StrongAuthRegistrationRequiredStateV2 -> requestChallengeV2(
                call = { state.selectAuthMethod(authMethod) },
                authMethod = authMethod
            )
            else -> displayDialog(getString(R.string.unexpected_sdk_result_title), state.toString())
        }
    }

    private fun requestMfaChallengeV1(state: MFARequiredState, authMethod: AuthMethod) {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            when (val result = state.requestChallenge(authMethod)) {
                is MFARequiredResult.VerificationRequired -> {
                    navigateToMfaVerification(
                        verificationState = result.nextState,
                        sentTo = result.sentTo,
                        channel = result.channel,
                        authMethod = authMethod
                    )
                }
                is MFARequestChallengeError -> {
                    displayDialog(result.error, result.errorMessage)
                }
                else -> {
                    displayDialog(getString(R.string.unexpected_sdk_result_title), result.toString())
                }
            }
        }
    }

    private fun requestChallengeV2(
        call: suspend () -> NativeAuthResultV2,
        authMethod: AuthMethod
    ) {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            when (val result = call()) {
                is NativeAuthResultV2.MFAVerificationRequired -> {
                    navigateToMfaVerification(
                        verificationState = result.nextState,
                        sentTo = result.sentTo,
                        channel = result.channel,
                        authMethod = authMethod
                    )
                }
                is NativeAuthResultV2.StrongAuthVerificationRequired -> {
                    navigateToMfaVerification(
                        verificationState = result.nextState,
                        sentTo = result.sentTo,
                        channel = result.channel,
                        authMethod = authMethod
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
    }

    private fun navigateToMfaVerification(
        verificationState: Parcelable,
        sentTo: String,
        channel: String,
        authMethod: AuthMethod
    ) {
        val bundle = Bundle()
        bundle.putParcelable(Constants.STATE, verificationState)
        bundle.putString(Constants.SENT_TO, sentTo)
        bundle.putString(Constants.CHANNEL, channel)
        bundle.putParcelable(Constants.AUTH_METHOD, authMethod)
        bundle.putBoolean(Constants.RETURN_TO_MFA_TAB, true)

        val fragment = MFAVerificationFragment()
        fragment.arguments = bundle

        requireActivity().supportFragmentManager
            .beginTransaction()
            .setReorderingAllowed(true)
            .addToBackStack(fragment::class.java.name)
            .replace(R.id.scenario_fragment, fragment)
            .commit()
    }

    private fun navigateToVerificationContact(nextState: RegisterStrongAuthState, authMethod: AuthMethod) {
        val bundle = Bundle()
        bundle.putParcelable(Constants.STATE, nextState)
        bundle.putParcelable(Constants.AUTH_METHOD, authMethod)

        val fragment = StrongAuthVerificationContactFragment()
        fragment.arguments = bundle

        requireActivity().supportFragmentManager
            .beginTransaction()
            .setReorderingAllowed(true)
            .addToBackStack(fragment::class.java.name)
            .replace(R.id.scenario_fragment, fragment)
            .commit()
    }

    private fun displayDialog(error: String? = null, message: String?) {
        AlertDialog.Builder(requireContext())
            .setTitle(error)
            .setMessage(message)
            .create()
            .show()
    }
}