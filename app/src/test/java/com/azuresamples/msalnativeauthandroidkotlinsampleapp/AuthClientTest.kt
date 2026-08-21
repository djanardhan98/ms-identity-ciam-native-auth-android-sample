//  Copyright (c) Microsoft Corporation.
//  All rights reserved.
//
//  This code is licensed under the MIT License.

package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AuthClientTest {

    @Test
    fun initializeCanBeCalledAfterActivityRecreation() {
        val context = RuntimeEnvironment.getApplication()

        AuthClient.initialize(context)
        AuthClient.initialize(context)
    }
}
