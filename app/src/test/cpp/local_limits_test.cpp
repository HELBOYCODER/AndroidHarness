#include "../../main/cpp/local_limits.h"
#include <cassert>
#include <climits>
#include <functional>
#include <iostream>

int main() {
    int tests = 0;
    auto rejects = [&](std::function<void()> block) {
        bool failed = false;
        try { block(); } catch (const std::runtime_error &) { failed = true; }
        assert(failed); ++tests;
    };
    local_limits::validate(2048, 1536, 512, 4, 4); ++tests;
    local_limits::validate(131072, 98304, 32768, 32, 128); ++tests;
    rejects([] { local_limits::validate(INT_MAX, 1, 1, 4, 4); });
    rejects([] { local_limits::validate(2048, INT_MAX, 1, 4, 4); });
    rejects([] { local_limits::validate(2048, 1, 1, 129, 4); });
    rejects([] { local_limits::validate(2048, 1, 1, 4, 0); });
    local_limits::require_prompt_fits(1536, 1536, 1536, 512, 2048); ++tests;
    rejects([] { local_limits::require_prompt_fits(1537, 1537, 1536, 512, 2048); });
    rejects([] { local_limits::require_prompt_fits(1536, 1537, 1536, 512, 2048); });
    rejects([] { local_limits::require_prompt_fits(INT64_MAX, 1, 1536, 512, 2048); });
    rejects([] { local_limits::require_prompt_fits(1, INT64_MAX, 1536, 512, 2048); });
    rejects([] { local_limits::require_prompt_fits(0, 0, 1536, 512, 2048); });
    rejects([] { local_limits::require_prompt_fits(2000, 2000, 2048, 512, 2048); });
    std::cout << tests << " native safety cases passed\n";
}
