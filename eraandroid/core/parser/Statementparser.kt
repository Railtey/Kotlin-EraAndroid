package com.eraandroid.core.parser

import com.eraandroid.core.lexer.Token
import com.eraandroid.core.lexer.TokenType

// ─── 명령어별 파싱 (Parser extension) ───────────────────────────────────────

internal fun Parser.parseIf(): IfStatement {
    val line = current().line
    advance() // IF
    val condition = try {
        parseExpression()
    } catch (e: Exception) {
        skipToNewline(); skipNewlines()
        while (!check(TokenType.ENDIF) && !check(TokenType.FUNCTION_LABEL) && !check(TokenType.EOF)) advance()
        if (check(TokenType.ENDIF)) advance()
        skipNewlines()
        return IfStatement(listOf(IfBranch(IntLiteral(0, line), emptyList())), null, line)
    }
    skipNewlines()
    val branches = mutableListOf<IfBranch>()
    val body = parseBody { it.type in listOf(TokenType.ELSEIF, TokenType.ELSE, TokenType.ENDIF) }
    branches.add(IfBranch(condition, body))

    while (check(TokenType.ELSEIF)) {
        advance()
        val elseifCond = try {
            parseExpression()
        } catch (e: Exception) {
            skipToNewline(); skipNewlines()
            continue
        }
        skipNewlines()
        val elseifBody = parseBody { it.type in listOf(TokenType.ELSEIF, TokenType.ELSE, TokenType.ENDIF) }
        branches.add(IfBranch(elseifCond, elseifBody))
    }

    var elseBody: List<Statement>? = null
    if (check(TokenType.ELSE)) {
        advance(); skipNewlines()
        // ↓ 추가: ELSE IF 형태 처리
        if (check(TokenType.IF)) {
            val elseIfStmt = parseIf()
            val remaining = if (check(TokenType.ENDIF) || check(TokenType.FUNCTION_LABEL) || check(TokenType.EOF)) {
                emptyList()
            } else {
                parseBody { it.type == TokenType.ENDIF || it.type == TokenType.FUNCTION_LABEL }
            }
            elseBody = listOf(elseIfStmt) + remaining
        } else {
            elseBody = parseBody { it.type == TokenType.ENDIF }
        }
    }
    if (check(TokenType.ENDIF)) advance()
    skipNewlines()
    return IfStatement(branches, elseBody, line)
}

internal fun Parser.parseSif(): SifStatement {
    val line = current().line
    advance()
    val cond = parseExpression()
    skipNewlines()
    val stmt = parseStatement() ?: NopStatement(line)
    return SifStatement(cond, stmt, line)
}

internal fun Parser.parseFor(): ForStatement {
    val line = current().line
    advance()
    val variable = parseExpression()
    expect(TokenType.COMMA)
    val from = parseExpression()
    expect(TokenType.COMMA)
    val to = parseExpression()
    val step = if (check(TokenType.COMMA)) { advance(); parseExpression() } else null
    skipNewlines()
    val body = parseBody { it.type == TokenType.NEXT }
    expect(TokenType.NEXT)
    skipNewlines()
    return ForStatement(variable, from, to, step, body, line)
}

internal fun Parser.parseWhile(): WhileStatement {
    val line = current().line
    advance()
    val condition = parseExpression()
    skipNewlines()
    val body = parseBody { it.type == TokenType.WEND }
    expect(TokenType.WEND)
    skipNewlines()
    return WhileStatement(condition, body, line)
}

internal fun Parser.parseDoLoop(): DoLoopStatement {
    val line = current().line
    advance(); skipNewlines()
    val body = parseBody { it.type == TokenType.LOOP }
    expect(TokenType.LOOP)
    val isUntil = check(TokenType.UNTIL)
    val condition = if (check(TokenType.WHILE) || check(TokenType.UNTIL)) {
        advance(); parseExpression()
    } else null
    skipNewlines()
    return DoLoopStatement(body, condition, isUntil, line)
}

internal fun Parser.parseRepeat(): RepeatStatement {
    val line = current().line
    advance()
    val count = parseExpression()
    skipNewlines()
    val body = parseBody { it.type == TokenType.REND }
    expect(TokenType.REND)
    skipNewlines()
    return RepeatStatement(count, body, line)
}

