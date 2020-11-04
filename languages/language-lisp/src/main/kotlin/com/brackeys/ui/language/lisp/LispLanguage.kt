/*
 * Copyright 2020 Brackeys IDE contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.brackeys.ui.language.lisp

import com.brackeys.ui.language.base.Language
import com.brackeys.ui.language.base.parser.LanguageParser
import com.brackeys.ui.language.base.provider.SuggestionProvider
import com.brackeys.ui.language.base.styler.LanguageStyler
import com.brackeys.ui.language.base.utils.endsWith
import com.brackeys.ui.language.lisp.parser.LispParser
import com.brackeys.ui.language.lisp.provider.LispProvider
import com.brackeys.ui.language.lisp.styler.LispStyler

class LispLanguage : Language {

    companion object {

        private val FILE_EXTENSIONS = arrayOf(".lisp", ".lsp", ".cl", ".l")

        fun supportFormat(fileName: String): Boolean {
            return fileName.endsWith(FILE_EXTENSIONS)
        }
    }

    override fun getName(): String {
        return "lisp"
    }

    override fun getParser(): LanguageParser {
        return LispParser.getInstance()
    }

    override fun getProvider(): SuggestionProvider {
        return LispProvider.getInstance()
    }

    override fun getStyler(): LanguageStyler {
        return LispStyler.getInstance()
    }
}