package com.eraandroid.core.lexer

class Lexer(private val source: String, private val fileName: String = "") {

    private var pos = 0
    private var line = 1
    private var column = 1
    private val tokens = mutableListOf<Token>()

    // 원본 소스 줄 배열 - Parser가 peekRestOfLine에서 직접 사용
    val sourceLines: List<String> by lazy {
        source.lines().map { it.trimEnd('\r') }
    }

    companion object {
        val KEYWORDS = mapOf(
            "IF" to TokenType.IF,
            "ELSEIF" to TokenType.ELSEIF,
            "ELSE" to TokenType.ELSE,
            "ENDIF" to TokenType.ENDIF,
            "FOR" to TokenType.FOR,
            "TO" to TokenType.TO,
            "NEXT" to TokenType.NEXT,
            "WHILE" to TokenType.WHILE,
            "WEND" to TokenType.WEND,
            "DO" to TokenType.DO,
            "LOOP" to TokenType.LOOP,
            "UNTIL" to TokenType.UNTIL,
            "PRINTFORMW" to TokenType.PRINTW,
            "PRINTFORMLW" to TokenType.PRINTW,
            "PRINTLW" to TokenType.PRINTW,
            "REPEAT" to TokenType.REPEAT,
            "REND" to TokenType.REND,
            "CALL" to TokenType.CALL,
            "FUNC" to TokenType.FUNC,
            "ENDFUNC" to TokenType.ENDFUNC,
            "RETURN" to TokenType.RETURN,
            "GOTO" to TokenType.GOTO,
            "GOSUB" to TokenType.GOSUB,
            "PRINT" to TokenType.PRINT,
            "PRINTL" to TokenType.PRINTL,
            "PRINTW" to TokenType.PRINTW,
            "PRINTD" to TokenType.PRINTD,
            "PRINTFORM" to TokenType.PRINTFORM,
            "PRINTFORML" to TokenType.PRINTFORML,
            "PRINTV" to TokenType.PRINTV,
            "PRINTVL" to TokenType.PRINTVL,
            "PRINTS" to TokenType.PRINTS,
            "PRINTSL" to TokenType.PRINTSL,
            "INPUT" to TokenType.INPUT,
            "INPUTMOUSEKEY" to TokenType.INPUTMOUSEKEY,
            "TINPUT" to TokenType.TINPUT,
            "TWAIT" to TokenType.TWAIT,
            "WAIT" to TokenType.WAIT,
            "BEGIN" to TokenType.BEGIN,
            "CALLTRAIN" to TokenType.CALLTRAIN,
            "SETCOLOR" to TokenType.SET_COLOR,
            "RESETCOLOR" to TokenType.RESET_COLOR,
            "SETBGCOLOR" to TokenType.SET_BG_COLOR,
            "RESETBGCOLOR" to TokenType.RESET_BG_COLOR,
            "BUTTON" to TokenType.BUTTON,
            "SELECTCASE" to TokenType.SELECTCASE,
            "CASE" to TokenType.CASE,
            "CASEELSE" to TokenType.CASEELSE,
            "ENDSELECT" to TokenType.ENDSELECT,
            "VARSET" to TokenType.VARSET,
            "CVARSET" to TokenType.CVARSET,
            "ARRAYSHIFT" to TokenType.ARRAYSHIFT,
            "ARRAYREMOVE" to TokenType.ARRAYREMOVE,
            "RANDOMIZE" to TokenType.RANDOMIZE,
            "LIMIT" to TokenType.LIMIT,
            "SQRT" to TokenType.SQRT,
            "ABS" to TokenType.ABS,
            "SIGN" to TokenType.SIGN,
            "MAX" to TokenType.MAX,
            "MIN" to TokenType.MIN,
            "INRANGE" to TokenType.INRANGE,
            "STRJOIN" to TokenType.STRJOIN,
            "SPLIT" to TokenType.SPLIT,
            "STRLENS" to TokenType.STRLENS,
            "SUBSTRING" to TokenType.SUBSTRING,
            "CHARATU" to TokenType.CHARATU,
            "STRFIND" to TokenType.STRFIND,
            "TOINT" to TokenType.TOINT,
            "TOSTR" to TokenType.TOSTR,
            "ISNUMERIC" to TokenType.ISNUMERIC,
            "GETTIME" to TokenType.GETTIME,
            "SAVEDATA" to TokenType.SAVEDATA,
            "LOADDATA" to TokenType.LOADDATA,
            "DELDATA" to TokenType.DELDATA,
            "CHKDATA" to TokenType.CHKDATA,
            "SAVEGLOBAL" to TokenType.SAVEGLOBAL,
            "LOADGLOBAL" to TokenType.LOADGLOBAL,
            "THROW" to TokenType.THROW,
            "DEBUGPRINT" to TokenType.DEBUGPRINT,
            "ASSERT" to TokenType.ASSERT,
            "SIF" to TokenType.SIF,
            "NOP" to TokenType.NOP,
            "QUIT" to TokenType.QUIT,
            "ADDCHARA" to TokenType.ADDCHARA,
            "DELCHARA" to TokenType.DELCHARA,
            "SWAPCHARA" to TokenType.SWAPCHARA,
            "SORTCHARA" to TokenType.SORTCHARA,
            "PICKUPCHARA" to TokenType.PICKUPCHARA,
            "ADDCOPYCHARA" to TokenType.ADDCOPYCHARA,
            "NOBLANK" to TokenType.NOBLANK,
            "NODISP" to TokenType.NODISP,
            "ALIGNMENT" to TokenType.ALIGNMENT,
            "FONTSTYLE" to TokenType.FONTSTYLE,
            "SETFONT" to TokenType.SETFONT,
            "DRAWLINE" to TokenType.DRAWLINE,
            "CLEARLINE" to TokenType.CLEARLINE,
            "REPLACE" to TokenType.REPLACE,
            "REUSELASTLINE" to TokenType.REUSELASTLINE,
            "ADDDEFCHARA" to TokenType.ADDDEFCHARA,
            "ADDSPCHARA" to TokenType.ADDDEFCHARA,
            "RESETDATA" to TokenType.RESETDATA,
            "TALENT" to TokenType.IDENTIFIER,
            "MASTER" to TokenType.IDENTIFIER,
            "TARGET" to TokenType.IDENTIFIER,
            "PLAYER" to TokenType.IDENTIFIER,
            "TRYCALL" to TokenType.TRYCALL,
            "TRYCCALLFORM" to TokenType.TRYCCALLFORM,
            "TRYCALLFORM" to TokenType.TRYCALLFORM,
            "CALLFORM" to TokenType.CALLFORM,
            "JUMPFORM" to TokenType.JUMPFORM,
            "JUMP" to TokenType.JUMP,
            "CALLF" to TokenType.CALL,
            "CALLFFORM" to TokenType.CALLFORM,
            "PRINTC" to TokenType.PRINTC_CMD,
            "PRINTLC" to TokenType.PRINTLC_CMD,
            "PRINTFORMC" to TokenType.PRINTFORMC_CMD,
            "PRINTFORMLC" to TokenType.PRINTFORMLC_CMD,
            "PRINTFORMWC" to TokenType.PRINTW,
            "RETURNF" to TokenType.RETURNF,
            "PRINTBUTTON" to TokenType.PRINTBUTTON,
            "PRINTBUTTONC" to TokenType.PRINTBUTTONC,
            "PRINTBUTTONLC" to TokenType.PRINTBUTTONLC,

            // PRINT 추가 변형
            "PRINTSINGLEFORM"  to TokenType.PRINTFORM,   // 한 줄만 표시 (PRINTFORM과 동일 처리)
            "PRINTLC"          to TokenType.PRINTLC_CMD,
            "PRINTFORMLC"      to TokenType.PRINTFORMLC_CMD,
            "PRINTFORMSW"      to TokenType.PRINTFORM,
            "PRINTFORMLW"      to TokenType.PRINTW,
            "PRINTFORMW"       to TokenType.PRINTW,      // 기존과 동일 (중복 방지용)
            "PRINTFORMWC"      to TokenType.PRINTW,

            // CALLF 계열 (함수 호출 표현식 반환값 사용)
            "CALLF"            to TokenType.CALL,
            "CALLFFORM"        to TokenType.CALLFORM,
            "CALLFFORMF"       to TokenType.CALLFORM,

            // DATA 명령어 (PRINTDATA 블록 내 단일 데이터 줄)
            "DATA"             to TokenType.DATAFORM,

            // ── 루프 제어 ──────────────────────────────────────────────────
            "BREAK"          to TokenType.BREAK,
            "CONTINUE"       to TokenType.CONTINUE,

            // ── 문자열 입력 ────────────────────────────────────────────────
            "INPUTS"         to TokenType.INPUTS,

            // ── PRINTDATA 블록 ─────────────────────────────────────────────
            "PRINTDATA"      to TokenType.PRINTDATA,
            "PRINTDATAL"     to TokenType.PRINTDATAL,
            "PRINTDATAW"     to TokenType.PRINTDATAW,
            "PRINTDATAFORM"  to TokenType.PRINTDATA,
            "PRINTDATAFORML" to TokenType.PRINTDATAL,
            "PRINTDATAFORMW" to TokenType.PRINTDATAW,
            "DATALIST"       to TokenType.DATALIST,
            "DATAFORM"       to TokenType.DATAFORM,
            "ENDDATA"        to TokenType.ENDDATA,
            "ENDLIST"        to TokenType.ENDLIST,

            // ── 예외 처리 ──────────────────────────────────────────────────
            "CATCH"          to TokenType.CATCH,
            "ENDCATCH"       to TokenType.ENDCATCH,

            // ── 변수 조작 ──────────────────────────────────────────────────
            "SWAP"           to TokenType.SWAP,
            "TIMES"          to TokenType.TIMES,
            "SETBIT"         to TokenType.SETBIT,
            "CLEARBIT"       to TokenType.CLEARBIT,
            "INVERTBIT"      to TokenType.INVERTBIT,

            // ── 캐릭터 시스템 ──────────────────────────────────────────────
            // ISASSI는 변수(캐릭터 변수)이므로 IDENTIFIER로 처리
            "ISASSI"         to TokenType.IDENTIFIER,
            "FINDCHARA"      to TokenType.FINDCHARA,
            "GETCHARA"       to TokenType.GETCHARA,
            "ADDDEFCHARA"    to TokenType.ADDDEFCHARA,
            "ADDSPCHARA"     to TokenType.ADDDEFCHARA,

            // ── 캐릭터 레벨 ────────────────────────────────────────────────
            "GETPALAMLV"     to TokenType.GETPALAMLV,
            "GETEXPLV"       to TokenType.GETEXPLV,
            "UPCHECK"        to TokenType.UPCHECK,

            // ── 게임 리셋/재시작 ───────────────────────────────────────────
            "RESETDATA"      to TokenType.RESETDATA,
            "RESTART"        to TokenType.RESTART,

            // ── 출력 제어 ──────────────────────────────────────────────────
            "REDRAW"         to TokenType.REDRAW,
            "REUSELASTLINE"  to TokenType.REUSELASTLINE,
            "PUTFORM"        to TokenType.PUTFORM,
            "CUSTOMDRAWLINE" to TokenType.CUSTOMDRAWLINE,
            "BAR"            to TokenType.BAR,
            "BARL"           to TokenType.BAR,

            // ── 배열 ───────────────────────────────────────────────────────
            "SHUFFLE"        to TokenType.SHUFFLE,
            "SPLIT"          to TokenType.SPLIT_CMD,

            // ── 유니코드 문자열 함수 ───────────────────────────────────────
            "STRLENFORM"     to TokenType.STRLENFORM,
            "STRLENSFORM"    to TokenType.STRLENFORM,
            "STRLENSU"       to TokenType.STRLENSU,
            "SUBSTRINGU"     to TokenType.SUBSTRINGU,

            // ── 폰트 ───────────────────────────────────────────────────────
            "GETFONT"        to TokenType.GETFONT,
            "CHKFONT"        to TokenType.CHKFONT,
            "FONTBOLD"       to TokenType.FONTBOLD,
            "FONTITALIC"     to TokenType.FONTITALIC,
            "FONTREGULAR"    to TokenType.FONTREGULAR,

            // ── JUMP / TRY 계열 ───────────────────────────────────────────
            "JUMP"           to TokenType.JUMP,
            "JUMPFORM"       to TokenType.JUMPFORM,
            "TRYJUMP"        to TokenType.TRYJUMP,
            "TRYCCALL"       to TokenType.TRYCCALL,
            "TRYCCALLFORM"   to TokenType.TRYCCALLFORM,

            // ── 기타 ───────────────────────────────────────────────────────
            "UNICODE"        to TokenType.UNICODE,
            "GETMILLISECOND" to TokenType.GETMILLISECOND,
            "TONEINPUT"      to TokenType.TINPUT,
            "LOOPLABEL"      to TokenType.NOP,
            "RESET_STAIN"    to TokenType.RESET_STAIN,
        )
    }

