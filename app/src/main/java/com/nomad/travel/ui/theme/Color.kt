package com.nomad.travel.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Refined palette — sophisticated dark theme inspired by premium AI tools
// (Anthropic / Linear / Arc). Retains violet identity but trades vivid neon
// for low-saturation indigo-lavender so accents read as polished, not loud.
val NomadInk      = Color(0xFF0D0B18)  // deepest base — violet-tinted charcoal
val NomadNight    = Color(0xFF161226)  // dark surface — soft violet slate
val NomadPurple   = Color(0xFF221A38)  // primary surface — refined dark violet
val NomadViolet   = Color(0xFF362956)  // mid gradient — visible violet depth
val NomadRoyal    = Color(0xFF8A6FE0)  // accent primary — confident lavender-violet
val NomadGlow     = Color(0xFFB39EEB)  // accent highlight — soft luminous lavender
val NomadSilver   = Color(0xFFEDEAF6)  // headline text — cool white with violet hint
val NomadMist     = Color(0xFFB0A6CF)  // body text — muted lavender
val NomadMuted    = Color(0xFF73698E)  // muted — violet-tinted slate

val NomadUserBubble      = NomadRoyal
val NomadAssistantBubble = Color(0xFF241D3A)
val NomadInputField      = Color(0xFF1D1632)

val NomadGradient: Brush
    get() = Brush.verticalGradient(
        colors = listOf(NomadPurple, NomadNight, NomadInk)
    )
