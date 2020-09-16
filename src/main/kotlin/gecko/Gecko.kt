package gecko

import clampLength
import clampAbsoluteRadiansAngleDifference
import clampRadiansAngleDifference
import gecko.BodyPartWithLegs.Side.LEFT
import gecko.BodyPartWithLegs.Side.RIGHT
import org.openrndr.math.Vector2
import radiansAngle
import setLength
import toRadians
import unitWithRadiansAngle
import java.lang.Double.max
import java.lang.Double.min

class Gecko(private val spawnPosition: Vector2, var velocity: Vector2) {
    companion object {
        const val NUMBER_OF_BODY_PARTS = 14
        const val BODY_PART_SEPARATION = 30.0

        val BODY_PARTS_WITH_LEGS_SETTINGS = listOf(
            PartWithLegSettings(
                2,
                LEFT,
                140.0,
                50.0,
                60.0,
                45.0,
                55.0,
                5.0,
                190.0
            ),
            PartWithLegSettings(
                6,
                RIGHT,
                160.0,
                60.0,
                85.0,
                110.0,
                30.0,
                -175.0,
                -20.0
            )
        )

        // Maximum head turn per time step
        const val MAX_TURN = 5.0

        // Maximum angle "compression" between one part and the next
        const val MAX_ANGLE_BETWEEN_PARTS = 40.0

        const val MINIMUM_SPEED = 0.0
        const val MAXIMUM_SPEED = 4.0

        const val DRAG_FACTOR = 0.2

        const val OBJECTIVE_CHASING_FACTOR = 0.01
        const val OBJECTIVE_REACHED_RADIUS = 5.0

        fun spawnGecko(): Gecko {
            return Gecko(Vector2(World.WORLD_WIDTH / 2, World.WORLD_HEIGHT / 2), Vector2.UNIT_X)
        }
    }

    val bodyParts = (1..NUMBER_OF_BODY_PARTS).map { partPosition ->
        val position = spawnPosition - velocity.normalized * (BODY_PART_SEPARATION * partPosition)
        val legsSettings = BODY_PARTS_WITH_LEGS_SETTINGS.firstOrNull { partPosition == it.bodyPartIndex }

        if (legsSettings != null) {
            BodyPartWithLegs(position, legsSettings, -velocity)
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
            .clampAbsoluteRadiansAngleDifference(velocity, MAX_TURN.toRadians())
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

                currentDirection = currentDirection.clampAbsoluteRadiansAngleDifference(
                    previousDirection,
                    MAX_ANGLE_BETWEEN_PARTS.toRadians()
                )
            }
            part.position = bodyParts[i - 1].position + currentDirection.normalized * BODY_PART_SEPARATION

            (part as? BodyPartWithLegs)?.moveLegs(currentDirection, velocity)
        }
    }
}

open class BodyPart(var position: Vector2)

class BodyPartWithLegs(position: Vector2, val settings: PartWithLegSettings, direction: Vector2) : BodyPart(position) {
    enum class Side {
        LEFT,
        RIGHT
    }

    val legs = mutableListOf(Leg(), Leg())

    init {
        this.calculateStepTargets(direction)

        legs.forEachIndexed { index, leg ->
            leg.moving = index == settings.startMovingSide.ordinal

            // Initial position of the feet
            // TODO auto generate proper feet position by mirroring the target with the direction
            leg.footPosition = if (leg.moving) {
                val angleSign = if (index == LEFT.ordinal) -1 else +1
                val angle = (-direction).radiansAngle() + settings.movingLegFootStartAngle.toRadians() * angleSign
                position + Vector2.unitWithRadiansAngle(angle) * settings.movingLegFootStartSeparation
            } else {
                leg.stepTarget
            }

            // Initial position of the feet
            // TODO auto generate proper joint positions
            leg.jointPosition = position + (leg.footPosition - position) / 2.0
        }
    }

    fun moveLegs(direction: Vector2, velocity: Vector2) {
        calculateStepTargets(direction)

        legs.forEachIndexed { index, leg ->
            val target = if (leg.moving) {
                // If it's taking a step the target is to get close to the step target
                if (leg.footPosition.distanceTo(leg.stepTarget) < velocity.length * 2) {
                    leg.stepTarget
                } else {
                    leg.footPosition + (leg.stepTarget - leg.footPosition).setLength(velocity.length * 2.2)
                }
            } else {
                //
                leg.footPosition
            }

            // FABRIK (inverse kinematics) to find joint position
            var newFootPosition: Vector2
            var newJointPosition = leg.jointPosition
            // Use do while because it needs to adjust the joint even when the foot is already
            // reaching the target since the body position also moves

            var iterations = 0
            do {
                // Stage 1 forward reaching
                newJointPosition = target + (newJointPosition - target).setLength(settings.legLength / 2.0)

                // Stage 2 backward reaching
                newJointPosition = position + (newJointPosition - position).setLength(settings.legLength / 2.0)
                val angleSign = if (index == LEFT.ordinal) +1 else -1
                val firstAngle = angleSign * settings.jointConstraintMinAngle.toRadians()
                val secondAngle = angleSign * settings.jointConstraintMaxAngle.toRadians()
                val constrainedDirection = (target - newJointPosition).clampRadiansAngleDifference(
                    newJointPosition - position, min(firstAngle, secondAngle), max(firstAngle, secondAngle)
                )
                newFootPosition = newJointPosition + constrainedDirection.setLength(settings.legLength / 2.0)
                iterations++
            } while (newFootPosition.distanceTo(target) > 0.5 && iterations < 10)

            if (iterations == 10) {
                // Can get close to the target through inverse kinematics, change stepping leg
                switchMovingLeg()
                leg.jointPosition = newJointPosition
                leg.footPosition = newFootPosition
            } else {
                leg.jointPosition = newJointPosition
                leg.footPosition = target
            }
        }
    }

    private fun switchMovingLeg() {
        legs.forEach { leg ->
            leg.moving = !leg.moving
        }
    }

    private fun calculateStepTargets(direction: Vector2) {
        legs[LEFT.ordinal].stepTarget = position + Vector2.unitWithRadiansAngle(
            (-direction).radiansAngle() - settings.targetAngle.toRadians()
        ) * settings.targetSeparation

        legs[RIGHT.ordinal].stepTarget = position + Vector2.unitWithRadiansAngle(
            (-direction).radiansAngle() + settings.targetAngle.toRadians()
        ) * settings.targetSeparation
    }
}

class Leg(
    var moving: Boolean = false,
    var stepTarget: Vector2 = Vector2.ZERO,
    var jointPosition: Vector2 = Vector2.ZERO,
    var footPosition: Vector2 = Vector2.ZERO
)

data class PartWithLegSettings(
    val bodyPartIndex: Int,
    val startMovingSide: BodyPartWithLegs.Side,
    val movingLegFootStartAngle: Double,
    val movingLegFootStartSeparation: Double,
    val legLength: Double,
    val targetAngle: Double,
    val targetSeparation: Double,
    val jointConstraintMinAngle: Double,
    val jointConstraintMaxAngle: Double
)