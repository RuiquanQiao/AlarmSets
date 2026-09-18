package io.github.ruiquanqiao.alarmsets.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive leans on larger, more varied corner radii than the
 * original M3 scale - shape carries as much of the brand as colour does.
 */
val AlarmSetsShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

/** Shapes used by specific components rather than by the global scale. */
object AlarmSetsShapeTokens {
    /** Set cards: generous, pill-adjacent. */
    val setCard = RoundedCornerShape(28.dp)

    /** Rows inside a set: squarer, so the card reads as the container. */
    val alarmRow = RoundedCornerShape(16.dp)

    /** Fully rounded, for chips and toggles. */
    val pill = RoundedCornerShape(percent = 50)

    /** The big time readout on the ring screen. */
    val ringSurface = RoundedCornerShape(40.dp)
}
