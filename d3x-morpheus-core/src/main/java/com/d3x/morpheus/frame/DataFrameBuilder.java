/*
 * Copyright (C) 2018-2019 D3X Systems - All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.d3x.morpheus.frame;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import com.d3x.morpheus.array.ArrayBuilder;
import com.d3x.morpheus.array.ArrayType;
import com.d3x.morpheus.index.Index;
import com.d3x.morpheus.index.IndexException;

/**
 * A builder class to iteratively construct a DataFrame
 *
 * @param <R>   the row key type
 * @param <C>   the column key type
 *
 * <p>This is open source software released under the <a href="http://www.apache.org/licenses/LICENSE-2.0">Apache 2.0 License</a></p>
 *
 * @author  Xavier Witdouck
 */
public class DataFrameBuilder<R,C> {

    private Lock lock;
    private Class<R> rowType;
    private Class<C> colType;
    private Index<R> rowKeys;
    private int rowCapacity = 1000;
    private Map<C,Double> fillPercentMap;
    private Map<C,ArrayBuilder<?>> arrayMap;


    /**
     * Constructor
     * @param rowType   the row key type for frame
     * @param colType   the column key type for frame
     */
    DataFrameBuilder(
        @lombok.NonNull Class<R> rowType,
        @lombok.NonNull  Class<C> colType) {
        this.rowType = rowType;
        this.colType = colType;
        this.fillPercentMap = new HashMap<>();
    }


    /**
     * Constructor
     * @param frame the frame to initialize this builder from
     */
    DataFrameBuilder(@lombok.NonNull DataFrame<R,C> frame) {
        this(frame.rows().keyClass(), frame.cols().keyClass());
        this.capacity(frame.rowCount(), frame.colCount());
        this.putAll(frame);
    }


    /**
     * Returns a newly created frame from the contents of this builder
     * @return      the newly created DataFrame
     */
    public DataFrame<R,C> build() {
        this.capacity(100, 10);
        return DataFrame.of(rowKeys, colType, columns -> {
            arrayMap.forEach((key, value) -> {
                var array = value.toArray();
                columns.add(key, array);
            });
        });
    }


    /**
     * Sets the fill percent for column for when sparse data frame required
     * @param colKey    the column key
     * @param fillPct   the column fill percent which must be > 0 and <= 1 (1 implies dense array, < 1 implies sparse array)
     * @return          this builder
     */
    public final DataFrameBuilder<R,C> fillPct(@lombok.NonNull C colKey, double fillPct) {
        if (fillPct < 0 || fillPct > 1) {
            throw new IllegalStateException("Invalid fill percent for " + colKey + ", must be > 0 and <= 1, not " + fillPct);
        } else {
            this.fillPercentMap.put(colKey, fillPct);
            return this;
        }
    }


    /**
     * Sets the capacity for this builder if not already set
     * @param rowCapacity   the initial row capacity
     * @param colCapacity   the initial column capacity
     * @return              this builder
     */
    public final DataFrameBuilder<R,C> capacity(int rowCapacity, int colCapacity) {
        if (rowKeys != null) {
            return this;
        } else {
            this.rowCapacity = Math.max(rowCapacity, 10);
            this.rowKeys = Index.of(rowType, this.rowCapacity);
            this.arrayMap = new LinkedHashMap<>(Math.max(colCapacity, 10));
            return this;
        }
    }


    /**
     * Returns the current row count for builder
     * @return  the current row count
     */
    public int rowCount() {
        return rowKeys != null ? rowKeys.size() : 0;
    }


    /**
     * Returns the current column count for builder
     * @return  the current column count
     */
    public int colCount() {
        return arrayMap != null ? arrayMap.size() : 0;
    }


    /**
     * Returns the stream of row keys for this builder
     * @return  the stream of row keys
     */
    public Stream<R> rowKeys() {
        return rowKeys != null ? rowKeys.keys() : Stream.empty();
    }


    /**
     * Returns the stream of column keys for this builder
     * @return  the stream of column keys
     */
    public Stream<C> colKeys() {
        return arrayMap != null ? arrayMap.keySet().stream() : Stream.empty();
    }


