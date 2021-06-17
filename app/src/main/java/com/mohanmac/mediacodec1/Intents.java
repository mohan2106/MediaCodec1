package com.mohanmac.mediacodec1;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
public class Intents {
    @SuppressLint( "InlinedApi" )
    public static Intent GetLaunchVideoChooserIntent( Context context ) {
        if ( Build.VERSION.SDK_INT < 19 ) {
            Intent intent = new Intent();
            intent.addCategory( Intent.CATEGORY_OPENABLE );
            intent.setType( "video/mp4" );
            intent.setAction( Intent.ACTION_GET_CONTENT );
            return Intent.createChooser( intent, context.getString( R.string.select_video ) );
        } else {
            Intent intent = new Intent( Intent.ACTION_OPEN_DOCUMENT );
            intent.addCategory( Intent.CATEGORY_OPENABLE );
            intent.setType( "video/mp4" );
            intent.setAction( Intent.ACTION_GET_CONTENT );
            return Intent.createChooser( intent, context.getString( R.string.select_video ) );
        }
    }
}
