package org.distbc.data.structures;

import com.google.inject.Inject;
import org.distbc.GuiceJUnit4Runner;
import org.distbc.GuiceModules;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

@RunWith(GuiceJUnit4Runner.class)
@GuiceModules({ DataStructureModule.class, TxnManagerModule.class })
public class GalaxyTupleTest {
    @Inject
    private InternalDataStructureFactory dsFactory;

    @Inject
    private TxnManager txnManager;

    @Test
    public void testBasic() throws IOException {
        Txn t = txnManager.beginTransaction();
        DataBlock<Integer, Tuple> db = dsFactory.newDataBlock(t);

        Tuple tup1 = newTuple(3);
        tup1.put(0, "Hello");
        tup1.put(1, "World");
        tup1.put(2, "some_text");

        db.put(99, tup1, t);

        t.commit();

        DataBlock<Integer, Tuple> db2 = dsFactory.loadDataBlock(db.getId());
        Tuple loadedTuple = db2.get(99);
        assertEquals("World", loadedTuple.get(1));
    }

    private Tuple newTuple(int size) {
        return new Tuple(size);
    }
}
