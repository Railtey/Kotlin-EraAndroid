package com.eraandroid.core.parser

import com.eraandroid.core.lexer.Token
import com.eraandroid.core.lexer.TokenType

class Parser(internal val tokens: List<Token>, internal val fileName: String = "", internal val sourceLines: List<String> = emptyList()) {

    internal var pos = 0

    internal fun current(): Token = tokens.getOrNull(pos) ?: Token(TokenType.EOF, "", 0, 0)
    internal fun peek(offset: Int = 1): Token = tokens.getOrNull(pos + offset) ?: Token(TokenType.EOF, "", 0, 0)
    internal fun advance(): Token = tokens.getOrNull(pos++)  ?: Token(TokenType.EOF, "", 0, 0)
    internal fun check(type: TokenType) = current().type == type
    internal fun match(vararg types: TokenType): Boolean {
        if (current().type in types) { advance(); return true }
        return false
    }
    internal fun expect(type: TokenType): Token {
        if (current().type != type)
            throw ParseException("Expected $type but got ${current().type} '${current().value}'", current().line)
        return advance()
    }
    internal fun skipNewlines() { while (check(TokenType.NEWLINE) || check(TokenType.COMMENT)) advance() }
    internal fun skipToNewline() { while (!check(TokenType.NEWLINE) && !check(TokenType.EOF)) advance() }

    internal fun peekRestOfLine(): String {
        val token = tokens.getOrNull(pos) ?: return ""
        val lineNo = token.line  // 1-based

        // 원본 소스 줄이 있으면 현재 토큰 시작 위치부터 줄 끝까지 직접 반환
        if (sourceLines.isNotEmpty() && lineNo >= 1 && lineNo <= sourceLines.size) {
            val srcLine = sourceLines[lineNo - 1]
            // column은 끝+1이므로 토큰 시작 = column - value.length - 1 (0-based)
            val tokStart = (token.column - token.value.length - 1).coerceIn(0, srcLine.length)
            return srcLine.substring(tokStart)
        }

        // fallback: 토큰 재조합 (원본 소스 없을 때)
        val sb = StringBuilder()
        var i = pos
        var prevEnd = -1
        while (i < tokens.size && tokens[i].type != TokenType.NEWLINE && tokens[i].type != TokenType.EOF) {
            val t = tokens[i]
            val tokText = when (t.type) {
                TokenType.STRING -> "\"${t.value}\""
                else -> t.value
            }
            val tokenStart = t.column - tokText.length
            val tokenEnd   = t.column - 1
            if (prevEnd >= 0) {
                val spaces = (tokenStart - prevEnd - 1).coerceAtLeast(0)
                if (spaces > 0) sb.append(" ".repeat(spaces))
            }
            sb.append(tokText)
            prevEnd = tokenEnd
            i++
        }
        return sb.toString()
    }

    fun parse(): Program {
        val functions = mutableListOf<FunctionDef>()
        val globals = mutableListOf<AstNode>()
        skipNewlines()
        while (!check(TokenType.EOF)) {
            when (current().type) {
                TokenType.FUNCTION_LABEL -> functions.add(parseFunction())
                TokenType.SHARP_DIM, TokenType.SHARP_DIMS -> globals.add(parseDim())
                TokenType.SHARP_DEFINE -> globals.add(parseDefine())
                TokenType.COMMENT -> advance()
                TokenType.NEWLINE -> advance()
                else -> { skipToNewline(); skipNewlines() }
            }
        }
        return Program(functions, globals)
    }

