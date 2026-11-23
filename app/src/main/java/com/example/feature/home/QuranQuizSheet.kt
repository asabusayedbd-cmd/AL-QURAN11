package com.example.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

private val QUIZ_QUESTIONS = listOf(
    QuizQuestion(
        question = "How many Surahs are in the Holy Quran?",
        options = listOf("112", "114", "120", "30"),
        correctIndex = 1,
        explanation = "The Holy Quran contains exactly 114 Surahs, starting with Al-Fatihah and concluding with An-Nas."
    ),
    QuizQuestion(
        question = "Which Surah is known as the Heart of the Quran (قلب القرآن)?",
        options = listOf("Surah Al-Baqarah", "Surah Al-Mulk", "Surah Ya-Sin", "Surah Ar-Rahman"),
        correctIndex = 2,
        explanation = "Prophet Muhammad (PBUH) stated that everything has a heart, and the heart of the Quran is Surah Ya-Sin."
    ),
    QuizQuestion(
        question = "How many Juz (parts) is the Quran divided into?",
        options = listOf("10", "20", "30", "60"),
        correctIndex = 2,
        explanation = "The Quran is divided into 30 equal parts known as Juz, facilitating monthly Khatam reading."
    ),
    QuizQuestion(
        question = "Which Surah does not begin with Bismillah?",
        options = listOf("Surah At-Tawbah", "Surah Al-Anfal", "Surah Al-Kahf", "Surah Maryam"),
        correctIndex = 0,
        explanation = "Surah At-Tawbah (Surah 9) is the only Surah in the Quran that does not start with Bismillah."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranQuizSheet(
    onDismiss: () -> Unit
) {
    var currentQuestionIndex by remember { mutableIntStateOf(0) }
    var selectedOptionIndex by remember { mutableIntStateOf(-1) }
    var score by remember { mutableIntStateOf(0) }
    var answered by remember { mutableStateOf(false) }

    val q = QUIZ_QUESTIONS[currentQuestionIndex]

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Quranic Knowledge Quiz (${currentQuestionIndex + 1}/${QUIZ_QUESTIONS.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = q.question,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            q.options.forEachIndexed { idx, option ->
                val isSelected = selectedOptionIndex == idx
                val isCorrect = idx == q.correctIndex
                val containerColor = when {
                    answered && isCorrect -> MaterialTheme.colorScheme.primaryContainer
                    answered && isSelected && !isCorrect -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }

                Button(
                    onClick = {
                        if (!answered) {
                            selectedOptionIndex = idx
                            answered = true
                            if (isCorrect) score++
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = option)
                }
            }

            if (answered) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = q.explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (currentQuestionIndex < QUIZ_QUESTIONS.size - 1) {
                            currentQuestionIndex++
                            selectedOptionIndex = -1
                            answered = false
                        } else {
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (currentQuestionIndex < QUIZ_QUESTIONS.size - 1) "Next Question" else "Finish Quiz ($score/${QUIZ_QUESTIONS.size})")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
