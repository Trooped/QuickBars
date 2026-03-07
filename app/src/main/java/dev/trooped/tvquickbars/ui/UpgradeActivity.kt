package dev.trooped.tvquickbars.ui

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.appbar.MaterialToolbar
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.GetStoreProductsCallback
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.models.StoreProduct
import com.revenuecat.purchases.models.StoreTransaction
import dev.trooped.tvquickbars.R
import dev.trooped.tvquickbars.utils.PlusStatusManager

/**
 * UpgradeActivity
 * This is the activity that launches either via the settings "upgrade to plus" button, or when a user that
 * does not possess the plus status attempts to do a plus action (create more than 1 QuickBar/TriggerKey, change QuickBar position etc...)
 * The user sees all of the relevant plus perks, and can click the button on the bottom that will launch the Play Store payment action.
 */
class UpgradeActivity : BaseActivity() {
    private var plusProduct: StoreProduct? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upgrade)

        val closeButton: ImageButton = findViewById(R.id.btn_close)
        closeButton.setOnClickListener {
            finish()
        }

        val priceText: TextView = findViewById(R.id.price_text)
        val purchaseButton: Button = findViewById(R.id.btn_purchase)
        val restoreButton: TextView = findViewById(R.id.btn_restore)

        priceText.text = "Loading..."
        purchaseButton.isEnabled = false

        // Fetch the product using the same callback style as your donation code
        val productIds = listOf("plus_unlock")
        Purchases.sharedInstance.getProducts(
            productIds,
            object : GetStoreProductsCallback {
                override fun onReceived(storeProducts: List<StoreProduct>) {
                    plusProduct = storeProducts.firstOrNull()
                    runOnUiThread {
                        plusProduct?.let { prod ->
                            priceText.text = "One-time payment: ${prod.price.formatted}"
                            purchaseButton.isEnabled = true
                        } ?: run {
                            priceText.text = "Product not available"
                            purchaseButton.isEnabled = false
                        }
                    }
                }

                override fun onError(error: PurchasesError) {
                    runOnUiThread {
                        priceText.text = "Could not load product information"
                        purchaseButton.isEnabled = false
                        Toast.makeText(this@UpgradeActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )


        restoreButton.setOnClickListener {
            Toast.makeText(this, "Restoring purchases...", Toast.LENGTH_SHORT).show()

            Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
                // This is called on a successful restore
                override fun onReceived(customerInfo: CustomerInfo) {
                    PlusStatusManager.update(customerInfo)

                    if (customerInfo.entitlements["plus"]?.isActive == true) {
                        Toast.makeText(this@UpgradeActivity,
                            "Purchases restored!", Toast.LENGTH_LONG).show()
                        finish()                                           // close the screen
                    } else {
                        Toast.makeText(this@UpgradeActivity,
                            "No Plus purchase found.", Toast.LENGTH_SHORT).show()
                    }
                }

                // This is called if there is an error
                override fun onError(error: PurchasesError) {
                    Toast.makeText(this@UpgradeActivity,
                        "Restore failed: ${error.message}", Toast.LENGTH_LONG).show()
                }
            })
        }

        purchaseButton.setOnClickListener {
            plusProduct?.let { productToPurchase ->
                purchaseButton.isEnabled = false
                purchaseButton.text = "Processing..."

                // Purchase the product using the same callback style as your donation code
                Purchases.sharedInstance.purchaseProduct(
                    this,
                    productToPurchase,
                    object : PurchaseCallback {

                        override fun onCompleted(
                            storeTransaction: StoreTransaction,
                            customerInfo: CustomerInfo
                        ) {
                            PlusStatusManager.update(customerInfo)

                            if (customerInfo.entitlements["plus"]?.isActive == true) {
                                Toast.makeText(
                                    this@UpgradeActivity,
                                    "Upgrade successful! Thank you!", Toast.LENGTH_LONG
                                ).show()
                                finish()
                            } else {
                                Toast.makeText(
                                    this@UpgradeActivity,
                                    "Purchase completed but entitlement not active.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }

                        override fun onError(error: PurchasesError, userCancelled: Boolean) {
                            runOnUiThread {
                                // Re-enable the button so they can try again
                                purchaseButton.isEnabled = true
                                purchaseButton.text = "Upgrade Now"

                                if (userCancelled) {
                                    // User pressed back in the Google purchase UI – keep it quiet
                                    Toast.makeText(
                                        this@UpgradeActivity,
                                        "Purchase cancelled.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    // Actual error from Play / RevenueCat – show details
                                    Toast.makeText(
                                        this@UpgradeActivity,
                                        "Purchase failed: ${error.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}