    /**
     * Returns true if this builder is thread safe
     * @return  true if builder is thread safe
     */
    public boolean isThreadSafe() {
        return lock != null;
    }


    /**
     * Called to acquire the lock
     */
    private void acquireLock() {
        if (this.lock != null) {
            this.lock.lock();
        }
    }


    /**
     * Called to release the lock
     */
    private void releaseLock() {
        if (this.lock != null) {
            this.lock.unlock();
        }
    }


    /**
     * Adds the row key if new, and returns the coordinate for key
     * @param rowKey    the row key to add
     * @return          this coordinate for row key
     */
    private int putRow(R rowKey) {
        this.rowKeys.add(rowKey);
        return rowKeys.getCoordinate(rowKey);
    }


    /**
     * Returns the array builder for column key
     * @param colKey    the column key
     * @return          the array builder
     */
    @SuppressWarnings("unchecked")
    private <T> ArrayBuilder<T> array(C colKey) {
        var array = arrayMap.get(colKey);
        if (array != null) {
            return (ArrayBuilder<T>)array;
        } else {
            var fillPct = fillPercentMap.getOrDefault(colKey, 1d);
            array = ArrayBuilder.of(rowCapacity, fillPct);
            this.arrayMap.put(colKey, array);
            return (ArrayBuilder<T>)array;
        }
    }


    /**
     * Returns true if this builder contains the row key
     * @param rowKey    the row key to check
     * @return          true if row key exists
     */
    public boolean hasRow(R rowKey) {
        return rowKeys != null && rowKeys.contains(rowKey);
    }


    /**
     * Returns true if this builder contains the column key
     * @param colKey    the column key to check
     * @return          true if column key exists
     */
    public boolean hasColumn(C colKey) {
        return arrayMap != null && arrayMap.containsKey(colKey);
    }


    /**
     * Makes this builder thread safe
     * @return  this builder
     */
    public synchronized DataFrameBuilder<R,C> threadSafe() {
        if (lock != null) {
            return this;
        } else {
            this.lock = new ReentrantLock();
            return this;
        }
    }


