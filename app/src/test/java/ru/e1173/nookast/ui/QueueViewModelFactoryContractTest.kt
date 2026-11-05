package ru.e1173.nookast.ui

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueViewModelFactoryContractTest {
    @Test
    fun androidFactoryCanFindSingleApplicationConstructor() {
        val constructor = QueueViewModel::class.java.getConstructor(Application::class.java)

        assertEquals(listOf(Application::class.java), constructor.parameterTypes.toList())
    }
}
