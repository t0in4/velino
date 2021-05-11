package com.eyehail.velino



import android.Manifest
import android.R.attr.bitmap
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Switch
import android.widget.Toast

import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.drawToBitmap
import androidx.core.view.get
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.dialog_brush_size.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

import java.util.*
import kotlin.math.max
import kotlin.math.min


class MainActivity : AppCompatActivity() {

    private var mImageButtonCurrentPaint: ImageButton? = null
    //using this variables to store a last movement of drawable_view
    private var viewXListener:Float = 0.0f
    private var viewYListener:Float = 0.0f
    //add zoom to iv_background
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var scaleFactor = 1.0f
    private lateinit var imageView: ImageView



    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hide the action bar
        val actionBar = supportActionBar

        actionBar!!.hide()
        setContentView(R.layout.activity_main)


        // for enable onTouchListener
        var listener = android.view.View.OnTouchListener(function = { view, motionEvent ->

            if (motionEvent.action == MotionEvent.ACTION_MOVE) {

                view.y = motionEvent.rawY - view.height/2
                view.x = motionEvent.rawX - view.width/2

                viewXListener = view.x
                viewYListener = view.y
            }

            true

        })
        //for disable onTouchListener
        var unlistener = android.view.View.OnTouchListener(function = {  view, motionEvent ->
            view.y = viewYListener
            view.x = viewXListener
            false
        })



        ib_move.setOnClickListener(object : View.OnClickListener {
            var checker = true
            override fun onClick(view: View?) {
                if (checker) {
                    iv_background.setOnTouchListener(listener)
                drawing_view.setOnTouchListener(listener)
                    ib_text.text = getString(R.string.move_both)
                    checker = false

                } else {
                    iv_background.setOnTouchListener(unlistener)
                    drawing_view.setOnTouchListener(unlistener)
                    ib_text.text = getString(R.string.zoom_photo)
                    checker = true
                }
            }

        })
        //add zoom to iv_background
        //imageView = findViewById(R.id.iv_background)
        scaleGestureDetector = ScaleGestureDetector(this, ScaleListener())




