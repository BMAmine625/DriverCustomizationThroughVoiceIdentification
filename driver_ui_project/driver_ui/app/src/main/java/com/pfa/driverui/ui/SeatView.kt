package com.pfa.driverui.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.pfa.driverui.model.SeatPreferences
import com.pfa.driverui.ui.theme.CarElectricBlue
import com.pfa.driverui.ui.theme.CarOutline
import com.pfa.driverui.ui.theme.CarSurfaceVariant

/**
 * Side-profile car seat, with a dashboard/steering-wheel silhouette on
 * the left as a fixed visual reference — this makes "upright" and
 * "reclining backward, away from the wheel" unambiguous, unlike a bare
 * pair of rectangles with no orientation cue.
 *
 * Mapping from preferences to visuals:
 *  - positionAvantArriere (%) -> horizontal position along a rail
 *  - hauteur (%)              -> vertical position of the whole seat
 *  - inclinaisonDossier (90-160 deg, raw preference range) -> visual
 *    recline angle, but REMAPPED and CLAMPED to a realistic on-screen
 *    range (0-40 deg from vertical) rather than used directly. Using
 *    the raw range 1:1 made even small preference values look like the
 *    backrest was folding flat, since there's no reference geometry at
 *    that scale to judge a "small" tilt against.
 */
@Composable
fun SeatView(preferences: SeatPreferences, modifier: Modifier = Modifier) {
    val trackWidth = 130.dp
    val trackHalfRangeX = 34.dp
    val heightRange = 22.dp

    // Preference range -> realistic visual recline range (deg from vertical).
    // 90 (upright) -> 0 deg ; 160 (max recline) -> 40 deg (clearly reclined,
    // still clearly a seat someone could sit in).
    val maxVisualTiltDeg = 40f
    val rawFraction = ((preferences.inclinaisonDossier - 90f) / (160f - 90f)).coerceIn(0f, 1f)
    val targetTiltDeg = rawFraction * maxVisualTiltDeg

    val offsetX by animateFloatAsState(
        targetValue = (preferences.positionAvantArriere / 100f - 0.5f) * 2f * trackHalfRangeX.value,
        animationSpec = tween(durationMillis = 500),
        label = "seatOffsetX",
    )
    val offsetY by animateFloatAsState(
        targetValue = (0.5f - preferences.hauteur / 100f) * 2f * heightRange.value,
        animationSpec = tween(durationMillis = 500),
        label = "seatOffsetY",
    )
    // Positive rotationZ pivoted at the backrest's base tilts it to the
    // right — i.e. away from the dashboard drawn on the left, which is
    // the correct "reclining backward" direction.
    val backrestTilt by animateFloatAsState(
        targetValue = targetTiltDeg,
        animationSpec = tween(durationMillis = 500),
        label = "backrestTilt",
    )

    Box(
        modifier = modifier
            .width(trackWidth + trackHalfRangeX * 2)
            .height(120.dp),
    ) {
        // Rail (fixed reference)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(trackWidth + trackHalfRangeX)
                .height(3.dp)
                .background(CarOutline, RoundedCornerShape(2.dp)),
        )

        // Dashboard + steering wheel silhouette (fixed, establishes "front of car")
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 2.dp, y = (-2).dp)
        ) {
            Box(
                modifier = Modifier
                    .width(14.dp)
                    .height(46.dp)
                    .background(CarSurfaceVariant, RoundedCornerShape(topStart = 4.dp, topEnd = 10.dp)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = 6.dp)
                    .width(20.dp)
                    .height(20.dp)
                    .border(2.dp, CarOutline, CircleShape),
            )
        }

        // Seat assembly (position/height translated)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (34 + offsetX).dp, y = offsetY.dp),
        ) {
            // Cushion
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .width(56.dp)
                    .height(14.dp)
                    .background(CarElectricBlue, RoundedCornerShape(5.dp)),
            )

            // Backrest + headrest, rotated together as one rigid group,
            // pivoting at the base (where the backrest meets the cushion).
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = 38.dp, y = (-12).dp)
                    .graphicsLayer {
                        rotationZ = backrestTilt
                        transformOrigin = TransformOrigin(0f, 1f) // pivot at base
                    },
            ) {
                // Backrest
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .width(14.dp)
                        .height(52.dp)
                        .background(CarElectricBlue, RoundedCornerShape(5.dp)),
                )
                // Headrest, sits on top of the backrest, moves rigidly with it
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(y = (-50).dp)
                        .width(18.dp)
                        .height(14.dp)
                        .background(CarElectricBlue, RoundedCornerShape(5.dp)),
                )
            }
        }
    }
}
