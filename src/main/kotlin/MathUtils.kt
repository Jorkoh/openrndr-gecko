import org.openrndr.math.Vector2
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

fun Double.toRadians() = Math.toRadians(this)

fun Vector2.radiansAngle() = atan2(y, x)

fun Vector2.setRadiansAngle(newRadiansAngle: Double): Vector2 {
    return Vector2(length * cos(newRadiansAngle), length * sin(newRadiansAngle))
}

fun Vector2.setLength(newLength: Double): Vector2 {
    return this * sqrt(newLength * newLength / squaredLength)
}

fun Vector2.Companion.unitWithRadiansAngle(radiansAngle: Double): Vector2 {
    return Vector2(cos(radiansAngle), sin(radiansAngle))
}

// https://math.stackexchange.com/a/1649850
fun Double.radiansAngleDifference(secondRadiansAngle: Double): Double {
    return (this - secondRadiansAngle + 9.42478) % 6.28319 - 3.14159
}

fun Vector2.clampRadiansAngleDifference(secondVector: Vector2, maxAngleDifference: Double): Vector2 {
    val secondAngle = secondVector.radiansAngle()
    val angleDifference = radiansAngle().radiansAngleDifference(secondAngle)
    return when {
        angleDifference > maxAngleDifference -> {
            setRadiansAngle(secondAngle + maxAngleDifference)
        }
        angleDifference < -maxAngleDifference -> {
            setRadiansAngle(secondAngle - maxAngleDifference)
        }
        else -> this
    }
}

fun Vector2.clampLength(min: Double, max: Double): Vector2 {
    val squaredMax = max * max
    val squaredMin = min * min

    return when {
        squaredLength == 0.0 -> this
        squaredLength > squaredMax -> this * (sqrt(squaredMax / squaredLength))
        squaredLength < squaredMin -> this * (sqrt(squaredMin / squaredLength))
        else -> this
    }
}