    internal fun parseFunction(): FunctionDef {
        val label = advance() // @FunctionName
        val name = label.value.removePrefix("@")
        val line = label.line
        val attributes = mutableSetOf<FunctionAttribute>()
        val params = mutableListOf<ParamDef>()

        // 함수 파라미터 선언 처리: @FUNC(p0, p1, p2)
        if (check(TokenType.LPAREN)) {
            advance()
            while (!check(TokenType.RPAREN) && !check(TokenType.NEWLINE) && !check(TokenType.EOF)) {
                try { parseExpression() } catch (e: Exception) { }
                // 기본값 처리: ARG:0 = "" 같은 문법
                if (check(TokenType.ASSIGN)) {
                    advance()
                    try { parseExpression() } catch (e: Exception) { }
                }
                if (check(TokenType.COMMA)) advance() else break
            }
            if (check(TokenType.RPAREN)) advance()
        }

        // Parse attributes like #SINGLE #LATER #ONLY #PRI
        skipNewlines()
        while (current().type in listOf(
                TokenType.SHARP_SINGLE, TokenType.SHARP_LATER, TokenType.SHARP_ONLY, TokenType.SHARP_PRI,
                TokenType.SHARP_LOCALSIZE, TokenType.SHARP_LOCALSSIZE, TokenType.SHARP_FUNCTION
            )) {
            when (current().type) {
                TokenType.SHARP_SINGLE -> { attributes.add(FunctionAttribute.SINGLE); advance() }
                TokenType.SHARP_LATER -> { attributes.add(FunctionAttribute.LATER); advance() }
                TokenType.SHARP_ONLY -> { attributes.add(FunctionAttribute.ONLY); advance() }
                TokenType.SHARP_PRI -> { attributes.add(FunctionAttribute.PRI); advance() }
                else -> { skipToNewline() }
            }
            skipNewlines()
        }

        val body = parseBody(stopAt = { it.type == TokenType.FUNCTION_LABEL || it.type == TokenType.EOF })
        return FunctionDef(name, params, body, attributes, line, fileName)
    }

    internal fun parseBody(stopAt: (Token) -> Boolean): List<Statement> {
        val stmts = mutableListOf<Statement>()
        while (!stopAt(current()) && !check(TokenType.EOF)) {
            skipNewlines()
            if (stopAt(current()) || check(TokenType.EOF)) break
            val stmt = parseStatement() ?: continue
            stmts.add(stmt)
        }
        return stmts
    }

    internal fun readLabel(): String {
        return when (current().type) {
            TokenType.LABEL -> advance().value.removePrefix("\$")
            TokenType.IDENTIFIER -> advance().value
            else -> throw ParseException("Expected label but got ${current().type}", current().line)
        }
    }

