package com.apexvision.app.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.apexvision.app.databinding.ActivityCameraBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private var currentPhotoPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startCamera()
        setupButtons()

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun setupButtons() {
        // 1. Tomar la foto
        binding.btnTakePhoto.setOnClickListener {
            takePhoto()
        }

        // 2. RECHAZAR (X): Volver a intentar
        binding.btnRetry.setOnClickListener {
            // Borrar el archivo temporal para no llenar el celular de basura
            if (currentPhotoPath != null) {
                val file = File(currentPhotoPath!!)
                if (file.exists()) {
                    file.delete()
                }
            }
            currentPhotoPath = null
            showCameraPreview() // Volver a la cámara
        }

        // 3. CONFIRMAR (Check): Guardar y salir
        binding.btnConfirm.setOnClickListener {
            if (currentPhotoPath != null) {
                val resultIntent = Intent()
                resultIntent.putExtra("PHOTO_PATH", currentPhotoPath)
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e("ApexCamera", "Error al iniciar cámara", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis())
        val file = File(externalCacheDir, "$name.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("ApexCamera", "Error al capturar foto", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    currentPhotoPath = file.absolutePath

                    // Corremos esto en el hilo principal para tocar la UI
                    runOnUiThread {
                        showPhotoResult(file.absolutePath)
                    }
                }
            }
        )
    }

    // --- CORRECCIÓN VISUAL ---
    private fun showPhotoResult(path: String) {
        // 1. Cargar imagen
        Glide.with(this)
            .load(path)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .into(binding.imgPhotoPreview)

        // 2. Mostrar la foto ENCIMA de la cámara (No apagamos la cámara)
        // binding.viewFinder.visibility = View.GONE  <-- ESTO ERA LO QUE CAUSABA EL ERROR (Lo quitamos)

        binding.imgPhotoPreview.visibility = View.VISIBLE // La foto tapa la cámara
        binding.layoutResult.visibility = View.VISIBLE    // Botones X y Check
        binding.btnTakePhoto.visibility = View.GONE       // Ocultar disparador
    }

    private fun showCameraPreview() {
        // 1. Ocultar la foto y los botones de decisión
        binding.imgPhotoPreview.visibility = View.GONE
        binding.layoutResult.visibility = View.GONE

        // 2. Mostrar el disparador de nuevo
        binding.viewFinder.visibility = View.VISIBLE
        binding.btnTakePhoto.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}