internal fun Parser.parseSelectCase(): SelectCaseStatement {
    val line = current().line
    advance()
    val expr = parseExpression()
    skipNewlines()
    val cases = mutableListOf<CaseBranch>()
    var elseBody: List<Statement>? = null
    while (!check(TokenType.ENDSELECT) && !check(TokenType.EOF)) {
        when (current().type) {
            TokenType.CASE -> {
                advance()
                val conditions = parseCaseConditions()
                skipNewlines()
                val body = parseBody { it.type in listOf(TokenType.CASE, TokenType.CASEELSE, TokenType.ENDSELECT) }
                cases.add(CaseBranch(conditions, body))
            }
            TokenType.CASEELSE -> {
                advance(); skipNewlines()
                elseBody = parseBody { it.type == TokenType.ENDSELECT }
            }
            else -> { advance() }
        }
    }
    if (check(TokenType.ENDSELECT)) advance()
    skipNewlines()
    return SelectCaseStatement(expr, cases, elseBody, line)
}

internal fun Parser.parseCaseConditions(): List<CaseCondition> {
    val conditions = mutableListOf<CaseCondition>()
    do {
        when {
            check(TokenType.IDENTIFIER) && current().value.uppercase() == "IS" -> {
                advance()
                val op = advance().value
                conditions.add(CaseIs(op, parseExpression()))
            }
            else -> {
                val from = parseExpression()
                if (check(TokenType.TO)) {
                    advance()
                    conditions.add(CaseRange(from, parseExpression()))
                } else {
                    conditions.add(CaseValue(from))
                }
            }
        }
    } while (match(TokenType.COMMA))
    return conditions
}

internal fun Parser.parsePrint(): PrintStatement {
    val line = current().line
    val variant = when (advance().type) {
        TokenType.PRINT -> PrintVariant.PLAIN
        TokenType.PRINTL -> PrintVariant.LINE
        TokenType.PRINTW -> PrintVariant.WAIT
        TokenType.PRINTD -> PrintVariant.DATA
        TokenType.PRINTFORM -> PrintVariant.FORM
        TokenType.PRINTFORML -> PrintVariant.FORML
        TokenType.PRINTV -> PrintVariant.VALUE
        TokenType.PRINTVL -> PrintVariant.VALUEL
        TokenType.PRINTS -> PrintVariant.STRING
        TokenType.PRINTSL -> PrintVariant.STRINGL
        TokenType.PRINTC_CMD -> PrintVariant.COLUMN
        TokenType.PRINTLC_CMD -> PrintVariant.COLUMN_LINE
        TokenType.PRINTFORMC_CMD -> PrintVariant.COLUMN_FORM
        TokenType.PRINTFORMLC_CMD -> PrintVariant.COLUMN_FORML
        else -> PrintVariant.PLAIN
    }
    // PRINTS/PRINTSL/PRINTV/PRINTVL은 변수 참조를 표현식으로 파싱
    val args = if (!check(TokenType.NEWLINE) && !check(TokenType.EOF)) {
        if (variant == PrintVariant.STRING || variant == PrintVariant.STRINGL ||
            variant == PrintVariant.VALUE || variant == PrintVariant.VALUEL) {
            // PRINTSL SAVESTR:1 → parseExpressionList로 파싱
            val exprArgs = mutableListOf<Expression>()
            while (!check(TokenType.NEWLINE) && !check(TokenType.EOF)) {
                try { exprArgs.add(parseExpression()) } catch (e: Exception) { break }
                if (check(TokenType.COMMA)) advance() else break
            }
            exprArgs
        } else {
            val sb = StringBuilder()
            var prevEnd = -1  // 이전 토큰 끝 열(inclusive) = column-1
            while (!check(TokenType.NEWLINE) && !check(TokenType.EOF)) {
                val tok = current()
                // token.column = 끝+1, 시작 = column-length, 끝 = column-1
                val tokText = tok.value  // { } [ ] 모두 원문 value 그대로 보존
                val tokenStart = tok.column - tokText.length
                val tokenEnd   = tok.column - 1
                if (prevEnd >= 0 && tokenStart > prevEnd + 1) sb.append(' ')
                sb.append(tokText)
                prevEnd = tokenEnd
                advance()
            }
            val text = sb.toString()
            if (text.isNotEmpty()) listOf(StringLiteral(text, line)) else emptyList()
        }
    } else emptyList()
    skipNewlines()
    return PrintStatement(variant, args, line)
}

