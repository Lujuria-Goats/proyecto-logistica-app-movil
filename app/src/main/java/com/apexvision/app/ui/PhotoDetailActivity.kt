package com.apexvision.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.apexvision.app.databinding.ActivityPhotoDetailBinding
import com.bumptech.glide.Glide

class PhotoDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val photoPath = intent.getStringExtra("PHOTO_PATH")

        if (photoPath != null) {
            Glide.with(this)
                .load(photoPath)
                .into(binding.ivPhotoDetail)
        }

        binding.ivPhotoDetail.setOnClickListener {
            supportFinishAfterTransition()
        }
    }
}