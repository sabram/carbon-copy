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

package org.carbon.copy;

import com.google.inject.Inject;
import org.carbon.copy.data.structures.Catalog;
import org.carbon.copy.data.structures.DataStructureFactory;
import org.carbon.copy.data.structures.DataStructureModule;
import org.carbon.copy.data.structures.TxnManager;
import org.carbon.copy.data.structures.TxnManagerModule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiceJUnit4Runner.class)
@GuiceModules({ DataStructureModule.class, TxnManagerModule.class })
public class CarbonCopyResourceImplTest {

    @Inject
    private DataStructureFactory dsFactory;

    @Inject
    private TxnManager txnManager;

    @Inject
    private Catalog catalog;

    @Test(expected = IllegalArgumentException.class)
    @Ignore
    public void testNoTablePresent() throws Exception {
        CarbonCopyResource r = new CarbonCopyResourceImpl(dsFactory, txnManager, catalog);
        r.query("select * from table1");
    }
}
