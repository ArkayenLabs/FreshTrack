package com.example.freshtrack.domain.repository

import com.example.freshtrack.domain.model.ProductInfo

interface ProductLookupRepository {
    suspend fun getProductByBarcode(barcode: String): Result<ProductInfo>
}
