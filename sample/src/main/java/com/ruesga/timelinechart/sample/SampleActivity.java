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

import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.ruesga.timelinechart.InMemoryCursor;
import com.ruesga.timelinechart.TimelineChartView;
import com.ruesga.timelinechart.TimelineChartView.OnColorPaletteChangedListener;
import com.ruesga.timelinechart.TimelineChartView.OnSelectedItemChangedListener;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class SampleActivity extends AppCompatActivity {

    private InMemoryCursor mCursor;

    private Toolbar mToolbar;
    private TimelineChartView mGraph;
    private TextView mTimestamp;
    private TextView[] mSeries;
    private View[] mSeriesColors;

    private Calendar mStart;

    private final SimpleDateFormat DATETIME_FORMATTER =
            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final NumberFormat NUMBER_FORMATTER = new DecimalFormat("#0.00");
    private final String[] COLUMN_NAMES = {"timestamp", "Serie 1", "Serie 2", "Serie 3"};

    private final int[] MODES = {
            TimelineChartView.GRAPH_MODE_BARS,
            TimelineChartView.GRAPH_MODE_BARS_STACK,
            TimelineChartView.GRAPH_MODE_BARS_SIDE_BY_SIDE};
    private final String[] MODES_TEXT = {
            "GRAPH_MODE_BARS",
            "GRAPH_MODE_BARS_STACK",
            "GRAPH_MODE_BARS_SIDE_BY_SIDE"};
    private int mMode;
    private int mSound;

    private final View.OnClickListener mClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.mode:
                    mMode++;
                    if (mMode >= MODES.length) {
                        mMode = 0;
                    }
                    mGraph.setGraphMode(MODES[mMode]);
                    Toast.makeText(SampleActivity.this, MODES_TEXT[mMode], Toast.LENGTH_SHORT).show();
                    break;
                case R.id.footer:
                    mGraph.setShowFooter(!mGraph.isShowFooter());
                    break;
                case R.id.color:
                    int color = Color.rgb(random(255), random(255), random(255));
                    mToolbar.setBackgroundColor(color);
                    mGraph.setBackgroundColor(color);
                    mGraph.setGraphAreaBackground(color);
                    break;
                case R.id.sound:
                    mSound++;
                    if (mSound > 2) {
                        mSound = 0;
                    }

                    if (mSound == 0) {
                        mGraph.setPlaySelectionSoundEffect(false);
                    } else if (mSound == 1) {
                        mGraph.setSelectionSoundEffectSource(0);
                        mGraph.setPlaySelectionSoundEffect(true);
                    } else {
                        mGraph.setSelectionSoundEffectSource(R.raw.selection_effect);
                    }
                    break;
                case R.id.reload:
                    mCursor = createRandomData();
                    mGraph.observeData(mCursor);
                    break;
                case R.id.add:
                    mStart.add(Calendar.DAY_OF_YEAR, 1);
                    mCursor.add(createItem(mStart.getTimeInMillis()));
                    break;
                case R.id.delete:
                    int position = mCursor.getCount() - 1;
                    mCursor.remove(position);
                    mStart.add(Calendar.DAY_OF_YEAR, -1);
                    break;
                case R.id.update:
                    position = mCursor.getCount() - 1;
                    mCursor.update(position, createItem(mStart.getTimeInMillis()));
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sample_activity);

        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.app_name);
        }

        // Buttons
        Button button = (Button) findViewById(R.id.add);
        button.setOnClickListener(mClickListener);
        button = (Button) findViewById(R.id.delete);
        button.setOnClickListener(mClickListener);
        button = (Button) findViewById(R.id.update);
        button.setOnClickListener(mClickListener);
        button = (Button) findViewById(R.id.reload);
        button.setOnClickListener(mClickListener);
        button = (Button) findViewById(R.id.mode);
        button.setOnClickListener(mClickListener);
        button = (Button) findViewById(R.id.footer);
        button.setOnClickListener(mClickListener);
        button = (Button) findViewById(R.id.color);
        button.setOnClickListener(mClickListener);
        button = (Button) findViewById(R.id.sound);
        button.setOnClickListener(mClickListener);

        button = (Button) findViewById(R.id.mode);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mMode++;
                if (mMode >= MODES.length) {
                    mMode = 0;
                }
                mGraph.setGraphMode(MODES[mMode]);
                Toast.makeText(SampleActivity.this, MODES_TEXT[mMode], Toast.LENGTH_SHORT).show();
            }
        });


        // Create random data
        mCursor = createRandomData();

        // Retrieve the data and inject the cursor so the view can start observing changes
        mGraph = (TimelineChartView) findViewById(R.id.graph);
        mMode = mGraph.getGraphMode();
        mSound = mGraph.isPlaySelectionSoundEffect() ? 1 : 0;
        mSound += mGraph.getSelectionSoundEffectSource() != 0 ? 1 : 0;

        // Setup info view
        LayoutInflater inflater = LayoutInflater.from(this);
        mTimestamp = (TextView) findViewById(R.id.item_timestamp);
        ViewGroup series = (ViewGroup) findViewById(R.id.item_series);
        mSeries = new TextView[COLUMN_NAMES.length - 1];
        mSeriesColors = new View[COLUMN_NAMES.length - 1];
        for (int i = 1; i < COLUMN_NAMES.length; i++) {
            View v = inflater.inflate(R.layout.serie_item_layout, series, false);
            TextView title = (TextView) v.findViewById(R.id.title);
            title.setText(getString(R.string.item_name, COLUMN_NAMES[i]));
            mSeries[i - 1] = (TextView) v.findViewById(R.id.value);
            mSeries[i - 1].setText("-");
            mSeriesColors[i - 1] = v.findViewById(R.id.color);
            mSeriesColors[i - 1].setBackgroundColor(Color.TRANSPARENT);
            series.addView(v);
        }

        // Setup graph view data and start listening
        mGraph.addOnSelectedItemChangedListener(new OnSelectedItemChangedListener() {
            @Override
            public void onSelectedItemChanged(TimelineChartView.Item selectedItem, boolean fromUser) {
                mTimestamp.setText(DATETIME_FORMATTER.format(selectedItem.mTimestamp));
                for (int i = 0; i < mSeries.length; i++) {
                    mSeries[i].setText(NUMBER_FORMATTER.format(selectedItem.mSeries[i]));
                }
            }

            @Override
            public void onNothingSelected() {
                mTimestamp.setText("-");
                for (TextView v : mSeries) {
                    v.setText("-");
                }
            }
        });
        mGraph.addOnColorPaletteChangedListener(new OnColorPaletteChangedListener() {
            @Override
            public void onColorPaletteChanged(int[] palette) {
                int count = mSeriesColors.length;
                for (int i = 0; i < count; i++) {
                    mSeriesColors[i].setBackgroundColor(palette[i]);
                }
            }
        });
        mGraph.setOnClickItemListener(new TimelineChartView.OnClickItemListener() {
            @Override
            public void onClickItem(TimelineChartView.Item selectedItem) {
                String timestamp = DATETIME_FORMATTER.format(selectedItem.mTimestamp);
                Toast.makeText(SampleActivity.this, "onClickItem => " + timestamp,
                        Toast.LENGTH_SHORT).show();
                mGraph.smoothScrollTo(selectedItem.mTimestamp);
            }
        });
        mGraph.setOnLongClickItemListener(new TimelineChartView.OnLongClickItemListener() {
            @Override
            public void onLongClickItem(TimelineChartView.Item selectedItem) {
                String timestamp = DATETIME_FORMATTER.format(selectedItem.mTimestamp);
                Toast.makeText(SampleActivity.this, "onLongClickItem => " + timestamp,
                        Toast.LENGTH_SHORT).show();

            }
        });

        mGraph.observeData(mCursor);

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private int random(int max) {
        return (int) (Math.random() * (max + 1));
    }

    private Object[] createItem(long timestamp) {
        Object[] item = new Object[COLUMN_NAMES.length];
        item[0] = timestamp;
        for (int i = 1; i < COLUMN_NAMES.length; i++) {
            item[i] = random(9999);
        }
        return item;
    }

    private InMemoryCursor createRandomData() {
        InMemoryCursor cursor = new InMemoryCursor(COLUMN_NAMES);
        List<Object[]> data = new ArrayList<>();
        Calendar today = Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault());
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        mStart = (Calendar) today.clone();
        mStart.add(Calendar.DAY_OF_YEAR, -15);
        while (mStart.compareTo(today) <= 0) {
            data.add(createItem(mStart.getTimeInMillis()));
            mStart.add(Calendar.DAY_OF_YEAR, 1);
        }
        mStart.add(Calendar.DAY_OF_YEAR, -1);
        cursor.addAll(data);
        return cursor;
    }
}

