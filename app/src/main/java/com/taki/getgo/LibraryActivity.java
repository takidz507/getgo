package com.taki.getgo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * v2.2 dedicated Library screen: every file GetGo downloaded,
 * newest first. Tap a row to play it; the ⋮ on a row gives
 * Play / Share / Delete. Delete removes the file from the
 * device (best effort via MediaStore) and always removes it
 * from GetGo's own index, so it never stays listed after a
 * failed delete.
 */
public class LibraryActivity extends Activity {
    private LinearLayout container;
    private TextView emptyText;
    private View libraryCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Prefs.isDark(this)) {
            setTheme(R.style.AppTheme_Dark);
        } else {
            setTheme(R.style.AppTheme);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library);
        container = (LinearLayout) findViewById(R.id.library_list_container);
        emptyText = (TextView) findViewById(R.id.library_list_empty);
        libraryCard = findViewById(R.id.library_list_card);
        Prefs.applyCard(libraryCard, this);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        List<DownloadIndex.Entry> entries = DownloadIndex.read(this);
        container.removeAllViews();
        emptyText.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
        for (int i = 0; i < entries.size(); i++) {
            final DownloadIndex.Entry e = entries.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 12, 0, 12);
            TextView name = new TextView(this);
            name.setText(e.name + "  ·  " + DownloadIndex.formatSize(e.sizeBytes));
            name.setTextSize(14);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            name.setLayoutParams(lp);
            name.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openEntry(e);
                }
            });
            Button more = new Button(this);
            more.setText("⋮");
            more.setTextSize(18);
            more.setAllCaps(false);
            more.setBackgroundResource(android.R.color.transparent);
            more.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AnimHelper.press(v);
                    showRowMenu(v, e);
                }
            });
            row.addView(name);
            row.addView(more);
            container.addView(row);
        }
        AnimHelper.fadeIn(container);
    }

    private void showRowMenu(View anchor, final DownloadIndex.Entry e) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(getString(R.string.menu_play));
        menu.getMenu().add(getString(R.string.menu_share));
        menu.getMenu().add(getString(R.string.menu_delete));
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                String title = item.getTitle().toString();
                if (title.equals(getString(R.string.menu_play))) {
                    openEntry(e);
                } else if (title.equals(getString(R.string.menu_share))) {
                    shareEntry(e);
                } else if (title.equals(getString(R.string.menu_delete))) {
                    confirmDelete(e);
                }
                return true;
            }
        });
        menu.show();
    }

    private void openEntry(DownloadIndex.Entry e) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(e.uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception ex) {
            Toast.makeText(this,
                    getString(R.string.open_failed) + " " + e.name,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void shareEntry(DownloadIndex.Entry e) {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_STREAM, Uri.parse(e.uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            String lower = e.name.toLowerCase();
            intent.setType(lower.endsWith(".mp3") || lower.endsWith(".m4a")
                    ? "audio/*" : "video/*");
            startActivity(Intent.createChooser(intent,
                    getString(R.string.share_chooser)));
        } catch (Exception ex) {
            Toast.makeText(this,
                    getString(R.string.open_failed) + " " + e.name,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void confirmDelete(final DownloadIndex.Entry e) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_confirm_title))
                .setMessage(e.name + "\n\n"
                        + getString(R.string.delete_confirm_msg))
                .setPositiveButton(getString(R.string.menu_delete),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                deleteEntry(e);
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void deleteEntry(DownloadIndex.Entry e) {
        boolean fileDeleted = false;
        try {
            int rows = getContentResolver().delete(
                    Uri.parse(e.uri), null, null);
            fileDeleted = rows > 0;
        } catch (Exception ignored) {
            // MediaStore delete can be refused on some devices;
            // the index removal below still unlists the file.
        }
        DownloadIndex.remove(this, e);
        Toast.makeText(this,
                fileDeleted ? getString(R.string.delete_done)
                        : getString(R.string.delete_failed),
                Toast.LENGTH_SHORT).show();
        refresh();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
