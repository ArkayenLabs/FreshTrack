package com.example.freshtrack.di

import com.example.freshtrack.data.local.FreshTrackDatabase
import com.example.freshtrack.data.preferences.OnboardingPreferences
import com.example.freshtrack.data.repository.*
import com.example.freshtrack.presentation.viewmodel.*
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for database dependencies
 * Provides DAOs and database instance
 */
val databaseModule = module {

    // Singleton Database Instance
    single { FreshTrackDatabase.getInstance(androidContext()) }

    // DAOs
    single { get<FreshTrackDatabase>().productDao() }
    single { get<FreshTrackDatabase>().categoryDao() }
}

/**
 * Koin module for repository dependencies
 * Provides repository implementations
 */
val repositoryModule = module {

    // Product Repository
    single<ProductRepository> {
        ProductRepositoryImpl(productDao = get())
    }

    // Category Repository
    single<CategoryRepository> {
        CategoryRepositoryImpl(categoryDao = get())
    }
}

/**
 * Koin module for preferences and settings
 * Provides SharedPreferences-based managers
 */
val preferencesModule = module {

    // Onboarding Preferences
    single { OnboardingPreferences(androidContext()) }

    // Theme Manager
    single { ThemeManager(androidContext()) }
}

/**
 * Koin module for ViewModels
 * Provides ViewModels with injected dependencies
 */
val viewModelModule = module {

    // Dashboard ViewModel
    viewModel {
        DashboardViewModel(
            productRepository = get(),
            categoryRepository = get()
        )
    }

    // Product List ViewModel
    viewModel {
        ProductListViewModel(
            productRepository = get(),
            categoryRepository = get()
        )
    }

    // Add/Edit Product ViewModel
    viewModel {
        AddEditProductViewModel(
            productRepository = get(),
            categoryRepository = get()
        )
    }

    // Product Details ViewModel
    viewModel {
        ProductDetailsViewModel(
            productRepository = get()
        )
    }

    // Settings ViewModel
    viewModel {
        SettingsViewModel()
    }
}

/**
 * Combine all modules for app initialization
 */
val appModules = listOf(
    databaseModule,
    repositoryModule,
    preferencesModule,
    viewModelModule
)