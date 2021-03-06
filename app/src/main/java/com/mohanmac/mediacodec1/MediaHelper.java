package com.mohanmac.mediacodec1;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

import android.annotation.TargetApi;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.util.Log;


public class MediaHelper {
    public static final String MIME_TYPE_AVC = "video/avc";

    public static Bitmap GetThumbnailFromVideo( Uri uri, long timeMs ) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource( uri.toString() );
        return retriever.getFrameAtTime( timeMs * 1000 );
    }

    public static int GetDuration( Uri uri ) {
        return GetMediaMetadataRetrieverPropertyInteger( uri, MediaMetadataRetriever.METADATA_KEY_DURATION, 0 );
    }

    public static int GetWidth( Uri uri ) {
        return GetMediaMetadataRetrieverPropertyInteger( uri, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH, 0 );
    }

    public static int GetHeight( Uri uri ) {
        return GetMediaMetadataRetrieverPropertyInteger( uri, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT, 0 );
    }

    public static int GetBitRate( Uri uri ) {
        return GetMediaMetadataRetrieverPropertyInteger( uri, MediaMetadataRetriever.METADATA_KEY_BITRATE, 0 );
    }

    @TargetApi( Build.VERSION_CODES.JELLY_BEAN_MR1 )
    public static int GetRotation( Uri uri ) {
        return GetMediaMetadataRetrieverPropertyInteger( uri, MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION, 0 );
    }

    public static int GetMediaMetadataRetrieverPropertyInteger( Uri uri, int key, int defaultValue ) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        Log.d("Mohan",uri.toString());
        retriever.setDataSource(String.valueOf(uri));
        String value = retriever.extractMetadata( key );

        if ( value == null ) {
            return defaultValue;
        }
        return Integer.parseInt( value );

    }

    @TargetApi( Build.VERSION_CODES.JELLY_BEAN )
    public static int GetIFrameInterval( Uri uri ) {

        return GetMediaFormatPropertyInteger( uri, MediaFormat.KEY_I_FRAME_INTERVAL, -1 );
    }

    @TargetApi( Build.VERSION_CODES.JELLY_BEAN )
    public static int GetFrameRate( Uri uri ) {

        return GetMediaFormatPropertyInteger( uri, MediaFormat.KEY_FRAME_RATE, -1 );
    }

    @TargetApi( Build.VERSION_CODES.JELLY_BEAN )
    public static int GetMediaFormatPropertyInteger( Uri uri, String key, int defaultValue ) {
        int value = defaultValue;

        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource( uri.toString() );
        } catch ( IOException e ) {
            e.printStackTrace();
            return value;
        }

        MediaFormat format = GetTrackFormat( extractor, MIME_TYPE_AVC );
        extractor.release();

        if ( format.containsKey( key ) ) {
            value = format.getInteger( key );
        }

        return value;
    }

    @TargetApi( Build.VERSION_CODES.JELLY_BEAN )
    public static MediaFormat GetTrackFormat( MediaExtractor extractor, String mimeType ) {
        for ( int i = 0; i < extractor.getTrackCount(); i++ ) {
            MediaFormat format = extractor.getTrackFormat( i );
            String trackMimeType = format.getString( MediaFormat.KEY_MIME );
            if ( mimeType.equals( trackMimeType ) ) {
                return format;
            }
        }

        return null;
    }

//    public static Uri RotateVideo( Uri uri, int rotation ) {
//
//        Uri rotatedVideoUri = null;
//
//        try {
//            IsoFile file = new IsoFile( uri.toString() );
//
//            List<Box> boxes = file.getMovieBox().getBoxes();
//
//            for ( Box box : boxes ) {
//                if ( box instanceof TrackBox ) {
//                    TrackBox trackBox = (TrackBox) box;
//
//                    HandlerBox handlerBox = trackBox.getMediaBox().getHandlerBox();
//                    if ( handlerBox.getHandlerType().toLowerCase( Locale.US ).equals( "vide" ) ) {
//                        TrackHeaderBox trackHeaderBox = trackBox.getTrackHeaderBox();
//                        trackHeaderBox.setMatrix( GetMatrixFromRotation( rotation ) );
//                    }
//                }
//            }
//
//            String pathWithoutExtension = uri.toString().replace( ".mp4", "" );
//
//            String rotatedFileName = String.format( Locale.US, "%s_rotated_to_%d.mp4", pathWithoutExtension, rotation );
//
//            FileOutputStream videoFileOutputStream = new FileOutputStream( rotatedFileName );
//            file.getBox( videoFileOutputStream.getChannel() );
//
//            file.close();
//            videoFileOutputStream.close();
//
//            rotatedVideoUri = Uri.parse( rotatedFileName );
//
//        } catch ( IOException e ) {
//            e.printStackTrace();
//
//            return null;
//        }
//
//        return rotatedVideoUri;
//    }
//
//    private static Matrix GetMatrixFromRotation( int rotation ) {
//        switch ( rotation ) {
//            case 90:
//                return Matrix.ROTATE_90;
//            case 180:
//                return Matrix.ROTATE_180;
//            case 270:
//                return Matrix.ROTATE_270;
//            default:
//            case 0:
//                return Matrix.ROTATE_0;
//        }
//    }
}
