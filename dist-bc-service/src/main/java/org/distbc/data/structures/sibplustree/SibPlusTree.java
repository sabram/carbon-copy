package org.distbc.data.structures.sibplustree;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Vector;

/**
 * It's instrumental to only move to the right!
 * @param <K>
 * @param <V>
 */
public class SibPlusTree<K extends Comparable<K>, V extends Comparable<V>> {
    private final int numberOfNodesInInternalNodeGroup;
    private final int numberOfNodesInLeafNodeGroup;
    private final int internalNodeSize;
    private final int leafNodeSize;

    private InternalNodeGroup<K> root;

    public SibPlusTree() {
        this(3, 3);
    }

    SibPlusTree(int leafNodeSize, int numberOfNodesInLeafNodeGroup) {
        this.numberOfNodesInLeafNodeGroup = numberOfNodesInLeafNodeGroup;
        // this has to be leafNodeSize - 1
        // otherwise pushing up high keys doesn't work...duh
        this.numberOfNodesInInternalNodeGroup = leafNodeSize - 1;
        this.internalNodeSize = numberOfNodesInLeafNodeGroup - 1;
        this.leafNodeSize = leafNodeSize;

        // we need at least a tree with depth 3
        // in order to get basic routing right
        root = newInternalNodeGroup(2);
        InternalNodeGroup<K> intermediate = newInternalNodeGroup(1);
        root.setChildNodeOnNode(0, intermediate);
        LeafNodeGroup<K, V> lng = newLeafNodeGroup();
        intermediate.setChildNodeOnNode(0, lng);
        System.err.println(toString());
    }

    InternalNodeGroup<K> newInternalNodeGroup(int level) {
        assert level > 0;
        return new InternalNodeGroup<>(level, this.internalNodeSize, this.numberOfNodesInInternalNodeGroup);
    }

    LeafNodeGroup<K, V> newLeafNodeGroup() {
        return new LeafNodeGroup<>(this.leafNodeSize, this.numberOfNodesInLeafNodeGroup);
    }

    public synchronized void put(K key, V value) {
        List<Breadcrumb<K>> breadcrumbs = searchTree(key, root);
        // happens after a root node split for example
        if (breadcrumbs.isEmpty()) {
            splitNodes(root, Collections.emptyList());
            System.err.println(toString());
            put(key, value);
        } else {
            Breadcrumb<K> bc = breadcrumbs.get(breadcrumbs.size() - 1);
            LeafNodeGroup<K, V> lng = getLeafNodeGroup(bc);
            NodeIdxAndIdx insertionIdx = findInsertionIndex(key, lng, bc.indexes);
            NodeIdxAndIdx emptyIdx = lng.findClosestEmptySlotFrom(insertionIdx);
            if (NodeIdxAndIdx.INVALID.equals(emptyIdx)) {
                splitNodes(lng, breadcrumbs);
                put(key, value);
            } else {
                // there's still space in lng
                lng.maybeShiftOneRight(insertionIdx, emptyIdx);
                lng.put(insertionIdx, key, value);
                // do the highest keys business
                doHighKeyBusiness(breadcrumbs, insertionIdx, emptyIdx);
            }
        }
        System.err.println(toString());
    }

    private List<Breadcrumb<K>> searchTree(K key, InternalNodeGroup<K> ing) {
        NodeGroup<K> ng = ing;
        Breadcrumb parentBC = null;
        List<Breadcrumb<K>> breadcrumbs = new ArrayList<>(ing.getLevel());

        do {
            // I can do that because I know better
            // level > 1 :)
            InternalNodeGroup<K> tmp = (InternalNodeGroup<K>) ng;
            Breadcrumb<K> bc = searchInternalNodeGroup(key, tmp, parentBC);
            breadcrumbs.add(bc);
            // might happen when the tree is full
            // we gotta split the root
            if (NodeIdxAndIdx.INVALID.equals(bc.indexes)) {
                return Collections.emptyList();
            }
            ng = tmp.getChildForNode(bc.indexes.nodeIdx);
            if (ng == null && tmp.getLevel() > 1) {
                ng = newInternalNodeGroup(tmp.getLevel() - 1);
                tmp.setChildNodeOnNode(bc.indexes.nodeIdx, ng);
            }
            parentBC = bc;
        } while (ng != null && ng.getLevel() > 0);

        return breadcrumbs;
    }

