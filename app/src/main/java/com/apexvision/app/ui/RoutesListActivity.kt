package com.apexvision.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.apexvision.app.adapter.RoutesAdapter
import com.apexvision.app.databinding.ActivityRoutesListBinding // Layout activity_routes_list.xml
import com.apexvision.app.model.RouteItem

class RoutesListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRoutesListBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoutesListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- RUTAS DISPONIBLES (Simuladas) ---
        val routes = listOf(
            RouteItem(1, "Ruta 1: Norte", "Bello - Niquía", 4, "PENDIENTE"),
            RouteItem(2, "Ruta 2: Sur", "Envigado - Sabaneta", 3, "ASIGNADA"),
            RouteItem(3, "Ruta 3: Centro", "Candelaria - Boston", 5, "PENDIENTE")
        )

        binding.recyclerRoutes.layoutManager = LinearLayoutManager(this)
        binding.recyclerRoutes.adapter = RoutesAdapter(routes) { route ->
            // Al hacer clic, vamos al detalle para confirmar
            val intent = Intent(this, RouteDetailActivity::class.java)
            intent.putExtra("ROUTE_ID", route.id)
            intent.putExtra("ROUTE_NAME", route.name)
            startActivity(intent)
        }

        binding.btnBack.setOnClickListener { finish() }
    }
}