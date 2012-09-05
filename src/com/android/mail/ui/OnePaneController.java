/*******************************************************************************
 *      Copyright (C) 2012 Google Inc.
 *      Licensed to The Android Open Source Project.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *******************************************************************************/

package com.android.mail.ui;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.LoaderManager.LoaderCallbacks;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import com.android.mail.ConversationListContext;
import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.Settings;
import com.android.mail.providers.UIProvider;
import com.android.mail.utils.LogUtils;

/**
 * Controller for one-pane Mail activity. One Pane is used for phones, where screen real estate is
 * limited. This controller also does the layout, since the layout is simpler in the one pane case.
 */

// Called OnePaneActivityController in Gmail.
public final class OnePaneController extends AbstractActivityController {
    /** Key used to store {@link #mLastFolderListTransactionId}. */
    private static final String FOLDER_LIST_TRANSACTION_KEY = "folder-list-transaction";
    /** Key used to store {@link #mLastInboxConversationListTransactionId} */
    private static final String INBOX_CONVERSATION_LIST_TRANSACTION_KEY =
            "inbox_conversation-list-transaction";
    /** Key used to store {@link #mLastConversationListTransactionId} */
    private static final String CONVERSATION_LIST_TRANSACTION_KEY = "conversation-list-transaction";
    /** Key used to store {@link #mLastConversationTransactionId}. */
    private static final String CONVERSATION_TRANSACTION_KEY = "conversation-transaction";
    /** Key used to store {@link #mConversationListVisible}. */
    private static final String CONVERSATION_LIST_VISIBLE_KEY = "conversation-list-visible";
    /** Key used to store {@link #mConversationListNeverShown}. */
    private static final String CONVERSATION_LIST_NEVER_SHOWN_KEY = "conversation-list-never-shown";
    /** Key to store {@link #mInbox}. */
    private final static String SAVED_INBOX_KEY = "m-inbox";

    private static final int INVALID_ID = -1;
    private boolean mConversationListVisible = false;
    private int mLastInboxConversationListTransactionId = INVALID_ID;
    private int mLastConversationListTransactionId = INVALID_ID;
    private int mLastConversationTransactionId = INVALID_ID;
    private int mLastFolderListTransactionId = INVALID_ID;
    private Folder mInbox;
    /** Whether a conversation list for this account has ever been shown.*/
    private boolean mConversationListNeverShown = true;

    /**
     * @param activity
     * @param viewMode
     */
    public OnePaneController(MailActivity activity, ViewMode viewMode) {
        super(activity, viewMode);
    }

    @Override
    public void onRestoreInstanceState(Bundle inState) {
        super.onRestoreInstanceState(inState);
        // TODO(mindyp) handle saved state.
        if (inState == null) {
            return;
        }
        mLastFolderListTransactionId = inState.getInt(FOLDER_LIST_TRANSACTION_KEY, INVALID_ID);
        mLastInboxConversationListTransactionId =
                inState.getInt(INBOX_CONVERSATION_LIST_TRANSACTION_KEY, INVALID_ID);
        mLastConversationListTransactionId =
                inState.getInt(CONVERSATION_LIST_TRANSACTION_KEY, INVALID_ID);
        mLastConversationTransactionId = inState.getInt(CONVERSATION_TRANSACTION_KEY, INVALID_ID);
        mConversationListVisible = inState.getBoolean(CONVERSATION_LIST_VISIBLE_KEY);
        mConversationListNeverShown = inState.getBoolean(CONVERSATION_LIST_NEVER_SHOWN_KEY);
        mInbox = inState.getParcelable(SAVED_INBOX_KEY);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // TODO(mindyp) handle saved state.
        outState.putInt(FOLDER_LIST_TRANSACTION_KEY, mLastFolderListTransactionId);
        outState.putInt(INBOX_CONVERSATION_LIST_TRANSACTION_KEY,
                mLastInboxConversationListTransactionId);
        outState.putInt(CONVERSATION_LIST_TRANSACTION_KEY, mLastConversationListTransactionId);
        outState.putInt(CONVERSATION_TRANSACTION_KEY, mLastConversationTransactionId);
        outState.putBoolean(CONVERSATION_LIST_VISIBLE_KEY, mConversationListVisible);
        outState.putBoolean(CONVERSATION_LIST_NEVER_SHOWN_KEY, mConversationListNeverShown);
        outState.putParcelable(SAVED_INBOX_KEY, mInbox);
    }