    private LeafNodeGroup<K, V> getLeafNodeGroup(Breadcrumb<K> bc) {
        LeafNodeGroup<K, V> lng = (LeafNodeGroup<K, V>) bc.ing.getChildForNode(bc.indexes.nodeIdx);
        if (lng == null) {
            lng = newLeafNodeGroup();
            bc.ing.setChildNodeOnNode(bc.indexes.nodeIdx, lng);
        }
        return (LeafNodeGroup<K, V>) bc.ing.getChildForNode(bc.indexes.nodeIdx);
    }

    private Breadcrumb<K> searchInternalNodeGroup(K key, InternalNodeGroup<K> ing, @Nullable Breadcrumb parentBC) {
        int startIdx = (parentBC != null) ? parentBC.indexes.idx : searchStartIdx(key, ing);
        NodeIdxAndIdx iis = (startIdx > -1) ? searchInternalNodeGroup(key, ing, startIdx) : NodeIdxAndIdx.INVALID;
        return Breadcrumb.of(ing, iis);
    }

    private int searchStartIdx(K key, InternalNodeGroup<K> ing) {
        NodeIdxAndIdx p = NodeIdxAndIdx.of(0, 0);
        while (!NodeIdxAndIdx.INVALID.equals(p)
                && !isNullAndLast(ing, p.nodeIdx, p.idx)
                || (ing.getKey(p) != null && compareTo(key, ing.getKey(p)) > 0)
              ) {
                p = ing.plusOne(p);
        }
        return p.nodeIdx;
    }

    private NodeIdxAndIdx searchInternalNodeGroup(K key, InternalNodeGroup<K> ing, int startIdx) {
        for (int j = 0; j < internalNodeSize; j++) {
            if (isNullAndLast(ing, startIdx, j) || (ing.getKey(startIdx, j) != null && compareTo(key, ing.getKey(startIdx, j)) <= 0)) {
                return NodeIdxAndIdx.of(startIdx, j);
            }
        }

        return NodeIdxAndIdx.of(startIdx, internalNodeSize);
    }

    private NodeIdxAndIdx findSearchIndex(K key, LeafNodeGroup<K, V>  lng, NodeIdxAndIdx startIdx) {
        return searchLeafNodeGroupForGets(key, lng, startIdx.nodeIdx);
    }

    private NodeIdxAndIdx findInsertionIndex(K key, LeafNodeGroup<K, V>  lng, NodeIdxAndIdx startIdx) {
        return searchLeafNodeGroup(key, lng, startIdx.idx);
    }

    private NodeIdxAndIdx searchLeafNodeGroup(K key, LeafNodeGroup<K, V> ing, int startIdx) {
        for (int i = startIdx; i < numberOfNodesInLeafNodeGroup; i++) {
            for (int j = 0; j < leafNodeSize; j++) {
                if (isNullAndLast(ing, i, j) || (ing.getKey(i, j) != null && compareTo(key, ing.getKey(i, j)) < 0)) {
                    return NodeIdxAndIdx.of(i, j);
                }
            }
        }

        return NodeIdxAndIdx.of(numberOfNodesInLeafNodeGroup, 0);
    }

    private boolean isNullAndLast(NodeGroup<K> ng, int nodeIdx, int idx) {
        return ng.getKey(nodeIdx, idx) == null && NodeIdxAndIdx.INVALID.equals(
                ng.findClosestFullSlotFrom(NodeIdxAndIdx.of(nodeIdx, idx)));
    }

    private NodeIdxAndIdx searchLeafNodeGroupForGets(K key, LeafNodeGroup<K, V> ing, int startIdx) {
        for (int i = startIdx; i < numberOfNodesInLeafNodeGroup; i++) {
            for (int j = 0; j < leafNodeSize; j++) {
                if (isNullAndLast(ing, i, j) || compareTo(key, ing.getKey(i, j)) <= 0) {
                    return NodeIdxAndIdx.of(i, j);
                }
            }
        }

        // for all I can tell now this shouldn't really happen
        // unless we want a key bigger than anything in the tree
        return NodeIdxAndIdx.of(numberOfNodesInLeafNodeGroup, 0);
    }

