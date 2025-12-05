package com.apexvision.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.apexvision.app.R
import com.apexvision.app.model.Job

class JobsAdapter(
    private val jobsList: List<Job>,
    private val onJobClick: (Job) -> Unit // Función que se ejecutará al dar clic
) : RecyclerView.Adapter<JobsAdapter.JobViewHolder>() {

    class JobViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvCompany: TextView = view.findViewById(R.id.tvCompanyName)
        val tvTitle: TextView = view.findViewById(R.id.tvVacancies) // Usamos este ID del xml item_job
        val tvSalary: TextView = view.findViewById(R.id.tvSalary)
        val btnDetail: Button = view.findViewById(R.id.btnApply) // El botón "VER DETALLES"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JobViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_job, parent, false)
        return JobViewHolder(view)
    }

    override fun onBindViewHolder(holder: JobViewHolder, position: Int) {
        val job = jobsList[position]

        holder.tvCompany.text = job.companyName
        holder.tvTitle.text = job.description
        holder.tvSalary.text = job.salary

        // Cuando tocan el botón, llamamos a la función
        holder.btnDetail.setOnClickListener {
            onJobClick(job)
        }
    }

    override fun getItemCount() = jobsList.size
}