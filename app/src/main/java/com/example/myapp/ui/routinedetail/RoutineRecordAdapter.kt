package com.example.myapp.ui.routinedetail

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myapp.R
import android.view.View
import com.example.myapp.databinding.RoutineRecordItemBinding
import com.example.myapp.model.RoutineRecordEntity
import androidx.core.net.toUri


class RoutineRecordAdapter (
    private var records: List<RoutineRecordEntity>,
    private val onItemClick: (RoutineRecordEntity) -> Unit
) : RecyclerView.Adapter<RoutineRecordAdapter.RecordViewHolder>(){

    inner class RecordViewHolder(val binding: RoutineRecordItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(record: RoutineRecordEntity) {
            val sentiment = when (record.sentiment) {
                1 -> " 😊"
                -1 -> " 😢"
                else -> " 😐"
            }
            binding.tvRecordDate.text = record.date.toString() + sentiment
            binding.tvRecordDetail.text = record.detail ?: ""
            if (!record.photoUri.isNullOrEmpty()) {
                binding.imageRecordPhoto.visibility = View.VISIBLE
                binding.imageRecordPhoto.setImageURI(record.photoUri.toUri())
            }
            else {
                binding.imageRecordPhoto.visibility = View.GONE
            }
            binding.root.setOnClickListener { onItemClick(record) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val binding = RoutineRecordItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RecordViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        holder.bind(records[position])
    }

    override fun getItemCount(): Int = records.size

    fun submitList(newRecords: List<RoutineRecordEntity>) {
        records = newRecords
        notifyDataSetChanged()
    }
}