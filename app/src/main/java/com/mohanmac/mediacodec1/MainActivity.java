package com.mohanmac.mediacodec1;

import androidx.appcompat.app.AppCompatActivity;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.SyncStateContract;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.mohanmac.mediacodec1.Utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Mohan_MainActivity";

    private static final int REQUEST_CODE_VIDEO = 100;
    private static final int REQUEST_CODE_PERMISSIONS = 101;

    private static final String KEY_PERMISSIONS_REQUEST_COUNT = "KEY_PERMISSIONS_REQUEST_COUNT";
    private static final int MAX_NUMBER_REQUEST_PERMISSIONS = 2;

    private static final List<String> sPermissions = Arrays.asList(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    );
    private Button selectVideoBtn;

    private int mPermissionRequestCount;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        selectVideoBtn = (Button)findViewById(R.id.select_video);

        if (savedInstanceState != null) {
            mPermissionRequestCount =
                    savedInstanceState.getInt(KEY_PERMISSIONS_REQUEST_COUNT, 0);
        }

        // Make sure the app has correct permissions to run
        requestPermissionsIfNecessary();

//        File file = FileUtils.createFileDir(this,"output");
//        String outputUri = file.getAbsolutePath() + "/mohan_input_muxer2.mp4";
//        EncoderMuxer encoderMuxer = new EncoderMuxer();
//        encoderMuxer.testEncodeVideoToMp4(outputUri);
        // Create request to get image from filesystem when button clicked
        selectVideoBtn.setOnClickListener(view -> {
            Intent intent = new Intent();
            intent.setType("video/avc");

            Intent chooseIntent = new Intent(
                    Intent.ACTION_PICK,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
//            intent.setAction(Intent.ACTION_GET_CONTENT);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(chooseIntent, REQUEST_CODE_VIDEO);
            }
//            startActivityForResult(chooseIntent, REQUEST_CODE_VIDEO);
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_PERMISSIONS_REQUEST_COUNT, mPermissionRequestCount);
    }

    /**
     * Request permissions twice - if the user denies twice then show a toast about how to update
     * the permission for storage. Also disable the button if we don't have access to pictures on
     * the device.
     */
    private void requestPermissionsIfNecessary() {
        if (!checkAllPermissions()) {
            if (mPermissionRequestCount < MAX_NUMBER_REQUEST_PERMISSIONS) {
                mPermissionRequestCount += 1;
                ActivityCompat.requestPermissions(
                        this,
                        sPermissions.toArray(new String[0]),
                        REQUEST_CODE_PERMISSIONS);
            } else {
                Toast.makeText(this, R.string.set_permissions_in_settings,
                        Toast.LENGTH_LONG).show();
                selectVideoBtn.setEnabled(false);
            }
        }
    }

    private boolean checkAllPermissions() {
        boolean hasPermissions = true;
        for (String permission : sPermissions) {
            hasPermissions &=
                    ContextCompat.checkSelfPermission(
                            this, permission) == PackageManager.PERMISSION_GRANTED;
        }
        return hasPermissions;
    }

    /** Permission Checking **/

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            requestPermissionsIfNecessary(); // no-op if permissions are granted already.
        }
    }

    /** Image Selection **/

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case REQUEST_CODE_VIDEO:
                    handleVideoRequestResult(data);
                    break;
                default:
                    Log.d(TAG, "Unknown request code.");
            }
        } else {
            Log.e(TAG, String.format("Unexpected Result code %s", resultCode));
        }
    }

    private void handleVideoRequestResult(Intent data) {
        final File file = FileUtils.getFileFromUri(this, data.getData());
        String filePath = file.getAbsolutePath();
//        Bundle bundle = new Bundle();
//        bundle.putString("filePath", filePath);
//        Intent msgIntent = new Intent(this, VideoCompressService.class);
//        msgIntent.putExtras(bundle);
//        startService(msgIntent);
        Log.d(TAG,"File path is " + filePath);
        Toast.makeText(this, "File path is " + filePath, Toast.LENGTH_SHORT).show();
        MediaInfo(filePath,Uri.parse(filePath));

//        Uri imageUri = null;
//        if (data.getClipData() != null) {
//            imageUri = data.getClipData().getItemAt(0).getUri();
//        } else if (data.getData() != null) {
//            imageUri = data.getData();
//        }
//
//        if (imageUri == null) {
//            Log.e(TAG, "Invalid input image Uri.");
//            return;
//        }
//
//        Intent filterIntent = new Intent(this, BlurActivity.class);
//        filterIntent.putExtra(SyncStateContract.Constants.KEY_IMAGE_URI, imageUri.toString());
//        startActivity(filterIntent);
    }

    private void MediaInfo(String filepath,Uri uri){

        /*VideoResampler resampler = new VideoResampler();
        resampler.setInput(uri);

        SamplerClip clip = new SamplerClip(uri);
        int duration = MediaHelper.GetDuration(uri);
        clip.setVideoDuration(duration);
        resampler.addSamplerClip(clip);
        File file = FileUtils.createFileDir(this,"output");
        Uri outputUri = Uri.parse(file.getAbsolutePath() + "/mohan.mp4");
        resampler.setOutput(outputUri);
        int width = Resolution.RESOLUTION_360P.getWidth();
        int height = Resolution.RESOLUTION_360P.getHeight();
        resampler.setOutputResolution(width,height);
        int bitrate = MediaHelper.GetBitRate(Uri.parse(filepath));
        resampler.setOutputBitRate(bitrate/2);
        int frameRate = MediaHelper.GetFrameRate(uri);
        resampler.setOutputFrameRate(30);
        resampler.setOutputIFrameInterval(5);


        try {
            resampler.start();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }*/

//        ExtractDecodeEditEncodeMuxTest extractDecodeEditEncodeMuxTest = ExtractDecodeEditEncodeMuxTest.getInstance();
//        try {
//            extractDecodeEditEncodeMuxTest.testExtractDecodeEditEncodeMux720p(filepath);
//        } catch (Throwable throwable) {
//            throwable.printStackTrace();
//        }


        File file = FileUtils.createFileDir(this,"output");
        String outputUri = file.getAbsolutePath() + "/mohan" + ".mp4";
        File file1 = new File(outputUri);
        if(file1.exists()){
            file1.delete();
        }
        copyVideo(filepath,outputUri);
        Compressor compressor = Compressor.getInstance();
        try {
            compressor.testEncodeDecodeVideoFromBufferToBuffer720p(filepath, outputUri);
        } catch (Exception e) {
            e.printStackTrace();
            Log.d("Mohan",e.getMessage());
        }
        /*MediaExtractor extractor = new MediaExtractor();
        File file = new File(filepath);

        try {
            extractor.setDataSource(filepath);
            int numTracks = extractor.getTrackCount();
            Log.d(TAG,"No of tracks in this media is " + numTracks);
            for (int i = 0; i < numTracks; ++i) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
//                int bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE);
                Log.d(TAG, mime);
//                Log.d(TAG," bitrate is : " + bitrate);
                if(mime.equals("video/avc")){
                    //int bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE);
                    //int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);

                    int width = format.getInteger(MediaFormat.KEY_WIDTH);
                    int height  = format.getInteger(MediaFormat.KEY_HEIGHT);
                    //Log.d(TAG," bitrate is : " + bitrate);
                    //Log.d(TAG," sampleRate is : " + sampleRate);
                    Log.d(TAG," Width is : " + width);
                    Log.d(TAG,"Height is : " + height);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }*/
    }

    private void copyVideo(String source,String dest){
        try {
            CopyVideo.genVideoUsingMuxer(source,dest,0,100,false,true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}