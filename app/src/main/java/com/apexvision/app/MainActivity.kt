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
import com.apexvision.app.ui.OrderDetailActivity
import com.apexvision.app.ui.ProfileActivity // Asegúrate de tener este import

// Mapbox Imports
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
import androidx.core.graphics.createBitmap
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

    private var currentAzimuth = 0f

    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private val ALPHA = 0.05f

    private var jobsList = mutableListOf<com.apexvision.app.model.Job>() // Lista dinámica
    private lateinit var jobsAdapter: com.apexvision.app.adapter.JobsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar BD
        database = AppDatabase.getDatabase(this)

        // Inicializar Sensores
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Cargar preferencias
        val prefs = getSharedPreferences("APEX_PREFS", MODE_PRIVATE)
        isDarkMode = prefs.getBoolean("DARK_MODE", false)

        // Configuración Inicial
        setupRecyclerView()     // Configura lista de pedidos
        setupJobsRecyclerView() // Configura lista de trabajos
        setupButtons()          // Configura clicks

        // Carga de Datos
        checkPermissions()      // Esto desencadena loadOrdersFromDatabase()
        loadJobsFromDatabase()  // <--- IMPORTANTE: Cargar los trabajos al iniciar
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

            // 1. SI LA BD ESTÁ VACÍA, CREAR DATOS PARA TODAS LAS RUTAS
            if (dao.getAllOrders().isEmpty()) {
                val mockData = listOf(
                    // --- RUTA 1: MEDELLÍN NORTE ---
                    Order(1, 1, "C.C. Santafé", "Nike Store", "PENDIENTE", 6.1968, -75.5736),
                    Order(2, 1, "Terminal del Norte", "Transportes Veloz", "PENDIENTE", 6.2300, -75.5800),
                    Order(3, 1, "U. de Antioquia", "Facultad Ingeniería", "PENDIENTE", 6.2672, -75.5690),
                    Order(4, 1, "Jardín Botánico", "Restaurante In Situ", "PENDIENTE", 6.2707, -75.5656),

                    // --- RUTA 2: MEDELLÍN SUR (Envigado) ---
                    Order(10, 2, "Parque Envigado", "El Rancherito", "PENDIENTE", 6.1759, -75.5917),
                    Order(11, 2, "Mayorcade Plaza", "Cine Colombia", "PENDIENTE", 6.1610, -75.6048),
                    Order(12, 2, "Parque Sabaneta", "Los Buñuelos", "PENDIENTE", 6.1517, -75.6156),

                    // --- RUTA 3: MEDELLÍN CENTRO ---
                    Order(20, 3, "Plaza Botero", "Museo de Antioquia", "PENDIENTE", 6.2518, -75.5683),
                    Order(21, 3, "Alpujarra", "Centro Administrativo", "PENDIENTE", 6.2453, -75.5736),
                    Order(22, 3, "Edificio Coltejer", "Oficina 302", "PENDIENTE", 6.2498, -75.5693),
                    Order(23, 3, "Parque Berrio", "Estación Metro", "PENDIENTE", 6.2491, -75.5689),
                    Order(24, 3, "San Ignacio", "Comfama", "PENDIENTE", 6.2450, -75.5640)
                )
                dao.insertAll(mockData)
            }

            // 2. LEER PREFERENCIA DE RUTA ACTIVA
            val prefs = getSharedPreferences("APEX_PREFS", MODE_PRIVATE)
            val activeRouteId = prefs.getInt("ACTIVE_ROUTE_ID", 1)

            // 3. CARGAR ÓRDENES DE LA RUTA
            val routeOrders = dao.getOrdersByRoute(activeRouteId)

            // --- 4. 🧹 LIMPIEZA FORZADA DE CHECKS (NUEVO) ---
            // Recorremos la lista. Si dice "ENTREGADO" pero tiene check, se lo quitamos.
            val cleanedOrders = routeOrders.map { order ->
                if (order.status == "ENTREGADO" && order.isSelected) {
                    order.isSelected = false // Visualmente desmarcar
                    dao.updateOrder(order)   // Guardar corrección en BD
                }
                order
            }

            // 5. ACTUALIZAR LISTA Y UI
            ordersList.clear()
            ordersList.addAll(cleanedOrders) // Usamos la lista limpia
            adapter.notifyDataSetChanged()

            // Recargar mapa
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
            // 1. Configurar Manager de Anotaciones
            val annotationApi = binding.mapView.annotations
            pointAnnotationManager = annotationApi.createPointAnnotationManager()

            // 2. Configurar Posición Inicial
            val lat = lastUserLocation?.latitude ?: 6.1968
            val lng = lastUserLocation?.longitude ?: -75.5736
            val startPoint = Point.fromLngLat(lng, lat)

            // 3. Crear Icono de Flecha
            val arrowBitmap = convertDrawableToBitmap(AppCompatResources.getDrawable(this, R.drawable.ic_nav_arrow))

            if (arrowBitmap != null) {
                val userOptions = PointAnnotationOptions()
                    .withPoint(startPoint)
                    .withIconImage(arrowBitmap)
                    .withIconSize(1.0)

                // Crear y guardar la referencia
                userAnnotation = pointAnnotationManager?.create(userOptions)
            }

            // 4. Pintar los pedidos (ahora que el mapa está listo)
            updateMapMarkers()
        }

        // 5. Configuración de Cámara Inicial
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

    // --- (RESTO DE FUNCIONES IGUAL QUE ANTES: Sensores, GPS, Adapter, etc) ---
    // Mantenlas tal cual las tenías en tu código anterior.

    private fun lowPass(input: FloatArray, output: FloatArray): FloatArray {
        for (i in input.indices) output[i] = output[i] + ALPHA * (input[i] - output[i])
        return output
    }

    override fun onSensorChanged(event: SensorEvent) {
        // 1. Suavizado de datos crudos (Low Pass Filter)
        // ALPHA debe ser pequeño (ej: 0.05f) para que sea suave.
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            lowPass(event.values, accelerometerReading)
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            lowPass(event.values, magnetometerReading)
        }

        // 2. Calcular Rotación
        val success = SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)

        if (success) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            // Convertir a grados (0 a 360)
            var azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()

            // Normalizar a 0-360 positivo
            azimuth = (azimuth + 360) % 360

            // 3. CORRECCIÓN DE EJE INVERTIDO (La clave para Mapbox)
            // Mapbox rota en sentido horario. Si tu flecha gira al revés, invierte este valor.
            // Probamos con negativo primero:
            val targetRotation = azimuth

            // 4. INTERPOLACIÓN SUAVE (Evitar saltos bruscos)
            // Calculamos la diferencia mínima entre el ángulo actual y el destino
            var delta = targetRotation - currentAzimuth

            // Corregir el "Salto del Norte" (359 -> 0)
            while (delta < -180) delta += 360
            while (delta > 180) delta -= 360

            // Aplicar factor de suavizado (0.1 = 10% de corrección por frame)
            // Si se siente muy lento, sube a 0.2. Si vibra mucho, baja a 0.05.
            currentAzimuth += delta * 0.1f

            // 5. ACTUALIZAR MAPBOX
            userAnnotation?.let { annotation ->
                annotation.iconRotate = currentAzimuth.toDouble()
                pointAnnotationManager?.update(annotation)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }

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

        // Actualizar posición en Mapbox de forma segura
        userAnnotation?.let { annotation ->
            annotation.point = newPoint
            pointAnnotationManager?.update(annotation)
        }
    }

    private fun updateMapMarkers() {
        pointAnnotationManager?.deleteAll()

        // Recrear flecha de usuario
        val arrowBitmap = convertDrawableToBitmap(AppCompatResources.getDrawable(this, R.drawable.ic_nav_arrow))
        if (arrowBitmap != null) {
            val lat = lastUserLocation?.latitude ?: 6.1968
            val lng = lastUserLocation?.longitude ?: -75.5736
            val userOptions = PointAnnotationOptions().withPoint(Point.fromLngLat(lng, lat)).withIconImage(arrowBitmap)
            userAnnotation = pointAnnotationManager?.create(userOptions)
        }

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
        // Accedemos al recycler dentro del include layoutJobs
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
        // 1. CHECKBOX "SELECCIONAR TODAS"
        binding.cbSelectAll.setOnCheckedChangeListener { _, isChecked ->
            ordersList.forEach { order ->
                if (order.status != "ENTREGADO") {
                    order.isSelected = isChecked
                    // Actualizar base de datos en segundo plano
                    lifecycleScope.launch { database.orderDao().updateOrder(order) }
                }
            }
            adapter.notifyDataSetChanged()
            // updateMapMarkers() // Descomenta si tienes la función para repintar el mapa
        }

        // 2. BOTÓN FLOTANTE "IR AL GPS"
        binding.btnStartRoute.setOnClickListener { launchGoogleMapsRoute() }

        // 3. BOTÓN MAPA: CENTRAR UBICACIÓN (Lógica Mapbox)
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

        // 4. BOTÓN MAPA: TEMA DÍA/NOCHE (Lógica Mapbox)
        binding.btnToggleTheme.setOnClickListener {
            isDarkMode = !isDarkMode

            // Cambiar estilo de Mapbox
            val styleUrl = if (isDarkMode) Style.TRAFFIC_NIGHT else Style.MAPBOX_STREETS
            binding.mapView.getMapboxMap().loadStyleUri(styleUrl)

            // Cambiar icono del botón (Luna/Sol)
            binding.btnToggleTheme.setImageResource(if (isDarkMode) R.drawable.ic_sun else R.drawable.ic_moon)

            // Guardar preferencia
            val prefs = getSharedPreferences("APEX_PREFS", MODE_PRIVATE)
            prefs.edit { putBoolean("DARK_MODE", isDarkMode) }
        }

        // 5. BARRA DE NAVEGACIÓN INFERIOR
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_route -> {
                    binding.groupRoute.visibility = View.VISIBLE

                    binding.layoutJobs.root.visibility = View.GONE

                    if (ordersList.isEmpty()) binding.layoutEmptyState.visibility = View.VISIBLE

                    true
                }

                R.id.navigation_jobs -> {
                    // Ocultar Ruta
                    binding.groupRoute.visibility = View.GONE
                    binding.layoutEmptyState.visibility = View.GONE
                    binding.shimmerViewContainer.visibility = View.GONE

                    // ⚠️ CAMBIO: Mostrar Convocatorias usando .root
                    binding.layoutJobs.root.visibility = View.VISIBLE
                    true
                }


                R.id.navigation_profile -> {
                    // === ABRIR PERFIL ===
                    // Como el perfil es una Activity nueva, la lanzamos y no cambiamos la vista actual
                    val intent = Intent(this, ProfileActivity::class.java)
                    startActivity(intent)
                    false // Retornamos false para que el botón de "Perfil" no se quede marcado en el Main
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

            // 1. Verificar si el resultado fue una ENTREGA EXITOSA
            val wasDelivered = result.data?.getBooleanExtra("EXTRA_DELIVERY_SUCCESS", false) ?: false

            // 2. Recargar la lista (siempre es bueno para actualizar los estados)
            loadOrdersFromDatabase()

            // 3. ¡CELEBRAR! (Si hubo entrega)
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
                // Guardar cambios de selección en DB
                lifecycleScope.launch {
                    ordersList.forEach { database.orderDao().updateOrder(it) }
                }
            },
            // 🔥 ABRIR DETALLE DESDE EL HOME TAMBIÉN
            onItemClick = { order ->
                val intent = Intent(this, OrderDetailActivity::class.java)
                intent.putExtra("ORDER_EXTRA", order)
                // Usamos launcher para saber si vuelve "ENTREGADO"
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

        if (waypoints.isNotEmpty()) {
            uriString.append("&waypoints=$waypoints")
        }

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

        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
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