    @Override
    public void resetActionBarIcon() {
        final int mode = mViewMode.getMode();
        // If the viewmode is not set, preserve existing icon.
        if (mode == ViewMode.UNKNOWN) {
            return;
        }
        if (!inInbox(mAccount, mConvListContext)
                || mode == ViewMode.SEARCH_RESULTS_LIST
                || mode == ViewMode.SEARCH_RESULTS_CONVERSATION
                || mode == ViewMode.CONVERSATION
                || mode == ViewMode.FOLDER_LIST) {
            mActionBarView.setBackButton();
        } else {
            mActionBarView.removeBackButton();
        }
    }

    /**
     * Returns true if the user is currently in the conversation list view, viewing the default
     * inbox.
     * @return
     */
    private static boolean inInbox(final Account account, final ConversationListContext context) {
        // If we don't have valid state, then we are not in the inbox.
        if (account == null || context == null || context.folder == null
                || account.settings == null) {
            return false;
        }
        final Uri inboxUri = Settings.getDefaultInboxUri(account.settings);
        return !ConversationListContext.isSearchResult(context)
                && context.folder.uri.equals(inboxUri);
    }

    @Override
    public void onAccountChanged(Account account) {
        super.onAccountChanged(account);
        mConversationListNeverShown = true;
    }

    @Override
    public boolean onCreate(Bundle savedInstanceState) {
        mActivity.setContentView(R.layout.one_pane_activity);
        // The parent class sets the correct viewmode and starts the application off.
        return super.onCreate(savedInstanceState);
    }

    @Override
    protected boolean isConversationListVisible() {
        return mConversationListVisible;
    }

    @Override
    public void onViewModeChanged(int newMode) {
        super.onViewModeChanged(newMode);

        // When entering conversation list mode, hide and clean up any currently visible
        // conversation.
        // TODO: improve this transition
        if (newMode == ViewMode.CONVERSATION_LIST || newMode == ViewMode.SEARCH_RESULTS_LIST) {
            mPagerController.hide();
        }
    }

    @Override
    public void showConversationList(ConversationListContext listContext) {
        super.showConversationList(listContext);
        enableCabMode();
        // TODO(viki): Check if the account has been changed since the previous
        // time.
        if (ConversationListContext.isSearchResult(listContext)) {
            mViewMode.enterSearchResultsListMode();
        } else {
            mViewMode.enterConversationListMode();
        }
        // TODO(viki): This account transition looks strange in two pane mode.
        // Revisit as the app is coming together and improve the look and feel.
        final int transition = mConversationListNeverShown
                ? FragmentTransaction.TRANSIT_FRAGMENT_FADE
                : FragmentTransaction.TRANSIT_FRAGMENT_OPEN;
        Fragment conversationListFragment = ConversationListFragment.newInstance(listContext);

        if (!inInbox(mAccount, mConvListContext)) {
            // Maintain fragment transaction history so we can get back to the
            // fragment used to launch this list.
            mLastConversationListTransactionId = replaceFragment(conversationListFragment,
                    transition, TAG_CONVERSATION_LIST);
        } else {
            // If going to the inbox, clear the folder list transaction history.
            mInbox = listContext.folder;
            mLastInboxConversationListTransactionId = replaceFragment(conversationListFragment,
                    transition, TAG_CONVERSATION_LIST);
            mLastFolderListTransactionId = INVALID_ID;

            // If we ever to to the inbox, we want to unset the transation id for any other
            // non-inbox folder.
            mLastConversationListTransactionId = INVALID_ID;
        }
        mConversationListVisible = true;
        onConversationVisibilityChanged(false);
        onConversationListVisibilityChanged(true);
        mConversationListNeverShown = false;
    }

