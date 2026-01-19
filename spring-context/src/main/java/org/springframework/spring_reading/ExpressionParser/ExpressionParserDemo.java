package org.springframework.spring_reading.ExpressionParser;

import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

public class ExpressionParserDemo {

	public static void main(String[] args) {
		// 创建解析器实例
		ExpressionParser parser = new SpelExpressionParser();
		// 解析基本表达式
		Expression expression = parser.parseExpression("100 * 2 + 10");

		System.out.println("expression = " + expression.getValue());
	}
}