    fun tokenize(): List<Token> {
        while (pos < source.length) {
            skipWhitespaceNoNewline()
            if (pos >= source.length) break

            when {
                current() == '\n' -> {
                    // 직전 토큰이 \로 끝나면 줄 이음 처리
                    val lastToken = tokens.lastOrNull()
                    if (lastToken?.value?.endsWith("\\") == true && lastToken.type == TokenType.STRING) {
                        // 문자열 안의 백슬래시만 줄 이음 처리
                        tokens[tokens.size - 1] = lastToken.copy(value = lastToken.value.dropLast(1))
                    } else {
                        addToken(TokenType.NEWLINE, "\n")
                    }
                    advance()
                    line++
                    column = 1
                }

                current() == '\r' -> {
                    advance()
                    if (pos < source.length && current() == '\n') {
                        advance()
                    }
                    addToken(TokenType.NEWLINE, "\n")
                    line++
                    column = 1
                }
                current() == ';' -> readLineComment()
                source.startsWith(";;", pos) -> readLineComment()
                current() == '@' -> readFunctionOrLabel()
                current() == '$' -> readLabel()
                current() == '#' -> readSharpDirective()
                current() == '"' -> readString()
                current().isDigit() -> readNumber()
                current().isLetter() || current() == '_' -> readIdentifierOrKeyword()
                current() == '+' -> readPlusOp()
                current() == '-' -> readMinusOp()
                current() == '*' -> readMultiplyOp()
                current() == '/' -> readDivideOp()
                current() == '%' -> readModuloOp()
                current() == '^' -> {
                    advance()
                    if (pos < source.length && current() == '^') {
                        addToken(TokenType.BITXOR, "^^"); advance()
                    } else {
                        addToken(TokenType.BITXOR, "^")
                    }
                }
                current() == '=' -> readAssignOrEq()
                current() == '!' -> readNotOrNeq()
                current() == '<' -> readLtOp()
                current() == '>' -> readGtOp()
                current() == '&' -> readAndOp()
                current() == '|' -> readOrOp()
                current() == '~' -> { addToken(TokenType.BITNOT, "~"); advance() }
                current() == '?' -> { addToken(TokenType.TERNARY_IF, "?"); advance() }
                current() == '(' -> { addToken(TokenType.LPAREN, "("); advance() }
                current() == ')' -> { addToken(TokenType.RPAREN, ")"); advance() }
                current() == '[' -> { addToken(TokenType.LBRACKET, "["); advance() }
                current() == ']' -> { addToken(TokenType.RBRACKET, "]"); advance() }
                current() == ',' -> { addToken(TokenType.COMMA, ","); advance() }
                current() == ':' -> { addToken(TokenType.COLON, ":"); advance() }
                current() == '{' -> { addToken(TokenType.LBRACKET, "{"); advance() }
                current() == '}' -> { addToken(TokenType.RBRACKET, "}"); advance() }
                current() == '.' -> { addToken(TokenType.IDENTIFIER, "."); advance() }
                else -> {
                    // 알 수 없는 문자도 IDENTIFIER로 보존 (특수문자, 한자, 기호 등)
                    val sb = StringBuilder()
                    while (pos < source.length
                        && current() != '\n'
                        && current() != ' '
                        && current() != '\t'
                        && !current().isLetterOrDigit()
                        && current() != '_'
                        && current() != '"'
                        && current() != ';'
                    ) {
                        sb.append(advance())
                    }
                    if (sb.isNotEmpty()) addToken(TokenType.IDENTIFIER, sb.toString())
                }
            }
        }
        addToken(TokenType.EOF, "")
        return tokens
    }

