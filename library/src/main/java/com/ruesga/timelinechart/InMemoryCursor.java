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
package com.ruesga.timelinechart;

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
 * An in-memory cursor implementation.
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

    public InMemoryCursor(String[] columnNames) {
        mCurrentPosition = -1;
        mColumnNames = columnNames;
    }

    public void addAll(List<Object[]> data) {
        for (Object[] fields : data) {
            internalAdd(fields);
        }
        notifyObservers();
    }

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

    @Override
    public int getCount() {
        return mRecords.size();
    }

    @Override
    public int getPosition() {
        return mCurrentPosition;
    }

    @Override
    public boolean move(int offset) {
        if (mCurrentPosition + offset >= mRecords.size()) {
            return false;
        }
        mCurrentPosition += offset;
        return true;
    }

    @Override
    public boolean moveToPosition(int position) {
        if (position >= mRecords.size()) {
            return false;
        }
        mCurrentPosition = position;
        return true;
    }

    @Override
    public boolean moveToFirst() {
        if (mRecords.size() == 0) {
            return false;
        }
        mCurrentPosition = 0;
        return true;
    }

    @Override
    public boolean moveToLast() {
        if (mRecords.size() == 0) {
            return false;
        }
        mCurrentPosition = mRecords.size() - 1;
        return true;
    }

    @Override
    public boolean moveToNext() {
        return move(1);
    }

    @Override
    public boolean moveToPrevious() {
        return move(-1);
    }

    @Override
    public boolean isFirst() {
        return mCurrentPosition == 0;
    }

    @Override
    public boolean isLast() {
        return mCurrentPosition == (mRecords.size() - 1);
    }

    @Override
    public boolean isBeforeFirst() {
        return mCurrentPosition < 0;
    }

    @Override
    public boolean isAfterLast() {
        return mCurrentPosition >= mRecords.size();
    }

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

    @Override
    public int getColumnIndexOrThrow(String columnName) throws IllegalArgumentException {
        int columnIndex = getColumnIndex(columnName);
        if (columnIndex == -1) {
            throw new IllegalArgumentException();
        }
        return columnIndex;
    }

    @Override
    public String getColumnName(int columnIndex) {
        return mColumnNames[columnIndex];
    }

    @Override
    public String[] getColumnNames() {
        return mColumnNames;
    }

    @Override
    public int getColumnCount() {
        return mColumnNames.length;
    }

    @Override
    public byte[] getBlob(int columnIndex) {
        if (isNull(columnIndex)) {
            return null;
        }
        return (byte[]) mRecords.get(mCurrentPosition).mFields[columnIndex];
    }

    @Override
    public String getString(int columnIndex) {
        if (isNull(columnIndex)) {
            return null;
        }
        final Object o = mRecords.get(mCurrentPosition).mFields[columnIndex];
        return String.valueOf(o);
    }

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

    @Override
    public short getShort(int columnIndex) {
        return (short) getDouble(columnIndex);
    }

    @Override
    public int getInt(int columnIndex) {
        return (int) getDouble(columnIndex);
    }

    @Override
    public long getLong(int columnIndex) {
        return (long) getDouble(columnIndex);
    }

    @Override
    public float getFloat(int columnIndex) {
        return (float) getDouble(columnIndex);
    }

    @Override
    public double getDouble(int columnIndex) {
        final Object o = mRecords.get(mCurrentPosition).mFields[columnIndex];
        return Double.parseDouble(String.valueOf(o));
    }

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

    @Override
    public boolean isNull(int columnIndex) {
        return mRecords.get(mCurrentPosition).mFields[columnIndex] == null;
    }

    @Override
    public void deactivate() {
    }

    @Override
    public boolean requery() {
        return true;
    }

    @Override
    public void close() {
        mRecords.clear();
        mCurrentPosition = -1;
    }

    @Override
    public boolean isClosed() {
        return mCurrentPosition == -1;
    }

    @Override
    public void registerContentObserver(ContentObserver observer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void unregisterContentObserver(ContentObserver observer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        mObservers.add(observer);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        mObservers.remove(observer);
    }

    @Override
    public void setNotificationUri(ContentResolver cr, Uri uri) {
        mNotificationUri = uri;
    }

    @Override
    public Uri getNotificationUri() {
        return mNotificationUri;
    }

    @Override
    public boolean getWantsAllOnMoveCalls() {
        return false;
    }

    @Override
    public void setExtras(Bundle extras) {
        mExtras.putAll(extras);
    }

    @Override
    public Bundle getExtras() {
        return mExtras;
    }

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