internal fun Parser.parseInput(): InputStatement {
    val line = current().line
    val isString = current().type == TokenType.INPUTMOUSEKEY
    advance()
    skipNewlines()
    return InputStatement(null, null, null, null, isString, line)
}

internal fun Parser.parseTInput(): InputStatement {
    val line = current().line
    advance()
    val timeout = parseExpression()
    val default = if (check(TokenType.COMMA)) { advance(); parseExpression() } else null
    skipNewlines()
    return InputStatement(null, default, timeout, null, false, line)
}

internal fun Parser.parseTwait(): TwaitStatement {
    val line = current().line
    advance()
    val time = parseExpression()
    expect(TokenType.COMMA)
    val flag = parseExpression()
    skipNewlines()
    return TwaitStatement(time, flag, line)
}

internal fun Parser.parseButton(): ButtonStatement {
    val line = current().line
    advance()
    val value = try { parseExpression() } catch (e: Exception) {
        skipToNewline(); return ButtonStatement(IntLiteral(0, line), StringLiteral("", line), line)
    }
    if (!check(TokenType.COMMA)) {
        skipToNewline()
        return ButtonStatement(value, StringLiteral("", line), line)
    }
    advance() // comma
    // 줄 끝까지 텍스트로 읽기
    val sb = StringBuilder()
    while (!check(TokenType.NEWLINE) && !check(TokenType.EOF)) {
        sb.append(current().value)
        advance()
    }
    skipNewlines()
    return ButtonStatement(value, StringLiteral(sb.toString().trim(), line), line)
}

internal fun Parser.parseSetColor(): SetColorStatement {
    val line = current().line
    advance()
    val first = parseExpression()
    return if (!check(TokenType.COMMA)) {
        // 색상값 하나로 넘긴 경우 (예: SETCOLOR 0xFF0000)
        skipNewlines()
        // RGB 분리: R=(v>>16)&0xFF, G=(v>>8)&0xFF, B=v&0xFF
        val v = first
        val r = BinaryOp("&", BinaryOp(">>", v, IntLiteral(16, line), line), IntLiteral(0xFF, line), line)
        val g = BinaryOp("&", BinaryOp(">>", first, IntLiteral(8, line), line), IntLiteral(0xFF, line), line)
        val b = BinaryOp("&", first, IntLiteral(0xFF, line), line)
        SetColorStatement(r, g, b, line)
    } else {
        expect(TokenType.COMMA)
        val g = parseExpression(); expect(TokenType.COMMA)
        val b = parseExpression()
        skipNewlines()
        SetColorStatement(first, g, b, line)
    }
}

internal fun Parser.parseSetBgColor(): SetBgColorStatement {
    val line = current().line
    advance()
    val first = parseExpression()
    return if (!check(TokenType.COMMA)) {
        skipNewlines()
        val r = BinaryOp("&", BinaryOp(">>", first, IntLiteral(16, line), line), IntLiteral(0xFF, line), line)
        val g = BinaryOp("&", BinaryOp(">>", first, IntLiteral(8, line), line), IntLiteral(0xFF, line), line)
        val b = BinaryOp("&", first, IntLiteral(0xFF, line), line)
        SetBgColorStatement(r, g, b, line)
    } else {
        expect(TokenType.COMMA); val g = parseExpression(); expect(TokenType.COMMA); val b = parseExpression()
        skipNewlines(); SetBgColorStatement(first, g, b, line)
    }
}

internal fun Parser.parseClearLine(): ClearLineStatement {
    val line = current().line
    advance()
    val count = if (!check(TokenType.NEWLINE)) parseExpression() else null
    skipNewlines()
    return ClearLineStatement(count, line)
}