    private fun current() = source[pos]
    private fun peek(offset: Int = 1) = if (pos + offset < source.length) source[pos + offset] else '\u0000'

    private fun advance(): Char {
        val c = source[pos]
        pos++
        column++
        return c
    }

    private fun addToken(type: TokenType, value: String) {
        tokens.add(Token(type, value, line, column))
    }

    private fun skipWhitespaceNoNewline() {
        while (pos < source.length && (current() == ' ' || current() == '\t')) advance()
    }

    private fun readLineComment() {
        val start = pos
        while (pos < source.length && current() != '\n' && current() != '\r') advance()
        addToken(TokenType.COMMENT, source.substring(start, pos))
    }

    private fun readFunctionOrLabel() {
        advance() // consume '@'
        val start = pos
        while (pos < source.length && (current().isLetterOrDigit() || current() == '_')) advance()
        val name = source.substring(start, pos)

        // @"..." 형식 — ERA의 FORMSTRING 리터럴. 내용을 그대로 STRING으로 내보냄
        if (name.isEmpty() && pos < source.length && current() == '"') {
            readString()
            return
        }

        addToken(TokenType.FUNCTION_LABEL, "@$name")

        // @FUNC(ARG:0, ...) 형식 — 괄호째로 줄 끝까지 스킵
        // name이 비어있으면 \@( 같은 삼항 연산자이므로 스킵하지 않음
        if (name.isNotEmpty() && pos < source.length && current() == '(') {
            while (pos < source.length && current() != '\n' && current() != '\r') advance()
            return
        }

        // @FUNC, ARG:0, ARG:1 형식 — 쉼표로 시작하면 줄 끝까지 스킵 (파라미터 선언이므로 무시)
        // name이 비어있으면 스킵하지 않음
        if (name.isNotEmpty()) {
            skipWhitespaceNoNewline()
            if (pos < source.length && current() == ',') {
                while (pos < source.length && current() != '\n' && current() != '\r') advance()
            }
        }
    }

