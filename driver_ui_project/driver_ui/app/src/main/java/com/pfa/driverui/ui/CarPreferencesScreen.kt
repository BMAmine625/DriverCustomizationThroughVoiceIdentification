package com.pfa.driverui.ui

import android.annotation.SuppressLint
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfa.driverui.R
import com.pfa.driverui.model.DriverPreferences

/**
 * Photo-cutout dashboard: a static background photo of the cabin (the
 * moving parts digitally removed) with each moving part as its own
 * transparent PNG cutout, positioned on top and animated with Compose
 * `graphicsLayer` transforms (translation for sliding, rotationZ for an
 * in-plane recline, rotationX/rotationY for a pseudo-3D mirror tilt).
 * Compose can't morph/reshape a photo, so every part is a separate
 * layer that moves as a rigid cutout — the background itself never
 * changes.
 *
 * Two backgrounds are available, switchable with the toggle at the top:
 *  - MAIN  : dashboard-forward view (driver's own point of view).
 *  - SIDE  : as if the driver's door were removed, side-on — easier to
 *            judge seat recline and steering wheel height/depth.
 *
 * Calibration: every part's placement is a fractional box (left/top/width,
 * relative to the photo) plus a multiplier that turns a preference's 0..1
 * position into a translation/rotation amount. Both are eyeballed against
 * the actual background photos and are meant to be nudged in Android
 * Studio's preview once this runs on a real screen — look for the
 * `// CALIBRATION` comments below.
 */

private fun norm(value: Float, min: Float, max: Float): Float =
    ((value - min) / (max - min)).coerceIn(0f, 1f)

// Both background photos (car_bg_main.jpg, car_bg_side.jpg) are 1536x1024 px.
private const val BG_ASPECT = 1536f / 1024f

// Given the box we were measured into (containerW x containerH) and the
// background photo's own aspect ratio, computes the size the photo is
// actually drawn at under ContentScale.Fit (i.e. the letterboxed rect,
// without the black bars) plus it's offset to be centered in the box.
private data class ImageRect(val offsetX: Dp, val offsetY: Dp, val width: Dp, val height: Dp)

private fun fittedImageRect(containerW: Dp, containerH: Dp, aspect: Float): ImageRect {
    val containerAspect = containerW / containerH
    val (w, h) = if (containerAspect > aspect) {
        // box is relatively wider than the photo -> photo's height fills the box,
        // black bars left/right.
        val h = containerH
        val w = h * aspect
        w to h
    } else {
        // box is relatively taller/narrower than the photo -> photo's width fills
        // the box, black bars top/bottom (the usual case here).
        val w = containerW
        val h = w / aspect
        w to h
    }
    return ImageRect(
        offsetX = (containerW - w) / 2,
        offsetY = (containerH - h) / 2,
        width = w,
        height = h,
    )
}

@Composable
private fun animatedNorm(value: Float, min: Float, max: Float): Float {
    val target = norm(value, min, max)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 700),
        label = "prefNorm",
    )
    return animated
}

enum class InteriorView { MAIN, SIDE }

// Below this width, the two interior views are too cramped to sit side by
// side (labels/cutouts start overlapping) and we fall back to a toggle +
// crossfade between them instead. Above it (tablets, landscape phones),
// both views are shown at once.
private val SIDE_BY_SIDE_MIN_WIDTH = 700.dp

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun CarPreferencesScreen(
    driverName: String,
    preferences: DriverPreferences,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var view by remember(driverName) { mutableStateOf(InteriorView.MAIN) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val sideBySide = maxWidth >= SIDE_BY_SIDE_MIN_WIDTH

        if (sideBySide) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    MainInteriorView(preferences)
                    ViewLabel("Vue principale", Alignment.TopStart)
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxSize()
                        .background(Color(0x33FFFFFF)),
                )
                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    SideInteriorView(preferences)
                    ViewLabel("Vue latérale", Alignment.TopStart)
                }
            }
        } else {
            Crossfade(targetState = view, label = "interiorView") { current ->
                when (current) {
                    InteriorView.MAIN -> MainInteriorView(preferences)
                    InteriorView.SIDE -> SideInteriorView(preferences)
                }
            }

            // View toggle, top-center — only needed when we're not showing
            // both views already.
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xB3000000)),
            ) {
                ViewToggleButton("Vue principale", view == InteriorView.MAIN) { view = InteriorView.MAIN }
                ViewToggleButton("Vue latérale", view == InteriorView.SIDE) { view = InteriorView.SIDE }
            }
        }

        // Bottom bar: driver name + action. Shared by both layouts.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xAA000000))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = driverName,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedButton(onClick = onDone) {
                Text("Identify another driver")
            }
        }
    }
}

