/*
 * Copyright (C) 2014 The Android Open Source Project
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

package net.catsonmars.android.sunshinewatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
    private static final Typeface BASE_TYPEFACE = Typeface.SANS_SERIF;
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(BASE_TYPEFACE, Typeface.NORMAL);
    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(BASE_TYPEFACE, Typeface.BOLD);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    // see http://www.slideshare.net/rtc1/intro-todrawingtextandroid for the explanation of this awesome code snippet
    public enum TextVertAlign { Top, Middle, Baseline, Bottom } // Enumeration representing vertical alignment positions
    public static void drawHvAlignedText(Canvas canvas, float x, float y, String s, Paint p, Paint.Align horizAlign, TextVertAlign vertAlign ) {
        // Set horizontal alignment
        p.setTextAlign(horizAlign);

        // Get bounding rectangle which we’ll need below...
        Rect r = new Rect();
        p.getTextBounds(s, 0, s.length(), r);

        // Note: r.top will be negative
        // Compute y-coordinate we'll need for drawing text for specified vertical alignment
        float textX = x;
        float textY = y;
        switch (vertAlign) {
            case Top:
                textY = y - r.top; // Recall that r.top is negative
                break;
            case Middle:
                textY = y - r.top - r.height() / 2;
                break;
            case Baseline: // Default behavior - no changes to y-coordinate
                break;
            case Bottom:
                textY = y - (r.height() + r.top);
                break;
        }

        canvas.drawText(s, textX, textY, p);
        // Now we can draw the text with the proper ( x, y ) coordinates
    }

    private class Engine extends CanvasWatchFaceService.Engine {

        final Handler mUpdateTimeHandler = new EngineHandler(this);

        final float mLeading = (float)1.8;

        boolean mRegisteredTimeZoneReceiver = false;

        String mTimeSeparator;

        String mTemperatureFormat;
        String mDateFormat;
        String mHourFormat;

        Paint mHourPaint;
        Paint mMinutePaint;
        Paint mDatePaint;
        Paint mDateAmbientPaint;
        Paint mDividerPaint;
//        Paint mDividerAmbientPaint;
        Paint mBackgroundPaint;
        Paint mBackgroundAmbientPaint;
        Paint mHighTempPaint;
        Paint mLowTempPaint;
        Paint mTextPaint;
        Paint mTextBoldPaint;

        float mXOffset;
        float mYOffset;
        float mLineSpace;
        float mCharSpace;

        float mHighTemperature = 105;
        float mLowTemperature = 16;

//        float mXOffsetHours;
//        float mYOffsetHours;
//        float mXOffsetMinutes;
//        float mYOffsetMinutes;

        boolean mAmbient;
        boolean mLowBitAmbient;

        Time mTime;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = SunshineWatchFace.this.getResources();

            // initialize hour paint
            mHourPaint = createTextPaint(ContextCompat.getColor(getBaseContext(), R.color.text), resources.getDimension(R.dimen.text_size_time), BOLD_TYPEFACE);

            // initialize minute paint
            mMinutePaint = createTextPaint(ContextCompat.getColor(getBaseContext(), R.color.text), resources.getDimension(R.dimen.text_size_time), NORMAL_TYPEFACE);

            // initialize time separator
            mTimeSeparator = resources.getString(R.string.time_separator);

            // initialize date paints
            mDatePaint = createTextPaint(ContextCompat.getColor(getBaseContext(), R.color.text), resources.getDimension(R.dimen.text_size_date), NORMAL_TYPEFACE);
            mDateAmbientPaint = createTextPaint(ContextCompat.getColor(getBaseContext(), R.color.text_semitransparent), resources.getDimension(R.dimen.text_size_date), NORMAL_TYPEFACE);

            // initialize horizontal divider paints
            mDividerPaint = new Paint();
            mDividerPaint.setColor(ContextCompat.getColor(getBaseContext(), R.color.text_semitransparent));

            // initialize background paints
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(ContextCompat.getColor(getBaseContext(), R.color.background));

            mBackgroundAmbientPaint = new Paint();
            mBackgroundAmbientPaint.setColor(ContextCompat.getColor(getBaseContext(), R.color.background_ambient));

            // initialize temperature paints
            mHighTempPaint = createTextPaint(ContextCompat.getColor(getBaseContext(), R.color.text), resources.getDimension(R.dimen.text_size_temperature), NORMAL_TYPEFACE);
            mLowTempPaint = createTextPaint(ContextCompat.getColor(getBaseContext(), R.color.text_semitransparent), resources.getDimension(R.dimen.text_size_temperature), NORMAL_TYPEFACE);

            // initialize format strings
            mTemperatureFormat = resources.getString(R.string.format_temperature);
            mDateFormat = resources.getString(R.string.format_watchface_date);
            mHourFormat = resources.getString(R.string.format_hours);

            mTextPaint = createTextPaint(ContextCompat.getColor(getBaseContext(), R.color.digital_text), NORMAL_TYPEFACE);
            mTextBoldPaint = createTextPaint(ContextCompat.getColor(getBaseContext(), R.color.digital_text), BOLD_TYPEFACE);

            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mLineSpace = resources.getDimension(R.dimen.line_space);
            mCharSpace = resources.getDimension(R.dimen.char_space);

            mTime = new Time();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor, Typeface typeface) {
            Paint paint = new Paint();

            paint.setColor(textColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);

            return paint;
        }

        private Paint createTextPaint(int textColor, float textSize, Typeface typeface) {
            Paint paint = new Paint();

            paint.setColor(textColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            paint.setTextSize(textSize);

            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

//        @Override
//        public void onApplyWindowInsets(WindowInsets insets) {
//            super.onApplyWindowInsets(insets);
//
//            // Load resources that have alternate values for round watches.
//            Resources resources = SunshineWatchFace.this.getResources();
//            boolean isRound = insets.isRound();
//            mXOffset = resources.getDimension(isRound
//                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
//            float textSize = resources.getDimension(isRound
//                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
//
//            mTextPaint.setTextSize(textSize);
//        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
//                    mTextPaint.setAntiAlias(!inAmbientMode);
                    mHourPaint.setAntiAlias(!inAmbientMode);
                    mMinutePaint.setAntiAlias(!inAmbientMode);
                    mDateAmbientPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            Paint backgroundPaint = mAmbient ? mBackgroundAmbientPaint : mBackgroundPaint;
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), backgroundPaint);

            mTime.setToNow();

            // draw date
            String dateText = mTime.format(mDateFormat).toUpperCase();
            Paint datePaint = mAmbient ? mDateAmbientPaint : mDatePaint;
            Rect dateBounds = new Rect();
            datePaint.getTextBounds(dateText, 0, dateText.length(), dateBounds);
            drawHvAlignedText(
                    canvas,
                    (bounds.width() - dateBounds.width()) / 2,
                    bounds.height() / 2 - mLeading * dateBounds.height(),
                    dateText,
                    datePaint,
                    Paint.Align.LEFT,
                    TextVertAlign.Top);

            // initialize separator stuff
            Paint separatorPaint = mMinutePaint;
            Rect separatorBounds = new Rect();
            separatorPaint.getTextBounds(mTimeSeparator, 0, mTimeSeparator.length(), separatorBounds);

            // draw minutes
            String minuteText = mTime.format("%M");
            Paint minutePaint = mMinutePaint;
            Rect minuteBounds = new Rect();
            minutePaint.getTextBounds(minuteText, 0, minuteText.length(), minuteBounds);
            drawHvAlignedText(
                    canvas,
                    (bounds.width() + separatorBounds.width()) / 2,
                    bounds.height() / 2 - dateBounds.height() - mLeading * minuteBounds.height(),
                    minuteText,
                    minutePaint,
                    Paint.Align.LEFT,
                    TextVertAlign.Top);

            // draw hours
            String hourText = mTime.format(mHourFormat);
            Paint hourPaint = mHourPaint;
            Rect hourBounds = new Rect();
            hourPaint.getTextBounds(hourText, 0, hourText.length(), hourBounds);
            drawHvAlignedText(
                    canvas,
                    (bounds.width() - separatorBounds.width()) / 2 - hourBounds.width(),
                    bounds.height() / 2 - dateBounds.height() - mLeading * minuteBounds.height(),
                    hourText,
                    hourPaint,
                    Paint.Align.LEFT,
                    TextVertAlign.Top);

            // draw time separator
            boolean drawSeparator = mAmbient || (mTime.second % 2) == 0;
            if (drawSeparator) {
                drawHvAlignedText(
                        canvas,
                        (bounds.width() - separatorBounds.width()) / 2,
                        bounds.height() / 2 - dateBounds.height() - mLeading * minuteBounds.height() + minuteBounds.height() / 2,
                        // for some reason I cannot keep leading and trailing spaces added to the separator string in the strings.xml file;
                        // so I'm adding a magic character to replace it with a space character right before displaying the separator
                        mTimeSeparator.replace('|', ' '),
                        separatorPaint,
                        Paint.Align.LEFT,
                        TextVertAlign.Middle);
            }

            // draw a horizontal divider
            canvas.drawRect(
                    (bounds.width() * 3) / 8,
                    bounds.height() / 2 - 1,
                    (bounds.width() * 5) / 8,
                    bounds.height() / 2 + 1,
                    mDividerPaint);

            // For temperatures and weather icons I'm dividing the screen into 5 equal columns,
            // and display the weather icon in the 2nd column.
            // The high temperature goes into the 3rd column, and the low temperature goes into the 4th column.
            // Everything is centered.

            // draw an icon

            // draw a high temperature
            String highTempText = String.format(mTemperatureFormat, mHighTemperature);
            Paint highTempPaint = mHighTempPaint;
            Rect highTempBounds = new Rect();
            highTempPaint.getTextBounds(highTempText, 0, highTempText.length(), highTempBounds);
            drawHvAlignedText(
                    canvas,
                    // The high temperature goes into the 3rd column.
                    // Everything is centered.
                    (bounds.width() * 2 / 5 + (bounds.width() / 5 - highTempBounds.width()) / 2),
                    bounds.height() / 2 + mLeading * dateBounds.height() - dateBounds.height(),
                    highTempText,
                    highTempPaint,
                    Paint.Align.LEFT,
                    TextVertAlign.Top);

            // draw a low temperature
            String lowTempText = String.format(mTemperatureFormat, mLowTemperature);
            Paint lowTempPaint = mLowTempPaint;
            Rect lowTempBounds = new Rect();
            lowTempPaint.getTextBounds(lowTempText, 0, lowTempText.length(), lowTempBounds);
            drawHvAlignedText(
                    canvas,
                    // The low temperature goes into the 4th column.
                    // Everything is centered.
                    (bounds.width() * 3 / 5 + (bounds.width() / 5 - lowTempBounds.width()) / 2),
                    bounds.height() / 2 + mLeading * dateBounds.height() - dateBounds.height(),
                    lowTempText,
                    lowTempPaint,
                    Paint.Align.LEFT,
                    TextVertAlign.Top);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
