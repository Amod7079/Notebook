package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onNavigateToHome: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    
    // Scale spring animation for the core logo
    val scaleAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1.0f else 0.5f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "logo_scale"
    )

    // Smooth fade transition
    val fadeAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 1000, easing = LinearOutSlowInEasing),
        label = "logo_fade"
    )

    // Shimmer/Rotation value for the accent pen
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val angleAnim by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pen_swivel"
    )

    LaunchedEffect(key1 = true) {
        startAnimation = true
        delay(2200) // Elegant cinematic pause
        onNavigateToHome()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1E293B), // Premium gunmetal slate
                        Color(0xFF0F172A)  // Deep obsidian slate
                    ),
                    radius = 2200f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Aesthetic ambient aura background glow
        Box(
            modifier = Modifier
                .size(400.dp)
                .alpha(0.08f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFB3925F), Color.Transparent)
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(24.dp)
                .scale(scaleAnim)
                .alpha(fadeAnim)
        ) {
            // Elegant Stacked Brush and Journal emblem
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(140.dp)
            ) {
                // outer ring glow
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFB3925F).copy(alpha = 0.05f))
                )
                
                // inner rounded container
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF2C3E50), Color(0xFF1E293B))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Book,
                        contentDescription = "Journal Ledger Icon",
                        tint = Color(0xFFB3925F), // Radiant gold/brass bronze
                        modifier = Modifier.size(48.dp)
                    )
                }

                // Calligraphy Pen Floating accent overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-8).dp, y = (-8).dp)
                        .scale(1.1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFB3925F))
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Draw,
                            contentDescription = null,
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Premium typographic branding
            Text(
                text = "N O T E B O O K",
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.SansSerif,
                color = Color.White,
                letterSpacing = 8.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "P R E M I U M   E D I T I O N",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFB3925F), // Brass
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Premium minimalist line indicator
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                val progressWidth = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    progressWidth.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 1800, easing = FastOutSlowInEasing)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressWidth.value)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFFB3925F), Color(0xFFF39C12))
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Unlocking hardware acceleration...",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.4f),
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}
