/*
 * Copyright (C) 2015 Jorge Ruesga
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ruesga.timelinechart.sample;

import android.content.ContentResolver;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An in-memory {@link Cursor} implementation.
 */
public class InMemoryCursor implements Cursor {

    private static class Record {
        private Object[] mFields;
    }

    private String[] mColumnNames;
    private List<Record> mRecords = Collections.synchronizedList(new ArrayList<Record>());
    private int mCurrentPosition;

    private List<DataSetObserver> mObservers = new ArrayList<>();
    private Uri mNotificationUri;
    private Bundle mExtras = new Bundle();

    /**
     * Creates a new cursor setting the columns names that will be used by this cursor. The
     * length of this array determines the number of columns that the cursor can handle.
     * Filling that below this number will set the rest of the values to null, and all
     * items above it will be ignored.
     * @param columnNames the columns names of this cursor.
     */
    public InMemoryCursor(String[] columnNames) {
        mCurrentPosition = -1;
        mColumnNames = columnNames;
    }

    /**
     * Adds all the {@link List} as new rows of the cursor.
     */
    public void addAll(List<Object[]> data) {
        for (Object[] fields : data) {
            internalAdd(fields);
        }
        notifyObservers();
    }

    /**
     * Adds a new row to the cursor.
     */
    public void add(Object[] data) {
        internalAdd(data);
        notifyObservers();
    }

    private void internalAdd(Object[] data) {
        Record record = new Record();
        record.mFields = new Object[mColumnNames.length];
        int count = Math.min(record.mFields.length, data.length);
        System.arraycopy(data, 0, record.mFields, 0, count);
        boolean updatePosition = mRecords.size() == 0;
        mRecords.add(record);
        if (updatePosition) {
            mCurrentPosition = 0;
        }
    }

    /**
     * Update the cursor with the given data at the position passed as argument.
     * @return if the row was found and updated.
     */
    public boolean update(int position, Object[] data) {
        if (position >= 0 && position < mRecords.size()){
            Record record =  mRecords.get(position);
            int count = Math.min(record.mFields.length, data.length);
            System.arraycopy(data, 0, record.mFields, 0, count);
            notifyObservers();
            return true;
        }
        return false;
    }

    /**
     * Remove the row at the passed position from the cursor if exists.
     * @return if the row was found and deleted.
     */
    public boolean remove(int position) {
        if (position >= 0 && position < mRecords.size()){
            mRecords.remove(position);
            if (mRecords.size() == 0) {
                mCurrentPosition = -1;
            } else if (mCurrentPosition >= mRecords.size()) {
                mCurrentPosition--;
            }
            notifyObservers();
            return true;
        }
        return false;
    }

    /**
     * Clear the internal cursor data.
     */
    public void removeAll() {
        if (mRecords.size() >= 0) {
            mRecords.clear();
            mCurrentPosition = -1;
            notifyObservers();
        }
    }

    /** {@inheritDoc} */
    @Override
    public int getCount() {
        return mRecords.size();
    }

    /** {@inheritDoc} */
    @Override
    public int getPosition() {
        return mCurrentPosition;
    }

