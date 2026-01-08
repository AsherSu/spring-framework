package org.springframework.spring_reading;

import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;

import java.io.IOException;
import java.util.Arrays;


// org.springframework.core.type.classreading.MetadataReader 接口是 Spring 框架中用于读取和解析类文件元数据的核心接口之一。
// 它的主要作用是允许应用程序获取有关类的元数据信息，包括类的名称、访问修饰符、接口、超类、注解等等。这些元数据信息可以在运行时用于实现
// 各种高级功能，例如组件扫描、条件化注解处理、AOP（面向切面编程）等。
public class MetadataReaderDemo {
	public static void main(String[] args) throws IOException {

		// 创建 MetadataReaderFactory
		SimpleMetadataReaderFactory readerFactory = new SimpleMetadataReaderFactory();
		// 获取 MetadataReader
		MetadataReader metadataReader = readerFactory.getMetadataReader("org.springframework.spring_reading.My.MyBean");

		// 获取类的基本信息
		ClassMetadata classMetadata = metadataReader.getClassMetadata();
		System.out.println("Class Name = " + classMetadata.getClassName());
		System.out.println("Class IsInterface = " + classMetadata.isInterface());
		System.out.println("Class IsAnnotation = " + classMetadata.isAnnotation());
		System.out.println("Class IsAbstract = " + classMetadata.isAbstract());
		System.out.println("Class IsConcrete = " + classMetadata.isConcrete());
		System.out.println("Class IsFinal = " + classMetadata.isFinal());
		System.out.println("Class IsIndependent = " + classMetadata.isIndependent());
		System.out.println("Class HasEnclosingClass = " + classMetadata.hasEnclosingClass());
		System.out.println("Class EnclosingClassName = " + classMetadata.getEnclosingClassName());
		System.out.println("Class HasSuperClass = " + classMetadata.hasSuperClass());
		System.out.println("Class SuperClassName = " + classMetadata.getSuperClassName());
		System.out.println("Class InterfaceNames = " + Arrays.toString(classMetadata.getInterfaceNames()));
		System.out.println("Class MemberClassNames = " + Arrays.toString(classMetadata.getMemberClassNames()));
		System.out.println("Class Annotations: " +  metadataReader.getAnnotationMetadata().getAnnotationTypes());

		System.out.println();

		// 获取方法上的注解信息
		for (MethodMetadata methodMetadata : metadataReader.getAnnotationMetadata().getAnnotatedMethods("com.xcs.spring.annotation.MyAnnotation")) {
			System.out.println("Method Name: " + methodMetadata.getMethodName());
			System.out.println("Method DeclaringClassName: " + methodMetadata.getDeclaringClassName());
			System.out.println("Method ReturnTypeName: " + methodMetadata.getReturnTypeName());
			System.out.println("Method IsAbstract: " + methodMetadata.isAbstract());
			System.out.println("Method IsStatic: " + methodMetadata.isStatic());
			System.out.println("Method IsFinal: " + methodMetadata.isFinal());
			System.out.println("Method IsOverridable: " + methodMetadata.isOverridable());
			System.out.println();
		}
	}
}