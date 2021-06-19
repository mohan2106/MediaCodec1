package com.mohanmac.mediacodec1;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;


public class temp {

    private static volatile temp videoEncoder = null;

    private temp(){};

    public static temp getInstance(){
        if(videoEncoder == null){
            synchronized (temp.class){
                if(videoEncoder == null){
                    videoEncoder = new temp();
                }
            }
        }
        return videoEncoder;
    }

    public interface CompletionHandler {
        void videoEncodingDidComplete(Error error);
    }

    // Constants

    private static final String TAG = "VideoConverter";
    private static final boolean VERBOSE = true;           // lots of logging

    // parameters for the encoder
    private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
    private static final int FRAME_RATE = 15;               // 15fps
    private static final int CAPTURE_RATE = 15;               // 15fps
    private static final int IFRAME_INTERVAL = 10;          // 10 seconds between I-frames
    private static final int CHANNEL_COUNT = 1;
    private static final int SAMPLE_RATE = 128000;
    private static final int TIMEOUT_USEC = 10000;

    // size of a frame, in pixels
    private int mWidth = -1;
    private int mHeight = -1;

    // bit rate, in bits per second
    private int mBitRate = -1;

    // encoder / muxer state
    private MediaCodec mDecoder;
    private MediaCodec mEncoder;
    private MediaMuxer mMuxer;
    private int mTrackIndex;
    private boolean mMuxerStarted;
    FileOutputStream outputStream = null;
    private String outputFilePat;

    public void convertVideo(String mediaFilePath, String destinationFilePath, CompletionHandler handler) {

        // TODO: Make configurable
//        mWidth = 480;
//        mHeight = 360;
        mWidth = Resolution.RESOLUTION_360P.getWidth();
        mHeight = Resolution.RESOLUTION_360P.getHeight();
        mBitRate = 500000;
        this.outputFilePat = destinationFilePath;
        try {

            if ((mWidth % 16) != 0 || (mHeight % 16) != 0) {
                Log.e(TAG, "Width or Height not multiple of 16");
                Error e = new Error("Width and height must be a multiple of 16");
                handler.videoEncodingDidComplete(e);
                return;
            }

            // prep the decoder and the encoder
            prepareEncoderDecoder(destinationFilePath);

            // load file
            File file = new File(mediaFilePath);
            byte[] fileData = readContentIntoByteArray(file);

            // fill up the input buffer
            fillInputBuffer(fileData);

            // encode buffer
            encode();

        } catch (Exception ex) {
            Log.e(TAG, ex.toString());
            ex.printStackTrace();

        } finally {

            // release encoder and muxer
            releaseEncoder();
        }
    }

    /**
     * Configures encoder and muxer state
     */

    private void prepareEncoderDecoder(String outputPath) throws Exception {

        // create decoder to read in the file data
        mDecoder = MediaCodec.createDecoderByType(MIME_TYPE);

        // create encoder to encode the file data into a new format
        MediaCodecInfo info = selectCodec(MIME_TYPE);
        int colorFormat = selectColorFormat(info, MIME_TYPE);

        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
        format.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);

        mEncoder = MediaCodec.createByCodecName(info.getName());
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mEncoder.start();

