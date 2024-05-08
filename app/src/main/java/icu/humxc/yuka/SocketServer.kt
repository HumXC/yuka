package icu.humxc.yuka

import android.net.LocalServerSocket
import android.net.LocalSocket
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

const val HEAD_SIZE = 5
const val HEAD_TYPE_PNG: Byte = 1
const val HEAD_TYPE_JPG: Byte = 2

class Head(var type: Byte) {
    var size: Int = 0
}

class SocketServer(name: String) {
    private val serverSocket = LocalServerSocket(name)

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun serve() {
        while (true) {
            val clientSocket = serverSocket.accept()
            GlobalScope.launch(context = Dispatchers.IO) {
                handler(clientSocket)
            }
        }
    }

    private suspend fun handler(socket: LocalSocket) {
        println("新连接")
        assert(socket.inputStream != null)
        assert(socket.outputStream != null)

        val input = BufferedReader(InputStreamReader(socket.inputStream))
        val output = socket.outputStream
        val sp = SurfaceProvider(0)
        while (true) {
            var msg: String
            try {
                msg = withContext(Dispatchers.IO) {
                    input.readLine()
                }.trim()
            } catch (e: NullPointerException) {
                break
            }
            try {
                println("来自对端: $msg")
                if (msg == "png") {
                    runBlocking {
                        write(output, Head(HEAD_TYPE_PNG), imageToPNG(sp.frame()))
                    }
                } else if (msg.startsWith("jpg")) {
                    var quality = 100
                    val args = msg.split(" ")
                    if (args.size > 1) {
                        quality = args[1].toInt()
                    }
                    write(output, Head(HEAD_TYPE_JPG), imageToJPG(sp.frame(), quality))
                }
            } catch (e: IOException) {
                break
            } catch (e: Throwable) {
                println("未知错误")
                e.printStackTrace()
                break
            }
        }
        sp.close()
        println("断开连接")
    }

    private fun write(output: OutputStream, head: Head, data: ByteArray) {
        head.size = data.size
        val byteArray = ByteBuffer.allocate(HEAD_SIZE).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put(head.type)
            putInt(head.size)
        }.array()
        output.write(byteArray)
        output.flush()
        output.write(data)
        output.flush()
        println("send: " + HEAD_SIZE.toString() + " + " + data.size.toString() + " bytes")
    }
}