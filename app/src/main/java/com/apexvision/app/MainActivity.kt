package com.apexvision.app

import android.Manifest
import android.animation.ValueAnimator
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
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.apexvision.app.adapter.OrdersAdapter
import com.apexvision.app.databinding.ActivityMainBinding
import com.apexvision.app.db.AppDatabase
import com.apexvision.app.model.Order
import com.apexvision.app.ui.OrderDetailActivity
import com.apexvision.app.ui.ProfileActivity
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

class MainActivity : AppCompatActivity(), SensorEventListener, LocationListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: OrdersAdapter
    private lateinit var database: AppDatabase
    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager

    private var pointAnnotationManager: PointAnnotationManager? = null
    private var userAnnotation: PointAnnotation? = null

    private var isDarkMode = false
    private var ordersList = mutableListOf<Order>()
    private var lastUserLocation: Location? = null

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var currentRotation: Double = 0.0
    private var rotationAnimator: ValueAnimator? = null

    private var jobsList = mutableListOf<com.apexvision.app.model.Job>()
    private lateinit var jobsAdapter: com.apexvision.app.adapter.JobsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDatabase.getDatabase(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val prefs = getSharedPreferences("APEX_PREFS", MODE_PRIVATE)
        isDarkMode = prefs.getBoolean("DARK_MODE", false)

        setupRecyclerView()
        setupJobsRecyclerView()
        setupButtons()
        checkPermissions()
        loadJobsFromDatabase()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadOrdersFromDatabase() {
        binding.shimmerViewContainer.startShimmer()
        binding.shimmerViewContainer.visibility = View.VISIBLE
        binding.recyclerOrders.visibility = View.GONE
        binding.layoutEmptyState.visibility = View.GONE

        lifecycleScope.launch {
            delay(1000)

            val dao = database.orderDao()

            if (dao.getAllOrders().isEmpty()) {
                val mockData = listOf(
                    Order(1, 1, "C.C. Santafé", "Nike Store", "PENDIENTE", 6.1968, -75.5736),
                    Order(2, 1, "Terminal del Norte", "Transportes Veloz", "PENDIENTE", 6.2300, -75.5800),
                    Order(3, 1, "U. de Antioquia", "Facultad Ingeniería", "PENDIENTE", 6.2672, -75.5690),
                    Order(4, 1, "Jardín Botánico", "Restaurante In Situ", "PENDIENTE", 6.2707, -75.5656),
                    Order(20, 3, "Plaza Botero", "Museo de Antioquia", "PENDIENTE", 6.2518, -75.5683),
                    Order(21, 3, "Alpujarra", "Centro Administrativo", "PENDIENTE", 6.2453, -75.5736),
                    Order(22, 3, "Edificio Coltejer", "Oficina 302", "PENDIENTE", 6.2498, -75.5693),
                    Order(23, 3, "Parque Berrio", "Estación Metro", "PENDIENTE", 6.2491, -75.5689),
                    Order(24, 3, "San Ignacio", "Comfama", "PENDIENTE", 6.2450, -75.5640)
                )
                dao.insertAll(mockData)
            }

            val prefs = getSharedPreferences("APEX_PREFS", MODE_PRIVATE)
            val activeRouteId = prefs.getInt("ACTIVE_ROUTE_ID", 1)
            val routeOrders = dao.getOrdersByRoute(activeRouteId)

            val cleanedOrders = routeOrders.map { order ->
                if (order.status == "ENTREGADO" && order.isSelected) {
                    order.isSelected = false
                    dao.updateOrder(order)
                }
                order
            }

            ordersList.clear()
            ordersList.addAll(cleanedOrders)
            adapter.notifyDataSetChanged()

            if (pointAnnotationManager == null) {
                setupMapbox()
            } else {
                updateMapMarkers()
            }

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

        binding.mapView.getMapboxMap().loadStyleUri(styleUrl) {
            val annotationApi = binding.mapView.annotations
            pointAnnotationManager = annotationApi.createPointAnnotationManager()

            val lat = lastUserLocation?.latitude ?: 6.1968
            val lng = lastUserLocation?.longitude ?: -75.5736
            val startPoint = Point.fromLngLat(lng, lat)

            val arrowBitmap = convertDrawableToBitmap(AppCompatResources.getDrawable(this, R.drawable.ic_nav_arrow))

            if (arrowBitmap != null) {
                val userOptions = PointAnnotationOptions()
                    .withPoint(startPoint)
                    .withIconImage(arrowBitmap)
                    .withIconSize(1.0)
                userAnnotation = pointAnnotationManager?.create(userOptions)
            }

            updateMapMarkers()
        }

        val centerPoint = if (ordersList.isNotEmpty()) {
            Point.fromLngLat(ordersList[0].longitude, ordersList[0].latitude)
        } else {
            Point.fromLngLat(-75.5736, 6.1968)
        }

        binding.mapView.getMapboxMap().setCamera(
            CameraOptions.Builder()
                .center(centerPoint)
                .zoom(15.0)
                .build()
        )
        updateThemeIcon()
    }

    private fun animateArrowRotation(targetRotation: Double) {
        if (userAnnotation == null) return

        var diff = targetRotation - currentRotation
        while (diff < -180) diff += 360
        while (diff > 180) diff -= 360

        if (abs(diff) < 1.0) return

        val finalRotation = currentRotation + diff

        rotationAnimator?.cancel()
        rotationAnimator = ValueAnimator.ofFloat(currentRotation.toFloat(), finalRotation.toFloat()).apply {
            duration = 300
            addUpdateListener { animation ->
                val animatedValue = (animation.animatedValue as Float).toDouble()
                userAnnotation?.iconRotate = animatedValue
                pointAnnotationManager?.update(userAnnotation!!)
                currentRotation = animatedValue
            }
            start()
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            var azimuth = Math.toDegrees(orientationAngles[0].toDouble())
            azimuth = (azimuth + 360) % 360
            animateArrowRotation(azimuth)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (allPermissionsGranted()) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1f, this)
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 1f, this)
        }
    }

    override fun onLocationChanged(location: Location) {
        lastUserLocation = location
        val newPoint = Point.fromLngLat(location.longitude, location.latitude)

        userAnnotation?.let { annotation ->
            annotation.point = newPoint
            pointAnnotationManager?.update(annotation)
        }
    }

    private fun updateMapMarkers() {
        val annotations = pointAnnotationManager?.annotations
        annotations?.forEach {
            if (it != userAnnotation) pointAnnotationManager?.delete(it)
        }

        val pinBitmap = convertDrawableToBitmap(AppCompatResources.getDrawable(this, R.drawable.ic_pin_apex))

        if (pinBitmap != null) {
            for (order in ordersList) {
                if (order.isSelected) {
                    val point = Point.fromLngLat(order.longitude, order.latitude)
                    val pointAnnotationOptions = PointAnnotationOptions()
                        .withPoint(point)
                        .withIconImage(pinBitmap)
                        .withIconSize(1.5)
                    pointAnnotationManager?.create(pointAnnotationOptions)
                }
            }
        }
    }

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

    private fun setupJobsRecyclerView() {
        jobsAdapter = com.apexvision.app.adapter.JobsAdapter(jobsList) { job ->
            val intent = Intent(this, com.apexvision.app.ui.JobDetailActivity::class.java).apply {
                putExtra("COMPANY", job.companyName)
                putExtra("TITLE", job.description)
                putExtra("SALARY", job.salary)
            }
            startActivity(intent)
        }
        binding.layoutJobs.recyclerJobs.layoutManager = LinearLayoutManager(this)
        binding.layoutJobs.recyclerJobs.adapter = jobsAdapter
    }

    private fun loadJobsFromDatabase() {
        lifecycleScope.launch {
            val jobDao = database.jobDao()
            var savedJobs = jobDao.getAllJobs()

            if (savedJobs.isEmpty()) {
                val mockJobs = listOf(
                    com.apexvision.app.model.Job(1, "Rappi Turbo", "Conductor Zona Norte", "$120.000 / día"),
                    com.apexvision.app.model.Job(2, "Domino's Pizza", "Domiciliario Fin de Semana", "$90.000 / turno"),
                    com.apexvision.app.model.Job(3, "Farmatodo", "Ruta Nocturna", "$150.000 / noche"),
                    com.apexvision.app.model.Job(4, "Envía Colvanes", "Mensajería Intermunicipal", "$1.8M / mes")
                )
                jobDao.insertAll(mockJobs)
                savedJobs = mockJobs
            }
            jobsList.clear()
            jobsList.addAll(savedJobs)
            if (::jobsAdapter.isInitialized) jobsAdapter.notifyDataSetChanged()
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
            binding.btnToggleTheme.setImageResource(if (isDarkMode) R.drawable.ic_sun else R.drawable.ic_moon)
            val prefs = getSharedPreferences("APEX_PREFS", MODE_PRIVATE)
            prefs.edit { putBoolean("DARK_MODE", isDarkMode) }
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_route -> {
                    binding.groupRoute.visibility = View.VISIBLE
                    binding.layoutJobs.root.visibility = View.GONE
                    if (ordersList.isEmpty()) binding.layoutEmptyState.visibility = View.VISIBLE
                    true
                }
                R.id.navigation_jobs -> {
                    binding.groupRoute.visibility = View.GONE
                    binding.layoutEmptyState.visibility = View.GONE
                    binding.shimmerViewContainer.visibility = View.GONE
                    binding.layoutJobs.root.visibility = View.VISIBLE
                    true
                }
                R.id.navigation_profile -> {
                    val intent = Intent(this, ProfileActivity::class.java)
                    startActivity(intent)
                    false
                }
                else -> false
            }
        }
    }

    private fun updateThemeIcon() {
        if (isDarkMode) binding.btnToggleTheme.setImageResource(R.drawable.ic_sun)
        else binding.btnToggleTheme.setImageResource(R.drawable.ic_moon)
    }

    private val orderDetailLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val wasDelivered = result.data?.getBooleanExtra("EXTRA_DELIVERY_SUCCESS", false) ?: false
            loadOrdersFromDatabase()
            if (wasDelivered) {
                playSuccessAnimation()
                triggerHapticFeedback()
            }
        }
    }

    private fun playSuccessAnimation() {
        binding.layoutSuccessParams.visibility = View.VISIBLE
        com.bumptech.glide.Glide.with(this).asGif().load(R.drawable.success_anim).into(binding.imgSuccess)
        binding.layoutSuccessParams.postDelayed({ binding.layoutSuccessParams.visibility = View.GONE }, 2500)
    }

    @SuppressLint("MissingPermission")
    private fun triggerHapticFeedback() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (vibrator.hasVibrator()) vibrator.vibrate(VibrationEffect.createOneShot(400, 255))
    }

    private fun setupRecyclerView() {
        adapter = OrdersAdapter(
            ordersList = ordersList,
            onCheckboxClick = {
                updateMapMarkers()
                lifecycleScope.launch {
                    ordersList.forEach { database.orderDao().updateOrder(it) }
                }
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
        val selectedOrders = ordersList.filter { it.isSelected && it.status != "ENTREGADO" }
        if (selectedOrders.isEmpty()) {
            Toast.makeText(this, "Selecciona al menos un destino pendiente", Toast.LENGTH_SHORT).show()
            return
        }
        val lastOrder = selectedOrders.last()
        val waypoints = selectedOrders.dropLast(1).joinToString("|") { "${it.latitude},${it.longitude}" }
        val uriString = StringBuilder("https://www.google.com/maps/dir/?api=1")
        uriString.append("&destination=${lastOrder.latitude},${lastOrder.longitude}")
        if (waypoints.isNotEmpty()) uriString.append("&waypoints=$waypoints")
        uriString.append("&travelmode=driving")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uriString.toString().toUri()))
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir el mapa", Toast.LENGTH_SHORT).show()
        }
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
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
        } else {
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
            sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
        startLocationUpdates()
        loadJobsFromDatabase()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        locationManager.removeUpdates(this)
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.VIBRATE)
    }
}