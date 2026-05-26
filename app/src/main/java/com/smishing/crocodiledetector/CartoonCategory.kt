package com.smishing.crocodiledetector

data class CartoonCategory(
    val name: String,
    val assetPaths: List<String>
) {
    val thumbnailPath: String get() = assetPaths.first()
    val pageCount: Int get() = assetPaths.size
}