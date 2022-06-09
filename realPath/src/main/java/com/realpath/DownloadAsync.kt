package com.realpath

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*

class DownloadAsync(
    private val uri: Uri,
    private val callback: TaskCallBack,
    context: Context
) {

    private var folder: File? = null
    private var returnCursor: Cursor? = null
    private var inputStream: InputStream? = null
    private var errorReason = ""
    private var pathPlusName: String? = null

    init {
        CoroutineScope(Dispatchers.IO).launch {
            doWork(context)
        }
    }

    private suspend fun doWork(context: Context) = withContext(Dispatchers.IO) {
        var file: File? = null
        var size: Int? = -1
        folder = context.getExternalFilesDir("Temp")
        folder?.let { folder ->
            if (!folder.exists()) {
                if (folder.mkdirs()) {
                    Log.i("RealPather : ", "Temp folder createdd")
                }
            }
        }
        returnCursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            inputStream = context.contentResolver.openInputStream(uri)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }
        // File is now available
        withContext(Dispatchers.Main) {
            callback.realPatherOnPreExecute()
        }
        try {
            try {
                returnCursor?.let {
                    if (it.moveToFirst()) {
                        if (uri.scheme.equals("content")) {
                            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                            size = it.getLong(sizeIndex).toInt()
                        } else if (uri.scheme.equals("file")) {
                            val file = uri.path?.let { path -> File(path) }
                            size = file?.length()?.toInt()
                        }
                    }
                }
            } finally {
                if (returnCursor != null) returnCursor?.close()
            }

            pathPlusName = folder.toString() + "/" + getFileName(uri, context)
            file = File(folder.toString() + "/" + getFileName(uri, context))

            val bufferedInputStream = BufferedInputStream(inputStream)

            val fileOutputStream = FileOutputStream(file)

            val data = ByteArray(1024)

            var total: Long = 0
            var count: Int
            while (bufferedInputStream.read(data).also { count = it } != -1) {
                total += count.toLong()
                if (size != -1) {
                    try {
                        withContext(Dispatchers.Main) {
                            callback.realPatherOnProgressUpdate((total * 100 / size!!).toInt(),false)
                        }
                    } catch (e: Exception) {
                        Log.i("RealPather -", "File size is less than 1")
                        withContext(Dispatchers.Main) {
                            callback.realPatherOnProgressUpdate(0,true)
                        }
                    }
                }
                fileOutputStream.write(data, 0, count)
            }
            fileOutputStream.flush()
            fileOutputStream.close()
        } catch (e: Exception) {
            errorReason = e.message.toString()
        }

        file?.absolutePath?.let {
            onPostExecute(uri, it)
        }

    }


    @SuppressLint("Range")
    private fun getFileName(uri: Uri, context: Context): String? {
        var result: String? = null

        if (uri.scheme != null) {
            if (uri.scheme == "content") {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                }
                cursor?.close()
            }
        }

        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result?.let {
                    result = it.substring(cut + 1)
                }

            }
        }
        return result
    }

    private fun onPostExecute(uri: Uri, result: String?) {
        if (result == null) {
            callback.realPatherOnPostExecute(
                uri, pathPlusName,
                wasDriveFile = true,
                wasSuccessful = false,
                reason = errorReason
            )
        } else {
            callback.realPatherOnPostExecute(
                uri, pathPlusName,
                wasDriveFile = true,
                wasSuccessful = true,
                reason = ""
            )
        }
    }

}