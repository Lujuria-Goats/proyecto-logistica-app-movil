package com.apexvision.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.preference.PreferenceManager
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.apexvision.app.adapter.OrdersAdapter
import com.apexvision.app.databinding.ActivityMainBinding
import com.apexvision.app.db.AppDatabase
import com.apexvision.app.model.Order
import com.apexvision.app.ui.CameraActivity
import com.apexvision.app.ui.OrderDetailActivity

// --- IMPORTS DE MAPBOX ---
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
// -------------------------

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.core.graphics.createBitmap

class MainActivity : AppCompatActivity(), SensorEventListener, LocationListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: OrdersAdapter
    private lateinit var database: AppDatabase
    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager

    // Mapbox Managers
    private var pointAnnotationManager: PointAnnotationManager? = null

    // NUESTRO MARCADOR DE USUARIO (LA FLECHA)
    private var userAnnotation: PointAnnotation? = null

    // Estado
    private var isDarkMode = false
    private var ordersList = mutableListOf<Order>()
    private var lastUserLocation: Location? = null

    // VARIABLES BRÚJULA SUAVE
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private val ALPHA = 0.05f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDatabase.getDatabase(this)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val prefs = getSharedPreferences("APEX_PREFS", MODE_PRIVATE)
        isDarkMode = prefs.getBoolean("DARK_MODE", false)

        checkPermissions()
        setupRecyclerView()
        setupButtons()
    }

    private fun loadOrdersFromDatabase() {
        binding.shimmerViewContainer.startShimmer()
        binding.shimmerViewContainer.visibility = View.VISIBLE
        binding.recyclerOrders.visibility = View.GONE
        binding.layoutEmptyState.visibility = View.GONE

        lifecycleScope.launch {
            delay(1000)

            val dao = database.orderDao()
            var savedOrders = dao.getAllOrders()

            if (savedOrders.isEmpty()) {
                val mockData = listOf(
                    Order(1, "C.C. Santafé Medellín", "Tienda Nike", "PENDIENTE", 6.1968, -75.5736),
                    Order(2, "Plaza Botero", "Museo de Antioquia", "PENDIENTE", 6.2518, -75.5683),
                    Order(3, "Unicentro Medellín", "Librería Nacional", "PENDIENTE", 6.2423, -75.5898),
                    Order(4, "Parque Lleras", "Restaurante Mondongos", "PENDIENTE", 6.2089, -75.5670)
                )
                dao.insertAll(mockData)
                savedOrders = mockData
            }

            ordersList.clear()
            ordersList.addAll(savedOrders)
            adapter.notifyDataSetChanged()

            setupMapbox()

            binding.shimmerViewContainer.stopShimmer()
            binding.shimmerViewContainer.visibility = View.GONE

            if (ordersList.isEmpty()) {
                binding.recyclerOrders.visibility = View.GONE
                binding.layoutEmptyState.visibility = View.VISIBLE
            } else {
                binding.recyclerOrders.visibility = View.VISIBLE
                binding.layoutEmptyState.visibility = View.GONE
            }
        }
    }

    private fun setupMapbox() {
        val styleUrl = if (isDarkMode) Style.TRAFFIC_NIGHT else Style.MAPBOX_STREETS

        binding.mapView.getMapboxMap().loadStyleUri(styleUrl) { style ->

            // Inicializar Gestor de Pines
            val annotationApi = binding.mapView.annotations
            pointAnnotationManager = annotationApi.createPointAnnotationManager()

            // CREAR NUESTRA FLECHA (YO)
            // Usamos una posición inicial temporal hasta que el GPS detecte la real
            val startPoint = Point.fromLngLat(-75.5736, 6.1968)
            val arrowBitmap = convertDrawableToBitmap(AppCompatResources.getDrawable(this, R.drawable.ic_nav_arrow))

            if (arrowBitmap != null) {
                val userOptions = PointAnnotationOptions()
                    .withPoint(startPoint)
                    .withIconImage(arrowBitmap)
                    .withIconSize(1.0)

                userAnnotation = pointAnnotationManager?.create(userOptions)
            }

            // Pintar los pedidos
            updateMapMarkers()
        }

        // Centrar cámara inicial en Medellín
        binding.mapView.getMapboxMap().setCamera(
            CameraOptions.Builder()
                .center(Point.fromLngLat(-75.5736, 6.1968))
                .zoom(13.0)
                .build()
        )

        updateThemeIcon()
    }

    // --- LÓGICA DE BRÚJULA SUAVE (Manual) ---
    private fun lowPass(input: FloatArray, output: FloatArray): FloatArray {
        for (i in input.indices) output[i] = output[i] + ALPHA * (input[i] - output[i])
        return output
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) lowPass(event.values, accelerometerReading)
        else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) lowPass(event.values, magnetometerReading)

        val success = SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)
        if (success) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            // Convertir a grados
            var azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            if (azimuth < 0) azimuth += 360f

            // 🔥 INYECTAR GIRO SUAVE A NUESTRA FLECHA
            // El signo negativo es porque el mapa rota al revés que el icono
            userAnnotation?.iconRotate = azimuth.toDouble()

            // Forzar actualización visual
            if (userAnnotation != null) {
                pointAnnotationManager?.update(userAnnotation!!)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }

    // --- GPS ---
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (allPermissionsGranted()) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1f, this)
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 1f, this)
        }
    }

    override fun onLocationChanged(location: Location) {
        this.lastUserLocation = location

        // MOVER NUESTRA FLECHA
        val newPoint = Point.fromLngLat(location.longitude, location.latitude)
        userAnnotation?.point = newPoint

        if (userAnnotation != null) {
            pointAnnotationManager?.update(userAnnotation!!)
        }
    }

    private fun updateMapMarkers() {
        // OJO: No podemos usar deleteAll() porque borraría nuestra flecha
        // Así que borramos todo y recreamos todo, o gestionamos listas separadas.
        // Para simplificar, recreamos la flecha si se borra.

        pointAnnotationManager?.deleteAll()

        // Recrear Flecha si existía
        val arrowBitmap = convertDrawableToBitmap(AppCompatResources.getDrawable(this, R.drawable.ic_nav_arrow))
        if (arrowBitmap != null && lastUserLocation != null) {
            val userOptions = PointAnnotationOptions()
                .withPoint(Point.fromLngLat(lastUserLocation!!.longitude, lastUserLocation!!.latitude))
                .withIconImage(arrowBitmap)
            userAnnotation = pointAnnotationManager?.create(userOptions)
        } else if (arrowBitmap != null) {
            // Si no hay ubicación aún, la ponemos en el centro por defecto
            val userOptions = PointAnnotationOptions()
                .withPoint(Point.fromLngLat(-75.5736, 6.1968))
                .withIconImage(arrowBitmap)
            userAnnotation = pointAnnotationManager?.create(userOptions)
        }

        // Crear Pines de Pedidos
        val pinBitmap = convertDrawableToBitmap(AppCompatResources.getDrawable(this, R.drawable.ic_pin_apex))

        for (order in ordersList) {
            if (order.isSelected && pinBitmap != null) {
                val point = Point.fromLngLat(order.longitude, order.latitude)

                val pointAnnotationOptions = PointAnnotationOptions()
                    .withPoint(point)
                    .withIconImage(pinBitmap)
                    .withIconSize(1.5)

                pointAnnotationManager?.create(pointAnnotationOptions)
            }
        }
    }

    // Utilidad para convertir Vectores a Bitmaps (Mapbox lo necesita)
    private fun convertDrawableToBitmap(sourceDrawable: Drawable?): Bitmap? {
        if (sourceDrawable == null) return null
        return if (sourceDrawable is BitmapDrawable) {
            sourceDrawable.bitmap
        } else {
            val constantState = sourceDrawable.constantState ?: return null
            val drawable = constantState.newDrawable().mutate()
            val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setupButtons() {
        binding.cbSelectAll.setOnCheckedChangeListener { _, isChecked ->
            ordersList.forEach { order ->
                if (order.status != "ENTREGADO") {
                    order.isSelected = isChecked
                    lifecycleScope.launch { database.orderDao().updateOrder(order) }
                }
            }
            adapter.notifyDataSetChanged()
            updateMapMarkers()
        }

        binding.btnStartRoute.setOnClickListener { launchGoogleMapsRoute() }

        binding.btnCenterLocation.setOnClickListener {
            if (lastUserLocation != null) {
                binding.mapView.getMapboxMap().flyTo(
                    CameraOptions.Builder()
                        .center(Point.fromLngLat(lastUserLocation!!.longitude, lastUserLocation!!.latitude))
                        .zoom(16.0)
                        .build()
                )
            } else {
                Toast.makeText(this, "Buscando tu ubicación...", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnToggleTheme.setOnClickListener {
            isDarkMode = !isDarkMode
            val styleUrl = if (isDarkMode) Style.TRAFFIC_NIGHT else Style.MAPBOX_STREETS
            binding.mapView.getMapboxMap().loadStyleUri(styleUrl)
            updateThemeIcon()

            val prefs = getSharedPreferences("APEX_PREFS", MODE_PRIVATE)
            prefs.edit().putBoolean("DARK_MODE", isDarkMode).apply()
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_route -> {
                    // Estamos aquí, no hacer nada
                    true
                }
                R.id.navigation_profile -> {
                    // ABRIR PERFIL
                    val intent = Intent(this, com.apexvision.app.ui.ProfileActivity::class.java)
                    startActivity(intent)
                    false
                }
                else -> false
            }
        }
    }

    private fun updateThemeIcon() {
        if (isDarkMode) {
            binding.btnToggleTheme.setImageResource(R.drawable.ic_sun)
        } else {
            binding.btnToggleTheme.setImageResource(R.drawable.ic_moon)
        }
    }

    // --- RESTO DE FUNCIONES (RecyclerView, Permisos, Launcher) ---

    @SuppressLint("NotifyDataSetChanged")
    private val orderDetailLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val updatedOrder = result.data?.getSerializableExtra("ORDER_RESULT") as? Order
            if (updatedOrder != null) {
                val index = ordersList.indexOfFirst { it.id == updatedOrder.id }
                if (index != -1) {
                    if (updatedOrder.status == "ENTREGADO") {
                        updatedOrder.isSelected = false
                    }
                    ordersList[index] = updatedOrder
                    adapter.notifyItemChanged(index)
                    updateMapMarkers()
                    lifecycleScope.launch { database.orderDao().updateOrder(updatedOrder) }
                    if (updatedOrder.status == "ENTREGADO") {
                        playSuccessAnimation()
                        triggerHapticFeedback()
                    }
                }
            }
        }
    }

    private fun playSuccessAnimation() {
        binding.layoutSuccessParams.visibility = View.VISIBLE
        com.bumptech.glide.Glide.with(this).asGif().load(R.drawable.success_anim).into(binding.imgSuccess)
        binding.layoutSuccessParams.postDelayed({
            binding.layoutSuccessParams.visibility = View.GONE
        }, 2500)
    }

    private fun triggerHapticFeedback() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(100)
        }
    }

    private fun setupRecyclerView() {
        adapter = OrdersAdapter(
            ordersList,
            onCheckboxClick = {
                updateMapMarkers()
                lifecycleScope.launch { ordersList.forEach { database.orderDao().updateOrder(it) } }
            },
            onItemClick = { order ->
                val intent = Intent(this, OrderDetailActivity::class.java)
                intent.putExtra("ORDER_EXTRA", order)
                orderDetailLauncher.launch(intent)
            }
        )
        binding.recyclerOrders.layoutManager = LinearLayoutManager(this)
        binding.recyclerOrders.adapter = adapter
    }

    private fun launchGoogleMapsRoute() {
        val selectedOrders = ordersList.filter { it.isSelected }
        if (selectedOrders.isEmpty()) return
        val lastOrder = selectedOrders.last()
        val waypoints = selectedOrders.dropLast(1).joinToString("|") { "${it.latitude},${it.longitude}" }
        val uriString = StringBuilder("https://www.google.com/maps/dir/?api=1")
        uriString.append("&destination=${lastOrder.latitude},${lastOrder.longitude}")
        if (waypoints.isNotEmpty()) uriString.append("&waypoints=$waypoints")
        uriString.append("&travelmode=driving")
        try { startActivity(Intent(Intent.ACTION_VIEW, uriString.toString().toUri())) } catch (e: Exception) {}
    }

    private fun checkPermissions() {
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        } else {
            loadOrdersFromDatabase()
            startLocationUpdates()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all { ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS && allPermissionsGranted()) {
            loadOrdersFromDatabase()
            startLocationUpdates()
        }
    }

    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        locationManager.removeUpdates(this)
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
    }
}