    /**
     * Replaces an existing row key with a new key
     * @param existing      the existing key to replace
     * @param replacement   the replacement key
     * @return              this builder
     */
    public DataFrameBuilder<R,C> replaceRowKey(R existing, R replacement) {
        try {
            this.rowKeys.replace(existing, replacement);
            return this;
        } catch (IndexException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }


    /**
     * Replaces an existing column key with a new key
     * @param existing      the existing key to replace
     * @param replacement   the replacement key
     * @return              this builder
     */
    public DataFrameBuilder<R,C> replaceColKey(C existing, C replacement) {
        var array = arrayMap.remove(existing);
        if (array == null) {
            return this;
        } else if (arrayMap.containsKey(replacement)) {
            throw new IllegalArgumentException("Replacement key already exists: " + replacement);
        } else {
            this.arrayMap.put(replacement, array);
            return this;
        }
    }


    /**
     * Applies a value to this builder for the row and column key
     * @param rowKey    the row key
     * @param colKey    the column key
     * @param value     the value to apply
     * @return          this builder
     */
    public DataFrameBuilder<R,C> putBoolean(R rowKey, C colKey, boolean value) {
        try {
            this.acquireLock();
            this.capacity(1000, 10);
            var coord = this.putRow(rowKey);
            var array = this.array(colKey);
            array.setBoolean(coord, value);
            return this;
        } finally {
            this.releaseLock();
        }
    }


    /**
     * Applies a value to this builder for the row and column key
     * @param rowKey    the row key
     * @param colKey    the column key
     * @param value     the value to apply
     * @return          this builder
     */
    public DataFrameBuilder<R,C> putInt(R rowKey, C colKey, int value) {
        try {
            this.acquireLock();
            this.capacity(1000, 10);
            var coord = this.putRow(rowKey);
            var array = this.array(colKey);
            array.setInt(coord, value);
            return this;
        } finally {
            this.releaseLock();
        }
    }


    /**
     * Applies a value to this builder for the row and column key
     * @param rowKey    the row key
     * @param colKey    the column key
     * @param value     the value to apply
     * @return          this builder
     */
    public DataFrameBuilder<R,C> putLong(R rowKey, C colKey, long value) {
        try {
            this.acquireLock();
            this.capacity(1000, 10);
            var coord = this.putRow(rowKey);
            var array = this.array(colKey);
            array.setLong(coord, value);
            return this;
        } finally {
            this.releaseLock();
        }
    }


    /**
     * Applies a value to this builder for the row and column key
     * @param rowKey    the row key
     * @param colKey    the column key
     * @param value     the value to apply
     * @return          this builder
     */
    public DataFrameBuilder<R,C> putDouble(R rowKey, C colKey, double value) {
        try {
            this.acquireLock();
            this.capacity(1000, 10);
            var coord = this.putRow(rowKey);
            var array = this.array(colKey);
            array.setDouble(coord, value);
            return this;
        } finally {
            this.releaseLock();
        }
    }


    /**
     * Applies a value to this builder for the row and column key
     * @param rowKey    the row key
     * @param colKey    the column key
     * @param value     the value to apply
     * @return          this builder
     */
    public <T> DataFrameBuilder<R,C> putValue(R rowKey, C colKey, T value) {
        try {
            this.acquireLock();
            this.capacity(1000, 10);
            var coord = this.putRow(rowKey);
            var array = this.array(colKey);
            array.setValue(coord, value);
            return this;
        } finally {
            this.releaseLock();
        }
    }


    /**
     * Adds the int value to an existing value at coordinates, or applies the value if no existing value
     * @param rowKey    the row key
     * @param colKey    the column key
     * @param value     the value to add
     * @return          this builder
     */
    public DataFrameBuilder<R,C> plusInt(R rowKey, C colKey, int value) {
        try {
            this.acquireLock();
            this.capacity(1000, 10);
            var coord = this.putRow(rowKey);
            var array = this.array(colKey);
            array.plusInt(coord, value);
            return this;
        } finally {
            this.releaseLock();
        }
    }


    /**
     * Adds the int value to an existing value at coordinates, or applies the value if no existing value
     * @param rowKey    the row key
     * @param colKey    the column key
     * @param value     the value to add
     * @return          this builder
     */
    public DataFrameBuilder<R,C> plusLong(R rowKey, C colKey, long value) {
        try {
            this.acquireLock();
            this.capacity(1000, 10);
            var coord = this.putRow(rowKey);
            var array = this.array(colKey);
            array.plusLong(coord, value);
            return this;
        } finally {
            this.releaseLock();
        }
    }


    /**
     * Adds the double value to an existing value at coordinates, or applies the value if no existing value
     * @param rowKey    the row key
     * @param colKey    the column key
     * @param value     the value to add
     * @return          this builder
     */
    public DataFrameBuilder<R,C> plusDouble(R rowKey, C colKey, double value) {
        try {
            this.acquireLock();
            this.capacity(1000, 10);
            var coord = this.putRow(rowKey);
            var array = this.array(colKey);
            array.plusDouble(coord, value);
            return this;
        } finally {
            this.releaseLock();
        }
    }


    /**
     * Puts all data from the arg frame into this builder
     * @param other the other frame to extract data from
     * @return      this builder
     */
    public DataFrameBuilder<R,C> putAll(DataFrame<R,C> other) {
        other.cols().forEach(column -> {
            var dataClass = column.dataClass();
            var dataType = ArrayType.of(dataClass);
            switch (dataType) {
                case BOOLEAN:   column.forEach(v -> putBoolean(v.rowKey(), v.colKey(), v.getBoolean()));    break;
                case INTEGER:   column.forEach(v -> putInt(v.rowKey(), v.colKey(), v.getInt()));            break;
                case LONG:      column.forEach(v -> putLong(v.rowKey(), v.colKey(), v.getLong()));          break;
                case DOUBLE:    column.forEach(v -> putDouble(v.rowKey(), v.colKey(), v.getDouble()));      break;
                default:        column.forEach(v -> putValue(v.rowKey(), v.colKey(), v.getValue()));        break;
            }
        });
        return this;
    }
}