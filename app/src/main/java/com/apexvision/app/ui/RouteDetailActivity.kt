package com.apexvision.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.apexvision.app.MainActivity
import com.apexvision.app.adapter.OrdersAdapter
import com.apexvision.app.databinding.ActivityRouteDetailBinding
import com.apexvision.app.db.AppDatabase
import kotlinx.coroutines.launch

class RouteDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRouteDetailBinding
    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRouteDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDatabase.getDatabase(this)

        val routeId = intent.getIntExtra("ROUTE_ID", 1)
        val routeName = intent.getStringExtra("ROUTE_NAME") ?: "Ruta"

        binding.tvRouteName.text = routeName

        // Cargar órdenes de la base de datos
        lifecycleScope.launch {
            val orders = database.orderDao().getOrdersByRoute(routeId)

            val adapter = OrdersAdapter(
                ordersList = orders,
                onCheckboxClick = {
                    // Acción opcional: Guardar selección si lo necesitas en esta vista
                },
                // 🔥 AQUÍ ESTÁ LA MAGIA: ABRIR EL DETALLE
                onItemClick = { order ->
                    val intent = Intent(this@RouteDetailActivity, OrderDetailActivity::class.java)
                    intent.putExtra("ORDER_EXTRA", order)
                    startActivity(intent)
                }
            )

            binding.recyclerRoutePreview.layoutManager = LinearLayoutManager(this@RouteDetailActivity)
            binding.recyclerRoutePreview.adapter = adapter
        }

        // Botón Iniciar Ruta
        binding.btnActivateRoute.setOnClickListener {
            val prefs = getSharedPreferences("APEX_PREFS", MODE_PRIVATE)
            prefs.edit().putInt("ACTIVE_ROUTE_ID", routeId).apply()

            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        binding.btnBack.setOnClickListener { finish() }
    }
}