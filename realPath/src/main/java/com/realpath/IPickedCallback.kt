package com.realpath

import android.net.Uri

interface IPickedCallback {

    fun pickedUriReturned()
    fun pickedStartListener()
    fun pickedProgressUpdate(progress: Int,isLess:Boolean)
    fun pickedCompleteListener(
        uri: Uri,
        path: String?,
        wasDriveFile: Boolean,
        wasUnknownProvider: Boolean,
        wasSuccessful: Boolean,
        error: String?
    )

    fun pickedMultipleCompleteListener(
        uri: ArrayList<Uri>,
        paths: ArrayList<String>,
        wasSuccessful: Boolean,
        error: String?
    )
}