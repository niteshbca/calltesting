package com.bharatsoftware.dialer;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import java.util.Calendar;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends Activity {
    private static final int PERMISSION_REQUEST = 41;
    private EditText number, batchNumbers;
    private TextView queueStatus;
    private final List<String> queue = new ArrayList<>();
    private int queueIndex = 0;
    private boolean queueActive = false, callObserved = false, receiverRegistered = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final BroadcastReceiver callStateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!queueActive || !TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) return;
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) callObserved = true;
            if (TelephonyManager.EXTRA_STATE_IDLE.equals(state) && callObserved) {
                callObserved = false;
                queueIndex++;
                queueStatus.setText("Call ended. Next call in 5 seconds...");
                handler.postDelayed(thisActivityNextCall(), 5000);
            }
        }
    };
    private Runnable thisActivityNextCall() { return () -> { if (queueActive) { if (telecom.isInCall()) stopQueue("Another call is active. List stopped."); else callNext(); } }; }
    private LinearLayout sims, history;
    private TextView reportSummary;
    private TelecomManager telecom;
    private PhoneAccountHandle selected;
    private final List<PhoneAccountHandle> handles = new ArrayList<>();

    private int dp(int size) { return (int) (size * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius));
        return d;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(14), dp(16), dp(14));
        container.setBackground(shape(Color.WHITE, 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(14));
        parent.addView(container, lp);
        return container;
    }
    private void sectionTitle(LinearLayout parent, String title, String subtitle) {
        parent.addView(text(title, 18, 0xff152a45, true));
        TextView hint = text(subtitle, 13, 0xff60758d, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(4), 0, dp(10));
        parent.addView(hint, lp);
    }
    private Button button(String title, boolean primary) {
        Button b = new Button(this);
