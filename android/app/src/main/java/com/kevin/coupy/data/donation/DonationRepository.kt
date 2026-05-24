package com.kevin.coupy.data.donation

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Donate 後端，包 Google Play Billing 7.x。
 *
 * 流程：
 *   1. 啟動連線 → queryProductDetails 抓 3 個商品價格
 *   2. UI 按下檔位 → launchBillingFlow
 *   3. PurchasesUpdatedListener 收到 → consumeAsync 立刻消費（讓使用者可重複買）
 *   4. 透過 donationEvents emit 結果給 UI 顯示 Snackbar
 *
 * 不記錄任何贊助歷史、不綁帳號——純粹「回禮」精神。
 *
 * 生命週期說明：
 * Singleton scope，BillingClient 在 App process 整個生命週期都活著。
 * Coupy 是「開一下就關」的 App（不是長時間在前景的串流類 App），
 * 沒必要做 endConnection 的 ceremony——process 結束時系統會自動清理 binding。
 * Service 被 Play Store 重啟時透過 onBillingServiceDisconnected → 自動重連。
 */
@Singleton
class DonationRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    private val _productDetails = MutableStateFlow<Map<DonationProduct, ProductDetails>>(emptyMap())
    val productDetails: StateFlow<Map<DonationProduct, ProductDetails>> = _productDetails.asStateFlow()

    private val _donationEvents = MutableSharedFlow<DonationEvent>(extraBufferCapacity = 4)
    val donationEvents: SharedFlow<DonationEvent> = _donationEvents.asSharedFlow()

    init {
        connect()
    }

    private fun connect() {
        if (billingClient.isReady) return
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch { loadProducts() }
                }
            }

            override fun onBillingServiceDisconnected() {
                // Play Store 進程被 kill / 重啟。延遲後自動重連，避免使用者下次點 Donate 才發現壞了
                scope.launch {
                    delay(RECONNECT_DELAY_MS)
                    if (!billingClient.isReady) connect()
                }
            }
        })
    }

    private suspend fun loadProducts() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                DonationProduct.entries.map { product ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(product.productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                }
            )
            .build()

        val result = billingClient.queryProductDetails(params)
        val details = result.productDetailsList.orEmpty()
        _productDetails.value = details.mapNotNull { d ->
            DonationProduct.fromProductId(d.productId)?.let { it to d }
        }.toMap()
    }

    /**
     * 啟動購買流程。需要 Activity context（Billing flow 是 UI 等級的）。
     */
    fun launchDonation(activity: Activity, product: DonationProduct) {
        val details = _productDetails.value[product] ?: run {
            scope.launch { _donationEvents.emit(DonationEvent.NotReady) }
            // 沒拿到價格通常是因為 Play Console IAP 還沒設定，或設備沒登入 Google 帳號
            if (!billingClient.isReady) connect()
            return
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()

        billingClient.launchBillingFlow(activity, flowParams)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        scope.launch { consume(purchase) }
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                // 使用者自己取消，不噪音
            }
            else -> {
                scope.launch { _donationEvents.emit(DonationEvent.Failed) }
            }
        }
    }

    private suspend fun consume(purchase: Purchase) {
        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        val result = billingClient.consumePurchase(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            _donationEvents.emit(DonationEvent.Thanks)
        } else {
            _donationEvents.emit(DonationEvent.Failed)
        }
    }

    companion object {
        private const val RECONNECT_DELAY_MS = 1_000L
    }
}

sealed interface DonationEvent {
    /** 購買成功並消費完成——UI 顯示「謝謝 ☕」*/
    data object Thanks : DonationEvent

    /** Billing 還沒連上或商品還沒 load——UI 提示「暫時無法處理，稍後再試」*/
    data object NotReady : DonationEvent

    /** 購買或消費失敗 */
    data object Failed : DonationEvent
}
