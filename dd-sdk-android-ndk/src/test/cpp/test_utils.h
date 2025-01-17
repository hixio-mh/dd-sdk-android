/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

#include <string>
#include <list>

#ifdef __cplusplus
extern "C" {
#endif

namespace testutils {

    std::list<std::string> split_backtrace_into_lines(const char *buffer);
}

#ifdef __cplusplus
}
#endif
