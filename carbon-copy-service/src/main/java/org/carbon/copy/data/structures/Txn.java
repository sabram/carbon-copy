/*
 *
 *  Copyright 2017 Marco Helmich
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.carbon.copy.data.structures;

import co.paralleluniverse.galaxy.Store;
import co.paralleluniverse.galaxy.StoreTransaction;
import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.carbon.copy.data.structures.DataStructure.TIMEOUT_SECS;

public class Txn {
    private static Logger logger = LoggerFactory.getLogger(Txn.class);

    private final Store store;
    private final StoreTransaction stxn;
    // any data structures in this set will be upserted
    // this obviously doesn't work well when data is deleted ;)
    private final Set<DataStructure> changedObjects = new HashSet<>();
    // all data structures in this set will be deleted
    private final Set<DataStructure> deletedObjects = new HashSet<>();

    Txn(Store store) {
        this(store, store.beginTransaction());
    }

    Txn(Store store, StoreTransaction stxn) {
        this.store = store;
        this.stxn = stxn;
    }

    public void commit() throws IOException {
        try {
            // this is a very naive implementation for now
            // all the work is happening at commit time
            // this is pretty pessimistic
            // it might be better to do this as early as
            // when we are getting the lock on the object
            List<ListenableFuture> f1 = changedObjects.stream()
                    .filter(ds -> !deletedObjects.contains(ds))
                    .map(ds -> ds.asyncUpsert(ds, this))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            List<ListenableFuture> f2 = deletedObjects.stream()
                    .map(ds -> ds.asyncDelete(ds, this))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            f1.forEach(f -> wait(f, TIMEOUT_SECS));
            f2.forEach(f -> wait(f, TIMEOUT_SECS));

            store.commit(stxn);
        } catch (Exception xcp) {
            logger.error("Committing txn failed with {}", changedObjects);
            throw new IOException(xcp);
        } finally {
            changedObjects.clear();
            deletedObjects.clear();
        }
    }

    private void wait(ListenableFuture f, int timeoutSecs) {
        try {
            f.get(timeoutSecs, TimeUnit.SECONDS);
        } catch (Exception xcp) {
            throw new RuntimeException(xcp);
        }
    }

    public void abort() throws IOException {
        try {
            store.abort(stxn);
        } catch (Exception xcp) {
            throw new IOException(xcp);
        } finally {
            changedObjects.clear();
        }
    }

    public void rollback() {
        store.rollback(stxn);
    }

    StoreTransaction getStoreTransaction() {
        return stxn;
    }

    void addToChangedObjects(DataStructure ds) {
        changedObjects.add(ds);
    }

    void addToDeletedObjects(DataStructure ds) {
        changedObjects.remove(ds);
        deletedObjects.add(ds);
    }
}