internal fun Parser.parseCall(isTry: Boolean = false): Statement {
    val line = current().line
    advance()
    val name = try { parseExpression() } catch (e: Exception) {
        skipToNewline(); skipNewlines()
        return CallStatement(StringLiteral("", line), emptyList(), line, isTry)
    }
    val args = if (check(TokenType.COMMA)) {
        advance()
        try { parseExpressionList() } catch (e: Exception) {
            skipToNewline(); emptyList()
        }
    } else emptyList()
    skipNewlines()

    // isTry이고 다음에 CATCH 블록이 있으면: tryBody + catchBody를 CatchStatement로 래핑
    if (isTry && check(TokenType.CATCH)) {
        // tryBody는 없음 (TRYCALL 자체가 call이므로), catchBody만 파싱
        val catchToken = current()
        return parseTryCatchBlock(line, CallStatement(name, args, line, isTry))
    }
    return CallStatement(name, args, line, isTry)
}

internal fun Parser.parseCallForm(isTry: Boolean = false): Statement {
    val line = current().line
    advance()
    val sb = StringBuilder()
    while (!check(TokenType.NEWLINE) && !check(TokenType.EOF)) {
        sb.append(current().value)
        advance()
    }
    skipNewlines()
    val raw = sb.toString().trim()
    val commaIdx = raw.indexOf(',')
    val callStmt = if (commaIdx >= 0) {
        val rawName = raw.substring(0, commaIdx).trim()
        val rawArgs = raw.substring(commaIdx + 1).trim()
        val argTokens = try {
            com.eraandroid.core.lexer.Lexer(rawArgs).tokenize()
                .filter { it.type != com.eraandroid.core.lexer.TokenType.COMMENT && it.type != com.eraandroid.core.lexer.TokenType.EOF }
        } catch (e: Exception) { emptyList() }
        val args = if (argTokens.isNotEmpty()) {
            try {
                Parser(argTokens, fileName).parseExpressionList()
            } catch (e: Exception) { emptyList() }
        } else emptyList()
        CallStatement(StringLiteral(rawName, line), args, line, isTry)
    } else {
        CallStatement(StringLiteral(raw, line), emptyList(), line, isTry)
    }

    // isTry이고 다음에 CATCH 블록이 있으면 tryBody + catchBody를 CatchStatement로 래핑
    if (isTry && check(TokenType.CATCH)) {
        return parseTryCatchBlock(line, callStmt)
    }
    return callStmt
}

/**
 * TRYCALL/TRYCCALL 다음에 CATCH...ENDCATCH 블록이 있을 때 파싱.
 * emuera 동작:
 *   TRYCCALL funcname
 *       [tryBody: funcname 성공 시 실행]
 *   CATCH
 *       [catchBody: funcname 실패 시 실행]
 *   ENDCATCH
 *
 * → CatchStatement(tryBody=[callStmt]+tryBodyStmts, catchBody=[...])
 * 실행 시: tryBody를 executeStatements로 실행,
 *          callStmt(isTry)가 TryCatchSignal을 던지면 catchBody로 점프
 */
internal fun Parser.parseTryCatchBlock(line: Int, callStmt: CallStatement): CatchStatement {
    // CATCH 전까지의 statements를 tryBody로 파싱
    val tryBody = mutableListOf<Statement>(callStmt)
    val bodyStmts = parseBody { it.type == TokenType.CATCH || it.type == TokenType.ENDCATCH || it.type == TokenType.FUNCTION_LABEL }
    tryBody.addAll(bodyStmts)

    // CATCH 토큰 소비
    val catchBody = if (check(TokenType.CATCH)) {
        advance()
        skipNewlines()
        val body = parseBody { it.type == TokenType.ENDCATCH || it.type == TokenType.FUNCTION_LABEL }
        if (check(TokenType.ENDCATCH)) advance()
        skipNewlines()
        body
    } else emptyList()

    return CatchStatement(tryBody, catchBody, line)
}


internal fun Parser.parsePrintButton(isInline: Boolean = false): ButtonStatement {
    val line = current().line
    advance()
    val value = try { parseExpression() } catch (e: Exception) { skipToNewline(); return ButtonStatement(IntLiteral(0, line), StringLiteral("", line), line, isInline) }
    if (!check(TokenType.COMMA)) { skipToNewline(); return ButtonStatement(value, StringLiteral("", line), line, isInline) }
    advance()
    val sb = StringBuilder()
    while (!check(TokenType.NEWLINE) && !check(TokenType.EOF)) { sb.append(current().value); advance() }
    skipNewlines()
    return ButtonStatement(value, StringLiteral(sb.toString().trim(), line), line, isInline)
}

