package icu.humxc.yuka

import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream

private const val DEFAULT_I_FRAME_INTERVAL = 10 // seconds

private const val REPEAT_FRAME_DELAY_US = 100000 // repeat after 100ms

private const val KEY_MAX_FPS_TO_ENCODER = "max-fps-to-encoder"

// Keep the values in descending order
private val MAX_SIZE_FALLBACK = intArrayOf(2560, 1920, 1600, 1280, 1024, 800)
private const val MAX_CONSECUTIVE_ERRORS = 3

@OptIn(DelicateCoroutinesApi::class)
class VideoEncoder(
    private val output: OutputStream,
    width: Int, height: Int, frameRate: Int, bitRateMbps: Int
) {
    private val mediaCodec: MediaCodec
    private val quit = Channel<Boolean>(capacity = 1)
    private val err = Channel<Throwable>(capacity = 1)
    private var ts = 0L

    init {
        // 创建 MediaFormat
        val mediaFormat =
            MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRateMbps * 1000000)
        // must be present to configure the encoder, but does not impact the actual frame rate, which is variable
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 60)
        mediaFormat.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, DEFAULT_I_FRAME_INTERVAL)
        // display the very first frame, and recover from bad quality when no new frames
        mediaFormat.setLong(
            MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER,
            REPEAT_FRAME_DELAY_US.toLong()
        ) // µs
        if (frameRate > 0) {
            // The key existed privately before Android 10:
            // <https://android.googlesource.com/platform/frameworks/base/+/625f0aad9f7a259b6881006ad8710adce57d1384%5E%21/>
            // <https://github.com/Genymobile/scrcpy/issues/488#issuecomment-567321437>
            mediaFormat.setFloat(KEY_MAX_FPS_TO_ENCODER, frameRate.toFloat())
        }
        // 创建 MediaCodec
        mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mediaCodec.start()

        GlobalScope.launch(context = Dispatchers.IO) {
            while (!quit.tryReceive().isSuccess) {
                try {
                    val bufferInfo = MediaCodec.BufferInfo()
                    val outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0)
                    if (outputBufferIndex < 0) {
                        continue
                    }
                    val outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex) ?: break
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                    // 将编码后的数据写入文件
                    val buffer = ByteArray(bufferInfo.size)
                    outputBuffer.get(buffer)
                    output.write(buffer)
                    // 释放输出缓冲区
                    mediaCodec.releaseOutputBuffer(outputBufferIndex, false)
                } catch (e: Throwable) {
                    err.send(e)
                    break
                }
            }
        }
    }

    fun encodeImage(image: Image) {
        ts += 1
        // 获取输入缓冲区
        for (plane in image.planes) {
            val e = err.tryReceive().getOrNull()
            if (e != null) throw e
            val buffer = plane.buffer
            buffer.rewind()
            val limit = buffer.limit()
            while (buffer.hasRemaining()) {
                val inputBufferIndex = mediaCodec.dequeueInputBuffer(-1)
                if (inputBufferIndex < 0) {
                    throw IOException("dsdsadsa")
                }
                val inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex)
                    ?: throw IOException("inputBuffer is null")
                inputBuffer.clear()
                var bufferSize = 0
                while (inputBuffer.hasRemaining()) {
                    var lmt = buffer.position() + inputBuffer.remaining()
                    if (lmt > buffer.capacity()) {
                        lmt = buffer.capacity()
                    }
                    buffer.limit(lmt)
                    bufferSize += lmt - buffer.position()
                    inputBuffer.put(buffer)
                    buffer.limit(limit)
                    if (lmt == buffer.capacity()) {
                        break
                    }
                }
                mediaCodec.queueInputBuffer(
                    inputBufferIndex,
                    0,
                    bufferSize,
                    ts,
                    0
                )
            }
            buffer.flip()
        }
    }

    suspend fun release() {
        quit.send(true)
        mediaCodec.stop()
        mediaCodec.release()
        withContext(Dispatchers.IO) {
            output.close()
        }
    }
}
