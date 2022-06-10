package com.realpaths

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.realpath.PathPickerCallback
import com.realpath.RealPather
import com.realpaths.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), PathPickerCallback {

    companion object {
        const val REQ_ID_WRITE_EXTERNAL_STORAGE = 493
    }

    private var realPather: RealPather? = null
    private lateinit var pathList: ArrayList<UriModel>
    private lateinit var binding: ActivityMainBinding
    private lateinit var mAdapter: MainAdapter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        //Initialize RealPather
        realPather = RealPather(this, this)

        pathList = ArrayList()
        mAdapter = MainAdapter()
        binding.run {
            rv.run {
                layoutManager = LinearLayoutManager(this@MainActivity)
                setHasFixedSize(true)
                adapter = mAdapter
            }

            buttonForVideo.setOnClickListener {
                openGallery("video")
            }
            buttonForImage.setOnClickListener {
                try {
                    openGallery("image")
                } catch (e: Exception) {
                }

            }

        }

    }

    private fun openGallery(videoOrImage: String) {
        //  first check if permissions was granted
        if (Build.VERSION.SDK_INT < 28) {
            if (!checkSelfPermission()) return
        }
        if (videoOrImage == "video") {
            val intent: Intent =
                if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                    Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                } else {
                    Intent(Intent.ACTION_PICK, MediaStore.Video.Media.INTERNAL_CONTENT_URI)
                }
            //  In this example we will set the type to video
            intent.type = "video/*"
            intent.action = Intent.ACTION_GET_CONTENT
            intent.putExtra("return-data", true)
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            activityResultLauncher.launch(intent)
        } else {
            val intent: Intent =
                if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                    Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                } else {
                    Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
                }
            //  In this example we will set the type to Image
            intent.type = "image/*"
            intent.action = Intent.ACTION_GET_CONTENT
            intent.putExtra("return-data", true)
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            activityResultLauncher.launch(intent)

        }
    }


    private fun checkSelfPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ),
                REQ_ID_WRITE_EXTERNAL_STORAGE
            )
            return false
        }
        return true
    }

    private var activityResultLauncher = registerForActivityResult(
        StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data

            /**
             * Get path from RealPather (The path will be returned in RealPatherOnCompleteListener)

             * If the selected file is from Dropbox/Google Drive or OnDrive:
            Then it will be "copied" to your app directory (see path example below) and when done the path will be returned in RealPatherOnCompleteListener
            storage/emulated/0/Android/data/your.package.name/files/Temp/tempDriveFile.mp4
            else the path will directly be returned in RealPatherOnCompleteListener
             * */

            val clipData = data?.clipData
            if (clipData != null) {
                val numberOfFilesSelected = clipData.itemCount
                if (numberOfFilesSelected > 1) {
                    realPather?.getMultiplePaths(clipData)
                } else {
                    realPather?.getPath(clipData.getItemAt(0).uri, Build.VERSION.SDK_INT)
                }
            } else {
                data!!.data?.let { realPather?.getPath(it, Build.VERSION.SDK_INT) }
            }
        }
    }

    override fun pickedUriReturned() {

    }

    override fun pickedStartListener() {

    }

    override fun pickedProgressUpdate(progress: Int, isLess: Boolean) {
        if (!isLess) {
            binding.roundedProgressBar.run {
                visibility = View.VISIBLE
                setProgressPercentage(progress.toDouble())
                if (progress == 100) visibility = View.GONE
            }
        }

    }

    override fun pickedCompleteListener(
        uri: Uri,
        path: String?,
        wasDriveFile: Boolean,
        wasUnknownProvider: Boolean,
        wasSuccessful: Boolean,
        error: String?
    ) {
        path?.let {
            pathList.add(UriModel(uri, path))
            mAdapter.submitList(null)
            mAdapter.submitList(pathList)
        }

    }

    override fun pickedMultipleCompleteListener(
        uri: ArrayList<Uri>,
        paths: ArrayList<String>,
        wasSuccessful: Boolean,
        error: String?
    ) {

        for (i in paths.indices) {
            pathList.add(UriModel(uri[i], paths[i]))
        }
        mAdapter.submitList(null)
        mAdapter.submitList(pathList)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        if (realPather != null) {
            realPather?.deleteTemporaryFile(this)
        }
    }
}