internal fun Parser.parseArrayShift(): Statement {
    val line = current().line; advance()
    val variable = parseExpression()
    val shift  = if (check(TokenType.COMMA)) { advance(); parseExpression() } else IntLiteral(0, line)
    val value  = if (check(TokenType.COMMA)) { advance(); parseExpression() } else IntLiteral(0, line)
    val start  = if (check(TokenType.COMMA)) { advance(); parseExpression() } else null
    val count  = if (check(TokenType.COMMA)) { advance(); parseExpression() } else null
    skipNewlines()
    return CallStatement(StringLiteral("__ARRAYSHIFT__", line), listOfNotNull(variable, shift, value, start, count), line, false)
}

internal fun Parser.parseArrayRemove(): Statement {
    val line = current().line; advance()
    val variable = parseExpression()
    val start  = if (check(TokenType.COMMA)) { advance(); parseExpression() } else IntLiteral(0, line)
    val count  = if (check(TokenType.COMMA)) { advance(); parseExpression() } else null
    skipNewlines()
    return CallStatement(StringLiteral("__ARRAYREMOVE__", line), listOfNotNull(variable, start, count), line, false)
}

internal fun Parser.parseReturn(): ReturnStatement {
    val line = current().line
    advance()
    if (check(TokenType.NEWLINE) || check(TokenType.EOF)) { skipNewlines(); return ReturnStatement(null, line = line) }
    val values = mutableListOf<Expression>()
    try {
        values.add(parseExpression())
        while (check(TokenType.COMMA)) { advance(); values.add(parseExpression()) }
    } catch (e: Exception) { skipToNewline() }
    skipNewlines()
    return ReturnStatement(
        value = values.getOrNull(0),
        multiValues = if (values.size > 1) values else emptyList(),
        line = line
    )
}

internal fun Parser.parseCvarSet(): Statement {
    // CVARSET VARNAME, INDEX, VALUE  → 모든 캐릭터의 해당 변수/인덱스에 값 설정
    val line = current().line
    advance()
    val args = mutableListOf<Expression>()
    // 첫 인수: 변수명(IDENTIFIER로 읽힘)
    args.add(parseExpression())
    if (check(TokenType.COMMA)) { advance(); args.add(parseExpression()) }
    if (check(TokenType.COMMA)) { advance(); args.add(parseExpression()) }
    if (check(TokenType.COMMA)) { advance(); args.add(parseExpression()) }
    skipNewlines()
    return CallStatement(VariableRef("__CVARSET__", emptyList(), line), args, line, false)
}

internal fun Parser.parseVarSet(): VarSetStatement {
    // PC 스펙: VARSET var, value, start, end(절대인덱스)
    val line = current().line
    advance()
    val variable = parseExpression()
    val value = if (check(TokenType.COMMA)) { advance(); parseExpression() } else null
    val start = if (check(TokenType.COMMA)) { advance(); parseExpression() } else null
    val endExpr = if (check(TokenType.COMMA)) { advance(); parseExpression() } else null
    skipNewlines()
    // count = end - start
    val count = if (endExpr != null && start != null) BinaryOp("-", endExpr, start, line)
    else if (endExpr != null) endExpr
    else null
    return VarSetStatement(variable, value, start, count, line)
}

internal fun Parser.parseSaveData(): SaveDataStatement {
    val line = current().line
    advance()
    val slot = parseExpression()
    val comment = if (check(TokenType.COMMA)) { advance(); parseExpression() } else null
    skipNewlines()
    return SaveDataStatement(slot, comment, line)
}

internal fun Parser.parseSortChara(): SortCharaStatement {
    val line = current().line
    advance()
    if (check(TokenType.NEWLINE) || check(TokenType.EOF)) { skipNewlines(); return SortCharaStatement(VariableRef("NO", emptyList(), line), true, line) }
    val firstVal = current().value.uppercase()
    if (firstVal == "FORWARD" || firstVal == "BACK") {
        val asc = firstVal == "FORWARD"; advance(); skipNewlines()
        return SortCharaStatement(VariableRef("NO", emptyList(), line), asc, line)
    }
    val variable = parseExpression()
    val ascending = if (check(TokenType.COMMA)) {
        advance()
        val v = current().value.uppercase()
        when {
            v == "BACK"    -> { advance(); false }
            v == "FORWARD" -> { advance(); true }
            else -> { try { parseExpression().let { (it as? IntLiteral)?.value != 0L } } catch (e: Exception) { true } }
        }
    } else true
    skipNewlines()
    return SortCharaStatement(variable, ascending, line)
}

