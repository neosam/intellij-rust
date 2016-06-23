package org.rust.ide.formatter.settings

import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CustomCodeStyleSettings

class RustCodeStyleSettings(container: CodeStyleSettings) :
    CustomCodeStyleSettings(RustCodeStyleSettings::class.java.simpleName, container) {

    @JvmField var ALIGN_RET_TYPE_AND_WHERE_CLAUSE = true
    @JvmField var ALIGN_WHERE_BOUNDS = true
    @JvmField var ALIGN_TYPE_PARAMS = false
    @JvmField var MIN_NUMBER_OF_BLANKS_BETWEEN_ITEMS = 1
}