    private fun readLabel() {
        advance() // consume '$'
        val start = pos
        while (pos < source.length && (current().isLetterOrDigit() || current() == '_')) advance()
        val name = source.substring(start, pos)
        addToken(TokenType.LABEL, "\$$name")
    }

    private fun readSharpDirective() {
        advance() // consume '#'
        val start = pos
        while (pos < source.length && current().isLetter()) advance()
        val directive = source.substring(start, pos).uppercase()
        val type = when (directive) {
            "DIM" -> TokenType.SHARP_DIM
            "DIMS" -> TokenType.SHARP_DIMS
            "CONST" -> TokenType.SHARP_CONST
            "DEFINE" -> TokenType.SHARP_DEFINE
            "LOCALSIZE" -> TokenType.SHARP_LOCALSIZE
            "LOCALSSIZE" -> TokenType.SHARP_LOCALSSIZE
            "FUNCTION" -> TokenType.SHARP_FUNCTION
            "SINGLE" -> TokenType.SHARP_SINGLE
            "LATER" -> TokenType.SHARP_LATER
            "ONLY" -> TokenType.SHARP_ONLY
            "PRI" -> TokenType.SHARP_PRI
            else -> TokenType.IDENTIFIER
        }
        addToken(type, "#$directive")
    }

    private fun readString() {
        advance() // consume '"'
        val sb = StringBuilder()
        while (pos < source.length && current() != '"' && current() != '\n') {
            if (current() == '\\' && peek() == '"') {
                sb.append('"')
                advance(); advance()
            } else {
                sb.append(advance())
            }
        }
        if (pos < source.length && current() == '"') advance()
        addToken(TokenType.STRING, sb.toString())
    }

