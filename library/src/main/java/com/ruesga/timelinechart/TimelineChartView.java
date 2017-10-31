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
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.RawRes;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.LongSparseArray;
import android.support.v4.util.Pair;
import android.support.v4.view.ViewCompat;
import android.text.DynamicLayout;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.AbsoluteSizeSpan;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.EdgeEffect;
import android.widget.OverScroller;

import com.ruesga.timelinechart.helpers.ArraysHelper;
import com.ruesga.timelinechart.helpers.MaterialPaletteHelper;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * A view to represent data over a timeline.<p />
 * <p />
 * This class uses a {@link Cursor} to draw the data. All cursors must follow
 * the following constrains:
 * <ul>
 *     <li>The first field must contains a timestamp, which represent
 *         a time in the graph timeline. This value will be the key to access to
 *         the graph information.</li>
 *     <li>One or more float/double numeric in the rest of the fields of
 *         the cursor. Every one of this fields will represent a serie in the
 *         graph.</li>
 * </ul>
 * <p />
 *
 * User must call {@link #observeData(Cursor)} or {@link #observeData(Cursor, int)}
 * to allow the view observe for data changes in the cursor. Once the view observes
 * the cursor, any change detected in the cursor will be reflected in the graph view.
 * The method {@link #observeData(Cursor, int)} can be used to perform optimizations around
 * the data load process. The follow optimizations can be used.
 * <ul>
 *     <li><b>NO_OPTIMIZATION</b>. Internal data will be destroyed and recreated every
 *         time the cursor changes. Use this value if you know that the cursor can vary its
 *         number of fields (series to display in the graph). This will be the default
 *         optimization by default.</li>
 *     <li><b>NO_DELETES_OPTIMIZATION</b>. Swap data is consistent, so is safe to add and
 *         update information (no deletion will happen), reducing the number of internal
 *         references to create. Use this optimization if you know that the cursor won't
 *         vary its number of fields (series to display in the graph) and existent data
 *         must not be deleted.</li>
 *     <li><b>ONLY_ADDITIONS_OPTIMIZATION</b>. Only new records are added at the end of
 *         the cursor. This is optimized for live graphs where new records are added as
 *         time goes. The data load process won't update, delete or add information
 *         older than the last timestamp saw in the last iteration. Data in cursor expected
 *         to be sorted ascending by timestamp and cursor won't vary its number
 *         of fields (series to display in the graph).</li>
 * </ul>
 * <p />
 * <p />
 *
 * The view supports various graph mode representations that can be established via
 * {@code tlcGraphMode} attribute or {@link #setGraphMode(int)} method.<p />
 *
 * The view features an auto-generation of a material-based color palette based on the
 * background of the {@code tlcGraphBackground} attribute. User can override this
 * color palette with its own one.<p />
 *
 * The view features an auto tick labeled, based on the sort of time. The minimum level
 * displayed label is based in days, displaying hours, minutes or seconds when
 * those amounts are detected.
 * <p />
 * <p />
 *
 * <h4>Attributes:</h4>
 *
 * The view has a set of custom attributes to allow configure of the view behaviour. All
 * this attributes can be set via layout's xml attributes or setters.<p />
 *
 * <ul>
 *     <li><b>tlcGraphBackground</b>: Background color of the graph area. This value
 *         also determines the auto-generated palette of colors, if no user palette was
 *         used. {@link #setGraphAreaBackground(int)} can be used at runtime to
 *         set this color.</li><p />
 *
 *     <li><b>tlcFooterBackground</b>: Background color of the footer area.
 *         {@link #setFooterAreaBackground(int)} can be used at runtime to set this
 *         color.</li><p />
 *
 *     <li><b>tlcShowFooter</b>: A boolean value indicating whether to show/hide
 *         the footer of the graph. {@link #setShowFooter(boolean)} can be used at runtime
 *         to show/hide the footer area.</li><p />
 *
 *     <li><b>tlcFooterBarHeight</b>: A float value indicating the height of footer
 *         area of the graph. {@link #setFooterHeight(float)} can be used at runtime
 *         to set the height the footer area.</li><p />
 *
 *     <li><b>tlcBarItemWidth</b>: A float value indicating the width of a bar item
 *         of the graph. {@link #setBarItemWidth(float)} can be used at runtime
 *         to set the width of a bar item.</li><p />
 *
 *     <li><b>tlcBarItemSpace</b>: A float value indicating the space between bar items.
 *         {@link #setBarItemSpace(float)} can be used at runtime
 *         to set the space between bar items.</li><p />
 *
 *     <li><b>tlcGraphMode</b>: The graph representation mode. This attribute accepts
 *         3 modes: tlcBars (series are draw one over the other), tlcBarsStack
 *         (series are draw one on top the other), tlcBarsSideBySide (series are
 *         draw one beside the other). {@link #setGraphMode(int)} can be used at runtime
 *         to set the graph mode (see {@link #GRAPH_MODE_BARS}, {@link #GRAPH_MODE_BARS_STACK}
 *         and {@link #GRAPH_MODE_BARS_SIDE_BY_SIDE}).</li><p />
 *
 *     <li><b>tlcPlaySelectionSoundEffect</b>: A boolean value indicating whether play
 *         sound effects on item selection. {@link #setPlaySelectionSoundEffect(boolean)} can be
 *         used at runtime to set if the view should reproduce sound effects.</li><p />
 *
 *     <li><b>tlcSelectionSoundEffectSource</b>: A reference to a raw resource identifier
 *         that will be play as sound effect on item selection. Define an invalid resource identifier
 *         (value 0) to use the system default sound effect.
 *         {@link #setSelectionSoundEffectSource(int)} can be used at runtime to set the
 *         resource to use as sound effect.</li><p />
 *
 *     <li><b>tlcAnimateCursorTransition</b>: Whether full cursor swap are graphical animated.
 *         {@link #setAnimateCursorTransition(boolean)} can be used at runtime to set
 *
 *     <li><b>tlcFollowCursorPosition</b>: Whether follow real time cursor updates (live update).
 *         Only if scroll is in the last item. {@link #setFollowCursorPosition(boolean)}
 *         can be used at runtime to set the behaviour on cursor updates.</li><p />
 *
 *     <li><b>tlcAlwaysEnsureSelection</b>: Whether move current view to the nearest selection
 *         if, after a user scroll/fling operation, the view is not centered in an item.
 *         If {@code true} move view to the nearest item and selected it.
 *         {@link #setAlwaysEnsureSelection(boolean)} can be used at runtime to set the
 *         behaviour when selection requires to be ensured.</li><p />
 * </ul>
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class TimelineChartView extends View {

    private static final String TAG = "TimelineChartView";

    /**
     * A class that represents a item information.
     */
    public static class Item {
        private Item() {
        }

        /**
         * The timestamp that data belongs to.
         */
        public long mTimestamp;

        /**
         * The values of all the series for the timestamp.
         */
        public double[] mSeries;
    }

    /**
     * A class that represents a item event information.
     */
    public static class ItemEvent {
        private ItemEvent() {
        }

        /**
         * The timestamp associated to the event.
         */
        public long mTimestamp;

        /**
         * The serie associated to the event.
         */
        public int mSerie;
    }

    /**
     * An interface definition to notify click event on items.
     */
    public interface OnClickItemListener {
        /**
         * Called on a click event detected on a area containing an item.
         *
         * @param item the item where the click was detected
         * @param serie the number of the serie on which the click was detected, or -1 if
         *              it happened on a shared area of the item (xe: the tick label area).
         */
        void onClickItem(Item item, int serie);
    }

    /**
     * An interface definition to notify long click event on items.
     */
    public interface OnLongClickItemListener {
        /**
         * Called on a long click event detected on a area containing an item.
         *
         * @param item the item where the long click was detected
         * @param serie the number of the serie on which the long click was detected, or -1 if
         *              it happened on a shared area of the item (xe: the tick label area).
         */
        void onLongClickItem(Item item, int serie);
    }

    /**
     * An interface definition to notify item selection event.
     */
    public interface OnSelectedItemChangedListener {
        /**
         * Called when a item was selected.
         *
         * @param selectedItem information about the selected item
         * @param fromUser whether the event came from a user iteration.
         */
        void onSelectedItemChanged(Item selectedItem, boolean fromUser);

        /**
         * Called when there is no selection.
         */
        void onNothingSelected();
    }

    /**
     * An interface definition to notify changes in the color palette.
     */
    public interface OnColorPaletteChangedListener {
        /**
         * Called when the color palette changed.
         *
         * @param palette the new color palette.
         */
        void onColorPaletteChanged(int[] palette);
    }

    private class LongPressDetector implements Runnable {
        boolean mLongPressTriggered;

        @Override
        public void run() {
            mLongPressTriggered = true;
            Message.obtain(mUiHandler, MSG_ON_LONG_CLICK_ITEM, computeItemEvent()).sendToTarget();
        }
    }

    /** Constant to define the graph mode to normal bars (series are draw one over the other).*/
    public static final int GRAPH_MODE_BARS = 0;
    /** Constant to define the graph mode to stacked bars (series are draw one on top the other).*/
    public static final int GRAPH_MODE_BARS_STACK = 1;
    /** Constant to define the graph mode to beside bars (series are draw one beside the other).*/
    public static final int GRAPH_MODE_BARS_SIDE_BY_SIDE = 2;

    /**
     * Internal data will be destroyed and recreated every time the cursor changes. Use this
     * value if you know that the cursor can vary its number of fields (series to display in
     * the graph).
     */
    public static final int NO_OPTIMIZATION = 0;
    /**
     * Swap data is consistent, so is safe to add and update information (no deletion will happen),
     * reducing the number of internal references to create. Use this optimization if
     * you know that the cursor won't vary its number of fields (series to display in
     * the graph) and existent data must not be deleted.
     */
    public static final int NO_DELETES_OPTIMIZATION = 1;
    /**
     * Only new records are added at the end of the cursor. This is optimized for live
     * graphs where new records are added as time goes. The data load process won't update,
     * delete or add information older than the last timestamp saw in the last iteration.
     * Data in cursor expected to be sorted ascending by timestamp and cursor won't vary
     * its number of fields (series to display in the graph).
     */
    public static final int ONLY_ADDITIONS_OPTIMIZATION = 2;

    // Sort of available formats for tick labels
    private static final int TICK_LABEL_SECONDS_FORMAT = 0;
    private static final int TICK_LABEL_HOUR_MINUTES_FORMAT = 1;
    private static final int TICK_LABEL_DAY_FORMAT = 2;

    private static final float MAX_ZOOM_OUT = 4.0f;
    private static final float MIN_ZOOM_OUT = 1.0f;

    private static final float SOUND_EFFECT_VOLUME = 0.3f;
    private static final int SYSTEM_SOUND_EFFECT = 0;

    private static final int TAP_TIMEOUT = 50;

    private Cursor mCursor;
    private int mOptimizationFlag = NO_OPTIMIZATION;
    private int mSeries;
    private LongSparseArray<Pair<double[],int[]>> mData = new LongSparseArray<>();
    private double mMaxValue;
    private final Item mItem = new Item();
    private final RectF mSerieRect = new RectF();

    private int mSeriesSwap;
    private LongSparseArray<Pair<double[],int[]>> mDataSwap = new LongSparseArray<>();
    private double mMaxValueSwap;
    private float mMaxOffsetSwap;
    private boolean mTickHasDayFormatSwap;

    private final RectF mViewArea = new RectF();
    private final RectF mGraphArea = new RectF();
    private final RectF mFooterArea = new RectF();
    private float mDefFooterBarHeight;
    private float mFooterBarHeight;
    private boolean mShowFooter;
    private int mGraphMode;
    private boolean mPlaySelectionSoundEffect;
    private int mSelectionSoundEffectSource;
    private boolean mAnimateCursorTransition;
    private boolean mFollowCursorPosition;
    private boolean mAlwaysEnsureSelection;

    private EdgeEffect mEdgeEffectLeft;
    private EdgeEffect mEdgeEffectRight;
    private boolean mEdgeEffectLeftActive;
    private boolean mEdgeEffectRightActive;

    private int[] mUserPalette;
    private int[] mCurrentPalette;

    private float mBarItemWidth;
    private float mBarItemSpace;
    private float mBarWidth;
    private float mCurrentPositionIndicatorWidth;
    private float mCurrentPositionIndicatorHeight;

    private Paint mGraphAreaBgPaint;
    private Paint mFooterAreaBgPaint;

    private Paint[] mSeriesBgPaint;
    private Paint[] mHighlightSeriesBgPaint;
    private TextPaint mTickLabelFgPaint;

    private final Path mCurrentPositionPath = new Path();

    private long mCurrentTimestamp = -1;
    private long mLastTimestamp = -1;
    private float mCurrentOffset = 0.f;
    private float mLastOffset = -1.f;
    private float mMaxOffset = 0.f;
    private float mInitialTouchOffset = 0.f;
    private float mInitialTouchX = 0.f;
    private float mInitialTouchY = 0.f;
    private float mLastX = 0.f;
    private float mLastY = 0.f;
    private float mCurrentZoom = 1.f;

    private int mMaxBarItemsInScreen = 0;
    private final int[] mItemsOnScreen = new int[2];

    private SimpleDateFormat[] mTickFormatter;
    private Date mTickDate;
    private SparseArray<DynamicSpannableString>[] mTickTextSpannables;
    private SparseArray<DynamicLayout>[] mTickTextLayouts;
    private Calendar mTickCalendar;
    private boolean mTickHasDayFormat;
    private float mTickLabelMinHeight;

    private String[] mTickLabels;
    private String[] mTickFormats;

    private float mTextSizeFactor;
    private float mSize8;
    private float mSize12;
    private float mSize20;

    private VelocityTracker mVelocityTracker;
    private OverScroller mScroller;
    private final LongPressDetector mLongPressDetector = new LongPressDetector();
    private long mLongPressTimeout;
    private float mTouchSlop;
    private float mMaxFlingVelocity;
    private long mLastPressTimestamp;

    private static final int STATE_IDLE = 0;
    private static final int STATE_INITIALIZE = 1;
    private static final int STATE_MOVING = 2;
    private static final int STATE_FLINGING = 3;
    private static final int STATE_SCROLLING = 4;
    private static final int STATE_ZOOMING = 5;
    private int mState = STATE_IDLE;

    private static final int MSG_ON_SELECTION_ITEM_CHANGED = 1;
    private static final int MSG_ON_CLICK_ITEM = 2;
    private static final int MSG_ON_LONG_CLICK_ITEM = 3;
    private static final int MSG_COMPUTE_DATA = 4;
    private static final int MSG_UPDATE_COMPUTED_DATA = 5;

    private Handler mUiHandler;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundHandlerThread;

    private boolean mIsDataComputed;

    private final Handler.Callback mMessenger = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                // Ui thread
                case MSG_ON_SELECTION_ITEM_CHANGED:
                    notifyOnSelectionItemChanged((boolean) msg.obj);
                    return true;
                case MSG_ON_CLICK_ITEM:
                    notifyGenericClickEvent((ItemEvent) msg.obj);
                    return true;
                case MSG_ON_LONG_CLICK_ITEM:
                    notifyGenericLongClickEvent((ItemEvent) msg.obj);
                    return true;
                case MSG_UPDATE_COMPUTED_DATA:
                    // Generate bar items palette based on background color
                    setupSeriesBackground(mGraphAreaBgPaint.getColor());
                    mIsDataComputed = true;

                    // Redraw the data and notify the changes
                    notifyOnSelectionItemChanged(false);

                    // Animate?
                    if (msg.arg1 == 1) {
                        mInZoomOut = false;
                        mZoomAnimator.setFloatValues(MAX_ZOOM_OUT, MIN_ZOOM_OUT);
                        mZoomAnimator.start();
                    }

                    // Update the graph view
                    ViewCompat.postInvalidateOnAnimation(TimelineChartView.this);
                    return true;

                // Non-Ui thread
                case MSG_COMPUTE_DATA:
                    performComputeData(msg.arg1 == 1, msg.arg2 == 1);
                    return true;
            }
            return false;
        }
    };

    // Cursor observers
    private final DataSetObserver mDataSetObserver = new DataSetObserver() {
        @Override
        public void onChanged() {
            // Avoid this operation if ContentObserver is reloading the cursor
            if (mObserverStatus != 2) {
                mObserverStatus = 1;
                reloadCursorData(false);
                mObserverStatus = 0;
            }
        }

        @Override
        public void onInvalidated() {
            // Avoid this operation if ContentObserver is reloading the cursor
            if (mObserverStatus != 2) {
                mObserverStatus = 1;
                clear();
                mObserverStatus = 0;
            }
        }
    };
    private class CursorContentObserver extends ContentObserver {
        public CursorContentObserver(Handler handler) {
            super(handler);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            // This required database/disk access so, it must be execute in
            // a background handler
            // Avoid this operation if DataSetObserver is reloading the cursor
            if (mObserverStatus != 1) {
                mObserverStatus = 2;
                synchronized (mCursorLock) {
                    mCursor.requery();
                }
                reloadCursorData(false);
                mObserverStatus = 0;
            }
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            onChange(selfChange);
        }
    }
    private ContentObserver mContentObserver;
    private int mObserverStatus = 0;

    private AudioManager mAudioManager;
    private MediaPlayer mSoundEffectMP;

    private OnClickItemListener mOnClickItemCallback;
    private OnLongClickItemListener mOnLongClickItemCallback;
    private final Set<OnSelectedItemChangedListener> mOnSelectedItemChangedCallbacks =
            Collections.synchronizedSet(new HashSet<OnSelectedItemChangedListener>());
    private final Set<OnColorPaletteChangedListener> mOnColorPaletteChangedCallbacks =
            Collections.synchronizedSet(new HashSet<OnColorPaletteChangedListener>());

    private boolean mInZoomOut = false;
    private ValueAnimator mZoomAnimator;

    private final Object mLock = new Object();
    private final Object mCursorLock = new Object();


    /** {@inheritDoc} */
    public TimelineChartView(Context ctx) {
        this(ctx, null, 0);
    }

    /** {@inheritDoc} */
    public TimelineChartView(Context ctx, AttributeSet attrs) {
        this(ctx, attrs, 0);
    }

    /** {@inheritDoc} */
    public TimelineChartView(Context ctx, AttributeSet attrs, int defStyleAttr) {
        super(ctx, attrs, defStyleAttr);
        init(ctx, attrs, defStyleAttr, 0);
    }

    /** {@inheritDoc} */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public TimelineChartView(Context ctx, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(ctx, attrs, defStyleAttr, defStyleRes);
        init(ctx, attrs, defStyleAttr, defStyleRes);
    }

    private void init(Context ctx, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        mUiHandler = new Handler(Looper.getMainLooper(), mMessenger);
        if (!isInEditMode()) {
            mAudioManager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        }

        final Resources res = getResources();
        final Resources.Theme theme = ctx.getTheme();

        mTickFormats = getResources().getStringArray(R.array.tlcDefTickLabelFormats);
        mTickLabels = getResources().getStringArray(R.array.tlcDefTickLabelValues);

        final DisplayMetrics dp = getResources().getDisplayMetrics();
        mSize8 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 8, dp);
        mSize12 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12, dp);
        mSize20 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 20, dp);

        final ViewConfiguration vc = ViewConfiguration.get(ctx);
        mLongPressTimeout = ViewConfiguration.getLongPressTimeout();
        mTouchSlop = vc.getScaledTouchSlop() / 2;
        mMaxFlingVelocity = vc.getScaledMaximumFlingVelocity();
        mScroller = new OverScroller(ctx);

        int graphBgColor = ContextCompat.getColor(ctx, R.color.tlcDefGraphBackgroundColor);
        int footerBgColor = ContextCompat.getColor(ctx, R.color.tlcDefFooterBackgroundColor);

        mDefFooterBarHeight = mFooterBarHeight = res.getDimension(R.dimen.tlcDefFooterBarHeight);
        mShowFooter = res.getBoolean(R.bool.tlcDefShowFooter);
        mGraphMode = res.getInteger(R.integer.tlcDefGraphMode);
        mPlaySelectionSoundEffect = res.getBoolean(R.bool.tlcDefPlaySelectionSoundEffect);
        mSelectionSoundEffectSource = res.getInteger(R.integer.tlcDefSelectionSoundEffectSource);
        mAnimateCursorTransition = res.getBoolean(R.bool.tlcDefAnimateCursorTransition);
        mFollowCursorPosition = res.getBoolean(R.bool.tlcDefFollowCursorPosition);
        mAlwaysEnsureSelection = res.getBoolean(R.bool.tlcDefAlwaysEnsureSelection);

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
                } else if (attr == R.styleable.tlcTimelineChartView_tlcAnimateCursorTransition) {
                    mAnimateCursorTransition = a.getBoolean(attr, mAnimateCursorTransition);
                } else if (attr == R.styleable.tlcTimelineChartView_tlcFollowCursorPosition) {
                    mFollowCursorPosition = a.getBoolean(attr, mFollowCursorPosition);
                } else if (attr == R.styleable.tlcTimelineChartView_tlcAlwaysEnsureSelection) {
                    mAlwaysEnsureSelection = a.getBoolean(attr, mAlwaysEnsureSelection);
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

        // Minimize the impact of create dynamic layouts by assume that in most case
        // we will have a day formatter
        mTickHasDayFormat = true;

        // Initialize stuff
        setupBackgroundHandler();
        setupTickLabels();
        if (getOverScrollMode() != OVER_SCROLL_NEVER) {
            setupEdgeEffects();
        }
        setupAnimators();
        setupSoundEffects();

        // Initialize the drawing refs (this will be update when we have
        // the real size of the canvas)
        computeBoundAreas();

        // Create a fake data for the edit mode
        if (isInEditMode()) {
            setupViewInEditMode();
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setupBackgroundHandler();
        setupSoundEffects();
        mContentObserver = new CursorContentObserver(mBackgroundHandler);
        synchronized (mCursorLock) {
            if (mCursor != null) {
                mCursor.registerContentObserver(mContentObserver);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        // Destroy background thread
        mBackgroundHandlerThread.quit();
        mBackgroundHandler = null;
        mBackgroundHandlerThread = null;

        // Destroy cursor
        releaseCursor();

        // Destroy internal tracking variables
        clear();
        releaseSoundEffects();
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mTickCalendar = Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault());
    }

    /**
     * Set the color of the background of the graph area.
     */
    public void setGraphAreaBackground(int color) {
        if (mGraphAreaBgPaint.getColor() != color) {
            mGraphAreaBgPaint.setColor(color);
            setupSeriesBackground(color);
            setupEdgeEffectColor();
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    /**
     * Whether the footer is shown.
     */
    public boolean isShowFooter() {
        return mShowFooter;
    }

    /**
     * Whether the footer will be shown.
     */
    public void setShowFooter(boolean show) {
        if (mShowFooter != show) {
            mShowFooter = show;
            computeBoundAreas();
            requestLayout();
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    /**
     * Set the color of the background of the footer area.
     */
    public void setFooterAreaBackground(int color) {
        if (mFooterAreaBgPaint.getColor() != color) {
            mFooterAreaBgPaint.setColor(color);
            mTickLabelFgPaint.setColor(MaterialPaletteHelper.isDarkColor(color)
                    ? Color.LTGRAY : Color.DKGRAY);
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    /**
     * Returns the height in pixels of the footer area.
     */
    public float getFooterBarHeight() {
        return mFooterBarHeight;
    }

    /**
     * Sets the height in pixels of the footer area.
     */
    public void setFooterHeight(float height) {
        if (mFooterBarHeight != height) {
            mFooterBarHeight = height;
            computeBoundAreas();
            setupTickLabels();
            requestLayout();
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    /**
     * Returns the space in pixels between bar items.
     */
    public float getBarItemSpace() {
        return mBarItemSpace;
    }

    /**
     * Sets the space in pixels between bar items.
     */
    public void setBarItemSpace(float barItemSpace) {
        if (mBarItemSpace != barItemSpace) {
            mBarItemSpace = barItemSpace;
            computeMaxBarItemsInScreen();
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    /**
     * Returns the width in pixels of a bar item.
     */
    public float getBarItemWidth() {
        return mBarItemWidth;
    }

    /**
     * Sets the width in pixels of a bar item.
     */
    public void setBarItemWidth(float barItemWidth) {
        if (mBarItemWidth != barItemWidth) {
            mBarItemWidth = barItemWidth;
            computeBoundAreas();
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    /**
     * Returns the graph mode representation.
     * @see #GRAPH_MODE_BARS
     * @see #GRAPH_MODE_BARS_STACK}
     * @see #GRAPH_MODE_BARS_SIDE_BY_SIDE
     */
    public int getGraphMode() {
        return mGraphMode;
    }

    /**
     * Sets the graph mode representation.
     * @see #GRAPH_MODE_BARS
     * @see #GRAPH_MODE_BARS_STACK
     * @see #GRAPH_MODE_BARS_SIDE_BY_SIDE
     */
    public void setGraphMode(int mode) {
        if (mode != mGraphMode) {
            mGraphMode = mode;
            Message.obtain(mBackgroundHandler, MSG_COMPUTE_DATA).sendToTarget();
        }
    }

    /**
     * Whether cursor swaps are animated.
     */
    public boolean isAnimateCursorTransition() {
        return mAnimateCursorTransition;
    }

    /**
     * Whether cursor swaps should be animated.
     */
    public void setAnimateCursorTransition(boolean animateCursorTransition) {
        mAnimateCursorTransition = animateCursorTransition;
    }

    /**
     * Whether graph is following real time cursor updates (live update).
     */
    public boolean isFollowCursorPosition() {
        return mFollowCursorPosition;
    }

    /**
     * Whether follow real time cursor updates (live update). Only if scroll is in the last item.
     */
    public void setFollowCursorPosition(boolean follow) {
        mFollowCursorPosition = follow;
    }

    /**
     * Whether view will move to the nearest selection if, after a user
     * scroll/fling operation, the view is not centered in an item.
     */
    public boolean isAlwaysEnsureSelection() {
        return mAlwaysEnsureSelection;
    }

    /**
     * Whether move current view to the nearest selection if, after a user
     * scroll/fling operation, the view is not centered in an item. If {@code true} move
     * view to the nearest item and selected it.
     */
    public void setAlwaysEnsureSelection(boolean ensureSelection) {
        mAlwaysEnsureSelection = ensureSelection;
    }

    /**
     * Returns the user color palette.
     */
    public int[] getUserPalette() {
        return mUserPalette;
    }

    /**
     * Sets the user color palette.
     */
    public void setUserPalette(int[] userPalette) {
        if (!Arrays.equals(mUserPalette, userPalette)) {
            mUserPalette = userPalette;
            setupSeriesBackground(mGraphAreaBgPaint.getColor());
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    /**
     * Returns the current color palette (it could be a combination of the user
     * palette and the generated one).
     * @see #setUserPalette(int[])
     */
    public int[] getCurrentPalette() {
        return mCurrentPalette;
    }

    /**
     * Whether play a sound effect on item selection.
     */
    public boolean isPlaySelectionSoundEffect() {
        return mPlaySelectionSoundEffect;
    }

    /**
     * Whether should play a sound effect on item selection.
     */
    public void setPlaySelectionSoundEffect(boolean value) {
        mPlaySelectionSoundEffect = value;
        setupSoundEffects();
    }

    /**
     * Returns the raw resource identifier used to play a sound effect
     * on item selection, or {@code 0} if default system effect is used.
     */
    public int getSelectionSoundEffectSource() {
        return mSelectionSoundEffectSource;
    }

    /**
     * Sets the raw resource identifier to use to play a sound effect
     * on item selection. Use {@code 0} to use the default system effect.
     */
    public void setSelectionSoundEffectSource(@RawRes int source) {
        this.mSelectionSoundEffectSource = source;
        setupSoundEffects();
    }

    /**
     * Returns the callback which listen for click events on items.
     * @see com.ruesga.timelinechart.TimelineChartView.OnClickItemListener
     */
    public OnClickItemListener getOnClickItemListener() {
        return mOnClickItemCallback;
    }

    /**
     * Returns the callback which will listen for click events on items.
     * @see com.ruesga.timelinechart.TimelineChartView.OnClickItemListener
     */
    public void setOnClickItemListener(OnClickItemListener cb) {
        mOnClickItemCallback = cb;
    }

    /**
     * Returns the callback which listen for long click events on items.
     * @see com.ruesga.timelinechart.TimelineChartView.OnLongClickItemListener
     */
    public OnLongClickItemListener getOnLongClickItemListener() {
        return mOnLongClickItemCallback;
    }

    /**
     * Sets the callback which will listen for click events on items.
     * @see com.ruesga.timelinechart.TimelineChartView.OnLongClickItemListener
     */
    public void setOnLongClickItemListener(OnLongClickItemListener cb) {
        mOnLongClickItemCallback = cb;
    }

    /**
     * Register the callback as a listener to start receiving item selection changes.
     * @see com.ruesga.timelinechart.TimelineChartView.OnSelectedItemChangedListener
     */
    public void addOnSelectedItemChangedListener(OnSelectedItemChangedListener cb) {
        mOnSelectedItemChangedCallbacks.add(cb);
    }

    /**
     * Unregister the callback as a listener to stop receiving item selection changes.
     * @see com.ruesga.timelinechart.TimelineChartView.OnSelectedItemChangedListener
     */
    public void removeOnSelectedItemChangedListener(OnSelectedItemChangedListener cb) {
        mOnSelectedItemChangedCallbacks.remove(cb);
    }

    /**
     * Register the callback as a listener to start receiving color palette changes.
     * @see com.ruesga.timelinechart.TimelineChartView.OnColorPaletteChangedListener
     */
    public void addOnColorPaletteChangedListener(OnColorPaletteChangedListener cb) {
        mOnColorPaletteChangedCallbacks.add(cb);
    }

    /**
     * Unregister the callback as a listener to stop receiving color palette changes.
     * @see com.ruesga.timelinechart.TimelineChartView.OnColorPaletteChangedListener
     */
    public void removeOnColorPaletteChangedListener(OnColorPaletteChangedListener cb) {
        mOnColorPaletteChangedCallbacks.remove(cb);
    }

    /**
     * Registers the cursor and start observing changes on it. This method won't perform
     * any sort of optimization in the data processing.
     * @see #observeData(Cursor, int)
     * @see #NO_OPTIMIZATION}
     */
    public void observeData(Cursor c) {
        observeData(c, NO_OPTIMIZATION);
    }

    /**
     * Registers the cursor and start observing changes on it.
     *
     * The cursor <i>MUST</i> follow the next constrains:
     * <ul>
     *     <li>The first field must contains a timestamp, which represent
     *         a time in the graph timeline. This value will be the key to access to
     *         the graph information.</li>
     *     <li>One or more float/double numeric in the rest of the fields of
     *         the cursor. Every one of this fields will represent a serie in the
     *         graph.</li>
     * </ul>
     *
     * @param c the cursor to observe.
     * @param flag An optimization flag. See optimization constants for a description of
     *             what every optimization does.
     * @see #NO_OPTIMIZATION
     * @see #NO_DELETES_OPTIMIZATION
     * @see #ONLY_ADDITIONS_OPTIMIZATION
     */
    public void observeData(Cursor c, int flag) {
        synchronized (mCursorLock) {
            checkCursorIntegrity(c);

            // Close previous cursor
            final boolean animate = mCursor != null;
            releaseCursor();

            // Ensure we have a valid handler (if for some reason view wasn't attached yet)
            setupBackgroundHandler();

            // Save the cursor reference and listen for changes
            mCursor = c;
            mOptimizationFlag = flag;
            reloadCursorData(animate);
            mCursor.registerDataSetObserver(mDataSetObserver);
            if (mContentObserver != null) {
                mCursor.registerContentObserver(mContentObserver);
            }
        }
    }

    @Override
    public boolean canScrollHorizontally(int direction) {
        final float x = mScroller.getCurrX();
        return (direction < 0 && x < mMaxOffset) || (direction > 0 && x > 0);
    }

    @Override
    public boolean canScrollVertically(int direction) {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("ClickableViewAccessibility")
    public boolean onTouchEvent(final MotionEvent event) {
        // Ignore events while performing scrolling animation
        if (mState == STATE_ZOOMING) {
            return true;
        }

        final int action = event.getActionMasked();
        final int index = event.getActionIndex();
        final int pointerId = event.getPointerId(index);
        final long now = System.currentTimeMillis();
        mLastX = event.getX();
        mLastY = event.getY();

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
                releaseEdgeEffects();
                mState = STATE_INITIALIZE;

                mLongPressDetector.mLongPressTriggered = false;
                mUiHandler.postDelayed(mLongPressDetector, mLongPressTimeout);

                mInitialTouchOffset = mCurrentOffset;
                mInitialTouchX = event.getX();
                mInitialTouchY = event.getY();
                mLastPressTimestamp = now;
                return true;

            case MotionEvent.ACTION_MOVE:
                // If a long press was detected then we end with the movement
                if (mLongPressDetector.mLongPressTriggered) {
                    return true;
                }

                mVelocityTracker.addMovement(event);
                float diffX = event.getX() - mInitialTouchX;
                float diffY = event.getY() - mInitialTouchY;
                if (Math.abs(diffX) > mTouchSlop || mState >= STATE_MOVING) {
                    mUiHandler.removeCallbacks(mLongPressDetector);

                    mCurrentOffset = mInitialTouchOffset + diffX;
                    if (mCurrentOffset < 0) {
                        onOverScroll();
                        mCurrentOffset = 0;
                    } else if (mCurrentOffset > mMaxOffset) {
                        onOverScroll();
                        mCurrentOffset = mMaxOffset;
                    }
                    mVelocityTracker.computeCurrentVelocity(1000, mMaxFlingVelocity);
                    mState = STATE_MOVING;
                    ViewCompat.postInvalidateOnAnimation(this);
                } else if (Math.abs(diffY) > mTouchSlop && mState < STATE_MOVING) {
                    mUiHandler.removeCallbacks(mLongPressDetector);
                    return false;
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mUiHandler.removeCallbacks(mLongPressDetector);
                // If a long press was detected then we end with the movement
                if (mLongPressDetector.mLongPressTriggered) {
                    return true;
                }

                if (mState >= STATE_MOVING) {
                    final int velocity = (int) mVelocityTracker.getXVelocity(pointerId);
                    mScroller.forceFinished(true);
                    mState = STATE_FLINGING;
                    releaseEdgeEffects();
                    mScroller.fling((int) mCurrentOffset, 0, velocity, 0, 0, (int) mMaxOffset, 0, 0);
                    ViewCompat.postInvalidateOnAnimation(this);
                } else {
                    // Reset scrolling state
                    mState = STATE_IDLE;

                    if (action == MotionEvent.ACTION_UP) {
                        // we are in a tap or long press action
                        final long timeDiff = (now - mLastPressTimestamp);
                        // If diff < 0, that means that time have change. ignore this event
                        if (timeDiff >= 0) {
                            if (timeDiff > TAP_TIMEOUT && timeDiff < mLongPressTimeout) {
                                // A tap event happens. Long click are detected outside
                                Message.obtain(mUiHandler, MSG_ON_CLICK_ITEM,
                                        computeItemEvent()).sendToTarget();
                            }
                        }
                    }
                }

                mLastPressTimestamp = -1;
                return true;
        }
        return false;
    }

    private void onOverScroll() {
        final boolean needOverScroll;
        synchronized (mLock) {
            needOverScroll = mData.size() >= Math.floor(mMaxBarItemsInScreen / 2);
        }
        final int overScrollMode = getOverScrollMode();
        if (overScrollMode == OVER_SCROLL_ALWAYS ||
                (overScrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && needOverScroll)) {
            boolean needsInvalidate = false;
            if (mCurrentOffset > mMaxOffset) {
                mEdgeEffectLeft.onPull(mCurrentOffset - mMaxOffset);
                needsInvalidate = true;
            }
            if (mCurrentOffset < 0) {
                mEdgeEffectRight.onPull(mCurrentOffset);
                needsInvalidate = true;
            }

            if (needsInvalidate) {
                ViewCompat.postInvalidateOnAnimation(this);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void computeScroll() {
        super.computeScroll();

        // Ignore any scroll while performing scrolling animation
        if (mState == STATE_ZOOMING) {
            return;
        }

        // Determine whether we still scrolling and needs a viewport refresh
        final boolean scrolling = mScroller.computeScrollOffset();
        if (scrolling) {
            float x = mScroller.getCurrX();
            if (x > mMaxOffset || x < 0) {
                return;
            }
            mCurrentOffset = x;
            ViewCompat.postInvalidateOnAnimation(this);
        } else if (mState > STATE_MOVING) {
            boolean needsInvalidate = false;
            final boolean needOverScroll;
            synchronized (mLock) {
                needOverScroll = mData.size() >= Math.floor(mMaxBarItemsInScreen / 2);
            }
            final int overScrollMode = getOverScrollMode();
            if (overScrollMode == OVER_SCROLL_ALWAYS || (needOverScroll &&
                    overScrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS)) {
                float x = mScroller.getCurrX();
                if (x >= mMaxOffset && mEdgeEffectLeft.isFinished() && !mEdgeEffectLeftActive) {
                    mEdgeEffectLeft.onAbsorb((int) mScroller.getCurrVelocity());
                    mEdgeEffectLeftActive = true;
                    needsInvalidate = true;
                }
                if (x <= 0 && mEdgeEffectRight.isFinished() && !mEdgeEffectRightActive) {
                    mEdgeEffectRight.onAbsorb((int) mScroller.getCurrVelocity());
                    mEdgeEffectRightActive = true;
                    needsInvalidate = true;
                }
            }
            if (!needsInvalidate) {
                // Reset state
                mState = STATE_IDLE;
                mLastTimestamp = -1;
            } else {
                ViewCompat.postInvalidateOnAnimation(this);
            }
        }


        long timestamp = computeTimestampFromOffset(mCurrentOffset);

        // If we are not centered in a item, perform an scroll
        if (mAlwaysEnsureSelection && mState == STATE_IDLE) {
            timestamp = computeNearestTimestampFromOffset(mCurrentOffset);
            smoothScrollTo(timestamp);
        }

        if (mCurrentTimestamp != timestamp) {
            // Don't perform selection operations while we are just scrolling
            if (mState != STATE_SCROLLING) {
                boolean fromUser = mCurrentTimestamp != -2;
                mCurrentTimestamp = timestamp;
                if (fromUser) {
                    performSelectionSoundEffect();
                }

                // Notify any valid item, but only notify invalid items if
                // we are not panning/scrolling
                if (mCurrentTimestamp >= 0 || !scrolling) {
                    Message.obtain(mUiHandler, MSG_ON_SELECTION_ITEM_CHANGED, fromUser)
                            .sendToTarget();
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setOverScrollMode(int overScrollMode) {
        if(overScrollMode != OVER_SCROLL_NEVER) {
            setupEdgeEffects();
        } else {
            mEdgeEffectLeft = null;
            mEdgeEffectRight = null;
        }
        super.setOverScrollMode(overScrollMode);
    }

    /**
     * Move the current viewport of this view to the timestamp passed as argument. If
     * timestamp doesn't exists no operation will be performed.
     */
    public void scrollTo(long timestamp) {
        // Ignore any scroll while performing scrolling animation
        if (mState == STATE_ZOOMING) {
            return;
        }

        final float offset = computeOffsetForTimestamp(timestamp);
        if (offset >= 0 && offset != mCurrentOffset) {
            mCurrentOffset = offset;
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    /**
     * Performs a smooth transition of the current viewport of this view to
     * the timestamp passed as argument. If timestamp doesn't exists no
     * operation will be performed.
     */
    public void smoothScrollTo(long timestamp) {
        // Ignore any scroll while performing scrolling animation
        if (mState == STATE_ZOOMING) {
            return;
        }

        final float offset = computeOffsetForTimestamp(timestamp);
        if (offset >= 0 && offset != mCurrentOffset) {
            int dx = (int) (mCurrentOffset - offset) * -1;
            mScroller.forceFinished(true);
            mState = STATE_SCROLLING;
            mLastTimestamp = mCurrentTimestamp;
            mScroller.startScroll((int) mCurrentOffset, 0, dx, 0);
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    private ItemEvent computeItemEvent() {
        // Check whether tap happens outside viewport area
        if (!mViewArea.contains(mLastX, mLastY)) {
            return null;
        }

        final LongSparseArray<Pair<double[], int[]>> data;
        final double maxValue;
        synchronized (mLock) {
            data = mData;
            maxValue = mMaxValue;
        }
        int size = data.size() -1;
        if (size <= 0) {
            return null;
        }

        // Check if we are in an space area and not in a bar area
        float offset = mInitialTouchOffset + ((mGraphArea.width() / 2)
                + mGraphArea.left - mInitialTouchX);
        double s = (offset + (mBarItemWidth / 2)) % mBarWidth;
        if (s > mBarItemWidth) {
            // We are in a space area
            return null;
        }

        // So we are in an bar area, so we have a valid index
        final int index = size - ((int) Math.ceil((offset - (mBarItemWidth / 2)) / mBarWidth));
        if (index < 0 || index >= data.size()) {
            return null;
        }
        final Pair<double[], int[]> o = data.valueAt(index);
        if (o == null) {
            return null;
        }

        final float halfItemBarWidth = (mBarItemWidth / 2);
        final ItemEvent itemEvent = new ItemEvent();
        itemEvent.mTimestamp = data.keyAt(index);
        if (mShowFooter && mFooterArea.contains(mLastX, mLastY)) {
            // Tap in the bar area means we cannot extract the serie
            itemEvent.mSerie = -1;
        } else {
            // Determine if the tap happens in a drawing area (and to what serie belongs)
            final double[] values = o.first;
            final int[] indexes = o.second;
            final float height = mGraphArea.height();
            float y1, y2 = mGraphArea.height();
            final float cx = mGraphArea.left + (mGraphArea.width() / 2);
            final float x = cx + (mCurrentOffset - computeOffsetForTimestamp(itemEvent.mTimestamp));

            itemEvent.mSerie = -1;
            if (mGraphMode != GRAPH_MODE_BARS_STACK) {
                int count = values.length;
                float x1 = x - halfItemBarWidth;
                float x2 = x + halfItemBarWidth;
                float bw = mBarItemWidth / mSeries;

                for (int j = 0; j < count; j++) {
                    y2 = height;
                    if (mGraphMode == GRAPH_MODE_BARS_SIDE_BY_SIDE) {
                        y1 = (float) (height - ((height * ((values[j] * 100) / maxValue)) / 100));
                        x1 = x - halfItemBarWidth + (bw * j);
                        x2 = x1 + bw;
                    } else {
                        y1 = (float) (height - ((height * ((values[j] * 100) / maxValue)) / 100));
                    }
                    mSerieRect.set(x1, y1, x2, y2);
                    if (mSerieRect.contains(mLastX, mLastY)) {
                        itemEvent.mSerie = indexes[j];
                        break;
                    }
                }
            } else {
                int count = values.length;
                for (int j = 0; j < count; j++) {
                    float h = (float) ((height * ((values[j] * 100) / maxValue)) / 100);
                    y1 = y2 - h;
                    mSerieRect.set(x - halfItemBarWidth, y1, x + halfItemBarWidth, y2);
                    if (mSerieRect.contains(mLastX, mLastY)) {
                        itemEvent.mSerie = j;
                        break;
                    }
                    y2 -= h;
                }
            }

            // If tap isn't in an area then there is not item event
            if (itemEvent.mSerie == -1) {
                return null;
            }
        }
        return itemEvent;
    }

    private long computeTimestampFromOffset(float offset) {
        final LongSparseArray<Pair<double[], int[]>> data;
        synchronized (mLock) {
            data = mData;
        }
        int size = data.size() -1;
        if (size < 0) {
            return -1;
        }

        // Check if we are in an space area and not in a bar area
        double s = (offset + (mBarItemWidth / 2)) % mBarWidth;
        if (s > mBarItemWidth) {
            // We are in a space area
            return -1;
        }

        // So we are in an bar area, so we have a valid index
        final int index = size - ((int) Math.ceil((offset - (mBarItemWidth / 2)) / mBarWidth));
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
            return (mBarWidth * (size - index - 1));
        }
        return -1;
    }

    private long computeNearestTimestampFromOffset(float offset) {
        final LongSparseArray<Pair<double[], int[]>> data;
        synchronized (mLock) {
            data = mData;
        }
        int size = data.size() -1;
        if (size < 0) {
            return -1;
        }

        // So we are in an bar area, so we have a valid index
        final int index = size - ((int) Math.ceil((offset - (mBarItemWidth / 2)) / mBarWidth));
        return data.keyAt(index);
    }

    /** {@inheritDoc} */
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
        if (hasData && mIsDataComputed) {
            // 3.- Compute viewport and draw the data
            computeItemsOnScreen(data);
            drawBarItems(c, data, maxValue);

            // 4.- Draw tick labels and current position
            if (mShowFooter) {
                drawTickLabels(c, data);
                c.drawPath(mCurrentPositionPath, mFooterAreaBgPaint);
            }
        }

        // Draw the edge scrolling effects
        drawEdgeEffects(c);
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
            final float x = cx + mCurrentOffset - (mBarWidth * (size - i));
            float bw = mBarItemWidth / mSeries;
            double[] values = data.valueAt(i).first;
            int[] indexes = data.valueAt(i).second;

            float y1, y2 = height;
            float x1 = x - halfItemBarWidth, x2 = x + halfItemBarWidth;
            if (mGraphMode != GRAPH_MODE_BARS_STACK) {
                int count = values.length - 1;
                for (int j = count, n = 0; j >= 0; j--, n++) {
                    y2 = height;
                    final Paint paint;
                    if (mGraphMode == GRAPH_MODE_BARS_SIDE_BY_SIDE) {
                        y1 = (float) (height - ((height * ((values[n] * 100) / maxValue)) / 100));
                        x1 = x - halfItemBarWidth + (bw * n);
                        x2 = x1 + bw;
                        paint = (x - halfItemBarWidth) < cx && (x + halfItemBarWidth) > cx &&
                                (mLastTimestamp == mCurrentTimestamp ||
                                        (mState != STATE_SCROLLING))
                                ? highlightSeriesBgPaint[indexes[n]] : seriesBgPaint[indexes[n]];
                    } else {
                        y1 = (float) (height - ((height * ((values[j] * 100) / maxValue)) / 100));
                        paint = x1 < cx && x2 > cx &&
                                (mLastTimestamp == mCurrentTimestamp ||
                                        (mState != STATE_SCROLLING))
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

                    final Paint paint = x1 < cx && x2 > cx &&
                            (mLastTimestamp == mCurrentTimestamp ||
                                    (mState != STATE_SCROLLING))
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
            final int tickFormat = getTickLabelFormat(timestamp);
            mTickDate.setTime(timestamp);
            final String text = mTickFormatter[tickFormat].format(mTickDate)
                    .replace(".", "")
                    .toUpperCase(Locale.getDefault());
            DynamicSpannableString spannable =
                    mTickTextSpannables[tickFormat].get(text.length());
            if (spannable == null) {
                // If we don't have an spannable for the text length, create a new one
                // that allow to use it now and in the future. Doing this here (on draw)
                // is not the best, but it supposed to only be performed one time per
                // different tick text length
                spannable = createSpannableTick(tickFormat, text);
                mTickTextSpannables[tickFormat].put(text.length(), spannable);
            }
            spannable.update(text);

            DynamicLayout layout = mTickTextLayouts[tickFormat].get(text.length());
            if (layout == null) {
                // Update the layout as well
                layout = new DynamicLayout(spannable, mTickLabelFgPaint,
                        (int) mBarItemWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 1.0f, false);
                mTickTextLayouts[tickFormat].put(text.length(), layout);
            }

            // Calculate the x position and draw the layout
            final float x = cx + mCurrentOffset - (mBarWidth * (size - i))
                    - (layout.getWidth() / 2);
            final int restoreCount = c.save();
            c.translate(x, mFooterArea.top
                    + (mFooterArea.height() / 2 - mTickLabelMinHeight / 2));
            layout.draw(c);
            c.restoreToCount(restoreCount);
        }
    }

    private void drawEdgeEffects(Canvas c) {
        boolean needsInvalidate = false;

        if (mEdgeEffectLeft != null && !mEdgeEffectLeft.isFinished()) {
            final int restoreCount = c.save();
            c.rotate(270);
            c.translate(-mGraphArea.height() - mGraphArea.top, mGraphArea.left);
            mEdgeEffectLeft.setSize((int) mGraphArea.height(), (int) mGraphArea.width());
            needsInvalidate = mEdgeEffectLeft.draw(c);
            c.restoreToCount(restoreCount);
        }

        if (mEdgeEffectRight != null && !mEdgeEffectRight.isFinished()) {
            final int restoreCount = c.save();
            c.rotate(90);
            c.translate(mGraphArea.top,
                    -getWidth() + (getWidth() - mGraphArea.right));
            mEdgeEffectRight.setSize((int) mGraphArea.height(), (int) mGraphArea.width());
            needsInvalidate |= mEdgeEffectRight.draw(c);
            c.restoreToCount(restoreCount);
        }

        if (needsInvalidate) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    /** {@inheritDoc} */
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
        int last = size - (int) Math.floor(offset / mBarWidth)
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
        mMaxBarItemsInScreen = (int) Math.ceil(mGraphArea.width() / mBarWidth) + 2;
    }

    private void computeCurrentPositionIndicatorDimensions() {
        mCurrentPositionIndicatorWidth = mBarItemWidth / 2.8f;
        mCurrentPositionIndicatorHeight = mBarItemWidth / 4f;
    }

    private synchronized void setupBackgroundHandler() {
        if (mBackgroundHandler == null) {
            // Create a background thread
            mBackgroundHandlerThread = new HandlerThread(TAG + "BackgroundThread");
            mBackgroundHandlerThread.start();
            mBackgroundHandler = new Handler(mBackgroundHandlerThread.getLooper(), mMessenger);
        }
    }

    @SuppressWarnings("unchecked")
    private void setupTickLabels() {
        synchronized (mLock) {
            mTickCalendar = Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault());

            mTextSizeFactor = mFooterBarHeight / mDefFooterBarHeight;
            mTickLabelFgPaint.setTextSize((int) (mSize8 * mTextSizeFactor));

            mTickDate = new Date();

            int count = mTickFormats.length;
            mTickTextLayouts = new SparseArray[count];
            mTickFormatter = new SimpleDateFormat[count];
            mTickTextSpannables = new SparseArray[count];
            for (int i = 0; i < count; i++) {
                mTickFormatter[i] = new SimpleDateFormat(mTickFormats[i], Locale.getDefault());
                mTickDate.setTime(Long.valueOf(mTickLabels[i]));
                final String text = mTickFormatter[i].format(mTickDate)
                        .replace(".", "")
                        .toUpperCase(Locale.getDefault());
                mTickTextSpannables[i] = new SparseArray<>();
                mTickTextLayouts[i] = new SparseArray<>();

                // Store spannable in memory based in its length, so we don't have to rebuild
                // a every time, just only in case they are needed (normally never)
                DynamicSpannableString spannable = createSpannableTick(i, text);
                mTickTextLayouts[i].put(text.length(),
                        new DynamicLayout(spannable, mTickLabelFgPaint,
                            (int) mBarItemWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 1.0f, false));

                // Save min height
                mTickLabelMinHeight = Math.max(
                        mTickLabelMinHeight, mTickTextLayouts[i].get(text.length()).getHeight());
            }
        }
    }

    private DynamicSpannableString createSpannableTick(int tickFormat, CharSequence text) {
        DynamicSpannableString spannable = new DynamicSpannableString(text);
        mTickTextSpannables[tickFormat].put(text.length(), spannable);
        if (tickFormat == (mTickFormats.length - 1)) {
            spannable.setSpan(new AbsoluteSizeSpan(
                            (int) (mSize20 * mTextSizeFactor)), 0, 2,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (tickFormat == 1) {
            spannable.setSpan(new AbsoluteSizeSpan(
                            (int) (mSize12 * mTextSizeFactor)), 0, text.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return spannable;
    }

    private void setupEdgeEffects() {
        if (mEdgeEffectLeft == null) {
            mEdgeEffectLeft = new EdgeEffect(getContext());
        }
        if (mEdgeEffectRight == null) {
            mEdgeEffectRight = new EdgeEffect(getContext());
        }
        setupEdgeEffectColor();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setupEdgeEffectColor() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            if (mGraphAreaBgPaint != null && mEdgeEffectLeft != null && mEdgeEffectRight != null) {
                int color = MaterialPaletteHelper.isDarkColor(
                        mGraphAreaBgPaint.getColor()) ? Color.WHITE : Color.BLACK;
                mEdgeEffectLeft.setColor(color);
                mEdgeEffectRight.setColor(color);
            }
        }
    }

    private void setupSoundEffects() {
        if (mPlaySelectionSoundEffect && mSelectionSoundEffectSource != SYSTEM_SOUND_EFFECT) {
            if (mSoundEffectMP == null) {
                mSoundEffectMP = MediaPlayer.create(getContext(), mSelectionSoundEffectSource);
                mSoundEffectMP.setVolume(SOUND_EFFECT_VOLUME, SOUND_EFFECT_VOLUME);
            }
        } else if (mSoundEffectMP != null) {
            releaseSoundEffects();
        }
    }

    private void releaseSoundEffects() {
        if (mSoundEffectMP == null)
            return;
        if (mSoundEffectMP.isPlaying()) {
            mSoundEffectMP.stop();
        }
        mSoundEffectMP.release();
        mSoundEffectMP = null;
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
                    // Swap temporary refs
                    swapRefs();

                    // Update the view, notify and end the animation
                    Message.obtain(mUiHandler, MSG_UPDATE_COMPUTED_DATA, 1, 0).sendToTarget();
                } else {
                    mState = STATE_IDLE;
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                mState = STATE_IDLE;
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        });
    }

    private void reloadCursorData(boolean animate) {
        int arg1 = mAnimateCursorTransition && animate ? 1 : 0;
        Message.obtain(mBackgroundHandler, MSG_COMPUTE_DATA, arg1, 1).sendToTarget();
    }

    private void performComputeData(boolean animate, boolean notify) {
        // Process the data
        processData();

        if (animate) {
            // Run in an animation
            if (mZoomAnimator.isRunning()) {
                mZoomAnimator.cancel();
            }
            mInZoomOut = true;
            mZoomAnimator.setFloatValues(MIN_ZOOM_OUT, MAX_ZOOM_OUT);
            mScroller.forceFinished(true);
            mState = STATE_ZOOMING;
            mZoomAnimator.start();

        } else if (notify) {
            // Swap temporary refs
            mScroller.forceFinished(true);
            mState = STATE_IDLE;
            swapRefs();

            // Update the view and notify
            Message.obtain(mUiHandler, MSG_UPDATE_COMPUTED_DATA, 0, 0).sendToTarget();
        }

        // Update the graph view
        ViewCompat.postInvalidateOnAnimation(TimelineChartView.this);
    }

    private void processData() {
        // This optimizations can by applied to data in this method according to the
        // defined current optimization flag:
        //
        //  NO_OPTIMIZATION: All the data is compute again
        //  NO_DELETES_OPTIMIZATION: Internal can be preserve and only updates
        //    and additions will happen
        //  ONLY_ADDITIONS_OPTIMIZATION: Internal is preserve, and only additions are accounted
        synchronized (mCursorLock) {
            if (mCursor != null && !mCursor.isClosed() && mCursor.moveToFirst()) {
                // Load the cursor to memory
                boolean hasDayFormat = false;
                double max = 0d;
                final LongSparseArray<Pair<double[], int[]>> data;
                // Clone the data if we optimization flag allow it.
                if (mOptimizationFlag != NO_OPTIMIZATION) {
                    data = cloneCurrentData(mCursor.getCount());
                } else {
                    data = new LongSparseArray<>(mCursor.getCount());
                }

                int series = mCursor.getColumnCount() - 1;
                if (mItem.mSeries == null || mItem.mSeries.length != series) {
                    mItem.mSeries = new double[series];
                }

                long lastTimestamp = -1;
                if (mOptimizationFlag == ONLY_ADDITIONS_OPTIMIZATION) {
                    hasDayFormat = mTickHasDayFormat;
                    max = mMaxValue;
                    mCursor.moveToLast();
                    if (data.size() > 0) {
                        lastTimestamp = data.keyAt(data.size() - 1);
                    }
                }

                // Extract the data from the cursor applying the current optimization flag.
                int lastTickLabelFormat = -1;
                do {
                    long timestamp = mCursor.getLong(0);
                    if (timestamp == lastTimestamp
                            && mOptimizationFlag == ONLY_ADDITIONS_OPTIMIZATION) {
                        break;
                    }

                    // Determine the best tick vertical alignment
                    final int tickLabelFormat = getTickLabelFormat(timestamp);
                    if (tickLabelFormat == TICK_LABEL_DAY_FORMAT ||
                            (lastTickLabelFormat != -1 && lastTickLabelFormat != tickLabelFormat)) {
                        hasDayFormat = true;
                    }
                    lastTickLabelFormat = tickLabelFormat;

                    final double[] seriesData;
                    final int[] indexes;
                    if (mOptimizationFlag == NO_OPTIMIZATION) {
                        seriesData = new double[series];
                        indexes = new int[series];
                    } else {
                        Pair<double[], int[]> v = data.get(timestamp);
                        if (v != null) {
                            seriesData = v.first;
                            indexes = v.second;
                        } else {
                            seriesData = new double[series];
                            indexes = new int[series];
                        }
                    }
                    double stackVal = 0d;
                    for (int i = 0; i < series; i++) {
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
                } while (mOptimizationFlag == ONLY_ADDITIONS_OPTIMIZATION
                        ? mCursor.moveToPrevious() : mCursor.moveToNext());

                // Calculate the max available offset
                int size = data.size() - 1;
                float maxOffset = mBarWidth * size;

                //swap data
                synchronized (mLock) {
                    mSeriesSwap = series;
                    mDataSwap = data;
                    mMaxValueSwap = max;
                    mMaxOffsetSwap = maxOffset;
                    mTickHasDayFormatSwap = hasDayFormat;
                }
            } else {
                // Cursor is empty or closed
                clearSwapRefs();
            }
        }
    }

    private LongSparseArray<Pair<double[], int[]>> cloneCurrentData(int capacity) {
        final LongSparseArray<Pair<double[], int[]>> prevData;
        synchronized (mLock) {
            prevData = mData;
        }
        if (prevData != null) {
            final int size = prevData.size();
            final LongSparseArray<Pair<double[], int[]>> data
                    = new LongSparseArray<>(Math.max(capacity, size));
            for (int i = 0; i < size; i++) {
                data.append(prevData.keyAt(i), prevData.valueAt(i));
            }
            return data;
        }
        return new LongSparseArray<>();
    }

    private void checkCursorIntegrity(Cursor c) {
        if (c.getCount() == 0) {
            return;
        }
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

    private void swapRefs() {
        synchronized (mLock) {
            mSeries = mSeriesSwap;
            mData = mDataSwap;
            mMaxValue = mMaxValueSwap;
            mLastOffset = -1.f;
            mMaxOffset = mMaxOffsetSwap;

            // Compute current offset and timestamp
            final int index = mData.indexOfKey(mCurrentTimestamp);
            final boolean lastItem = mCurrentOffset == 0.f;
            final boolean haveTimestamp = index >= 0;
            if (haveTimestamp && (!lastItem || !mFollowCursorPosition)) {
                mCurrentOffset = computeOffsetForTimestamp(mCurrentTimestamp);
            } else {
                mCurrentOffset = 0;
                mCurrentTimestamp = -2;
            }

            // Setup tick labels if we detected changes
            if (mTickHasDayFormat != mTickHasDayFormatSwap) {
                mTickHasDayFormat = mTickHasDayFormatSwap;
                setupTickLabels();
            }
        }
    }

    private void clearSwapRefs() {
        mDataSwap.clear();
        mMaxValueSwap = 0d;
        mTickHasDayFormatSwap = false;
    }

    private void clear() {
        synchronized (mLock) {
            mData.clear();
            mMaxValue = 0d;
            mCurrentTimestamp = -1;
        }
    }

    private void notifyGenericClickEvent(ItemEvent itemEvent) {
        if (mOnClickItemCallback != null && itemEvent != null && itemEvent.mTimestamp > 0) {
            final Item item = obtainItem(itemEvent.mTimestamp);
            if (item != null) {
                mOnClickItemCallback.onClickItem(item, itemEvent.mSerie);
            }
        } else if (isClickable()) {
            // Click on a empty area or click item not registered and view request
            // click actions
            performClick();
        }
    }

    private void notifyGenericLongClickEvent(ItemEvent itemEvent) {
        if (mOnLongClickItemCallback != null && itemEvent != null && itemEvent.mTimestamp > 0) {
            final Item item = obtainItem(itemEvent.mTimestamp);
            if (item != null) {
                mOnLongClickItemCallback.onLongClickItem(item, itemEvent.mSerie);
            }
        } else if (isLongClickable()) {
            // Long click on a empty area or long click item not registered and view request
            // long click actions
            performLongClick();
        }
    }

    private void notifyOnSelectionItemChanged(boolean fromUser) {
        if (mOnSelectedItemChangedCallbacks.size() == 0) {
            return;
        }

        final Item item = obtainItem(mCurrentTimestamp);
        if (item == null) {
            for (OnSelectedItemChangedListener cb : mOnSelectedItemChangedCallbacks) {
                cb.onNothingSelected();
            }
        } else {
            for (OnSelectedItemChangedListener cb : mOnSelectedItemChangedCallbacks) {
                cb.onSelectedItemChanged(item, fromUser);
            }
        }
    }

    private void notifyOnColorPaletteChanged() {
        for (OnColorPaletteChangedListener cb : mOnColorPaletteChangedCallbacks) {
            cb.onColorPaletteChanged(mCurrentPalette);
        }
    }

    private Item obtainItem(long timestamp) {
        final Pair<double[], int[]> data;
        final int count;
        synchronized (mLock) {
            data = mData.get(timestamp);
            count = mSeries;
        }
        if (data == null) {
            return null;
        }

        // Compute item. Restore original sort before notify
        mItem.mTimestamp = timestamp;
        for (int i = 0; i < count; i++) {
            mItem.mSeries[i] = data.first[data.second[i]];
        }
        return mItem;
    }

    private void ensureBarWidth() {
        if (!mShowFooter) {
            return;
        }
        if (mTickTextLayouts != null) {
            float minWidth = 0.f;
            for (SparseArray<DynamicLayout> a : mTickTextLayouts) {
                int count = a.size();
                for (int i = 0; i < count; i++) {
                    DynamicLayout layout = a.valueAt(i);
                    final float width = layout.getWidth();
                    if (minWidth < width) {
                        minWidth = width;
                    }
                }
            }
            if (minWidth > mBarItemWidth) {
                Log.w(TAG, "There is not enough space for labels. Switch BarItemWidth to " + minWidth);
                mBarItemWidth = minWidth;
            }
        }
        mBarWidth = mBarItemWidth + mBarItemSpace;
    }

    private int getTickLabelFormat(long timestamp) {
        mTickCalendar.setTimeInMillis(timestamp);
        final int hour = mTickCalendar.get(Calendar.HOUR_OF_DAY);
        final int minute = mTickCalendar.get(Calendar.MINUTE);
        final int second = mTickCalendar.get(Calendar.SECOND);
        final int millisecond = mTickCalendar.get(Calendar.MILLISECOND);
        if (hour == 0 && minute == 0 && second == 0 && millisecond == 0) {
            return TICK_LABEL_DAY_FORMAT;
        }
        if (second == 0 && millisecond == 0) {
            return TICK_LABEL_HOUR_MINUTES_FORMAT;
        }
        return TICK_LABEL_SECONDS_FORMAT;
    }

    private void performSelectionSoundEffect() {
        if (!isInEditMode()) {
            if (mPlaySelectionSoundEffect) {
                if (mSelectionSoundEffectSource == SYSTEM_SOUND_EFFECT) {
                    mAudioManager.playSoundEffect(SoundEffectConstants.CLICK, SOUND_EFFECT_VOLUME);
                } else {
                    mSoundEffectMP.start();
                }
            }
        }
    }

    private void releaseEdgeEffects() {
        mEdgeEffectLeftActive = mEdgeEffectRightActive = false;
        if (mEdgeEffectLeft != null) {
            mEdgeEffectLeft.onRelease();
        }
        if (mEdgeEffectRight != null) {
            mEdgeEffectRight.onRelease();
        }
    }

    private void releaseCursor() {
        synchronized (mCursorLock) {
            if (mCursor != null) {
                mCursor.unregisterDataSetObserver(mDataSetObserver);
                if (mContentObserver != null) {
                    mCursor.unregisterContentObserver(mContentObserver);
                }
                if (!mCursor.isClosed()) {
                    mCursor.close();
                }
                mCursor = null;
                mSeries = 0;
                mItem.mSeries = new double[mSeries];
            }
        }
    }

    private void setupViewInEditMode() {
        final int[] INDEXES = new int[]{0, 1};
        mData = new LongSparseArray<>();
        mData.put(1452639600000L, new Pair<>(new double[]{1867263,2262779}, INDEXES));
        mData.put(1452726000000L, new Pair<>(new double[]{578273,2871800}, INDEXES));
        mData.put(1452812400000L, new Pair<>(new double[]{2709,2960491}, INDEXES));
        mData.put(1452898800000L, new Pair<>(new double[]{1322623,6864896}, INDEXES));
        mData.put(1452985200000L, new Pair<>(new double[]{1272367,4282328}, INDEXES));
        mData.put(1453071600000L, new Pair<>(new double[]{115774,7706941}, INDEXES));
        mData.put(1453158000000L, new Pair<>(new double[]{1920784,3800944}, INDEXES));
        mData.put(1453244400000L, new Pair<>(new double[]{534265,5978142}, INDEXES));
        mData.put(1453330800000L, new Pair<>(new double[]{117245,7801457}, INDEXES));
        mData.put(1453417200000L, new Pair<>(new double[]{430320,5054115}, INDEXES));
        mData.put(1453503600000L, new Pair<>(new double[]{2461596,8174509}, INDEXES));
        mData.put(1453590000000L, new Pair<>(new double[]{702240,503133}, INDEXES));
        mData.put(1453676400000L, new Pair<>(new double[]{1364885,4013798}, INDEXES));
        mData.put(1453762800000L, new Pair<>(new double[]{1310028,877585}, INDEXES));
        mData.put(1453849200000L, new Pair<>(new double[]{801779,8092978}, INDEXES));
        mData.put(1453935600000L, new Pair<>(new double[]{1089847,3678389}, INDEXES));
        mSeries = 2;
        mMaxValue = 8174509;
        //setupSeriesBackground(mGraphAreaBgPaint.getColor());
        mIsDataComputed = true;
        mState = STATE_IDLE;
        mLastTimestamp = -1;
        mCurrentTimestamp = 1453935600000L;

        int[] palette1 = MaterialPaletteHelper.createMaterialSpectrumPalette(
                mGraphAreaBgPaint.getColor(), 2);
        int[] palette2 = MaterialPaletteHelper.createMaterialSpectrumPalette(
                MaterialPaletteHelper.getComplementaryColor(mGraphAreaBgPaint.getColor()), 2);

        mSeriesBgPaint = new Paint[2];
        mSeriesBgPaint[0] = new Paint();
        mSeriesBgPaint[0].setColor(palette1[0]);
        mSeriesBgPaint[1] = new Paint();
        mSeriesBgPaint[1].setColor(palette1[1]);

        mHighlightSeriesBgPaint = new Paint[2];
        mHighlightSeriesBgPaint[0] = new Paint();
        mHighlightSeriesBgPaint[0].setColor(palette2[0]);
        mHighlightSeriesBgPaint[1] = new Paint();
        mHighlightSeriesBgPaint[1].setColor(palette2[1]);

    }
}