    private void splitNodes(LeafNodeGroup<K, V> lng, List<Breadcrumb<K>> breadcrumbs) {
        LeafNodeGroup<K, V> newLng = lng.split();
        Breadcrumb<K> bc = breadcrumbs.remove(breadcrumbs.size() - 1);
        NodeIdxAndIdx next = NodeIdxAndIdx.of(bc.indexes.nodeIdx + 1, bc.indexes.idx);
        NodeIdxAndIdx nextEmpty = bc.ing.findNodeIndexOfEmptyNodeFrom(next);
        if (NodeIdxAndIdx.INVALID.equals(nextEmpty)) {
            splitNodes(bc.ing, breadcrumbs);
        }
        bc.ing.shiftNodesOneRight(next, nextEmpty);
        bc.ing.setChildNodeOnNode(next.nodeIdx, newLng);
        bc.ing.setChildNodeOnNode(bc.indexes.nodeIdx, lng);
    }

    private void splitNodes(InternalNodeGroup<K> ing, List<Breadcrumb<K>> breadcrumbs) {
        if (breadcrumbs.isEmpty()) {
            InternalNodeGroup<K> newRoot = newInternalNodeGroup(ing.getLevel() + 1);
            InternalNodeGroup<K> newIng = ing.split();
            newRoot.setChildNodeOnNode(0, ing);
            newRoot.setChildNodeOnNode(1, newIng);

            NodeIdxAndIdx indexes = NodeIdxAndIdx.of(0, 0);
            while (!NodeIdxAndIdx.INVALID.equals(indexes)) {
                K key = newRoot.getChildForNode(indexes.nodeIdx).getHighestKeyForNode(indexes.idx);
                newRoot.put(indexes, key);
                indexes = newRoot.plusOne(indexes);
            }
            this.root = newRoot;
        } else {
            InternalNodeGroup<K> newIng = ing.split();
            Breadcrumb<K> bc = breadcrumbs.remove(breadcrumbs.size() - 1);
            NodeIdxAndIdx next = NodeIdxAndIdx.of(bc.indexes.nodeIdx + 1, bc.indexes.idx);
            NodeIdxAndIdx nextEmpty = bc.ing.findNodeIndexOfEmptyNodeFrom(next);
            if (NodeIdxAndIdx.INVALID.equals(nextEmpty)) {
                splitNodes(bc.ing, breadcrumbs);
            }
            bc.ing.shiftNodesOneRight(next, nextEmpty);
            bc.ing.setChildNodeOnNode(next.nodeIdx, newIng);
            bc.ing.setChildNodeOnNode(bc.indexes.nodeIdx, ing);
        }
    }

    void doHighKeyBusiness(List<Breadcrumb<K>> breadcrumbs, NodeIdxAndIdx insertionIdx, NodeIdxAndIdx emptyIdx) {
        Breadcrumb<K> parent = breadcrumbs.get(breadcrumbs.size() - 1);
        boolean setHighKey =
                emptyIdx.nodeIdx < (leafNodeSize - 1)
                        && emptyIdx.idx > 0
                        && emptyIdx.idx % (leafNodeSize - 1) == 0;

        boolean shiftedHighKey =
                ((emptyIdx.nodeIdx * leafNodeSize + emptyIdx.idx) - (insertionIdx.nodeIdx * leafNodeSize + insertionIdx.idx)) / leafNodeSize > 0;

        if (setHighKey) {
            LeafNodeGroup<K, V> lng = (LeafNodeGroup<K, V>) parent.ing.getChildForNode(parent.indexes.nodeIdx);
            K key = lng.getKey(emptyIdx);
            parent.ing.put(parent.indexes, key);
        } else if (shiftedHighKey) {
            // find all the high keys between the indexes
            // move them up
            LeafNodeGroup<K, V> lng = (LeafNodeGroup<K, V>) parent.ing.getChildForNode(parent.indexes.nodeIdx);
            List<NodeIdxAndIdx> highKeys = getHighKeysBetweenInsertionAndEmpty(insertionIdx, emptyIdx);
            for (NodeIdxAndIdx indexes : highKeys) {
                K key = lng.getKey(indexes);
                parent.ing.put(NodeIdxAndIdx.of(parent.indexes.nodeIdx, indexes.nodeIdx), key);
            }
        }

        NodeGroup<K> current = parent.ing.getChildForNode(parent.indexes.nodeIdx);
        boolean shouldDoIt =
                emptyIdx.nodeIdx == (numberOfNodesInLeafNodeGroup - 1)
                        && emptyIdx.idx > 0
                        && emptyIdx.idx % (leafNodeSize - 1) == 0;

        for (int i = breadcrumbs.size() - 2; shouldDoIt && i >= 0; i--) {
            Breadcrumb<K> grandParent = breadcrumbs.get(i);
            grandParent.ing.put(grandParent.indexes, current.getHighestKey());
            Breadcrumb<K> bc = breadcrumbs.get(i + 1);
            shouldDoIt =
                    bc.indexes.nodeIdx == (numberOfNodesInInternalNodeGroup - 1)
                            && bc.indexes.idx > 0
                            && bc.indexes.idx % (internalNodeSize - 1) == 0;
            current = grandParent.ing;
        }

    }

