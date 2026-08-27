package com.mediasage.feature.briefing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediasage.theme.BrandAmber
import com.mediasage.theme.MediaSageTheme
import com.mediasage.theme.PlayfairDisplayFamily
import com.mediasage.ui.MediaSageScriptureBlock

// Exploration only — mockup for an illuminated drop-cap paragraph treatment (MS ticket TBD).
// Replaces the three identical Text() calls in MediaSageBriefingBody's insight/implication/
// inspiration paragraphs with a large accent-colored first letter, no text labels, no motion.

private val SampleInsight = "When I look upon this day's news — families deported, children slain, " +
    "whole communities left without power and left to pray in the dark — I see that the demands of " +
    "justice are not theoretical abstractions but daily emergencies written in the suffering of " +
    "actual men, women, and children. God does not ask us to admire righteousness from a comfortable " +
    "distance; He has shown us, plainly and without ambiguity, what goodness requires."

private val SampleImplication = "The man or woman of conscience cannot content themselves with " +
    "private virtue while the machinery of oppression grinds on in plain sight; to walk humbly with " +
    "God is to walk alongside those whom the powerful have cast aside. Mercy without justice is " +
    "sentiment, and justice without mercy is cruelty — we are called to hold both, at cost to " +
    "ourselves if need be."

private val SampleInspiration = "I have known what it is to be counted a troublemaker for insisting " +
    "that mercy and justice cannot be separated. Take heart: the same conviction that unsettles the " +
    "comfortable is the one that steadies the afflicted."

@Composable
private fun dropCapText(text: String, capColor: Color, capFontFamily: FontFamily = PlayfairDisplayFamily) =
    buildAnnotatedString {
        if (text.isEmpty()) return@buildAnnotatedString
        withStyle(
            SpanStyle(
                fontFamily = capFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                color = capColor,
            )
        ) {
            append(text.first().toString())
        }
        append(text.substring(1))
    }

@Composable
private fun DropCapParagraph(text: String, capColor: Color, modifier: Modifier = Modifier) {
    Text(
        text = dropCapText(text, capColor),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

@Composable
private fun BriefingBodyDropCapContent() {
    MediaSageTheme {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.primary, thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))
            MediaSageScriptureBlock(
                scriptureReference = "Micah 6:8",
                scriptureText = "He has shown you, O mortal, what is good. And what does the LORD " +
                    "require of you? To act justly and to love mercy and to walk humbly with your God.",
            )
            Spacer(modifier = Modifier.height(16.dp))
            DropCapParagraph(SampleInsight, capColor = BrandAmber)
            Spacer(modifier = Modifier.height(16.dp))
            DropCapParagraph(SampleImplication, capColor = BrandAmber)
            Spacer(modifier = Modifier.height(16.dp))
            DropCapParagraph(SampleInspiration, capColor = BrandAmber)
        }
    }
}

@Composable
private fun BriefingBodyDropCapPrimaryContent() {
    MediaSageTheme {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.primary, thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))
            MediaSageScriptureBlock(
                scriptureReference = "Micah 6:8",
                scriptureText = "He has shown you, O mortal, what is good. And what does the LORD " +
                    "require of you? To act justly and to love mercy and to walk humbly with your God.",
            )
            Spacer(modifier = Modifier.height(16.dp))
            DropCapParagraph(SampleInsight, capColor = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            DropCapParagraph(SampleImplication, capColor = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            DropCapParagraph(SampleInspiration, capColor = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun BriefingBodyCurrentContent() {
    // For side-by-side comparison — the current shipped look (identical Text() calls, tight spacing).
    MediaSageTheme {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.primary, thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))
            MediaSageScriptureBlock(
                scriptureReference = "Micah 6:8",
                scriptureText = "He has shown you, O mortal, what is good. And what does the LORD " +
                    "require of you? To act justly and to love mercy and to walk humbly with your God.",
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = SampleInsight, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = SampleImplication, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = SampleInspiration, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// region Previews

@Preview(name = "Briefing Body — Current (wall of text)", showBackground = true)
@Composable
private fun BriefingBodyCurrentPreview() {
    BriefingBodyCurrentContent()
}

@Preview(name = "Briefing Body — Drop Cap (amber)", showBackground = true)
@Composable
private fun BriefingBodyDropCapPreview() {
    BriefingBodyDropCapContent()
}

@Preview(name = "Briefing Body — Drop Cap (primary)", showBackground = true)
@Composable
private fun BriefingBodyDropCapPrimaryPreview() {
    BriefingBodyDropCapPrimaryContent()
}

// endregion