    private fun readNumber() {
        val start = pos
        var isFloat = false
        // 0x / 0X 16진수 처리 (예: 0x0020, 0xFF00FF)
        if (pos < source.length && current() == '0') {
            val next = if (pos + 1 < source.length) source[pos + 1] else ' '
            if (next == 'x' || next == 'X') {
                advance() // '0'
                advance() // 'x'
                val hexStart = pos
                while (pos < source.length && (current().isDigit() || current() in 'a'..'f' || current() in 'A'..'F')) advance()
                val hex = source.substring(hexStart, pos)
                val value = hex.toLongOrNull(16) ?: 0L
                addToken(TokenType.INTEGER, value.toString())
                return
            }
        }
        while (pos < source.length && current().isDigit()) advance()
        // 1p6 형식 처리: ERA의 비트 시프트 표현 (1p6 = 1 << 6)
        if (pos < source.length && (current() == 'p' || current() == 'P') && peek().isDigit()) {
            val base = source.substring(start, pos).toLongOrNull() ?: 1L
            advance() // 'p' 소비
            val expStart = pos
            while (pos < source.length && current().isDigit()) advance()
            val exp = source.substring(expStart, pos).toIntOrNull() ?: 0
            val value = base shl exp
            addToken(TokenType.INTEGER, value.toString())
            return
        }
        if (pos < source.length && current() == '.' && peek().isDigit()) {
            isFloat = true
            advance()
            while (pos < source.length && current().isDigit()) advance()
        }
        val num = source.substring(start, pos)
        addToken(if (isFloat) TokenType.FLOAT else TokenType.INTEGER, num)
    }

