package com.mediasage.feature.headlinedetail

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.mediasage.theme.MediaSageTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class HeadlineDetailScreenRenderTest {

    private val loadedEncouragement = HeadlineDetailContract.EncouragementState.Loaded(
        summary = "This article explores how communities are finding hope amid adversity through ancient wisdom.",
        quoteText = "All shall be well, and all shall be well, and all manner of thing shall be well.",
        figureName = "Julian of Norwich",
        figureRole = "Mystic & Theologian",
        figureImageUrl = null,
        scriptureReference = "Romans 8:28",
        scriptureText = "And we know that in all things God works for the good of those who love him.",
        matchExplanation = "Julian's vision of divine love mirrors the hope expressed in this passage.",
        matchTheme = "Hope in Adversity",
        tone = "contemplative",
    )

    private val figureProfile = HeadlineDetailContract.FigureProfileState(
        name = "Julian of Norwich",
        role = "Mystic & Theologian",
        imageUrl = null,
        bio = "Julian of Norwich (c. 1342–c. 1416) was an English anchorite of the Middle Ages " +
            "and an important Christian mystic. She is the earliest known woman to write in the " +
            "English language.",
    )

    @Test
    fun rendersHeadlineDetailScreenWithEncouragementLoaded() {
        captureRoboImage("build/outputs/roborazzi/headline_detail_loaded.png") {
            MediaSageTheme {
                HeadlineDetailScreen(
                    state = HeadlineDetailContract.UiState.Success(
                        headlineTitle = "Communities Find Ancient Wisdom in Modern Challenges",
                        headlineSource = "The Atlantic",
                        headlineCategory = "Faith & Culture",
                        headlineImageUrl = null,
                        encouragement = loadedEncouragement,
                    ),
                    onIntent = {}
                )
            }
        }
    }

    /**
     * Captures the figure-profile sheet CONTENT in isolation rather than through the live
     * [com.mediasage.ui.MediaSageBottomSheet]. A ModalBottomSheet renders at partial height over a
     * dimmed scrim, so on a static Roborazzi canvas the tall portrait consumes the whole visible
     * region and the name/role/bio fall below the fold — the screenshot then fails to show the
     * content the AC is about. Rendering [FigureProfileSheetContent] directly, wrapped in the sheet's
     * rounded surface + drag handle, produces a faithful, fully-visible capture of the profile.
     */
    @Test
    fun rendersFigureProfileSheet() {
        captureRoboImage("build/outputs/roborazzi/headline_detail_figure_sheet.png") {
            MediaSageTheme {
                SheetPreviewChrome {
                    FigureProfileSheetContent(profile = figureProfile)
                }
            }
        }
    }

    @Composable
    private fun SheetPreviewChrome(content: @Composable () -> Unit) {
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 4.dp)
                        .width(32.dp)
                        .height(4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    content = {},
                )
                content()
            }
        }
    }
}
