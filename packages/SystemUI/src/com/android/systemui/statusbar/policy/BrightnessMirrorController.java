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
 * limitations under the License
 */

package com.android.systemui.statusbar.policy;

import android.view.LayoutInflater;

import android.content.ContentResolver;
import android.content.Context;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.widget.ImageView;
import android.widget.FrameLayout;

import com.android.systemui.Interpolators;
import com.android.systemui.R;
import com.android.systemui.statusbar.ScrimView;
import com.android.systemui.statusbar.phone.StatusBarWindowView;
import com.android.systemui.statusbar.stack.NotificationStackScrollLayout;

/**
 * Controls showing and hiding of the brightness mirror.
 */
public class BrightnessMirrorController {

    private final NotificationStackScrollLayout mStackScroller;
    public long TRANSITION_DURATION_OUT = 150;
    public long TRANSITION_DURATION_IN = 200;

    private final StatusBarWindowView mStatusBarWindow;
    private final ScrimView mScrimBehind;
    private final View mNotificationPanel;
    private final int[] mInt2Cache = new int[2];
    private View mBrightnessMirror;
    private ImageView mIcon;
    private Context mContext;

    public BrightnessMirrorController(Context context, StatusBarWindowView statusBarWindow) {
        mContext = context;
        mStatusBarWindow = statusBarWindow;
        mScrimBehind = (ScrimView) statusBarWindow.findViewById(R.id.scrim_behind);
        mBrightnessMirror = statusBarWindow.findViewById(R.id.brightness_mirror);
        setPadding();
        mNotificationPanel = statusBarWindow.findViewById(R.id.notification_panel);
        mStackScroller = (NotificationStackScrollLayout) statusBarWindow.findViewById(
                R.id.notification_stack_scroller);
        mIcon = (ImageView) mBrightnessMirror.findViewById(R.id.brightness_icon);
    }

    public void showMirror() {
        updateIcon();
        mBrightnessMirror.setVisibility(View.VISIBLE);
        mStackScroller.setFadingOut(true);
        mScrimBehind.animateViewAlpha(0.0f, TRANSITION_DURATION_OUT, Interpolators.ALPHA_OUT);
        outAnimation(mNotificationPanel.animate())
                .withLayer();
    }

    public void hideMirror() {
        mScrimBehind.animateViewAlpha(1.0f, TRANSITION_DURATION_IN, Interpolators.ALPHA_IN);
        inAnimation(mNotificationPanel.animate())
                .withLayer()
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        mBrightnessMirror.setVisibility(View.INVISIBLE);
                        mStackScroller.setFadingOut(false);
                    }
                });
    }

    private ViewPropertyAnimator outAnimation(ViewPropertyAnimator a) {
        return a.alpha(0.0f)
                .setDuration(TRANSITION_DURATION_OUT)
                .setInterpolator(Interpolators.ALPHA_OUT)
                .withEndAction(null);
    }
    private ViewPropertyAnimator inAnimation(ViewPropertyAnimator a) {
        return a.alpha(1.0f)
                .setDuration(TRANSITION_DURATION_IN)
                .setInterpolator(Interpolators.ALPHA_IN);
    }


    public void setLocation(View original) {
        original.getLocationInWindow(mInt2Cache);

        // Original is slightly larger than the mirror, so make sure to use the center for the
        // positioning.
        int originalX = mInt2Cache[0] + original.getWidth() / 2;
        int originalY = mInt2Cache[1] + original.getHeight() / 2;
        mBrightnessMirror.setTranslationX(0);
        mBrightnessMirror.setTranslationY(0);
        mBrightnessMirror.getLocationInWindow(mInt2Cache);
        int mirrorX = mInt2Cache[0] + mBrightnessMirror.getWidth() / 2;
        int mirrorY = mInt2Cache[1] + mBrightnessMirror.getHeight() / 2;
        mBrightnessMirror.setTranslationX(originalX - mirrorX);
        mBrightnessMirror.setTranslationY(originalY - mirrorY);
    }

    public View getMirror() {
        return mBrightnessMirror;
    }

    public void updateResources() {
        FrameLayout.LayoutParams lp =
                (FrameLayout.LayoutParams) mBrightnessMirror.getLayoutParams();
        lp.width = mBrightnessMirror.getResources().getDimensionPixelSize(
                R.dimen.notification_panel_width);
        lp.gravity = mBrightnessMirror.getResources().getInteger(
                R.integer.notification_panel_layout_gravity);
        mBrightnessMirror.setLayoutParams(lp);
    }

    public void onDensityOrFontScaleChanged() {
        int index = mStatusBarWindow.indexOfChild(mBrightnessMirror);
        mStatusBarWindow.removeView(mBrightnessMirror);
        mBrightnessMirror = LayoutInflater.from(mBrightnessMirror.getContext()).inflate(
                R.layout.brightness_mirror, mStatusBarWindow, false);
        setPadding();
        mStatusBarWindow.addView(mBrightnessMirror, index);
    }

    private void setPadding(){
        mBrightnessMirror.setPadding(mBrightnessMirror.getPaddingLeft(),
                    mBrightnessMirror.getPaddingTop(), mBrightnessMirror.getPaddingRight(),
                    mContext.getResources().getDimensionPixelSize(R.dimen.qs_brightness_footer_padding));
    }

    private void updateIcon() {
        if (mIcon == null) {
            return;
        }
        // enable the brightness icon
        boolean brightnessIconEnabled = Settings.System.getIntForUser(
                mContext.getContentResolver(), Settings.System.QS_SHOW_BRIGHTNESS_MODE,
                1, UserHandle.USER_CURRENT) == 1;
        mIcon = (ImageView) mBrightnessMirror.findViewById(R.id.brightness_icon);
        mIcon.setVisibility(brightnessIconEnabled? View.VISIBLE : View.GONE);

        boolean automatic = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                UserHandle.USER_CURRENT) != Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;
        mIcon.setImageResource(automatic ?
                com.android.systemui.R.drawable.ic_qs_brightness_auto_on_new :
                com.android.systemui.R.drawable.ic_qs_brightness_auto_off_new);
    }
}
