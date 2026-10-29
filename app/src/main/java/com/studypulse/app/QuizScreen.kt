package com.studypulse.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Full-screen quiz: loading, questions with feedback, then the score, the revision date and flashcards. */
@Composable
fun QuizScreen(vm: AppVM) {
    BackHandler { vm.closeQuiz() }
    val ctx = LocalContext.current
    val tick = vm.tick
    val item = Plan.item(vm.quizFor)
    val free = Entitlement.tier(ctx) == PlanTier.FREE

    Column(
        Modifier.fillMaxSize().background(C.bg).systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)
    ) {
        Gap(18)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Pill("Close", C.ink) { vm.closeQuiz() }
            Text("Quizzes left today: ${vm.quizLeft()} of ${vm.quizLimit()}", color = C.muted, fontSize = 12.sp)
        }
        Gap(18)
        Label("Quiz", C.brand, size = 12)
        Gap(8)
        Head((item?.text ?: "Topic").take(80), 24, tracking = -0.03)
        Gap(6)
        Text(
            "Written by AI, so it can contain mistakes. Check your notes if an answer looks wrong.",
            color = C.muted, fontSize = 12.5.sp, lineHeight = 18.sp
        )
        Gap(18)

        val set = vm.quizSet
        when {
            vm.quizState == "loading" -> {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 50.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = C.brand, trackColor = C.brandSoft, strokeWidth = 4.dp, modifier = Modifier.size(42.dp))
                    Text("Writing your quiz", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            vm.quizState == "error" -> {
                Panel(Modifier.fillMaxWidth(), bg = C.warm) {
                    Text(vm.quizError, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                    if (vm.quizLimitHit && free) {
                        Gap(12)
                        Text("Premium gives you ${Quiz.PREMIUM_DAILY} quizzes a day instead of ${Quiz.FREE_DAILY}.", color = C.muted, fontSize = 13.sp)
                        Gap(10)
                        Btn("See Premium", Modifier.fillMaxWidth()) { vm.closeQuiz(); vm.paywall = true }
                    }
                }
                Gap(12)
                Btn("Close", Modifier.fillMaxWidth(), BtnKind.Ghost) { vm.closeQuiz() }
            }
            vm.quizState == "run" && set != null -> {
                val m = set.mcqs[vm.quizIdx]
                val picked = vm.quizPick
                Text("Question ${vm.quizIdx + 1} of ${set.mcqs.size}", color = C.muted, fontSize = 13.sp)
                Gap(8)
                Bar(vm.quizIdx.toFloat() / set.mcqs.size)
                Gap(14)
                Text(m.q, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp)
                Gap(14)
                for ((i, o) in m.options.withIndex()) {
                    val label = when {
                        picked >= 0 && i == m.answer -> "\u2713  $o"
                        picked >= 0 && i == picked -> "\u2717  $o"
                        else -> o
                    }
                    Btn(label, Modifier.fillMaxWidth(), if (picked < 0 || i == m.answer) BtnKind.Secondary else BtnKind.Ghost) { vm.quizAnswer(i) }
                    Gap(8)
                }
                if (picked >= 0) {
                    Gap(6)
                    Panel(Modifier.fillMaxWidth(), bg = if (picked == m.answer) C.card else C.warm) {
                        Text(if (picked == m.answer) "Right." else "Not quite.", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        if (m.why.isNotEmpty()) Text(m.why, color = C.muted, fontSize = 13.5.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    Gap(12)
                    Btn(if (vm.quizIdx < set.mcqs.size - 1) "Next question" else "See my result", Modifier.fillMaxWidth(), icon = "check") { vm.quizNext() }
                }
            }
            vm.quizState == "result" && set != null -> {
                val n = set.mcqs.size
                val pct = (vm.quizCorrect * 100 + n / 2) / n
                Panel(Modifier.fillMaxWidth(), bg = C.fill, border = C.fill, pad = 20.dp) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.weight(1f)) {
                            Label("Your score", Color(0xFFCFE2D7), size = 12)
                            Gap(6)
                            Head("$pct%", 40, Color.White, weight = FontWeight.Bold, tracking = -0.02)
                            Text("${vm.quizCorrect} of $n correct", color = Color(0xFFCFE2D7), fontSize = 13.5.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                        Mascot(80.dp, cheer = pct >= Sm2.PASS_PCT, mood = if (pct < 50) "sad" else "")
                    }
                }
                Gap(12)
                Panel(Modifier.fillMaxWidth(), bg = if (pct < Sm2.PASS_PCT) C.warm else C.card) {
                    Text(vm.quizResultLine, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, lineHeight = 20.sp)
                }
                if (set.cards.isNotEmpty()) {
                    Gap(18)
                    Label("Flashcards", C.brand, size = 12)
                    for (c in set.cards) {
                        Gap(8)
                        FlashCardView(c)
                    }
                }
                Gap(18)
                Btn("Done", Modifier.fillMaxWidth(), icon = "check") { vm.closeQuiz() }
            }
        }
        Gap(40)
    }
}

@Composable
private fun FlashCardView(c: FlashCard) {
    var shown by remember(c) { mutableStateOf(false) }
    Panel(Modifier.fillMaxWidth(), pad = 14.dp) {
        Text(c.front, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, lineHeight = 20.sp)
        if (shown) Text(c.back, color = C.muted, fontSize = 13.5.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp))
        Gap(8)
        Pill(if (shown) "Hide answer" else "Show answer", C.brand) { shown = !shown }
    }
}
