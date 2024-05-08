package icu.humxc.yuka

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.genymobile.scrcpy.wrappers.SurfaceControl
import io.devicefarmer.minicap.utils.DisplayManagerGlobal
import kotlinx.coroutines.channels.Channel
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.system.exitProcess

class SurfaceProvider(displayID: Int) : ImageReader.OnImageAvailableListener {
    private val channel = Channel<Image>(capacity = 1)
    private var frameCache: Image? = null
    private val secure =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Build.VERSION.SDK_INT == Build.VERSION_CODES.R && "S" != Build.VERSION.CODENAME
    private val display = SurfaceControl.createDisplay("minicap", secure)

    val displayInfo = DisplayManagerGlobal.getDisplayInfo(displayID)
    private val imageReader = ImageReader.newInstance(
        displayInfo.size.width,
        displayInfo.size.height,
        PixelFormat.RGBA_8888,
        2
    )
    private var err: Throwable? = null

    init {
        //initialise the surface to get the display in the ImageReader
        SurfaceControl.openTransaction()
        try {
            SurfaceControl.setDisplaySurface(display, imageReader.surface)
            SurfaceControl.setDisplayProjection(
                display,
                0,
                Rect(
                    0, 0,
                    displayInfo.size.width,
                    displayInfo.size.height
                ),
                Rect(
                    0, 0,
                    displayInfo.size.width,
                    displayInfo.size.height
                )
            )
            SurfaceControl.setDisplayLayerStack(display, displayInfo.layerStack)
        } finally {
            SurfaceControl.closeTransaction()
        }
        imageReader.setOnImageAvailableListener(this, Handler(Looper.getMainLooper()))
    }

    override fun onImageAvailable(reader: ImageReader) {
        try {
            channel.tryReceive().getOrNull()?.close()
            val image = reader.acquireLatestImage() ?: return
            if (!channel.trySend(image).isSuccess) {
                image.close()
            }
        } catch (e: IllegalAccessError) {
            e.printStackTrace()
            exitProcess(1)
        } catch (e: Exception) {
            err = e
        }
    }

    fun close() {
        imageReader.close()
        SurfaceControl.destroyDisplay(display)
        channel.close()
    }

    // 不要对返回值做任何修改，否则可能会出问题。例如读取其中 buffer 之后，需要将其 flip
    suspend fun frame(): Image {
        val f = channel.tryReceive().getOrNull()
        if (f != null) {
            frameCache?.close()
            frameCache = f
            return f
        }
        if (frameCache != null) return frameCache as Image
        return waitFrame()
    }

    // 不要对返回值做任何修改，否则可能会出问题。例如读取其中 buffer 之后，需要将其 flip
    suspend fun waitFrame(): Image {
        if (err != null) {
            throw err as Throwable
        }
        val f = channel.receive()
        frameCache?.close()
        frameCache = f
        return f
    }
}

fun imageToPNG(img: Image): ByteArray {
    val out = ByteArrayOutputStream(1024)
    with(img) {
        val planes: Array<Image.Plane> = planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride: Int = planes[0].pixelStride
        val rowStride: Int = planes[0].rowStride
        val rowPadding: Int = rowStride - pixelStride * width
        // createBitmap can be resources consuming
        Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            android.graphics.Bitmap.Config.ARGB_8888
        ).apply {
            copyPixelsFromBuffer(buffer)
            buffer.flip()
        }.apply {
            compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }
    }
    out.flush()
    return out.toByteArray()
}

fun imageToJPG(img: Image, quality: Int): ByteArray {
    val out = ByteArrayOutputStream(1024)
    with(img) {
        val planes: Array<Image.Plane> = planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride: Int = planes[0].pixelStride
        val rowStride: Int = planes[0].rowStride
        val rowPadding: Int = rowStride - pixelStride * width
        // createBitmap can be resources consuming
        Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            android.graphics.Bitmap.Config.ARGB_8888
        ).apply {
            copyPixelsFromBuffer(buffer)
            buffer.flip()
        }.apply {
            compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
        }
    }
    out.flush()
    return out.toByteArray()
}