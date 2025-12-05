package com.apexvision.app.adapter

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.apexvision.app.databinding.ItemRouteCardBinding
import com.apexvision.app.model.RouteItem

class RoutesAdapter(
    private val routes: List<RouteItem>,
    private val onRouteClick: (RouteItem) -> Unit
) : RecyclerView.Adapter<RoutesAdapter.RouteViewHolder>() {

    inner class RouteViewHolder(val binding: ItemRouteCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
        val binding = ItemRouteCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RouteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
        val route = routes[position]
        val context = holder.itemView.context // Obtenemos el contexto para usarlo varias veces

        with(holder.binding) {
            tvRouteName.text = route.name
            tvRouteInfo.text = route.region
            tvOrderCount.text = "${route.totalOrders} Paradas"

            // --- LÓGICA DEL BOTÓN ACTIVAR ---
            btnActivate.setOnClickListener {
                // 1. GUARDAR LA PREFERENCIA (Persistencia)
                // Guardamos qué ID de ruta eligió el usuario (1, 2 o 3)
                val prefs = context.getSharedPreferences("APEX_PREFS", Context.MODE_PRIVATE)
                prefs.edit().putInt("ACTIVE_ROUTE_ID", route.id).apply()

                // 2. FEEDBACK VISUAL
                Toast.makeText(context, "Ruta '${route.name}' activada", Toast.LENGTH_SHORT).show()

                // 3. CALLBACK (Por si la Activity necesita hacer algo extra)
                onRouteClick(route)

                // 4. CERRAR LA PANTALLA (Volver al Mapa)
                // Convertimos el contexto a Activity para poder llamar a finish()
                if (context is Activity) {
                    context.finish()
                }
            }

            // Clic en toda la tarjeta (Hacemos lo mismo que el botón)
            root.setOnClickListener {
                btnActivate.performClick() // Simulamos click en el botón para reutilizar la lógica
            }
        }
    }

    override fun getItemCount() = routes.size
}