        // Create a MediaMuxer for saving the data
        File file = new File(outputPath);
        if(file.exists()){
            file.delete();
        }
        mMuxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        mTrackIndex = -1;
        mMuxerStarted = false;
    }

    /**
     * Releases encoder resources.  May be called after partial / failed initialization.
     */
    private void releaseEncoder() {

        if (VERBOSE) Log.d(TAG, "releasing encoder objects");

        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }

        if (mMuxer != null) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }
    }

    private void fillInputBuffer(byte[] data) {

        boolean inputDone = false;
        int processedDataSize = 0;
        int frameIndex = 0;

        Log.d(TAG, "[fillInputBuffer] Buffer load start");

        ByteBuffer[] inputBuffers = mEncoder.getInputBuffers();
        ByteBuffer[] encoderOutputBuffers = mEncoder.getOutputBuffers();

        ByteBuffer[] decoderInputBuffers = null;
        ByteBuffer[] decoderOutputBuffers = null;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        MediaFormat decoderOutputFormat = null;
        int generateIndex = 0;
        int checkIndex = 0;
        int badFrames = 0;
        boolean decoderConfigured = false;
        try {
            outputStream = new FileOutputStream(outputFilePat);
            Log.d(TAG, "encoded output will be saved as " + outputFilePat);
        } catch (FileNotFoundException e) {
            Log.w(TAG, "Unable to create debug output file " + outputFilePat);
            e.printStackTrace();
        }

        // Loop until the output side is done.
        boolean encoderDone = false;
        boolean outputDone = false;

        while (!inputDone) {

            int inputBufferIndex = mEncoder.dequeueInputBuffer(10000);
            if (inputBufferIndex >= 0) {

                if (processedDataSize >= data.length) {

                    mEncoder.queueInputBuffer(inputBufferIndex, 0, 0, computePresentationTime(frameIndex), MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    inputDone = true;
                    Log.d(TAG, "[fillInputBuffer] Buffer load complete");

                } else {

                    ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];

                    int limit = inputBuffer.capacity();
                    int pos = frameIndex * limit;
                    byte[] subData = new byte[limit];
                    System.arraycopy(data, pos, subData, 0, limit);

                    inputBuffer.clear();
                    inputBuffer.put(subData);

                    Log.d(TAG, "[encode] call queueInputBuffer");
                    mEncoder.queueInputBuffer(inputBufferIndex, 0, subData.length, computePresentationTime(frameIndex), MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
                    Log.d(TAG, "[encode] did call queueInputBuffer");

                    Log.d(TAG, "[encode] Loaded frame " + frameIndex + " into buffer");

                    frameIndex++;
                }
            }else{
                Log.d(TAG,"[encode] inputBufferIndex is negative");
            }
        }
    }

    private void encode() throws Exception {

        // get buffer info
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        // start encoding
        ByteBuffer[] encoderOutputBuffers = mEncoder.getOutputBuffers();

        while (true) {

            int encoderStatus = mEncoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);

            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {

                // no output available yet
                if (VERBOSE) Log.d(TAG, "no output available, spinning to await EOS");
                break;

            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {

                // not expected for an encoder
                encoderOutputBuffers = mEncoder.getOutputBuffers();

            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {

                // should happen before receiving buffers, and should only happen once
                if (!mMuxerStarted) {

                    MediaFormat newFormat = mEncoder.getOutputFormat();
                    Log.d(TAG, "encoder output format changed: " + newFormat);

                    // now that we have the Magic Goodies, start the muxer
                    mTrackIndex = mMuxer.addTrack(newFormat);
                    mMuxer.start();
                    mMuxerStarted = true;
                }

            } else if (encoderStatus > 0) {

                ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];

                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
                }

                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    if (VERBOSE) Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    bufferInfo.size = 0;
                }

                if (bufferInfo.size != 0) {

                    if (!mMuxerStarted) {
                        throw new RuntimeException("muxer hasn't started");
                    }

                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    encodedData.position(bufferInfo.offset);
                    encodedData.limit(bufferInfo.offset + bufferInfo.size);

                    mMuxer.writeSampleData(mTrackIndex, encodedData, bufferInfo);
                    if (VERBOSE) Log.d(TAG, "sent " + bufferInfo.size + " bytes to muxer");
                }

                mEncoder.releaseOutputBuffer(encoderStatus, false);

                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (VERBOSE) Log.d(TAG, "end of stream reached");
                    break;      // out of while
                }
            }
        }
    }

    private byte[] readContentIntoByteArray(File file) throws Exception
    {
        FileInputStream fileInputStream = null;
        byte[] bFile = new byte[(int) file.length()];

        //convert file into array of bytes
        fileInputStream = new FileInputStream(file);
        fileInputStream.read(bFile);
        fileInputStream.close();

        return bFile;
    }

    /**
     * Returns the first codec capable of encoding the specified MIME type, or null if no
     * match was found.
     */
    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    private static int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }

        return 0;   // not reached
    }

    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            // these are the formats we know how to handle for this test
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }

    /**
     * Generates the presentation time for frame N, in microseconds.
     */
    private static long computePresentationTime(int frameIndex) {
        return 132 + frameIndex * 1000000 / FRAME_RATE;
    }
}
