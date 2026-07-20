package com.example.freshtrack.data.repository

import com.example.freshtrack.data.remote.OpenFoodFactsApi
import com.example.freshtrack.domain.model.ProductInfo
import com.example.freshtrack.domain.repository.ProductLookupRepository
import java.io.IOException

class ProductLookupRepositoryImpl(
    private val api: OpenFoodFactsApi
) : ProductLookupRepository {

    override suspend fun getProductByBarcode(barcode: String): Result<ProductInfo> {
        return try {
            val response = api.getProductByBarcode(barcode)
            if (response.status == 1 && response.product != null) {
                val productName = response.product.productName
                if (!productName.isNullOrBlank()) {
                    Result.success(
                        ProductInfo(
                            name = productName.trim(),
                            brand = response.product.brands?.trim(),
                            imageUrl = response.product.imageUrl,
                            category = response.product.categories?.trim()
                        )
                    )
                } else {
                    Result.failure(Exception("Product found but name is missing"))
                }
            } else {
                Result.failure(Exception("Product not found for barcode: $barcode"))
            }
        } catch (e: IOException) {
            Result.failure(Exception("Network error while looking up product: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(Exception("Unknown error occurred during lookup: ${e.message}"))
        }
    }
}
