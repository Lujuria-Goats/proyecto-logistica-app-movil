package com.apexvision.app.ui

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.apexvision.app.R
import com.apexvision.app.databinding.ActivityOrderDetailBinding
import com.apexvision.app.model.Order
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.ncorti.slidetoact.SlideToActView

class OrderDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrderDetailBinding
    private lateinit var order: Order
    private var isDarkMode = false // Inicia en modo CLARO por defecto en el detalle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        order = intent.getSerializableExtra("ORDER_EXTRA") as? Order ?: return finish()

        setupUI()
        setupMap(if (isDarkMode) Style.TRAFFIC_NIGHT else Style.MAPBOX_STREETS)
        setupActions()
    }

    // --- SETUP UI (TEXTOS Y FOTOS) ---
    private fun setupUI() {
        binding.tvDetailAddress.text = order.address
        binding.tvDetailCustomer.text = "Cliente: ${order.customerName}"

        if (order.photoPath != null) {
            loadPhotoView(order.photoPath!!)
            unlockSlider()
        } else {
            binding.layoutPhotoPlaceholder.visibility = View.VISIBLE
            binding.ivDetailPhoto.visibility = View.GONE
            binding.tvRetakeAction.visibility = View.GONE

            if (order.requiresPhoto) {
                binding.sliderDetailComplete.isLocked = true
                binding.sliderDetailComplete.text = "FOTO REQUERIDA"
            } else {
                unlockSlider()
            }
        }

        if (order.status == "ENTREGADO") {
            binding.tvDetailStatus.text = "ENTREGADO"
            binding.tvDetailStatus.setTextColor(Color.parseColor("#4CAF50"))
            binding.sliderDetailComplete.visibility = View.GONE

            // Bloquear interacciones si ya se entregó
            binding.tvRetakeAction.visibility = View.GONE
            binding.ivRetakeIcon.visibility = View.GONE
            binding.cardPhotoContainer.isEnabled = false
            binding.cardPhotoContainer.setOnClickListener(null)
        } else {
            binding.tvDetailStatus.text = "PENDIENTE"
            binding.tvDetailStatus.setTextColor(Color.parseColor("#D4AF37"))
        }

        updateThemeIcon()
    }

    // --- MAPA MAPBOX ---
    private fun setupMap(styleUri: String) {
        binding.mapDetail.getMapboxMap().loadStyleUri(styleUri) { style ->
            createMarker() // Re-creamos el marcador cada vez que carga el estilo
        }
    }

    private fun createMarker() {
        val annotationApi = binding.mapDetail.annotations
        val pointAnnotationManager = annotationApi.createPointAnnotationManager()
        pointAnnotationManager.deleteAll() // Limpiar

        val bitmap = convertDrawableToBitmap(AppCompatResources.getDrawable(this, R.drawable.ic_pin_apex))

        if (bitmap != null) {
            val point = Point.fromLngLat(order.longitude, order.latitude)
            val pointAnnotationOptions = PointAnnotationOptions()
                .withPoint(point)
                .withIconImage(bitmap)
                .withIconSize(1.5)

            pointAnnotationManager.create(pointAnnotationOptions)

            binding.mapDetail.getMapboxMap().setCamera(
                CameraOptions.Builder()
                    .center(point)
                    .zoom(16.0)
                    .build()
            )
        }
    }

    private fun convertDrawableToBitmap(sourceDrawable: Drawable?): Bitmap? {
        if (sourceDrawable == null) return null
        return if (sourceDrawable is BitmapDrawable) {
            sourceDrawable.bitmap
        } else {
            val constantState = sourceDrawable.constantState ?: return null
            val drawable = constantState.newDrawable().mutate()
            val bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth, drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        }
    }

    // --- ACCIONES DE BOTONES ---
    private fun setupActions() {
        binding.btnClose.setOnClickListener { finish() }

        // Botón de Tema (Nuevo)
        binding.btnToggleThemeDetail.setOnClickListener {
            isDarkMode = !isDarkMode
            val newStyle = if (isDarkMode) Style.TRAFFIC_NIGHT else Style.MAPBOX_STREETS
            setupMap(newStyle) // Recargar mapa con nuevo estilo
            updateThemeIcon()
        }

        if (order.status != "ENTREGADO") {
            binding.cardPhotoContainer.setOnClickListener { launchCamera() }
            binding.tvRetakeAction.setOnClickListener { launchCamera() }
        }

        binding.btnReportIssue.setOnClickListener { showReportOptions() }

        binding.sliderDetailComplete.onSlideCompleteListener = object : SlideToActView.OnSlideCompleteListener {
            override fun onSlideComplete(view: SlideToActView) {
                val resultIntent = Intent()
                order.status = "ENTREGADO"
                resultIntent.putExtra("ORDER_RESULT", order)
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        }
    }

    private fun updateThemeIcon() {
        if (isDarkMode) {
            binding.btnToggleThemeDetail.setImageResource(R.drawable.ic_sun)
        } else {
            binding.btnToggleThemeDetail.setImageResource(R.drawable.ic_moon)
        }
    }

    // ... (Resto de funciones: showReportOptions, launchCamera, etc. IGUALES) ...

    private fun showReportOptions() {
        val options = arrayOf("Cliente ausente", "Dirección incorrecta", "Zona peligrosa", "Vehículo averiado", "Paquete dañado", "Cliente rechazó")
        MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered)
            .setTitle("Reportar Incidente")
            .setItems(options) { dialog, which ->
                val selectedReason = options[which]
                order.incidentReport = selectedReason
                Toast.makeText(this, "Reporte enviado: $selectedReason", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun launchCamera() {
        val intent = Intent(this, CameraActivity::class.java)
        takePhotoLauncher.launch(intent)
    }

    private fun unlockSlider() {
        binding.sliderDetailComplete.isLocked = false
        binding.sliderDetailComplete.text = "DESLIZA PARA FINALIZAR"
        binding.sliderDetailComplete.outerColor = Color.parseColor("#333333")
    }

    private fun loadPhotoView(path: String) {
        binding.ivDetailPhoto.visibility = View.VISIBLE
        binding.layoutPhotoPlaceholder.visibility = View.GONE
        binding.tvRetakeAction.visibility = View.VISIBLE

        Glide.with(this)
            .load(path)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .centerCrop()
            .into(binding.ivDetailPhoto)

        binding.ivDetailPhoto.setOnClickListener {
            showFullScreenPhoto(path)
        }
    }

    private fun showFullScreenPhoto(path: String) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_photo)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val imgView = dialog.findViewById<ImageView>(R.id.ivDialogPhoto)
        val btnClose = dialog.findViewById<View>(R.id.btnCloseDialog)

        Glide.with(this).load(path).into(imgView)
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val path = result.data?.getStringExtra("PHOTO_PATH")
            if (path != null) {
                order.photoPath = path
                loadPhotoView(path)
                unlockSlider()
                Toast.makeText(this, "Evidencia guardada", Toast.LENGTH_SHORT).show()
            }
        }
    }
}