    @Override
    protected void showConversation(Conversation conversation, boolean inLoaderCallbacks) {
        super.showConversation(conversation, inLoaderCallbacks);
        if (conversation == null) {
            // This is a request to remove the conversation view, and pop back the view stack.
            // If we are in conversation list view already, this should be a safe thing to do, so
            // we don't check viewmode.
            transitionBackToConversationListMode(inLoaderCallbacks);
            return;
        }
        disableCabMode();
        if (ConversationListContext.isSearchResult(mConvListContext)) {
            mViewMode.enterSearchResultsConversationMode();
        } else {
            mViewMode.enterConversationMode();
        }

        // Switching to conversation view is an incongruous transition: we are not replacing a
        // fragment with another fragment as usual. Instead, reveal the heretofore inert
        // conversation ViewPager and just remove the previously visible fragment
        // (e.g. conversation list, or possibly label list?).
        final FragmentManager fm = mActivity.getFragmentManager();
        final Fragment f = fm.findFragmentById(R.id.content_pane);
        if (f != null) {
            final FragmentTransaction ft = fm.beginTransaction();
            ft.addToBackStack(null);
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            ft.remove(f);
            ft.commitAllowingStateLoss();
        }

        // TODO: improve this transition
        mPagerController.show(mAccount, mFolder, conversation);
        onConversationVisibilityChanged(true);
        resetActionBarIcon();

        mConversationListVisible = false;
        onConversationListVisibilityChanged(false);
    }

    @Override
    public void showWaitForInitialization() {
        super.showWaitForInitialization();

        replaceFragment(WaitFragment.newInstance(mAccount),
                FragmentTransaction.TRANSIT_FRAGMENT_OPEN, TAG_WAIT);
    }

    @Override
    public void hideWaitForInitialization() {
        transitionToInbox();
    }

    @Override
    public void showFolderList() {
        if (mAccount == null) {
            LogUtils.e(LOG_TAG, "Null account in showFolderList");
            return;
        }
        // Null out the currently selected folder; we have nothing selected the
        // first time the user enters the folder list
        setHierarchyFolder(null);
        mViewMode.enterFolderListMode();
        enableCabMode();
        mLastFolderListTransactionId = replaceFragment(
                FolderListFragment.newInstance(null, mAccount.folderListUri, false),
                FragmentTransaction.TRANSIT_FRAGMENT_OPEN, TAG_FOLDER_LIST);
        mConversationListVisible = false;
        onConversationVisibilityChanged(false);
        onConversationListVisibilityChanged(false);
    }

    /**
     * Replace the content_pane with the fragment specified here. The tag is specified so that
     * the {@link ActivityController} can look up the fragments through the
     * {@link android.app.FragmentManager}.
     * @param fragment
     * @param transition
     * @param tag
     * @return transaction ID returned when the transition is committed.
     */
    private int replaceFragment(Fragment fragment, int transition, String tag) {
        FragmentTransaction fragmentTransaction = mActivity.getFragmentManager().beginTransaction();
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.setTransition(transition);
        fragmentTransaction.replace(R.id.content_pane, fragment, tag);
        final int transactionId = fragmentTransaction.commitAllowingStateLoss();
        resetActionBarIcon();
        return transactionId;
    }

    /**
     * Back works as follows:
     * 1) If the user is in the folder list view, go back
     * to the account default inbox.
     * 2) If the user is in a conversation list
     * that is not the inbox AND:
     *  a) they got there by going through the folder
     *  list view, go back to the folder list view.
     *  b) they got there by using some other means (account dropdown), go back to the inbox.
     * 3) If the user is in a conversation, go back to the conversation list they were last in.
     * 4) If the user is in the conversation list for the default account inbox,
     * back exits the app.
     */
    @Override
    public boolean onBackPressed() {
        final int mode = mViewMode.getMode();
        if (mode == ViewMode.FOLDER_LIST) {
            final Folder hierarchyFolder = getHierarchyFolder();
            final FolderListFragment folderListFragment = getFolderListFragment();
            if (folderListFragment != null &&
                    folderListFragment.showingHierarchy() && hierarchyFolder != null) {
                // If we are showing the folder list and the user is exploring
                // the children of a single parent folder,
                // back should display the parent folder's parent and siblings.
                goUpFolderHierarchy(hierarchyFolder);
            } else {
                // We are at the topmost list of folders: go back
                mLastFolderListTransactionId = INVALID_ID;
                transitionToInbox();
            }
        } else if (mode == ViewMode.SEARCH_RESULTS_LIST) {
            mActivity.finish();
        } else if (mViewMode.isListMode() && !inInbox(mAccount, mConvListContext)) {
            if (mLastFolderListTransactionId != INVALID_ID) {
                // If the user got here by navigating via the folder list, back
                // should bring them back to the folder list.
                mViewMode.enterFolderListMode();
                mActivity.getFragmentManager().popBackStack(mLastFolderListTransactionId, 0);
            } else {
                transitionToInbox();
            }
        } else if (mode == ViewMode.CONVERSATION || mode == ViewMode.SEARCH_RESULTS_CONVERSATION) {
            transitionBackToConversationListMode(false /* inLoaderCallbacks */);
        } else {
            mActivity.finish();
        }
        mToastBar.hide(false);
        return true;
    }

