package com.realpath

import android.net.Uri

interface TaskCallBack {
    fun realPatherOnUriReturned()
    fun realPatherOnPreExecute()
    fun realPatherOnProgressUpdate(progress: Int, isLess: Boolean)
    fun realPatherOnPostExecute(
        uri: Uri,
        path: String?,
        wasDriveFile: Boolean,
        wasSuccessful: Boolean,
        reason: String?
    )
}