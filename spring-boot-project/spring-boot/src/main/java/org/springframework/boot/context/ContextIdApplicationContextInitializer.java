/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.context;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 设置Spring应用上下文ID，也就是通过ApplicationContext#getId()去获取的那个属性，如果属性
 * "spring.application.name"有设置，则使用它作为应用上下文id，否则使用字符串"application"。
 */
public class ContextIdApplicationContextInitializer implements
		ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {

	//设置顺序
	private int order = Ordered.LOWEST_PRECEDENCE - 10;

	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	@Override
	public void initialize(ConfigurableApplicationContext applicationContext) {
		// 构建针对当前应用上下文的ContextId对象：可能基于双亲应用上下文创建或者直接创建
		ContextId contextId = getContextId(applicationContext);
		// 设置当前应用上下文的id
		applicationContext.setId(contextId.getId());
		// 将上面生成的ContextId对象，作为一个单例bean注册到当前应用上下文，
		// 从下面的代码可以看到，这样做的用途之一就是万一当前应用上下文有子应用上下文，
		// 该bean可以用于创建子应用上下文的ContextId对象
		applicationContext.getBeanFactory().registerSingleton(ContextId.class.getName(),
				contextId);
	}

	// 构建针对当前应用上下文的ContextId对象
	private ContextId getContextId(ConfigurableApplicationContext applicationContext) {
		ApplicationContext parent = applicationContext.getParent();
		if (parent != null && parent.containsBean(ContextId.class.getName())) {
			// 如果当前应用上下文有双亲上下文，并且双亲上下文已经存在自己的ContextId bean，
			// 现在使用双亲上下文的ContextId bean生成当前应用上下文的ContextId对象
			return parent.getBean(ContextId.class).createChildId();
		}
		// 如果当前应用上下文没有双亲应用上下文或者双亲应用上下文没有自己的ContextId bean，
		// 则直接创建当前应用上下文的ContextId对象
		return new ContextId(getApplicationId(applicationContext.getEnvironment()));
	}
	// 决定应用上下文id的名称：或者使用环境属性"spring.application.name"指定的值，或者使用
	// 缺省值"application"
	private String getApplicationId(ConfigurableEnvironment environment) {
		//默认就是应用的名字
		String name = environment.getProperty("spring.application.name");
		return (StringUtils.hasText(name) ? name : "application");
	}

	/**
	 * The ID of a context.
	 */
	class ContextId {

		private final AtomicLong children = new AtomicLong(0);

		private final String id;

		ContextId(String id) {
			this.id = id;
		}

		//设置子容器的名字
		ContextId createChildId() {
			return new ContextId(this.id + "-" + this.children.incrementAndGet());
		}

		String getId() {
			return this.id;
		}

	}

}