    private void goUpFolderHierarchy(Folder current) {
        Folder top = current.parent;
        if (top != null) {
            setHierarchyFolder(top);
            // Replace this fragment with a new FolderListFragment
            // showing this folder's children if we are not already
            // looking at the child view for this folder.
            mLastFolderListTransactionId = replaceFragment(FolderListFragment.newInstance(
                    top, top.childFoldersListUri, false),
                    FragmentTransaction.TRANSIT_FRAGMENT_OPEN, TAG_FOLDER_LIST);
            // Show the up affordance when digging into child folders.
            mActionBarView.setBackButton();
        } else {
            // Otherwise, clear the selected folder and go back to whatever the
            // last folder list displayed was.
            showFolderList();
        }
    }

    /**
     * Switch to the Inbox by creating a new conversation list context that loads the inbox.
     */
    private void transitionToInbox() {
        mViewMode.enterConversationListMode();
        if (mInbox == null) {
            loadAccountInbox();
        } else {
            final ConversationListContext listContext =
                    ConversationListContext.forFolder(mAccount, mInbox);
            // Set the correct context for what the conversation view will be now.
            onFolderChanged(mInbox);
            showConversationList(listContext);
        }
    }

    @Override
    public void onFolderSelected(Folder folder) {
        if (folder.hasChildren && !folder.equals(getHierarchyFolder())) {
            mViewMode.enterFolderListMode();
            setHierarchyFolder(folder);
            // Replace this fragment with a new FolderListFragment
            // showing this folder's children if we are not already
            // looking at the child view for this folder.
            mLastFolderListTransactionId = replaceFragment(
                    FolderListFragment.newInstance(folder, folder.childFoldersListUri, false),
                    FragmentTransaction.TRANSIT_FRAGMENT_OPEN, TAG_FOLDER_LIST);
            // Show the up affordance when digging into child folders.
            mActionBarView.setBackButton();
        } else {
            super.onFolderSelected(folder);
        }
    }

    private boolean isTransactionIdValid(int id) {
        return id >= 0;
    }

    /**
     * Up works as follows:
     * 1) If the user is in a conversation list that is not the default account inbox,
     * a conversation, or the folder list, up follows the rules of back.
     * 2) If the user is in search results, up exits search
     * mode and returns the user to whatever view they were in when they began search.
     * 3) If the user is in the inbox, there is no up.
     */
    @Override
    public boolean onUpPressed() {
        final int mode = mViewMode.getMode();
        if (mode == ViewMode.SEARCH_RESULTS_LIST) {
            mActivity.finish();
        } else if ((!inInbox(mAccount, mConvListContext) && mViewMode.isListMode())
                || mode == ViewMode.CONVERSATION
                || mode == ViewMode.FOLDER_LIST
                || mode == ViewMode.SEARCH_RESULTS_CONVERSATION) {
            // Same as go back.
            mActivity.onBackPressed();
        }
        return true;
    }

    private void transitionBackToConversationListMode(boolean inLoaderCallbacks) {
        final int mode = mViewMode.getMode();
        enableCabMode();
        if (mode == ViewMode.SEARCH_RESULTS_CONVERSATION) {
            mViewMode.enterSearchResultsListMode();
        } else {
            mViewMode.enterConversationListMode();
        }
        if (isTransactionIdValid(mLastConversationListTransactionId)) {
            safelyPopBackStack(mLastConversationListTransactionId, inLoaderCallbacks);
        } else if (isTransactionIdValid(mLastInboxConversationListTransactionId)) {
            safelyPopBackStack(mLastInboxConversationListTransactionId, inLoaderCallbacks);
            onFolderChanged(mInbox);
        } else {
            // TODO: revisit if this block is necessary
            final ConversationListContext listContext = ConversationListContext.forFolder(
                    mAccount, mInbox);
            // Set the correct context for what the conversation view will be now.
            onFolderChanged(mInbox);
            showConversationList(listContext);
        }
        resetActionBarIcon();

        mConversationListVisible = true;
        onConversationVisibilityChanged(false);
        onConversationListVisibilityChanged(true);
    }

