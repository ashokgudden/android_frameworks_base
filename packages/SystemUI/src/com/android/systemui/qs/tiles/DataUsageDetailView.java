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

package com.android.systemui.qs.tiles;

import android.annotation.ColorInt;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.android.settingslib.Utils;
import com.android.settingslib.net.DataUsageController;
import com.android.systemui.FontSizeUtils;
import com.android.systemui.R;
import com.android.systemui.qs.DataUsageGraph;

import java.text.DecimalFormat;
import java.util.List;

/**
 * Layout for the data usage detail in quick settings.
 */
public class DataUsageDetailView extends LinearLayout {

    private static final double KB = 1024;
    private static final double MB = 1024 * KB;
    private static final double GB = 1024 * MB;

    private final DecimalFormat FORMAT = new DecimalFormat("#.##");

    private RadioGroup mRadioGroup;
    private int mDefaultDataSim;

    public DataUsageDetailView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        FontSizeUtils.updateFontSize(this, android.R.id.title, R.dimen.qs_data_usage_text_size);
        FontSizeUtils.updateFontSize(this, R.id.usage_text, R.dimen.qs_data_usage_usage_text_size);
        FontSizeUtils.updateFontSize(this, R.id.usage_carrier_text,
                R.dimen.qs_data_usage_text_size);
        FontSizeUtils.updateFontSize(this, R.id.usage_info_top_text,
                R.dimen.qs_data_usage_text_size);
        FontSizeUtils.updateFontSize(this, R.id.usage_period_text, R.dimen.qs_data_usage_text_size);
        FontSizeUtils.updateFontSize(this, R.id.usage_info_bottom_text,
                R.dimen.qs_data_usage_text_size);
    }

    public void bind(DataUsageController.DataUsageInfo info) {
        final Resources res = mContext.getResources();
        final int titleId;
        final long bytes;
        @ColorInt int usageColor = 0;
        final String top;
        String bottom = null;
        if (info.usageLevel < info.warningLevel || info.limitLevel <= 0) {
            // under warning, or no limit
            titleId = R.string.quick_settings_cellular_detail_data_usage;
            bytes = info.usageLevel;
            top = res.getString(R.string.quick_settings_cellular_detail_data_warning,
                    formatBytes(info.warningLevel));
        } else if (info.usageLevel <= info.limitLevel) {
            // over warning, under limit
            titleId = R.string.quick_settings_cellular_detail_remaining_data;
            bytes = info.limitLevel - info.usageLevel;
            top = res.getString(R.string.quick_settings_cellular_detail_data_used,
                    formatBytes(info.usageLevel));
            bottom = res.getString(R.string.quick_settings_cellular_detail_data_limit,
                    formatBytes(info.limitLevel));
        } else {
            // over limit
            titleId = R.string.quick_settings_cellular_detail_over_limit;
            bytes = info.usageLevel - info.limitLevel;
            top = res.getString(R.string.quick_settings_cellular_detail_data_used,
                    formatBytes(info.usageLevel));
            bottom = res.getString(R.string.quick_settings_cellular_detail_data_limit,
                    formatBytes(info.limitLevel));
            usageColor = Utils.getDefaultColor(mContext, android.R.attr.colorError);
        }

        if (usageColor == 0) {
            usageColor = Utils.getColorAccent(mContext);
        }

        final TextView title = findViewById(android.R.id.title);
        title.setText(titleId);
        final TextView usage = findViewById(R.id.usage_text);
        usage.setText(formatBytes(bytes));
        usage.setTextColor(usageColor);
        final DataUsageGraph graph = findViewById(R.id.usage_graph);
        graph.setLevels(info.limitLevel, info.warningLevel, info.usageLevel);
        final TextView carrier = findViewById(R.id.usage_carrier_text);
        carrier.setText(info.carrier);
        final TextView period = findViewById(R.id.usage_period_text);
        period.setText(info.period);
        final TextView infoTop = findViewById(R.id.usage_info_top_text);
        infoTop.setVisibility(top != null ? View.VISIBLE : View.GONE);
        infoTop.setText(top);
        final TextView infoBottom = findViewById(R.id.usage_info_bottom_text);
        infoBottom.setVisibility(bottom != null ? View.VISIBLE : View.GONE);
        infoBottom.setText(bottom);
        boolean showLevel = info.warningLevel > 0 || info.limitLevel > 0;
        graph.setVisibility(showLevel ? View.VISIBLE : View.GONE);
        if (!showLevel) {
            infoTop.setVisibility(View.GONE);
        }

        // prepare view

        mRadioGroup = (RadioGroup) findViewById(R.id.sim_cards);
        mRadioGroup.removeAllViews();
        
        int activeSimCount = getActiveSimCount();
        if (activeSimCount > 1) {
            mRadioGroup.setVisibility(View.VISIBLE);
            for (int slotId = 0; slotId < activeSimCount; slotId++) {
                String carrierName = getCarrierName(slotId);
                RadioButton radioButton = new RadioButton(mContext);
                radioButton.setText(mContext.getString(R.string.use_data, carrierName));
//                mRadioGroup.addView(radioButton, LayoutParams.MATCH_PARENT,
                mRadioGroup.addView(radioButton, LayoutParams.WRAP_CONTENT,
                        LayoutParams.WRAP_CONTENT);
            }

            // check default data slot
            mDefaultDataSim = SubscriptionManager.getDefaultDataSubscriptionId();
            int defaultDataSlotId = SubscriptionManager.getSlotIndex(mDefaultDataSim);
            RadioButton radioButton = (RadioButton) mRadioGroup.getChildAt(defaultDataSlotId);
            radioButton.setChecked(true);

            // set checkListener
            mRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(RadioGroup group, int checkedId) {
                    for (int slotId = 0; slotId < getActiveSimCount(); slotId++) {
                        if ((mRadioGroup.getChildAt(slotId).getId() == checkedId)) {
                            int[] subId = SubscriptionManager.getSubId(slotId);
                            SubscriptionManager.from(mContext).setDefaultDataSubId(subId[0]);
                        }
                    }

                    int mNewDefaultSim = SubscriptionManager.getDefaultDataSubscriptionId();
                    if (mDefaultDataSim != mNewDefaultSim) {
                        Intent closeIntent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
                        Toast.makeText(mContext, R.string.data_switch_started, Toast.LENGTH_SHORT).show();
                        mContext.sendBroadcast(closeIntent);
                    }
                }
            });
        }
    }

    private int getActiveSimCount() {
        int activeSimCount = 0;
        TelephonyManager telephonyManager =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        for (int i =0 ; i < telephonyManager.getPhoneCount(); i++) {
            if (telephonyManager.getSimState(i) == TelephonyManager.SIM_STATE_READY) {
                activeSimCount++;
            }
        }
        return activeSimCount;
    }

    private String getCarrierName(int slotId) {
        SubscriptionManager sm = SubscriptionManager.from(mContext);
        List<SubscriptionInfo> subs = sm.getActiveSubscriptionInfoList();
        CharSequence carrierName = subs.get(slotId).getCarrierName();
        return carrierName.toString();
    }

    private String formatBytes(long bytes) {
        final long b = Math.abs(bytes);
        double val;
        String suffix;
        if (b > 100 * MB) {
            val = b / GB;
            suffix = "GB";
        } else if (b > 100 * KB) {
            val = b / MB;
            suffix = "MB";
        } else {
            val = b / KB;
            suffix = "KB";
        }
        return FORMAT.format(val * (bytes < 0 ? -1 : 1)) + " " + suffix;
    }
}
