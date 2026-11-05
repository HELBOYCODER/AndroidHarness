#pragma once
#include <cstdint>
#include <stdexcept>

namespace local_limits {
constexpr int max_context = 131072;
constexpr int max_threads = 128;
inline void validate(int context, int input, int output, int threads, int prompt_threads) {
    if (context < 1 || context > max_context || input < 1 || input > max_context ||
        output < 1 || output > max_context || threads < 1 || threads > max_threads ||
        prompt_threads < 1 || prompt_threads > max_threads)
        throw std::runtime_error("Unsafe local model limits. Context/token limits must be 1..131072 and threads 1..128.");
}
inline void require_prompt_fits(int64_t tokens, int64_t positions, int input, int output, int context) {
    if (tokens < 1 || positions < 1 || tokens > input || tokens > context ||
        positions > context || output > context - tokens || output > context - positions)
        throw std::runtime_error("Conversation exceeds the saved input/context budget. Start a new chat, shorten it, or explicitly raise limits in Local models.");
}
}
