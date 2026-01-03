package com.jack.qrcodefor1922.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.jack.qrcodefor1922.R
import com.jack.qrcodefor1922.databinding.FragmentItemBinding
import com.jack.qrcodefor1922.ui.database.ScanResult
import com.jack.qrcodefor1922.ui.database.TYPE

import java.text.SimpleDateFormat
import java.util.*

class ScanResultRecyclerViewAdapter(
    private val results: List<ScanResult>,
    private val listener: (ScanResult) -> Unit
) : RecyclerView.Adapter<ScanResultRecyclerViewAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

        return ViewHolder(
            FragmentItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = results[position]
        val imgId = when(item.type) {
            TYPE.SMS_1922 -> R.drawable.baseline_sms
            TYPE.TEXT -> R.drawable.baseline_abc
            TYPE.REDIRECT -> R.drawable.baseline_insert_link
        }
        holder.typeView.setImageResource(imgId)
        holder.timeStampView.text =
            SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.TAIWAN).format(item.timestamp)
        holder.contentView.text = item.content
        holder.itemView.setOnClickListener {
            listener(item)
        }
    }

    override fun getItemCount(): Int = results.size

    inner class ViewHolder(binding: FragmentItemBinding) : RecyclerView.ViewHolder(binding.root),
    View.OnLongClickListener {
        val typeView: ImageView = binding.type
        val timeStampView: TextView = binding.timestamp
        val contentView: TextView = binding.content

        init {
            itemView.setOnLongClickListener(this)
        }

        override fun toString(): String {
            return super.toString() + " '" + contentView.text + "'"
        }

        override fun onLongClick(p0: View?): Boolean {
            val content = contentView.text.toString()
            val clipboardManager =
                p0?.context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData = ClipData.newPlainText("simple text", content)
            clipboardManager.setPrimaryClip(clip)
            Toast.makeText(
                p0.context,
                String.format(p0.context.getString(R.string.copy_already), content),
                Toast.LENGTH_SHORT
            ).show()
            return true
        }
    }

}