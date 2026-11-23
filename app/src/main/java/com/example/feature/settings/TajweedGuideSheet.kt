package com.example.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LightTajweedPalette

private data class TajweedRuleInfo(val title: String, val arabicName: String, val description: String, val color: Color)

private val TAJWEED_RULES = listOf(
    TajweedRuleInfo("Ghunnah (Nasality)", "الغنة", "Holding sound in the nasal cavity for 2 beats on Noon Mushaddah or Meem Mushaddah.", LightTajweedPalette.ghunnah),
    TajweedRuleInfo("Idgham with Ghunnah", "إدغام بغنة", "Merging Noon Sakinah or Tanween into letters ي ن م و with nasal resonance.", LightTajweedPalette.idgham),
    TajweedRuleInfo("Idgham without Ghunnah", "إدغام بغير غنة", "Merging Noon Sakinah or Tanween into ل or ر without nasality.", LightTajweedPalette.idgham),
    TajweedRuleInfo("Ikhfa (Hiding)", "الإخفاء", "Hiding the Noon Sakinah sound near 15 letters with light nasal breath.", LightTajweedPalette.ikhfa),
    TajweedRuleInfo("Iqlab (Conversion)", "الإقلاب", "Converting Noon Sakinah or Tanween into a hidden Meem before letter ب.", LightTajweedPalette.iqlab),
    TajweedRuleInfo("Qalqalah (Echo)", "القلقلة", "Bouncing/echoing sound on letters ق ط ب ج د when they have Sukoon.", LightTajweedPalette.qalqalah),
    TajweedRuleInfo("Madd (Prolongation)", "المد", "Stretching vowel sounds 2, 4, or 6 beats depending on the type of Madd.", LightTajweedPalette.madd)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TajweedGuideSheet(
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Tajweed Rules & Color Guide",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Al-Quran features live color-coded Tajweed highlighting across Uthmani script to assist precise recitation.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(TAJWEED_RULES) { rule ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = rule.color,
                                modifier = Modifier.size(24.dp)
                            ) {}
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = rule.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = rule.arabicName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = rule.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