internal fun Parser.parseBegin(): BeginStatement {
    val line = current().line
    advance()
    val name = advance().value
    skipNewlines()
    return BeginStatement(name, line)
}

// ── 새로 추가된 파싱 함수들 ──────────────────────────────────────────────────

internal fun Parser.parseInputs(): InputsStatement {
    val line = current().line
    advance()
    skipNewlines()
    return InputsStatement(null, line)
}

internal fun Parser.parsePrintData(startToken: Token): PrintDataStatement {
    val line = startToken.line
    val variant = when (startToken.type) {
        TokenType.PRINTDATAL -> PrintVariant.LINE
        TokenType.PRINTDATAW -> PrintVariant.WAIT
        else -> PrintVariant.PLAIN
    }
    advance()
    // PRINTDATAW LOCAL:1 등 인수 토큰을 줄바꿈 전까지 모두 소비
    while (!check(TokenType.NEWLINE) && !check(TokenType.EOF)) advance()
    skipNewlines()
    val blocks = mutableListOf<List<Expression>>()
    while (!check(TokenType.ENDDATA) && !check(TokenType.FUNCTION_LABEL) && !check(TokenType.EOF)) {
        when (current().type) {
            TokenType.DATALIST, TokenType.DATAFORM -> {
                advance(); skipNewlines()
                val items = mutableListOf<Expression>()
                while (!check(TokenType.ENDLIST) && !check(TokenType.DATALIST)
                    && !check(TokenType.DATAFORM) && !check(TokenType.ENDDATA)
                    && !check(TokenType.FUNCTION_LABEL) && !check(TokenType.EOF)) {
                    val sb = StringBuilder()
                    while (!check(TokenType.NEWLINE) && !check(TokenType.EOF)) { sb.append(current().value); advance() }
                    val text = sb.toString().trim()
                    if (text.isNotEmpty()) items.add(StringLiteral(text, line))
                    skipNewlines()
                }
                if (check(TokenType.ENDLIST)) advance()
                blocks.add(items)
            }
            TokenType.NEWLINE, TokenType.COMMENT -> advance()
            else -> { skipToNewline(); skipNewlines() }
        }
    }
    if (check(TokenType.ENDDATA)) advance()
    skipNewlines()
    return PrintDataStatement(variant, blocks, line)
}

internal fun Parser.parseCatch(startToken: Token): CatchStatement {
    val line = startToken.line
    advance()
    skipNewlines()
    val catchBody = parseBody { it.type == TokenType.ENDCATCH || it.type == TokenType.FUNCTION_LABEL }
    if (check(TokenType.ENDCATCH)) advance()
    skipNewlines()
    return CatchStatement(emptyList(), catchBody, line)
}

internal fun Parser.parseFindChara(): FindCharaStatement {
    val line = current().line
    advance()
    val variable = parseExpression()
    expect(TokenType.COMMA)
    val target = parseExpression()
    val index = if (check(TokenType.COMMA)) { advance(); parseExpression() } else null
    skipNewlines()
    return FindCharaStatement(variable, target, index, line)
}

internal fun Parser.parseGetLv(isPalam: Boolean): Statement {
    val line = current().line
    advance()
    // GETPALAMLV varRef, lvNum  (2인수)
    // GETEXPLV varRef, lvNum    (2인수)
    // result는 RESULT 변수에 자동 저장됨
    val varRef = parseExpression()
    val lvNum = if (check(TokenType.COMMA)) { advance(); parseExpression() } else IntLiteral(0, line)
    skipNewlines()
    return if (isPalam) GetPalamLvStatement(varRef, IntLiteral(0, line), lvNum, line)
    else GetExpLvStatement(varRef, IntLiteral(0, line), lvNum, line)
}

internal fun Parser.parseBar(): BarStatement {
    val line = current().line
    advance()
    val value = parseExpression()
    expect(TokenType.COMMA)
    val max = parseExpression()
    expect(TokenType.COMMA)
    val length = parseExpression()
    skipNewlines()
    return BarStatement(value, max, length, line)
}

