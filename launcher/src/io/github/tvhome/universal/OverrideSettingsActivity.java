package io.github.tvhome.universal;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class OverrideSettingsActivity extends Activity {
    private TextView status;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() { updateStatus(); handler.postDelayed(this, 1000); }
    };
    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        status = new TextView(this);
        status.setTextSize(13);
        status.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
        status.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        status.setTextAlignment(android.view.View.TEXT_ALIGNMENT_GRAVITY);
        status.setSingleLine(true);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status.setPadding(dp(14), 0, dp(14), 0);
        status.setTextColor(Color.WHITE);
        column.addView(status, new LinearLayout.LayoutParams(-1, dp(36)));
        addButton(column, UiText.get(R.string.ui_022), "power", () -> { LauncherOverrideService.setEnabled(this, true); updateStatus(); });
        addButton(column, UiText.get(R.string.ui_059), "accessibility", () -> {
            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
            catch (RuntimeException error) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        });
        addButton(column, UiText.get(R.string.ui_082), "power_off", () -> { LauncherOverrideService.setEnabled(this, false); updateStatus(); });

        SettingsPopup.show(this, UiText.get(R.string.ui_091), column, null);
    }
    private void addButton(LinearLayout parent, String label, String icon, Runnable action) {
        Button button = new Button(this);
        MenuIcon.attach(button,icon,19,Color.WHITE);
        button.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
        button.setSingleLine(true);
        button.setEllipsize(android.text.TextUtils.TruncateAt.END);
        button.setTextAlignment(android.view.View.TEXT_ALIGNMENT_GRAVITY);
        button.setText(label); button.setAllCaps(false);button.setTextSize(12);button.setMinHeight(0);button.setMinimumHeight(0); button.setOnClickListener(view -> action.run());
        parent.addView(button, new LinearLayout.LayoutParams(-1, -2));
    }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private void updateStatus() {
        boolean enabled=LauncherOverrideService.isEnabled(this);
        boolean connected=LauncherOverrideService.isConnected();
        MenuIcon.attach(status,!enabled?"status_inactive":connected?"status_active":"status_waiting",19,Color.WHITE);
        status.setText(!enabled ? UiText.get(R.string.ui_125)
                : connected ? UiText.get(R.string.ui_124)
                : UiText.get(R.string.ui_123));
    }
}
