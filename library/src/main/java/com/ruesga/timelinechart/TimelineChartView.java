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

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.RawRes;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.LongSparseArray;
import android.support.v4.util.Pair;
import android.support.v4.view.VelocityTrackerCompat;
import android.support.v4.view.ViewCompat;
import android.text.DynamicLayout;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.AbsoluteSizeSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.OverScroller;

import com.ruesga.timelinechart.helpers.ArraysHelper;
import com.ruesga.timelinechart.helpers.MaterialPaletteHelper;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class TimelineChartView extends View {

    private static final String TAG = "TimelineChartView";

    public static class Item {
        private Item() {
        }
        public long mTimestamp;
        public double[] mSeries;
    }

    public interface OnSelectedItemChangedListener {
        void onSelectedItemChanged(Item selectedItem, boolean fromUser);
        void onNothingSelected();
    }

    public interface OnColorPaletteChanged {
        void onColorPaletteChanged(int[] palette);
    }

    // Bar item mode constants
    public static final int GRAPH_MODE_BARS = 0;
    public static final int GRAPH_MODE_BARS_STACK = 1;
    public static final int GRAPH_MODE_BARS_SIDE_BY_SIDE = 2;

    private static final float MAX_ZOOM_OUT = 4.0f;
    private static final float MIN_ZOOM_OUT = 1.0f;

    private static final float SOUND_EFFECT_VOLUME = 0.3f;
    private static final int SYSTEM_SOUND_EFFECT = 0;

    private Cursor mCursor;
    private int mSeries;
    private LongSparseArray<Pair<double[],int[]>> mData = new LongSparseArray<>();
    private double mMaxValue;

    private final RectF mViewArea = new RectF();
    private final RectF mGraphArea = new RectF();
    private final RectF mFooterArea = new RectF();
    private final float mDefFooterBarHeight;
    private float mFooterBarHeight;
    private boolean mShowFooter;
    private int mGraphMode;
    private boolean mPlaySelectionSoundEffect;
    private int mSelectionSoundEffectSource;
    private boolean mAnimateCursorSwapTransition;

    private int[] mUserPalette;
    private int[] mCurrentPalette;

    private float mBarItemWidth;
    private float mBarItemSpace;
    private float mCurrentPositionIndicatorWidth;
    private float mCurrentPositionIndicatorHeight;

    private Paint mGraphAreaBgPaint;
    private Paint mFooterAreaBgPaint;

    private Paint[] mSeriesBgPaint;
    private Paint[] mHighlightSeriesBgPaint;
    private TextPaint mTickLabelFgPaint;

    private final Path mCurrentPositionPath = new Path();

    private long mCurrentTimestamp = -1;
    private float mCurrentOffset = 0.f;
    private float mLastOffset = -1.f;
    private float mMaxOffset = 0.f;
    private float mInitialTouchOffset = 0.f;
    private float mInitialTouchX = 0.f;
    private float mCurrentZoom = 1.f;

    private int mMaxBarItemsInScreen = 0;
    private final int[] mItemsOnScreen = new int[2];

    private SimpleDateFormat mTickFormatter;
    private Date mTickDate;
    private StringBuilder mTickText;
    private DynamicSpannableString mTickTextSpannable;
    private DynamicLayout mTickTextLayout;

    private VelocityTracker mVelocityTracker;
    private OverScroller mScroller;
    private final float mTouchSlop;
    private final float mMaxFlingVelocity;

    private static final int MSG_ON_SELECTION_ITEM_CHANGED = 1;

    private final Handler mUiHandler;
    private final Handler.Callback mUiHandlerMessenger = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_ON_SELECTION_ITEM_CHANGED:
                    notifyOnSelectionItemChanged((boolean) msg.obj);
                    return true;
            }
            return false;
        }
    };

    private final AudioManager mAudioManager;
    private MediaPlayer mSoundEffectMP;

    private Set<OnSelectedItemChangedListener> mOnSelectedItemChangedCallbacks =
            Collections.synchronizedSet(new HashSet<OnSelectedItemChangedListener>());
    private Set<OnColorPaletteChanged> mOnColorPaletteChangedCallbacks =
            Collections.synchronizedSet(new HashSet<OnColorPaletteChanged>());

    private boolean mInZoomOut = false;
    private ValueAnimator mZoomAnimator;

    private final Object mLock = new Object();

    public TimelineChartView(Context ctx) {
        this(ctx, null, 0, 0);
    }

    public TimelineChartView(Context ctx, AttributeSet attrs) {
        this(ctx, attrs, 0, 0);
    }

    public TimelineChartView(Context ctx, AttributeSet attrs, int defStyleAttr) {
        this(ctx, attrs, defStyleAttr, 0);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public TimelineChartView(Context ctx, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(ctx, attrs, defStyleAttr, defStyleRes);

        mUiHandler = new Handler(Looper.getMainLooper(), mUiHandlerMessenger);
        mAudioManager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);

        final Resources res = getResources();
        final Resources.Theme theme = ctx.getTheme();

        final ViewConfiguration vc = ViewConfiguration.get(ctx);
        mTouchSlop = vc.getScaledTouchSlop();
        mMaxFlingVelocity = vc.getScaledMaximumFlingVelocity();
        mScroller = new OverScroller(ctx);

        int graphBgColor = ContextCompat.getColor(ctx, R.color.tlcDefGraphBackgroundColor);
        int footerBgColor = ContextCompat.getColor(ctx, R.color.tlcDefFooterBackgroundColor);

        mDefFooterBarHeight = mFooterBarHeight = res.getDimension(R.dimen.tlcDefFooterBarHeight);
        mShowFooter = res.getBoolean(R.bool.tlcDefShowFooter);
        mGraphMode = res.getInteger(R.integer.tlcDefGraphMode);
        mPlaySelectionSoundEffect = res.getBoolean(R.bool.tlcDefPlaySelectionSoundEffect);
        mSelectionSoundEffectSource = res.getInteger(R.integer.tlcDefSelectionSoundEffectSource);
        mAnimateCursorSwapTransition = res.getBoolean(R.bool.tlcDefAnimateCursorSwapTransition);

        mGraphAreaBgPaint = new Paint();
        mGraphAreaBgPaint.setColor(graphBgColor);
        mFooterAreaBgPaint = new Paint();
        mFooterAreaBgPaint.setColor(footerBgColor);
        mTickLabelFgPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.LINEAR_TEXT_FLAG);
        mTickLabelFgPaint.setFakeBoldText(true);
        mTickLabelFgPaint.setColor(MaterialPaletteHelper.isDarkColor(footerBgColor)
                ? Color.LTGRAY : Color.DKGRAY);

        mBarItemWidth = res.getDimension(R.dimen.tlcDefBarItemWidth);
        mBarItemSpace = res.getDimension(R.dimen.tlcDefBarItemSpace);

        TypedArray a = theme.obtainStyledAttributes(attrs,
                R.styleable.tlcTimelineChartView, defStyleAttr, defStyleRes);
        try {
            int n = a.getIndexCount();
            for (int i = 0; i < n; i++) {
                int attr = a.getIndex(i);
                if (attr == R.styleable.tlcTimelineChartView_tlcGraphBackground) {
                    graphBgColor = a.getColor(attr, graphBgColor);
                    mGraphAreaBgPaint.setColor(graphBgColor);
                } else if (attr == R.styleable.tlcTimelineChartView_tlcShowFooter) {
                    mShowFooter = a.getBoolean(attr, mShowFooter);
                } else if (attr == R.styleable.tlcTimelineChartView_tlcFooterBackground) {
                    footerBgColor = a.getColor(attr, footerBgColor);
                    mFooterAreaBgPaint.setColor(footerBgColor);
                } else if (attr == R.styleable.tlcTimelineChartView_tlcFooterBarHeight) {
                    mFooterBarHeight = a.getDimension(attr, mFooterBarHeight);
                } else if (attr == R.styleable.tlcTimelineChartView_tlcGraphMode) {
                    mGraphMode = a.getInt(attr, mGraphMode);
                } else if (attr == R.styleable.tlcTimelineChartView_tlcAnimateCursorSwapTransition) {
                    mAnimateCursorSwapTransition = a.getBoolean(attr, mAnimateCursorSwapTransition);
                } else if (attr == R.styleable.tlcTimelineChartView_tlcBarItemWidth) {
                    mBarItemWidth = a.getDimension(attr, mBarItemWidth);
                } else if (attr == R.styleable.tlcTimelineChartView_tlcBarItemSpace) {
                    mBarItemSpace = a.getDimension(attr, mBarItemSpace);
                } else if (attr == R.styleable.tlcTimelineChartView_tlcPlaySelectionSoundEffect) {
                    mPlaySelectionSoundEffect = a.getBoolean(attr, mPlaySelectionSoundEffect);
                } else if (attr == R.styleable.tlcTimelineChartView_tlcSelectionSoundEffectSource) {
                    mSelectionSoundEffectSource = a.getInt(attr, mSelectionSoundEffectSource);
                }
            }
        } finally {
            a.recycle();
        }

        // SurfaceView requires a background
        if (getBackground() == null) {
            setBackgroundColor(ContextCompat.getColor(ctx, android.R.color.transparent));
        }

        // Initialize stuff
        setupTickLabels();
        setupAnimators();
        setupSoundEffects();

        // Initialize the drawing refs (this will be update when we have
        // the real size of the canvas)
        computeBoundAreas();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    public void setGraphAreaBackground(int color) {
        if (mGraphAreaBgPaint.getColor() != color) {
            mGraphAreaBgPaint.setColor(color);
            setupSeriesBackground(color);
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    public boolean isShowFooter() {
        return mShowFooter;
    }

    public void setShowFooter(boolean show) {
        if (mShowFooter != show) {
            mShowFooter = show;
            computeBoundAreas();
            requestLayout();
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    public void setFooterAreaBackground(int color) {
        if (mFooterAreaBgPaint.getColor() != color) {
            mFooterAreaBgPaint.setColor(color);
            mTickLabelFgPaint.setColor(MaterialPaletteHelper.isDarkColor(color)
                    ? Color.LTGRAY : Color.DKGRAY);
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    public float getFooterBarHeight() {
        return mFooterBarHeight;
    }

    public void setFooterHeight(float height) {
        if (mFooterBarHeight != height) {
            mFooterBarHeight = height;
            computeBoundAreas();
            setupTickLabels();
            requestLayout();
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    public float getBarItemSpace() {
        return mBarItemSpace;
    }

    public void setBarItemSpace(float barItemSpace) {
        if (mBarItemSpace != barItemSpace) {
            mBarItemSpace = barItemSpace;
            computeMaxBarItemsInScreen();
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    public float getBarItemWidth() {
        return mBarItemWidth;
    }

    public void setBarItemWidth(float barItemWidth) {
        if (mBarItemWidth != barItemWidth) {
            mBarItemWidth = barItemWidth;
            computeBoundAreas();
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    public int getGraphMode() {
        return mGraphMode;
    }

    public void setGraphMode(int mode) {
        if (mode != mGraphMode) {
            mGraphMode = mode;
            // FIXME Perform in a background
            computeData();
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    public boolean isAnimateCursorSwapTransition() {
        return mAnimateCursorSwapTransition;
    }

    public void setAnimateCursorSwapTransition(boolean animateCursorSwapTransition) {
        mAnimateCursorSwapTransition = animateCursorSwapTransition;
    }

    public int[] getUserPalette() {
        return mUserPalette;
    }

    public void setUserPalette(int[] userPalette) {
        if (!Arrays.equals(mUserPalette, userPalette)) {
            mUserPalette = userPalette;
            setupSeriesBackground(mGraphAreaBgPaint.getColor());
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    public int[] getCurrentPalette() {
        return mCurrentPalette;
    }

    public boolean isPlaySelectionSoundEffect() {
        return mPlaySelectionSoundEffect;
    }

    public void setPlaySelectionSoundEffect(boolean value) {
        mPlaySelectionSoundEffect = value;
        setupSoundEffects();
    }

    public int getSelectionSoundEffectSource() {
        return mSelectionSoundEffectSource;
    }

    public void setSelectionSoundEffectSource(@RawRes int source) {
        this.mSelectionSoundEffectSource = source;
        setupSoundEffects();
    }

    public void addOnSelectedItemChanged(OnSelectedItemChangedListener cb) {
        mOnSelectedItemChangedCallbacks.add(cb);
    }

    public void removeOnSelectedItemChanged(OnSelectedItemChangedListener cb) {
        mOnSelectedItemChangedCallbacks.remove(cb);
    }

    public void addOnColorPaletteChanged(OnColorPaletteChanged cb) {
        mOnColorPaletteChangedCallbacks.add(cb);
    }

    public void removeOnColorPaletteChanged(OnColorPaletteChanged cb) {
        mOnColorPaletteChangedCallbacks.remove(cb);
    }

    public void observeData(Cursor c) {
        checkCursorIntegrity(c);

        // Close previous cursor
        final boolean animate = mCursor != null;
        if (mCursor != null) {
            mCursor.close();
            mSeries = 0;
        }

        // Save the cursor reference and listen for changes
        mCursor = c;
        reloadCursorData(animate);
        mCursor.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                reloadCursorData(false);
            }

            @Override
            public void onInvalidated() {
                invalidateCursorData();
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mCursor != null && !mCursor.isClosed()) {
            mCursor.close();
            mSeries = 0;
        }
        clear();
        mVelocityTracker.recycle();
        mVelocityTracker = null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        int pointerId = event.getPointerId(index);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                // Initialize velocity tracker
                if (mVelocityTracker == null) {
                    mVelocityTracker = VelocityTracker.obtain();
                } else {
                    mVelocityTracker.clear();
                }
                mVelocityTracker.addMovement(event);
                mScroller.forceFinished(true);

                mInitialTouchOffset = mCurrentOffset;
                mInitialTouchX = event.getX();
                break;

            case MotionEvent.ACTION_MOVE:
                mVelocityTracker.addMovement(event);
                float diff = event.getX() - mInitialTouchX;
                mCurrentOffset = mInitialTouchOffset + diff;
                if (mCurrentOffset < 0) {
                    mCurrentOffset = 0;
                } else if (mCurrentOffset > mMaxOffset) {
                    mCurrentOffset = mMaxOffset;
                }
                mVelocityTracker.computeCurrentVelocity(1000, mMaxFlingVelocity);
                ViewCompat.postInvalidateOnAnimation(this);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                final int velocity = (int) VelocityTrackerCompat.getXVelocity(
                        mVelocityTracker, pointerId);
                mScroller.forceFinished(true);
                mScroller.fling((int) mCurrentOffset, 0, velocity, 0, 0, (int) mMaxOffset, 0, 0);
                ViewCompat.postInvalidateOnAnimation(this);
                break;
        }
        return true;
    }

    @Override
    public void computeScroll() {
        super.computeScroll();

        // Obtain current item from the current scroll

        // Determine whether we still scrolling and needs a viewport refresh
        final boolean scrolling = mScroller.computeScrollOffset();
        if (scrolling) {
            float x = mScroller.getCurrX();
            if (x > mMaxOffset || x < 0) {
                return;
            }
            mCurrentOffset = x;
            ViewCompat.postInvalidateOnAnimation(this);
        }

        // FIXME If we are not centered in a item, perform an scroll


        // Determine the highlighted item and notify it to callbacks
        long timestamp = computeTimestampFromOffset(mCurrentOffset);
        if (mCurrentTimestamp != timestamp) {
            boolean fromUser = mCurrentTimestamp != -2;
            mCurrentTimestamp = timestamp;
            if (fromUser) {
                performSelectionSoundEffect();
            }

            // Notify any valid item, but only notify invalid items if we are not panning/scrolling
            if (mCurrentTimestamp >= 0 || !scrolling) {
                Message.obtain(mUiHandler, MSG_ON_SELECTION_ITEM_CHANGED, fromUser).sendToTarget();
            }
        }
    }

    public void scrollTo(long timestamp) {
        final float offset = computeOffsetForTimestamp(timestamp);
        if (offset >= 0 && offset != mCurrentOffset) {
            mCurrentOffset = offset;
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    public void smoothScrollTo(long timestamp) {
        final float offset = computeOffsetForTimestamp(timestamp);
        if (offset >= 0 && offset != mCurrentOffset) {
            int dx = (int) (mCurrentOffset - offset) * -1;
            mScroller.forceFinished(true);
            mScroller.startScroll((int) mCurrentOffset, 0, dx, 0);
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    private long computeTimestampFromOffset(float offset) {
        final LongSparseArray<Pair<double[], int[]>> data;
        synchronized (mLock) {
            data = mData;
        }
        int size = data.size() -1;
        if (size <= 0) {
            return -1;
        }

        // Check if we are in an space area and not in a bar area
        final float barSize = (mBarItemWidth + mBarItemSpace);
        double s = (offset + (mBarItemWidth / 2)) % barSize;
        if (s > mBarItemWidth) {
            // We are in a space area
            return -1;
        }

        // So we are in an bar area, so we have a valid index
        final int index = size - ((int) Math.ceil((offset - (mBarItemWidth / 2)) / barSize));
        return data.keyAt(index);
    }

    private float computeOffsetForTimestamp(long timestamp) {
        final LongSparseArray<Pair<double[], int[]>> data;
        synchronized (mLock) {
            data = mData;
        }
        final int index = data.indexOfKey(timestamp);
        if (index >= 0) {
            final int size = data.size();
            return ((mBarItemWidth + mBarItemSpace) * (size - index - 1));
        }
        return -1;
    }

    @Override
    protected void onDraw(Canvas c) {
        // 1.- Clip to padding
        c.clipRect(mViewArea);

        // 2.- Draw the backgrounds areas
        c.drawRect(mGraphArea, mGraphAreaBgPaint);
        if (mShowFooter) {
            c.drawRect(mFooterArea, mFooterAreaBgPaint);
        }

        final LongSparseArray<Pair<double[], int[]>> data;
        final double maxValue;
        synchronized (mLock) {
            data = mData;
            maxValue = mMaxValue;
        }
        boolean hasData = data.size() > 0;
        if (hasData) {
            // 3.- Compute viewport and draw the data
            computeItemsOnScreen(data);
            drawBarItems(c, data, maxValue);

            // 4.- Draw tick labels and current position
            if (mShowFooter) {
                drawTickLabels(c, data);
                c.drawPath(mCurrentPositionPath, mFooterAreaBgPaint);
            }
        }
    }

    private void drawBarItems(Canvas c, LongSparseArray<Pair<double[], int[]>> data,
            double maxValue) {

        final float halfItemBarWidth = mBarItemWidth / 2;
        final float height = mGraphArea.height();
        final Paint[] seriesBgPaint;
        final Paint[] highlightSeriesBgPaint;
        synchronized (mLock) {
            seriesBgPaint = mSeriesBgPaint;
            highlightSeriesBgPaint = mHighlightSeriesBgPaint;
        }

        // Apply zoom animation
        final float zoom = mCurrentZoom;
        final float cx = mGraphArea.left + (mGraphArea.width() / 2);
        int restoreCount = 0;
        if (zoom != 1.f) {
            restoreCount = c.save();
            c.scale(zoom, zoom, cx, mGraphArea.bottom);
        }

        final int size = data.size() - 1;
        for (int i = mItemsOnScreen[1]; i >= mItemsOnScreen[0]; i--) {
            final float x = mGraphArea.left + cx + mCurrentOffset
                    - ((mBarItemWidth + mBarItemSpace) * (size - i));
            float bw = mBarItemWidth / mSeries;
            double[] values = data.valueAt(i).first;
            int[] indexes = data.valueAt(i).second;

            float y1, y2 = height;
            float x1 = x - halfItemBarWidth, x2 = x + halfItemBarWidth;
            if (mGraphMode != GRAPH_MODE_BARS_STACK) {
                int count = values.length - 1;
                for (int j = count, n = 0; j >= 0; j--, n++) {
                    y1 = (float) (height - ((height * ((values[j] * 100) / maxValue)) / 100));
                    y2 = height;
                    final Paint paint;
                    if (mGraphMode == GRAPH_MODE_BARS_SIDE_BY_SIDE) {
                        x1 = x - halfItemBarWidth + (bw * n);
                        x2 = x1 + bw;
                        paint = (x - halfItemBarWidth) < cx && (x + halfItemBarWidth) > cx
                                ? highlightSeriesBgPaint[indexes[j]] : seriesBgPaint[indexes[j]];
                    } else {
                        paint = x1 < cx && x2 > cx
                                ? highlightSeriesBgPaint[indexes[j]] : seriesBgPaint[indexes[j]];
                    }

                    c.drawRect(
                            x1,
                            mGraphArea.top + y1,
                            x2,
                            mGraphArea.top + y2,
                            paint);
                }
            } else {
                int count = values.length;
                for (int j = 0; j < count; j++) {
                    float h = (float) ((height * ((values[j] * 100) / maxValue)) / 100);
                    y1 = y2 - h;

                    final Paint paint = x1 < cx && x2 > cx
                            ? highlightSeriesBgPaint[indexes[j]] : seriesBgPaint[indexes[j]];
                    c.drawRect(
                            x1,
                            mGraphArea.top + y1,
                            x2,
                            mGraphArea.top + y2,
                            paint);
                    y2 -= h;
                }
            }
        }

        // Restore from zoom
        if (zoom != 1.f) {
            c.restoreToCount(restoreCount);
        }
    }

    private void drawTickLabels(Canvas c, LongSparseArray<Pair<double[], int[]>> data) {
        final float alphaVariation = MAX_ZOOM_OUT - MIN_ZOOM_OUT;
        final float alpha = MAX_ZOOM_OUT - mCurrentZoom;
        mTickLabelFgPaint.setAlpha((int) ((alpha * 255) / alphaVariation));

        final int size = data.size() - 1;
        final float cx = mGraphArea.left + (mGraphArea.width() / 2);
        for (int i = mItemsOnScreen[1]; i >= mItemsOnScreen[0]; i--) {
            // Update the dynamic layout
            long timestamp = data.keyAt(i);
            mTickDate.setTime(timestamp);
            final String text = mTickFormatter.format(mTickDate)
                    .replace(".", "")
                    .toUpperCase(Locale.getDefault());
            mTickText.replace(0, mTickText.length(), text);
            mTickTextSpannable.update(mTickText);

            // Calculate the x position and draw the layout
            final float x = mGraphArea.left + cx + mCurrentOffset
                    - ((mBarItemWidth + mBarItemSpace) * (size - i))
                    - (mTickTextLayout.getWidth() / 2);
            final int restoreCount = c.save();
            c.translate(x, mFooterArea.top
                    + (mFooterArea.height() / 2 - mTickTextLayout.getHeight() / 2));
            mTickTextLayout.draw(c);
            c.restoreToCount(restoreCount);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mViewArea.set(
                getPaddingLeft(),
                getPaddingTop(),
                getWidth() - getPaddingRight(),
                getHeight() - getPaddingBottom());
        computeBoundAreas();
    }

    private void computeItemsOnScreen(LongSparseArray<Pair<double[], int[]>> data) {
        if (mLastOffset == mCurrentOffset) {
            return;
        }
        int size = data.size() - 1;
        float offset = mCurrentOffset + (mBarItemWidth / 2);
        float steps = mBarItemWidth + mBarItemSpace;
        int last = size - (int) Math.floor(offset / steps)
                + (int) Math.ceil(mMaxBarItemsInScreen / 2);
        int rest = 0;
        if (last > size) {
            rest = last - size;
            last = size;
        }
        int first = last - (mMaxBarItemsInScreen - 1) + rest;
        if (first < 0) {
            first = 0;
        }

        // Save the item positions
        mItemsOnScreen[0] = first;
        mItemsOnScreen[1] = last;
        mLastOffset = mCurrentOffset;
    }

    private void computeBoundAreas() {
        if (mShowFooter) {
            // Compute current based on the bar height
            computeCurrentPositionIndicatorDimensions();

            mGraphArea.set(mViewArea);
            mGraphArea.bottom = Math.max(mViewArea.bottom - mFooterBarHeight, 0);
            mFooterArea.set(mViewArea);
            mFooterArea.top = mGraphArea.bottom;
            mFooterArea.bottom = mGraphArea.bottom + mFooterBarHeight;

            mCurrentPositionPath.reset();
            final float w = mGraphArea.width();
            final float h = mGraphArea.height();
            if (w > 0 && h > 0) {
                mCurrentPositionPath.moveTo(
                        mGraphArea.left + (w / 2) - (mCurrentPositionIndicatorWidth / 2),
                        mGraphArea.bottom);
                mCurrentPositionPath.lineTo(
                        mGraphArea.left + w / 2,
                        mGraphArea.bottom - mCurrentPositionIndicatorHeight);
                mCurrentPositionPath.lineTo(
                        mGraphArea.left + (w / 2) + (mCurrentPositionIndicatorWidth / 2),
                        mGraphArea.bottom);
            }
        } else {
            mGraphArea.set(mViewArea);
            mFooterArea.set(-1, -1, -1, -1);
        }

        // Compute max bar items here too
        computeMaxBarItemsInScreen();
    }

    private void computeMaxBarItemsInScreen() {
        ensureBarWidth();
        mMaxBarItemsInScreen = (int) Math.ceil(mGraphArea.width() / (mBarItemWidth + mBarItemSpace));
    }

    private void computeCurrentPositionIndicatorDimensions() {
        mCurrentPositionIndicatorWidth = mBarItemWidth / 2.8f;
        mCurrentPositionIndicatorHeight = mBarItemWidth / 4f;
    }

    private void setupTickLabels() {
        synchronized (mLock) {
            final float textSizeFactor = mFooterBarHeight / mDefFooterBarHeight;
            mTickLabelFgPaint.setTextSize((int)(36 * textSizeFactor));

            final String format = getResources().getString(R.string.tlcDefTickFormat);
            mTickFormatter = new SimpleDateFormat(format, Locale.getDefault());
            mTickDate = new Date();
            final String text = mTickFormatter.format(mTickDate)
                    .replace(".", "")
                    .toUpperCase(Locale.getDefault());
            mTickText = new StringBuilder(text);
            mTickTextSpannable = new DynamicSpannableString(mTickText);
            mTickTextSpannable.setSpan(new AbsoluteSizeSpan((int)(56 * textSizeFactor)),0, 2,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            mTickTextLayout = new DynamicLayout(mTickTextSpannable, mTickLabelFgPaint,
                    (int) mBarItemWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 1.0f, false);
        }
    }

    private void setupSoundEffects() {
        if (mPlaySelectionSoundEffect && mSelectionSoundEffectSource != SYSTEM_SOUND_EFFECT) {
            if (mSoundEffectMP == null) {
                mSoundEffectMP = MediaPlayer.create(getContext(), mSelectionSoundEffectSource);
                mSoundEffectMP.setVolume(SOUND_EFFECT_VOLUME, SOUND_EFFECT_VOLUME);
            }
        } else if (mSoundEffectMP != null) {
            if (mSoundEffectMP.isPlaying()) {
                mSoundEffectMP.stop();
            }
            mSoundEffectMP.release();
            mSoundEffectMP = null;
        }
    }

    private void setupAnimators() {
        // A zoom-in/zoom-out animator
        mZoomAnimator = ValueAnimator.ofFloat(1.f);
        mZoomAnimator.setDuration(350L);
        mZoomAnimator.setInterpolator(new DecelerateInterpolator());
        mZoomAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mCurrentZoom = (Float) animation.getAnimatedValue();
                ViewCompat.postInvalidateOnAnimation(TimelineChartView.this);
            }
        });
        mZoomAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mInZoomOut) {
                    // FIXME Perform in a background
                    computeData();

                    // Generate bar items palette based on background color
                    setupSeriesBackground(mGraphAreaBgPaint.getColor());

                    // Redraw the data and notify the changes
                    notifyOnSelectionItemChanged(false);

                    // ZoomIn Effect
                    mInZoomOut = false;
                    mZoomAnimator.setFloatValues(MAX_ZOOM_OUT, MIN_ZOOM_OUT);
                    mZoomAnimator.start();
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        });
    }

    private void reloadCursorData(boolean animate) {
        // Load the cursor to memory
        if (mAnimateCursorSwapTransition && animate) {
            if (mZoomAnimator.isRunning()) {
                mZoomAnimator.cancel();
            }
            mInZoomOut = true;
            mZoomAnimator.setFloatValues(MIN_ZOOM_OUT, MAX_ZOOM_OUT);
            mZoomAnimator.start();
        } else {
            // FIXME Perform in a background
            computeData();

            // Generate bar items palette based on background color
            setupSeriesBackground(mGraphAreaBgPaint.getColor());

            // Redraw the data and notify the changes
            ViewCompat.postInvalidateOnAnimation(this);
            notifyOnSelectionItemChanged(false);
        }
    }

    private void computeData() {
        if (!mCursor.isClosed() && mCursor.moveToFirst()) {
            // Load the cursor to memory
            double max = 0d;
            LongSparseArray<Pair<double[], int[]>> data = new LongSparseArray<>();
            mSeries = mCursor.getColumnCount() - 1;

            do {
                long timestamp = mCursor.getLong(0);
                final double[] seriesData = new double[mSeries];
                final int[] indexes = new int[mSeries];
                double stackVal = 0d;
                for (int i = 0; i < mSeries; i++) {
                    final double v = mCursor.getDouble(i + 1);
                    seriesData[i] = v;
                    if (mGraphMode != GRAPH_MODE_BARS_STACK && v > max) {
                        max = v;
                    } else {
                        stackVal += v;
                    }
                    indexes[i] = i;
                }
                if (mGraphMode == GRAPH_MODE_BARS_STACK && stackVal > max) {
                    max = stackVal;
                }

                // Sort the items to properly one over other in screen
                if (mGraphMode == GRAPH_MODE_BARS) {
                    ArraysHelper.sort(seriesData, indexes);
                }
                Pair<double[], int[]> pair = new Pair<>(seriesData, indexes);
                data.put(timestamp, pair);
            } while (mCursor.moveToNext());

            // Calculate the max available offset
            int size = data.size() - 1;
            float maxOffset = (mBarItemWidth + mBarItemSpace) * size;

            //swap data
            synchronized (mLock) {
                mData = data;
                mMaxValue = max;
                mLastOffset = -1.f;
                mMaxOffset = maxOffset;
                mCurrentTimestamp = -2;
            }
        } else {
            // Cursor is empty or closed
            clear();
        }
    }

    private void checkCursorIntegrity(Cursor c) {
        int columnCount = c.getColumnCount();
        if (columnCount < 1) {
            throw new IllegalArgumentException("Cursor must have at least 2 columns");
        }
        if (!isNumericColumnType(0, c)) {
            throw new IllegalArgumentException("Column 0 must be a timestamp (numeric type)");
        }
        for (int i = 1; i < columnCount; i++) {
            if (!isNumericColumnType(i, c)) {
                throw new IllegalArgumentException("All series must be a valid numeric type");
            }
        }
    }

    private boolean isNumericColumnType(int columnIndex, Cursor c) {
        int type = c.getType(columnIndex);
        return type == Cursor.FIELD_TYPE_INTEGER || type == Cursor.FIELD_TYPE_FLOAT;
    }

    private void setupSeriesBackground(int color) {
        int[] currentPalette = new int[mSeries];
        Paint[] seriesBgPaint = new Paint[mSeries];
        Paint[] highlightSeriesBgPaint = new Paint[mSeries];
        if (mSeries == 0) {
            return;
        }

        int userPaletteCount = 0;
        if (mUserPalette != null) {
            userPaletteCount = mUserPalette.length;
            for (int i = 0; i < userPaletteCount; i++) {
                seriesBgPaint[i] = new Paint();
                currentPalette[i] = mUserPalette[i];
                seriesBgPaint[i].setColor(currentPalette[i]);
                highlightSeriesBgPaint[i] = new Paint(seriesBgPaint[i]);
                highlightSeriesBgPaint[i].setColor(
                        MaterialPaletteHelper.getComplementaryColor(currentPalette[i]));
            }
        }

        // Generate bar items palette based on background color
        int needed = mSeries - userPaletteCount;
        int[] palette = MaterialPaletteHelper.createMaterialSpectrumPalette(color, needed);
        for (int i = userPaletteCount; i < mSeries; i++) {
            seriesBgPaint[i] = new Paint();
            currentPalette[i] = palette[i - userPaletteCount];
            seriesBgPaint[i].setColor(currentPalette[i]);
            highlightSeriesBgPaint[i] = new Paint(seriesBgPaint[i]);
            highlightSeriesBgPaint[i].setColor(
                    MaterialPaletteHelper.getComplementaryColor(currentPalette[i]));
        }

        final boolean changed = !(Arrays.equals(currentPalette, mCurrentPalette));

        synchronized (mLock) {
            mCurrentPalette = currentPalette;
            mSeriesBgPaint = seriesBgPaint;
            mHighlightSeriesBgPaint = highlightSeriesBgPaint;
        }

        if (changed) {
            notifyOnColorPaletteChanged();
        }
    }

    private void invalidateCursorData() {
        clear();
    }

    private void clear() {
        synchronized (mLock) {
            mData.clear();
            mMaxValue = 0d;
            mCurrentTimestamp = -1;
        }
    }

    private void notifyOnSelectionItemChanged(boolean fromUser) {
        if (mOnSelectedItemChangedCallbacks.size() == 0) {
            return;
        }

        Pair<double[], int[]> data = mData.get(mCurrentTimestamp);
        if (data == null) {
            for (OnSelectedItemChangedListener cb : mOnSelectedItemChangedCallbacks) {
                cb.onNothingSelected();
            }
        } else {
            // Compute current item. Restore original sort before notify
            Item item = new Item();
            item.mTimestamp = mCurrentTimestamp;
            item.mSeries = new double[data.first.length];
            for (int i = 0; i < data.first.length; i++) {
                item.mSeries[i] = data.first[data.second[i]];
            }

            for (OnSelectedItemChangedListener cb : mOnSelectedItemChangedCallbacks) {
                cb.onSelectedItemChanged(item, fromUser);
            }
        }
    }

    private void notifyOnColorPaletteChanged() {
        for (OnColorPaletteChanged cb : mOnColorPaletteChangedCallbacks) {
            cb.onColorPaletteChanged(mCurrentPalette);
        }
    }

    private void ensureBarWidth() {
        if (!mShowFooter) {
            return;
        }
        float minWidth = mTickTextLayout.getWidth();
        if (minWidth > mBarItemWidth) {
            Log.w(TAG, "There is not enough space for labels. Switch BarItemWidth to " + minWidth);
            mBarItemWidth = mTickTextLayout.getWidth();
        }
    }

    private void performSelectionSoundEffect() {
        if (mPlaySelectionSoundEffect) {
            if (mSelectionSoundEffectSource == SYSTEM_SOUND_EFFECT) {
                mAudioManager.playSoundEffect(SoundEffectConstants.CLICK, SOUND_EFFECT_VOLUME);
            } else {
                mSoundEffectMP.start();
            }
        }
    }
}
