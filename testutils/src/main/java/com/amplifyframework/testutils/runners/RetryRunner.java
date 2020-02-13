/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amplifyframework.testutils.runners;

import android.util.Log;

import org.junit.Ignore;
import org.junit.internal.AssumptionViolatedException;
import org.junit.internal.runners.model.EachTestNotifier;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runner.notification.StoppedByUserException;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

/**
 * Retry Runner for as a temporary work-around for flaky instrumentation tests.
 *
 * Some of the tests may fail the first attempt due to various reasons such as
 * unstable connection. This test runner will attempt to run a test suite one
 * more time if it fails the first time.
 *
 * This SHOULD NOT be used whenever possible. If a test is flaky, it should be
 * fixed to be more reliably passable the first time rather than relying on
 * this test runner.
 *
 * The source code for this RetryRunner was obtained from an article in
 * automationrhapsody.com.
 *
 * @author  Lyudmil Latinov
 * @version 1.0
 * @since   2018-04-05
 * @see     <a href="https://automationrhapsody.com/retry-junit-failed-tests-immediatelly/">source article</a>
 */
public final class RetryRunner extends BlockJUnit4ClassRunner {

    private static final String TAG = RetryRunner.class.getSimpleName();
    private static final int RETRY_COUNT = 2;

    /**
     * Instantiates a RetryRunner to run tests.
     * @param clazz class being tested
     * @throws InitializationError if constructor fails unexpectedly
     */
    public RetryRunner(Class<?> clazz) throws InitializationError {
        super(clazz);
    }

    @Override
    public void run(final RunNotifier notifier) {
        EachTestNotifier testNotifier = new EachTestNotifier(notifier, getDescription());
        Statement statement = classBlock(notifier);
        try {
            statement.evaluate();
        } catch (AssumptionViolatedException ave) {
            testNotifier.fireTestIgnored();
        } catch (StoppedByUserException sbue) {
            throw sbue;
        } catch (Throwable t) {
            Log.d(TAG, "Retry class: " + getDescription().getDisplayName());
            retry(testNotifier, statement, t, getDescription());
        }
    }

    @Override
    protected void runChild(final FrameworkMethod method, RunNotifier notifier) {
        Description description = describeChild(method);
        if (method.getAnnotation(Ignore.class) != null) {
            notifier.fireTestIgnored(description);
        } else {
            runTest(methodBlock(method), description, notifier);
        }
    }

    private void runTest(Statement statement, Description description, RunNotifier notifier) {
        EachTestNotifier eachNotifier = new EachTestNotifier(notifier, description);
        eachNotifier.fireTestStarted();
        try {
            statement.evaluate();
        } catch (AssumptionViolatedException e) {
            eachNotifier.addFailedAssumption(e);
        } catch (Throwable e) {
            Log.d(TAG, "Retry test: " + description.getDisplayName());
            retry(eachNotifier, statement, e, description);
        } finally {
            eachNotifier.fireTestFinished();
        }
    }

    private void retry(EachTestNotifier notifier, Statement statement, Throwable currentThrowable, Description info) {
        int failedAttempts = 0;
        Throwable caughtThrowable = currentThrowable;
        while (RETRY_COUNT > failedAttempts) {
            try {
                Log.d(TAG, "Retry attempt " + (failedAttempts + 1) + " for " + info.getDisplayName());
                statement.evaluate();
                return;
            } catch (Throwable t) {
                failedAttempts++;
                caughtThrowable = t;
            }
        }
        notifier.addFailure(caughtThrowable);
    }
}