    private fun readIdentifierOrKeyword() {
        val start = pos
        // x로 시작하는 hex 색상값 처리 (예: xFF0000)
        if (current() == 'x' || current() == 'X') {
            val next = peek()
            if (next.isDigit() || next in 'a'..'f' || next in 'A'..'F') {
                advance() // x
                while (pos < source.length && (current().isDigit() || current() in 'a'..'f' || current() in 'A'..'F')) advance()
                val hex = source.substring(start + 1, pos)
                val value = hex.toLongOrNull(16) ?: 0L
                addToken(TokenType.INTEGER, value.toString())
                return
            }
        }
        while (pos < source.length && (current().isLetterOrDigit() || current() == '_')) advance()
        // LOCAL@FUNCNAME 형태: @ 뒤 함수명을 이름의 일부로 읽음 (같은 IDENTIFIER 토큰으로)
        if (pos < source.length && current() == '@') {
            advance() // '@' 소비
            while (pos < source.length && (current().isLetterOrDigit() || current() == '_')) advance()
        }
        val word = source.substring(start, pos)
        val upper = word.uppercase()
        // p0~p99 파라미터를 ARG:N 으로 변환
        if (upper.matches(Regex("P\\d{1,2}"))) {
            val idx = upper.substring(1).toIntOrNull() ?: 0
            addToken(TokenType.IDENTIFIER, "ARG")
            addToken(TokenType.COLON, ":")
            addToken(TokenType.INTEGER, idx.toString())
            return
        }
        val type = KEYWORDS[upper] ?: TokenType.IDENTIFIER
        // RESTART는 GOTO __RESTART__ 두 토큰으로 변환
        if (upper == "RESTART") {
            addToken(TokenType.GOTO, "GOTO")
            addToken(TokenType.IDENTIFIER, "__RESTART__")
            return
        }
        addToken(type, word)
        // PRINT 계열 명령어 뒤의 텍스트를 공백 포함 raw STRING 토큰으로 읽기
        if (type in listOf(
                TokenType.PRINT, TokenType.PRINTL, TokenType.PRINTW, TokenType.PRINTD,
                TokenType.PRINTFORM, TokenType.PRINTFORML,
                TokenType.DEBUGPRINT,
                TokenType.PRINTDATA, TokenType.PRINTDATAL, TokenType.PRINTDATAW,
                TokenType.PUTFORM, TokenType.CUSTOMDRAWLINE
            )) {
            // 앞 공백 하나만 스킵 (명령어와 텍스트 사이 구분자)
            if (pos < source.length && current() == ' ') advance()
            // 줄 끝까지 raw로 읽어 STRING 토큰으로 저장
            val textStart = pos
            while (pos < source.length && current() != '\n' && current() != '\r') advance()
            val text = source.substring(textStart, pos).trimEnd()
            if (text.isNotEmpty()) addToken(TokenType.STRING, text)
        }
    }

