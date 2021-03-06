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

package org.carbon.copy.data.structures.experimental.cct;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Vector;

abstract class NodeGroup<K extends Comparable<K>> {
    private BitSet full;
    private int numEmptySlots;
    int nodeSize;
    int numNodes;

    NodeGroup(int nodeSize, int numNodes) {
        full = new BitSet(nodeSize * numNodes);
        this.nodeSize = nodeSize;
        this.numNodes = numNodes;
        this.numEmptySlots = nodeSize * numNodes;
    }

    ArrayList<ArrayList<K>> initKeyLists() {
        Vector<ArrayList<K>> vks = new Vector<>(numNodes);
        vks.setSize(numNodes);
        ArrayList<ArrayList<K>> keys = new ArrayList<>(vks);
        for (int i = 0; i < numNodes; i++) {
            keys.set(i, newKeyList());
        }
        return keys;
    }

    private ArrayList<K> newKeyList() {
        Vector<K> vk = new Vector<>(nodeSize);
        vk.setSize(nodeSize);
        return new ArrayList<>(vk);
    }

    @VisibleForTesting
    int getEmptySlots() {
        return this.numEmptySlots;
    }

    boolean hasEmptySlots() {
        return getEmptySlots() > 0;
    }

    private void markEmpty(int idx) {
        full.clear(idx);
    }

    private void markFull(int idx) {
        full.set(idx);
    }

    boolean isEmpty(int idx) {
        return !full.get(idx);
    }

    boolean isFull(int idx) {
        return full.get(idx);
    }

    int findClosestEmptySlotFrom(int idx) {
        int emptySlotIdx = full.nextClearBit(idx);
        return (emptySlotIdx < getTotalNodeGroupSize()) ? emptySlotIdx : -1;
    }

    int findClosestFullSlotFrom(int idx) {
        int fullSlotIdx = full.nextSetBit(idx);
        return (fullSlotIdx < getTotalNodeGroupSize()) ? fullSlotIdx : -1;
    }

    Pair<Integer, Integer> relativeAddress(int idx) {
        return Pair.of(idx / nodeSize, idx % nodeSize);
    }

    abstract K getKey(int idx);
    abstract K getKey(int index, int offset);

    List<K> getHighestKeys() {
        // array lists can contain nulls
        // linked lists can't -- thanks java
        List<K> l = new ArrayList<>(numNodes);
        // this might be more complicated
        // assuming there will be nulls in the list
        for (int i = 0; i < numNodes; i++) {
            l.add(getKey(i, nodeSize - 1));
        }
        return l;
    }

    @VisibleForTesting
    abstract void shiftOneRight(int from, int to);

    void doBookKeepingForPut(int idx, boolean isKeyAndValueNull, boolean isShifting) {
        // a put with nulls is a delete
        // delete and shift might transport null values
        if (isKeyAndValueNull) {
            markEmpty(idx);
            // when we shift we don't want to modify the empty slots
            // because we...yeah...only shift
            if (!isShifting) {
                this.numEmptySlots++;
            }
        } else {
            markFull(idx);
            if (!isShifting) {
                // when we shift we don't want to modify the empty slots
                // because we...yeah...only shift
                this.numEmptySlots--;
            }
        }
    }

    int getTotalNodeGroupSize() {
        return nodeSize * numNodes;
    }

    int getLevel() {
        return 0;
    }
}
