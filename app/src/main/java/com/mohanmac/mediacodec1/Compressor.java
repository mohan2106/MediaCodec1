package com.mohanmac.mediacodec1;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.opengl.GLES20;
import android.util.Log;

import com.mohanmac.mediacodec1.Utils.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import javax.microedition.khronos.opengles.GL10;


public class Compressor {
    private static Compressor compressor = null;
    private Compressor(){};
    public static Compressor getInstance(){
        if(compressor==null){
            synchronized (Compressor.class){
                if(compressor==null){
                    compressor  = new Compressor();
                }
                return compressor;
            }
        }else{
            return compressor;
        }
    }

    private static final String TAG = "[ Mohan ]";

    private static final boolean VERBOSE = true;
    private static final boolean DEBUG_SAVE_FILE = true;   // save copy of encoded movie
    private static final String DEBUG_FILE_NAME_BASE = "/sdcard/test.";

    // parameters for the encoder
    private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
    private static final int FRAME_RATE = 15;               // 15fps
    private static final int IFRAME_INTERVAL = 10;          // 10 seconds between I-frames

    // movie length, in frames
    private static final int NUM_FRAMES = 30;               // two seconds of video

    // size of a frame, in pixels
    private int mWidth = -1;
    private int mHeight = -1;
    // bit rate, in bits per second
    private int mBitRate = -1;
    private String inputFilePath = null;
    private String outputFilePath = null;
    private static final int OUTPUT_VIDEO_COLOR_FORMAT =
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;

    private static final int TEST_Y = 120;                  // YUV values for colored rect
    private static final int TEST_U = 160;
    private static final int TEST_V = 200;
    private static final int TEST_R0 = 0;                   // RGB equivalent of {0,0,0}
    private static final int TEST_G0 = 136;
    private static final int TEST_B0 = 0;
    private static final int TEST_R1 = 236;                 // RGB equivalent of {120,160,200}
    private static final int TEST_G1 = 50;
    private static final int TEST_B1 = 186;
    // largest color component delta seen (i.e. actual vs. expected)
    private int mLargestColorDelta;
    private boolean mMuxerStarted;
    private int mTrackIndex;

    public void testEncodeDecodeVideoFromBufferToBuffer720p(String inputFilePath,String outputFilePath) throws Exception {
        this.outputFilePath = outputFilePath;
        this.inputFilePath = inputFilePath;
        setParameters(1280, 720, 6000000);
        encodeDecodeVideoFromBuffer(false);
    }

    private void setParameters(int width, int height, int bitRate) {
        if ((width % 16) != 0 || (height % 16) != 0) {
            Log.w(TAG, "WARNING: width or height not multiple of 16");
        }
        mWidth = width;
        mHeight = height;
        mBitRate = bitRate;
    }

