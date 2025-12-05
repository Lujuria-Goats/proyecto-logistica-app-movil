package com.apexvision.app.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.apexvision.app.model.Job   // <--- Asegúrate de importar esto
import com.apexvision.app.model.Order

// --- DAO DE PEDIDOS ---
@Dao
interface OrderDao {
    @Query("SELECT * FROM orders_table")
    suspend fun getAllOrders(): List<Order>

    @Query("SELECT * FROM orders_table WHERE routeId = :routeId")
    suspend fun getOrdersByRoute(routeId: Int): List<Order>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(orders: List<Order>)

    @Update
    suspend fun updateOrder(order: Order)
}


@Database(entities = [Order::class, Job::class], version = 2, exportSchema = false) // <--- VERSIÓN 2
abstract class AppDatabase : RoomDatabase() {
    abstract fun orderDao(): OrderDao
    abstract fun jobDao(): JobDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "apex_vision_database"
                )
                    .fallbackToDestructiveMigration() // <--- ESTO ES VITAL
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}