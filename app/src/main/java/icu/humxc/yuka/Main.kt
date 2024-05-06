package icu.humxc.yuka

import android.os.Looper
import icu.humxc.yuka.utils.DisplayManagerGlobal

class Main {
    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            //called because we are not run from the Android environment
            Looper.getMainLooper()
            val displayIds = DisplayManagerGlobal.getDisplayIds()
            displayIds.iterator()
            for (element in displayIds) {
                println(element)
            }
            val info = DisplayManagerGlobal.getDisplayInfo(0)
            println(info)
            Looper.loop()
        }

    }
}
