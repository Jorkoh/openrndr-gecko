import org.openrndr.application

fun main() = application {
    configure {
        width = World.WORLD_WIDTH.toInt()
        height = World.WORLD_HEIGHT.toInt()
    }

    program {

        val worldRenderer = WorldRenderer(drawer)

        extend {
            World.update(mouse.position)
            worldRenderer.draw()
        }
    }
}