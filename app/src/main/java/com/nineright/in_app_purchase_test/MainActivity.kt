package com.nineright.in_app_purchase_test

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.AcknowledgePurchaseResponseListener
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsParams.*
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.consumePurchase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainActivity : AppCompatActivity() {
    var count = 0
    private lateinit var billingClient: BillingClient
    private lateinit var productDetailsList: List<ProductDetails>
    private lateinit var logger: Logger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initLog()
        initBilling()
        initView()
    }

    override fun onResume() {
        super.onResume()
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)

        // uses queryPurchasesAsync Kotlin extension function
        val purchasesResult = billingClient.queryPurchasesAsync(params.build(), purchaseResponseListener)

        // check purchasesResult.billingResult
        // process returned purchasesResult.purchasesList, e.g. display the plans user owns

        this.logger.log("onResume - PurchasesResult=${purchasesResult.toString()}")
    }

    private fun initLog() {
        val textView: TextView = findViewById(R.id.log);
        this.logger = Logger(textView)
    }

    fun onClickBuy(view: View) {
        count++
        val text: TextView = findViewById(R.id.count1)
        text.setText("$count")
    }

    private fun initBilling() {
        initBillingClient()
        connectToGooglePlay()
    }

    private fun initView() {
        val text: TextView = findViewById(R.id.log)

        text.setTextIsSelectable(false)
        text.measure(-1, -1)  //you can specific other values.
        text.setTextIsSelectable(true)
    }

    private val purchaseResponseListener = PurchasesResponseListener {billingResult, purchases -> }

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                lifecycleScope.launch {
                    handleConsumablePurchase(purchase)
                }
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            // Handle an error caused by a user cancelling the purchase flow.
            this.logger.err("User canceled the billing")
        } else {
            // Handle any other error codes.
            this.logger.err("Billing is not successful")
        }
    }

    private suspend fun handleConsumablePurchase(purchase: Purchase?) {
        // Verify the purchase.
        // Ensure entitlement was not already granted for this purchaseToken.
        // Grant entitlement to the user.

        if (!validateReceipt()) {
            return
        }

        val consumeParams =
            ConsumeParams.newBuilder()
                .setPurchaseToken(purchase!!.getPurchaseToken())
                .build()
        val consumeResult = withContext(Dispatchers.IO) {
            billingClient.consumePurchase(consumeParams)
        }

        this.logger.log("consumeResult=$consumeResult")
    }

    private suspend fun handleNonConsumablePurchase(purchase: Purchase?) {
        if (purchase!!.purchaseState === Purchase.PurchaseState.PURCHASED) {
            if (!purchase!!.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                val ackPurchaseResult = withContext(Dispatchers.IO) {
                    billingClient.acknowledgePurchase(acknowledgePurchaseParams.build(), AcknowledgePurchaseResponseListener {  })
                }

                this.logger.log("ackPurchaseResult=$ackPurchaseResult")
            }
        }
    }

    private fun initBillingClient() {
        billingClient =
            BillingClient.newBuilder(this@MainActivity).enablePendingPurchases()
                .setListener(purchasesUpdatedListener)
                .build()
    }

    private fun connectToGooglePlay() {
        val logger = this.logger

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    logger.log("Billing Client is ready")
                    // The BillingClient is ready. You can query purchases here.
                    retrieveProducts()
//                    processPurchasesCoroutine()
                }
            }

            override fun onBillingServiceDisconnected() {
                logger.log("Service Disconnected.")
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.
            }
        })
    }

    private fun retrieveProducts() {
        val productList: ArrayList<Product> = ArrayList()
        for (i in 1..4) {
            val number = i.toString().padStart(4, '0')
            productList.add(
                Product.newBuilder()
                    .setProductId("in_app_purchase_test_$number")
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build(),
            )
        }

        val queryProductDetailsParams =
            QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build()

        billingClient.queryProductDetailsAsync(queryProductDetailsParams) { billingResult,
                                                                            productDetailsList ->
            // check billingResult
            // process returned productDetailsList
            if (productDetailsList.isNotEmpty()) {
                for (productDet in productDetailsList) {
                    this.logger.log("product name: ${productDet.name}")
                }
            } else {
                this.logger.log("RetrieveProducts - No matches found")
            }
            this.productDetailsList = productDetailsList
        }
    }

    private fun launchBillingFlow(index: Int) {
        // An activity reference from which the billing flow will be launched.
        val activity : Activity = this

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                // retrieve a value for "productDetails" by calling queryProductDetailsAsync()
                .setProductDetails(productDetailsList[index])
                // For One-time product, "setOfferToken" method shouldn't be called.
                // For subscriptions, to get an offer token, call ProductDetails.subscriptionOfferDetails()
                // for a list of offers that are available to the user
//                .setOfferToken(selectedOfferToken)
                .build(),
//            BillingFlowParams.ProductDetailsParams.newBuilder()
//                .setProductDetails(productDetailsList[1])
//                .build()
        )


        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        // Launch the billing flow
        val billingResult = billingClient.launchBillingFlow(activity, billingFlowParams)

        this.logger.log(billingResult.toString())
        this.logger.log("Launched billing flow.")
    }

    fun launchItem1BillingFlow(view: View) {
        launchBillingFlow(0)
    }

    fun launchItem2BillingFlow(view: View) {
        launchBillingFlow(1)
    }

    fun launchItem3BillingFlow(view: View) {
        launchBillingFlow(2)
    }

    fun launchItem4BillingFlow(view: View) {
        launchBillingFlow(3)
    }

    private fun validateReceipt(): Boolean {
        var ret = false
        ApiCall(this.logger).validateReceipt(this) { isValidationSucceeded ->
            if (isValidationSucceeded.result) {
                this.logger.log("Receipt validation is successful")
                ret = true
            } else {
                this.logger.err("Receipt validation is not successful")
            }

        }

        return ret
    }
}