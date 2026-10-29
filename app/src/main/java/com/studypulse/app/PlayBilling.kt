package com.studypulse.app

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Google Play Billing for the Monthly and Yearly plans. Create two subscriptions in Play Console
 * with the product IDs below (see FEATURES.md). It only works for builds installed from Google Play
 * (internal testing track is enough).
 */
object PlayBilling {
    const val MONTHLY_ID = "studypulse_monthly"
    const val YEARLY_ID = "studypulse_yearly"
    private const val GRACE_MS = 3L * 24 * 60 * 60 * 1000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val details = HashMap<String, ProductDetails>()
    private var client: BillingClient? = null
    private var ctx: Context? = null
    private var report: ((String, Boolean) -> Unit)? = null

    private fun idFor(t: PlanTier) = when (t) { PlanTier.MONTHLY -> MONTHLY_ID; PlanTier.YEARLY -> YEARLY_ID; else -> "" }

    /** [onResult] gets a message (may be empty) and whether a paid plan is now active. */
    fun start(c: Context, onResult: (String, Boolean) -> Unit) {
        report = onResult
        if (client != null) return
        ctx = c.applicationContext
        val listener = PurchasesUpdatedListener { r, list ->
            when {
                r.responseCode == BillingClient.BillingResponseCode.OK && list != null -> scope.launch { handle(list, true) }
                r.responseCode == BillingClient.BillingResponseCode.USER_CANCELED -> report?.invoke("Purchase cancelled.", false)
                else -> report?.invoke("Google Play could not complete the purchase (${r.responseCode}).", false)
            }
        }
        client = BillingClient.newBuilder(c.applicationContext)
            .setListener(listener)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        connect()
    }

    private fun connect() {
        val bc = client ?: return
        if (bc.isReady) { scope.launch { refreshNow() }; return }
        bc.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(r: BillingResult) {
                if (r.responseCode == BillingClient.BillingResponseCode.OK) scope.launch { loadDetails(); refreshNow() }
            }
            override fun onBillingServiceDisconnected() { }
        })
    }

    /** Re-reads the active Google Play subscriptions (called after every sign-in). */
    fun refresh() { connect() }

    private suspend fun loadDetails() {
        val bc = client ?: return
        val products = listOf(MONTHLY_ID, YEARLY_ID).map {
            QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.SUBS).build()
        }
        val res = bc.queryProductDetails(QueryProductDetailsParams.newBuilder().setProductList(products).build())
        details.clear()
        for (d in res.productDetailsList.orEmpty()) details[d.productId] = d
    }

    private suspend fun refreshNow() {
        val bc = client ?: return
        val res = bc.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build())
        if (res.billingResult.responseCode == BillingClient.BillingResponseCode.OK) handle(res.purchasesList, false)
    }

    private suspend fun handle(purchases: List<Purchase>, fromPurchase: Boolean) {
        val bc = client ?: return
        val c = ctx ?: return
        var best = PlanTier.FREE
        for (p in purchases) {
            if (p.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            if (!p.isAcknowledged) {
                bc.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build())
            }
            val t = when {
                p.products.contains(YEARLY_ID) -> PlanTier.YEARLY
                p.products.contains(MONTHLY_ID) -> PlanTier.MONTHLY
                else -> PlanTier.FREE
            }
            if (t.ordinal > best.ordinal) best = t
        }
        Entitlement.applyPlay(c, best.name, if (best == PlanTier.FREE) 0L else System.currentTimeMillis() + GRACE_MS)
        val active = best != PlanTier.FREE
        report?.invoke(if (fromPurchase && active) "Premium is active. Thank you!" else if (fromPurchase) "Your purchase is pending." else "", active)
    }

    /** True when Google Play is connected and has this plan's product set up. */
    fun available(t: PlanTier): Boolean = client?.isReady == true && details[idFor(t)]?.subscriptionOfferDetails?.isNotEmpty() == true

    /** Price text from Google Play (for the user's country), or null until Play has answered. */
    fun price(t: PlanTier): String? =
        details[idFor(t)]?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice

    fun launch(act: Activity, t: PlanTier): Boolean {
        val d = details[idFor(t)] ?: return false
        val offer = d.subscriptionOfferDetails?.firstOrNull() ?: return false
        val pdp = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).setOfferToken(offer.offerToken).build()
        val flow = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(pdp)).build()
        return client?.launchBillingFlow(act, flow)?.responseCode == BillingClient.BillingResponseCode.OK
    }
}
