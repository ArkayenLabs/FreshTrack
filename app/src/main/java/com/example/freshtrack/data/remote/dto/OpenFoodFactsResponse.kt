package com.example.freshtrack.data.remote.dto

import com.google.gson.annotations.SerializedName

data class OpenFoodFactsResponse(
    @SerializedName("code")
    val code: String?,
    @SerializedName("product")
    val product: ProductDto?,
    @SerializedName("status")
    val status: Int?
)

data class ProductDto(
    @SerializedName("product_name")
    val productName: String?,
    @SerializedName("image_url")
    val imageUrl: String?,
    @SerializedName("brands")
    val brands: String?,
    @SerializedName("categories")
    val categories: String?
)
