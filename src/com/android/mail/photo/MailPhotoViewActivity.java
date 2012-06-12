package com.android.mail.photo;

import android.app.ActionBar;
import android.database.Cursor;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.android.mail.R;
import com.android.mail.providers.UIProvider.AttachmentColumns;
import com.android.mail.utils.AttachmentUtils;

public class MailPhotoViewActivity extends PhotoViewActivity {

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.photo_view_menu, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // app icon in action bar clicked; go back to conversation
                finish();
                return true;
            case R.id.menu_save:
                // do stuff
                return true;
            case R.id.menu_save_all:
                // do stuff
                return true;
            case R.id.menu_share:
                // do stuff
                return true;
            case R.id.menu_share_all:
                // do stuff
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Adjusts the activity title and subtitle to reflect the image name and size.
     */
    @Override
    protected void updateTitleAndSubtitle() {
        super.updateTitleAndSubtitle();

        Cursor cursor = getCursorAtProperPosition();

        if (cursor == null) {
            return;
        }

        final String subtitle = AttachmentUtils.convertToHumanReadableSize(this,
                cursor.getInt(cursor.getColumnIndex(AttachmentColumns.SIZE)));
        final ActionBar actionBar = getActionBar();

        actionBar.setSubtitle(subtitle);

    }
}
