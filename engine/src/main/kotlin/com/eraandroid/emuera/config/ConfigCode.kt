package com.eraandroid.emuera.config

enum class DisplayWarningFlag { IGNORE, LATER, ONCE, DISPLAY }
enum class ReduceArgumentOnLoadFlag { YES, ONCE, NO }
enum class TextDrawingMode { GRAPHICS, TEXTRENDERER, WINAPI }
enum class UseLanguage { JAPANESE, KOREAN, CHINESE_HANS, CHINESE_HANT }
enum class TextEditorType { SAKURA, TERAPAD, EMEDITOR, USER_SETTING }

enum class ConfigCode {
    IgnoreCase, UseRenameFile, UseReplaceFile, UseMouse, UseMenu, UseDebugCommand, AllowMultipleInstances,
    AutoSave, SizableWindow, TextDrawingMode, UseImageBuffer, WindowX, WindowY, MaxLog, PrintCPerLine,
    PrintCLength, FontName, FontSize, LineHeight, ForeColor, BackColor, FocusColor, LogColor, FPS, SkipFrame,
    InfiniteLoopAlertTime, DisplayWarningLevel, DisplayReport, ReduceArgumentOnLoad, IgnoreUncalledFunction,
    FunctionNotFoundWarning, FunctionNotCalledWarning, ChangeMasterNameIfDebug, LastKey, ButtonWrap,
    SearchSubdirectory, SortWithFilename, SetWindowPos, WindowPosX, WindowPosY, ScrollHeight, SaveDataNos,
    WarnBackCompatibility, AllowFunctionOverloading, WarnFunctionOverloading, WindowMaximixed, TextEditor,
    EditorType, EditorArgument, WarnNormalFunctionOverloading, CompatiErrorLine, CompatiCALLNAME,
    DebugShowWindow, DebugWindowTopMost, DebugWindowWidth, DebugWindowHeight, DebugSetWindowPos,
    DebugWindowPosX, DebugWindowPosY, UseSaveFolder, CompatiRAND, CompatiDRAWLINE, CompatiFunctionNoignoreCase,
    SystemAllowFullSpace, SystemSaveInUTF8, CompatiLinefeedAs1739, useLanguage, SystemSaveInBinary,
    CompatiFuncArgAutoConvert, CompatiFuncArgOptional, AllowLongInputByMouse, CompatiCallEvent,
    SystemIgnoreTripleSymbol, CompatiSPChara, TimesNotRigorousCalculation, SystemNoTarget, SystemIgnoreStringSet,

    MoneyLabel, MoneyFirst, LoadLabel, MaxShopItem, DrawLineString, BarChar1, BarChar2, TitleMenuString0,
    TitleMenuString1, ComAbleDefault, StainDefault, TimeupLabel, ExpLvDef, PalamLvDef, pbandDef, RelationDef,

    UseKeyMacro,
}
