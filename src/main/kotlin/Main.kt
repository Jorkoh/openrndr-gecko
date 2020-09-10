import org.openrndr.application
import org.openrndr.ffmpeg.ScreenRecorder

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