    /**
     * Pop to a specified point in the fragment back stack without causing IllegalStateExceptions
     * from committing a fragment transaction "at the wrong time".
     * <p>
     * If the caller specifies that we are in
     * the scope of an {@link LoaderCallbacks#onLoadFinished(android.content.Loader, Object)},
     * this method will pop back in a Handler. The deferred job will also check that the Activity
     * is in a valid state for fragment transactions, using {@link #safeToModifyFragments()}.
     * Otherwise, this method will pop back immediately if safe. Finally, if we are not in
     * onLoadFinished and it's not safe, this method will just ignore the request.
     *
     * @param transactionId back stack destination to pop to
     * @param inLoaderCallbacks whether we are in the scope of an onLoadFinished (when fragment
     * transactions are disallowed)
     */
    private void safelyPopBackStack(int transactionId, boolean inLoaderCallbacks) {
        final PopBackStackRunnable r = new PopBackStackRunnable(transactionId);
        if (inLoaderCallbacks) {
            // always run deferred. ensure deferred job checks safety.
            mHandler.post(r);
        } else if (safeToModifyFragments()) {
            // run now
            r.popBackStack();
        } else {
            // ignore
            LogUtils.i(LOG_TAG, "Activity has been saved; ignoring unsafe immediate request"
                    + " to pop back stack");
        }
    }

    @Override
    public boolean shouldShowFirstConversation() {
        return false;
    }

    @Override
    public void onUndoAvailable(ToastBarOperation op) {
        if (op != null && mAccount.supportsCapability(UIProvider.AccountCapabilities.UNDO)) {
            final int mode = mViewMode.getMode();
            final ConversationListFragment convList = getConversationListFragment();
            switch (mode) {
                case ViewMode.SEARCH_RESULTS_CONVERSATION:
                case ViewMode.CONVERSATION:
                    mToastBar.setConversationMode(true);
                    mToastBar.show(
                            getUndoClickedListener(
                                    convList != null ? convList.getAnimatedAdapter() : null),
                            0,
                            Html.fromHtml(op.getDescription(mActivity.getActivityContext(),
                                    mFolder)),
                            true, /* showActionIcon */
                            R.string.undo,
                            true,  /* replaceVisibleToast */
                            op);
                    break;
                case ViewMode.SEARCH_RESULTS_LIST:
                case ViewMode.CONVERSATION_LIST:
                    if (convList != null) {
                        mToastBar.setConversationMode(false);
                        mToastBar.show(
                                getUndoClickedListener(convList.getAnimatedAdapter()),
                                0,
                                Html.fromHtml(op.getDescription(mActivity.getActivityContext(),
                                        mFolder)),
                                true, /* showActionIcon */
                                R.string.undo,
                                true,  /* replaceVisibleToast */
                                op);
                    } else {
                        mActivity.setPendingToastOperation(op);
                    }
                    break;
            }
        }
    }

    @Override
    public void onError(final Folder folder, boolean replaceVisibleToast) {
        final int mode = mViewMode.getMode();
        switch (mode) {
            case ViewMode.SEARCH_RESULTS_LIST:
            case ViewMode.CONVERSATION_LIST:
                showErrorToast(folder, replaceVisibleToast);
                break;
            default:
                break;
        }
    }

    @Override
    public String getHelpContext() {
        final int mode = mViewMode.getMode();
        switch (mode) {
            case ViewMode.FOLDER_LIST:
                return mContext.getString(R.string.one_pane_folder_list_help_context);
        }
        return super.getHelpContext();
    }

    private final class PopBackStackRunnable implements Runnable {

        private final int mTransactionId;

        public PopBackStackRunnable(int transactionId) {
            mTransactionId = transactionId;
        }

        public void popBackStack() {
            mActivity.getFragmentManager().popBackStack(mTransactionId, 0);
        }

        @Override
        public void run() {
            if (safeToModifyFragments()) {
                popBackStack();
            } else {
                LogUtils.i(LOG_TAG, "Activity has been saved; ignoring unsafe deferred request"
                        + " to pop back stack");
            }
        }
    }

}