        mImageButtonCurrentPaint = ll_paint_colors[1] as ImageButton
        mImageButtonCurrentPaint!!.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_pressed)
        )

        drawing_view.setSizeForBrush(20.toFloat()) //for work i had to add
        ib_brush.setOnClickListener {
            showBrushSizeChooseDialog()
        }
        ib_gallery.setOnClickListener {
            if (isReadStorageAllowed()) {

                val pickPhotoIntent = Intent(Intent.ACTION_PICK,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

                startActivityForResult(pickPhotoIntent, GALLERY)

            } else {
                requestStoragePermission()
            }
        }
        ib_undo.setOnClickListener {
            drawing_view.onClickUndo()
        }
        ib_save.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val ivBackground = drawing_view
                val bitmap = ivBackground.drawToBitmap()
                saveImageToGallery(bitmap)
            }
            if (isReadStorageAllowed()) {
                BitmapAsyncTask(getBitmapFromView(drawing_view)).execute()
            } else {
                requestStoragePermission()
            }
        }





    }

    //add zoom to iv_background
    override fun onTouchEvent(motionEvent: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(motionEvent)
        return true
    }
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(scaleGestureDetector: ScaleGestureDetector): Boolean {
            scaleFactor *= scaleGestureDetector.scaleFactor
            scaleFactor = max(0.1f, min(scaleFactor, 10.0f))
            /*imageView.scaleX = scaleFactor - imageView.width/2
            imageView.scaleY = scaleFactor*/
            iv_background.scaleX = scaleFactor
            iv_background.scaleY = scaleFactor
            return true
        }
    } //end of adding zoom to iv_background


    private fun saveImageToGallery(bitmap: Bitmap){
        val fos: OutputStream
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {

                val resolver = contentResolver
                val contentValues = ContentValues()
                contentValues.put(MediaColumns.DISPLAY_NAME, "Image_" + System.currentTimeMillis() / 1000
                        + ".png")
                contentValues.put(MediaColumns.MIME_TYPE, "image/png")


                contentValues.put(MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "TestFolder")


                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues)
                fos = resolver.openOutputStream(Objects.requireNonNull(imageUri)!!)!!
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos)
                Objects.requireNonNull<OutputStream?>(fos)
                Toast.makeText(this, "Image Saved", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Image Not Saved", Toast.LENGTH_SHORT).show()
        }
    }



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == GALLERY) {
                try {
                    if (data!!.data != null) {
                        iv_background.visibility = View.VISIBLE
                        iv_background.setImageURI(data.data)
                    } else {
                        Toast.makeText(
                                this@MainActivity,
                                "Error in parsing the image or its corrupted",
                                Toast.LENGTH_SHORT).show()

                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    private fun showBrushSizeChooseDialog(){
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush size: ")
        val microBtn = brushDialog.ib_micro_brush
        microBtn.setOnClickListener {
            drawing_view.setSizeForBrush(1.toFloat())
            brushDialog.dismiss()
        }
        val miniBtn = brushDialog.ib_mini_brush
        miniBtn.setOnClickListener{
            drawing_view.setSizeForBrush(5.toFloat())
            brushDialog.dismiss()
        }
        val smallBtn = brushDialog.ib_small_brush
        smallBtn.setOnClickListener{
            drawing_view.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        }
        val mediumBtn = brushDialog.ib_medium_brush
        mediumBtn.setOnClickListener{
            drawing_view.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        }
        val largeBtn = brushDialog.ib_large_brush
        largeBtn.setOnClickListener{
            drawing_view.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        }
        brushDialog.show()

    }
    fun paintClicked(view: View) {
        if (view != mImageButtonCurrentPaint) {
            val imageButton = view as ImageButton

            val colorTag = imageButton.tag.toString()
            drawing_view.setColor(colorTag)
            imageButton.setImageDrawable(
                    ContextCompat.getDrawable(this, R.drawable.pallet_pressed)

            )
            mImageButtonCurrentPaint!!.setImageDrawable(
                    ContextCompat.getDrawable(this, R.drawable.pallet_normal)

            )
            mImageButtonCurrentPaint = view
        }


    }
    private fun requestStoragePermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE).toString())){
            Toast.makeText(this, "Need permission to add a background",
                    Toast.LENGTH_LONG).show()
        }
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_PERMISSION_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0]
                    == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                        this@MainActivity,
                        "Permission granted now you can read the storage",
                        Toast.LENGTH_LONG).show()
            }else {
                Toast.makeText(
                        this@MainActivity,
                        "Oops you just denied the permission",
                        Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun isReadStorageAllowed():Boolean{
        val result = ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun getBitmapFromView(view: View) : Bitmap {
        val returnedBitmap = Bitmap.createBitmap(
                view.width,
                view.height,
                Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)

        canvas.drawColor(Color.TRANSPARENT)
        view.draw(canvas)


        return returnedBitmap

    }

    private inner class BitmapAsyncTask(val mBitmap: Bitmap): ViewModel(){

        fun execute() = viewModelScope.launch {

            onPreExecute()

            val result = doInBackground()

            onPostExecute(result)

        }

        private lateinit var mProgressDialog: Dialog

        private fun onPreExecute() {

            showProgressDialog()

        }

        private suspend fun doInBackground(): String = withContext(Dispatchers.IO) {

            var result = ""

            if(mBitmap != null){

                try{

                    val bytes = ByteArrayOutputStream()

                    mBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)

                    val f = File(externalCacheDir!!.absoluteFile.toString()
                            + File.separator + "Velino_"
                            + System.currentTimeMillis() / 1000
                            + ".png")

                    val fos = FileOutputStream(f)

                    fos.write(bytes.toByteArray())

                    fos.close()

                    result = f.absolutePath

                } catch (e: Exception){

                    result = ""

                    e.printStackTrace()

                }

            }

            return@withContext result

        }

        private fun onPostExecute(result: String?) {

            cancelDialog()

            if(!result!!.isEmpty()) {

                Toast.makeText(

                        this@MainActivity,

                        "File Saved Succesfully : $result",

                        Toast.LENGTH_SHORT

                ).show()

            } else {

                Toast.makeText(this@MainActivity,

                        "Something went wrong while saving file",

                        Toast.LENGTH_SHORT).show()

            }

            MediaScannerConnection.scanFile(this@MainActivity, arrayOf(result), null){

                path, uri -> val shareIntent = Intent()

                shareIntent.action = Intent.ACTION_SEND

                shareIntent.putExtra(Intent.EXTRA_STREAM, uri)

                shareIntent.type = "image/png"


                startActivity(

                        Intent.createChooser(

                                shareIntent, "Share"

                        )

                )

            }

        }

        private fun showProgressDialog(){

            mProgressDialog = Dialog(this@MainActivity)

            mProgressDialog.setContentView(R.layout.dialog_custom_progress)

            mProgressDialog.show()

        }

        private fun cancelDialog(){

            mProgressDialog.dismiss()

        }

    }

    companion object {
        private const val STORAGE_PERMISSION_CODE = 1
        private const val GALLERY = 2

    }
}