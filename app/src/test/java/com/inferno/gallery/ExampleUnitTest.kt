package com.inferno.gallery

import com.inferno.gallery.data.ai.BPETokenizer
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class ExampleUnitTest {
    @Test
    fun testBPETokenizer() {
        val file = File(if (File("app/src/main/assets/tokenizer.json").exists()) "app/src/main/assets/tokenizer.json" else "src/main/assets/tokenizer.json")
        assertTrue("tokenizer.json should exist", file.exists())
        val json = file.readText()
        val tokenizer = BPETokenizer(json)
        
        val idToWord = tokenizer.vocab.entries.associate { it.value to it.key }
        
        val testIds = listOf(2368, 1929, 320, 1125, 539)
        for (id in testIds) {
            println("ID: $id -> Word: '${idToWord[id]}'")
        }
        
        val testWords = listOf("cat</w>", "dog</w>", "photo</w>", "a</w>")
        for (word in testWords) {
            println("Word: '$word' -> ID: ${tokenizer.vocab[word]}")
        }
        
        val queries = listOf("cat", "dog", "a photo of a dog")
        for (query in queries) {
            val tokens = tokenizer.encode(query)
            println("Query: '$query' -> Tokens: ${tokens.joinToString(", ")}")
        }
    }
}