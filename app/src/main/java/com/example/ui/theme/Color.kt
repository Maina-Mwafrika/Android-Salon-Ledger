package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Primary Palette (The "Bones")
val CorePink = Color(0xFFF5D6D2)       // Blush Nouveau
val DeepAnchor = Color(0xFF4A3B3C)     // Mink Espresso
val IvoryBase = Color(0xFFFCF9F7)      // Cashmere Cream

// Secondary Palette (The "Accents")
val SupportingMauve = Color(0xFFB8A1A7) // Twilight Orchid
val MetallicAccent = Color(0xFFD4AF87)  // Champagne Gold
val MetallicAccentEnd = Color(0xFFE8C9A3)

// Functional Palette (The "Statuses")
val SuccessGreen = Color(0xFF9DBBA6)    // Eucalyptus (Soft sage green)
val AlertOrange = Color(0xFFD4A08C)     // Terracotta (Warm clay)
val ErrorRed = Color(0xFFD4A08C)        // Terracotta (Warm clay)

// Light Theme Palette
val HighDensityPrimary = DeepAnchor
val HighDensitySecondary = SupportingMauve
val HighDensityTertiary = MetallicAccent
val HighDensityBackground = CorePink
val HighDensitySurface = IvoryBase
val HighDensitySurfaceVariant = Color(0xFFFBEBE8) // Very soft pink tinted white
val HighDensityContainer = IvoryBase
val HighDensityItemBg = IvoryBase
val HighDensityOutline = SupportingMauve
val HighDensityOnBackground = DeepAnchor
val HighDensityOnSurface = DeepAnchor
val HighDensityTextSecondary = SupportingMauve
val HighDensityError = AlertOrange

// Dark Theme Palette - Luxurious Warm Mink & Deep Espresso tones
val HighDensityDarkPrimary = MetallicAccent
val HighDensityDarkSecondary = SupportingMauve
val HighDensityDarkTertiary = CorePink
val HighDensityDarkBackground = Color(0xFF261D1E) // Ultra-rich warm deep espresso-mink
val HighDensityDarkSurface = Color(0xFF332728)    // Warm dark mink surface
val HighDensityDarkSurfaceVariant = Color(0xFF423435) // Elegant dark mauve-espresso container
val HighDensityDarkOutline = SupportingMauve
val HighDensityDarkOnBackground = IvoryBase
val HighDensityDarkOnSurface = IvoryBase
val HighDensityDarkError = AlertOrange

// Status Colors (Legacy / Global access)
val InfoBlue = Color(0xFF0288D1)
