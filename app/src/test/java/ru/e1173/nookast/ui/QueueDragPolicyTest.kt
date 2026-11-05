package ru.e1173.nookast.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueDragPolicyTest {
    private val targets = listOf(
        QueueDragTarget(index = 2, centerY = 100f),
        QueueDragTarget(index = 3, centerY = 200f),
        QueueDragTarget(index = 4, centerY = 300f),
        QueueDragTarget(index = 5, centerY = 400f),
    )

    @Test
    fun pointerCanCrossSeveralRowsInOneMovement() {
        assertEquals(
            4,
            queueDragTargetIndex(
                pointerCenterY = 390f,
                visibleTargets = targets,
                currentIndex = 2,
            ),
        )
    }

    @Test
    fun pointerAtVisibleEdgesSelectsFirstOrLastTarget() {
        assertEquals(2, queueDragTargetIndex(0f, targets, currentIndex = 4))
        assertEquals(5, queueDragTargetIndex(500f, targets, currentIndex = 3))
    }

    @Test
    fun aRowIsPassedOnlyAfterItsCentre() {
        // Just short of the next row's centre: stay; this is what stopped the swap-back jitter.
        assertEquals(3, queueDragTargetIndex(290f, targets, currentIndex = 3))
        assertEquals(4, queueDragTargetIndex(310f, targets, currentIndex = 3))
        assertEquals(3, queueDragTargetIndex(110f, targets, currentIndex = 3))
        assertEquals(2, queueDragTargetIndex(90f, targets, currentIndex = 3))
    }

    @Test
    fun missingLayoutKeepsCurrentIndex() {
        assertEquals(3, queueDragTargetIndex(250f, emptyList(), currentIndex = 3))
    }
}
