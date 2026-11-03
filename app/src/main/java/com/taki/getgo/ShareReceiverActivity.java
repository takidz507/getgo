package com.taki.getgo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/**
 * v2.3 share target: "Share -> GetGo" from any app starts a
 * download WITHOUT opening GetGo's main screen. This activity
 * has no UI at all — it extracts the link, hands it to
 * DownloadService (which shows the progress notification),
 * and finishes at once, so the user stays in the app they
 * shared from. Shared links always use Video / Best, because
 * the share sheet carries no quality choice.
 */
public class ShareReceiverActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String link = extractLink(getIntent());
        if (link == null) {
            Toast.makeText(this, getString(R.string.share_no_link),
                    Toast.LENGTH_SHORT).show();
        } else if (DownloadService.isPaused() || Prefs.hasPausedJob(this)) {
            // v2.4: a paused download owns the slot, so a shared
            // link cannot start until it is resumed or cancelled.
            // Say so instead of the usual "downloading" toast.
            Toast.makeText(this, getString(R.string.status_paused_exists),
                    Toast.LENGTH_LONG).show();
        } else {
            DownloadService.startDownload(this, link, false, "Best");
            Toast.makeText(this, getString(R.string.share_downloading),
                    Toast.LENGTH_SHORT).show();
        }
        finish();
        overridePendingTransition(0, 0);
    }

    private String extractLink(Intent intent) {
        if (intent == null) {
            return null;
        }
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (text == null) {
            return null;
        }
        String[] parts = text.split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            String token = parts[i].trim();
            if (token.startsWith("http://") || token.startsWith("https://")) {
                // Strip trailing punctuation the share text may add.
                while (token.length() > 0 && ".,)".indexOf(
                        token.charAt(token.length() - 1)) >= 0) {
                    token = token.substring(0, token.length() - 1);
                }
                return token;
            }
        }
        return null;
    }
}
