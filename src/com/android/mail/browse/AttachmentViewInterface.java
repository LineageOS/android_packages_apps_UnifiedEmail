package com.android.mail.browse;

public interface AttachmentViewInterface {

    /**
     * View an attachment. The different attachment types handle this
     * action differently and so each view handles it in their
     * own manner.
     */
    public void viewAttachment();

    /**
     * Allows the view to know when it should update its progress.
     * @param showProgress true if the the view should show a determinate
     * progress value
     */
    public void updateProgress(boolean showDeterminateProgress);

    /**
     * Allows the view to do some view-specific status updating.
     * Called in {@link AttachmentActionHandler#updateStatus}.
     */
    public void onUpdateStatus();
}
