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
        b.setText(title); b.setAllCaps(false); b.setTextSize(15);
        b.setTextColor(primary ? Color.WHITE : 0xff155bbb);
        b.setBackground(shape(primary ? 0xff155bbb : 0xffe9f1fc, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
        lp.setMargins(0, dp(8), 0, 0);
        b.setLayoutParams(lp);
        return b;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        telecom = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
        ScrollView page = new ScrollView(this);
        page.setFillViewport(true);
        page.setBackgroundColor(0xfff3f7fc);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(24), dp(18), dp(32));
        page.addView(root);
        root.addView(text("BHARAT SOFTWARE", 12, 0xff155bbb, true));
        TextView heading = text("Call workspace", 28, 0xff142b49, true);
        root.addView(heading);
        TextView subheading = text("Dial one number or run a call list", 14, 0xff60758d, false);
        LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(-1, -2);
        headingLp.setMargins(0, dp(2), 0, dp(20));
        root.addView(subheading, headingLp);

        LinearLayout simCard = card(root);
        sectionTitle(simCard, "1. Choose SIM", "Calls in the list use this SIM");
        sims = new LinearLayout(this); sims.setOrientation(LinearLayout.VERTICAL);
        simCard.addView(sims);

        LinearLayout singleCard = card(root);
        sectionTitle(singleCard, "Single call", "Enter a number and call now");
        number = new EditText(this);
        number.setHint("Phone number"); number.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        number.setSingleLine(true);
        singleCard.addView(number);
        Button call = button("Call number", true); singleCard.addView(call);
        call.setOnClickListener(v -> placeCall());

        LinearLayout listCard = card(root);
        sectionTitle(listCard, "2. Call list", "Paste one phone number on each line");
        batchNumbers = new EditText(this);
        batchNumbers.setHint("9876543210\n9123456789");
        batchNumbers.setSingleLine(false); batchNumbers.setMinLines(5);
        batchNumbers.setGravity(android.view.Gravity.TOP);
        batchNumbers.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        batchNumbers.setPadding(dp(12), dp(12), dp(12), dp(12));
        batchNumbers.setBackground(shape(0xfff3f7fc, 10));
        listCard.addView(batchNumbers, new LinearLayout.LayoutParams(-1, dp(126)));
        Button startQueue = button("Start call list", true); listCard.addView(startQueue);
        startQueue.setOnClickListener(v -> startQueue());
        Button stopQueue = button("Stop next calls", false); listCard.addView(stopQueue);
        stopQueue.setOnClickListener(v -> stopQueue("Stopped. Current call remains connected."));
        queueStatus = text("No list running", 14, 0xff34506e, true);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.setMargins(0, dp(12), 0, 0);
        listCard.addView(queueStatus, statusLp);
        listCard.addView(text("Next number starts 5 seconds after the current call ends. Keep the app open.", 12, 0xff60758d, false));

        LinearLayout reportCard = card(root);
        sectionTitle(reportCard, "3. Call report", "Saved in this phone's call history");
        reportSummary = text("Loading report...", 15, 0xff155bbb, true);
        reportCard.addView(reportSummary);
        Button refresh = button("Refresh report", false); reportCard.addView(refresh);
        refresh.setOnClickListener(v -> loadHistory());
        history = new LinearLayout(this); history.setOrientation(LinearLayout.VERTICAL);
        reportCard.addView(history);
        setContentView(page);
        askPermissions();
    }

    private boolean granted(String p) { return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED; }
    private void askPermissions() {
        List<String> missing = new ArrayList<>();
        for (String p : new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE, Manifest.permission.READ_CALL_LOG})
            if (!granted(p)) missing.add(p);
        if (!missing.isEmpty()) requestPermissions(missing.toArray(new String[0]), PERMISSION_REQUEST);
        else refresh();
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == PERMISSION_REQUEST) refresh();
    }
    @Override protected void onResume() { super.onResume(); if (sims != null) refresh(); }
    private void refresh() { loadSims(); loadHistory(); }

    private void loadSims() {
        PhoneAccountHandle previousSelection = selected;
        sims.removeAllViews(); handles.clear(); selected = null;
        if (!granted(Manifest.permission.READ_PHONE_STATE)) {
            info(sims, "Allow Phone permission to show available SIMs."); return;
        }
        try {
            List<PhoneAccountHandle> available = telecom.getCallCapablePhoneAccounts();
            SubscriptionManager sm = (SubscriptionManager) getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            List<SubscriptionInfo> subscriptions = sm.getActiveSubscriptionInfoList();
            if (available != null) for (PhoneAccountHandle handle : available) {
                PhoneAccount account = telecom.getPhoneAccount(handle);
                if (account == null || !account.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)) continue;
                handles.add(handle);
                int subId = -1;
                if (account.getExtras() != null)
                    subId = account.getExtras().getInt("android.telephony.extra.SUBSCRIPTION_INDEX", -1);
                String label = account.getLabel() == null ? "Mobile SIM" : account.getLabel().toString();
                if (subscriptions != null) for (SubscriptionInfo sub : subscriptions) {
                    if (sub.getSubscriptionId() == subId) {
                        label = "SIM " + (sub.getSimSlotIndex() + 1) + " · " + label;
                        break;
                    }
                }
                Button option = new Button(this);
                option.setText(label + "  (tap to select)");
                option.setAllCaps(false);
                sims.addView(option);
                if (handle.equals(previousSelection)) { selected = handle; option.setText(label + "  ✓ selected"); }
                option.setOnClickListener(v -> {
                    if (queueActive) { Toast.makeText(this, "Stop the list before changing SIM", Toast.LENGTH_SHORT).show(); return; }
                    selected = handle;
                    for (int i = 0; i < sims.getChildCount(); i++) {
                        View child = sims.getChildAt(i);
                        if (child instanceof Button) child.setAlpha(child == option ? 1f : .45f);
                    }
                    option.setText(labelFor(account) + "  ✓ selected");
                });
            }
            if (handles.isEmpty()) info(sims, "No callable SIM found. Insert and enable a SIM.");
        } catch (SecurityException e) { info(sims, "Phone permission is required."); }
    }
    private String labelFor(PhoneAccount account) {
        return account.getLabel() == null ? "Mobile SIM" : account.getLabel().toString();
    }
    private void startQueue() {
        if (queueActive) { Toast.makeText(this, "A list is already running", Toast.LENGTH_SHORT).show(); return; }
        if (selected == null || !handles.contains(selected)) {
            Toast.makeText(this, "Select a SIM first", Toast.LENGTH_SHORT).show(); return;
        }
        if (!granted(Manifest.permission.CALL_PHONE) || !granted(Manifest.permission.READ_PHONE_STATE)) {
            askPermissions(); return;
        }
        if (telecom.isInCall()) { Toast.makeText(this, "Finish current call first", Toast.LENGTH_SHORT).show(); return; }
        queue.clear();
        for (String line : batchNumbers.getText().toString().split("\\R")) {
            String digits = line.trim();
            if (digits.isEmpty()) continue;
            if (!digits.matches("[+]?[0-9]{3,15}")) {
                Toast.makeText(this, "Invalid number: " + digits, Toast.LENGTH_LONG).show(); return;
            }
            queue.add(digits);
        }
        if (queue.isEmpty()) { Toast.makeText(this, "Enter at least one number", Toast.LENGTH_SHORT).show(); return; }
        queueIndex = 0; callObserved = false; queueActive = true;
        if (!receiverRegistered) {
            registerReceiver(callStateReceiver, new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED));
            receiverRegistered = true;
        }
        callNext();
    }
    private void callNext() {
        if (!queueActive) return;
        if (queueIndex >= queue.size()) { stopQueue("Finished all " + queue.size() + " numbers."); return; }
        if (selected == null || !handles.contains(selected)) { stopQueue("Selected SIM unavailable. List stopped."); return; }
        queueStatus.setText("Calling " + (queueIndex + 1) + "/" + queue.size() + ": " + queue.get(queueIndex));
        callObserved = false;
        if (!callNumber(queue.get(queueIndex))) { stopQueue("Call could not start. List stopped."); return; }
        final int expected = queueIndex;
        handler.postDelayed(() -> {
            if (queueActive && queueIndex == expected && !callObserved)
                stopQueue("No call state detected. List paused to avoid a wrong next call.");
        }, 45000);
    }
    private void stopQueue(String message) {
        queueActive = false; callObserved = false;
        handler.removeCallbacksAndMessages(null);
        if (receiverRegistered) { unregisterReceiver(callStateReceiver); receiverRegistered = false; }
        if (queueStatus != null) queueStatus.setText(message);
    }
    @Override protected void onDestroy() {
        stopQueue("List stopped because app closed.");
        super.onDestroy();
    }
    private boolean callNumber(String digits) {
        if (selected == null || !granted(Manifest.permission.CALL_PHONE)) return false;
        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, selected);
        try { telecom.placeCall(Uri.fromParts("tel", digits, null), extras); return true; }
        catch (SecurityException | IllegalArgumentException e) { return false; }
    }
    private void placeCall() {
        String digits = number.getText().toString().trim();
        if (digits.isEmpty() || !digits.matches("[+0-9*#() .-]+")) {
            Toast.makeText(this, "Enter a valid number", Toast.LENGTH_SHORT).show(); return;
        }
        if (selected == null || !handles.contains(selected)) {
            Toast.makeText(this, "Select a SIM first", Toast.LENGTH_SHORT).show(); return;
        }
        if (!granted(Manifest.permission.CALL_PHONE)) { askPermissions(); return; }
        if (!callNumber(digits)) Toast.makeText(this, "Call failed: check SIM and permissions", Toast.LENGTH_LONG).show();
    }
    private void loadHistory() {
        history.removeAllViews();
        if (!granted(Manifest.permission.READ_CALL_LOG)) {
            reportSummary.setText("Call log permission needed");
            info(history, "Allow Call logs permission to view the report."); return;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0);
        long today = calendar.getTimeInMillis();
        String[] cols = {CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION};
        try (Cursor c = getContentResolver().query(CallLog.Calls.CONTENT_URI, cols,
                null, null, CallLog.Calls.DATE + " DESC")) {
            if (c == null) { reportSummary.setText("Call report unavailable"); return; }
            int shown = 0, outgoingToday = 0;
            long secondsToday = 0;
            while (c.moveToNext()) {
                String phone = c.getString(0);
                int type = c.getInt(1);
                long date = c.getLong(2);
                long duration = c.getLong(3);
                if (date >= today && type == CallLog.Calls.OUTGOING_TYPE) {
                    outgoingToday++;
                    secondsToday += duration;
                }
                if (shown++ >= 50) continue;
                String kind = type == CallLog.Calls.INCOMING_TYPE ? "Incoming" :
                        type == CallLog.Calls.OUTGOING_TYPE ? "Outgoing" :
                        type == CallLog.Calls.MISSED_TYPE ? "Missed" : "Call";
                TextView row = text((phone == null ? "Unknown" : phone) + "  ·  " + kind +
                        "\n" + DateFormat.getDateTimeInstance().format(new Date(date)) +
                        "  ·  " + (duration / 60) + "m " + (duration % 60) + "s", 14, 0xff233a55, false);
                row.setPadding(0, dp(12), 0, dp(12));
                history.addView(row);
                View divider = new View(this); divider.setBackgroundColor(0xffe6edf5);
                history.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
                if (phone != null && !phone.isEmpty()) row.setOnClickListener(v -> number.setText(phone));
            }
            reportSummary.setText("Today: " + outgoingToday + " outgoing calls  ·  " +
                    (secondsToday / 60) + " min total talk time");
            if (shown == 0) info(history, "No recent calls.");
        } catch (SecurityException e) { reportSummary.setText("Call log permission required"); }
    }
    private void info(LinearLayout container, String value) {
        TextView t = new TextView(this); t.setText(value); t.setPadding(0, 12, 0, 12);
        container.addView(t);
    }
}
