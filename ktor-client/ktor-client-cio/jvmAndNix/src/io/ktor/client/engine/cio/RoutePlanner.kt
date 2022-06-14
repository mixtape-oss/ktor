/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.engine.*
import io.ktor.client.request.*

public interface RoutePlanner {
    /**
     *
     */
    public suspend fun planRoute(data: HttpRequestData, proxy: ProxyConfig?): Route
}
