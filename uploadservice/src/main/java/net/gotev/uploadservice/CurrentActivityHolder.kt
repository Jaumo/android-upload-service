package net.gotev.uploadservice

import android.app.Activity

interface CurrentActivityHolder {
    fun getCurrentActivity(): Activity?
}