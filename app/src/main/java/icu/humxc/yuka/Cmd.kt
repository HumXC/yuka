package icu.humxc.yuka

import com.beust.jcommander.JCommander
import com.beust.jcommander.MissingCommandException
import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.OutputStream

@Parameters(commandDescription = "输出png", commandNames = ["png"])
class CommandPng {
    @Parameter(
        names = ["-o"],
        description = "写入到文件，为空时输出到 stdout",
        arity = 1,
    )
    var output: String = ""

}

@Parameters(commandDescription = "输出jpg", commandNames = ["png"])
class CommandJpg {
    @Parameter(
        names = ["-o"],
        description = "写入到文件，为空时输出到 stdout",
        arity = 1,
    )
    var output: String = ""

    @Parameter(
        names = ["-q"],
        description = "jpg 格式的压缩质量 1-100",
        arity = 1,
    )
    var quality: Int = 100
}

@Parameters(commandDescription = "使用 socket", commandNames = ["socket"])
class CommandSocket {
    @Parameter(
        names = ["-n"],
        description = "socket 的名称",
        arity = 1,
    )
    var name: String = "yuka"
}

@Parameters(commandDescription = "输出视频流", commandNames = ["video"])
class CommandVideo {
    @Parameter(
        names = ["-n"],
        description = "socket 的名称",
        arity = 1,
    )
    var name: String = "yuka"
}

suspend fun execCommand(args: Array<String>) {
    val cPng = CommandPng()
    val cJpg = CommandJpg()
    val cVideo = CommandVideo()
    val cSocket = CommandSocket()
    val commander = JCommander.Builder()
        .addCommand("png", cPng)
        .addCommand("jpg", cJpg)
        .addCommand("video", cVideo)
        .addCommand("socket", cSocket)
        .build()
    try {
        commander.parse(*args)
    } catch (e: MissingCommandException) {
        commander.usage()
    }

    when (commander.parsedCommand) {
        null -> commander.usage()
        "png" -> {
            val sp = SurfaceProvider(0)
            var out: OutputStream = System.out
            try {
                if (cPng.output != "") {
                    out = withContext(Dispatchers.IO) {
                        FileOutputStream(cPng.output)
                    }
                }
                withContext(Dispatchers.IO) {
                    out.write(imageToPNG(sp.frame()))
                }
            } finally {
                sp.close()
            }
        }

        "jpg" -> {
            val sp = SurfaceProvider(0)
            var out: OutputStream = System.out
            try {
                if (cPng.output != "") {
                    out = withContext(Dispatchers.IO) {
                        FileOutputStream(cPng.output)
                    }
                }
                withContext(Dispatchers.IO) {
                    out.write(imageToJPG(sp.frame(), cJpg.quality))
                }
            } finally {
                sp.close()
            }
        }

        "video" -> {
            val sp = SurfaceProvider(0)

            var out: OutputStream = System.out
            if (cPng.output != "") {
                out = withContext(Dispatchers.IO) {
                    FileOutputStream(cPng.output)
                }
            }

            val e = VideoEncoder(
                out,
                sp.displayInfo.size.width,
                sp.displayInfo.size.height,
                60,
                8
            )
            var c = 100

            while (c > 0) {
                val f = sp.waitFrame()
                try {
                    e.encodeImage(f)
                } catch (e: Throwable) {
                    e.printStackTrace()
                    break
                }
                c -= 1
            }
            e.release()
        }

        "socket" -> {
            SocketServer(cSocket.name).serve()
        }
    }

}