    private fun readPlusOp() {
        advance()
        when {
            pos < source.length && current() == '+' -> { addToken(TokenType.INCREMENT, "++"); advance() }
            pos < source.length && current() == '=' -> { addToken(TokenType.PLUS_ASSIGN, "+="); advance() }
            else -> addToken(TokenType.PLUS, "+")
        }
    }

    private fun readMinusOp() {
        advance()
        when {
            pos < source.length && current() == '-' -> { addToken(TokenType.DECREMENT, "--"); advance() }
            pos < source.length && current() == '=' -> { addToken(TokenType.MINUS_ASSIGN, "-="); advance() }
            else -> addToken(TokenType.MINUS, "-")
        }
    }

    private fun readMultiplyOp() {
        advance()
        if (pos < source.length && current() == '=') {
            addToken(TokenType.MULTIPLY_ASSIGN, "*="); advance()
        } else {
            addToken(TokenType.MULTIPLY, "*")
        }
    }

    private fun readDivideOp() {
        advance()
        if (pos < source.length && current() == '=') {
            addToken(TokenType.DIVIDE_ASSIGN, "/="); advance()
        } else {
            addToken(TokenType.DIVIDE, "/")
        }
    }

    private fun readModuloOp() {
        advance()
        if (pos < source.length && current() == '=') {
            addToken(TokenType.MODULO_ASSIGN, "%="); advance()
        } else {
            addToken(TokenType.MODULO, "%")
        }
    }

    private fun readAssignOrEq() {
        advance()
        if (pos < source.length && current() == '=') {
            addToken(TokenType.EQ, "=="); advance()
        } else {
            addToken(TokenType.ASSIGN, "=")
        }
    }

    private fun readNotOrNeq() {
        advance()
        if (pos < source.length && current() == '=') {
            addToken(TokenType.NEQ, "!="); advance()
        } else {
            addToken(TokenType.NOT, "!")
        }
    }

    private fun readLtOp() {
        advance()
        when {
            pos < source.length && current() == '=' -> { addToken(TokenType.LTE, "<="); advance() }
            pos < source.length && current() == '<' -> { addToken(TokenType.LSHIFT, "<<"); advance() }
            else -> addToken(TokenType.LT, "<")
        }
    }

    private fun readGtOp() {
        advance()
        when {
            pos < source.length && current() == '=' -> { addToken(TokenType.GTE, ">="); advance() }
            pos < source.length && current() == '>' -> { addToken(TokenType.RSHIFT, ">>"); advance() }
            else -> addToken(TokenType.GT, ">")
        }
    }

    private fun readAndOp() {
        advance()
        if (pos < source.length && current() == '&') {
            addToken(TokenType.AND, "&&"); advance()
        } else {
            addToken(TokenType.BITAND, "&")
        }
    }

    private fun readOrOp() {
        advance()
        if (pos < source.length && current() == '|') {
            addToken(TokenType.OR, "||"); advance()
        } else {
            addToken(TokenType.BITOR, "|")
        }
    }
}