    /** {@inheritDoc} */
    @Override
    public boolean move(int offset) {
        final int next = mCurrentPosition + offset;
        if (next < 0 || next >= mRecords.size()) {
            return false;
        }
        mCurrentPosition = next;
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean moveToPosition(int position) {
        if (position < 0 || position >= mRecords.size()) {
            return false;
        }
        mCurrentPosition = position;
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean moveToFirst() {
        if (mRecords.size() == 0) {
            return false;
        }
        mCurrentPosition = 0;
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean moveToLast() {
        if (mRecords.size() == 0) {
            return false;
        }
        mCurrentPosition = mRecords.size() - 1;
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean moveToNext() {
        return move(1);
    }

    /** {@inheritDoc} */
    @Override
    public boolean moveToPrevious() {
        return move(-1);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isFirst() {
        return mCurrentPosition == 0;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isLast() {
        return mCurrentPosition == (mRecords.size() - 1);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isBeforeFirst() {
        return mCurrentPosition < 0;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAfterLast() {
        return mCurrentPosition >= mRecords.size();
    }

    /** {@inheritDoc} */
    @Override
    public int getColumnIndex(String columnName) {
        int i = 0;
        for (String name : mColumnNames) {
            if (name.equalsIgnoreCase(columnName)) {
                return i;
            }
            i++;
        }
        return -1;
    }

    /** {@inheritDoc} */
    @Override
    public int getColumnIndexOrThrow(String columnName) throws IllegalArgumentException {
        int columnIndex = getColumnIndex(columnName);
        if (columnIndex == -1) {
            throw new IllegalArgumentException();
        }
        return columnIndex;
    }

    /** {@inheritDoc} */
    @Override
    public String getColumnName(int columnIndex) {
        return mColumnNames[columnIndex];
    }

    @Override
    public String[] getColumnNames() {
        return mColumnNames;
    }

    /** {@inheritDoc} */
    @Override
    public int getColumnCount() {
        return mColumnNames.length;
    }

    /** {@inheritDoc} */
    @Override
    public byte[] getBlob(int columnIndex) {
        if (isNull(columnIndex)) {
            return null;
        }
        return (byte[]) mRecords.get(mCurrentPosition).mFields[columnIndex];
    }

    /** {@inheritDoc} */
    @Override
    public String getString(int columnIndex) {
        if (isNull(columnIndex)) {
            return null;
        }
        final Object o = mRecords.get(mCurrentPosition).mFields[columnIndex];
        return String.valueOf(o);
    }

    /** {@inheritDoc} */
    @Override
    public void copyStringToBuffer(int columnIndex, CharArrayBuffer buffer) {
        final Object o = mRecords.get(mCurrentPosition).mFields[columnIndex];
        if (o == null) {
            buffer.sizeCopied = 0;
            return;
        }
        final char[] data;
        if (o instanceof byte[]) {
            data = new String((byte[]) o).toCharArray();
        } else {
            data = String.valueOf(o).toCharArray();
        }
        if (buffer.data.length >= data.length) {
            System.arraycopy(buffer.data, 0, data, 0, data.length);
            buffer.sizeCopied = data.length;
        } else {
            buffer.data = data;
            buffer.sizeCopied = data.length;
        }
    }

    /** {@inheritDoc} */
    @Override
    public short getShort(int columnIndex) {
        return (short) getDouble(columnIndex);
    }

    /** {@inheritDoc} */
    @Override
    public int getInt(int columnIndex) {
        return (int) getDouble(columnIndex);
    }

    /** {@inheritDoc} */
    @Override
    public long getLong(int columnIndex) {
        return (long) getDouble(columnIndex);
    }

    /** {@inheritDoc} */
    @Override
    public float getFloat(int columnIndex) {
        return (float) getDouble(columnIndex);
    }

    /** {@inheritDoc} */
    @Override
    public double getDouble(int columnIndex) {
        final Object o = mRecords.get(mCurrentPosition).mFields[columnIndex];
        return Double.parseDouble(String.valueOf(o));
    }

    /** {@inheritDoc} */
    @Override
    public int getType(int columnIndex) {
        final Object o = mRecords.get(mCurrentPosition).mFields[columnIndex];
        if (o == null) {
            return Cursor.FIELD_TYPE_NULL;
        }
        if (o instanceof Byte || o instanceof Short || o instanceof Integer || o instanceof Long) {
            return Cursor.FIELD_TYPE_INTEGER;
        }
        if (o instanceof Float || o instanceof Double) {
            return Cursor.FIELD_TYPE_FLOAT;
        }
        if (o instanceof byte[]) {
            return Cursor.FIELD_TYPE_BLOB;
        }
        return Cursor.FIELD_TYPE_STRING;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isNull(int columnIndex) {
        return mRecords.get(mCurrentPosition).mFields[columnIndex] == null;
    }

    /** {@inheritDoc} */
    @Override
    public void deactivate() {
    }

    /** {@inheritDoc} */
    @Override
    public boolean requery() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
        mRecords.clear();
        mCurrentPosition = -1;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isClosed() {
        return mCurrentPosition == -1;
    }

    /** {@inheritDoc} */
    @Override
    public void registerContentObserver(ContentObserver observer) {
        throw new UnsupportedOperationException();
    }

    /** {@inheritDoc} */
    @Override
    public void unregisterContentObserver(ContentObserver observer) {
        throw new UnsupportedOperationException();
    }

    /** {@inheritDoc} */
    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        mObservers.add(observer);
    }

    /** {@inheritDoc} */
    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        mObservers.remove(observer);
    }

    /** {@inheritDoc} */
    @Override
    public void setNotificationUri(ContentResolver cr, Uri uri) {
        mNotificationUri = uri;
    }

    /** {@inheritDoc} */
    @Override
    public Uri getNotificationUri() {
        return mNotificationUri;
    }

    /** {@inheritDoc} */
    @Override
    public boolean getWantsAllOnMoveCalls() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public void setExtras(Bundle extras) {
        mExtras.putAll(extras);
    }

    /** {@inheritDoc} */
    @Override
    public Bundle getExtras() {
        return mExtras;
    }

    /** {@inheritDoc} */
    @Override
    public Bundle respond(Bundle extras) {
        return extras;
    }

    private void notifyObservers() {
        for (DataSetObserver observer : mObservers) {
            observer.onChanged();
        }
    }
}
