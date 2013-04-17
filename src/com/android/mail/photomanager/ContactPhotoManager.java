/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mail.photomanager;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Contacts.Photo;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Directory;
import android.text.TextUtils;
import android.util.LruCache;

import com.android.mail.ui.DividedImageCanvas;
import com.android.mail.ui.ImageCanvas;
import com.android.mail.utils.LogUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Asynchronously loads contact photos and maintains a cache of photos.
 */
public class ContactPhotoManager extends PhotoManager {
    private static final DefaultImageProvider DEFAULT_AVATAR = new LetterTileProvider();
    public static final String CONTACT_PHOTO_SERVICE = "contactPhotos";

    @Override
    public DefaultImageProvider getDefaultImageProvider() {
        return DEFAULT_AVATAR;
    }

    @Override
    public long getHash(PhotoIdentifier id, ImageCanvas view) {
        ContactIdentifier contact = (ContactIdentifier) id;
        DividedImageCanvas dividedImageCanvas = (DividedImageCanvas) view;
        return DividedImageCanvas.generateHash(
                dividedImageCanvas, contact.pos, contact.emailAddress);
    }

    @Override
    public PhotoLoaderThread getLoaderThread(ContentResolver contentResolver) {
        return new ContactPhotoLoaderThread(contentResolver);
    }

    @Override
    public int getCapacityOfBitmapCache() {
        return 6;
    }

    /**
     * Requests the singleton instance of {@link AccountTypeManager} with data
     * bound from the available authenticators. This method can safely be called
     * from the UI thread.
     */
    public static ContactPhotoManager getInstance(Context context) {
        Context applicationContext = context.getApplicationContext();
        ContactPhotoManager service =
                (ContactPhotoManager) applicationContext.getSystemService(CONTACT_PHOTO_SERVICE);
        if (service == null) {
            service = createContactPhotoManager(applicationContext);
            LogUtils.e(TAG, "No contact photo service in context: " + applicationContext);
        }
        return service;
    }

    public static synchronized ContactPhotoManager createContactPhotoManager(Context context) {
        return new ContactPhotoManager(context);
    }

    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    private static final String[] COLUMNS = new String[] { Photo._ID, Photo.PHOTO };

    /**
     * An LRU cache for photo ids mapped to contact addresses.
     */
    private final LruCache<String, Long> mPhotoIdCache;

    /** Cache size for {@link #mPhotoIdCache}. Starting with 500 entries. */
    private static final int PHOTO_ID_CACHE_SIZE = 500;

    public ContactPhotoManager(Context context) {
        super(context);
        mPhotoIdCache = new LruCache<String, Long>(PHOTO_ID_CACHE_SIZE);
    }

    @Override
    public void clear() {
        super.clear();
        mPhotoIdCache.evictAll();
    }

    /**
     * Store the supplied photo id to contact address mapping so that we don't
     * have to lookup the contact again.
     * @param id Id of the photo matching the contact
     * @param contactAddress Email address of the contact
     */
    private void cachePhotoId(Long id, String contactAddress) {
        mPhotoIdCache.put(contactAddress, id);
    }

    public static class ContactIdentifier implements PhotoIdentifier {
        public final String name;
        public final String emailAddress;
        public final int pos;

        public ContactIdentifier(String name, String emailAddress, int pos) {
            this.name = name;
            this.emailAddress = emailAddress;
            this.pos = pos;
        }

        @Override
        public boolean isValid() {
            return !TextUtils.isEmpty(emailAddress);
        }

        @Override
        public Object getKey() {
            return emailAddress;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(emailAddress, name, pos);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ContactIdentifier other = (ContactIdentifier) obj;
            return Objects.equal(emailAddress, other.emailAddress)
                    && Objects.equal(name, other.name) && Objects.equal(pos, other.pos);
        }
    }

    public class ContactPhotoLoaderThread extends PhotoLoaderThread {
        private static final int PHOTO_PRELOAD_DELAY = 1000;
        private static final int PRELOAD_BATCH = 25;
        /**
         * Maximum number of photos to preload.  If the cache size is 2Mb and
         * the expected average size of a photo is 4kb, then this number should be 2Mb/4kb = 500.
         */
        private static final int MAX_PHOTOS_TO_PRELOAD = 100;

        private final String[] DATA_COLS = new String[] {
            Email.DATA,                 // 0
            Email.PHOTO_ID              // 1
        };

        private static final int DATA_EMAIL_COLUMN = 0;
        private static final int DATA_PHOTO_ID_COLUMN = 1;

        public ContactPhotoLoaderThread(ContentResolver resolver) {
            super(resolver);
        }

        @Override
        protected int getPhotoPreloadDelay() {
            return PHOTO_PRELOAD_DELAY;
        }

        @Override
        protected int getPreloadBatch() {
            return PRELOAD_BATCH;
        }