@Composable
private fun BoxScope.ViewLabel(text: String, alignment: Alignment) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .align(alignment)
            .padding(8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x99000000))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun ViewToggleButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Color(0xFFE0A030) else Color.Transparent)
            .then(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)),
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(0.dp),
        )
    }
}

// ---------------------------------------------------------------------
// Vue principale (tableau de bord, point de vue du conducteur)
// ---------------------------------------------------------------------

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun MainInteriorView(prefs: DriverPreferences) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val rect = fittedImageRect(maxWidth, maxHeight, BG_ASPECT)

        // Black backdrop for the letterbox bars.
        Box(modifier = Modifier.fillMaxSize().background(Color.Black))

        Box(
            modifier = Modifier
                .offset(x = rect.offsetX, y = rect.offsetY)
                .size(rect.width, rect.height),
        ) {
            // w/h below are the actual photo's on-screen size (not the whole
            // container) — every fraction of w/h lands on the photo itself,
            // matching what you'd measure by eye on car_bg_main.jpg directly.
            val w = rect.width
            val h = rect.height

            Image(
                painter = painterResource(id = R.drawable.car_bg_main),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds, // box is already the exact photo size
            )

            // --- Siège (avant/arrière, hauteur, inclinaison) ---
            // CALIBRATION : boîte occupant le rail vide en bas à gauche.
            val seatFB = animatedNorm(prefs.seat.positionAvantArriere, 0f, 100f) // 0=arrière, 1=avant
            val seatH = animatedNorm(prefs.seat.hauteur, 0f, 100f)
            val seatRecline = animatedNorm(prefs.seat.inclinaisonDossier, 90f, 160f) // 0=droit, 1=très incliné
            Image(
                painter = painterResource(id = R.drawable.cutout_seat_main),
                contentDescription = null,
                modifier = Modifier
                    .offset(x = w * -0.043f, y = h * 0.58f)
                    .width(w * 0.48f)
                    .graphicsLayer {
                        val aspect = 670f / 450f
                        translationY = (seatFB - 0.5f) * 100.dp.toPx()   // avant/arrière
                    }
                    .then(Modifier),
            )

            // --- Rétroviseur gauche ---
            MirrorPart(
                resId = R.drawable.cutout_mirror_left_main,
                aspect = 130f / 100f,
                boxLeft = 0.015f, boxTop = 0.175f, boxWidth = 0.09f,
                horizontal = prefs.mirrors.gauche.horizontal,
                vertical = prefs.mirrors.gauche.vertical,
                containerW = w, containerH = h,
            )

            // --- Rétroviseur droit ---
            MirrorPart(
                resId = R.drawable.cutout_mirror_right_main,
                aspect = 130f / 100f,
                boxLeft = 0.86f, boxTop = 0.14f, boxWidth = 0.13f,
                horizontal = prefs.mirrors.droit.horizontal,
                vertical = prefs.mirrors.droit.vertical,
                containerW = w, containerH = h,
            )

            // --- Rétroviseur intérieur ---
            MirrorPart(
                resId = R.drawable.cutout_mirror_interior_main,
                aspect = 220f / 70f,
                boxLeft = 0.37f, boxTop = -0.015f, boxWidth = 0.26f,
                horizontal = prefs.mirrors.interieur.horizontal,
                vertical = prefs.mirrors.interieur.vertical,
                containerW = w, containerH = h,
            )

            // Pas de volant animé dans cette vue (aucun cutout fourni pour cet angle) —
            // le trou du tableau de bord reste visible tel quel sur la photo.
        } // fin de la Box dimensionnée sur la photo
    }
}

