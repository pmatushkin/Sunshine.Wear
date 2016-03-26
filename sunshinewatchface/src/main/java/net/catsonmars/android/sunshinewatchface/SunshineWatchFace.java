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

    private class Engine extends CanvasWatchFaceService.Engine {

        final Handler mUpdateTimeHandler = new EngineHandler(this);

        boolean mRegisteredTimeZoneReceiver = false;

        String mTimeSeparator;

        String mTemperatureFormat;

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

        float mHighTemperature = 25;
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

//            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
//            mTime.setToNow();
//            String text = mAmbient
//                    ? String.format("%d:%02d", mTime.hour, mTime.minute)
//                    : String.format("%d:%02d:%02d", mTime.hour, mTime.minute, mTime.second);
//            canvas.drawText(text, mXOffset, mYOffset, mTextPaint);

            // draw date
            String dateText = mTime.format("%a, %b %d %Y").toUpperCase();
            Paint datePaint = mAmbient ? mDateAmbientPaint : mDatePaint;
            Rect dateBounds = new Rect();
            datePaint.getTextBounds(dateText, 0, dateText.length(), dateBounds);
            canvas.drawText(
                    dateText,
                    (bounds.width() - dateBounds.width()) / 2,
                    bounds.height() / 2 - 1 - mLineSpace - dateBounds.height(),
                    datePaint);

            // initialize separator stuff
            Paint separatorPaint = mMinutePaint;
            Rect separatorBounds = new Rect();
            separatorPaint.getTextBounds(mTimeSeparator, 0, mTimeSeparator.length(), separatorBounds);

            // draw minutes
            String minuteText = mTime.format("%M");
            Paint minutePaint = mMinutePaint;
            Rect minuteBounds = new Rect();
            minutePaint.getTextBounds(minuteText, 0, minuteText.length(), minuteBounds);
            canvas.drawText(
                    minuteText,
                    (bounds.width() + separatorBounds.width()) / 2,
                    bounds.height() / 2 - 1 - mLineSpace - dateBounds.height() - minuteBounds.height(),
                    minutePaint);

            // draw hours
            String hourText = mTime.format("%H");
            Paint hourPaint = mHourPaint;
            Rect hourBounds = new Rect();
            hourPaint.getTextBounds(hourText, 0, hourText.length(), hourBounds);
            canvas.drawText(
                    hourText,
                    (bounds.width() - separatorBounds.width()) / 2 - hourBounds.width(),
                    bounds.height() / 2 - 1 - mLineSpace - dateBounds.height() - minuteBounds.height(),
                    hourPaint);

            // draw separator
            boolean drawSeparator = mAmbient || (mTime.second % 2) == 0;
            if (drawSeparator) {
                canvas.drawText(
                        mTimeSeparator.replace('|', ' '),
                        (bounds.width() - separatorBounds.width()) / 2,
                        bounds.height() / 2 - 1 - mLineSpace - dateBounds.height() - separatorBounds.height(),
                        separatorPaint);
            }

            // draw a horizontal divider
            canvas.drawRect((bounds.width() * 3) / 8, bounds.height() / 2 - 1, (bounds.width() * 5) / 8, bounds.height() / 2 + 1, mDividerPaint);

            // draw a horizontal divider
            canvas.drawRect(
                    (bounds.width() * 3) / 8,
                    bounds.height() / 2 - 1,
                    (bounds.width() * 5) / 8,
                    bounds.height() / 2 + 1,
                    mDividerPaint);

            // draw an icon

            // draw a high temperature
            String highTempText = String.format(mTemperatureFormat, mHighTemperature);
            Paint highTempPaint = mHighTempPaint;
            Rect highTempBounds = new Rect();
            highTempPaint.getTextBounds(highTempText, 0, highTempText.length(), highTempBounds);
            canvas.drawText(
                    highTempText,
                    (bounds.width() * 2 / 5 + (bounds.width() / 5 - highTempBounds.width()) / 2),
                    bounds.height() / 2 + 1 + mLineSpace,
                    highTempPaint);

            // draw a low temperature
            String lowTempText = String.format(mTemperatureFormat, mLowTemperature);
            Paint lowTempPaint = mLowTempPaint;
            Rect lowTempBounds = new Rect();
            lowTempPaint.getTextBounds(lowTempText, 0, lowTempText.length(), lowTempBounds);
            canvas.drawText(
                    lowTempText,
                    (bounds.width() * 3 / 5 + (bounds.width() / 5 - lowTempBounds.width()) / 2),
                    bounds.height() / 2 + 1 + mLineSpace,
                    lowTempPaint);
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
