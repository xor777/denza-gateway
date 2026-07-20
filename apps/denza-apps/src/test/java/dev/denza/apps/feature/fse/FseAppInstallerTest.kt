package dev.denza.apps.feature.fse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FseAppInstallerTest {
    @Test
    fun parsesMatchingSuccessResponse() {
        assertEquals(
            true,
            FseInstallResponse.result(
                "cross theme data = {\"action\":\"android.intent.action.using_wallpaper_result\",\"result\":1,\"res_id\":123}",
                123,
            ),
        )
    }

    @Test
    fun parsesMatchingFailureResponse() {
        assertEquals(
            false,
            FseInstallResponse.result(
                "cross theme data = {\"action\":\"android.intent.action.using_wallpaper_result\",\"result\":0,\"res_id\":123}",
                123,
            ),
        )
    }

    @Test
    fun ignoresAnotherRequest() {
        assertNull(
            FseInstallResponse.result(
                "cross theme data = {\"action\":\"android.intent.action.using_wallpaper_result\",\"result\":1,\"res_id\":124}",
                123,
            ),
        )
    }
}
