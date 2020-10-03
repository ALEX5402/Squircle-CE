/*
 * Licensed to the Light Team Software (Light Team) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The Light Team licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lightteam.language.base.styler

import com.lightteam.language.base.model.SyntaxScheme
import com.lightteam.language.base.styler.span.SyntaxHighlightSpan
import com.lightteam.language.base.styler.utils.StylingResult

interface LanguageStyler {
    fun execute(sourceCode: String, syntaxScheme: SyntaxScheme): List<SyntaxHighlightSpan>
    fun enqueue(sourceCode: String, syntaxScheme: SyntaxScheme, stylingResult: StylingResult)
    fun cancel()
}