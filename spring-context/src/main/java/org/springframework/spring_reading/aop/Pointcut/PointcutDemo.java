package org.springframework.spring_reading.aop.Pointcut;

import org.springframework.aop.ClassFilter;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;

import java.lang.reflect.Method;

public class PointcutDemo {
	public static void main(String[] args) {
		customPointcut();
	}

	/**
	 * 自定义 Pointcut
	 */
	private static void customPointcut() {
		MyCustomPointcut pointcut = new MyCustomPointcut();
		showMatchesLog(pointcut);
	}

	public static void showMatchesLog(Pointcut pointcut) {
		try {
			Class<MyService> target = MyService.class;
			Method getNameMethod = target.getDeclaredMethod("getName");
			Method getAgeMethod = target.getDeclaredMethod("getAge");
			Method setNameMethod = target.getDeclaredMethod("setName");

			ClassFilter classFilter = pointcut.getClassFilter();
			MethodMatcher methodMatcher = pointcut.getMethodMatcher();

			System.out.println("ClassFilter MyService = " + classFilter.matches(target));
			System.out.println("MethodMatcher MyService getName = " + methodMatcher.matches(getNameMethod, target));
			System.out.println("MethodMatcher MyService getAge = " + methodMatcher.matches(getAgeMethod, target));
			System.out.println("MethodMatcher MyService setName = " + methodMatcher.matches(setNameMethod, target));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
