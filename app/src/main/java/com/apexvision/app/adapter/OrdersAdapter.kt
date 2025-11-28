package com.apexvision.app.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.apexvision.app.databinding.ItemOrderBinding
import com.apexvision.app.model.Order

class OrdersAdapter(
    private val ordersList: List<Order>,
    private val onCheckboxClick: () -> Unit,
    private val onItemClick: (Order) -> Unit
) : RecyclerView.Adapter<OrdersAdapter.OrderViewHolder>() {

    inner class OrderViewHolder(val binding: ItemOrderBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val order = ordersList[position]

        with(holder.binding) {
            // 1. Datos Básicos
            tvAddress.text = order.address
            tvCustomerName.text = order.customerName

            // 2. Lógica del Checkbox
            cbSelect.setOnCheckedChangeListener(null)
            cbSelect.isChecked = order.isSelected
            // Desactivar checkbox si ya se entregó (opcional)
            cbSelect.isEnabled = order.status != "ENTREGADO"

            cbSelect.setOnCheckedChangeListener { _, isChecked ->
                order.isSelected = isChecked
                onCheckboxClick()
            }

            // 3. Estado Visual (Color del texto pequeño)
            if (order.status == "ENTREGADO") {
                tvStatusTop.text = "ENTREGADO"
                tvStatusTop.setTextColor(Color.parseColor("#4CAF50")) // Verde
            } else {
                tvStatusTop.text = "PENDIENTE"
                tvStatusTop.setTextColor(Color.parseColor("#D4AF37")) // Dorado
            }

            // 4. Click en TODA la tarjeta para abrir el detalle
            root.setOnClickListener {
                onItemClick(order)
            }
        }
    }

    override fun getItemCount() = ordersList.size
}