    internal fun parseStatement(): Statement? {
        val token = current()
        return when (token.type) {
            TokenType.COMMENT, TokenType.NEWLINE -> { advance(); null }
            TokenType.LABEL -> { val s = LabelStatement(advance().value.removePrefix("\$"), token.line); skipNewlines(); s }
            TokenType.IF -> try { parseIf() } catch (e: Exception) {
                while (!check(TokenType.ENDIF) && !check(TokenType.FUNCTION_LABEL) && !check(TokenType.EOF)) advance()
                if (check(TokenType.ENDIF)) advance()
                skipNewlines()
                null
            }
            TokenType.SIF -> parseSif()
            TokenType.FOR -> parseFor()
            TokenType.WHILE -> parseWhile()
            TokenType.DO -> parseDoLoop()
            TokenType.REPEAT -> parseRepeat()
            TokenType.SELECTCASE -> parseSelectCase()
            TokenType.PRINT, TokenType.PRINTL, TokenType.PRINTW, TokenType.PRINTD,
            TokenType.PRINTFORM, TokenType.PRINTFORML, TokenType.PRINTV, TokenType.PRINTVL,
            TokenType.PRINTS, TokenType.PRINTSL,
            TokenType.PRINTC_CMD, TokenType.PRINTLC_CMD,
            TokenType.PRINTFORMC_CMD, TokenType.PRINTFORMLC_CMD -> parsePrint()
            TokenType.INPUT, TokenType.INPUTMOUSEKEY -> parseInput()
            TokenType.TINPUT -> parseTInput()
            TokenType.WAIT -> { advance(); skipNewlines(); WaitStatement(false, token.line) }
            TokenType.TWAIT -> parseTwait()
            TokenType.BUTTON -> parseButton()
            TokenType.PRINTBUTTON -> parsePrintButton(isInline = false)
            TokenType.PRINTBUTTONC, TokenType.PRINTBUTTONLC -> parsePrintButton(isInline = true)
            TokenType.SET_COLOR -> parseSetColor()
            TokenType.RESET_COLOR -> { advance(); skipNewlines(); ResetColorStatement(token.line) }
            TokenType.SET_BG_COLOR -> parseSetBgColor()
            TokenType.RESET_BG_COLOR -> { advance(); skipNewlines(); ResetBgColorStatement(token.line) }
            TokenType.DRAWLINE -> { advance(); skipNewlines(); DrawLineStatement(token.line) }
            TokenType.CLEARLINE -> parseClearLine()
            TokenType.ALIGNMENT -> { advance(); AlignmentStatement(parseExpression(), token.line).also { skipNewlines() } }
            TokenType.FONTSTYLE -> { advance(); FontStyleStatement(parseExpression(), token.line).also { skipNewlines() } }
            TokenType.SETFONT -> { advance(); SetFontStatement(parseExpression(), token.line).also { skipNewlines() } }
            TokenType.CALL -> parseCall(isTry = false)
            TokenType.CALLFORM -> parseCallForm(isTry = false)
            TokenType.TRYCALL -> parseCall(isTry = true)        // ← 추가
            TokenType.TRYFUNC -> parseCall(isTry = true)
            TokenType.TRYCALLFORM -> parseCallForm(isTry = true)
            TokenType.RETURN -> parseReturn()
            TokenType.GOTO -> { advance(); GotoStatement(readLabel(), token.line).also { skipNewlines() } }
            TokenType.GOSUB -> { advance(); GosubStatement(readLabel(), token.line).also { skipNewlines() } }
            TokenType.VARSET -> parseVarSet()
            TokenType.CVARSET -> parseCvarSet()
            TokenType.SAVEDATA -> parseSaveData()
            TokenType.LOADDATA -> { advance(); LoadDataStatement(parseExpression(), token.line).also { skipNewlines() } }
            TokenType.DELDATA -> { advance(); DelDataStatement(parseExpression(), token.line).also { skipNewlines() } }
            TokenType.CHKDATA -> { advance(); ChkDataStatement(parseExpression(), token.line).also { skipNewlines() } }
            TokenType.SAVEGLOBAL -> { advance(); skipNewlines(); SaveGlobalStatement(token.line) }
            TokenType.LOADGLOBAL -> { advance(); skipNewlines(); LoadGlobalStatement(token.line) }
            TokenType.ADDCHARA -> { advance(); AddCharaStatement(parseExpression(), token.line).also { skipNewlines() } }
            TokenType.DELCHARA -> { advance(); DelCharaStatement(parseExpression(), token.line).also { skipNewlines() } }
            TokenType.SWAPCHARA -> { advance(); val a = parseExpression(); expect(TokenType.COMMA); SwapCharaStatement(a, parseExpression(), token.line).also { skipNewlines() } }
            TokenType.SORTCHARA -> parseSortChara()
            TokenType.ADDCOPYCHARA -> { advance(); val arg = parseExpression(); skipNewlines(); CallStatement(StringLiteral("__ADDCOPYCHARA__", token.line), listOf(arg), token.line, false) }
            TokenType.THROW -> { advance(); ThrowStatement(parseExpression(), token.line).also { skipNewlines() } }
            TokenType.QUIT -> { advance(); skipNewlines(); QuitStatement(token.line) }
            TokenType.NOP -> { advance(); skipNewlines(); NopStatement(token.line) }
            TokenType.BEGIN -> parseBegin()
            TokenType.RETURNF -> {
                advance()
                val expr = parseExpression()
                skipNewlines()
                ReturnStatement(expr, isFunctionReturn = true, line = token.line)
            }
            TokenType.DEBUGPRINT -> { advance(); DebugPrintStatement(parseExpressionList(), token.line).also { skipNewlines() } }
            TokenType.SHARP_DIM, TokenType.SHARP_DIMS -> parseDim()
            TokenType.SHARP_DEFINE -> parseDefine()

            // ── 루프 제어 ────────────────────────────────────────────────────
            TokenType.BREAK    -> { advance(); skipNewlines(); BreakStatement(token.line) }
            TokenType.CONTINUE -> { advance(); skipNewlines(); ContinueStatement(token.line) }

            // ── 문자열 입력 ──────────────────────────────────────────────────
            TokenType.INPUTS -> parseInputs()

            // ── PRINTDATA 블록 ───────────────────────────────────────────────
            TokenType.PRINTDATA, TokenType.PRINTDATAL, TokenType.PRINTDATAW -> parsePrintData(token)

            // ── CATCH 블록 ───────────────────────────────────────────────────
            TokenType.CATCH -> parseCatch(token)

            // ── 변수 조작 ────────────────────────────────────────────────────
            TokenType.SWAP      -> { advance(); val a = parseExpression(); expect(TokenType.COMMA); SwapStatement(a, parseExpression(), token.line).also { skipNewlines() } }
            TokenType.TIMES     -> { advance(); val t = parseExpression(); expect(TokenType.COMMA); TimesStatement(t, parseExpression(), token.line).also { skipNewlines() } }
            TokenType.SETBIT    -> { advance(); val t = parseExpression(); expect(TokenType.COMMA); SetBitStatement(t, parseExpression(), token.line).also { skipNewlines() } }
            // GETBIT VARNAME, BIT → RESULT에 비트값 저장 (Statement 위치)
            TokenType.GETBIT -> {
                advance()
                val target = parseExpression()
                val bit = if (check(TokenType.COMMA)) { advance(); parseExpression() } else IntLiteral(0, token.line)
                // CallStatement로 처리: Interpreter builtinHandled에서 RESULT 저장
                CallStatement(VariableRef("__GETBIT__", emptyList(), token.line), listOf(target, bit), token.line, false).also { skipNewlines() }
            }
            TokenType.CLEARBIT  -> { advance(); val t = parseExpression(); expect(TokenType.COMMA); ClearBitStatement(t, parseExpression(), token.line).also { skipNewlines() } }
            TokenType.INVERTBIT -> { advance(); val t = parseExpression(); expect(TokenType.COMMA); InvertBitStatement(t, parseExpression(), token.line).also { skipNewlines() } }

            // ── 캐릭터 ──────────────────────────────────────────────────────
            TokenType.FINDCHARA   -> parseFindChara()
            TokenType.GETCHARA    -> { advance(); GetCharaStatement(IntLiteral(0, token.line), parseExpression(), token.line).also { skipNewlines() } }
            TokenType.ADDDEFCHARA -> { advance(); val arg = if (!check(TokenType.NEWLINE) && !check(TokenType.EOF)) parseExpression() else IntLiteral(-1, token.line); skipNewlines(); AddDefCharaStatement(arg, token.line) }
            // ISASSI는 변수이므로 IDENTIFIER로 파싱됨 → parseIdentifierOrCall에서 처리
            TokenType.GETPALAMLV  -> parseGetLv(isPalam = true)
            TokenType.GETEXPLV    -> parseGetLv(isPalam = false)
            TokenType.UPCHECK     -> { advance(); skipNewlines(); UpCheckStatement(token.line) }

            // ── 리셋/재시작 ──────────────────────────────────────────────────
            TokenType.RESETDATA -> { advance(); skipNewlines(); ResetDataStatement(token.line) }
            TokenType.RESTART   -> { advance(); skipNewlines(); RestartStatement(token.line) }

            // ── 출력 제어 ────────────────────────────────────────────────────
            TokenType.REDRAW         -> { advance(); val f = if (!check(TokenType.NEWLINE) && !check(TokenType.EOF)) parseExpression() else null; skipNewlines(); RedrawStatement(f, token.line) }
            TokenType.REUSELASTLINE  -> { advance(); val t = if (!check(TokenType.NEWLINE) && !check(TokenType.EOF)) parseExpression() else null; skipNewlines(); ReuseLastLineStatement(t, token.line) }
            TokenType.PUTFORM        -> { advance(); PutFormStatement(parseExpression(), token.line).also { skipNewlines() } }
            TokenType.CUSTOMDRAWLINE -> { advance(); val t = if (!check(TokenType.NEWLINE) && !check(TokenType.EOF)) parseExpression() else null; skipNewlines(); CustomDrawLineStatement(t ?: StringLiteral("-", token.line), token.line) }
            TokenType.BAR            -> parseBar()

            // ── 배열 ─────────────────────────────────────────────────────────
            TokenType.SHUFFLE   -> { advance(); ShuffleStatement(parseExpression(), token.line).also { skipNewlines() } }
            TokenType.SPLIT_CMD -> parseSplitCmd()
            TokenType.SPLIT     -> parseSplitCmd()
            TokenType.CALLTRAIN -> { advance(); skipNewlines(); CallTrainStatement(token.line) }
            TokenType.ARRAYSHIFT   -> parseArrayShift()
            TokenType.ARRAYREMOVE  -> parseArrayRemove()
            TokenType.NOBLANK, TokenType.NODISP -> { advance(); skipNewlines(); NopStatement(token.line) }
            TokenType.RESET_STAIN -> {
                advance()
                val arg = if (!check(TokenType.NEWLINE) && !check(TokenType.EOF)) parseExpression() else IntLiteral(0, token.line)
                skipNewlines()
                CallStatement(StringLiteral("__RESET_STAIN__", token.line), listOf(arg), token.line, false)
            }

            // ── JUMP 계열 ────────────────────────────────────────────────────
            TokenType.JUMP          -> parseJump(isTry = false)
            TokenType.JUMPFORM      -> parseJumpForm(isTry = false)
            TokenType.TRYJUMP       -> parseJump(isTry = true)
            TokenType.TRYCCALL      -> parseCall(isTry = true)
            TokenType.TRYCCALLFORM  -> parseCallForm(isTry = true)

            // ── 폰트 단축 ────────────────────────────────────────────────────
            TokenType.FONTBOLD    -> { advance(); skipNewlines(); FontStyleStatement(IntLiteral(1, token.line), token.line) }
            TokenType.FONTITALIC  -> { advance(); skipNewlines(); FontStyleStatement(IntLiteral(2, token.line), token.line) }
            TokenType.FONTREGULAR -> { advance(); skipNewlines(); FontStyleStatement(IntLiteral(0, token.line), token.line) }
            TokenType.GETFONT     -> { advance(); skipNewlines(); NopStatement(token.line) }  // 결과 무시
            TokenType.CHKFONT     -> { advance(); skipNewlines(); NopStatement(token.line) }

            // ── 기타 ─────────────────────────────────────────────────────────
            TokenType.UNICODE        -> { advance(); UnicodeStatement(parseExpression(), token.line).also { skipNewlines() } }
            TokenType.GETMILLISECOND -> { advance(); skipNewlines(); NopStatement(token.line) }
            TokenType.GETEXPLV       -> parseGetLv(isPalam = false)
            TokenType.IDENTIFIER -> try { parseIdentifierStatement() } catch (e: Exception) {
                skipToNewline(); skipNewlines(); null
            }
            // 수식 함수(STRLENS, ABS, MAX 등)가 statement 위치에 오면 CallStatement로 처리
            TokenType.STRLENS, TokenType.STRLENSU, TokenType.ABS, TokenType.SQRT, TokenType.SIGN,
            TokenType.MAX, TokenType.MIN, TokenType.LIMIT, TokenType.INRANGE,
            TokenType.TOINT, TokenType.TOSTR, TokenType.ISNUMERIC, TokenType.STRFIND,
            TokenType.SUBSTRING, TokenType.CHARATU, TokenType.GETTIME -> {
                val funcName = token.value
                advance()
                val args = mutableListOf<Expression>()
                while (!check(TokenType.NEWLINE) && !check(TokenType.EOF)) {
                    try { args.add(parseExpression()) } catch (e: Exception) { break }
                    if (check(TokenType.COMMA)) advance() else break
                }
                skipNewlines()
                CallStatement(StringLiteral(funcName, token.line), args, token.line, false)
            }
            else -> { skipToNewline(); skipNewlines(); null }
        }
    }


    internal fun parseExpressionList(): List<Expression> {
        val list = mutableListOf(parseExpression())
        while (check(TokenType.COMMA)) { advance(); list.add(parseExpression()) }
        return list
    }

    fun parseExpression(): Expression = parseTernary()


}

class ParseException(message: String, val line: Int) : Exception("Line $line: $message")