package com.androidtvopen.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class DiscoveredPcAdapter(
    private val onSelect: (DiscoveredPc) -> Unit
) : RecyclerView.Adapter<DiscoveredPcAdapter.ViewHolder>() {

    private val items = mutableListOf<DiscoveredPc>()

    fun submitList(newItems: List<DiscoveredPc>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_discovered_pc, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvItemPcTitle)
        private val tvMac: TextView = itemView.findViewById(R.id.tvItemPcMac)
        private val tvState: TextView = itemView.findViewById(R.id.tvItemPcState)

        fun bind(item: DiscoveredPc) {
            tvTitle.text = if (item.hostname.isNotBlank()) {
                "${item.hostname} (${item.ip})"
            } else {
                item.ip
            }
            tvMac.text = if (item.mac.isNotBlank()) {
                "MAC: ${item.mac}"
            } else {
                "MAC bulunamadı (elle girilebilir)"
            }
            tvState.text = if (item.isOnline) "Çevrimiçi" else "ARP Kaydı"
            tvState.setTextColor(
                ContextCompat.getColor(
                    itemView.context,
                    if (item.isOnline) R.color.tv_success else R.color.tv_text_secondary
                )
            )
            itemView.setOnClickListener { onSelect(item) }
        }
    }
}

class TvSourceAdapter(
    private val onSelect: (TvSourceItem) -> Unit
) : RecyclerView.Adapter<TvSourceAdapter.ViewHolder>() {

    private val items = mutableListOf<TvSourceItem>()

    fun submitList(newItems: List<TvSourceItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tv_source, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvLabel: TextView = itemView.findViewById(R.id.tvItemSourceLabel)
        private val tvId: TextView = itemView.findViewById(R.id.tvItemSourceId)
        private val tvState: TextView = itemView.findViewById(R.id.tvItemSourceState)

        fun bind(item: TvSourceItem) {
            tvLabel.text = item.label
            tvId.text = item.inputId
            tvState.text = "${item.typeLabel} • ${item.stateLabel}"
            tvState.setTextColor(
                ContextCompat.getColor(
                    itemView.context,
                    if (item.isConnected) R.color.tv_success else R.color.tv_focus_ring
                )
            )
            itemView.setOnClickListener { onSelect(item) }
        }
    }
}

class ProfileChipAdapter(
    private val onSelect: (TargetProfile) -> Unit
) : RecyclerView.Adapter<ProfileChipAdapter.ViewHolder>() {

    private val items = mutableListOf<TargetProfile>()
    private var activeId: String = ""

    fun submitList(newItems: List<TargetProfile>, currentActiveId: String) {
        items.clear()
        items.addAll(newItems)
        activeId = currentActiveId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_profile_chip, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], items[position].id == activeId)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvProfileChipTitle)

        fun bind(profile: TargetProfile, isActive: Boolean) {
            val prefix = if (isActive) "● " else ""
            tvTitle.text = "$prefix${profile.pcName} → ${profile.sourceLabel}"
            tvTitle.setTextColor(
                ContextCompat.getColor(
                    itemView.context,
                    if (isActive) R.color.tv_focus_ring else R.color.tv_text_primary
                )
            )
            itemView.setOnClickListener { onSelect(profile) }
        }
    }
}
