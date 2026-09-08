package io.github.recrivenvi.connectorx.windows

import io.github.recrivenvi.connectorx.ui.theme.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.*

class WindowsAppearanceStorageTest {
    @Test fun sharedThemeRoundTripsThroughWindowsAdapter() {
        val root=Path.of("D:/Workspaces/Scratches/ConnectorX/WindowsTests")
        Files.createDirectories(root)
        val dir=Files.createTempDirectory(root,"appearance-")
        val expected=ThemeConfig(colorMode=2,pureBlack=true,useMonet=true,
            accentColor=ThemeAccentColor.Teal,blurEnabled=false,topBarBlurStyle=TopBarBlurStyle.Progressive,
            floatingBottomBar=true,floatingBottomBarStyle=FloatingBottomBarStyle.IosLike,
            bottomBarMode=BottomBarMode.IconOnly,densityScale=0.9f)
        writeThemeConfig(WindowsAppearanceStorage(dir),expected)
        assertEquals(expected,readThemeConfig(WindowsAppearanceStorage(dir)))
        assertFalse(Files.exists(dir.resolve("identity.dpapi")))
    }
}
