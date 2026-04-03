#pragma once
#include <string>

namespace exynix {
int estimateTokenCount(const std::string& text);
std::string formatPrompt(const std::string& system, const std::string& user,
                          const std::string& tmpl = "llama3");
} // namespace exynix
