package gecko

import clampLength
import clampRadiansAngleDifference
import gecko.BodyPartWithLegs.Side.LEFT
import gecko.BodyPartWithLegs.Side.RIGHT
import gecko.Gecko.Companion.LEG_LENGTH
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
            BodyPartWithLegs(position, feetDown, -velocity)
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

            (part as? BodyPartWithLegs)?.moveFeet(currentDirection, velocity)
        }
    }
}

open class BodyPart(var position: Vector2)

class BodyPartWithLegs(position: Vector2, takingAStepSide: Side, direction: Vector2) : BodyPart(position) {
    enum class Side {
        LEFT,
        RIGHT
    }

    val legs = mutableListOf(Leg(), Leg())

    init {
        this.calculateStepTargets(direction)

        legs.forEachIndexed { index, leg ->
            leg.takingAStep = index == takingAStepSide.ordinal

            // Initial position of the feet
            leg.footPosition = if (leg.takingAStep) {
                // If it's taking a step put it behind
                val angleSign = if (index == LEFT.ordinal) -1 else +1
                position + Vector2.unitWithRadiansAngle(
                    direction.radiansAngle() - Gecko.FEET_TARGET_ANGLE.toRadians() * angleSign
                ) * LEG_LENGTH
            } else {
                // If it's not taking a step put it forward
                leg.stepTarget
            }

            leg.jointPosition = position + (leg.footPosition - position) / 2.0
        }
    }

    fun moveFeet(direction: Vector2, velocity: Vector2) {
        calculateStepTargets(direction)

        // TODO on this version foot alternate but each side could be independent https://youtu.be/z_fmMD-Gazw?t=167
        // If one feet is too far, set the other one down and start moving it towards the target
        if (legs[LEFT.ordinal].footPosition.distanceTo(position) > LEG_LENGTH) {
            legs[LEFT.ordinal].takingAStep = true
            legs[RIGHT.ordinal].takingAStep = false
        } else if (legs[RIGHT.ordinal].footPosition.distanceTo(position) > LEG_LENGTH) {
            legs[RIGHT.ordinal].takingAStep = true
            legs[LEFT.ordinal].takingAStep = false
        }

        legs.forEach { leg ->
            val target = if (leg.takingAStep) {
                // If it's taking a step the target is to get close to the step target
                if (leg.footPosition.distanceTo(leg.stepTarget) < velocity.length * 2) {
                    leg.stepTarget
                } else {
                    leg.footPosition + (leg.stepTarget - leg.footPosition).setLength(velocity.length * 2)
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
            do {
                // forwards, reaching target
                newJointPosition = target + (newJointPosition - target).setLength(LEG_LENGTH / 2.0)

                // backwards, reaching body
                newJointPosition = position + (newJointPosition - position).setLength(LEG_LENGTH / 2.0)
                newFootPosition = newJointPosition + (target - newJointPosition).setLength(LEG_LENGTH / 2.0)
            } while (newFootPosition.distanceTo(target) > 1.0)

            leg.footPosition = newFootPosition
            leg.jointPosition = newJointPosition
        }
    }

    private fun calculateStepTargets(direction: Vector2) {
        // TODO targets have to be wider (and not full leg length) on back
        legs[LEFT.ordinal].stepTarget = position + Vector2.unitWithRadiansAngle(
            (-direction).radiansAngle() + Gecko.FEET_TARGET_ANGLE.toRadians()
        ) * LEG_LENGTH

        legs[RIGHT.ordinal].stepTarget = position + Vector2.unitWithRadiansAngle(
            (-direction).radiansAngle() - Gecko.FEET_TARGET_ANGLE.toRadians()
        ) * LEG_LENGTH
    }
}

class Leg(
    var takingAStep: Boolean = false,
    var stepTarget: Vector2 = Vector2.ZERO,
    var jointPosition: Vector2 = Vector2.ZERO,
    var footPosition: Vector2 = Vector2.ZERO
)