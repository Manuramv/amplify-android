/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package com.amplifyframework.datastore.storage.sqlite;

import android.os.StrictMode;

import com.amplifyframework.core.Consumer;
import com.amplifyframework.core.model.ModelSchema;
import com.amplifyframework.datastore.DataStoreException;
import com.amplifyframework.testmodels.personcar.AmplifyCliGeneratedModelProvider;
import com.amplifyframework.testmodels.personcar.RandomVersionModelProvider;
import com.amplifyframework.testutils.Await;
import com.amplifyframework.util.CollectionUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Test the functionality of {@link SQLiteStorageAdapter} with model update operations.
 */
public final class ModelUpgradeSQLiteInstrumentedTest {
    private static final long SQLITE_OPERATION_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(1);
    private static final String DATABASE_NAME = "AmplifyDatastore.db";

    private SQLiteStorageAdapter sqliteStorageAdapter;
    private AmplifyCliGeneratedModelProvider modelProvider;
    private RandomVersionModelProvider modelProviderThatUpgradesVersion;

    /**
     * Enable strict mode for catching SQLite leaks.
     */
    @BeforeClass
    public static void enableStrictMode() {
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
            .detectLeakedSqlLiteObjects()
            .detectLeakedClosableObjects()
            .penaltyLog()
            .penaltyDeath()
            .build());
    }

    /**
     * Setup the required information for SQLiteStorageHelper construction.
     */
    @Before
    public void setUp() {
        getApplicationContext().deleteDatabase(DATABASE_NAME);

        modelProvider = AmplifyCliGeneratedModelProvider.singletonInstance();
        modelProviderThatUpgradesVersion = RandomVersionModelProvider.singletonInstance();
    }

    /**
     * Drop all tables and database, terminate and delete the database.
     * @throws DataStoreException On failure to terminate adapter
     */
    @After
    public void tearDown() throws DataStoreException {
        sqliteStorageAdapter.terminate();
        getApplicationContext().deleteDatabase(DATABASE_NAME);
    }

    /**
     * Asserts if the model version change updates the new version in local storage.
     * @throws DataStoreException On failure to terminate adapter
     */
    @Test
    public void modelVersionStoredCorrectlyBeforeAndAfterUpgrade() throws DataStoreException {
        // Initialize StorageAdapter with models
        sqliteStorageAdapter = SQLiteStorageAdapter.forModels(modelProvider);
        List<ModelSchema> firstResults = Await.result(
            SQLITE_OPERATION_TIMEOUT_MS,
            (Consumer<List<ModelSchema>> onResult, Consumer<DataStoreException> onError) ->
                sqliteStorageAdapter.initialize(getApplicationContext(), onResult, onError)
        );
        // Assert if initialize succeeds.
        assertFalse(CollectionUtils.isNullOrEmpty(firstResults));

        // Assert if version is stored correctly
        String expectedVersion = modelProvider.version();
        PersistentModelVersion persistentModelVersion =
                PersistentModelVersion
                        .fromLocalStorage(sqliteStorageAdapter)
                        .blockingGet()
                        .next();
        String actualVersion = persistentModelVersion.getVersion();
        assertEquals(expectedVersion, actualVersion);

        // Terminate storage adapter and create a new storage adapter with
        // a model provider that upgrades version to mimic restartability with
        // version update.
        sqliteStorageAdapter.terminate();
        sqliteStorageAdapter = null;

        sqliteStorageAdapter = SQLiteStorageAdapter.forModels(modelProviderThatUpgradesVersion);

        // Now, initialize storage adapter with the new models
        List<ModelSchema> secondResults = Await.result(
            SQLITE_OPERATION_TIMEOUT_MS,
            (Consumer<List<ModelSchema>> onResult, Consumer<DataStoreException> onError) ->
                sqliteStorageAdapter.initialize(getApplicationContext(), onResult, onError)
        );
        assertFalse(CollectionUtils.isNullOrEmpty(secondResults));

        // Check if the new version is stored in local storage.
        expectedVersion = modelProviderThatUpgradesVersion.version();
        persistentModelVersion = PersistentModelVersion
                .fromLocalStorage(sqliteStorageAdapter)
                .blockingGet()
                .next();
        actualVersion = persistentModelVersion.getVersion();
        assertEquals(expectedVersion, actualVersion);
    }
}