    private void encodeDecodeVideoFromBuffer(boolean toSurface) throws Exception {
        MediaCodec encoder = null;
        MediaCodec decoder = null;
        MediaMuxer mMuxer = null;
        mLargestColorDelta = -1;
        try {
            MediaCodecInfo codecInfo = selectCodec(MIME_TYPE);
            if (codecInfo == null) {
                // Don't fail CTS if they don't have an AVC codec (not here, anyway).
                Log.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE);
                return;
            }
            if (VERBOSE) Log.d(TAG, "found codec: " + codecInfo.getName());
            int colorFormat = selectColorFormat(codecInfo, MIME_TYPE);
            if (VERBOSE) Log.d(TAG, "found colorFormat: " + OUTPUT_VIDEO_COLOR_FORMAT);
            // We avoid the device-specific limitations on width and height by using values that
            // are multiples of 16, which all tested devices seem to be able to handle.
            MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
            // Set some properties.  Failing to specify some of these can cause the MediaCodec
            // configure() call to throw an unhelpful exception.
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
            format.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
            if (VERBOSE) Log.d(TAG, "format: " + format);
            // Create a MediaCodec for the desired codec, then configure it as an encoder with
            // our desired properties.

            //===================================================================Encoder type should be same
            MediaExtractor extractor = new MediaExtractor();
            extractor.setDataSource(inputFilePath);
            //selecting video track
            int videoTrack = getVideoTrackIndex(extractor);
            MediaFormat inputFormat = extractor.getTrackFormat(videoTrack);

            encoder = MediaCodec.createByCodecName(codecInfo.getName());
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
//            encoder = MediaCodec.createEncoderByType(inputFormat.getString(MediaFormat.KEY_MIME));
//            encoder.configure(inputFormat,null,null,0);
            encoder.start();
            // Create a MediaCodec for the decoder, just based on the MIME type.  The various
            // format details will be passed through the csd-0 meta-data later on.
            decoder = MediaCodec.createDecoderByType(MIME_TYPE);
            mMuxer = new MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mTrackIndex = -1;
            mMuxerStarted = false;
            doEncodeDecodeVideoFromBuffer(encoder, colorFormat, decoder, toSurface, mMuxer);
        } finally {
            if (VERBOSE) Log.d(TAG, "releasing codecs");
            if (encoder != null) {
                encoder.stop();
                encoder.release();
            }
            if (decoder != null) {
                decoder.stop();
                decoder.release();
            }
            if(mMuxer!=null) {
                mMuxer.stop();
                mMuxer.release();
                mMuxer = null;
            }
            Log.i(TAG, "Largest color delta: " + mLargestColorDelta);
        }

    }

    private static int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }
        Log.d(TAG,"couldn't find a good color format for " + codecInfo.getName() + " / " + mimeType);
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

    private void doEncodeDecodeVideoFromBuffer(MediaCodec encoder, int encoderColorFormat,
                                               MediaCodec decoder, boolean toSurface, MediaMuxer mMuxer) {
        final int TIMEOUT_USEC = 10000;
        ByteBuffer[] encoderInputBuffers = encoder.getInputBuffers();
        ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
        ByteBuffer[] decoderInputBuffers = null;
        ByteBuffer[] decoderOutputBuffers = null;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        MediaFormat decoderOutputFormat = null;
        int generateIndex = 0;
        int checkIndex = 0;
        int badFrames = 0;
        boolean decoderConfigured = false;
        OutputSurface outputSurface = null;

        //=======================EXTRACTOR
        byte[] fileData;
        try {
            MediaExtractor extractor = new MediaExtractor();
            extractor.setDataSource(inputFilePath);
            //selecting video track
            extractor.selectTrack(getVideoTrackIndex(extractor));

            File file = new File(inputFilePath);
            fileData = readContentIntoByteArray(file);
            // The size of a frame of video data, in the formats we handle, is stride*sliceHeight
            // for Y, and (stride/2)*(sliceHeight/2) for each of the Cb and Cr channels.  Application
            // of algebra and assuming that stride==width and sliceHeight==height yields:
            byte[] frameData = new byte[mWidth * mHeight * 3 / 2];
            // Just out of curiosity.
            long rawSize = 0;
            long encodedSize = 0;
            // Save a copy to disk.  Useful for debugging the test.  Note this is a raw elementary
            // stream, not a .mp4 file, so not all players will know what to do with it.
//            FileOutputStream outputStream = null;
//            if (DEBUG_SAVE_FILE) {
//                String fileName = outputFilePath;
//                File file1 = new File(fileName);
//                if(file1.exists()){
//                    file1.delete();
//                }
//                outputStream = new FileOutputStream(fileName);
//                Log.d(TAG, "encoded output will be saved as " + fileName);
//            }
            if (toSurface) {
                outputSurface = new OutputSurface(mWidth, mHeight);
            }
            // Loop until the output side is done.
            boolean inputDone = false;
            boolean encoderDone = false;
            boolean outputDone = false;
            int totalDataRead = 0;
            while (!encoderDone) {
                if (VERBOSE) Log.d(TAG, "loop");
                // If we're not done submitting frames, generate a new one and submit it.  By
                // doing this on every loop we're working to ensure that the encoder always has
                // work to do.
                //
                // We don't really want a timeout here, but sometimes there's a delay opening
                // the encoder device, so a short timeout can keep us from spinning hard.
                if (!inputDone) {
                    int inputBufIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC);
                    if (VERBOSE) Log.d(TAG, "inputBufIndex=" + inputBufIndex);
                    if (inputBufIndex >= 0) {

                        ByteBuffer inputBuf = encoderInputBuffers[inputBufIndex];
                        int size = extractor.readSampleData(inputBuf, 0);
                        long presentationTime = extractor.getSampleTime();
                        if (VERBOSE) {
                            Log.d(TAG, "video extractor: returned buffer of size " + size);
                            Log.d(TAG, "video extractor: returned buffer for time " + presentationTime);
                        }
                        if (size >= 0) {
                            encoder.queueInputBuffer(
                                    inputBufIndex,
                                    0,
                                    size,
                                    presentationTime,
                                    extractor.getSampleFlags());
                        }
                        inputDone = !extractor.advance();
                        if (inputDone) {
                            inputBufIndex = encoder.dequeueInputBuffer(-1);
                            if (VERBOSE) Log.d(TAG, "video extractor: EOS");
                            try{
                                encoder.queueInputBuffer(
                                        inputBufIndex,
                                        0,
                                        0,
                                        0,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            }catch (Exception e){
                                Log.d(TAG,e.getMessage());
                            }

                        }
                        generateIndex++;
                        // We extracted a frame, let's try something else next.

                    } else {
                        // either all in use, or we timed out during initial setup
                        if (VERBOSE) Log.d(TAG, "input buffer not available");
                    }
                }
                // Check for output from the encoder.  If there's no output yet, we either need to
                // provide more input, or we need to wait for the encoder to work its magic.  We
                // can't actually tell which is the case, so if we can't get an output buffer right
                // away we loop around and see if it wants more input.
                //
                // Once we get EOS from the encoder, we don't need to do this anymore.
                if (!encoderDone) {
                    int encoderStatus = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                    if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // no output available yet
                        if (VERBOSE) Log.d(TAG, "no output from encoder available");
                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        // not expected for an encoder
                        encoderOutputBuffers = encoder.getOutputBuffers();
                        if (VERBOSE) Log.d(TAG, "encoder output buffers changed");
                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // not expected for an encoder
                        MediaFormat newFormat = encoder.getOutputFormat();
                        if (mMuxerStarted) {
                            throw new RuntimeException("format changed twice");
                        }
                        Log.d(TAG, "encoder output format changed: " + newFormat);

                        // now that we have the Magic Goodies, start the muxer
                        mTrackIndex = mMuxer.addTrack(newFormat);
                        mMuxer.start();
                        mMuxerStarted = true;
                        if (VERBOSE) Log.d(TAG, "encoder output format changed: " + newFormat);
                    } else if (encoderStatus < 0) {
                        Log.d(TAG,"unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                    } else { // encoderStatus >= 0
                        ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                        if (encodedData == null) {
                            Log.d(TAG,"encoderOutputBuffer " + encoderStatus + " was null");
                        }
                        // It's usually necessary to adjust the ByteBuffer values to match BufferInfo.
                        encodedData.position(info.offset);
                        encodedData.limit(info.offset + info.size);
                        encodedSize += info.size;

                        if(info.size > 0) {
                            if (!mMuxerStarted) {
                                throw new RuntimeException("muxer hasn't started");
                            }
                            mMuxer.writeSampleData(mTrackIndex, encodedData, info);
                            if (VERBOSE) Log.d(TAG, "sent " + info.size + " bytes to muxer");
                        }
//                        if (outputStream != null) {
//                            byte[] data = new byte[info.size];
//                            encodedData.get(data);
//                            encodedData.position(info.offset);
//
//                            outputStream.write(encodedData);
//
//
//                        }
                        encoderDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                        if (VERBOSE) Log.d(TAG, "passed " + info.size + " bytes to decoder"
                                + (encoderDone ? " (EOS)" : ""));
                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0 && false) {
                            // Codec config info.  Only expected on first packet.  One way to
                            // handle this is to manually stuff the data into the MediaFormat
                            // and pass that to configure().  We do that here to exercise the API.
                            if(!decoderConfigured){
                                MediaFormat format =
                                        MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
                                format.setByteBuffer("csd-0", encodedData);
                                decoder.configure(format, toSurface ? outputSurface.getSurface() : null,
                                        null, 0);
                                decoder.start();
                                decoderInputBuffers = decoder.getInputBuffers();
                                decoderOutputBuffers = decoder.getOutputBuffers();
                                decoderConfigured = true;
                                if (VERBOSE) Log.d(TAG, "decoder configured (" + info.size + " bytes)");
                            }

                        } else {
                            // Get a decoder input buffer, blocking until it's available.
                            if(decoderConfigured){
                                int inputBufIndex = decoder.dequeueInputBuffer(-1);
                                ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
                                inputBuf.clear();
                                inputBuf.put(encodedData);
                                decoder.queueInputBuffer(inputBufIndex, 0, info.size,
                                        info.presentationTimeUs, info.flags);

                            }
                        }
                        encoder.releaseOutputBuffer(encoderStatus, false);
                    }
                }
                // Check for output from the decoder.  We want to do this on every loop to avoid
                // the possibility of stalling the pipeline.  We use a short timeout to avoid
                // burning CPU if the decoder is hard at work but the next frame isn't quite ready.
                //
                // If we're decoding to a Surface, we'll get notified here as usual but the
                // ByteBuffer references will be null.  The data is sent to Surface instead.
                if (decoderConfigured && false) {
                    int decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                    if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // no output available yet
                        if (VERBOSE) Log.d(TAG, "no output from decoder available");
                    } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        // The storage associated with the direct ByteBuffer may already be unmapped,
                        // so attempting to access data through the old output buffer array could
                        // lead to a native crash.
                        if (VERBOSE) Log.d(TAG, "decoder output buffers changed");
                        decoderOutputBuffers = decoder.getOutputBuffers();
                    } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // this happens before the first frame is returned
                        decoderOutputFormat = decoder.getOutputFormat();
                        if (mMuxerStarted) {
                            throw new RuntimeException("format changed twice");
                        }

                        Log.d(TAG, "encoder output format changed: " + decoderOutputFormat);

                        // now that we have the Magic Goodies, start the muxer
                        mTrackIndex = mMuxer.addTrack(decoderOutputFormat);
                        mMuxer.start();
                        mMuxerStarted = true;
                        if (VERBOSE) Log.d(TAG, "decoder output format changed: " +
                                decoderOutputFormat);
                    } else if (decoderStatus < 0) {
                        Log.d(TAG,"unexpected result from deocder.dequeueOutputBuffer: " + decoderStatus);
                    } else {  // decoderStatus >= 0
                        if (!toSurface) {
                            ByteBuffer outputFrame = decoderOutputBuffers[decoderStatus];
                            outputFrame.position(info.offset);
                            outputFrame.limit(info.offset + info.size);

                            rawSize += info.size;
                            if (info.size == 0) {
                                if (VERBOSE) Log.d(TAG, "got empty frame");
                            } else {
                                 if (VERBOSE) Log.d(TAG, "decoded, checking frame " + checkIndex);
                                 if( computePresentationTime(checkIndex) == info.presentationTimeUs) {
                                     if (!checkFrame(checkIndex++, decoderOutputFormat, outputFrame)) {
                                         badFrames++;
                                     }
                                 }else{
                                     Log.d(TAG,"Wrong time stamp");
                                 }
                                if (!mMuxerStarted) {
                                    throw new RuntimeException("muxer hasn't started");
                                }
                                mMuxer.writeSampleData(mTrackIndex, outputFrame, info);
                                if (VERBOSE) Log.d(TAG, "sent " + info.size + " bytes to muxer");
                            }
                            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                if (VERBOSE) Log.d(TAG, "output EOS");
                                outputDone = true;
                            }
                            decoder.releaseOutputBuffer(decoderStatus, false /*render*/);
                        } else {
                            if (VERBOSE) Log.d(TAG, "surface decoder given buffer " + decoderStatus +
                                    " (size=" + info.size + ")");
                            rawSize += info.size;
                            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                if (VERBOSE) Log.d(TAG, "output EOS");
                                outputDone = true;
                            }
                            boolean doRender = (info.size != 0);
                            // As soon as we call releaseOutputBuffer, the buffer will be forwarded
                            // to SurfaceTexture to convert to a texture.  The API doesn't guarantee
                            // that the texture will be available before the call returns, so we
                            // need to wait for the onFrameAvailable callback to fire.
                            decoder.releaseOutputBuffer(decoderStatus, doRender);
                            if (doRender) {
                                if (VERBOSE) Log.d(TAG, "awaiting frame " + checkIndex);
                                if( computePresentationTime(checkIndex) ==
                                        info.presentationTimeUs) {
                                    outputSurface.awaitNewImage();
                                    outputSurface.drawImage();
                                    if (!checkSurfaceFrame(checkIndex++)) {
                                        badFrames++;
                                    }
                                }else{
                                    Log.d(TAG,"Wrong time stamp");
                                }
                            }
                        }
                    }
                }
            }
            if (VERBOSE) Log.d(TAG, "decoded " + checkIndex + " frames at "
                    + mWidth + "x" + mHeight + ": raw=" + rawSize + ", enc=" + encodedSize);
