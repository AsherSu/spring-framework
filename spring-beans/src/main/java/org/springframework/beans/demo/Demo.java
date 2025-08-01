package org.springframework.beans.demo;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.ChildBeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

public class Demo {
	public static void main(String[] args) {
		/* ---------- 1. 搭好父子 BeanFactory ---------- */
		DefaultListableBeanFactory parentFactory = new DefaultListableBeanFactory();
		DefaultListableBeanFactory childFactory  = new DefaultListableBeanFactory(parentFactory);

		/* ---------- 2. 在父容器注册 “父 BeanDefinition” ---------- */
		RootBeanDefinition parentDef = new RootBeanDefinition(ExampleBean.class);
		parentDef.getPropertyValues().add("name", "parent-name");
		parentFactory.registerBeanDefinition("exampleBean", parentDef);

		/* ---------- 3. 在子容器注册 “子 BeanDefinition”，声明继承 ---------- */
		ChildBeanDefinition childDef = new ChildBeanDefinition("exampleBean"); // 指定 parentName
		childDef.getPropertyValues().add("age", 18);                          // 只覆盖差异字段
		childFactory.registerBeanDefinition("exampleBean", childDef);

		/* ---------- 4. 触发 getMergedBeanDefinition ---------- */
		BeanDefinition merged = childFactory.getMergedBeanDefinition("exampleBean");

		/* ---------- 5. 打印结果，验证父子属性已合并 ---------- */
		System.out.println("====== Merged PropertyValues ======");
		merged.getPropertyValues().forEach(pv ->
				System.out.println(pv.getName() + " = " + pv.getValue()));
	}

	class ExampleBean{
		private String name;
		private int age;

		public void setName(String name) {
			this.name = name;
		}

		public void setAge(int age) {
			this.age = age;
		}

		@Override
		public String toString() {
			return "ExampleBean{name='" + name + "', age=" + age + '}';
		}
	}
}