// ---------------------------------------------------------------------
// Vue latérale (porte retirée, profil)
// ---------------------------------------------------------------------

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun SideInteriorView(prefs: DriverPreferences) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val rect = fittedImageRect(maxWidth, maxHeight, BG_ASPECT)

        // Black backdrop for the letterbox bars.
        Box(modifier = Modifier.fillMaxSize().background(Color.Black))

        Box(
            modifier = Modifier
                .offset(x = rect.offsetX, y = rect.offsetY)
                .size(rect.width, rect.height),
        ) {
            // w/h below are the actual photo's on-screen size (not the whole
            // container) — every fraction of w/h lands on the photo itself.
            val w = rect.width
            val h = rect.height

            Image(
                painter = painterResource(id = R.drawable.car_bg_side),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds, // box is already the exact photo size
            )

            // --- Siège (avant/arrière, hauteur, inclinaison) ---
            // CALIBRATION : boîte occupant le rail vide au centre-bas.
            // Base et dossier sont deux cutouts séparés (même cadrage/taille
            // d'origine) : les deux suivent la position avant/arrière et la
            // hauteur ensemble, mais seul le dossier tourne pour l'inclinaison,
            // pivoté autour du point où il rejoint la base (le "pied" du
            // dossier dans cutout_seat_side_backrest).
            val seatFB = animatedNorm(prefs.seat.positionAvantArriere, 0f, 100f)
            val seatH = animatedNorm(prefs.seat.hauteur, 0f, 100f)
            val seatRecline = animatedNorm(prefs.seat.inclinaisonDossier, 90f, 160f)

            val seatOffsetX = w * 0.28f
            val seatOffsetY = h * 0.48f
            val seatWidth = w * 0.46f

            // Base (assise + rails) : ne fait que suivre la translation d'ensemble.
            Image(
                painter = painterResource(id = R.drawable.cutout_seat_side_base),
                contentDescription = null,
                modifier = Modifier
                    .offset(x = seatOffsetX, y = seatOffsetY)
                    .width(seatWidth)
                    .graphicsLayer {
                        translationX = (seatFB - 0.5f) * 44.dp.toPx()
                        translationY = -(seatH) * 24.dp.toPx()
                    },
            )

            // Dossier + appui-tête : translation d'ensemble + rotation propre,
            // pivotée près du bas (charnière avec la base).
            Image(
                painter = painterResource(id = R.drawable.cutout_seat_side_backrest),
                contentDescription = null,
                modifier = Modifier
                    .offset(x = seatOffsetX, y = seatOffsetY)
                    .width(seatWidth)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.65f, 0.85f) // CALIBRATION : charnière dossier/base
                        translationX = (seatFB - 0.5f) * 44.dp.toPx()
                        translationY = -(seatH) * 24.dp.toPx()
                        rotationZ = (seatRecline - 0.3f) * 22f
                    },
            )

            // --- Volant + colonne (hauteur, profondeur) ---
            // CALIBRATION : boîte occupant le trou du volant retiré.
            val wheelH = animatedNorm(prefs.steeringWheel.hauteur, 0f, 100f)
            val wheelDepth = animatedNorm(prefs.steeringWheel.profondeur, 0f, 100f)
            Image(
                painter = painterResource(id = R.drawable.cutout_wheel_side),
                contentDescription = null,
                modifier = Modifier
                    .offset(x = w * 0.14f, y = h * 0.24f)
                    .width(w * 0.28f)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                        translationY = -(wheelH - 0.5f) * 26.dp.toPx()
                        translationX = (wheelDepth - 0.5f) * 20.dp.toPx()
                        val scale = 0.94f + wheelDepth * 0.12f // plus "profond" -> légèrement plus grand
                        scaleX = scale
                        scaleY = scale
                    },
            )

            // --- Rétroviseur intérieur ---
            MirrorPart(
                resId = R.drawable.cutout_mirror_interior_side,
                aspect = 137f / 86f,
                boxLeft = 0.34f, boxTop = -0.01f, boxWidth = 0.16f,
                horizontal = prefs.mirrors.interieur.horizontal,
                vertical = prefs.mirrors.interieur.vertical,
                containerW = w, containerH = h,
            )

            // Pas de rétroviseurs extérieurs animés dans cette vue (aucun cutout
            // fourni pour cet angle) — celui visible au loin dans la vitre reste
            // tel quel sur la photo.
        } // fin de la Box dimensionnée sur la photo
    }
}

// ---------------------------------------------------------------------
// Pièce partagée : rétroviseur (bascule pseudo-3D horizontal/vertical)
// ---------------------------------------------------------------------

@Composable
private fun MirrorPart(
    resId: Int,
    aspect: Float,
    boxLeft: Float,
    boxTop: Float,
    boxWidth: Float,
    horizontal: Float,
    vertical: Float,
    containerW: androidx.compose.ui.unit.Dp,
    containerH: androidx.compose.ui.unit.Dp,
) {
    val hNorm = animatedNorm(horizontal, -30f, 30f) // 0=butée gauche, 1=butée droite
    val vNorm = animatedNorm(vertical, -20f, 20f)
    Image(
        painter = painterResource(id = resId),
        contentDescription = null,
        modifier = Modifier
            .offset(x = containerW * boxLeft, y = containerH * boxTop)
            .width(containerW * boxWidth)
            .graphicsLayer {
                cameraDistance = 24f * density // limite la distorsion en perspective
                transformOrigin = TransformOrigin(0.5f, 0.5f)
                rotationY = (hNorm - 0.5f) * 44f // bascule horizontale (pseudo-3D)
                rotationX = (vNorm - 0.5f) * -32f // bascule verticale (pseudo-3D)
            },
    )
}