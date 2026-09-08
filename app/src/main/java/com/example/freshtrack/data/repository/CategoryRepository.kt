package com.example.freshtrack.data.repository

import com.example.freshtrack.data.local.dao.CategoryDao
import com.example.freshtrack.domain.model.Category
import com.example.freshtrack.domain.model.toDomain
import com.example.freshtrack.domain.model.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface CategoryRepository {
    fun getAllCategories(): Flow<List<Category>>
    suspend fun getAllCategoriesOnce(): List<Category>
    suspend fun getCategoryByName(name: String): Category?
    suspend fun insertCategory(category: Category)
    suspend fun updateCategory(category: Category)
    suspend fun deleteCategory(name: String)
}

class CategoryRepositoryImpl(
    private val categoryDao: CategoryDao
) : CategoryRepository {

    override fun getAllCategories(): Flow<List<Category>> =
        categoryDao.getAllCategories().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getAllCategoriesOnce(): List<Category> =
        categoryDao.getAllCategoriesOnce().map { it.toDomain() }

    override suspend fun getCategoryByName(name: String): Category? =
        categoryDao.getCategoryByName(name)?.toDomain()

    override suspend fun insertCategory(category: Category) =
        categoryDao.insertCategory(category.toEntity())

    override suspend fun updateCategory(category: Category) =
        categoryDao.updateCategory(category.toEntity())

    override suspend fun deleteCategory(name: String) = categoryDao.deleteCategoryByName(name)
}
