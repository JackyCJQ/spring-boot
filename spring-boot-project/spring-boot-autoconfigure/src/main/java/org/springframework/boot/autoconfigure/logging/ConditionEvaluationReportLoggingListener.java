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

package org.springframework.boot.autoconfigure.logging;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.logging.LogLevel;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ApplicationContextEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.GenericApplicationListener;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;

/**
 * {@link ApplicationContextInitializer} that writes the {@link ConditionEvaluationReport}
 * to the log. Reports are logged at the {@link LogLevel#DEBUG DEBUG} level unless there
 * was a problem, in which case they are the {@link LogLevel#INFO INFO} level is used.
 * <p>
 * This initializer is not intended to be shared across multiple application context
 * instances.
 * 虽然名字是一个Listener，但这实际上是一个ApplicationContextInitializer，其目的是将
 * ConditionEvaluationReport写入到日志，使用DEBUG级别输出。程序崩溃报告会触发一个消息输出，
 * 建议用户使用调试模式显示报告。
 * <p>
 * 该ApplicationContextInitializer的具体做法是在应用初始化时绑定一个ConditionEvaluationReportListener
 * 事件监听器，然后相应的事件发生时输出ConditionEvaluationReport报告。
 */
public class ConditionEvaluationReportLoggingListener
		implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	private final Log logger = LogFactory.getLog(getClass());

	private ConfigurableApplicationContext applicationContext;

	private ConditionEvaluationReport report;

	@Override
	public void initialize(ConfigurableApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
		applicationContext
				.addApplicationListener(new ConditionEvaluationReportListener());
		// 向容器绑定一个ConditionEvaluationReportListener事件监听器
		if (applicationContext instanceof GenericApplicationContext) {
			// Get the report early in case the context fails to load
			this.report = ConditionEvaluationReport
					.get(this.applicationContext.getBeanFactory());
		}
	}

	protected void onApplicationEvent(ApplicationEvent event) {
		ConfigurableApplicationContext initializerApplicationContext = this.applicationContext;
		if (event instanceof ContextRefreshedEvent) {
			// 遇到ContextRefreshedEvent事件，根据绑定应用上下文是否活跃作相应报告
			if (((ApplicationContextEvent) event)
					.getApplicationContext() == initializerApplicationContext) {
				// 事件应用上下文是该初始化器所绑定的应用上下文的话才报告
				logAutoConfigurationReport();
			}
		} else if (event instanceof ApplicationFailedEvent
				&& ((ApplicationFailedEvent) event)
				.getApplicationContext() == initializerApplicationContext) {
			// 遇到ApplicationFailedEvent事件，说起应用程序启动失败，做应用崩溃报告
			// 事件应用上下文是该初始化器所绑定的应用上下文的话才报告
			logAutoConfigurationReport(true);
		}
	}

	private void logAutoConfigurationReport() {
		// 如果所绑定应用上下文不活跃则认为是崩溃，否则认为是正常
		logAutoConfigurationReport(!this.applicationContext.isActive());
	}

	public void logAutoConfigurationReport(boolean isCrashReport) {
		if (this.report == null) {
			if (this.applicationContext == null) {
				this.logger.info("Unable to provide the conditions report "
						+ "due to missing ApplicationContext");
				return;
			}
			this.report = ConditionEvaluationReport
					.get(this.applicationContext.getBeanFactory());
		}
		if (!this.report.getConditionAndOutcomesBySource().isEmpty()) {
			if (isCrashReport && this.logger.isInfoEnabled()
					&& !this.logger.isDebugEnabled()) {
				this.logger.info(String
						.format("%n%nError starting ApplicationContext. To display the "
								+ "conditions report re-run your application with "
								+ "'debug' enabled."));
			}
			if (this.logger.isDebugEnabled()) {
				this.logger.debug(new ConditionEvaluationReportMessage(this.report));
			}
		}
	}

	private class ConditionEvaluationReportListener
			implements GenericApplicationListener {

		@Override
		public int getOrder() {
			return Ordered.LOWEST_PRECEDENCE;
		}

		@Override
		public boolean supportsEventType(ResolvableType resolvableType) {
			Class<?> type = resolvableType.getRawClass();
			if (type == null) {
				return false;
			}
			return ContextRefreshedEvent.class.isAssignableFrom(type)
					|| ApplicationFailedEvent.class.isAssignableFrom(type);
		}

		@Override
		public boolean supportsSourceType(Class<?> sourceType) {
			return true;
		}

		@Override
		public void onApplicationEvent(ApplicationEvent event) {
			ConditionEvaluationReportLoggingListener.this.onApplicationEvent(event);
		}

	}

}
