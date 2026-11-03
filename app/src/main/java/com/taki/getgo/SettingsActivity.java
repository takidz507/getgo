package com.taki.getgo;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Simple Settings: two toggles, an About card, a GitHub line, a
 * legal note. Nothing else — v1 stays simple on purpose.
 */
public class SettingsActivity extends Activity {
    private boolean appliedDark;
    private View aboutCard;
    private View togglesCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        appliedDark = Prefs.isDark(this);
        if (appliedDark) {
            setTheme(R.style.AppTheme_Dark);
        } else {
            setTheme(R.style.AppTheme);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        aboutCard = findViewById(R.id.about_card);
        togglesCard = findViewById(R.id.toggles_card);
        applyCards();

        final Switch darkSwitch = (Switch) findViewById(R.id.dark_switch);
        darkSwitch.setChecked(Prefs.isDark(this));
        darkSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setDark(SettingsActivity.this, isChecked);
                recreate();
            }
        });

        final Switch glassSwitch = (Switch) findViewById(R.id.glass_switch);
        glassSwitch.setChecked(Prefs.isGlass(this));
        glassSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setGlass(SettingsActivity.this, isChecked);
                applyCards();
                // Subtle fade so the glass change feels smooth, not abrupt.
                aboutCard.setAlpha(0f);
                aboutCard.animate().alpha(1f).setDuration(300).start();
            }
        });

        TextView githubLine = (TextView) findViewById(R.id.github_line);
        githubLine.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/takidz507/getgo")));
                } catch (Exception ignored) {
                }
            }
        });
    }

    private void applyCards() {
        Prefs.applyCard(aboutCard, this);
        Prefs.applyCard(togglesCard, this);
    }

    @Override
    public void finish() {
        super.finish();
        // Soft return transition, matching the entry fade.
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
