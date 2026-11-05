package ru.e1173.nookast.player

import org.junit.Assert.assertEquals
import org.junit.Test

class ControllerAccessPolicyTest {
    @Test
    fun ownAppSystemAndNotificationGetFullAccess() {
        assertEquals(ControllerAccess.FULL, controllerAccess("ru.e1173.nookast", isOwnApp = true, isTrusted = false, isMediaNotificationController = false))
        assertEquals(ControllerAccess.FULL, controllerAccess("com.desaysv.svaudioapp", isOwnApp = false, isTrusted = true, isMediaNotificationController = false))
        assertEquals(ControllerAccess.FULL, controllerAccess("android", isOwnApp = false, isTrusted = false, isMediaNotificationController = true))
        assertEquals(ControllerAccess.FULL, controllerAccess("com.google.android.projection.gearhead", isOwnApp = false, isTrusted = false, isMediaNotificationController = false))
    }

    @Test
    fun anyOtherAppOnlyDrivesPlayback() {
        assertEquals(ControllerAccess.TRANSPORT, controllerAccess("com.example.widget", isOwnApp = false, isTrusted = false, isMediaNotificationController = false))
    }
}
