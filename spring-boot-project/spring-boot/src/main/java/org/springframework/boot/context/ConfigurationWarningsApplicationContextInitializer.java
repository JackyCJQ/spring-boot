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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 对于一些一般性配置错误在日志上输出警告
 */
public class ConfigurationWarningsApplicationContextInitializer
		implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	private static final Log logger = LogFactory.getLog(ConfigurationWarningsApplicationContextInitializer.class);

	@Override
	public void initialize(ConfigurableApplicationContext context) {
		// 并非真正在该初始化方法中执行相应的检查和日志报警任务，
		// 而是构造一个任务(getChecks())，然后注册一个BeanDefinitionRegistryPostProcessor
		// (ConfigurationWarningsPostProcessor)到容器，真正的检查和日志报警任务
		// 会在容器的BeanFactory post-process过程中进行。
		context.addBeanFactoryPostProcessor(new ConfigurationWarningsPostProcessor(getChecks()));
	}

	/**
	 * Returns the checks that should be applied.
	 * 构建要执行的检查任务
	 *
	 * @return the checks to apply
	 */
	protected Check[] getChecks() {
		return new Check[]{new ComponentScanPackageCheck()};
	}

	/**
	 * 定义一个BeanDefinitionRegistryPostProcessor，它会在BeanFactory的post-process阶段
	 * 执行指定的检查逻辑，并在遇到问题时日志输出警告
	 * {@link BeanDefinitionRegistryPostProcessor} to report warnings.
	 */
	protected static final class ConfigurationWarningsPostProcessor
			implements PriorityOrdered, BeanDefinitionRegistryPostProcessor {

		private Check[] checks;

		public ConfigurationWarningsPostProcessor(Check[] checks) {
			this.checks = checks;
		}

		@Override
		public int getOrder() {
			return Ordered.LOWEST_PRECEDENCE - 1;
		}

		@Override
		public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
				throws BeansException {
		}

		@Override
		public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry)
				throws BeansException {
			for (Check check : this.checks) {
				// 执行指定的检查任务
				String message = check.getWarning(registry);
				// 如果指定的检查任务执行遇到错误，会有非空错误消息返回，
				if (StringUtils.hasLength(message)) {
					// 如果有非空错误消息返回，日志输出该错误消息，使用警告级别
					warn(message);
				}
			}

		}

		private void warn(String message) {
			if (logger.isWarnEnabled()) {
				logger.warn(String.format("%n%n** WARNING ** : %s%n%n", message));
			}
		}

	}

	/**
	 * A single check that can be applied.
	 * 定义一个函数式接口，用于建模一个检查，接收一个参数BeanDefinitionRegistry。
	 */
	@FunctionalInterface
	protected interface Check {

		/**
		 * Returns a warning if the check fails or {@code null} if there are no problems.
		 * 如果检查失败返回一个警告消息，如果没问题返回null
		 *
		 * @param registry the {@link BeanDefinitionRegistry}
		 * @return a warning message or {@code null}
		 */
		String getWarning(BeanDefinitionRegistry registry);

	}

	/**
	 * @ComponentScan 包扫描检查问题包任务的抽象和建模
	 * {@link Check} for {@code @ComponentScan} on problematic package.
	 */
	protected static class ComponentScanPackageCheck implements Check {

		private static final Set<String> PROBLEM_PACKAGES;

		static {
			Set<String> packages = new HashSet<>();
			packages.add("org.springframework");
			packages.add("org");
			PROBLEM_PACKAGES = Collections.unmodifiableSet(packages);
		}

		@Override
		public String getWarning(BeanDefinitionRegistry registry) {
			// 获取所有要扫描的包的集合
			Set<String> scannedPackages = getComponentScanningPackages(registry);
			// 检查所有要扫描的包，并返回其中有问题的包的集合
			List<String> problematicPackages = getProblematicPackages(scannedPackages);
			if (problematicPackages.isEmpty()) {
				// 如果不存在问题包，返回null
				return null;
			}
			// 返回一个消息，描述哪些被扫描包存在问题
			return "Your ApplicationContext is unlikely to "
					+ "start due to a @ComponentScan of "
					+ StringUtils.collectionToDelimitedString(problematicPackages, ", ")
					+ ".";
		}

		protected Set<String> getComponentScanningPackages(
				BeanDefinitionRegistry registry) {
			Set<String> packages = new LinkedHashSet<>();
			// 获取bean注册表中所有BeanDefinition的名称，也可以理解成所有登记的bean的名称
			String[] names = registry.getBeanDefinitionNames();
			for (String name : names) {
				// 获取相应的BeanDefinitaion
				BeanDefinition definition = registry.getBeanDefinition(name);
				if (definition instanceof AnnotatedBeanDefinition) {
					// 如果这是一个类型为AnnotatedBeanDefinition的BeanDefinition,
					// 从其注解属性中搜集所有要扫描的包和类所在的包
					AnnotatedBeanDefinition annotatedDefinition = (AnnotatedBeanDefinition) definition;
					addComponentScanningPackages(packages,
							annotatedDefinition.getMetadata());
				}
			}
			return packages;
		}

		private void addComponentScanningPackages(Set<String> packages,
												  AnnotationMetadata metadata) {
			AnnotationAttributes attributes = AnnotationAttributes.fromMap(metadata
					.getAnnotationAttributes(ComponentScan.class.getName(), true));
			if (attributes != null) {
				addPackages(packages, attributes.getStringArray("value"));
				addPackages(packages, attributes.getStringArray("basePackages"));
				// 增加要扫描的类所在的包
				addClasses(packages, attributes.getStringArray("basePackageClasses"));
				if (packages.isEmpty()) {
					// 将当前metadata所属类的所在包也增加到packages中
					packages.add(ClassUtils.getPackageName(metadata.getClassName()));
				}
			}
		}

		private void addPackages(Set<String> packages, String[] values) {
			if (values != null) {
				Collections.addAll(packages, values);
			}
		}

		private void addClasses(Set<String> packages, String[] values) {
			if (values != null) {
				for (String value : values) {
					packages.add(ClassUtils.getPackageName(value));
				}
			}
		}

		private List<String> getProblematicPackages(Set<String> scannedPackages) {
			List<String> problematicPackages = new ArrayList<>();
			for (String scannedPackage : scannedPackages) {
				if (isProblematicPackage(scannedPackage)) {
					problematicPackages.add(getDisplayName(scannedPackage));
				}
			}
			return problematicPackages;
		}

		private boolean isProblematicPackage(String scannedPackage) {
			//如果该包为null或者0长度字符串，则认为这是一个问题包
			if (scannedPackage == null || scannedPackage.isEmpty()) {
				return true;
			}
			// 如果该包是PROBLEM_PACKAGES中的任何一个，也就是说如果它是
			// org 或者 org.springframework 也认为是问题包
			return PROBLEM_PACKAGES.contains(scannedPackage);
		}

		private String getDisplayName(String scannedPackage) {
			if (scannedPackage == null || scannedPackage.isEmpty()) {
				return "the default package";
			}
			return "'" + scannedPackage + "'";
		}

	}

}
