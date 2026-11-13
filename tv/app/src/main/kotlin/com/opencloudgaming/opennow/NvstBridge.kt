package com.opencloudgaming.opennow

import androidx.annotation.Keep

/**
 * The door into libopennow_nvst, OpenNOW's implementation of NVIDIA's native streaming
 * protocol (see tv/nvst). The library looks these functions up by this exact package and class
 * name, which is why the file lives outside the app's own package.
 *
 * The callback handed to [run] must have these three methods, which the library calls by name:
 *   onNativeEvent(kind: String, detail: String)
 *   onNativeMedia(codec: String, bytes: ByteBuffer, timestampNs: Long, keyframe: Boolean, contiguous: Boolean): Boolean
 *   onNativeInput(bytes: ByteArray)
 */
@Keep
object NvstBridge {
    init { System.loadLibrary("opennow_nvst") }

    external fun create(): Long

    /** Blocks for the life of the stream, calling back on the calling thread. */
    external fun run(handle: Long, context: String, callback: Any)
    external fun stop(handle: Long)
    external fun input(handle: Long, bytes: ByteArray, partial: Boolean): Boolean
    external fun microphoneEnabled(handle: Long, enabled: Boolean)
    external fun microphone(handle: Long, samples: ShortArray, timestamp: Long, restart: Boolean): Boolean
    external fun keyframe(handle: Long)
}
