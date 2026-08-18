package com.kks.bharatkirana.data.repository

import com.kks.bharatkirana.R
import com.kks.bharatkirana.data.model.CartItem
import com.kks.bharatkirana.data.model.Category
import com.kks.bharatkirana.data.model.Order
import com.kks.bharatkirana.data.model.OrderStatus
import com.kks.bharatkirana.data.model.OrderTimelineItem
import com.kks.bharatkirana.data.model.Product
import com.kks.bharatkirana.data.model.ProductFeature
import com.kks.bharatkirana.data.model.Shop
import com.kks.bharatkirana.data.model.UserProfile
import com.kks.bharatkirana.data.model.WeightOption

class GroceryRepository {

  fun getShops(): List<Shop> = listOf(
    Shop(
      id = "s_bharat_kirana",
      name = "Bharat Kirana Store",
      ownerName = "Bharat Jain",
      address = "Banjara Hills Rd 12, Hyderabad",
      lat = 17.4123,
      lng = 78.4475,
      distance = "0.8 km",
      rating = 4.9f,
      deliveryTime = "15-20 mins",
      localImageRes = R.drawable.img_welcome_hero,
      primaryCategory = "Grocery"
    )
  )

  fun getCategories(): List<Category> = listOf(
    Category("dairy", "Dairy, Bread & Eggs", "egg", 0xFFEFF6FF),
    Category("drinks", "Cold Drinks & Juices", "local_drink", 0xFFFFF1F2),
    Category("tea_coffee", "Tea, Coffee & More", "coffee", 0xFFFFFBEB),
    Category("masala", "Masala, Dry Fruits & More", "grain", 0xFFFEF3C7),
    Category("munchies", "Munchies", "fastfood", 0xFFECFDF5),
    Category("sweets", "Sweet Cravings", "icecream", 0xFFFDF2F8),
    Category("biscuits", "Biscuits", "cookie", 0xFFF5F3FF),
    Category("meat", "Meat, Fish & Eggs", "restaurant", 0xFFFEF2F2),
    Category("packaged", "Packaged Food", "inventory_2", 0xFFF0FDF4),
    Category("breakfast", "Breakfast & Sauces", "breakfast_dining", 0xFFFFF7ED),
    Category("pan", "Paan Corner", "spa", 0xFFF0FDF4),
    Category("baby_care", "Health & Baby Care", "medical_services", 0xFFF5F3FF)
  )

  fun getProducts(): List<Product> = listOf(
    Product(
      id = "p_atta_aashirvaad",
      name = "Premium Whole Wheat Atta",
      brand = "AASHIRVAAD",
      categoryId = "masala",
      shopId = "s_bharat_kirana",
      currentPrice = 280,
      originalPrice = 300,
      discountPercent = 6,
      unit = "5 kg pack",
      subtitle = "5 kg pack • 100% MP Sharbati Wheat",
      localImageRes = R.drawable.img_atta_pack,
      description = "Aashirvaad Superior MP Atta is made using the 4-step advantage process which ensures 100% pure and natural whole wheat atta.\n\nThe wheat is ground using the chakki-grinding process that ensures the flour retains its natural bran, giving you wholesome, soft rotis that stay fresh longer.",
      features = listOf(
        ProductFeature("wheat", "100% Whole Wheat"),
        ProductFeature("clean", "0% Maida Added"),
        ProductFeature("safety", "No Preservatives"),
        ProductFeature("fresh", "Soft Rotis")
      ),
      weightOptions = listOf(
        WeightOption("5 kg", 280, 300, "6% OFF"),
        WeightOption("1 kg", 65, 70, "7% OFF"),
        WeightOption("10 kg", 540, 590, "8% OFF")
      ),
      isPopular = true,
      isDailyEssential = true,
      rating = 4.9f,
      reviewCount = 420
    ),
    Product(
      id = "p_milk_amul_taaza",
      name = "Amul Taaza Milk",
      brand = "AMUL",
      categoryId = "dairy",
      shopId = "s_bharat_kirana",
      currentPrice = 54,
      originalPrice = 56,
      discountPercent = 4,
      unit = "1 L pouch",
      subtitle = "1 L Pouch • Homogenized Toned Milk",
      description = "Amul Taaza Toned Milk is pasteurized and homogenized to preserve all milk nutrients with 3% fat and 8.5% SNF for tea, coffee, and everyday health.",
      features = listOf(
        ProductFeature("safety", "Pasteurized"),
        ProductFeature("clean", "No Added Water"),
        ProductFeature("fresh", "Daily Fresh Morning Batch")
      ),
      weightOptions = listOf(
        WeightOption("1 L", 54, 56),
        WeightOption("500 ml", 28, 29)
      ),
      isPopular = true,
      isDailyEssential = true,
      rating = 4.9f,
      reviewCount = 890
    ),
    Product(
      id = "p_milk_amul_gold",
      name = "Amul Gold Full Cream Milk",
      brand = "AMUL",
      categoryId = "dairy",
      shopId = "s_bharat_kirana",
      currentPrice = 66,
      originalPrice = 68,
      discountPercent = 3,
      unit = "1 L Pouch",
      subtitle = "1 L Pouch • High Cream Rich Milk",
      description = "Amul Gold is full cream pasteurized milk with 6% fat, perfect for creamy curd, paneer, rabdi, and thick chai.",
      features = listOf(
        ProductFeature("fresh", "6% Cream Fat"),
        ProductFeature("clean", "Great for Curd & Sweets"),
        ProductFeature("safety", "Pasteurized & Safe")
      ),
      weightOptions = listOf(
        WeightOption("1 L", 66, 68),
        WeightOption("500 ml", 34, 35)
      ),
      isPopular = true,
      isDailyEssential = true,
      rating = 4.9f,
      reviewCount = 780
    ),
    Product(
      id = "p_salt_tata",
      name = "Tata Salt",
      brand = "TATA",
      categoryId = "masala",
      shopId = "s_bharat_kirana",
      currentPrice = 25,
      originalPrice = 28,
      discountPercent = 10,
      unit = "1 kg",
      subtitle = "1 kg • Vacuum Evaporated Iodized Salt",
      description = "Tata Salt has been the symbol of trust in Indian kitchens for 40 years. Vacuum evaporated with guaranteed iodine levels for mental development.",
      features = listOf(
        ProductFeature("safety", "Desh Ka Namak"),
        ProductFeature("clean", "Vacuum Evaporated"),
        ProductFeature("fresh", "Iodine Enriched")
      ),
      weightOptions = listOf(
        WeightOption("1 kg", 25, 28)
      ),
      isPopular = true,
      isDailyEssential = true,
      rating = 4.9f,
      reviewCount = 1200
    )
  )

  fun getInitialCart(): List<CartItem> = emptyList()

  fun getSampleOrders(): List<Order> = emptyList()
}