        @Override
        protected void queryAndAddPhotosForPreload(List<Object> preloadPhotoIds) {
            Cursor cursor = null;
            try {
                Uri uri = Contacts.CONTENT_URI.buildUpon().appendQueryParameter(
                        ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(Directory.DEFAULT))
                        .appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY,
                                String.valueOf(MAX_PHOTOS_TO_PRELOAD))
                        .build();
                cursor = getResolver().query(uri, new String[] { Contacts.PHOTO_ID },
                        Contacts.PHOTO_ID + " NOT NULL AND " + Contacts.PHOTO_ID + "!=0",
                        null,
                        Contacts.STARRED + " DESC, " + Contacts.LAST_TIME_CONTACTED + " DESC");

                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        // Insert them in reverse order, because we will be taking
                        // them from the end of the list for loading.
                        long photoId = cursor.getLong(0);
                        preloadPhotoIds.add(0, photoId);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        @Override
        protected Map<Object, byte[]> preloadPhotos(Set<Object> photoIds) {
            Map<Object, byte[]> photos = new HashMap<Object, byte[]>(photoIds.size());

            if (photoIds == null || photoIds.isEmpty()) {
                return photos;
            }

            List<String> photoIdsAsString = Lists.newArrayList();
            Iterator<Object> iterator = photoIds.iterator();
            while (iterator.hasNext()) {
                photoIdsAsString.add(String.valueOf(iterator.next()));
            }

            // first try getting photos from Contacts
            Cursor contactCursor = null;
            try {
                contactCursor = getResolver().query(Data.CONTENT_URI, COLUMNS,
                        createInQuery(Photo._ID, photoIds.size()),
                        photoIdsAsString.toArray(EMPTY_STRING_ARRAY), null);
                while (contactCursor.moveToNext()) {
                    Long id = contactCursor.getLong(0);
                    byte[] bytes = contactCursor.getBlob(1);
                    photoIds.remove(id);
                    photos.put(id, bytes);
                }
            } finally {
                if (contactCursor != null) {
                    contactCursor.close();
                }
            }

            iterator = photoIds.iterator();
            // then try to get the rest from Profiles
            while (iterator.hasNext()) {
                Long id = (Long) iterator.next();
                if (ContactsContract.isProfileId(id)) {
                    Cursor profileCursor = null;
                    try {
                        profileCursor = getResolver().query(
                                ContentUris.withAppendedId(Data.CONTENT_URI, id),
                                COLUMNS, null, null, null);
                        if (profileCursor != null && profileCursor.moveToFirst()) {
                            photos.put(profileCursor.getLong(0), profileCursor.getBlob(1));
                        } else {
                            // Couldn't load a photo this way either.
                            photos.put(id, null);
                        }
                    } finally {
                        if (profileCursor != null) {
                            profileCursor.close();
                        }
                    }
                } else {
                    // Not a profile photo and not found - mark the cache accordingly
                    photos.put(id, null);
                }
                iterator.remove();
            }

            return photos;
        }

        @Override
        protected Map<String, byte[]> loadPhotos(Collection<Request> requests) {
            Map<String, byte[]> photos = new HashMap<String, byte[]>(requests.size());

            Set<String> addresses = new HashSet<String>();
            Set<Object> photoIds = new HashSet<Object>();
            HashMap<Long, String> photoIdMap = new HashMap<Long, String>();

            Long match;
            String emailAddress;
            for (Request request : requests) {
                emailAddress = (String) request.getKey();
                match = mPhotoIdCache.get(emailAddress);
                if (match != null) {
                    photoIds.add(match);
                    photoIdMap.put(match, emailAddress);
                } else {
                    addresses.add(emailAddress);
                }
            }

            if (addresses.size() > 0) {
                String[] selectionArgs = new String[addresses.size()];
                addresses.toArray(selectionArgs);
                Cursor photoIdsCursor = null;
                try {
                    StringBuilder query = new StringBuilder().append(Data.MIMETYPE).append("='")
                            .append(Email.CONTENT_ITEM_TYPE).append("' AND ").append(Email.DATA)
                            .append(" IN (");
                    appendQuestionMarks(query, addresses.size());
                    query.append(')');
                    photoIdsCursor = getResolver().query(Data.CONTENT_URI, DATA_COLS,
                            query.toString(), selectionArgs, null /* sortOrder */);
                    Long id;
                    String contactAddress;
                    if (photoIdsCursor != null) {
                        while (photoIdsCursor.moveToNext()) {
                            id = photoIdsCursor.getLong(DATA_PHOTO_ID_COLUMN);
                            // In case there are multiple contacts for this
                            // contact, try to always pick the one that actually
                            // has a photo.
                            if (!photoIdsCursor.isNull(DATA_PHOTO_ID_COLUMN)) {
                                contactAddress = photoIdsCursor.getString(DATA_EMAIL_COLUMN);
                                photoIds.add(id);
                                photoIdMap.put(id, contactAddress);
                                cachePhotoId(id, contactAddress);
                            }
                        }
                    }
                } finally {
                    if (photoIdsCursor != null) {
                        photoIdsCursor.close();
                    }
                }
            }
            if (photoIds != null && photoIds.size() > 0) {
                Map<Object, byte[]> photosFromIds = preloadPhotos(photoIds);
                onLoaded(photosFromIds.keySet());

                for (Object id : photosFromIds.keySet()) {
                    byte[] bytes = photosFromIds.get(id);
                    photos.put(photoIdMap.get(id), bytes);
                }
            }

            // TODO(mindyp): this optimization assumes that contact photos don't
            // change/ update that often, and if you didn't have a matching id
            // for a contact before, you probably won't be getting it any time soon.
            for (String a : addresses) {
                if (!photoIdMap.containsValue(a)) {
                    // We couldn't find a matching photo id at all, so just
                    // cache this as needing a default image.
                    photos.put(a, null);
                }
            }

            return photos;
        }
    }
}