    private List<NodeIdxAndIdx> getHighKeysBetweenInsertionAndEmpty(NodeIdxAndIdx insertionIdx, NodeIdxAndIdx emptyIdx) {
        List<NodeIdxAndIdx> highKeys = new LinkedList<>();
        for (int i = insertionIdx.nodeIdx; i < emptyIdx.nodeIdx; i++) {
            highKeys.add(NodeIdxAndIdx.of(i, leafNodeSize - 1));
        }
        return highKeys;
    }

    public Set<V> get(K key) {
        List<Breadcrumb<K>> breadcrumbs = searchTree(key, root);
        Breadcrumb<K> bc = breadcrumbs.get(breadcrumbs.size() - 1);
        LeafNodeGroup<K, V> lng = getLeafNodeGroup(bc);
        NodeIdxAndIdx idx = findSearchIndex(key, lng, bc.indexes);
        Set<V> resultSet = new HashSet<>();
        while (lng != null
                && lng.getKey(idx) != null
                && key.equals(lng.getKey(idx))) {
            resultSet.add(lng.getValue(idx));
            idx = lng.plusOne(idx);
            if (NodeIdxAndIdx.INVALID.equals(idx)) {
                lng = lng.next;
                idx = NodeIdxAndIdx.of(0, 0);
            }
        }
        return resultSet;
    }

    private int compareTo(K k1, K k2) {
        if (k2 == null) {
            return -1;
        } else if (k1 == null) {
            return 1;
        } else {
            return k1.compareTo(k2);
        }
    }

    @Override
    public String toString() {
        Vector<ArrayList<NodeGroup<K>>> v = new Vector<>(root.getLevel() + 1);
        v.setSize(root.getLevel() + 1);
        ArrayList<ArrayList<NodeGroup<K>>> nodeGroups = new ArrayList<>(v);
        traverseIng(root, nodeGroups);

        StringBuilder sb = new StringBuilder();

        for (int i = nodeGroups.size(); i > 0; i--) {
            for (int j = 0; j < nodeGroups.get(i - 1).size(); j++) {
                NodeGroup<K> ng = nodeGroups.get(i - 1).get(j);
                sb.append(
                        (ng != null) ? ng.toString() : "NULL"
                )
                        .append(" || ");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private void traverseIng(InternalNodeGroup<K> ng, ArrayList<ArrayList<NodeGroup<K>>> nodeGroups) {
        if (nodeGroups.get(ng.getLevel()) == null) {
            nodeGroups.set(ng.getLevel(), new ArrayList<>());
        }
        nodeGroups.get(ng.getLevel()).add(ng);
        for (int i = 0; i < numberOfNodesInInternalNodeGroup; i++) {
            if (ng.getLevel() > 1) {
                InternalNodeGroup<K> ing = (InternalNodeGroup<K>)ng.getChildForNode(i);
                if (ing == null) {
                    if (nodeGroups.get(ng.getLevel() - 1) == null) {
                        nodeGroups.set(ng.getLevel() - 1, new ArrayList<>());
                    }
                    nodeGroups.get(ng.getLevel() - 1).add(null);
                } else {
                    traverseIng((InternalNodeGroup<K>)ng.getChildForNode(i), nodeGroups);
                }
            } else {
                traverseLng((LeafNodeGroup<K, V>)ng.getChildForNode(i), nodeGroups);
            }
        }
    }

    private void traverseLng(LeafNodeGroup<K, V> ng, ArrayList<ArrayList<NodeGroup<K>>> nodeGroups) {
        if (nodeGroups.get(0) == null) {
            nodeGroups.set(0, new ArrayList<>());
        }
        nodeGroups.get(0).add(ng);
    }
}