internal fun Parser.parseSplitCmd(): SplitCmdStatement {
    val line = current().line
    advance()
    val str = parseExpression()
    expect(TokenType.COMMA)
    val delim = parseExpression()
    expect(TokenType.COMMA)
    val target = parseExpression()
    skipNewlines()
    return SplitCmdStatement(str, delim, target, line)
}

internal fun Parser.parseJump(isTry: Boolean): JumpStatement {
    val line = current().line
    advance()
    val name = try { parseExpression() } catch (e: Exception) {
        skipToNewline(); skipNewlines()
        return JumpStatement(StringLiteral("", line), emptyList(), isTry, line)
    }
    val args = if (check(TokenType.COMMA)) { advance(); try { parseExpressionList() } catch (e: Exception) { emptyList() } } else emptyList()
    skipNewlines()
    return JumpStatement(name, args, isTry, line)
}

internal fun Parser.parseJumpForm(isTry: Boolean): JumpFormStatement {
    val line = current().line
    advance()
    val sb = StringBuilder()
    while (!check(TokenType.NEWLINE) && !check(TokenType.EOF)) { sb.append(current().value); advance() }
    skipNewlines()
    return JumpFormStatement(sb.toString().trim(), isTry, line)
}

internal fun Parser.parseDim(): DimStatement {
    val line = current().line
    val isString = current().type == TokenType.SHARP_DIMS
    advance()
    val isConst = check(TokenType.SHARP_CONST).also { if (it) advance() }
    val name = expect(TokenType.IDENTIFIER).value
    val sizes = mutableListOf<Expression>()
    if (check(TokenType.COMMA)) {
        advance()
        sizes.add(parseExpression())
        while (check(TokenType.COMMA)) { advance(); sizes.add(parseExpression()) }
    }
    val init = if (check(TokenType.ASSIGN)) { advance(); parseExpression() } else null
    skipNewlines()
    return DimStatement(name, isString, sizes, isConst, init, line)
}

internal fun Parser.parseDefine(): DefineStatement {
    val line = current().line
    advance()
    val name = expect(TokenType.IDENTIFIER).value
    val value = buildString { while (!check(TokenType.NEWLINE) && !check(TokenType.EOF)) append(advance().value) }
    skipNewlines()
    return DefineStatement(name, value.trim(), line)
}

