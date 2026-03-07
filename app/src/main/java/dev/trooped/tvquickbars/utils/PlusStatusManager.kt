package dev.trooped.tvquickbars.utils


import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.getCustomerInfoWith
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import androidx.core.content.edit

/**
 * PlusStatusManager
 * This object determines the plus status of an object during runtime.
 */
object PlusStatusManager {
    private val _isPlus = MutableStateFlow(false)
    val isPlus = _isPlus.asStateFlow()

    fun update(info: CustomerInfo) {
        _isPlus.value = info.entitlements["plus"]?.isActive == true
        Log.d("PlusStatus", "Entitlement refresh → isPlus=${_isPlus.value}")
    }
}
