package com.mohanmac.mediacodec1;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.File;
import java.nio.ByteBuffer;

public class TEST {
    public TEST(){};

    private static final String TAG = "[ Mohan ]";

    private static final boolean VERBOSE = true;
    private static final boolean DEBUG_SAVE_FILE = true;   // save copy of encoded movie
    private static final String DEBUG_FILE_NAME_BASE = "/sdcard/test.";
    private static final int OUTPUT_VIDEO_COLOR_FORMAT =
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;

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
        //MediaCodec decoder = null;
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
            if (VERBOSE) Log.d(TAG, "found colorFormat: " + colorFormat);
            // We avoid the device-specific limitations on width and height by using values that
            // are multiples of 16, which all tested devices seem to be able to handle.
            MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
            // Set some properties.  Failing to specify some of these can cause the MediaCodec
            // configure() call to throw an unhelpful exception.
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, OUTPUT_VIDEO_COLOR_FORMAT);
            format.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
            if (VERBOSE) Log.d(TAG, "format: " + format);
            // Create a MediaCodec for the desired codec, then configure it as an encoder with
            // our desired properties.
            encoder = MediaCodec.createByCodecName(codecInfo.getName());
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();
            // Create a MediaCodec for the decoder, just based on the MIME type.  The various
            // format details will be passed through the csd-0 meta-data later on.
            //decoder = MediaCodec.createDecoderByType(MIME_TYPE);
            mMuxer = new MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mTrackIndex = -1;
            mMuxerStarted = false;
            doEncodeDecodeVideoFromBuffer(encoder, colorFormat, toSurface, mMuxer);
        } finally {
            if (VERBOSE) Log.d(TAG, "releasing codecs");
            if (encoder != null) {
                encoder.stop();
                encoder.release();
            }
            if(mMuxer!=null) {
                mMuxer.stop();
                mMuxer.release();
                mMuxer = null;
            }
            Log.i(TAG, "Largest color delta: " + mLargestColorDelta);
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

    private void doEncodeDecodeVideoFromBuffer(MediaCodec encoder, int encoderColorFormat, boolean toSurface, MediaMuxer mMuxer) {
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

//            File file = new File(inputFilePath);
//            fileData = readContentIntoByteArray(file);
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
            /*while (!encoderDone) {
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
                        long ptsUsec = computePresentationTime(generateIndex);
                        if (totalDataRead >= fileData.length) {
                            // Send an empty frame with the end-of-stream flag set.  If we set EOS
                            // on a frame with data, that frame data will be ignored, and the
                            // output will be short one frame.
                            encoder.queueInputBuffer(inputBufIndex, 0, 0, ptsUsec,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                            if (VERBOSE) Log.d(TAG, "sent input EOS (with zero-length frame)");
                        } else {
                            //generateFrame(generateIndex, encoderColorFormat, frameData);
                            ByteBuffer inputBuf = encoderInputBuffers[inputBufIndex];

                            int limit = inputBuf.capacity();
                            limit = Math.min(limit,
                                    fileData.length - totalDataRead);

                            totalDataRead += limit;
                            int pos = generateIndex * limit;
                            byte[] subData = new byte[limit];
                            System.arraycopy(fileData, pos, subData, 0, limit);
                            inputBuf.clear();
                            inputBuf.put(subData);
                            encoder.queueInputBuffer(inputBufIndex, 0, limit, ptsUsec, 0);
                            if (VERBOSE) Log.d(TAG, "submitted frame " + generateIndex + " to enc");
//                            // the buffer should be sized to hold one full frame
//                            if(inputBuf.capacity() >= frameData.length) {
//                                inputBuf.clear();
//                                inputBuf.put(frameData);
//
//                            }else{
//                                Log.d(TAG,"Buffer size is too low to hold one frame");
//                            }
                        }
                        generateIndex++;
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
                        Log.d(TAG, "unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                    } else { // encoderStatus >= 0
                        ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                        if (encodedData == null) {
                            Log.d(TAG, "encoderOutputBuffer " + encoderStatus + " was null");
                        }
                        // It's usually necessary to adjust the ByteBuffer values to match BufferInfo.
                        encodedData.position(info.offset);
                        encodedData.limit(info.offset + info.size);
                        encodedSize += info.size;

                        if (info.size > 0) {
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
                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0 && false) {
                            // Codec config info.  Only expected on first packet.  One way to
                            // handle this is to manually stuff the data into the MediaFormat
                            // and pass that to configure().  We do that here to exercise the API.
                            if (!decoderConfigured) {
                                MediaFormat format =
                                        MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
                                format.setByteBuffer("csd-0", encodedData);
                                decoder.configure(format, toSurface ? outputSurface.getSurface() : null,
                                        null, 0);
                                decoder.start();
                                decoderInputBuffers = decoder.getInputBuffers();
                                decoderOutputBuffers = decoder.getOutputBuffers();
                                decoderConfigured = true;
                                if (VERBOSE)
                                    Log.d(TAG, "decoder configured (" + info.size + " bytes)");
                            }

                        } else {
                            // Get a decoder input buffer, blocking until it's available.
                            if (decoderConfigured) {
                                int inputBufIndex = decoder.dequeueInputBuffer(-1);
                                ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
                                inputBuf.clear();
                                inputBuf.put(encodedData);
                                decoder.queueInputBuffer(inputBufIndex, 0, info.size,
                                        info.presentationTimeUs, info.flags);
                                encoderDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                                if (VERBOSE) Log.d(TAG, "passed " + info.size + " bytes to decoder"
                                        + (encoderDone ? " (EOS)" : ""));
                            }
                        }
                        encoder.releaseOutputBuffer(encoderStatus, false);
                    }
                }
            }*/
            if (VERBOSE) Log.d(TAG, "decoded " + checkIndex + " frames at "
                    + mWidth + "x" + mHeight + ": raw=" + rawSize + ", enc=" + encodedSize);

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

    private static long computePresentationTime(int frameIndex) {
        return 132 + frameIndex * 1000000 / FRAME_RATE;
    }


}