internal fun Parser.parseIdentifierStatement(): Statement? {
    val line = current().line
    val expr = parsePrimary()

    return when {
        check(TokenType.INCREMENT) -> { advance(); skipNewlines(); IncrementStatement(expr, line) }
        check(TokenType.DECREMENT) -> { advance(); skipNewlines(); DecrementStatement(expr, line) }
        current().type in listOf(
            TokenType.ASSIGN, TokenType.PLUS_ASSIGN, TokenType.MINUS_ASSIGN,
            TokenType.MULTIPLY_ASSIGN, TokenType.DIVIDE_ASSIGN, TokenType.MODULO_ASSIGN
        ) -> {
            val op = advance().value
            val raw = peekRestOfLine()
            val percentCount = raw.count { it == '%' }
            val hasFormSyntax = raw.contains("{") || (raw.contains("%") && percentCount % 2 == 0) || raw.contains("\\@")
            // 우변이 순수 텍스트 문자열인지 토큰 타입으로 판별:
            // - form 문자열({..}, %..%) → hasFormSyntax=true → raw 전체를 StringLiteral로
            // - 첫 토큰이 ID/INT이고 두 번째가 공백 후 ID/INT이면 → 순수 텍스트
            //   예: RESULTS:1 = eratohoYM 데뷔, RESULTS:2 = 10일 이내에 클리어
            // - INT+ID 붙음(10일)이더라도 세 번째 토큰이 공백 후 ID이면 텍스트
            // - 그 외(연산자, 함수 호출, 단일 토큰 등) → parseExpression()
            val textTokenTypes = listOf(
                TokenType.IDENTIFIER, TokenType.INTEGER, TokenType.FLOAT, TokenType.STRING
            )
            val opTokenTypes = listOf(
                TokenType.ASSIGN, TokenType.PLUS_ASSIGN, TokenType.MINUS_ASSIGN,
                TokenType.MULTIPLY_ASSIGN, TokenType.DIVIDE_ASSIGN, TokenType.MODULO_ASSIGN,
                TokenType.EQ, TokenType.NEQ, TokenType.LT, TokenType.LTE, TokenType.GT, TokenType.GTE,
                TokenType.AND, TokenType.OR, TokenType.BITAND, TokenType.BITOR, TokenType.BITXOR,
                TokenType.LSHIFT, TokenType.RSHIFT,
                TokenType.PLUS, TokenType.MINUS, TokenType.MULTIPLY, TokenType.DIVIDE, TokenType.MODULO,
                TokenType.LPAREN, TokenType.LBRACKET,
                TokenType.TERNARY_IF, TokenType.TERNARY_ELSE,
                TokenType.INCREMENT, TokenType.DECREMENT,
                TokenType.COMMA, TokenType.COLON,
                TokenType.SHARP_DEFINE
            )
            val ft = current()
            val st = peek(1)
            val tt = peek(2)
            // token.column = 끝+1, 시작 = column-length, 끝 = column-1
            val ftEnd   = ft.column - 1
            val stStart = st.column - st.value.length
            val stEnd   = st.column - 1
            val ttStart = tt.column - tt.value.length
            val hasGap12 = stStart > ftEnd + 1   // ft와 st 사이 공백
            val hasGap23 = ttStart > stEnd + 1   // st와 tt 사이 공백
            val isRawString = !hasFormSyntax && when {
                // 첫 토큰이 연산자/괄호/특수 → 표현식
                ft.type in opTokenTypes || ft.type == TokenType.NEWLINE || ft.type == TokenType.EOF -> false
                // 두 번째 토큰이 없거나 줄 끝 → 단일 토큰 → 표현식
                st.type == TokenType.NEWLINE || st.type == TokenType.EOF -> false
                // ft·st 사이 공백 있고, st가 연산자/괄호/콜론이 아닌 경우 → 텍스트
                hasGap12 && st.type !in opTokenTypes -> true
                // ft와 st가 붙어있고(INT+ID 등), tt가 공백 후 ID/INT → 텍스트
                !hasGap12 && ft.type in textTokenTypes && st.type == TokenType.IDENTIFIER &&
                        tt.type != TokenType.NEWLINE && tt.type != TokenType.EOF &&
                        tt.type !in opTokenTypes && hasGap23 -> true
                else -> false
            }
            val value = when {
                raw.trim().isEmpty() -> StringLiteral("", line)
                hasFormSyntax || isRawString -> {
                    skipToNewline()
                    StringLiteral(raw.trim(), line)
                }
                else -> parseExpression()
            }
            skipNewlines()
            AssignStatement(expr, op, value, line)
        }
        // |= &= ^= 처리
        current().type == TokenType.BITOR && peek().type == TokenType.ASSIGN -> {
            advance(); advance()
            val value = parseExpression()
            skipNewlines()
            AssignStatement(expr, "|=", value, line)
        }
        current().type == TokenType.BITAND && peek().type == TokenType.ASSIGN -> {
            advance(); advance()
            val value = parseExpression()
            skipNewlines()
            AssignStatement(expr, "&=", value, line)
        }
        current().type == TokenType.BITXOR && peek().type == TokenType.ASSIGN -> {
            advance(); advance()
            val value = parseExpression()
            skipNewlines()
            AssignStatement(expr, "^=", value, line)
        }
        // VariableRef(인덱스 없음) 뒤에 줄 끝이 아닌 토큰이 남아있으면
        // "CSVNICKNAME NO:TARGET , 0" 같은 공백+인수 형태의 명령문으로 처리
        expr is VariableRef && expr.indices.isEmpty() &&
                !check(TokenType.NEWLINE) && !check(TokenType.EOF) -> {
            val args = mutableListOf<Expression>()
            while (!check(TokenType.NEWLINE) && !check(TokenType.EOF)) {
                try { args.add(parseExpression()) } catch (e: Exception) { break }
                if (check(TokenType.COMMA)) advance() else break
            }
            skipNewlines()
            CallStatement(StringLiteral(expr.name, line), args, line, false)
        }
        else -> { skipNewlines(); ExpressionStatement(expr, line) }
    }
}

// ─── Expression Parsing (Pratt / Precedence Climbing) ────────────────────