/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.qs;

import android.util.Log;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.View.OnLayoutChangeListener;
import android.widget.HorizontalScrollView;

import com.android.systemui.Dependency;
import com.android.systemui.plugins.qs.*;
import com.android.systemui.plugins.qs.QSTileView;
import com.android.systemui.qs.PagedTileLayout.PageListener;
import com.android.systemui.qs.QSPanel.QSTileLayout;
import com.android.systemui.qs.QSHost.Callback;
import com.android.systemui.qs.TouchAnimator.Builder;
import com.android.systemui.qs.TouchAnimator.Listener;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.tuner.TunerService.Tunable;

import java.util.ArrayList;
import java.util.Collection;

public class QSAnimator implements Callback, PageListener, Listener, OnLayoutChangeListener,
        OnAttachStateChangeListener, Tunable {

    private static final String TAG = "QSAnimator";

    private static final String ALLOW_FANCY_ANIMATION = "sysui_qs_fancy_anim";
    private static final String MOVE_FULL_ROWS = "sysui_qs_move_whole_rows";

    public static final float EXPANDED_TILE_DELAY = .86f;

    private final ArrayList<View> mAllViews = new ArrayList<>();
    private final ArrayList<View> mTopFiveQs = new ArrayList<>();
    private final QuickQSPanel mQuickQsPanel;
    private final QSPanel mQsPanel;
    private final QS mQs;

    private PagedTileLayout mPagedLayout;

    private boolean mOnFirstPage = true;
    private TouchAnimator mFirstPageAnimator;
    private TouchAnimator mFirstPageDelayedAnimator;
    private TouchAnimator mTranslationXAnimator;
    private TouchAnimator mTranslationYAnimator;
    private TouchAnimator mNonfirstPageAnimator;
    private TouchAnimator mBrightnessAnimator;

    private boolean mOnKeyguard;

    private boolean mAllowFancy;
    private boolean mFullRows;
    //private int mNumQuickTiles;
    private float mLastPosition;
    private boolean mNotScrolled = true;
    private QSTileHost mHost;
    private HorizontalScrollView mQuickQsPanelScroller;

    public QSAnimator(QS qs, QuickQSPanel quickPanel, QSPanel panel,
                      HorizontalScrollView quickPanelScroller) {
        mQs = qs;
        mQuickQsPanel = quickPanel;
        mQsPanel = panel;
        mQuickQsPanelScroller = quickPanelScroller;
        mQsPanel.addOnAttachStateChangeListener(this);
        qs.getView().addOnLayoutChangeListener(this);
        if (mQsPanel.isAttachedToWindow()) {
            onViewAttachedToWindow(null);
        }
        QSTileLayout tileLayout = mQsPanel.getTileLayout();
        if (tileLayout instanceof PagedTileLayout) {
            mPagedLayout = ((PagedTileLayout) tileLayout);
            mPagedLayout.setPageListener(this);
        } else {
            Log.w(TAG, "QS Not using page layout");
        }
    }

    public void onRtlChanged() {
        updateAnimators();
    }

    public void setOnKeyguard(boolean onKeyguard) {
        mOnKeyguard = onKeyguard;
        mQuickQsPanel.setVisibility(mOnKeyguard ? View.INVISIBLE : View.VISIBLE);
        if (mOnKeyguard) {
            clearAnimationState();
        }
    }

    public void setHost(QSTileHost qsh) {
        mHost = qsh;
        qsh.addCallback(this);
        updateAnimators();
    }

    @Override
    public void onViewAttachedToWindow(View v) {
        Dependency.get(TunerService.class).addTunable(this, ALLOW_FANCY_ANIMATION,
                MOVE_FULL_ROWS, QuickQSPanel.NUM_QUICK_TILES);
    }

    @Override
    public void onViewDetachedFromWindow(View v) {
        if (mHost != null) {
            mHost.removeCallback(this);
        }
        Dependency.get(TunerService.class).removeTunable(this);
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        if (ALLOW_FANCY_ANIMATION.equals(key)) {
            mAllowFancy = newValue == null || Integer.parseInt(newValue) != 0;
            if (!mAllowFancy) {
                clearAnimationState();
            }
        } else if (MOVE_FULL_ROWS.equals(key)) {
            mFullRows = newValue == null || Integer.parseInt(newValue) != 0;
        } else if (QuickQSPanel.NUM_QUICK_TILES.equals(key)) {
            //mNumQuickTiles = mQuickQsPanel.getNumQuickTiles(mQs.getContext());
            clearAnimationState();
        }
        updateAnimators();
    }

    @Override
    public void onPageChanged(boolean isFirst) {
        if (mOnFirstPage == isFirst) return;
        if (!isFirst) {
            clearAnimationState();
        }
        mOnFirstPage = isFirst;
    }

    private void updateAnimators() {
        TouchAnimator.Builder firstPageBuilder = new Builder();
        TouchAnimator.Builder translationXBuilder = new Builder();
        TouchAnimator.Builder translationYBuilder = new Builder();

        if (mQsPanel.getHost() == null) return;
        Collection<QSTile> tiles = mQsPanel.getHost().getTiles();
        int count = 0;
        int[] loc1 = new int[2];
        int[] loc2 = new int[2];
        int lastXDiff = 0;
        int lastX = 0;

        clearAnimationState();
        mAllViews.clear();
        mTopFiveQs.clear();

        QSTileLayout tileLayout = mQsPanel.getTileLayout();
        mAllViews.add((View) tileLayout);
        int height = mQs.getView() != null ? mQs.getView().getMeasuredHeight() : 0;
        int heightDiff = height - mQs.getHeader().getBottom()
                + mQs.getHeader().getPaddingBottom();
        firstPageBuilder.addFloat(tileLayout, "translationY", heightDiff, 0);

        for (QSTile tile : tiles) {
            QSTileView tileView = mQsPanel.getTileView(tile);
            if (tileView == null) {
                Log.e(TAG, "tileView is null " + tile.getTileSpec());
                continue;
            }
            final View tileIcon = tileView.getIcon().getIconView();
            View view = mQs.getView();
            if (count < mQuickQsPanel.getNumVisibleQuickTiles() && allowFancy(false)) {
                // Quick tiles.
                QSTileView quickTileView = mQuickQsPanel.getTileView(tile);
                if (quickTileView == null) continue;

                lastX = loc1[0];
                if (quickTileView.getIcon().getIconView() != null) {
                    getRelativePosition(loc1, quickTileView.getIcon().getIconView(), view);
                }               
                getRelativePosition(loc2, tileIcon, view);
                final int xDiff = loc2[0] - loc1[0];
                final int yDiff = loc2[1] - loc1[1];
                lastXDiff = loc1[0] - lastX;
                // Move the quick tile right from its location to the new one.
                translationXBuilder.addFloat(quickTileView, "translationX", 0, xDiff);
                translationYBuilder.addFloat(quickTileView, "translationY", 0, yDiff);

                // Counteract the parent translation on the tile. So we have a static base to
                // animate the label position off from.
                //firstPageBuilder.addFloat(tileView, "translationY", mQsPanel.getHeight(), 0);

                // Move the real tile from the quick tile position to its final
                // location.
                translationXBuilder.addFloat(tileView, "translationX", -xDiff, 0);
                translationYBuilder.addFloat(tileView, "translationY", -yDiff, 0);

                mTopFiveQs.add(tileView.getIcon());
                mAllViews.add(tileView.getIcon());
                mAllViews.add(quickTileView);
            } else if (fullRows() && isIconInAnimatedRow(count)) {
                // TODO: Refactor some of this, it shares a lot with the above block.
                // Move the last tile position over by the last difference between quick tiles.
                // This makes the extra icons seems as if they are coming from positions in the
                // quick panel.
                loc1[0] += lastXDiff;
                getRelativePosition(loc2, tileIcon, view);
                final int xDiff = loc2[0] - loc1[0];
                final int yDiff = loc2[1] - loc1[1];

                firstPageBuilder.addFloat(tileView, "translationY", heightDiff, 0);
                translationXBuilder.addFloat(tileView, "translationX", -xDiff, 0);
                translationYBuilder.addFloat(tileView, "translationY", -yDiff, 0);
                translationYBuilder.addFloat(tileIcon, "translationY", -yDiff, 0);

                mAllViews.add(tileIcon);
            } else {
                QSTileView quickTileView = mQuickQsPanel.getTileView(tile);
                if (quickTileView != null) {
                    mAllViews.add(quickTileView);
                }
            }
            mAllViews.add(tileView);
            count++;
        }
        View brightness = mQsPanel.getBrightnessView();
        if (allowFancy(false)) {
            // Make brightness appear static position and alpha in through second half.
            /*View brightness = mQsPanel.getBrightnessView();
            if (brightness != null) {
                firstPageBuilder.addFloat(brightness, "translationY", heightDiff, 0);
                mBrightnessAnimator = new TouchAnimator.Builder()
                        .addFloat(brightness, "alpha", 0, 1)
                        .setStartDelay(.5f)
                        .build();
                mAllViews.add(brightness);
            } else {
                mBrightnessAnimator = null;
            }*/
            mFirstPageAnimator = firstPageBuilder
                    .setListener(this)
                    .build();
            // Fade in the tiles/labels as we reach the final position.
            TouchAnimator.Builder builder = new TouchAnimator.Builder()
                    .setStartDelay(EXPANDED_TILE_DELAY)
                    .addFloat(tileLayout, "alpha", 0, 1)
                    .addFloat(mQsPanel.getPageIndicator(), "alpha", 0, 1)
                    .addFloat(mQsPanel.getDivider(), "alpha", 0, 1)
                    .addFloat(mQsPanel.getFooter().getView(), "alpha", 0, 1);
            if (brightness != null) {
                builder.addFloat(mQsPanel.getBrightnessView(), "alpha", 0, 1);
            }
            mFirstPageDelayedAnimator = builder.build();
            mAllViews.add(mQsPanel.getPageIndicator());
            mAllViews.add(mQsPanel.getDivider());
            mAllViews.add(mQsPanel.getFooter().getView());
            if (brightness != null) {
                mAllViews.add(mQsPanel.getBrightnessView());
            }
            float px = 0;
            float py = 1;
            if (tiles.size() <= 3) {
                px = 1;
            } else if (tiles.size() <= 6) {
                px = .4f;
            }
            PathInterpolatorBuilder interpolatorBuilder = new PathInterpolatorBuilder(0, 0, px, py);
            translationXBuilder.setInterpolator(interpolatorBuilder.getXInterpolator());
            translationYBuilder.setInterpolator(interpolatorBuilder.getYInterpolator());
            mTranslationXAnimator = translationXBuilder.build();
            mTranslationYAnimator = translationYBuilder.build();
        }
        TouchAnimator.Builder builder = new TouchAnimator.Builder()
                .addFloat(mQuickQsPanel, "alpha", 1, 0)
                .addFloat(mQsPanel.getPageIndicator(), "alpha", 0, 1)
                .addFloat(mQsPanel.getDivider(), "alpha", 0, 1)
                .setListener(mNonFirstPageListener)
                .setEndDelay(.5f);
        if (brightness != null) {
            builder.addFloat(mQsPanel.getBrightnessView(), "alpha", 0, 1);
        }
        mNonfirstPageAnimator = builder.build();
    }

    private boolean isIconInAnimatedRow(int count) {
        if (mPagedLayout == null) {
            return false;
        }
        final int columnCount = mPagedLayout.getColumnCount();
        return count < ((mQuickQsPanel.getNumVisibleQuickTiles() + columnCount - 1) / columnCount)
                * columnCount;
    }

    private void getRelativePosition(int[] loc1, View view, View parent) {
        loc1[0] = 0 + view.getWidth() / 2;
        loc1[1] = 0;
        getRelativePositionInt(loc1, view, parent);
    }

    private void getRelativePositionInt(int[] loc1, View view, View parent) {
        if(view == parent || view == null) return;
        // Ignore tile pages as they can have some offset we don't want to take into account in
        // RTL.
        if (!(view instanceof PagedTileLayout.TilePage)) {
            loc1[0] += view.getLeft();
            loc1[1] += view.getTop();
        }
        getRelativePositionInt(loc1, (View) view.getParent(), parent);
    }

    public void setPosition(float position) {
        if (mFirstPageAnimator == null) return;
        if (mOnKeyguard) {
            return;
        }
        mLastPosition = position;
        if (mOnFirstPage && allowFancy(true)) {
            mQuickQsPanel.setAlpha(1);
            mFirstPageAnimator.setPosition(position);
            mFirstPageDelayedAnimator.setPosition(position);
            mTranslationXAnimator.setPosition(position);
            mTranslationYAnimator.setPosition(position);
            /*if (mBrightnessAnimator != null) {
                mBrightnessAnimator.setPosition(position);
            }*/
        } else {
            mNonfirstPageAnimator.setPosition(position);
        }
    }

    @Override
    public void onAnimationAtStart() {
        mQuickQsPanel.setVisibility(View.VISIBLE);
    }

    @Override
    public void onAnimationAtEnd() {
        mQuickQsPanel.setVisibility(View.INVISIBLE);
        final int N = mTopFiveQs.size();
        for (int i = 0; i < N; i++) {
            mTopFiveQs.get(i).setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onAnimationStarted() {
        mQuickQsPanel.setVisibility(mOnKeyguard ? View.INVISIBLE : View.VISIBLE);
        if (mOnFirstPage) {
            final int N = mTopFiveQs.size();
            for (int i = 0; i < N; i++) {
                mTopFiveQs.get(i).setVisibility(View.INVISIBLE);
            }
        }
    }

    private void clearAnimationState() {
        final int N = mAllViews.size();
        mQuickQsPanel.setAlpha(0);
        for (int i = 0; i < N; i++) {
            View v = mAllViews.get(i);
            v.setAlpha(1);
            v.setTranslationX(0);
            v.setTranslationY(0);
        }
        final int N2 = mTopFiveQs.size();
        for (int i = 0; i < N2; i++) {
            mTopFiveQs.get(i).setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
            int oldTop, int oldRight, int oldBottom) {
        mQsPanel.post(mUpdateAnimators);
    }

    @Override
    public void onTilesChanged() {
        // Give the QS panels a moment to generate their new tiles, then create all new animators
        // hooked up to the new views.
        mQsPanel.post(mUpdateAnimators);
    }

    private final TouchAnimator.Listener mNonFirstPageListener =
            new TouchAnimator.ListenerAdapter() {
                @Override
                public void onAnimationAtEnd() {
                    mQuickQsPanel.setVisibility(View.INVISIBLE);
                }

                @Override
                public void onAnimationStarted() {
                    mQuickQsPanel.setVisibility(View.VISIBLE);
                }
            };

    private Runnable mUpdateAnimators = new Runnable() {
        @Override
        public void run() {
            updateAnimators();
            setPosition(mLastPosition);
        }
    };

    private boolean allowFancy(boolean clearAnim) {
        return mAllowFancy && notScrolled(clearAnim);
    }

    private boolean fullRows() {
        return mFullRows && notScrolled(false);
    }

    private boolean notScrolled(boolean clearAnim) {
        if ((mQuickQsPanelScroller.getScrollX() == 0) != mNotScrolled) {
            mNotScrolled = !mNotScrolled;
            if (clearAnim) {
                clearAnimationState();
            }
        }
        return mNotScrolled;
    }
}
