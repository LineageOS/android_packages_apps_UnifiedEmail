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
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.android.mail.ConversationListContext;
import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;

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
    private DrawerLayout mDrawerContainer;
    private ViewGroup mDrawerPullout;

    /** Icon changing hack - Maintain pointer to the Up view/drawable and the burger drawable */
    private ImageView mUp;
    private Drawable mUpDrawable;
    private Drawable mBurgerDrawable;

    public OnePaneController(MailActivity activity, ViewMode viewMode) {
        super(activity, viewMode);
    }

    @Override
    public void onRestoreInstanceState(Bundle inState) {
        super.onRestoreInstanceState(inState);
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
        // Calling resetActionBarIcon should never remove the up affordance
        // even when waiting for sync (Folder list should still show with one
        // account. Currently this method is blank to avoid any changes.
    }

    /**
     * Returns true if the candidate URI is the URI for the default inbox for the given account.
     * @param candidate the URI to check
     * @param account the account whose default Inbox the candidate might be
     * @return true if the candidate is indeed the default inbox for the given account.
     */
    private static boolean isDefaultInbox(Uri candidate, Account account) {
        return (candidate != null && account != null)
                && candidate.equals(account.settings.defaultInbox);
    }

    /**
     * Returns true if the user is currently in the conversation list view, viewing the default
     * inbox.
     * @return true if user is in conversation list mode, viewing the default inbox.
     */
    private static boolean inInbox(final Account account, final ConversationListContext context) {
        // If we don't have valid state, then we are not in the inbox.
        return !(account == null || context == null || context.folder == null
                || account.settings == null) && !ConversationListContext.isSearchResult(context)
                && isDefaultInbox(context.folder.uri, account);
    }

    /**
     * On account change, carry out super implementation, load FolderListFragment
     * into drawer (to avoid repetitive calls to replaceFragment).
     */
    @Override
    public void changeAccount(Account account) {
        super.changeAccount(account);
        mConversationListNeverShown = true;
        closeDrawerIfOpen();
    }

    @Override
    public boolean onCreate(Bundle savedInstanceState) {
        mActivity.setContentView(R.layout.one_pane_activity);
        mDrawerContainer = (DrawerLayout) mActivity.findViewById(R.id.drawer_container);
        mDrawerContainer.setScrimColor(
                mContext.getResources().getColor(R.color.drawer_background_dim));
        mDrawerPullout = (ViewGroup) mDrawerContainer.findViewById(R.id.drawer_pullout);
        configureUpDrawable();
        // The parent class sets the correct viewmode and starts the application off.
        return super.onCreate(savedInstanceState);
    }

    /**
     * Hack for configuring and saving pointers to the up arrow when required to change from Up to
     * Burger and vice-versa.
     *
     * NOTE: The hack takes in account that the OEM has not modified the home button's children.
     * If modified, there's a risk of modifying something that shouldn't be.
     *
     * TODO(shahrk): Make icon changing safer through framework or remove it?
     */
    private void configureUpDrawable() {
        final View home = mActivity.findViewById(android.R.id.home);
        if (home == null) {
            LogUtils.w(LOG_TAG,
                    "OnePaneController.configureUpDrawable(): Action bar home was not discovered");
            // Action bar doesn't have a known configuration, an OEM modified home completely
            return;
        }

        final ViewGroup parent = (ViewGroup) home.getParent();
        final int childCount = parent.getChildCount();
        if (childCount != 2) {
            // No idea which child will be the right ImageView for 'up', an OEM has modified home
            LogUtils.w(LOG_TAG, "OnePaneController.configureUpDrawable(): "
                    + "Action bar has incorrect number of children: %d expected 2", childCount);
            return;
        }

        final View first = parent.getChildAt(0);
        final View second = parent.getChildAt(1);
        // Get the view that's NOT android.R.id.home
        final View up = first.getId() == android.R.id.home ? second : first;
        if (up instanceof ImageView) {
            // We've most likely discovered the correct ImageView for the up arrow. Save the
            // drawable/reference to view and also load the burger drawable for drawer indication.
            mUp = (ImageView) up;
            mUpDrawable = mUp.getDrawable();
            mBurgerDrawable = mContext.getResources().getDrawable(R.drawable.ic_drawer_glyph);
        } else {
            LogUtils.w(LOG_TAG,
                    "OnePaneController.configureUpDrawable(): Up arrow was not of type ImageView");
        }
    }

    /**
     * Changes the up graphic (on left of gmail icon) to either be 'Up' or the 'Burger'.
     *
     * @param changeToBurger true if icon should be 'burger', false if icon should be 'up'
     */
    private void changeUpArrow(final boolean changeToBurger) {
        if(mUp != null) {
            if (changeToBurger) {
                mUp.setImageDrawable(mBurgerDrawable);
            } else {
                mUp.setImageDrawable(mUpDrawable);
            }
        }
    }

    @Override
    protected boolean isConversationListVisible() {
        return mConversationListVisible;
    }

    /**
     * If drawer is open/visible (even partially), close it.
     */
    private void closeDrawerIfOpen() {
        if(mDrawerContainer.isDrawerOpen(mDrawerPullout)) {
            mDrawerContainer.closeDrawers();
        }
    }

    @Override
    public void onViewModeChanged(int newMode) {
        super.onViewModeChanged(newMode);

        // When view mode changes, lock drawer if viewing search results or
        // viewing a conversation. Set unlocked otherwise.
        if (ViewMode.isSearchMode(newMode) || ViewMode.isConversationMode(newMode)) {
            changeUpArrow(false);
            mDrawerContainer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        } else {
            changeUpArrow(true);
            mDrawerContainer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        }
        closeDrawerIfOpen();

        // When entering conversation list mode, hide and clean up any currently visible
        // conversation.
        if (ViewMode.isListMode(newMode)) {
            mPagerController.hide(true /* changeVisibility */);
        }
        // When we step away from the conversation mode, we don't have a current conversation
        // anymore. Let's blank it out so clients calling getCurrentConversation are not misled.
        if (!ViewMode.isConversationMode(newMode)) {
            setCurrentConversation(null);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(super.toString());
        sb.append("{lastFolderListTransId=");
        sb.append(mLastFolderListTransactionId);
        sb.append(" lastInboxTransId=");
        sb.append(mLastInboxConversationListTransactionId);
        sb.append(" lastConvListTransId=");
        sb.append(mLastConversationListTransactionId);
        sb.append("}");
        return sb.toString();
    }

    @Override
    public void showConversationList(ConversationListContext listContext) {
        super.showConversationList(listContext);
        enableCabMode();
        if (ConversationListContext.isSearchResult(listContext)) {
            mViewMode.enterSearchResultsListMode();
        } else {
            mViewMode.enterConversationListMode();
        }
        final int transition = mConversationListNeverShown
                ? FragmentTransaction.TRANSIT_FRAGMENT_FADE
                : FragmentTransaction.TRANSIT_FRAGMENT_OPEN;
        Fragment conversationListFragment = ConversationListFragment.newInstance(listContext);

        if (!inInbox(mAccount, listContext)) {
            // Maintain fragment transaction history so we can get back to the
            // fragment used to launch this list.
            mLastConversationListTransactionId = replaceFragmentWithBack(conversationListFragment,
                    transition, TAG_CONVERSATION_LIST, R.id.content_pane);
        } else {
            // If going to the inbox, clear the folder list transaction history.
            mInbox = listContext.folder;
            mLastInboxConversationListTransactionId = replaceFragmentWithBack(
                    conversationListFragment, transition, TAG_CONVERSATION_LIST, R.id.content_pane);

            // FIXME: "forgetting" the folder list entry in the back stack doesn't remove it from
            // the back stack. only popping past it can remove it.
            // Maybe we should clear the back stack prior to opening a folder.
            mLastFolderListTransactionId = INVALID_ID;

            // If we ever to to the inbox, we want to unset the transation id for any other
            // non-inbox folder.
            mLastConversationListTransactionId = INVALID_ID;
        }

        mActivity.getFragmentManager().executePendingTransactions();

        mConversationListVisible = true;
        onConversationVisibilityChanged(false);
        onConversationListVisibilityChanged(true);
        mConversationListNeverShown = false;
    }

    @Override
    protected void showConversation(Conversation conversation, boolean inLoaderCallbacks) {
        super.showConversation(conversation, inLoaderCallbacks);
        if (conversation == null) {
            transitionBackToConversationListMode(inLoaderCallbacks);
            return;
        }
        disableCabMode();
        if (ConversationListContext.isSearchResult(mConvListContext)) {
            mViewMode.enterSearchResultsConversationMode();
        } else {
            mViewMode.enterConversationMode();
        }
        final FragmentManager fm = mActivity.getFragmentManager();
        final FragmentTransaction ft = fm.beginTransaction();
        ft.addToBackStack(null);
        // Switching to conversation view is an incongruous transition:
        // we are not replacing a fragment with another fragment as
        // usual. Instead, reveal the heretofore inert conversation
        // ViewPager and just remove the previously visible fragment
        // e.g. conversation list, or possibly label list?).
        final Fragment f = fm.findFragmentById(R.id.content_pane);
        // FragmentManager#findFragmentById can return fragments that are not added to the activity.
        // We want to make sure that we don't attempt to remove fragments that are not added to the
        // activity, as when the transaction is popped off, the FragmentManager will attempt to
        // readd the same fragment twice
        if (f != null && f.isAdded()) {
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            ft.remove(f);
            ft.commitAllowingStateLoss();
            fm.executePendingTransactions();
        }
        mPagerController.show(mAccount, mFolder, conversation, true /* changeVisibility */);
        onConversationVisibilityChanged(true);
        mConversationListVisible = false;
        onConversationListVisibilityChanged(false);
    }

    @Override
    public void showWaitForInitialization() {
        super.showWaitForInitialization();
        replaceFragment(getWaitFragment(), FragmentTransaction.TRANSIT_FRAGMENT_OPEN, TAG_WAIT,
                R.id.content_pane);
    }

    @Override
    protected void hideWaitForInitialization() {
        transitionToInbox();
        super.hideWaitForInitialization();
    }

    @Override
    public boolean doesActionChangeConversationListVisibility(int action) {
        switch (action) {
            case R.id.archive:
            case R.id.remove_folder:
            case R.id.delete:
            case R.id.discard_drafts:
            case R.id.mark_important:
            case R.id.mark_not_important:
            case R.id.mute:
            case R.id.report_spam:
            case R.id.mark_not_spam:
            case R.id.report_phishing:
            case R.id.refresh:
            case R.id.change_folder:
                return false;
            default:
                return true;
        }
    }

    /**
     * Toggles the drawer pullout. If it was open (Fully extended), the
     * drawer will be closed. Otherwise, the drawer will be opened. This should
     * only be called when used with a toggle item. Other cases should be handled
     * explicitly with just closeDrawers() or openDrawer(View drawerView);
     */
    @Override
    protected void toggleFolderListState() {
        if(mDrawerContainer.isDrawerOpen(mDrawerPullout)) {
            mDrawerContainer.closeDrawers();
        } else {
            mDrawerContainer.openDrawer(mDrawerPullout);
        }
    }

    @Override
    public void onFolderChanged(Folder folder) {
        mDrawerContainer.closeDrawers();
        super.onFolderChanged(folder);
    }

    /**
     * Replace the content_pane with the fragment specified here. The tag is specified so that
     * the {@link ActivityController} can look up the fragments through the
     * {@link android.app.FragmentManager}. This action will be placed on the back stack.
     * @param fragment the new fragment to put
     * @param transition the transition to show
     * @param tag a tag for the fragment manager.
     * @param anchor ID of view to replace fragment in
     * @return transaction ID returned when the transition is committed.
     */
    private int replaceFragmentWithBack(Fragment fragment, int transition, String tag, int anchor) {
        return replaceFragment(fragment, transition, tag, anchor, true);
    }

    // (Not on the back stack -> no transaction ID to return.)
    private void replaceFragment(Fragment fragment, int transition, String tag, int anchor) {
        replaceFragment(fragment, transition, tag, anchor, false);
    }

    private int replaceFragment(Fragment fragment, int transition, String tag, int anchor,
            boolean addToBackStack) {
        final FragmentManager fm = mActivity.getFragmentManager();
        FragmentTransaction fragmentTransaction = fm.beginTransaction();
        fragmentTransaction.setTransition(transition);
        if (addToBackStack) {
            fragmentTransaction.addToBackStack(null);
        }
        fragmentTransaction.replace(anchor, fragment, tag);
        final int id = fragmentTransaction.commitAllowingStateLoss();
        fm.executePendingTransactions();
        return id;
    }

    /**
     * Back works as follows:
     * 1) If the drawer is pulled out (Or mid-drag), close it - handled.
     * 2) If the user is in the folder list view, go back
     * to the account default inbox.
     * 3) If the user is in a conversation list
     * that is not the inbox AND:
     *  a) they got there by going through the folder
     *  list view, go back to the folder list view.
     *  b) they got there by using some other means (account dropdown), go back to the inbox.
     * 4) If the user is in a conversation, go back to the conversation list they were last in.
     * 5) If the user is in the conversation list for the default account inbox,
     * back exits the app.
     */
    @Override
    public boolean handleBackPress() {
        final int mode = mViewMode.getMode();

        if (mDrawerContainer.isDrawerVisible(mDrawerPullout)) {
            mDrawerContainer.closeDrawers();
            return true;
        }

        //TODO(shahrk): Remove the folder list standalone view
        if (mode == ViewMode.FOLDER_LIST) {
            final Folder hierarchyFolder = getHierarchyFolder();
            final FolderListFragment folderListFragment = getFolderListFragment();
            final boolean parentHasChildren = folderListFragment != null &&
                    folderListFragment.showingHierarchy() && hierarchyFolder != null
                    && hierarchyFolder.parent != null;
            if (parentHasChildren) {
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
        } else if (mViewMode.isConversationMode()) {
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
            // FIXME: This is silly. we worked so hard to add folder fragments to the back stack.
            // it should either just pop back, or should not use the back stack at all.

            setHierarchyFolder(top);
            // Replace this fragment with a new FolderListFragment
            // showing this folder's children if we are not already
            // looking at the child view for this folder.
            mLastFolderListTransactionId = replaceFragmentWithBack(
                    FolderListFragment.ofTree(top),
                    FragmentTransaction.TRANSIT_FRAGMENT_OPEN, TAG_FOLDER_LIST, R.id.content_pane);
        } else {
            // Otherwise, clear the selected folder and go back to whatever the
            // last folder list displayed was.
            // TODO(viki): Load folder list for parent folder.
        }
    }

    /**
     * Switch to the Inbox by creating a new conversation list context that loads the inbox.
     */
    private void transitionToInbox() {
        // The inbox could have changed, in which case we should load it again.
        if (mInbox == null || !isDefaultInbox(mInbox.uri, mAccount)) {
            loadAccountInbox();
        } else {
            final ConversationListContext listContext = ConversationListContext.forFolder(mAccount,
                    mInbox);
            // Set the correct context for what the conversation view will be
            // now.
            onFolderChanged(mInbox);
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
            mLastFolderListTransactionId = replaceFragmentWithBack(
                    FolderListFragment.ofTree(folder),
                    FragmentTransaction.TRANSIT_FRAGMENT_OPEN, TAG_FOLDER_LIST, R.id.content_pane);
        } else {
            super.onFolderSelected(folder);
        }
    }

    private static boolean isTransactionIdValid(int id) {
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
    public boolean handleUpPress() {
        final int mode = mViewMode.getMode();
        if (mode == ViewMode.SEARCH_RESULTS_LIST) {
            mActivity.finish();
            // Not needed, the activity is going away anyway.
            return true;
        }
        if (mode == ViewMode.CONVERSATION_LIST
                || mode == ViewMode.WAITING_FOR_ACCOUNT_INITIALIZATION) {
            // Up affordance: show the drawer.
            toggleFolderListState();
            return true;
        }
        if (mode == ViewMode.CONVERSATION
                || mode == ViewMode.SEARCH_RESULTS_CONVERSATION) {
            // Same as go back.
            handleBackPress();
            return true;
        }
        if (mode == ViewMode.FOLDER_LIST) {
            // If the folder is the top level of an heirarchy or flat, toggle the
            // state of the drawer. Otherwise, let it pass on as a back press.
            // We don't want this code in back press, otherwise pressing back
            // will exhibit the same behavior
            final FolderListFragment folderListFragment = getFolderListFragment();
            if (folderListFragment != null) {
                final Folder parentFolder = folderListFragment.getParentFolder();
                if (parentFolder == null || parentFolder.parent == null) {
                    toggleFolderListState();
                    return true;
                }
            }
            handleBackPress();
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
            // Set the correct context for what the conversation view will be now.
            onFolderChanged(mInbox);
        }
        mConversationListVisible = true;
        onConversationVisibilityChanged(false);
        onConversationListVisibilityChanged(true);
    }

    private void safelyPopBackStack(boolean inLoaderCallbacks) {
        safelyPopBackStack(-1, inLoaderCallbacks);
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
     * @param transactionId back stack destination to pop to, or -1 to just pop the top
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
                            Utils.convertHtmlToPlainText
                                (op.getDescription(mActivity.getActivityContext())),
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
                                Utils.convertHtmlToPlainText
                                    (op.getDescription(mActivity.getActivityContext())),
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
    protected void hideOrRepositionToastBar(boolean animated) {
        mToastBar.hide(animated);
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
            final FragmentManager fm = mActivity.getFragmentManager();
            if (mTransactionId < 0) {
                fm.popBackStackImmediate();
            } else {
                fm.popBackStackImmediate(mTransactionId, 0);
            }
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
