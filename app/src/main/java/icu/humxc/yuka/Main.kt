package icu.humxc.yuka

import android.os.Looper
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class Main {
    companion object {
        @OptIn(DelicateCoroutinesApi::class)
        @JvmStatic
        fun main(args: Array<String>) {
            //called because we are not run from the Android environment
            Looper.prepareMainLooper()
            GlobalScope.launch(context = Dispatchers.IO) {
                try {
                    mainFunc(args)
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
                Looper.getMainLooper().quit()
            }
            Looper.loop()
        }

    }
}

suspend fun mainFunc(args: Array<String>) {
    execCommand(args)
}
