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

import co.paralleluniverse.galaxy.Cluster;
import co.paralleluniverse.galaxy.Store;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class configures all things data structures and below
 */
public class DataStructureModule extends AbstractModule {
    private static final Logger logger = LoggerFactory.getLogger(DataStructureModule.class);

    private final String configFile;
    private final String propertiesFile;

    /**
     * This is for tests only.
     * This will be used by the guice test runner.
     * Tests are being run out of a different directory (inside the carbon-copy-service module for example).
     * That means the path to the galaxy config files are different (*sigh*).
     * One of these days I need to find a better way to do this.
     * Options include:
     * - read the config files out of the jar
     * - guice inject config values that change in tests
     */
    @VisibleForTesting
    @SuppressWarnings("unused")
    public DataStructureModule() {
        this("../config/peer.xml", "../config/peer.properties");
    }

    public DataStructureModule(String configFile, String propertiesFile) {
        this.configFile = configFile;
        this.propertiesFile = propertiesFile;
    }

    @Override
    protected void configure() {
        logger.info("Starting galaxy grid with the following config files: {} {}", this.configFile, this.propertiesFile);
        GalaxyGridImpl g = new GalaxyGridImpl(configFile, propertiesFile);
        bind(GalaxyGrid.class).toInstance(g);
        bind(Store.class).toInstance(g.store());
        bind(Cluster.class).toInstance(g.cluster());
        // maybe we shouldn't expose the galaxy messenger in the injector anymore
        bind(co.paralleluniverse.galaxy.Messenger.class).toInstance(g.messenger());
        bind(org.carbon.copy.data.structures.Messenger.class).to(MessengerImpl.class);

        bind(InternalDataStructureFactory.class).to(DataStructureFactoryImpl.class);
        bind(DataStructureFactory.class).to(DataStructureFactoryImpl.class);

        bind(Catalog.class).to(CatalogImpl.class);

        // attach all galaxy listeners
        // there must be a better way to do this
        g.messenger().addMessageListener(DistHash.PutRequestMessageListener.TOPIC,
                new DistHash.PutRequestMessageListener(getProvider(InternalDataStructureFactory.class), getProvider(TxnManager.class), getProvider(Messenger.class)));

        g.messenger().addMessageListener(DistHash.PutResponseMessageListener.TOPIC,
                new DistHash.PutResponseMessageListener(getProvider(org.carbon.copy.data.structures.Messenger.class)));

        g.messenger().addMessageListener(DistHash.GetRequestMessageListener.TOPIC,
                new DistHash.GetRequestMessageListener(getProvider(InternalDataStructureFactory.class), getProvider(Messenger.class)));

        g.messenger().addMessageListener(DistHash.GetResponseMessageListener.TOPIC,
                new DistHash.GetResponseMessageListener(getProvider(org.carbon.copy.data.structures.Messenger.class)));
    }
}
