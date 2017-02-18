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
public class GalaxyBTreeTest {
    @Inject
    private InternalDataStructureFactory dsFactory;

    @Inject
    private TxnManager txnManager;

    @Test
    public void testBasicWithOneNode() throws IOException {
        long treeId;
        Txn txn = txnManager.beginTransaction();
        BTree<String, String> t = dsFactory.newBTree(txn);
        t.put("key_1", "value_1", txn);
        t.put("key_2", "value_2", txn);
        t.put("key_3", "value_3", txn);
        treeId = t.getId();
        txn.commit();

        BTree<String, String> t2 = dsFactory.loadBTree(treeId);
        assertEquals("value_2", t2.get("key_2"));
        assertEquals("value_3", t2.get("key_3"));
        assertEquals("value_1", t2.get("key_1"));
    }

    @Test
    public void testBasicWithMultipleNodes() throws IOException {
        long treeId;
        int count = 10;
        Txn txn = txnManager.beginTransaction();
        BTree<Integer, String> t = dsFactory.newBTree(txn);
        for (int i = 0; i < count; i++) {
            t.put(i, "value_" + i, txn);
        }
        treeId = t.getId();
        txn.commit();

        BTree<Integer, String> t2 = dsFactory.loadBTree(treeId);
        for (int i = 0; i < count; i++) {
            assertEquals("value_" + i, t2.get(i));
        }
    }

    @Test
    public void testBasicWithMultipleNodesReadInViaDump() throws IOException {
        long treeId;
        int count = 10;
        Txn txn = txnManager.beginTransaction();
        BTree<Integer, String> t = dsFactory.newBTree(txn);
        for (int i = 0; i < count; i++) {
            t.put(i, "value_" + i, txn);
        }
        treeId = t.getId();
        txn.commit();
        System.err.println(t.dump());

        BTree<Integer, String> t2 = dsFactory.loadBTree(treeId);
        System.err.println(t2.dump());
        for (int i = 0; i < count; i++) {
            assertEquals("value_" + i, t2.get(i));
        }
    }

    @Test
    public void testValueIterationWithMultipleNodes() throws IOException {
        long treeId;
        int count = 10;
        Txn txn = txnManager.beginTransaction();
        BTree<Integer, String> t = dsFactory.newBTree(txn);
        for (int i = 0; i < count; i++) {
            t.put(i, "value_" + i, txn);
        }
        treeId = t.getId();
        txn.commit();

        BTree<Integer, String> t2 = dsFactory.loadBTree(treeId);
        int assertionCount = 0;
        for (Integer i : t2.keys()) {
            assertEquals(Integer.valueOf(assertionCount), i);
            assertionCount++;
        }

        assertEquals(count, assertionCount);
    }
}