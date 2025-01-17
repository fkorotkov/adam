/*
 * Copyright (C) 2019 Anton Malinskiy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.malinskiy.adam.request.sync

import com.malinskiy.adam.request.transform.ResponseTransformer
import com.malinskiy.adam.request.transform.StringResponseTransformer

class InstallRemotePackageRequest(
    val absoluteRemoteFilePath: String,
    val reinstall: Boolean,
    val extraArgs: List<String> = emptyList()
) : SyncShellCommandRequest<String>(
    cmd = StringBuilder().apply {
        append("pm install ")

        if (reinstall) {
            append("-r ")
        }

        if (extraArgs.isNotEmpty()) {
            append(extraArgs.joinToString(" "))
        }

        append(absoluteRemoteFilePath)
    }.toString()
), ResponseTransformer<String> by StringResponseTransformer()