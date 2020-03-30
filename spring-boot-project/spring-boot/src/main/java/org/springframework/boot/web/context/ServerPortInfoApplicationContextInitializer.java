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

package org.springframework.boot.web.context;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.WebServer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.*;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link ApplicationContextInitializer} that sets {@link Environment} properties for the
 * ports that {@link WebServer} servers are actually listening on. The property
 * {@literal "local.server.port"} can be injected directly into tests using
 * {@link Value @Value} or obtained via the {@link Environment}.
 * <p>
 * If the {@link WebServerInitializedEvent} has a
 * {@link WebServerApplicationContext#getServerNamespace() server namespace} , it will be
 * used to construct the property name. For example, the "management" actuator context
 * will have the property name {@literal "local.management.port"}.
 * <p>
 * Properties are automatically propagated up to any parent context.
 * 即是一个ApplicationContextInitializer也是一个ApplicationListener，应用上下文
 * 初始化时将自己作为一个ApplicationListener注册到应用上下文，关注事件WebServerInitializedEvent,
 * 在该事件发生时向环境属性对象中添加一个属性对象"local.server.port",值是
 * WebServer当前使用的监听端口。这样的话，"local.server.port"就可以通过注解@Value
 * 的方式直接注入到测试中，或者通过Environment对象获得：
 * environment.getProperty("local.server.port")
 */
public class ServerPortInfoApplicationContextInitializer
		implements ApplicationContextInitializer<ConfigurableApplicationContext>,
		ApplicationListener<WebServerInitializedEvent> {

	@Override
	public void initialize(ConfigurableApplicationContext applicationContext) {
		// 应用上下文初始化过程中将自己作为一个ApplicationListener注册到应用上下文,
		// 关注的事件是WebServerInitializedEvent
		applicationContext.addApplicationListener(this);
	}

	@Override
	public void onApplicationEvent(WebServerInitializedEvent event) {
		// 作为ApplicationListener时的任务:WebServerInitializedEvent 事件发生时,
		// 将当前Web服务器监听端口设置到环境属性local.server.port(通常是这个名字，从下面
		//的代码来看，也有可能是其他名字)
		String propertyName = "local." + getName(event.getApplicationContext()) + ".port";
		setPortProperty(event.getApplicationContext(), propertyName,
				event.getWebServer().getPort());
	}

	private String getName(WebServerApplicationContext context) {
		// 如果制定了名字使用指定的名字否则使用缺省名称 server
		String name = context.getServerNamespace();
		return (StringUtils.hasText(name) ? name : "server");
	}

	private void setPortProperty(ApplicationContext context, String propertyName,
								 int port) {
		if (context instanceof ConfigurableApplicationContext) {
			// 将端口信息设置到应用上下文的环境属性中去
			setPortProperty(((ConfigurableApplicationContext) context).getEnvironment(),
					propertyName, port);
		}
		if (context.getParent() != null) {
			// 如果当前应用上下文的双亲应用上下文存在，则把该属性设置向上传播
			setPortProperty(context.getParent(), propertyName, port);
		}
	}

	@SuppressWarnings("unchecked")
	private void setPortProperty(ConfigurableEnvironment environment, String propertyName,
								 int port) {
		// 获取环境对象environment中的属性源server.ports,如果不存在则先创建它
		MutablePropertySources sources = environment.getPropertySources();
		PropertySource<?> source = sources.get("server.ports");
		if (source == null) {
			source = new MapPropertySource("server.ports", new HashMap<>());
			sources.addFirst(source);
		}
		// 将端口名称/值设置到环境对象environment的属性源server.ports中去
		((Map<String, Object>) source.getSource()).put(propertyName, port);
	}

}
