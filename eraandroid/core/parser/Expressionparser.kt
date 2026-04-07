package com.eraandroid.core.parser

import com.eraandroid.core.lexer.Token
import com.eraandroid.core.lexer.TokenType

// ─── 식(Expression) 파싱 (Parser extension) ──────────────────────────────────

internal fun Parser.parseTernary(): Expression {
    val left = parseOr()
    if (!check(TokenType.TERNARY_IF)) return left
    val line = current().line
    advance()
    val trueBranch = parseTernary()
    // # 또는 Sharp 계열 토큰 모두 허용
    if (!check(TokenType.TERNARY_ELSE) && !check(TokenType.SHARP_DEFINE) &&
        !check(TokenType.SHARP_DIM) && current().value != "#") {
        return trueBranch
    }
    advance()
    val falseBranch = parseTernary()
    return TernaryOp(left, trueBranch, falseBranch, line)
}

internal fun Parser.parseOr(): Expression = parseBinaryLeft(::parseAnd, TokenType.OR)
internal fun Parser.parseAnd(): Expression = parseBinaryLeft(::parseBitOr, TokenType.AND)
internal fun Parser.parseBitOr(): Expression = parseBinaryLeft(::parseBitXor, TokenType.BITOR)
internal fun Parser.parseBitXor(): Expression = parseBinaryLeft(::parseBitAnd, TokenType.BITXOR)
internal fun Parser.parseBitAnd(): Expression = parseBinaryLeft(::parseEquality, TokenType.BITAND)
internal fun Parser.parseEquality(): Expression = parseBinaryLeft(::parseComparison, TokenType.EQ, TokenType.NEQ)
internal fun Parser.parseComparison(): Expression = parseBinaryLeft(::parseShift, TokenType.LT, TokenType.LTE, TokenType.GT, TokenType.GTE)
internal fun Parser.parseShift(): Expression = parseBinaryLeft(::parseAddSub, TokenType.LSHIFT, TokenType.RSHIFT)
internal fun Parser.parseAddSub(): Expression = parseBinaryLeft(::parseMulDiv, TokenType.PLUS, TokenType.MINUS)
internal fun Parser.parseMulDiv(): Expression = parseBinaryLeft(::parsePower, TokenType.MULTIPLY, TokenType.DIVIDE, TokenType.MODULO)

internal fun Parser.parsePower(): Expression = parseUnary()  // ^ = BITXOR, POWER는 함수로 처리

internal fun Parser.parseBinaryLeft(next: () -> Expression, vararg ops: TokenType): Expression {
    var left = next()
    while (current().type in ops) {
        val line = current().line
        val op = advance().value
        left = BinaryOp(op, left, next(), line)
    }
    return left
}

internal fun Parser.parseUnary(): Expression {
    val line = current().line
    return when (current().type) {
        TokenType.MINUS -> { advance(); UnaryOp("-", parseUnary(), line) }
        TokenType.NOT -> { advance(); UnaryOp("!", parseUnary(), line) }
        TokenType.BITNOT -> { advance(); UnaryOp("~", parseUnary(), line) }
        TokenType.INCREMENT -> { advance(); UnaryOp("++pre", parsePrimary(), line) }
        TokenType.DECREMENT -> { advance(); UnaryOp("--pre", parsePrimary(), line) }
        else -> parsePostfix()
    }
}

internal fun Parser.parsePostfix(): Expression {
    var expr = parsePrimary()
    val line = current().line
    while (true) {
        expr = when {
            check(TokenType.INCREMENT) -> { advance(); UnaryOp("++post", expr, line) }
            check(TokenType.DECREMENT) -> { advance(); UnaryOp("--post", expr, line) }
            else -> break
        }
    }
    return expr
}

