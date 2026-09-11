package dev.cypdashuhn.uidesigner.model

data class BlockPos(
    val x: Int,
    val y: Int,
    val z: Int
) : Comparable<BlockPos> {
    override fun compareTo(other: BlockPos): Int {
        val byX = x.compareTo(other.x)
        if (byX != 0) return byX
        val byY = y.compareTo(other.y)
        if (byY != 0) return byY
        return z.compareTo(other.z)
    }
}
