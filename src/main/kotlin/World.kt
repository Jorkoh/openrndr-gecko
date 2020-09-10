import gecko.Gecko
import org.openrndr.math.Vector2

object World {
    const val WORLD_WIDTH = 1600.0
    const val WORLD_HEIGHT = 900.0

    val gecko = Gecko.spawnGecko()

    fun update(objective: Vector2) {
        gecko.update(objective)
    }
}