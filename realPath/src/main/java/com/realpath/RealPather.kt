package com.realpath

import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.DocumentsContract
import android.util.Log
import android.webkit.MimeTypeMap
import com.realpath.Utils.errorReason
import com.realpath.Utils.getFilePath
import com.realpath.Utils.getRealPathFromURI_API19
import java.io.File
import java.util.*

class RealPather(private val context: Context, private val pickiTCallbacks: IPickedCallback) :
    TaskCallBack {
    private var isDriveFile = false
    private var isMsfDownload = false
    private var isFromUnknownProvider = false
    private var unknownProviderCalledBefore = false
    private var multiplePaths = ArrayList<String>()
    private var multipleUris = ArrayList<Uri>()
    private var wasMultipleFileSelected = false
    private var countMultiple = 0
    private var driveCountRef = 0
    private val enableProc = true
    fun getMultiplePaths(clipData: ClipData) {
        wasMultipleFileSelected = true
        countMultiple = clipData.itemCount
        for (i in 0 until clipData.itemCount) {
            val imageUri = clipData.getItemAt(i).uri
            multipleUris.add(imageUri)
            getPath(imageUri, Build.VERSION.SDK_INT)
        }
        if (!isDriveFile) {
            pickiTCallbacks.pickedMultipleCompleteListener(multipleUris, multiplePaths, true, "")
            multiplePaths.clear()
            wasMultipleFileSelected = false
            wasUriReturnedCalledBefore = false
            wasPreExecuteCalledBefore = false
        }
    }

    fun getPath(uri: Uri, APILevel: Int) {
        val returnedPath: String?
        if (APILevel >= 19) {
            var docId: String? = null
            /**This is only used when a file is selected from a sub-directory inside the Downloads folder
            and when the Uri returned has the msf: prefix*/
            try {
                docId = DocumentsContract.getDocumentId(uri)
            } catch (e: Exception) {
                // Ignore
            }
            // Drive file was selected
            if (isOneDrive(uri) || isDropBox(uri) || isGoogleDrive(uri)) {
                isDriveFile = true
                downloadFile(uri)
            } else if (docId != null && docId.startsWith("msf")) {
                val fileName = getFilePath(context, uri)
                try {
                    val file = File(
                        Environment.getExternalStorageDirectory()
                            .toString() + "/Download/" + fileName
                    )
                    // If the file exists in the Downloads directory
                    // we can return the path directly
                    if (file.exists()) {
                        pickiTCallbacks.pickedCompleteListener(
                            uri,
                            file.absolutePath,
                            wasDriveFile = false,
                            wasUnknownProvider = false,
                            wasSuccessful = true,
                            error = ""
                        )
                    } else {
                        if (enableProc) {
                            val parcelFileDescriptor: ParcelFileDescriptor?
                            try {
                                parcelFileDescriptor =
                                    context.contentResolver.openFileDescriptor(uri, "r")
                                val fd = parcelFileDescriptor!!.fd
                                val pid = Process.myPid()
                                val mediaFile = "/proc/$pid/fd/$fd"
                                val file1 = File(mediaFile)
                                if (file1.exists() && file1.canRead() && file1.canWrite()) {
                                    pickiTCallbacks.pickedCompleteListener(
                                        uri,
                                        file1.absolutePath,
                                        wasDriveFile = false,
                                        wasUnknownProvider = false,
                                        wasSuccessful = true,
                                        error = ""
                                    )
                                } else {
                                    isMsfDownload = true
                                    downloadFile(uri)
                                }
                            } catch (e: Exception) {
                                isMsfDownload = true
                                downloadFile(uri)
                            }
                        } else {
                            isMsfDownload = true
                            downloadFile(uri)
                        }
                    }
                } catch (e: Exception) {
                    isMsfDownload = true
                    downloadFile(uri)
                }
            } else {
                returnedPath = getRealPathFromURI_API19(context, uri)
                //Get the file extension
                val mime = MimeTypeMap.getSingleton()
                val subStringExtension =
                    returnedPath.toString().substring(returnedPath.toString().lastIndexOf(".") + 1)
                val extensionFromMime =
                    mime.getExtensionFromMimeType(context.contentResolver.getType(uri))

                // Path is null
                if (returnedPath == null || returnedPath == "") {
                    /** This can be caused by two situations
                    1. The file was selected from a third party app and the data column returned null (for example EZ File Explorer)
                    Some file providers (like EZ File Explorer) will return a URI as shown below:
                    content://es.fileexplorer.filebrowser.ezfilemanager.externalstorage.documents/document/primary%3AFolderName%2FNameOfFile.mp4
                    When you try to read the _data column, it will return null, without trowing an exception
                    In this case the file need to copied/created a new file in the temporary folder
                    2. There was an error
                    In this case call RealPatherOnCompleteListener and get/provide the reason why it failed

                    We first check if it was called before, avoiding multiple calls*/
                    if (!unknownProviderCalledBefore) {
                        unknownProviderCalledBefore = true
                        if (uri.scheme != null && uri.scheme == ContentResolver.SCHEME_CONTENT) {
                            //Then we check if the _data colomn returned null
                            if (errorReason() != null && errorReason() == "dataReturnedNull") {
                                isFromUnknownProvider = true
                                //Copy the file to the temporary folder
                                downloadFile(uri)
                                return
                            } else if (errorReason() != null && errorReason()!!
                                    .contains("column '_data' does not exist")
                            ) {
                                isFromUnknownProvider = true
                                //Copy the file to the temporary folder
                                downloadFile(uri)
                                return
                            } else if (errorReason() != null && errorReason() == "uri") {
                                isFromUnknownProvider = true
                                //Copy the file to the temporary folder
                                downloadFile(uri)
                                return
                            }
                        }
                    }
                    //Else an error occurred, get/set the reason for the error
                    pickiTCallbacks.pickedCompleteListener(
                        uri,
                        returnedPath,
                        wasDriveFile = false,
                        wasUnknownProvider = false,
                        wasSuccessful = false,
                        error = errorReason()
                    )
                } else {
                    /**This can be caused by two situations
                    1. The file was selected from an unknown provider (for example a file that was downloaded from a third party app)
                    2. getExtensionFromMimeType returned an unknown mime type for example "audio/mp4"

                    When this is case we will copy/write the file to the temp folder, same as when a file is selected from Google Drive etc.
                    We provide a name by getting the text after the last "/"
                    Remember if the extension can't be found, it will not be added, but you will still be able to use the file**/
                    //Todo: Add checks for unknown file extensions
                    if (subStringExtension != "jpeg" && subStringExtension != extensionFromMime && uri.scheme != null && uri.scheme == ContentResolver.SCHEME_CONTENT) {
                        // First check if the file is available
                        // With issue #48 the file is available
                        try {
                            val checkIfExist = File(returnedPath)
                            if (checkIfExist.exists()) {
                                pickiTCallbacks.pickedCompleteListener(
                                    uri,
                                    returnedPath,
                                    wasDriveFile = false,
                                    wasUnknownProvider = false,
                                    wasSuccessful = true,
                                    error = ""
                                )
                                return
                            }
                        } catch (e: Exception) {
                            //Ignore
                        }
                        isFromUnknownProvider = true
                        downloadFile(uri)
                        return
                    }

                    // Path can be returned, no need to make a "copy"
                    if (wasMultipleFileSelected) {
                        multiplePaths.add(returnedPath)
                    } else {
                        pickiTCallbacks.pickedCompleteListener(
                            uri,
                            returnedPath,
                            wasDriveFile = false,
                            wasUnknownProvider = false,
                            wasSuccessful = true,
                            error = ""
                        )
                    }
                }
            }
        }
    }

    // Create a new file from the Uri that was selected
    private fun downloadFile(uri: Uri) {
        DownloadAsync(uri, this, context)
    }

    fun isUnknownProvider(uri: Uri): Boolean {
        val returnedPath = getRealPathFromURI_API19(context, uri)
        val mime = MimeTypeMap.getSingleton()
        val subStringExtension =
            returnedPath.toString().substring(returnedPath.toString().lastIndexOf(".") + 1)
        val extensionFromMime = mime.getExtensionFromMimeType(context.contentResolver.getType(uri))
        if (isOneDrive(uri) || isDropBox(uri) || isGoogleDrive(uri)) {
            return false
        } else {
            // Path is null
            if (returnedPath == null || returnedPath == "") {
                if (uri.scheme != null && uri.scheme == ContentResolver.SCHEME_CONTENT) {
                    if (errorReason() != null && errorReason() == "dataReturnedNull") {
                        return true
                    } else if (errorReason() != null && errorReason()!!
                            .contains("column '_data' does not exist")
                    ) {
                        return true
                    } else if (errorReason() != null && errorReason() == "uri") {
                        return true
                    }
                }
                return false
            } else {
                if (subStringExtension != "jpeg" && subStringExtension != extensionFromMime && uri.scheme != null && uri.scheme == ContentResolver.SCHEME_CONTENT) {
                    return true
                }
            }
        }
        return false
    }

    fun isDriveFile(uri: Uri): Boolean {
        return isOneDrive(uri) || isDropBox(uri) || isGoogleDrive(uri)
    }

    fun wasLocalFileSelected(uri: Uri): Boolean {
        return !isDropBox(uri) && !isGoogleDrive(uri) && !isOneDrive(uri)
    }

    // Check different providers
    private fun isDropBox(uri: Uri): Boolean {
        return uri.toString().lowercase(Locale.getDefault()).contains("content://com.dropbox.")
    }

    private fun isGoogleDrive(uri: Uri): Boolean {
        return uri.toString().lowercase(Locale.getDefault()).contains("com.google.android.apps")
    }

    private fun isOneDrive(uri: Uri): Boolean {
        return uri.toString().lowercase(Locale.getDefault())
            .contains("com.microsoft.skydrive.content")
    }

    // RealPather callback Listeners
    private var wasUriReturnedCalledBefore = false
    override fun realPatherOnUriReturned() {
        if (wasMultipleFileSelected) {
            if (!wasUriReturnedCalledBefore) {
                pickiTCallbacks.pickedUriReturned()
                wasUriReturnedCalledBefore = true
            }
        } else {
            pickiTCallbacks.pickedUriReturned()
        }
    }

    private var wasPreExecuteCalledBefore = false
    override fun realPatherOnPreExecute() {
        if (wasMultipleFileSelected || isMsfDownload) {
            if (!wasPreExecuteCalledBefore) {
                wasPreExecuteCalledBefore = true
                pickiTCallbacks.pickedUriReturned()
            }
        } else {
            pickiTCallbacks.pickedUriReturned()
        }
    }

    override fun realPatherOnProgressUpdate(progress: Int, isLess: Boolean) {
        pickiTCallbacks.pickedProgressUpdate(progress,isLess)
    }

    override fun realPatherOnPostExecute(
        uri: Uri,
        path: String?,
        wasDriveFile: Boolean,
        wasSuccessful: Boolean,
        reason: String?
    ) {
        unknownProviderCalledBefore = false
        if (wasSuccessful) {
            if (wasMultipleFileSelected) {
                driveCountRef++
                if (path != null) {
                    multiplePaths.add(path)
                }

                multipleUris.add(uri)
                if (driveCountRef == countMultiple) {
                    wasPreExecuteCalledBefore = false
                    wasUriReturnedCalledBefore = false
                    pickiTCallbacks.pickedMultipleCompleteListener(
                        multipleUris,
                        multiplePaths,
                        true,
                        ""
                    )
                    multiplePaths.clear()
                    multipleUris.clear()
                    wasUriReturnedCalledBefore = false
                    wasPreExecuteCalledBefore = false
                }
            } else {
                if (isDriveFile) {
                    pickiTCallbacks.pickedCompleteListener(
                        uri, path,
                        wasDriveFile = true,
                        wasUnknownProvider = false,
                        wasSuccessful = true,
                        error = ""
                    )
                } else if (isFromUnknownProvider) {
                    pickiTCallbacks.pickedCompleteListener(
                        uri, path,
                        wasDriveFile = false,
                        wasUnknownProvider = true,
                        wasSuccessful = true,
                        error = ""
                    )
                } else if (isMsfDownload) {
                    pickiTCallbacks.pickedCompleteListener(
                        uri, path,
                        wasDriveFile = false,
                        wasUnknownProvider = true,
                        wasSuccessful = true,
                        error = ""
                    )
                }
            }
        } else {
            if (isDriveFile) {
                pickiTCallbacks.pickedCompleteListener(
                    uri, path,
                    wasDriveFile = true,
                    wasUnknownProvider = false,
                    wasSuccessful = false,
                    error = reason
                )
            } else if (isFromUnknownProvider) {
                pickiTCallbacks.pickedCompleteListener(
                    uri, path,
                    wasDriveFile = false,
                    wasUnknownProvider = true,
                    wasSuccessful = false,
                    error = reason
                )
            }
        }
    }

    // Delete the temporary folder
    fun deleteTemporaryFile(context: Context) {
        val folder = context.getExternalFilesDir("Temp")
        if (folder != null) {
            if (deleteDirectory(folder)) {
                Log.i("RealPather ", " deleteDirectory was called")
            }
        }
    }

    private fun deleteDirectory(path: File): Boolean {
        if (path.exists()) {
            val files = path.listFiles() ?: return false
            for (file in files) {
                if (file.isDirectory) {
                    deleteDirectory(file)
                } else {
                    val wasSuccessful = file.delete()
                    if (wasSuccessful) {
                        Log.i("Deleted ", "successfully")
                    }
                }
            }
        }
        return path.delete()
    }
}