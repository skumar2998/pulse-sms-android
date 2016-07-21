/*
 * Copyright (C) 2016 Jacob Klinker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.klinker.messenger.view;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;

import xyz.klinker.messenger.R;
import xyz.klinker.messenger.adapter.AttachImageListAdapter;
import xyz.klinker.messenger.data.MimeType;
import xyz.klinker.messenger.util.listener.ImageSelectedListener;

/**
 * View that displays a list of images that are currently on your device and allows you to choose
 * one to attach to a message.
 */
public class AttachImageView extends RecyclerView {

    private Cursor images;
    private ImageSelectedListener callback;

    public AttachImageView(Context context, ImageSelectedListener callback) {
        super(context);

        this.callback = callback;
        init();
    }

    private void init() {
        ContentResolver cr = getContext().getContentResolver();
        images = Images.Media.query(cr, Images.Media.EXTERNAL_CONTENT_URI,
                new String[] {BaseColumns._ID, MediaStore.MediaColumns.DATA},
                Images.Media.MIME_TYPE + " in (?, ?, ?)", new String[] {
                        MimeType.IMAGE_JPEG, MimeType.IMAGE_PNG, MimeType.IMAGE_JPG
                }, Images.Media.DATE_TAKEN + " DESC");

        setLayoutManager(new GridLayoutManager(getContext(),
                getResources().getInteger(R.integer.images_column_count)));
        setAdapter(new AttachImageListAdapter(images, callback));
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (!images.isClosed()) {
            images.close();
        }
    }

}
