package gecko

import clampLength
import clampRadiansAngleDifference
import gecko.BodyPartWithFeet.FeetSide.LEFT
import gecko.BodyPartWithFeet.FeetSide.RIGHT
import org.openrndr.math.Vector2
import radiansAngle
import setLength
import toRadians
import unitWithRadiansAngle

class Gecko(private val spawnPosition: Vector2, var velocity: Vector2) {
    companion object {
        const val NUMBER_OF_BODY_PARTS = 14
        const val BODY_PART_SEPARATION = 30.0

        val BODY_PARTS_WITH_FEET = listOf(2, 6)

        // Maximum head turn per time step
        const val MAX_TURN = 5.0

        // Maximum angle "compression" between one part and the next
        const val MAX_ANGLE_BETWEEN_PARTS = 40.0

        // TODO: make this variable depending on body part
        // Length of each leg
        const val LEG_LENGTH = 60.0

        // TODO: make this variable depending on body part
        // Angle between the body part direction and the feet target of that body part
        const val FEET_TARGET_ANGLE = 40.0

        const val MINIMUM_SPEED = 0.0
        const val MAXIMUM_SPEED = 3.0

        const val DRAG_FACTOR = 0.2

        const val OBJECTIVE_CHASING_FACTOR = 0.01
        const val OBJECTIVE_REACHED_RADIUS = 5.0

        fun spawnGecko(): Gecko {
            return Gecko(Vector2(World.WORLD_WIDTH / 2, World.WORLD_HEIGHT / 2), Vector2.UNIT_X)
        }
    }

    val bodyParts = (1..NUMBER_OF_BODY_PARTS).map { partPosition ->
        val position = spawnPosition - velocity.normalized * (BODY_PART_SEPARATION * partPosition)

        if (partPosition in BODY_PARTS_WITH_FEET) {
            // If it's a part with feet alternate which feet is forward
            val feetDown = if (BODY_PARTS_WITH_FEET.indexOf(partPosition) % 2 == 0) LEFT else RIGHT
            BodyPartWithFeet(position, feetDown, -velocity)
        } else {
            BodyPart(position)
        }
    }.toMutableList()

    val forces: MutableList<Vector2> = mutableListOf()

    fun update(objective: Vector2) {
        calculateVelocity(objective)
        move()
    }

    private fun calculateVelocity(objective: Vector2) {
        // Calculate forces
        forces.clear()
        forces.add(dragForce())
        forces.add(objectiveChasingForce(objective))

        // Calculate updated velocity
        var newVelocity = velocity.copy()
        for (force in forces) {
            newVelocity += force
        }
        velocity = newVelocity
            .clampRadiansAngleDifference(velocity, MAX_TURN.toRadians())
            .clampLength(MINIMUM_SPEED, MAXIMUM_SPEED)
    }

    private fun dragForce(): Vector2 {
        return -velocity * DRAG_FACTOR
    }

    private fun objectiveChasingForce(objective: Vector2): Vector2 {
        return if ((objective - bodyParts[0].position).length < OBJECTIVE_REACHED_RADIUS) {
            Vector2.ZERO
        } else {
            (objective - bodyParts[0].position) * OBJECTIVE_CHASING_FACTOR
        }
    }

    private fun move() {
        bodyParts[0].position += velocity

        // Adjust the body parts depending on the position of the previous part to follow the head movement
        for (i in bodyParts.indices) {
            // Head is not dependant on previous parts so it's not adjusted
            if (i == 0) continue

            val part = bodyParts[i]
            var currentDirection = part.position - bodyParts[i - 1].position
            if (i > 1) {
                // Adjust to avoid crossing over the body by clamping the angle between this part and the previous
                val previousDirection = bodyParts[i - 1].position - bodyParts[i - 2].position

                currentDirection = currentDirection.clampRadiansAngleDifference(
                    previousDirection,
                    MAX_ANGLE_BETWEEN_PARTS.toRadians()
                )
            }
            part.position = bodyParts[i - 1].position + currentDirection.normalized * BODY_PART_SEPARATION

            (part as? BodyPartWithFeet)?.moveFeet(currentDirection, velocity)
        }
    }
}

open class BodyPart(var position: Vector2)

class BodyPartWithFeet(position: Vector2, var feetDown: FeetSide, direction: Vector2) : BodyPart(position) {
    enum class FeetSide {
        LEFT,
        RIGHT
    }

    val feet = mutableListOf(Feet(), Feet())

    init {
        this.calculateTargets(direction)

        // Initial position of the feet
        feet[LEFT.ordinal].position = if (feetDown == LEFT) {
            feet[LEFT.ordinal].target
        } else {
            position + Vector2.unitWithRadiansAngle(
                direction.radiansAngle() - Gecko.FEET_TARGET_ANGLE.toRadians()
            ) * Gecko.LEG_LENGTH
        }

        feet[RIGHT.ordinal].position = if (feetDown == RIGHT) {
            feet[RIGHT.ordinal].target
        } else {
            position + Vector2.unitWithRadiansAngle(
                direction.radiansAngle() + Gecko.FEET_TARGET_ANGLE.toRadians()
            ) * Gecko.LEG_LENGTH
        }
    }

    fun moveFeet(direction: Vector2, velocity: Vector2) {
        calculateTargets(direction)

        // If the feet positions are too far from the targets step forward
        // TODO step has to be animated, not instant
        // TODO this only respects leg length if the part position moves towards the target (forward)
        //  but if it goes backwards or sideways leg stretches past its length

        // If one feet is too far, set the other one down and start moving it towards the target
        feetDown = when {
            feet[LEFT.ordinal].position.distanceTo(position) > Gecko.LEG_LENGTH -> RIGHT
            feet[RIGHT.ordinal].position.distanceTo(position) > Gecko.LEG_LENGTH -> LEFT
            else -> feetDown
        }

        // Move the lifted feet towards the target
        when (feetDown) {
            LEFT -> {
                if (feet[RIGHT.ordinal].position.distanceTo(feet[RIGHT.ordinal].target) < velocity.length * 2) {
                    feet[RIGHT.ordinal].position = feet[RIGHT.ordinal].target
                } else {
                    feet[RIGHT.ordinal].position += (feet[RIGHT.ordinal].target - feet[RIGHT.ordinal].position)
                        .setLength(velocity.length * 2)
                }
            }
            RIGHT -> {
                if (feet[LEFT.ordinal].position.distanceTo(feet[LEFT.ordinal].target) < velocity.length * 2) {
                    feet[LEFT.ordinal].position = feet[LEFT.ordinal].target
                } else {
                    feet[LEFT.ordinal].position += (feet[LEFT.ordinal].target - feet[LEFT.ordinal].position)
                        .setLength(velocity.length * 2)
                }
            }
        }
    }

    private fun calculateTargets(direction: Vector2) {
        feet[0].target = position + Vector2.unitWithRadiansAngle(
            (-direction).radiansAngle() + Gecko.FEET_TARGET_ANGLE.toRadians()
        ) * Gecko.LEG_LENGTH

        feet[1].target = position + Vector2.unitWithRadiansAngle(
            (-direction).radiansAngle() - Gecko.FEET_TARGET_ANGLE.toRadians()
        ) * Gecko.LEG_LENGTH
    }
}

class Feet(
    var target: Vector2 = Vector2.ZERO,
    var position: Vector2 = Vector2.ZERO
)