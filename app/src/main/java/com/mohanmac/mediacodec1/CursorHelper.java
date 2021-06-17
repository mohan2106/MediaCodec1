package com.mohanmac.mediacodec1;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

public class CursorHelper {
    public static String GetColumn(Context context, Uri uri, String column, String selection, String[] selectionArgs ) {

        String path = null;
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query( uri, new String[] { column }, selection, selectionArgs, null );
            if ( cursor != null && cursor.moveToFirst() ) {
                int column_index = cursor.getColumnIndexOrThrow( column );
                path = cursor.getString( column_index );
            }
        } finally {
            if ( cursor != null ) {
                cursor.close();
            }
        }

        return path;
    }
}
