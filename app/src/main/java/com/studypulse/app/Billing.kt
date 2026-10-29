package com.studypulse.app

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class PlanTier(val label: String, val rupees: Int) { FREE("Free", 0), MONTHLY("Monthly", 99), YEARLY("Yearly", 900) }

/** What this account may use. The server is the authority; this is the copy kept on the phone. */
object Entitlement {
    /** New study plans a free account may create each calendar month. */
    const val FREE_PLANS_PER_MONTH = 3

    private fun p(c: Context) = c.applicationContext.getSharedPreferences(Account.prefsName(c) + "_plan", Context.MODE_PRIVATE)

    /** The Google Play subscription is the only source of a paid plan. */
    fun tier(c: Context): PlanTier {
        val t = p(c).getString("ptier", "FREE") ?: "FREE"
        if (t == "FREE" || p(c).getLong("puntil", 0L) <= System.currentTimeMillis()) return PlanTier.FREE
        return try { PlanTier.valueOf(t) } catch (e: Exception) { PlanTier.FREE }
    }

    /** Google Play subscription state, refreshed from Play every time the app opens. */
    fun applyPlay(c: Context, tier: String, until: Long) {
        p(c).edit().putString("ptier", tier).putLong("puntil", until).apply()
    }

    fun untilMillis(c: Context): Long = p(c).getLong("puntil", 0L)
    fun showAds(c: Context): Boolean = tier(c) == PlanTier.FREE
    fun priority(c: Context): Boolean = tier(c) == PlanTier.YEARLY

    private fun monthKey() = "gen_" + LocalDate.now().let { it.year.toString() + "_" + it.monthValue }
    fun canGenerate(c: Context): Boolean = tier(c) != PlanTier.FREE || p(c).getInt(monthKey(), 0) < FREE_PLANS_PER_MONTH
    fun recordGeneration(c: Context) { p(c).edit().putInt(monthKey(), p(c).getInt(monthKey(), 0) + 1).apply() }
}

@Composable
private fun TierCard(t: PlanTier, cur: PlanTier, busy: Boolean, until: Long, onPick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val best = t == PlanTier.YEARLY
    val fg = if (best) Color.White else C.ink
    val sub = if (best) nt(Color(0xFFCFE2D7), Color(0xFFB4BBC2)) else C.muted
    val price = when (t) {
        PlanTier.FREE -> "\u20B90"
        PlanTier.MONTHLY -> (PlayBilling.price(t) ?: "\u20B999") + " / month"
        PlanTier.YEARLY -> (PlayBilling.price(t) ?: "\u20B9900") + " / year"
    }
    val lines = when (t) {
        PlanTier.FREE -> listOf("Shows ads", "${Entitlement.FREE_PLANS_PER_MONTH} new plans a month", "Videos and book suggestions")
        PlanTier.MONTHLY -> listOf("No ads", "Unlimited plans", "Advanced course suggestions")
        PlanTier.YEARLY -> listOf("Everything in Monthly", "Priority processing", "Saves \u20B9288 a year")
    }
    Column(
        Modifier.fillMaxWidth().clip(shape).background(if (best) C.fill else C.card)
            .border(1.dp, if (best) C.fill else C.line, shape).padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t.label, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            if (best) Text("Best value", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text(price, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, modifier = Modifier.padding(top = 4.dp))
        Gap(8)
        for (l in lines) Text("\u2022 $l", color = sub, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 2.dp))
        Gap(14)
        if (t == cur) {
            val extra = if (t != PlanTier.FREE && until > 0L)
                " \u00B7 until " + fmtFullDate(Instant.ofEpochMilli(until).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()) else ""
            Text("Your current plan$extra", color = sub, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
        } else if (t != PlanTier.FREE) {
            Btn(if (busy) "Please wait" else "Choose ${t.label}", Modifier.fillMaxWidth(), if (best) BtnKind.OnBrand else BtnKind.Primary) {
                if (!busy) onPick()
            }
        }
    }
}

@Composable
fun PaywallScreen(vm: AppVM) {
    val ctx = LocalContext.current
    val act = ctx.findActivity()
    val tick = vm.tick
    BackHandler { vm.paywall = false }
    val cur = Entitlement.tier(ctx)
    Column(
        Modifier.fillMaxSize().background(C.bg).systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)
    ) {
        Gap(18)
        Row(Modifier.fillMaxWidth()) { Pill("Close", C.ink) { vm.paywall = false } }
        Gap(18)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Mascot(110.dp, cheer = cur != PlanTier.FREE) }
        Gap(8)
        Label("StudyPulse Premium", C.brand, size = 12)
        Gap(8)
        Head("Study without limits", 30, tracking = -0.04)
        Text(
            "Remove ads, create as many plans as you need and get course suggestions for the hardest topics.",
            color = C.muted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 10.dp)
        )
        Gap(18)
        for (t in PlanTier.values()) {
            TierCard(t, cur, vm.payBusy, Entitlement.untilMillis(ctx)) { act?.let { a -> vm.startCheckout(a, t) } }
            Gap(12)
        }
        if (vm.payMsg.isNotEmpty()) {
            Text(vm.payMsg, color = C.accent, fontSize = 13.5.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        Text(
            "Billed through Google Play. Subscriptions renew until you cancel them in Google Play.",
            color = C.muted, fontSize = 12.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Gap(40)
    }
}
