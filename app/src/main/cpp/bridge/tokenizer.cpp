/**
 * ExynNix — Tokenizer Bridge
 * Provides basic tokenization utilities. Full tokenization requires llama.cpp.
 */
#include "tokenizer.h"
#include <android/log.h>
#include <fstream>
#include <sstream>
#include <algorithm>

#define LOG_TAG "ExynNix-Tok"

namespace exynix {

// Simple word-level tokenizer for testing without llama.cpp
int estimateTokenCount(const std::string& text) {
    // ~4 chars per token (rough BPE approximation for English)
    return std::max(1, (int)(text.size() / 4));
}

std::string formatPrompt(const std::string& system_prompt,
                          const std::string& user_input,
                          const std::string& template_name) {
    if (template_name == "llama3") {
        return "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n"
               + system_prompt + "<|eot_id|>"
               "<|start_header_id|>user<|end_header_id|>\n\n"
               + user_input + "<|eot_id|>"
               "<|start_header_id|>assistant<|end_header_id|>\n\n";
    } else if (template_name == "chatml") {
        return "<|im_start|>system\n" + system_prompt + "<|im_end|>\n"
               "<|im_start|>user\n" + user_input + "<|im_end|>\n"
               "<|im_start|>assistant\n";
    } else if (template_name == "gemma") {
        return "<bos><start_of_turn>user\n" + user_input
               + "<end_of_turn>\n<start_of_turn>model\n";
    }
    // Default plain
    return system_prompt.empty()
           ? user_input
           : system_prompt + "\n\nUser: " + user_input + "\nAssistant:";
}

} // namespace exynix
