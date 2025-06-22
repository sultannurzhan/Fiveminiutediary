package com.example.term_project

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions

class ImageAdapter(
    private val images: MutableList<String>,
    private val onImageClick: (String) -> Unit,
    private val onImageDelete: (Int) -> Unit
) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
        val deleteButton: ImageButton = itemView.findViewById(R.id.deleteImageButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_diary_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imagePath = images[position]
        
        // Load image with Glide
        Glide.with(holder.itemView.context)
            .load(imagePath)
            .apply(RequestOptions()
                .transform(RoundedCorners(16))
                .override(300, 300) // Limit thumbnail size
            )
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_gallery)
            .into(holder.imageView)

        // Set click listeners with null checks and exception handling
        holder.imageView.setOnClickListener {
            try {
                if (imagePath.isNotEmpty()) {
                    onImageClick(imagePath)
                }
            } catch (e: Exception) {
                // Handle click error silently or show toast if needed
            }
        }

        holder.deleteButton.setOnClickListener {
            try {
                onImageDelete(position)
            } catch (e: Exception) {
                // Handle delete error silently
            }
        }
    }

    override fun getItemCount(): Int = images.size

    fun addImage(imagePath: String) {
        images.add(imagePath)
        notifyItemInserted(images.size - 1)
    }

    fun removeImage(position: Int) {
        if (position in 0 until images.size) {
            images.removeAt(position)
            notifyItemRemoved(position)
        }
    }
}
