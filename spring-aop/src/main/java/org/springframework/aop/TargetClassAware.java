/*
 * Copyright 2002-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.aop;

import org.jspecify.annotations.Nullable;

/**
 * [通俗定义]：这是一个“身份透视”接口。
 * 它的存在只有一个目的：让外界能透过厚厚的代理（Proxy）外壳，看到里面包裹的真实业务类（Target Class）是谁。
 *
 * <p>哪些家伙会佩戴这个“身份牌”？
 * 1. AOP 代理对象本身（就是你平时 @Autowired 拿到的那个 Bean）。
 * 2. 代理工厂（ProxyFactory，制造代理的机器）。
 * 3. {@link TargetSource TargetSources}（负责从后厨把真实对象端出来的供应商）。
 *
 * (通过实现这个接口，Spring 内部工具就能轻易判断：这个 Bean 是不是代理？它原来是 UserServiceImpl 还是 OrderServiceImpl？)
 *
 * @author Juergen Hoeller
 * @since 2.0.3
 * @see org.springframework.aop.support.AopUtils#getTargetClass(Object)
 */
public interface TargetClassAware {

	/**
	 * [核心方法]：亮出你的真实身份！
	 *
	 * 作用：返回在这个代理对象（Proxy）背后隐藏的、真正的业务类的 Class 对象。
	 *
	 * @return 返回目标类（例如 UserServiceImpl.class）。
	 * 注意：如果仅仅是一个单纯的代理配置，还没确定具体的业务目标，或者实在找不到了，这里可能会返回 {@code null}。
	 */
	@Nullable Class<?> getTargetClass();

}
