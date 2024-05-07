package icu.humxc.yuka

import android.graphics.PixelFormat
import android.graphics.Rect
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.genymobile.scrcpy.wrappers.SurfaceControl
import io.devicefarmer.minicap.utils.DisplayManagerGlobal
import kotlinx.coroutines.channels.Channel

class Main {
    companion object {

        @JvmStatic
        suspend fun main(args: Array<String>) {
            //called because we are not run from the Android environment
            Looper.getMainLooper()
            val sp = SurfaceProvider(0)
            while (true) {
                val frame = sp.frame()
                println(frame.height)
            }
            Looper.loop()
        }

    }
}

class SurfaceProvider(displayID: Int) : ImageReader.OnImageAvailableListener {
    private val channel = Channel<ImageReader>(capacity = 2)
    private val isClosed = false
    private val secure =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Build.VERSION.SDK_INT == Build.VERSION_CODES.R && "S" != Build.VERSION.CODENAME
    private val display = SurfaceControl.createDisplay("minicap", secure)

    private val displayInfo = DisplayManagerGlobal.getDisplayInfo(displayID)
    private val imageReader = ImageReader.newInstance(
        displayInfo.size.width,
        displayInfo.size.height,
        PixelFormat.RGBA_8888,
        2
    )
    private val handler: Handler = Handler(Looper.getMainLooper())

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
        imageReader.setOnImageAvailableListener(this, handler)
    }


    override fun onImageAvailable(reader: ImageReader?) {
        if (reader == null) {
            println("？？？？？？？？？？")
            return
        }
        channel.trySend(reader)
    }

    fun close() {
        if (isClosed) return
        SurfaceControl.destroyDisplay(display)
        channel.close()
    }

    suspend fun frame(): ImageReader {
        return channel.receive()
    }

}