//            if (outputStream != null) {
//                try {
//                    outputStream.close();
//                } catch (IOException ioe) {
//                    Log.w(TAG, "failed closing debug file");
//                    throw new RuntimeException(ioe);
//                }
//            }
            if (outputSurface != null) {
                outputSurface.release();
            }
            if (checkIndex != NUM_FRAMES) {
                Log.d(TAG,"expected " + NUM_FRAMES + " frames, only decoded " + checkIndex);
            }
            if (badFrames != 0) {
                Log.d(TAG,"Found " + badFrames + " bad frames");
            }
        } catch (Exception e) {
            e.printStackTrace();
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

    private int getVideoTrackIndex(MediaExtractor extractor) {

        for ( int trackIndex = 0; trackIndex < extractor.getTrackCount(); trackIndex++ ) {
            MediaFormat format = extractor.getTrackFormat( trackIndex );

            String mime = format.getString( MediaFormat.KEY_MIME );
            if ( mime != null ) {
                if ( mime.equals( "video/avc" ) ) {
                    return trackIndex;
                }
            }
        }

        return -1;
    }



    private boolean checkFrame(int frameIndex, MediaFormat format, ByteBuffer frameData) {
        // Check for color formats we don't understand.  There is no requirement for video
        // decoders to use a "mundane" format, so we just give a pass on proprietary formats.
        // e.g. Nexus 4 0x7FA30C03 OMX_QCOM_COLOR_FormatYUV420PackedSemiPlanar64x32Tile2m8ka
        int colorFormat = format.getInteger(MediaFormat.KEY_COLOR_FORMAT);
        if (!isRecognizedFormat(colorFormat)) {
            Log.d(TAG, "unable to check frame contents for colorFormat=" +
                    Integer.toHexString(colorFormat));
            return true;
        }
        boolean frameFailed = false;
        boolean semiPlanar = isSemiPlanarYUV(colorFormat);
        int width = format.getInteger(MediaFormat.KEY_WIDTH);
        int height = format.getInteger(MediaFormat.KEY_HEIGHT);
        int halfWidth = width / 2;
        int cropLeft = format.getInteger("crop-left");
        int cropRight = format.getInteger("crop-right");
        int cropTop = format.getInteger("crop-top");
        int cropBottom = format.getInteger("crop-bottom");
        int cropWidth = cropRight - cropLeft + 1;
        int cropHeight = cropBottom - cropTop + 1;
        if(mWidth != cropWidth || mHeight !=cropHeight) return false;
//        assertEquals(mHeight, cropHeight);
        for (int i = 0; i < 8; i++) {
            int x, y;
            if (i < 4) {
                x = i * (mWidth / 4) + (mWidth / 8);
                y = mHeight / 4;
            } else {
                x = (7 - i) * (mWidth / 4) + (mWidth / 8);
                y = (mHeight * 3) / 4;
            }
            y += cropTop;
            x += cropLeft;
            int testY, testU, testV;
            if (semiPlanar) {
                // Galaxy Nexus uses OMX_TI_COLOR_FormatYUV420PackedSemiPlanar
                testY = frameData.get(y * width + x) & 0xff;
                testU = frameData.get(width*height + 2*(y/2) * halfWidth + 2*(x/2)) & 0xff;
                testV = frameData.get(width*height + 2*(y/2) * halfWidth + 2*(x/2) + 1) & 0xff;
            } else {
                // Nexus 10, Nexus 7 use COLOR_FormatYUV420Planar
                testY = frameData.get(y * width + x) & 0xff;
                testU = frameData.get(width*height + (y/2) * halfWidth + (x/2)) & 0xff;
                testV = frameData.get(width*height + halfWidth * (height / 2) +
                        (y/2) * halfWidth + (x/2)) & 0xff;
            }
            int expY, expU, expV;
            if (i == frameIndex % 8) {
                // colored rect
                expY = TEST_Y;
                expU = TEST_U;
                expV = TEST_V;
            } else {
                // should be our zeroed-out buffer
                expY = expU = expV = 0;
            }
            if (!isColorClose(testY, expY) ||
                    !isColorClose(testU, expU) ||
                    !isColorClose(testV, expV)) {
                Log.w(TAG, "Bad frame " + frameIndex + " (rect=" + i + ": yuv=" + testY +
                        "," + testU + "," + testV + " vs. expected " + expY + "," + expU +
                        "," + expV + ")");
                frameFailed = true;
            }
        }
        return !frameFailed;
    }
    /**
     * Returns true if the actual color value is close to the expected color value.  Updates
     * mLargestColorDelta.
     */
    boolean isColorClose(int actual, int expected) {
        final int MAX_DELTA = 8;
        int delta = Math.abs(actual - expected);
        if (delta > mLargestColorDelta) {
            mLargestColorDelta = delta;
        }
        return (delta <= MAX_DELTA);
    }
    /**
     * Generates the presentation time for frame N, in microseconds.
     */
    private static long computePresentationTime(int frameIndex) {
        return 132 + frameIndex * 1000000 / FRAME_RATE;
    }

    private void generateSurfaceFrame(int frameIndex) {
        frameIndex %= 8;
        int startX, startY;
        if (frameIndex < 4) {
            // (0,0) is bottom-left in GL
            startX = frameIndex * (mWidth / 4);
            startY = mHeight / 2;
        } else {
            startX = (7 - frameIndex) * (mWidth / 4);
            startY = 0;
        }
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClearColor(TEST_R0 / 255.0f, TEST_G0 / 255.0f, TEST_B0 / 255.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(startX, startY, mWidth / 4, mHeight / 2);
        GLES20.glClearColor(TEST_R1 / 255.0f, TEST_G1 / 255.0f, TEST_B1 / 255.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    }
    /**
     * Checks the frame for correctness.  Similar to {@link #checkFrame}, but uses GL to
     * read pixels from the current surface.
     *
     * @return true if the frame looks good
     */
    private boolean checkSurfaceFrame(int frameIndex) {
        ByteBuffer pixelBuf = ByteBuffer.allocateDirect(4); // TODO - reuse this
        boolean frameFailed = false;
        for (int i = 0; i < 8; i++) {
            // Note the coordinates are inverted on the Y-axis in GL.
            int x, y;
            if (i < 4) {
                x = i * (mWidth / 4) + (mWidth / 8);
                y = (mHeight * 3) / 4;
            } else {
                x = (7 - i) * (mWidth / 4) + (mWidth / 8);
                y = mHeight / 4;
            }
            GLES20.glReadPixels(x, y, 1, 1, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, pixelBuf);
            int r = pixelBuf.get(0) & 0xff;
            int g = pixelBuf.get(1) & 0xff;
            int b = pixelBuf.get(2) & 0xff;
            //Log.d(TAG, "GOT(" + frameIndex + "/" + i + "): r=" + r + " g=" + g + " b=" + b);
            int expR, expG, expB;
            if (i == frameIndex % 8) {
                // colored rect
                expR = TEST_R1;
                expG = TEST_G1;
                expB = TEST_B1;
            } else {
                // zero background color
                expR = TEST_R0;
                expG = TEST_G0;
                expB = TEST_B0;
            }
            if (!isColorClose(r, expR) ||
                    !isColorClose(g, expG) ||
                    !isColorClose(b, expB)) {
                Log.w(TAG, "Bad frame " + frameIndex + " (rect=" + i + ": rgb=" + r +
                        "," + g + "," + b + " vs. expected " + expR + "," + expG +
                        "," + expB + ")");
                frameFailed = true;
            }
        }
        return !frameFailed;
    }


    /**
     * Returns true if the specified color format is semi-planar YUV.  Throws an exception
     * if the color format is not recognized (e.g. not YUV).
     */
    private static boolean isSemiPlanarYUV(int colorFormat) {
        switch (colorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
                return false;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                throw new RuntimeException("unknown format " + colorFormat);
        }
    }

}
