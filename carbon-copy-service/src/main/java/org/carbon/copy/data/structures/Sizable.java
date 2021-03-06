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

import co.paralleluniverse.common.io.Persistable;
import org.xerial.snappy.Snappy;

import java.util.UUID;

/**
 * The purpose of this class is it hide away all the code that worries about byte sizing data structures (or data in general).
 * It provides an implementation for the size method from Galaxy that calculates the byte size of the current object.
 * I did this because from classes don't extend DataStructure (like Tuple or GUID) but they need to be serialized
 * (and hence their byte size needs to be calculated).
 */
abstract class Sizable {
    static final int MAX_BYTE_SIZE = 32768;

    // actually there's a +1 is for the kryo byte to identify the class
    // however I hope that compression and not having the +1
    // even out in the long run :)
    private static final int LONG_FIELD_SIZE     = 8;
    private static final int INT_FIELD_SIZE      = 4;
    private static final int SHORT_FIELD_SIZE    = 2;
    private static final int CHAR_FIELD_SIZE     = 2;
    private static final int BYTE_FIELD_SIZE     = 1;
    private static final int BOOLEAN_FIELD_SIZE  = 1;
    private static final int DOUBLE_FIELD_SIZE   = 8;
    private static final int FLOAT_FIELD_SIZE    = 4;

    private int currentObjectSize = 0;

    /**
     * Unfortunate naming of the galaxy library. This is the byte size of the object.
     * NOT the number of elements or items it contains.
     */
    public final int size() {
        //  8: for general compression and kryo overhead
        // 16: for a few leading bytes to put the number of elements in the object somewhere
        // obviously this is a very non-scientific approach
        //
        // since we default to compression, we need to calculate the biggest compressed size
        // this sometimes yields counter-intuitive results where the compressed size
        // is greater than the uncompressed size
        // it could be worth (in the long run) doing compression in certain cases and not always
        return Snappy.maxCompressedLength(magicSize());
    }

    private int magicSize() {
        return 8 + 16 + currentObjectSize;
    }

    /**
     * Be careful when calling this!
     * This blindly overrides the current size of the object.
     * It seems to be necessary when data structures are resized internally.
     */
    void setObjectSize(int newSize) {
        currentObjectSize = Math.max(newSize, 0);
    }

    void addObjectToObjectSize(Object o) {
        currentObjectSize += sizeOfObject(o);
    }

    void subtractObjectToObjectSize(Object o) {
        currentObjectSize = Math.max(0, currentObjectSize - sizeOfObject(o));
    }

    int sizeOfObject(Object o) {
        if (o == null) return 0;
        Class type = o.getClass();
        if (Integer.class.equals(type)) {
            return INT_FIELD_SIZE;
        } else if (String.class.equals(type)) {
            return ((String)o).getBytes().length;
        } else if (Long.class.equals(type)) {
            return LONG_FIELD_SIZE;
        } else if (Short.class.equals(type)) {
            return SHORT_FIELD_SIZE;
        } else if (Byte.class.equals(type)) {
            return BYTE_FIELD_SIZE;
        } else if (Boolean.class.equals(type)) {
            return BOOLEAN_FIELD_SIZE;
        } else if (Character.class.equals(type)) {
            return CHAR_FIELD_SIZE;
        } else if (Double.class.equals(type)) {
            return DOUBLE_FIELD_SIZE;
        } else if (Float.class.equals(type)) {
            return FLOAT_FIELD_SIZE;
        } else if (UUID.class.equals(type)) {
            return LONG_FIELD_SIZE * 2;
        } else if (Persistable.class.isAssignableFrom(type)) {
            return ((Persistable)o).size();
        } else if (Sizable.class.isAssignableFrom(type)) {
            return ((Sizable)o).size();
        } else {
            throw new IllegalArgumentException ("unrecognized type: " + o.getClass());
        }
    }

    boolean isUnderMaxByteSize(int addSize) {
        return Snappy.maxCompressedLength(magicSize() + addSize) <= getMaxByteSize();
    }

    int getMaxByteSize() {
        return MAX_BYTE_SIZE;
    }
}