internal fun Parser.parsePrimary(): Expression {
    val token = current()
    return when (token.type) {
        TokenType.INTEGER -> { advance(); IntLiteral(token.value.toLongOrNull() ?: 0L, token.line) }
        TokenType.FLOAT -> { advance(); FloatLiteral(token.value.toDoubleOrNull() ?: 0.0, token.line) }
        TokenType.STRING -> { advance(); StringLiteral(token.value, token.line) }
        TokenType.LPAREN -> {
            advance()
            parseExpression().also {
                if (check(TokenType.RPAREN)) advance() // RPAREN 없어도 에러 안 냄
            }
        }
        TokenType.IDENTIFIER -> parseIdentifierOrCall()
        // FUNCTION_LABEL이 표현식 위치에 나타날 경우 (파싱 에러 방지)
        TokenType.FUNCTION_LABEL -> {
            val name = advance().value.removePrefix("@")
            StringLiteral(name, token.line)
        }
        // Built-in functions
        TokenType.ABS, TokenType.SQRT, TokenType.SIGN, TokenType.MAX, TokenType.MIN,
        TokenType.LIMIT, TokenType.INRANGE, TokenType.TOINT, TokenType.TOSTR,
        TokenType.ISNUMERIC, TokenType.STRFIND, TokenType.STRLENS, TokenType.SUBSTRING,
        TokenType.CHARATU, TokenType.STRJOIN, TokenType.GETTIME -> parseBuiltinFunction()
        else -> { advance(); StringLiteral("", token.line) }
    }
}

internal fun Parser.parseIdentifierOrCall(): Expression {
    val name = advance().value
    val line = current().line
    val indices = mutableListOf<Expression>()

    if (check(TokenType.LPAREN)) {
        advance()
        val args = mutableListOf<Expression>()
        while (!check(TokenType.RPAREN) && !check(TokenType.NEWLINE) && !check(TokenType.EOF)) {
            val before = pos
            try { args.add(parseExpression()) } catch (e: Exception) { }
            when {
                check(TokenType.COMMA) -> advance()
                pos == before -> advance()
                else -> break
            }
        }
        if (check(TokenType.RPAREN)) advance()
        return FunctionCall(name.uppercase(), args, line)
    } else if (check(TokenType.COLON)) {
        advance()
        try {
            indices.add(parseIndexExpr())
            while (check(TokenType.COLON)) { advance(); indices.add(parseIndexExpr()) }
        } catch (e: Exception) { /* ignore */ }
    }

    return VariableRef(name, indices, line)
}

/**
 * 인덱스 표현식: VAR:IDX1:IDX2 에서 IDX1, IDX2 각각을 파싱
 * parsePrimary()와 달리, IDENTIFIER를 만나도 COLON을 소비하지 않음
 * 즉 MASTER는 VariableRef("MASTER", [])로 파싱됨 (MASTER:1 로 파싱하지 않음)
 */
internal fun Parser.parseIndexExpr(): Expression {
    val token = current()
    return when (token.type) {
        TokenType.INTEGER  -> { advance(); IntLiteral(token.value.toLongOrNull() ?: 0L, token.line) }
        TokenType.FLOAT    -> { advance(); FloatLiteral(token.value.toDoubleOrNull() ?: 0.0, token.line) }
        TokenType.STRING   -> { advance(); StringLiteral(token.value, token.line) }
        TokenType.LPAREN   -> {
            advance()
            val expr = parseExpression()
            if (check(TokenType.RPAREN)) advance()
            expr
        }
        TokenType.MINUS    -> { advance(); UnaryOp("-", parseIndexExpr(), token.line) }
        TokenType.IDENTIFIER -> {
            // 이름만 읽고 : 는 소비하지 않음 → VariableRef(name, [])
            val idxName = advance().value
            val idxLine = current().line
            // 함수 호출 형태인 경우만 괄호 처리
            if (check(TokenType.LPAREN)) {
                advance()
                val args = mutableListOf<Expression>()
                while (!check(TokenType.RPAREN) && !check(TokenType.NEWLINE) && !check(TokenType.EOF)) {
                    try { args.add(parseExpression()) } catch (e: Exception) { break }
                    if (check(TokenType.COMMA)) advance() else break
                }
                if (check(TokenType.RPAREN)) advance()
                FunctionCall(idxName.uppercase(), args, idxLine)
            } else {
                VariableRef(idxName, emptyList(), idxLine)
            }
        }
        else -> parsePrimary()
    }
}

internal fun Parser.parseBuiltinFunction(): Expression {
    val name = advance().value.uppercase()
    val line = current().line
    val args = if (check(TokenType.LPAREN)) {
        advance()
        val list = if (!check(TokenType.RPAREN)) parseExpressionList() else emptyList()
        // RPAREN 없어도 그냥 넘어감
        if (check(TokenType.RPAREN)) advance()
        list
    } else parseExpressionList()
    return FunctionCall(name, args, line)
}