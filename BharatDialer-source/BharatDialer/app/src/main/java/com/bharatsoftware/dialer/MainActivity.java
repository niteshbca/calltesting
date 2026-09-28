package com.bharatsoftware.dialer;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
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
    private EditText number;
    private LinearLayout sims, history;
    private TelecomManager telecom;
    private PhoneAccountHandle selected;
    private final List<PhoneAccountHandle> handles = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        telecom = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 32, 28, 20);
        root.setBackgroundColor(Color.WHITE);
        TextView heading = new TextView(this);
        heading.setText("Bharat Dialer"); heading.setTextSize(26); heading.setTextColor(0xff123b68);
        heading.setTypeface(null, Typeface.BOLD);
        root.addView(heading);
        number = new EditText(this);
        number.setHint("Phone number"); number.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        root.addView(number);
        TextView simTitle = new TextView(this);
        simTitle.setText("Select SIM before calling"); simTitle.setTextSize(18);
        root.addView(simTitle);
        sims = new LinearLayout(this); sims.setOrientation(LinearLayout.VERTICAL);
        root.addView(sims);
        Button call = new Button(this); call.setText("CALL"); root.addView(call);
        call.setOnClickListener(v -> placeCall());
        TextView title = new TextView(this); title.setText("Recent call history");
        title.setTextSize(20); title.setTypeface(null, Typeface.BOLD);
        root.addView(title);
        Button refresh = new Button(this); refresh.setText("Refresh history"); root.addView(refresh);
        refresh.setOnClickListener(v -> loadHistory());
        ScrollView scroll = new ScrollView(this);
        history = new LinearLayout(this); history.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(history);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
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
                sims.addView(option);
                option.setOnClickListener(v -> {
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
    private void placeCall() {
        String digits = number.getText().toString().trim();
        if (digits.isEmpty() || !digits.matches("[+0-9*#() .-]+")) {
            Toast.makeText(this, "Enter a valid number", Toast.LENGTH_SHORT).show(); return;
        }
        if (selected == null || !handles.contains(selected)) {
            Toast.makeText(this, "Select a SIM first", Toast.LENGTH_SHORT).show(); return;
        }
        if (!granted(Manifest.permission.CALL_PHONE)) { askPermissions(); return; }
        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, selected);
        try { telecom.placeCall(Uri.fromParts("tel", digits, null), extras); }
        catch (SecurityException | IllegalArgumentException e) {
            Toast.makeText(this, "Call failed: check SIM and permissions", Toast.LENGTH_LONG).show();
        }
    }
    private void loadHistory() {
        history.removeAllViews();
        if (!granted(Manifest.permission.READ_CALL_LOG)) {
            info(history, "Allow Call logs permission to show history."); return;
        }
        String[] cols = {CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE};
        try (Cursor c = getContentResolver().query(CallLog.Calls.CONTENT_URI, cols,
                null, null, CallLog.Calls.DATE + " DESC")) {
            if (c == null) { info(history, "Call history unavailable."); return; }
            int count = 0;
            while (c.moveToNext() && count++ < 50) {
                String phone = c.getString(0);
                int type = c.getInt(1);
                long date = c.getLong(2);
                String kind = type == CallLog.Calls.INCOMING_TYPE ? "Incoming" :
                        type == CallLog.Calls.OUTGOING_TYPE ? "Outgoing" :
                        type == CallLog.Calls.MISSED_TYPE ? "Missed" : "Call";
                TextView row = new TextView(this);
                row.setText((phone == null ? "Unknown" : phone) + "  ·  " + kind + "\n" +
                        DateFormat.getDateTimeInstance().format(new Date(date)));
                row.setTextSize(16); row.setPadding(0, 16, 0, 16);
                history.addView(row);
                if (phone != null && !phone.isEmpty()) row.setOnClickListener(v -> number.setText(phone));
            }
            if (count == 0) info(history, "No recent calls.");
        } catch (SecurityException e) { info(history, "Call logs permission is required."); }
    }
    private void info(LinearLayout container, String value) {
        TextView t = new TextView(this); t.setText(value); t.setPadding(0, 12, 0, 12);
        container.addView(t);
    }
}
