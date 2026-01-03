package com.jack.qrcodefor1922.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
    private val isUnlocked: Boolean,
    private val onItemClick: (ScanResult) -> Unit,
    private val onViewMoreClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // 是否需要顯示按鈕（超過 15 筆才需要）
    private val showButton: Boolean
        get() = results.size > HISTORY_LIMIT

    override fun getItemViewType(position: Int): Int {
        // 按鈕固定在第 15 個位置（index = 15）
        return if (showButton && position == HISTORY_LIMIT) {
            VIEW_TYPE_VIEW_MORE
        } else {
            VIEW_TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_VIEW_MORE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.fragment_view_more, parent, false)
                ViewMoreHolder(view)
            }
            else -> {
                ItemViewHolder(
                    FragmentItemBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                )
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ItemViewHolder -> {
                // 計算實際資料的 index
                // 按鈕在位置 15，所以位置 16+ 的資料要減 1
                val dataIndex = if (showButton && position > HISTORY_LIMIT) {
                    position - 1
                } else {
                    position
                }
                val item = results[dataIndex]
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
                    onItemClick(item)
                }
            }
            is ViewMoreHolder -> {
                if (isUnlocked) {
                    holder.btnViewMore.text = holder.itemView.context.getString(R.string.history_unlocked)
                    holder.btnViewMore.isEnabled = false
                } else {
                    holder.btnViewMore.text = holder.itemView.context.getString(R.string.view_all_with_ad)
                    holder.btnViewMore.isEnabled = true
                    holder.btnViewMore.setOnClickListener {
                        onViewMoreClick()
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return when {
            results.size <= HISTORY_LIMIT -> results.size  // 不需要按鈕
            !isUnlocked -> HISTORY_LIMIT + 1               // 未解鎖：15 筆 + 按鈕
            else -> results.size + 1                       // 已解鎖：全部 + 按鈕（在中間）
        }
    }

    inner class ItemViewHolder(binding: FragmentItemBinding) : RecyclerView.ViewHolder(binding.root),
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

    inner class ViewMoreHolder(view: View) : RecyclerView.ViewHolder(view) {
        val btnViewMore: Button = view.findViewById(R.id.btn_view_more)
    }

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_VIEW_MORE = 1
        private const val HISTORY_LIMIT = 10
    }
}
