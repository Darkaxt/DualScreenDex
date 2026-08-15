package com.darkaxt.dualdex.save.gen3

object Gen3Experience {
    fun level(growthRate: Int?, experience: Long): Int? {
        if (growthRate !in 0..5 || experience < 0) return null
        return (1..100).lastOrNull { required(growthRate!!, it) <= experience } ?: 1
    }

    fun progress(growthRate: Int?, experience: Long, level: Int?): Double? {
        if (growthRate !in 0..5 || level !in 1..100 || experience < 0) return null
        if (level == 100) return 1.0
        val current = required(growthRate!!, level!!)
        val next = required(growthRate, level + 1)
        if (experience < current || experience >= next || next <= current) return null
        return (experience - current).toDouble() / (next - current).toDouble()
    }

    internal fun required(growthRate: Int, level: Int): Long {
        require(growthRate in 0..5 && level in 1..100)
        val n = level.toLong()
        val cube = n * n * n
        return when (growthRate) {
            0 -> cube
            1 -> when (level) {
                in 1..50 -> cube * (100 - n) / 50
                in 51..68 -> cube * (150 - n) / 100
                in 69..98 -> cube * (1911 - 10 * n) / 1500
                else -> cube * (160 - n) / 100
            }
            2 -> when (level) {
                in 1..15 -> cube * ((n + 1) / 3 + 24) / 50
                in 16..35 -> cube * (n + 14) / 50
                else -> cube * (n / 2 + 32) / 50
            }
            3 -> ((6 * cube) / 5 - 15 * n * n + 100 * n - 140).coerceAtLeast(0)
            4 -> 4 * cube / 5
            else -> 5 * cube / 4
        }
    }
}
