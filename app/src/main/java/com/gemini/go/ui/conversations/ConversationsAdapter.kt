package com.gemini.go.ui.conversations

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gemini.go.R
import com.gemini.go.data.model.ConversationEntity
import com.google.android.material.button.MaterialButton
import java.text.DateFormat
import java.util.Date

class ConversationsAdapter(
    private val onClick: (ConversationEntity) -> Unit,
    private val onDelete: (ConversationEntity) -> Unit
) : ListAdapter<ConversationEntity, ConversationsAdapter.VH>(DIFF) {
    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ConversationEntity>() {
            override fun areItemsTheSame(a: ConversationEntity, b: ConversationEntity) = a.id == b.id
            override fun areContentsTheSame(a: ConversationEntity, b: ConversationEntity) = a == b
        }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_conversation, parent, false))
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position), onClick, onDelete)
    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.textTitle)
        private val date: TextView = itemView.findViewById(R.id.textDate)
        private val btnDelete: MaterialButton = itemView.findViewById(R.id.btnDelete)
        fun bind(item: ConversationEntity, onClick: (ConversationEntity) -> Unit, onDelete: (ConversationEntity) -> Unit) {
            title.text = item.title
            date.text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.updatedAt))
            itemView.setOnClickListener { onClick(item) }
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }
}
