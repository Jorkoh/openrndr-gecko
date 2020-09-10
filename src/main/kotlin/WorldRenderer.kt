import gecko.BodyPartWithFeet
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.Drawer
import org.openrndr.extra.compositor.compose
import org.openrndr.extra.compositor.draw

class WorldRenderer(private val drawer: Drawer) {

    private val composition = compose {
        draw {
            drawer.clear(ColorRGBa.WHITE)

            // Body
            drawer.stroke = ColorRGBa.BLACK
            drawer.fill = null
            World.gecko.bodyParts.forEachIndexed { index, bodyPart ->
                if (bodyPart is BodyPartWithFeet) {
                    drawer.fill = ColorRGBa.BLUE
                    drawer.circle(bodyPart.feet[0].target, 10.0)
                    drawer.circle(bodyPart.feet[1].target, 10.0)

                    if (bodyPart.feetDown == BodyPartWithFeet.FeetSide.LEFT) {
                        drawer.fill = ColorRGBa.RED
                    } else {
                        drawer.fill = ColorRGBa.GREEN
                    }
                    drawer.circle(bodyPart.feet[0].position, 10.0)
                    if (bodyPart.feetDown == BodyPartWithFeet.FeetSide.RIGHT) {
                        drawer.fill = ColorRGBa.RED
                    } else {
                        drawer.fill = ColorRGBa.GREEN
                    }
                    drawer.circle(bodyPart.feet[1].position, 10.0)

                    drawer.lineSegment(bodyPart.position, bodyPart.feet[0].position)
                    drawer.lineSegment(bodyPart.position, bodyPart.feet[1].position)

                    drawer.fill = ColorRGBa.YELLOW
                } else {
                    drawer.fill = null
                }

                drawer.circle(
                    bodyPart.position,
                    when (index) {
                        0 -> 17.0
                        1 -> 13.0
                        2 -> 19.0
                        3 -> 23.0
                        4 -> 18.0
                        5 -> 15.0
                        6 -> 9.0
                        7 -> 7.0
                        8 -> 5.0
                        9 -> 4.0
                        10 -> 3.0
                        11 -> 3.0
                        12 -> 3.0
                        13 -> 3.0
                        14 -> 2.0
                        else -> 0.0
                    }
                )

                if (index > 0) {
                    drawer.lineSegment(World.gecko.bodyParts[index - 1].position, bodyPart.position)
                }
            }

            // Velocity
            drawer.stroke = ColorRGBa.GREEN
            drawer.lineSegment(
                World.gecko.bodyParts[0].position,
                World.gecko.bodyParts[0].position + World.gecko.velocity * 10.0
            )

            // Forces
            drawer.stroke = ColorRGBa.RED
            for (force in World.gecko.forces) {
                drawer.lineSegment(World.gecko.bodyParts[0].position, World.gecko.bodyParts[0].position + force * 10.0)
            }
        }
    }

    fun draw() {
        composition.draw(drawer)
    }
}